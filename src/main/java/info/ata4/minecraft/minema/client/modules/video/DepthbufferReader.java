package info.ata4.minecraft.minema.client.modules.video;

import static org.lwjgl.opengl.ARBBufferObject.GL_STREAM_COPY_ARB;
import static org.lwjgl.opengl.ARBBufferObject.glBindBufferARB;
import static org.lwjgl.opengl.ARBBufferObject.glBufferDataARB;
import static org.lwjgl.opengl.ARBBufferObject.glDeleteBuffersARB;
import static org.lwjgl.opengl.ARBBufferObject.glGenBuffersARB;
import static org.lwjgl.opengl.ARBBufferObject.glMapBufferARB;
import static org.lwjgl.opengl.ARBBufferObject.glUnmapBufferARB;
import static org.lwjgl.opengl.ARBPixelBufferObject.GL_PIXEL_PACK_BUFFER_ARB;
import static org.lwjgl.opengl.ARBPixelBufferObject.GL_PIXEL_UNPACK_BUFFER_ARB;
import static org.lwjgl.opengl.GL11.*;

import java.nio.ByteBuffer;

import info.ata4.minecraft.minema.Minema;
import info.ata4.minecraft.minema.client.config.enums.BitDepth;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.Util;

import info.ata4.minecraft.minema.client.util.ShaderHelper;
import info.ata4.minecraft.minema.util.reflection.PrivateAccessor;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.math.MathHelper;

public class DepthbufferReader extends CommonReader {

	private static final int program = ShaderHelper.createShader(
			DepthbufferReader.class.getResourceAsStream("/assets/minema/shaders/screen.vert"),
			DepthbufferReader.class.getResourceAsStream("/assets/minema/shaders/depth.frag"));

	private float preCalcNear;
	private ByteBuffer prebuffer;

	private ColorbufferReader proxy;
	private int depthTex;
	private int depthPbo;

	public DepthbufferReader(int width, int height, boolean isPBO, boolean isFBO, float customFar) {
		super(width, height, 4, GL_FLOAT, GL_DEPTH_COMPONENT, isPBO, isFBO);

		customFar = customFar > 0 ? customFar : MC.gameSettings.renderDistanceChunks * 16;
		preCalcNear = 0.1F / customFar;

		BitDepth bitDepth = Minema.instance.getConfig().depthBufferBitDepth.get();
		int bytesPerChannel = bitDepth.getBytesPerChannel();
		int format = bitDepth.getFormat();

		/*if (isPBO && isFBO && program > 0) {
			GL20.glUseProgram(program);
			GL20.glUniform1i(GL20.glGetUniformLocation(program, "tex"), 0);
			GL20.glUniform1f(GL20.glGetUniformLocation(program, "near"), 0.05f);
			GL20.glUniform1f(GL20.glGetUniformLocation(program, "preCalcNear"), preCalcNear);
			GL20.glUseProgram(0);

			depthTex = GL11.glGenTextures();
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTex);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

			depthPbo = glGenBuffersARB();
			glBindBufferARB(PBO_TARGET, depthPbo);
			glBufferDataARB(PBO_TARGET, bufferSize, GL_STREAM_COPY_ARB);
			glBindBufferARB(PBO_TARGET, 0);

			proxy = new ColorbufferReader(width, height, bytesPerChannel, format, isPBO, isFBO, false);
			proxy.fb = new Framebuffer(width, height, false);
		} else {*/
			prebuffer = buffer;

			buffer = ByteBuffer.allocateDirect(width * height * ((bitDepth == BitDepth.BIT32) ? 1 : 3) * bytesPerChannel);
			buffer.rewind();
		//}
	}

	@Override
	public boolean readPixels() {
		// set alignment flags
		glPixelStorei(GL_PACK_ALIGNMENT, 1);
		glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

		// GPU acceleration
		if (proxy != null) {
			glBindBufferARB(GL_PIXEL_PACK_BUFFER_ARB, depthPbo);
			glReadPixels(0, 0, width, height, GL_DEPTH_COMPONENT, GL_FLOAT, 0);
			glBindBufferARB(GL_PIXEL_PACK_BUFFER_ARB, 0);

			GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTex);
			glBindBufferARB(GL_PIXEL_UNPACK_BUFFER_ARB, depthPbo);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, width, height, 0, GL11.GL_DEPTH_COMPONENT,
					GL11.GL_FLOAT, 0);
			glBindBufferARB(GL_PIXEL_UNPACK_BUFFER_ARB, 0);

			boolean alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
			boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
			boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
			boolean fog = GL11.glIsEnabled(GL11.GL_FOG);

			int prog = GlStateManager.glGetInteger(GL20.GL_CURRENT_PROGRAM);

			GL20.glUseProgram(program);

