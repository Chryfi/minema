/*
 ** 2014 July 28
 **
 ** The author disclaims copyright to this source code.  In place of
 ** a legal notice, here is a blessing:
 **    May you do good and not evil.
 **    May you find forgiveness for yourself and forgive others.
 **    May you share freely, never taking more than you give.
 */
package info.ata4.minecraft.minema.client.modules.modifiers;

import info.ata4.minecraft.minema.Minema;
import info.ata4.minecraft.minema.client.config.MinemaConfig;
import info.ata4.minecraft.minema.client.engine.FixedTimer;
import info.ata4.minecraft.minema.client.modules.CaptureModule;
import info.ata4.minecraft.minema.client.modules.video.vr.CubeFace;
import info.ata4.minecraft.minema.util.reflection.PrivateAccessor;
import net.minecraft.util.Timer;

/**
 *
 * @author Nico Bergemann <barracuda415 at yahoo.de>
 */
public class TimerModifier extends CaptureModule {

	private static FixedTimer timer = null;
	private float defaultTps;
    
    private static float frameTime;
    private static double frameTimeStep;

    // Called by ASM from EntityRenderer
    public static void doFrameTimeSync() {
        if (timer != null) {
            if (isFirstFrame()) {
                frameTime += frameTimeStep;
                frameTime %= 3600.0;
            }
            PrivateAccessor.setFrameTimeCounter(frameTime);
        }
    }

	public static FixedTimer getTimer() {
		return timer;
	}

	public static boolean isFirstFrame() {
		return timer == null || timer.isFirstFrame();
	}
	
	public static boolean canRecord() {
		return timer == null || timer.canRecord();
	}
	
	public static CubeFace getCubeFace() {
		return timer == null ? null : CubeFace.values()[timer.getCubeFace()];
	}

	@Override
	protected void doEnable() {
		MinemaConfig cfg = Minema.instance.getConfig();

		Timer defaultTimer = PrivateAccessor.getMinecraftTimer(MC);

		// check if it's modified already
		if (defaultTimer instanceof FixedTimer) {
			L.warn("Timer is already modified!");
			return;
		}

		// get default ticks per second if possible
		if (defaultTimer != null) {
			defaultTps = PrivateAccessor.getTimerTicksPerSecond(defaultTimer);
		}

		double fps = cfg.getFrameRate();
		double speed = cfg.engineSpeed.get().doubleValue();
		
        frameTime = PrivateAccessor.getFrameTimeCounter();
        frameTimeStep = speed / fps;

		// set fixed delay timer
		timer = new FixedTimer(defaultTps, fps, speed);
		PrivateAccessor.setMinecraftTimer(MC, timer);
	}

	@Override
	protected boolean checkEnable() {
		return (Minema.instance.getConfig().syncEngine.get() || Minema.instance.getConfig().vr.get()) && MC.isSingleplayer();
	}

	@Override
	protected void doDisable() {
		// check if it's still modified
		if (!(PrivateAccessor.getMinecraftTimer(MC) instanceof FixedTimer)) {
			L.warn("Timer is already restored!");
			return;
		}

		// restore default timer
		timer = null;
		PrivateAccessor.setMinecraftTimer(MC, new Timer(defaultTps));
	}

}
