package info.ata4.minecraft.minema.client.modules.video;

import info.ata4.minecraft.minema.Minema;
import info.ata4.minecraft.minema.client.modules.modifiers.TimerModifier;

public class VideoHandler extends AbstractVideoHandler {

	@Override
	protected void updateDepth() throws Exception {
		if (!TimerModifier.canRecord())
			return;
		
		this.exportDepth();
	}

	@Override
	protected boolean doExport() throws Exception {
		if (!TimerModifier.canRecord())
			return false;
		
		this.exportColor();
		return true;
	}

	@Override
	protected boolean checkEnable() {
		return !Minema.instance.getConfig().vr.get() || !MC.isSingleplayer();
	}

}