			float far = MC.gameSettings.renderDistanceChunks * 16;
			if (PrivateAccessor.isShaderPackSupported()) {
				far *= 2;

				if (PrivateAccessor.isFogFancy())
					far *= 0.95F;

				if (PrivateAccessor.isFogFast())
					far *= 0.83F;

				if (far < 173F)
					far = 173F;
			} else
				far *= MathHelper.SQRT_2;
			GL20.glUniform1f(GL20.glGetUniformLocation(program, "far"), this.calculateFar());

			GlStateManager.disableAlpha();
			GlStateManager.disableBlend();
			GlStateManager.disableDepth();
			GlStateManager.disableFog();

			int lastfb = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
			proxy.fb.bindFramebuffer(false);

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

			OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, lastfb);

			GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

			if (alpha)
				GlStateManager.enableAlpha();
			if (blend)
				GlStateManager.enableBlend();
			if (depth)
				GlStateManager.enableDepth();
			if (fog)
				GlStateManager.enableFog();

			GL20.glUseProgram(prog);

			boolean ret = proxy.readPixels();
			buffer = proxy.buffer;
			return ret;
		}
		else
		{
			// Cannot read Minecraft's framebuffer even if it is active, as the depth buffer
			// is not a texture
			if (isPBO)
			{
				glBindBufferARB(PBO_TARGET, frontName);

				glReadPixels(0, 0, width, height, FORMAT, TYPE, 0);

				// copy back-buffer
				glBindBufferARB(PBO_TARGET, backName);
				prebuffer = glMapBufferARB(PBO_TARGET, PBO_ACCESS, bufferSize, prebuffer);
				glUnmapBufferARB(PBO_TARGET);
				glBindBufferARB(PBO_TARGET, 0);

				// If mapping threw an error -> crash immediately please
				Util.checkGLError();

				// swap PBOs
				int swapName = frontName;
				frontName = backName;
				backName = swapName;
			}
			else
			{
				glReadPixels(0, 0, width, height, FORMAT, TYPE, prebuffer);
			}

			prebuffer.rewind();

			this.writeDepth();

			prebuffer.rewind();
			buffer.rewind();

			// first frame is empty in PBO mode, don't export it
			if (isPBO & firstFrame)
			{
				firstFrame = false;

				return false;
			}

			return true;
		}
	}

	@Override
	public boolean readLastFrame() {
		if (proxy != null) {
			boolean ret = proxy.readLastFrame();
			this.buffer = proxy.buffer;
			return ret;
		} else if (isPBO && !firstFrame) {
			glBindBufferARB(PBO_TARGET, backName);
			prebuffer = glMapBufferARB(PBO_TARGET, PBO_ACCESS, bufferSize, prebuffer);
			glUnmapBufferARB(PBO_TARGET);
			glBindBufferARB(PBO_TARGET, 0);

			Util.checkGLError();

			prebuffer.rewind();

			this.writeDepth();

			prebuffer.rewind();
			buffer.rewind();
			return true;
		}
		return false;
	}

	@Override
	public void destroy() {
		super.destroy();

		if (proxy != null) {
			proxy.destroy();
			proxy.fb.deleteFramebuffer();

			glDeleteBuffersARB(depthPbo);
			GL11.glDeleteTextures(depthTex);
		}
	}

	private void writeDepth()
	{
		while (prebuffer.hasRemaining())
		{
			float depth = prebuffer.getFloat();

			switch (Minema.instance.getConfig().depthBufferBitDepth.get())
			{
				case BIT8:
					byte b = (byte) (this.linearizeDepth(depth) * (Math.pow(2, 8) - 1));

					buffer.put(b);
					buffer.put(b);
					buffer.put(b);

					break;
				case BIT16:
					short s = (short) (this.linearizeDepth(depth) * (Math.pow(2, 16) - 1));

					buffer.putShort(s);
					buffer.putShort(s);
					buffer.putShort(s);

					break;
				case BIT32:
					float f = this.linearizeDepth(depth);

					buffer.putFloat(f);

					break;
			}
		}
	}

	private float linearizeDepth(float z) {
		final float near = 0.05f;
		float far = this.calculateFar();

		return MathHelper.clamp(preCalcNear * far / (far + near - (2 * z - 1) * (far - near)), 0F, 1F);
	}

	private float calculateFar()
	{
		float far = MC.gameSettings.renderDistanceChunks * 16;

		if (PrivateAccessor.isShaderPackSupported())
		{
			far *= 2;

			if (PrivateAccessor.isFogFancy()) far *= 0.95F;

			if (PrivateAccessor.isFogFast()) far *= 0.83F;

			if (far < 173F) far = 173F;
		}
		else
		{
			far *= MathHelper.SQRT_2;
		}

		return far;
	}
}
