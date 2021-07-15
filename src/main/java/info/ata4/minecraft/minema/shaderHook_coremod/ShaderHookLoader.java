package info.ata4.minecraft.minema.shaderHook_coremod;

import java.util.Map;

import info.ata4.minecraft.minema.Minema;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import net.minecraftforge.fml.relauncher.Side;

@IFMLLoadingPlugin.MCVersion(value = Minema.MCVERSION)
@IFMLLoadingPlugin.SortingIndex(1000)
public final class ShaderHookLoader implements IFMLLoadingPlugin {

	@Override
	public String[] getASMTransformerClass() {
	    if (FMLLaunchHandler.side() == Side.CLIENT)
	        return new String[] { ShaderHookInjector.class.getName() };
	    return null;
	}

	@Override
	public String getModContainerClass() {
		return null;
	}

	@Override
	public String getSetupClass() {
		return null;
	}

	@Override
	public void injectData(final Map<String, Object> data) {
	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}

}
