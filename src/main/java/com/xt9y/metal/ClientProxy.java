package com.xt9y.metal;

import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.FMLCommonHandler;

public final class ClientProxy extends CommonProxy {

    @Override
    public void init() {
        ClientRuntime runtime = ClientRuntime.INSTANCE;
        FMLCommonHandler.instance()
            .bus()
            .register(runtime);
        MinecraftForge.EVENT_BUS.register(runtime);
        ClientCommandHandler.instance.registerCommand(new MetalBenchCommand());
        MetalImprovements.LOG.info(
            "Initialized client optimizations; particle cap {}, background FPS {}, animation interval {}",
            ClientSettings.maxParticlesPerLayer,
            ClientSettings.backgroundFps,
            ClientSettings.animationTickInterval);
    }
}
