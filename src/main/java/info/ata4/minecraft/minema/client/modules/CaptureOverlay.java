/*
 ** 2012 March 31
 **
 ** The author disclaims copyright to this source code.  In place of
 ** a legal notice, here is a blessing:
 **    May you do good and not evil.
 **    May you find forgiveness for yourself and forgive others.
 **    May you share freely, never taking more than you give.
 */
package info.ata4.minecraft.minema.client.modules;

import java.util.ArrayList;

import info.ata4.minecraft.minema.client.config.MinemaConfig;
import info.ata4.minecraft.minema.client.util.CaptureTime;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Minema information screen overlay.
 *
 * @author Nico Bergemann <barracuda415 at yahoo.de>
 */
public class CaptureOverlay extends ACaptureModule {

	private static final Minecraft MC = Minecraft.getMinecraft();

	private final CaptureSession session;

	public CaptureOverlay(final MinemaConfig cfg, final CaptureSession session) {
		super(cfg);
		this.session = session;
	}

	@SubscribeEvent
	public void onRenderGameOverlay(final RenderGameOverlayEvent.Text evt) {
		final ArrayList<String> left = evt.left;

		if (MC.gameSettings.showDebugInfo) {
			left.add("");
		}

		final CaptureTime time = this.session.getCaptureTime();

		final String frame = String.valueOf(time.getNumFrames());
		left.add("Frame: " + frame);

		final String fps = Minecraft.getDebugFPS() + " fps";
		left.add("Rate: " + fps);

		final String avg = (int) time.getAverageFPS() + " fps";
		left.add("Avg.: " + avg);

		final String delay = CaptureTime.getTimeUnit(time.getPreviousCaptureTime());
		left.add("Delay: " + delay);

		left.add("Time R: " + time.getRealTimeString());
		left.add("Time V: " + time.getVideoTimeString());
	}

	@Override
	protected void doEnable() throws Exception {
		MinecraftForge.EVENT_BUS.register(this);
	}

	@Override
	protected void doDisable() throws Exception {
		MinecraftForge.EVENT_BUS.unregister(this);
	}
}
