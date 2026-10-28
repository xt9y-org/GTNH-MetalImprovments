package com.xt9y.metal.bootstrap;

import com.gtnewhorizons.retrofuturabootstrap.api.PluginContext;
import com.gtnewhorizons.retrofuturabootstrap.api.RetroFuturaBootstrap;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbClassTransformer;
import com.gtnewhorizons.retrofuturabootstrap.api.RfbPlugin;

public final class MetalRfbPlugin implements RfbPlugin {

    @Override
    public void onConstruction(PluginContext context) {
        // Angelica only uses SDL GPU on clients. Never request it on a server.
        if (RetroFuturaBootstrap.API.launchClassLoader()
            .findClassMetadata("net.minecraft.client.main.Main") != null) {
            MetalBootstrap.apply();
        }
    }

    @Override
    public RfbClassTransformer[] makeTransformers() {
        return null;
    }
}
