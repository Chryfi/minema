package info.ata4.minecraft.minema.client.modules.video;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.MathHelper;

import org.lwjgl.opengl.GLContext;

import info.ata4.minecraft.minema.CaptureSession;
import info.ata4.minecraft.minema.Minema;
import info.ata4.minecraft.minema.client.config.MinemaConfig;
import info.ata4.minecraft.minema.client.event.CameraTransformedEvent;
import info.ata4.minecraft.minema.client.event.EndRenderEvent;
import info.ata4.minecraft.minema.client.event.MidRenderEvent;
import info.ata4.minecraft.minema.client.event.MinemaEventbus;
import info.ata4.minecraft.minema.client.modules.CaptureModule;
import info.ata4.minecraft.minema.client.modules.modifiers.TimerModifier;
import info.ata4.minecraft.minema.client.modules.tracker.BaseTracker;
import info.ata4.minecraft.minema.client.modules.video.export.FrameExporter;
import info.ata4.minecraft.minema.client.modules.video.export.ImageFrameExporter;
import info.ata4.minecraft.minema.client.modules.video.export.PipeFrameExporter;
import info.ata4.minecraft.minema.util.reflection.PrivateAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;

public class VideoHandler extends CaptureModule {

	public static String customName;

	private ColorbufferReader colorReader;
	private FrameExporter colorExport;

	private DepthbufferReader depthReader;
	private FrameExporter depthExport;
	private ByteBuffer depthRemapping;
	private float depthDistance;

	private String colorName;
	private String depthName;
	private int startWidth;
	private int startHeight;
	private boolean recordGui;

	@Override
	protected void doEnable() throws Exception {
		MinemaConfig cfg = Minema.instance.getConfig();

		this.startWidth = MC.displayWidth;
		this.startHeight = MC.displayHeight;
		this.colorName = customName == null || customName.isEmpty() ? new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()) : customName;
		this.depthName = colorName.concat("depthBuffer");
		this.depthDistance = cfg.captureDepthDistance.get().floatValue();
		this.recordGui = cfg.recordGui.get();

		boolean usePBO = GLContext.getCapabilities().GL_ARB_pixel_buffer_object;
		boolean useFBO = OpenGlHelper.isFramebufferEnabled();
		boolean usePipe = cfg.useVideoEncoder.get();
		boolean recordDepth = cfg.captureDepth.get();

		CaptureSession.singleton.setFilename(colorName);
		customName = null;
		colorReader = new ColorbufferReader(startWidth, startHeight, usePBO, useFBO, Minema.instance.getConfig().useAlpha.get());
		colorExport = usePipe ? new PipeFrameExporter(true) : new ImageFrameExporter();

		if (recordDepth) {
			depthReader = new DepthbufferReader(startWidth, startHeight, usePBO, useFBO);
			depthExport = usePipe ? new PipeFrameExporter(false) : new ImageFrameExporter();
			depthRemapping = ByteBuffer.allocateDirect(startWidth * startHeight * 3);
			depthRemapping.rewind();
		}

		if (!Minema.instance.getConfig().useVideoEncoder.get()) {
			Path colorDir = CaptureSession.singleton.getCaptureDir().resolve(colorName);
			Path depthDir = CaptureSession.singleton.getCaptureDir().resolve(depthName);

			if (!Files.exists(colorDir)) {
				Files.createDirectory(colorDir);
			}
			if (recordDepth && !Files.exists(depthDir)) {
				Files.createDirectory(depthDir);
			}
		}

		colorExport.enable(colorName, startWidth, startHeight);
		if (depthExport != null)
			depthExport.enable(depthName, startWidth, startHeight);

		MinemaEventbus.midRenderBUS.registerListener((e) -> onRenderMid(e));
		MinemaEventbus.endRenderBUS.registerListener((e) -> onRenderEnd(e));
	}

	@Override
	protected void doDisable() throws Exception {
		
		// Export Last Frame
		colorExport.waitForLastExport();
		if (colorReader.readLastFrame()) {
			colorExport.exportFrame(colorReader.buffer);
		}
		
		colorReader.destroy();
		colorExport.destroy();
		colorReader = null;
		colorExport = null;

		if (depthReader == null)
			return;

		depthExport.waitForLastExport();
		if (depthReader.readLastFrame()) {
			ByteBuffer floats = depthReader.buffer;

			while (floats.hasRemaining()) {
				float f = floats.getFloat();
				byte b = (byte) (linearizeDepth(f) * 255);
				depthRemapping.put(b);
				depthRemapping.put(b);
				depthRemapping.put(b);
			}

			floats.rewind();
			depthRemapping.rewind();

			depthExport.exportFrame(depthRemapping);
		}
		depthReader.destroy();
		depthExport.destroy();
		depthExport = null;
		depthReader = null;
		depthRemapping = null;
	}

	@Override
	protected boolean checkEnable() {
		return !Minema.instance.getConfig().vr.get();
	}

	private void onRenderMid(MidRenderEvent e) throws Exception {
	    try {
	        checkDimensions();
	    } catch (IllegalStateException ex) {
	        if (PrivateAccessor.isShaderPackLoaded())
	            return;
	        throw ex;
	    }
		
		if (!TimerModifier.canRecord())
			return;

		if (depthReader != null) {
			depthExport.waitForLastExport();
			if (depthReader.readPixels()) {
				ByteBuffer floats = depthReader.buffer;

				while (floats.hasRemaining()) {
					float f = floats.getFloat();
					byte b = (byte) (linearizeDepth(f) * 255);
					depthRemapping.put(b);
					depthRemapping.put(b);
					depthRemapping.put(b);
				}

				floats.rewind();
				depthRemapping.rewind();

				depthExport.exportFrame(depthRemapping);
			}
		}

		if (!recordGui && !PrivateAccessor.isShaderPackLoaded()) {
			exportColor();

			e.session.getTime().nextFrame();
		}
	}

	private void exportColor() throws Exception {
		colorExport.waitForLastExport();
		if (colorReader.readPixels()) {
			colorExport.exportFrame(colorReader.buffer);
		}
	}

	private float linearizeDepth(float z) {
		final float near = 0.05f;
		float far = Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16;
		if (PrivateAccessor.isShaderPackSupported()) {
		    far *= 2;
		    if (far < 173F)
		        far = 173F;
		} else
		    far *= MathHelper.SQRT_2;
		float customFar = this.depthDistance > 0 ? this.depthDistance : Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16;
		return MathHelper.clamp(2 * near * far / (far + near - (2 * z - 1) * (far - near)) / customFar, 0F, 1F);
	}

	private void onRenderEnd(EndRenderEvent e) throws Exception {
		checkDimensions();
		
		if (!TimerModifier.canRecord())
			return;

		if (recordGui || PrivateAccessor.isShaderPackLoaded()) {
			exportColor();

			e.session.getTime().nextFrame();
		}

	}

	private void checkDimensions() {
		if (MC.displayWidth != startWidth || MC.displayHeight != startHeight) {
			throw new IllegalStateException(I18n.format("minema.error.size_change",
					MC.displayWidth, MC.displayHeight, startWidth, startHeight));
		}
	}

}
