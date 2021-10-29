package info.ata4.minecraft.minema;

import info.ata4.minecraft.minema.client.util.MinemaException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.lang.reflect.Field;

public class Utils {

	public static void print(final String msg, final TextFormatting format) {
		final TextComponentString text = new TextComponentString(msg == null ? "null" : msg);
		text.getStyle().setColor(format);
		Minecraft.getMinecraft().player.sendMessage(text);
	}

	public static void printError(Throwable throwable) {
		print(throwable.getClass().getName(), TextFormatting.RED);
		print(throwable.getMessage(), TextFormatting.RED);
		Throwable cause = throwable;
		Throwable cause2 = cause.getCause();
		while (cause2 != null && cause2 != cause) {
			cause = cause2;
			cause2 = cause2.getCause();
		}
		if (cause != null && cause != throwable) {
			print(I18n.format("minema.error.cause"), TextFormatting.RED);
			print(cause.getClass().getName(), TextFormatting.RED);
			print(cause.getMessage(), TextFormatting.RED);
		}
		throwable.printStackTrace();
		print(I18n.format("minema.error.stacktrace"), TextFormatting.RED);
	}

	public static void printPrettyError(Throwable throwable) {
		Throwable cause = throwable;
		Throwable cause2 = cause.getCause();
		while (cause2 != null && cause2 != cause) {
			cause = cause2;
			cause2 = cause2.getCause();
		}

		if (cause != null && (cause != throwable || throwable instanceof MinemaException)) {
			print(cause.getMessage() + "\n", TextFormatting.RED);
		}

		throwable.printStackTrace();
		print(I18n.format("minema.error.stacktrace"), TextFormatting.RED);
	}

	public static Field getField(Class clazz, String mcp, String srg) {
		Field field = null;

		try {
			field = clazz.getDeclaredField(mcp);
		} catch (Exception e) {}

		if (field == null) {
			try {
				field = clazz.getDeclaredField(srg);
			} catch (Exception e) {}
		}

		if (field != null) {
			field.setAccessible(true);
		}

		return field;
	}

}
