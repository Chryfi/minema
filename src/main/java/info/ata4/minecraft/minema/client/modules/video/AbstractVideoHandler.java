package info.ata4.minecraft.minema.client.modules.video;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.minecraft.client.resources.I18n;
import org.lwjgl.opengl.GLContext;

import info.ata4.minecraft.minema.CaptureSession;
import info.ata4.minecraft.minema.Minema;
import info.ata4.minecraft.minema.client.config.MinemaConfig;
import info.ata4.minecraft.minema.client.event.EndRenderEvent;
import info.ata4.minecraft.minema.client.event.MidRenderEvent;
import info.ata4.minecraft.minema.client.event.MinemaEventbus;
import info.ata4.minecraft.minema.client.modules.CaptureModule;
import info.ata4.minecraft.minema.client.modules.video.export.FrameExporter;
import info.ata4.minecraft.minema.client.modules.video.export.ImageFrameExporter;
import info.ata4.minecraft.minema.client.modules.video.export.PipeFrameExporter;
import info.ata4.minecraft.minema.util.reflection.PrivateAccessor;
import net.minecraft.client.renderer.OpenGlHelper;

public abstract class AbstractVideoHandler extends CaptureModule {

	public static String customName;

	protected CommonReader colorReader;
	protected FrameExporter colorExport;

	protected CommonReader depthReader;
	protected FrameExporter depthExport;

	protected String colorName;
	
	protected boolean recordDepth;

	protected int startWidth;
	protected int startHeight;
	protected boolean recordGui;

	@Override
	protected void doEnable() throws Exception
	{
		MinemaConfig cfg = Minema.instance.getConfig();

		this.startWidth = MC.displayWidth;
		this.startHeight = MC.displayHeight;
		this.colorName = customName == null || customName.isEmpty() ? new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()) : customName;
		this.recordGui = cfg.recordGui.get();
        this.recordDepth = cfg.captureDepth.get();

		boolean usePBO = GLContext.getCapabilities().GL_ARB_pixel_buffer_object;
		boolean useFBO = OpenGlHelper.isFramebufferEnabled();
		boolean usePipe = cfg.useVideoEncoder.get();

		CaptureSession.singleton.setFilename(colorName);
		customName = null;
		colorReader = new ColorbufferReader(startWidth, startHeight, 1, usePBO, useFBO, cfg.useAlpha.get());
		colorExport = usePipe ? new PipeFrameExporter(true) : new ImageFrameExporter(true);

		if (recordDepth)
		{
			depthReader = new DepthbufferReader(startWidth, startHeight, usePBO, useFBO, cfg.captureDepthDistance.get().floatValue());
			depthExport = usePipe ? new PipeFrameExporter(false) : new ImageFrameExporter(false);
		}

		if (!cfg.useVideoEncoder.get())
		{
			Path colorDir = CaptureSession.singleton.getCaptureDir().resolve(colorName);

			if (!Files.exists(colorDir))
			{
				Files.createDirectory(colorDir);
			}
		}

		colorExport.enable(colorName, startWidth, startHeight);

		if (depthExport != null)
		{
			depthExport.enable(colorName, startWidth, startHeight);
		}

		MinemaEventbus.midRenderBUS.registerListener((e) -> onRenderMid(e));
		MinemaEventbus.endRenderBUS.registerListener((e) -> onRenderEnd(e));
	}

	@Override
	protected void doDisable() throws Exception {
        try {
            colorExport.waitForLastExport();
            if (colorReader.readLastFrame()) {
                colorExport.exportFrame(colorReader.buffer);
            }
            colorExport.waitForLastExport();
        } catch (Exception e) {}
		
		colorReader.destroy();
		colorExport.destroy();
		colorReader = null;
		colorExport = null;

		if (!recordDepth)
			return;

		try {
	        depthExport.waitForLastExport();
	        if (depthReader.readLastFrame()) {
	            depthExport.exportFrame(depthReader.buffer);
	        }
	        depthExport.waitForLastExport();
		} catch (Exception e) {}

		depthReader.destroy();
		depthExport.destroy();
		depthExport = null;
		depthReader = null;
	}

    protected abstract void updateDepth() throws Exception;
    
    protected abstract boolean doExport() throws Exception;

	private void onRenderMid(MidRenderEvent e) throws Exception {
	    if (!checkDimensions())
	        return;

	    if (recordDepth)
	        updateDepth();

		if (!recordGui && !PrivateAccessor.isShaderPackLoaded()) {
            if (doExport())
                e.session.getTime().nextFrame();
		}
	}

	private void onRenderEnd(EndRenderEvent e) throws Exception {
		if (!checkDimensions())
            throw new IllegalStateException(I18n.format("minema.error.size_change",
                    MC.displayWidth, MC.displayHeight, startWidth, startHeight));

		if (recordGui || PrivateAccessor.isShaderPackLoaded())
			if (doExport())
			    e.session.getTime().nextFrame();
	}

    protected void exportColor() throws Exception {
        colorExport.waitForLastExport();
        if (colorReader.readPixels()) {
            colorExport.exportFrame(colorReader.buffer);
        }
    }
    
    protected void exportDepth() throws Exception {
        depthExport.waitForLastExport();
        if (depthReader.readPixels()) {
            depthExport.exportFrame(depthReader.buffer);
        }
    }

	private boolean checkDimensions() {
		return MC.displayWidth == startWidth && MC.displayHeight == startHeight;
	}

}
