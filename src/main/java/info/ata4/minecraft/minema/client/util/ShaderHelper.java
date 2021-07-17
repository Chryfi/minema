package info.ata4.minecraft.minema.client.util;

import java.io.InputStream;

import org.lwjgl.opengl.GL20;

public class ShaderHelper {
	
	public static int createShader(InputStream vertex, InputStream fragment) {
		int program = GL20.glCreateProgram();

		int vert = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);

		try (InputStream in = vertex) {
			byte[] b = new byte[in.available()];
			in.read(b);
			String code = new String(b);
			GL20.glShaderSource(vert, code);
			GL20.glCompileShader(vert);
			if (GL20.glGetShaderi(vert, GL20.GL_COMPILE_STATUS) == 0)
				throw new Exception();
			GL20.glAttachShader(program, vert);
		} catch (Exception e) {
			GL20.glDeleteShader(vert);
			GL20.glDeleteProgram(program);
			program = -1;
		}

		if (program != -1) {
			int frag = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
			try (InputStream in = fragment) {
				byte[] b = new byte[in.available()];
				in.read(b);
				String code = new String(b);
				GL20.glShaderSource(frag, code);
				GL20.glCompileShader(frag);
				if (GL20.glGetShaderi(vert, GL20.GL_COMPILE_STATUS) == 0)
					throw new Exception();
				GL20.glAttachShader(program, frag);
			} catch (Exception e) {
				GL20.glDeleteShader(vert);
				GL20.glDeleteShader(frag);
				GL20.glDeleteProgram(program);
				program = -1;
			}

			if (program != -1) {
				GL20.glLinkProgram(program);
				GL20.glDetachShader(program, vert);
				GL20.glDetachShader(program, frag);
				GL20.glDeleteShader(vert);
				GL20.glDeleteShader(frag);
				GL20.glValidateProgram(program);

				if (GL20.glGetProgrami(program, GL20.GL_VALIDATE_STATUS) == 0) {
					GL20.glDeleteProgram(program);
					program = -1;
				}
			}
		}
		
		return program;
	}

}
