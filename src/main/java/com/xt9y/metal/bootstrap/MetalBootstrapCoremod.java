package com.xt9y.metal.bootstrap;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

@IFMLLoadingPlugin.Name("GTNHMetalBootstrap")
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.SortingIndex(-10000)
@IFMLLoadingPlugin.TransformerExclusions({ "com.xt9y.metal.bootstrap" })
public final class MetalBootstrapCoremod implements IFMLLoadingPlugin {

    public MetalBootstrapCoremod() {
        MetalBootstrap.apply();
    }

    @Override
    public String[] getASMTransformerClass() {
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
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
