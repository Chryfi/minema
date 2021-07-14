/*
 ** 2014 August 01
 **
 ** The author disclaims copyright to this source code.  In place of
 ** a legal notice, here is a blessing:
 **    May you do good and not evil.
 **    May you find forgiveness for yourself and forgive others.
 **    May you share freely, never taking more than you give.
 */
package info.ata4.minecraft.minema.client.modules.modifiers;

import java.io.File;

import info.ata4.minecraft.minema.Minema;
import info.ata4.minecraft.minema.client.config.MinemaConfig;
import info.ata4.minecraft.minema.client.modules.CaptureModule;
import info.ata4.minecraft.minema.util.reflection.PrivateAccessor;
import net.minecraft.client.settings.GameSettings;

/**
 *
 * @author Nico Bergemann <barracuda415 at yahoo.de>
 */
public class GameSettingsModifier extends CaptureModule {

	private int framerateLimit;
	private boolean vSync;
	private boolean pauseOnLostFocus;
	private String lastShaderPack;
	private int renderDistance;

	@Override
	protected void doEnable() throws Exception {
        MinemaConfig cfg = Minema.instance.getConfig();
		GameSettings gs = MC.gameSettings;

		// Minecraft build-in framerate limit
		framerateLimit = gs.limitFramerate;
		if (cfg.syncEngine.get() && cfg.limitFramerate.get())
		    gs.limitFramerate = (int) cfg.getFrameRate() * cfg.heldFrames.get() * (cfg.vr.get() ? 6 : 1);
		else
		    gs.limitFramerate = Integer.MAX_VALUE;

		// disable vSync
		vSync = gs.enableVsync;
		gs.enableVsync = false;

		// don't pause when losing focus
		pauseOnLostFocus = gs.pauseOnLostFocus;
		gs.pauseOnLostFocus = false;
        
		if (PrivateAccessor.isShaderPackSupported()) {
			String pack = cfg.shaderpack.get();
			if (pack != null && !pack.isEmpty()) {
				File packDir = PrivateAccessor.getShaderPacksDir();
				if (packDir != null && new File(packDir, pack).exists() || pack.equals("OFF") || pack.equals("(internal)")) {
					String last = PrivateAccessor.getCurrentShaderName();
					if (!pack.equals(last)) {
					    lastShaderPack = last;
	                    PrivateAccessor.setShaderPack(pack);
	                    PrivateAccessor.uninitShaderPack();
					}
				}
			}
		}
		
		renderDistance = gs.renderDistanceChunks;
		int dist = cfg.renderDistance.get();
		if (dist > 0) {
		    gs.renderDistanceChunks = dist;
		    if (MC.getIntegratedServer() != null)
		        MC.getIntegratedServer().getPlayerList().setViewDistance(dist);
		}
	}

	@Override
	protected void doDisable() throws Exception {
		// restore everything
		GameSettings gs = MC.gameSettings;
		gs.limitFramerate = framerateLimit;
		gs.pauseOnLostFocus = pauseOnLostFocus;
		gs.enableVsync = vSync;
		
		if (lastShaderPack != null) {
			PrivateAccessor.setShaderPack(lastShaderPack);
			PrivateAccessor.uninitShaderPack();
			lastShaderPack = null;
		}
		
        gs.renderDistanceChunks = renderDistance;
	}

	@Override
	protected boolean checkEnable() {
		return true;
	}

}
