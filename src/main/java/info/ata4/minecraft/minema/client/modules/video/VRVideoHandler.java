package info.ata4.minecraft.minema.client.modules.video;

import static org.lwjgl.opengl.ARBBufferObject.GL_STREAM_COPY_ARB;
import static org.lwjgl.opengl.ARBBufferObject.glBindBufferARB;
import static org.lwjgl.opengl.ARBBufferObject.glBufferDataARB;
import static org.lwjgl.opengl.ARBBufferObject.glDeleteBuffersARB;
import static org.lwjgl.opengl.ARBBufferObject.glGenBuffersARB;
import static org.lwjgl.opengl.ARBPixelBufferObject.GL_PIXEL_PACK_BUFFER_ARB;
import static org.lwjgl.opengl.ARBPixelBufferObject.GL_PIXEL_UNPACK_BUFFER_ARB;

import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.file.Files;
import java.util.HashSet;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

import info.ata4.minecraft.minema.CaptureSession;
import info.ata4.minecraft.minema.Minema;
import info.ata4.minecraft.minema.client.config.MinemaConfig;
import info.ata4.minecraft.minema.client.event.MinemaEventbus;
import info.ata4.minecraft.minema.client.modules.modifiers.TimerModifier;
import info.ata4.minecraft.minema.client.modules.video.vr.CubeFace;
import info.ata4.minecraft.minema.client.modules.video.vr.Mp4SphericalInjector;
import info.ata4.minecraft.minema.client.util.MinemaException;
import info.ata4.minecraft.minema.client.util.ShaderHelper;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.client.event.EntityViewRenderEvent.FOVModifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class VRVideoHandler extends AbstractVideoHandler {

	private static float SCALEFOV = 112.619865f; // atan(1.5)

	private static int program = ShaderHelper.createShader(
				VRVideoHandler.class.getResourceAsStream("/assets/minema/shaders/screen.vert"),
				VRVideoHandler.class.getResourceAsStream("/assets/minema/shaders/vr.frag"));

	private int cubemap = 0;
	private int cubemapDepth = 0;

	private int pbo = 0;
	private ByteBuffer depthBuffer;

	private int x;
	private int y;
	private int w;
	private int h;

	private float fov;

	private final DoubleBuffer buffer = BufferUtils.createDoubleBuffer(16);
	private final HashSet<String> outputs = new HashSet<>();

	@Override
	protected void doEnable() throws Exception {
		super.doEnable();

		MinemaConfig cfg = Minema.instance.getConfig();

		cubemap = GL11.glGenTextures();
		GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, cubemap);
		GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);

		if (recordDepth) {
			cubemapDepth = GL11.glGenTextures();
			GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, cubemapDepth);
			GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
			GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, 0);
		}

		boolean usePBO = GLContext.getCapabilities().GL_ARB_pixel_buffer_object;
		boolean useFBO = OpenGlHelper.isFramebufferEnabled();
		boolean usePipe = cfg.useVideoEncoder.get();

		MinemaEventbus.cameraBUS.registerListener(e -> this.afterCamera());
		MinecraftForge.EVENT_BUS.register(this);

		outputs.clear();

		if (cfg.vrSSRSupport.get()) {
			x = startHeight / 3 * 2;
			y = startHeight / 6;
			w = startHeight / 3 * 2;
			h = startHeight / 3 * 2;
			fov = SCALEFOV;
		} else {
			x = startHeight / 2;
			y = 0;
			w = startHeight;
			h = startHeight;
			fov = 90;
		}

		if (recordDepth) {
			if (usePBO) {
				pbo = glGenBuffersARB();
				glBindBufferARB(GL_PIXEL_PACK_BUFFER_ARB, pbo);
				glBufferDataARB(GL_PIXEL_PACK_BUFFER_ARB, w * h * 4, GL_STREAM_COPY_ARB);
				glBindBufferARB(GL_PIXEL_PACK_BUFFER_ARB, 0);
			} else {
				depthBuffer = ByteBuffer.allocateDirect(w * h * 4);
			}
		}

		if (program == -1)
			throw new MinemaException(I18n.format("minema.error.vr_not_support"));
		
		GL20.glUseProgram(program);
		GL20.glUniform1i(GL20.glGetUniformLocation(program, "tex"), 0);
		GL20.glUniform1i(GL20.glGetUniformLocation(program, "depthtex"), 1);
		GL20.glUseProgram(0);
		
		if (usePipe) {
			String paramStr = cfg.useAlpha.get() ? cfg.videoEncoderParamsAlpha.get() : cfg.videoEncoderParams.get();
			paramStr = paramStr.substring(paramStr.lastIndexOf(" -i ") + 4);
			String[] params = paramStr.split(" ");
			for (String param : params) {
				if (param.endsWith(".mp4")) {
					String filename = param.replace("%NAME%", colorName);
					if (Files.exists(CaptureSession.singleton.getCaptureDir().resolve(filename))) {
						outputs.clear();
						throw new MinemaException(I18n.format("minema.error.file_exists", filename));
					}
					outputs.add(filename);
				}
			}
			
			if (recordDepth) {
				paramStr = cfg.videoEncoderParams.get();
				paramStr = paramStr.substring(paramStr.lastIndexOf(" -i ") + 4);
				params = paramStr.split(" ");
				for (String param : params) {
					if (param.endsWith(".mp4")) {
						String filename = param.replace("%NAME%", depthName);
						if (Files.exists(CaptureSession.singleton.getCaptureDir().resolve(filename))) {
							outputs.clear();
							throw new MinemaException(I18n.format("minema.error.file_exists", filename));
						}
						outputs.add(filename);
					}
				}
			}
		}
	}

	@Override
	protected void doDisable() throws Exception {
		super.doDisable();

		MinecraftForge.EVENT_BUS.unregister(this);

		GL11.glDeleteTextures(cubemap);
		cubemap = 0;

		if (cubemapDepth != 0)
			GL11.glDeleteTextures(cubemapDepth);
		cubemapDepth = 0;

		if (pbo != 0)
			glDeleteBuffersARB(pbo);
		pbo = 0;

		depthBuffer = null;

		if (Minema.instance.getConfig().vrMetadata.get()) {
			File dir = CaptureSession.singleton.getCaptureDir().toFile();
			for (String output : outputs) {
				File in = new File(dir, output);
				if (in.exists()) {
					try {
						Mp4SphericalInjector.inject(in);
					} catch (Mp4SphericalInjector.InjectFailedException e) {
						MC.ingameGUI.addChatMessage(ChatType.CHAT, new TextComponentTranslation("minema.error.vr_broken", output));
					}
				}
			}
		}
	}

	@Override
	protected boolean checkEnable() {
		return Minema.instance.getConfig().vr.get() && MC.isSingleplayer();
	}

	@Override
	protected boolean doExport() throws Exception {
		CubeFace face = TimerModifier.getCubeFace();

		GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, cubemap);

		if (TimerModifier.canRecord())
			GL11.glCopyTexImage2D(face.target, 0, GL11.GL_RGBA8, x, y, w, h, 0);

		GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, 0);

		boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
		boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
		boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
		boolean fog = GL11.glIsEnabled(GL11.GL_FOG);
		int prog = GlStateManager.glGetInteger(GL20.GL_CURRENT_PROGRAM);
		int func = GlStateManager.glGetInteger(GL11.GL_DEPTH_FUNC);
		boolean depthw = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

		GL20.glUseProgram(program);

		GlStateManager.disableAlpha();
		GlStateManager.disableBlend();
		if (recordDepth)
			GlStateManager.enableDepth();
		else
			GlStateManager.disableDepth();
		GlStateManager.disableFog();
		GlStateManager.depthFunc(GL11.GL_ALWAYS);

		GlStateManager.depthMask(true);
		GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, cubemap);
		GlStateManager.setActiveTexture(GL13.GL_TEXTURE1);
		GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, cubemapDepth);

		GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
		GL11.glTexCoord2f(0.0F, 0.0F);
		GL11.glVertex3f(-1.0F, -1.0F, 1.0F);
		GL11.glTexCoord2f(1.0F, 0.0F);
		GL11.glVertex3f(1.0F, -1.0F, 1.0F);
		GL11.glTexCoord2f(0.0F, 1.0F);
		GL11.glVertex3f(-1.0F, 1.0F, 1.0F);
		GL11.glTexCoord2f(1.0F, 1.0F);
		GL11.glVertex3f(1.0F, 1.0F, 1.0F);
		GL11.glEnd();
		GL11.glFlush();

		GlStateManager.setActiveTexture(GL13.GL_TEXTURE1);
		GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, 0);
		GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
		GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, 0);

		GlStateManager.depthMask(depthw);
		GlStateManager.depthFunc(func);

		if (alpha)
			GlStateManager.enableAlpha();
		if (blend)
			GlStateManager.enableBlend();
		if (!depth)
			GlStateManager.disableDepth();
		else
			GlStateManager.enableDepth();
		if (fog)
			GlStateManager.enableFog();

		GL20.glUseProgram(prog);

		if (TimerModifier.canRecord() && face == CubeFace.BOTTOM) {
			if (recordDepth)
				this.exportDepth();

			this.exportColor();
			return true;
		}
		return false;
	}

	@Override
	protected void updateDepth() throws Exception {
		if (!TimerModifier.canRecord())
			return;

		GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);

		CubeFace face = TimerModifier.getCubeFace();
		GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, cubemapDepth);
		if (pbo != 0) {
			glBindBufferARB(GL_PIXEL_PACK_BUFFER_ARB, pbo);
			GL11.glReadPixels(x, y, w, h, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, 0);
			glBindBufferARB(GL_PIXEL_PACK_BUFFER_ARB, 0);

			glBindBufferARB(GL_PIXEL_UNPACK_BUFFER_ARB, pbo);
			GL11.glTexImage2D(face.target, 0, GL11.GL_DEPTH_COMPONENT, w, h, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, 0);
			glBindBufferARB(GL_PIXEL_UNPACK_BUFFER_ARB, 0);
		} else {
			GL11.glReadPixels(x, y, w, h, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depthBuffer);
			depthBuffer.rewind();
			GL11.glTexImage2D(face.target, 0, GL11.GL_DEPTH_COMPONENT, w, h, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depthBuffer);
			depthBuffer.rewind();
		}
		GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, 0);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void lockFOV(FOVModifier e) {
		e.setFOV(fov);
	}

	private void afterCamera() {
		CubeFace face = TimerModifier.getCubeFace();
		int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
		GL11.glMatrixMode(GL11.GL_MODELVIEW);
		buffer.clear();
		GL11.glGetDouble(GL11.GL_MODELVIEW_MATRIX, buffer);
		buffer.rewind();
		
		GL11.glLoadIdentity();
		GL11.glRotated(face.rotateX, 1, 0, 0);
		GL11.glRotated(face.rotateY, 0, 1, 0);
		GL11.glMultMatrix(buffer);
		GL11.glMatrixMode(mode);
	}

}
