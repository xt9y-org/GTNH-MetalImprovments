package com.xt9y.metal;

import java.util.Arrays;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import com.xt9y.metal.bootstrap.MetalBootstrap;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class ClientRuntime {

    public static final ClientRuntime INSTANCE = new ClientRuntime();

    private final long[] frameTimes = new long[240];
    private int samples;
    private int nextSample;
    private long startNanos;
    private double averageMillis;
    private double p95Millis;
    private String stats = "collecting frames";
    private long rejectedParticles;
    private boolean backendChecked;
    private String backendName = "unknown";

    private ClientRuntime() {}

    public void rejectedParticle() {
        rejectedParticles++;
    }

    public boolean slowRendering() {
        return samples >= 60 && averageMillis > 25.0;
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.END) MetalBootstrap.markRenderedFrame();
        if (!ClientSettings.adaptiveBudget && !ClientSettings.showF3Statistics) {
            return;
        }

        if (event.phase == TickEvent.Phase.START) {
            startNanos = System.nanoTime();
        } else if (startNanos != 0L) {
            long duration = System.nanoTime() - startNanos;
            startNanos = 0L;
            if (duration < 0L || duration > 1_000_000_000L) {
                return;
            }

            frameTimes[nextSample] = duration;
            nextSample = (nextSample + 1) % frameTimes.length;
            if (samples < frameTimes.length) {
                samples++;
            }

            // Sort a small fixed-size buffer only once every 60 rendered frames.
            if (nextSample % 60 == 0 && samples != 0) {
                long[] sorted = Arrays.copyOf(frameTimes, samples);
                Arrays.sort(sorted);
                long total = 0L;
                for (long value : sorted) {
                    total += value;
                }
                averageMillis = total / (samples * 1_000_000.0);
                p95Millis = sorted[(int) Math.ceil(samples * 0.95) - 1] / 1_000_000.0;
                stats = String.format(
                    Locale.ROOT,
                    "GTNH Metal: avg %.2f ms, p95 %.2f ms (CPU render tick)",
                    averageMillis,
                    p95Millis);
            }
        }
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Text event) {
        if (!ClientSettings.showF3Statistics) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null || !mc.gameSettings.showDebugInfo) {
            return;
        }

        Runtime runtime = Runtime.getRuntime();
        long used = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L);
        long committed = runtime.totalMemory() / (1024L * 1024L);
        long max = runtime.maxMemory() / (1024L * 1024L);

        event.left.add(stats);
        event.left.add(
            "GTNH Metal: heap " + used
                + "/"
                + committed
                + "/"
                + max
                + " MiB used/committed/max; rejected particles "
                + rejectedParticles);
        if (!backendChecked) {
            backendChecked = true;
            if (Loader.isModLoaded("angelica")) {
                try {
                    Class<?> glsm = Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager");
                    backendName = String.valueOf(
                        glsm.getMethod("getRenderBackendName")
                            .invoke(null));
                } catch (ReflectiveOperationException | LinkageError e) {
                    backendName = "not detectable (" + e.getClass()
                        .getSimpleName() + ")";
                }
            } else {
                backendName = "Angelica absent";
            }
        }
        event.left.add("GTNH Metal: active renderer " + backendName);
        event.left.add("GTNH Metal: bootstrap " + MetalBootstrap.status());
    }
}
