package com.xt9y.metal;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public final class ClientSettings {

    public static boolean particleBudget = true;
    public static int maxParticlesPerLayer = 3072;
    public static int maxParticleDistance = 0;
    public static boolean adaptiveBudget = false;
    public static int backgroundFps = 20;
    public static int animationTickInterval = 1;
    public static boolean showF3Statistics = false;

    private ClientSettings() {}

    public static void load(File file) {
        Configuration config = new Configuration(file);
        try {
            config.load();
            particleBudget = config
                .get(
                    "particles",
                    "enabled",
                    true,
                    "Apply an upper bound to particle lists to reduce CPU work and heap use under load.")
                .getBoolean(true);
            maxParticlesPerLayer = config.getInt(
                "maxPerLayer",
                "particles",
                3072,
                128,
                4000,
                "Maximum number of particles in each vanilla effect layer (vanilla: 4000).");
            maxParticleDistance = config.getInt(
                "maxDistanceBlocks",
                "particles",
                0,
                0,
                512,
                "Reject particles farther from the player than this distance; 0 disables distance culling.");
            adaptiveBudget = config
                .get(
                    "particles",
                    "adaptiveBudget",
                    false,
                    "Reduce the particle cap during sustained slow rendering. May cause more particles to disappear.")
                .getBoolean(false);
            backgroundFps = config.getInt(
                "backgroundFps",
                "display",
                20,
                0,
                120,
                "Approximate FPS cap when the game is unfocused or hidden; 0 disables it.");
            animationTickInterval = config.getInt(
                "animationTickInterval",
                "textures",
                1,
                1,
                8,
                "Call the animated texture atlas update once per N ticks. Values above 1 slow texture animations.");
            showF3Statistics = config
                .get(
                    "diagnostics",
                    "showF3Statistics",
                    false,
                    "Show client frametime and heap estimates on the F3 debug overlay.")
                .getBoolean(false);
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }

    public static int effectiveParticleCap() {
        int cap = maxParticlesPerLayer;
        if (adaptiveBudget && ClientRuntime.INSTANCE.slowRendering()) {
            cap = Math.max(512, cap / 2);
        }
        return cap;
    }
}
