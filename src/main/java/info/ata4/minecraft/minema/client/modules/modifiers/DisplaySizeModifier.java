package info.ata4.minecraft.minema.client.modules.modifiers;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

import info.ata4.minecraft.minema.Minema;
import info.ata4.minecraft.minema.client.config.MinemaConfig;
import info.ata4.minecraft.minema.client.modules.CaptureModule;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;

public class DisplaySizeModifier extends CaptureModule {

    private static DisplaySizeModifier instance;
    
	private int originalWidth;
	private int originalHeight;
	private boolean aaFastRenderFix;

    public static boolean onResize(int w, int h) {
        if (instance != null && instance.isEnabled() && !instance.aaFastRenderFix) {
            instance.originalWidth = w;
            instance.originalHeight = h;
            MC.displayWidth = Minema.instance.getConfig().getFrameWidth();
            MC.displayHeight = Minema.instance.getConfig().getFrameHeight();
            if (OpenGlHelper.isFramebufferEnabled()) {
                instance.setFramebufferTextureSize(w, h);
            }
            return false;
        }
        return true;
    }

	@Override
	protected void doEnable() throws LWJGLException {
		MinemaConfig cfg = Minema.instance.getConfig();
		originalWidth = Display.getWidth();
		originalHeight = Display.getHeight();

		aaFastRenderFix = cfg.aaFastRenderFix.get();
		
		if (Minema.instance.getConfig().useFrameSize())
		{
	        resize(cfg.getFrameWidth(), cfg.getFrameHeight());

	        if (aaFastRenderFix) {
	            Display.setDisplayMode(new DisplayMode(cfg.getFrameWidth(), cfg.getFrameHeight()));
	            Display.update();
	        } else {
	            // render framebuffer texture in original size
	            if (OpenGlHelper.isFramebufferEnabled()) {
	                setFramebufferTextureSize(originalWidth, originalHeight);
	            }
	        }
		}

        instance = this;
	}

	@Override
	protected boolean checkEnable() {
		return true;
	}

	@Override
	protected void doDisable() throws LWJGLException {
        instance = null;
        
		if (aaFastRenderFix) {
			Display.setDisplayMode(new DisplayMode(originalWidth, originalHeight));
			// Fix MC-68754
			Display.setResizable(false);
			Display.setResizable(true);
		}
		resize(originalWidth, originalHeight);
	}

	public void resize(int width, int height) {
		MC.resize(width, height);
	}

	public void setFramebufferTextureSize(int width, int height) {
		Framebuffer fb = MC.getFramebuffer();
		fb.framebufferTextureWidth = width;
		fb.framebufferTextureHeight = height;
	}
}
