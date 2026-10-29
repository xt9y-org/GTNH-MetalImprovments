package com.xt9y.metal;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import com.xt9y.metal.bootstrap.MetalBootstrap;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class ClientRuntime {

    public static final ClientRuntime INSTANCE = new ClientRuntime();

    private final TimingWindow wallFrames = new TimingWindow();
    private final TimingWindow cpuTicks = new TimingWindow();
    private long lastFrameStart;
    private long frameStart;
    private int reportCounter;
    private long rejectedParticles;
    private String backendName;
    private String wallSummary = "GTNH Metal: collecting frame intervals";
    private String cpuSummary = "GTNH Metal: collecting CPU ticks";
    private String memorySummary = "GTNH Metal: collecting memory";
    private String pacerSummary = "";
    private double averageCpuMillis;

    private Method gpuWaitMethod;
    private Method gateMethod;
    private boolean pacerChecked;

    private ClientRuntime() {}

    public void rejectedParticle() {
        rejectedParticles++;
    }

    public boolean slowRendering() {
        return cpuTicks.samples() >= 60 && averageCpuMillis > 25.0;
    }

    public String activeBackend() {
        if (backendName != null) return backendName;
        if (!Loader.isModLoaded("angelica")) {
            backendName = "Angelica absent";
            return backendName;
        }
        try {
            Class<?> glsm = Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager");
            backendName = String.valueOf(
                glsm.getMethod("getRenderBackendName")
                    .invoke(null));
        } catch (ReflectiveOperationException | LinkageError e) {
            backendName = "unknown (" + e.getClass()
                .getSimpleName() + ")";
        }
        return backendName;
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            if (!ClientSettings.adaptiveBudget && !ClientSettings.showF3Statistics && !MetalBenchmark.INSTANCE.active())
                return;
            long now = System.nanoTime();
            wallFramesLastSample = lastFrameStart > 0L ? now - lastFrameStart : 0L;
            if (lastFrameStart > 0L) wallFrames.record(wallFramesLastSample);
            lastFrameStart = now;
            frameStart = now;
            return;
        }

        // The backend verification is always active, regardless of diagnostics settings.
        MetalBootstrap.markRenderedFrame();
        if (frameStart == 0L) return;
        long now = System.nanoTime();
        long renderNanos = now - frameStart;
        long wallNanos = wallFramesLastSample;
        frameStart = 0L;
        if (renderNanos <= 0L || renderNanos > 1_000_000_000L) return;
        cpuTicks.record(renderNanos);
        if (MetalBenchmark.INSTANCE.active()) {
            initPacer();
            MetalBenchmark.INSTANCE
                .record(now, wallNanos, renderNanos, pacerValue(gpuWaitMethod), pacerValue(gateMethod));
        }

        if (++reportCounter >= 120) {
            reportCounter = 0;
            updateSummaries();
        }
    }

    private static long pacerValue(Method method) {
        if (method == null) return 0L;
        try {
            return ((Number) method.invoke(null)).longValue();
        } catch (ReflectiveOperationException | LinkageError e) {
            return 0L;
        }
    }

    private long wallFramesLastSample;

    private void updateSummaries() {
        TimingWindow.Snapshot wall = wallFrames.snapshot();
        TimingWindow.Snapshot cpu = cpuTicks.snapshot();
        averageCpuMillis = cpu.avgMillis;
        wallSummary = String.format(
            Locale.ROOT,
            "GTNH Metal: frame interval avg %.2f / p95 %.2f / p99 %.2f ms, >33ms %d/%d",
            wall.avgMillis,
            wall.p95Millis,
            wall.p99Millis,
            wall.slowFrames,
            wall.samples);
        cpuSummary = String.format(
            Locale.ROOT,
            "GTNH Metal: CPU tick avg %.2f / p95 %.2f / p99 %.2f ms (not GPU time)",
            cpu.avgMillis,
            cpu.p95Millis,
            cpu.p99Millis);

        if (!ClientSettings.showF3Statistics) return;
        Runtime rt = Runtime.getRuntime();
        long heap = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
        long direct = 0L;
        for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            if ("direct".equals(pool.getName())) direct = pool.getMemoryUsed() / 1048576L;
        }
        long gcCount = 0L;
        long gcMillis = 0L;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gc.getCollectionCount() > 0) gcCount += gc.getCollectionCount();
            if (gc.getCollectionTime() > 0) gcMillis += gc.getCollectionTime();
        }
        memorySummary = "GTNH Metal: heap " + heap
            + " MiB, direct "
            + direct
            + " MiB, GC "
            + gcCount
            + " / "
            + gcMillis
            + " ms since start, particles rejected "
            + rejectedParticles;

        initPacer();
        if (gpuWaitMethod != null && gateMethod != null) {
            try {
                long gpuWait = ((Number) gpuWaitMethod.invoke(null)).longValue();
                long gate = ((Number) gateMethod.invoke(null)).longValue();
                pacerSummary = String.format(
                    Locale.ROOT,
                    "GTNH Metal: Angelica pacer GPU wait %.2f ms, present gate %.2f ms (CPU waits)",
                    gpuWait / 1_000_000.0,
                    gate / 1_000_000.0);
            } catch (ReflectiveOperationException e) {
                pacerSummary = "";
            }
        }
    }

    private void initPacer() {
        if (pacerChecked) return;
        pacerChecked = true;
        if (!Loader.isModLoaded("angelica")) return;
        try {
            Class<?> pacer = Class.forName("com.gtnewhorizons.angelica.rendering.FramePacer");
            gpuWaitMethod = pacer.getMethod("gpuWaitNanos");
            gateMethod = pacer.getMethod("gateDurationNanos");
        } catch (ReflectiveOperationException | LinkageError e) {
            gpuWaitMethod = null;
            gateMethod = null;
        }
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Text event) {
        if (!ClientSettings.showF3Statistics) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || !mc.gameSettings.showDebugInfo) return;
        event.left.add(wallSummary);
        event.left.add(cpuSummary);
        event.left.add(memorySummary);
        if (!pacerSummary.isEmpty()) event.left.add(pacerSummary);
        event.left.add("GTNH Metal: active renderer " + activeBackend());
        event.left.add("GTNH Metal: bootstrap " + MetalBootstrap.status());
        if (MetalBenchmark.INSTANCE.active()) {
            event.left.add("GTNH Metal: " + MetalBenchmark.INSTANCE.status());
        }
    }
}
