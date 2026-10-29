package com.xt9y.metal;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import org.lwjgl.opengl.Display;

/** One command, one launch, one read-only capture of the currently active backend. */
public final class MetalBenchmark {

    public static final MetalBenchmark INSTANCE = new MetalBenchmark();

    private static final int MAX_FRAMES = 120_000;
    private static final long WARMUP_NANOS = 5_000_000_000L;

    private final long[] frameIntervals = new long[MAX_FRAMES];
    private final long[] cpuTicks = new long[MAX_FRAMES];
    private final long[] gpuWaits = new long[MAX_FRAMES];
    private final long[] presentGates = new long[MAX_FRAMES];
    private final long[] passTotals = new long[AngelicaFrameProbe.metricNames().length];
    private final long[] passEndCauses = new long[8];
    private final GcEventSampler gcEvents = new GcEventSampler();

    private static final int MAX_MEMORY_SAMPLES = 130;
    private final long[] sampleSecond = new long[MAX_MEMORY_SAMPLES];
    private final long[] sampleHeap = new long[MAX_MEMORY_SAMPLES];
    private final long[] sampleDirect = new long[MAX_MEMORY_SAMPLES];
    private final long[] sampleGcMillis = new long[MAX_MEMORY_SAMPLES];
    private final long[] sampleGcCount = new long[MAX_MEMORY_SAMPLES];
    private int memorySamples;

    private Method restoreVSync;
    private Object previousVSync;
    private boolean temporaryVSyncChanged;
    private long focusPausedAt;
    private long totalFocusPausedNanos;
    private int focusPauseEvents;

    private long startedNanos;
    private long measuringSince;
    private long durationNanos;
    private long lastMemorySample;
    private long initialGcMillis;
    private long initialGcCount;
    private long initialHeap;
    private long initialDirect;
    private long peakHeap;
    private long peakDirect;
    private long lastHeap;
    private long lastDirect;
    private long lastGcMillis;
    private long lastGcCount;
    private long lastPassGeneration = -1L;
    private long totalPasses;
    private long samplePassFrames;
    private long passGpuWait;
    private long passAcquireWait;
    private int samples;
    private String backend;
    private String worldInfo;
    private String gameSettings;
    private String warning = "";
    private Thread renderThread;
    private int state; // 0 idle, 1 warming up, 2 capturing
    private int seconds;
    private long lastPositionCheck;
    private double startX;
    private double startY;
    private double startZ;
    private float startYaw;
    private float startPitch;

    private MetalBenchmark() {}

    public boolean active() {
        return state != 0;
    }

    public boolean isCapturing() {
        return state == 2;
    }

    public Thread renderThread() {
        return renderThread;
    }

    public String status() {
        if (state == 0) return "No benchmark running";
        if (focusPausedAt != 0L) return "Paused: Minecraft unfocused; resumes automatically";
        if (state == 1) {
            long remaining = Math.max(0, (WARMUP_NANOS - (System.nanoTime() - startedNanos)) / 1_000_000_000L);
            return "Warming up (" + remaining + "s remaining)";
        }
        return "Capturing " + samples
            + " frames ("
            + Math.min(seconds, (System.nanoTime() - measuringSince) / 1_000_000_000L)
            + "/"
            + seconds
            + "s)";
    }

    public String start(int captureSeconds, String renderer) {
        if (active()) return "Already running: " + status();
        if (captureSeconds < 10 || captureSeconds > 120) return "Duration must be 10-120 seconds";
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return "Join a world before benchmarking";
        Arrays.fill(passTotals, 0L);
        Arrays.fill(passEndCauses, 0L);
        samples = 0;
        memorySamples = 0;
        restoreVSync = null;
        previousVSync = null;
        temporaryVSyncChanged = false;
        focusPausedAt = 0L;
        totalFocusPausedNanos = 0L;
        focusPauseEvents = 0;
        totalPasses = 0L;
        samplePassFrames = 0L;
        passGpuWait = 0L;
        passAcquireWait = 0L;
        lastPassGeneration = -1L;
        warning = "";
        backend = renderer;
        seconds = captureSeconds;
        durationNanos = captureSeconds * 1_000_000_000L;
        startedNanos = System.nanoTime();
        measuringSince = 0L;
        renderThread = Thread.currentThread();
        state = 1;
        startX = mc.thePlayer.posX;
        startY = mc.thePlayer.posY;
        startZ = mc.thePlayer.posZ;
        startYaw = mc.thePlayer.rotationYaw;
        startPitch = mc.thePlayer.rotationPitch;
        worldInfo = "dimension=" + mc.thePlayer.dimension
            + " position="
            + String.format(Locale.ROOT, "%.2f,%.2f,%.2f", startX, startY, startZ);
        gameSettings = "renderDistanceChunks=" + mc.gameSettings.renderDistanceChunks
            + " maxFramerate="
            + mc.gameSettings.limitFramerate
            + " vsync="
            + mc.gameSettings.enableVsync;
        if (mc.gameSettings.limitFramerate > 0 && mc.gameSettings.limitFramerate < 260)
            warning += "Configured FPS cap bypassed temporarily; ";
        disableVSyncTemporarily();
        return "Uncapped foreground benchmark started: 5s warmup + " + captureSeconds
            + "s capture. Original VSync settings will be restored. Backend: "
            + renderer;
    }

    private static long[] memory() {
        Runtime rt = Runtime.getRuntime();
        long heap = rt.totalMemory() - rt.freeMemory();
        long direct = 0L;
        for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            if ("direct".equals(pool.getName())) direct += Math.max(0, pool.getMemoryUsed());
        }
        long gcMillis = 0L;
        long gcCount = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (bean.getCollectionTime() > 0) gcMillis += bean.getCollectionTime();
            if (bean.getCollectionCount() > 0) gcCount += bean.getCollectionCount();
        }
        return new long[] { heap, direct, gcMillis, gcCount };
    }

    private void sampleMemory(long now, boolean baseline) {
        long[] memory = memory();
        lastHeap = memory[0];
        lastDirect = memory[1];
        lastGcMillis = memory[2];
        lastGcCount = memory[3];
        if (baseline) {
            initialHeap = lastHeap;
            initialDirect = lastDirect;
            initialGcMillis = lastGcMillis;
            initialGcCount = lastGcCount;
            peakHeap = lastHeap;
            peakDirect = lastDirect;
        }
        peakHeap = Math.max(peakHeap, lastHeap);
        peakDirect = Math.max(peakDirect, lastDirect);
        lastMemorySample = now;
        if (!baseline && state == 2 && memorySamples < MAX_MEMORY_SAMPLES) {
            int i = memorySamples++;
            sampleSecond[i] = Math.max(0L, (now - measuringSince) / 1_000_000_000L);
            sampleHeap[i] = lastHeap;
            sampleDirect[i] = lastDirect;
            sampleGcMillis[i] = Math.max(0L, lastGcMillis - initialGcMillis);
            sampleGcCount[i] = Math.max(0L, lastGcCount - initialGcCount);
        }
    }

    public void recordPassStats(long generation, int[] metrics, int[] causes, long gpuWait, long acquireWait) {
        if (state != 2 || focusPausedAt != 0L) return;
        if (lastPassGeneration >= 0 && generation >= lastPassGeneration) totalPasses += generation - lastPassGeneration;
        lastPassGeneration = generation;
        for (int i = 0; i < Math.min(metrics.length, passTotals.length); i++) passTotals[i] += metrics[i];
        for (int i = 0; i < Math.min(causes.length, passEndCauses.length); i++) passEndCauses[i] += causes[i];
        passGpuWait += Math.max(0L, gpuWait);
        passAcquireWait += Math.max(0L, acquireWait);
        samplePassFrames++;
    }

    public void record(long now, long wallNanos, long cpuNanos, long gpuWait, long presentGate) {
        if (state == 0) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            warning += "World unloaded during benchmark; ";
            finish(true);
            return;
        }

        // The normal background limiter stays enabled, but unfocused time never
        // contaminates the FPS and P99 data; the capture resumes automatically.
        boolean foreground = Display.isCreated() && Display.isActive() && Display.isVisible();
        if (!foreground) {
            if (focusPausedAt == 0L) {
                focusPausedAt = now;
                focusPauseEvents++;
            }
            return;
        }
        if (focusPausedAt != 0L) {
            long delta = Math.max(0L, now - focusPausedAt);
            totalFocusPausedNanos += delta;
            startedNanos += delta;
            if (measuringSince != 0L) measuringSince += delta;
            if (lastMemorySample != 0L) lastMemorySample += delta;
            if (lastPositionCheck != 0L) lastPositionCheck += delta;
            focusPausedAt = 0L;
            // The first interval after focus returns includes background time.
            return;
        }

        if (state == 1) {
            if (now - startedNanos < WARMUP_NANOS) return;
            state = 2;
            AngelicaProbeGate.enabled = true;
            measuringSince = now;
            lastPositionCheck = now;
            sampleMemory(now, true);
            gcEvents.begin();
            sendChat("Warmup finished. Recording " + seconds + "s.");
            return;
        }

        if (wallNanos > 0L && wallNanos < 1_000_000_000L && cpuNanos > 0L && cpuNanos < 1_000_000_000L) {
            if (samples < MAX_FRAMES) {
                frameIntervals[samples] = wallNanos;
                cpuTicks[samples] = cpuNanos;
                gpuWaits[samples] = Math.max(0L, gpuWait);
                presentGates[samples] = Math.max(0L, presentGate);
                samples++;
            }
        }

        if (now - lastMemorySample >= 1_000_000_000L) sampleMemory(now, false);
        if (now - lastPositionCheck >= 1_000_000_000L) {
            lastPositionCheck = now;
            double dx = mc.thePlayer.posX - startX;
            double dy = mc.thePlayer.posY - startY;
            double dz = mc.thePlayer.posZ - startZ;
            if (dx * dx + dy * dy + dz * dz > 1.0D || Math.abs(mc.thePlayer.rotationYaw - startYaw) > 3.0F
                || Math.abs(mc.thePlayer.rotationPitch - startPitch) > 3.0F) {
                if (!warning.contains("Camera moved")) warning += "Camera moved; ";
            }
        }

        if (now - measuringSince >= durationNanos || samples >= MAX_FRAMES) sendChat(finish(false));
    }

    public String stop() {
        if (!active()) return "No benchmark running";
        return finish(true);
    }

    /**
     * Override only the running present mode, not the persisted video settings.
     * The benchmark cap mixin bypasses the ordinary FPS limit independently.
     */
    private void disableVSyncTemporarily() {
        try {
            Class<?> manager = Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager");
            Class<?> modeType = Class.forName("com.gtnewhorizons.angelica.glsm.backend.VSyncMode");
            Method getMode = manager.getMethod("getEffectiveVSyncMode");
            previousVSync = getMode.invoke(null);
            if (previousVSync == null) {
                warning += "VSync mode unavailable; ";
                return;
            }
            if (!"OFF".equals(String.valueOf(previousVSync))) {
                @SuppressWarnings({ "rawtypes", "unchecked" })
                Object off = Enum.valueOf((Class) modeType.asSubclass(Enum.class), "OFF");
                restoreVSync = manager.getMethod("setVSyncMode", modeType);
                restoreVSync.invoke(null, off);
                temporaryVSyncChanged = true;
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            warning += "Could not temporarily disable VSync; ";
            MetalImprovements.LOG.warn("Failed to disable VSync for benchmark", e);
        }
    }

    private void restoreVSyncMode() {
        if (!temporaryVSyncChanged || restoreVSync == null || previousVSync == null) return;
        try {
            restoreVSync.invoke(null, previousVSync);
        } catch (ReflectiveOperationException | LinkageError e) {
            MetalImprovements.LOG.warn("Could not restore original VSync mode", e);
        } finally {
            temporaryVSyncChanged = false;
            previousVSync = null;
            restoreVSync = null;
        }
    }

    private static long percentile(long[] sorted, double percent) {
        if (sorted.length == 0) return 0L;
        return sorted[Math.max(0, (int) Math.ceil(sorted.length * percent) - 1)];
    }

    private static long sum(long[] values) {
        long result = 0L;
        for (long value : values) result += value;
        return result;
    }

    private String finish(boolean interrupted) {
        if (state == 0) return "No benchmark running";
        if (focusPausedAt != 0L) {
            long delta = Math.max(0L, System.nanoTime() - focusPausedAt);
            totalFocusPausedNanos += delta;
            if (measuringSince != 0L) measuringSince += delta;
            focusPausedAt = 0L;
        }
        AngelicaProbeGate.enabled = false;
        state = 0;
        GcEventSampler.Event[] gcNotifications = gcEvents.end();
        restoreVSyncMode();
        if (samples == 0) return "Capture ended without any frames.";

        sampleMemory(System.nanoTime(), false);
        final int count = samples;
        final long[] wall = Arrays.copyOf(frameIntervals, count);
        final long[] cpu = Arrays.copyOf(cpuTicks, count);
        final long[] waits = Arrays.copyOf(gpuWaits, count);
        final long[] gates = Arrays.copyOf(presentGates, count);
        final long[] passCopy = passTotals.clone();
        final long[] reasonCopy = passEndCauses.clone();
        final GcEventSampler.Event[] gcNotificationsCopy = gcNotifications;
        final long startNanosCopy = measuringSince;
        final long focusPausedNanosCopy = totalFocusPausedNanos;
        final int focusEventsCopy = focusPauseEvents;
        final double focusSecondsCopy = totalFocusPausedNanos / 1_000_000_000.0;
        final int memorySampleCount = memorySamples;
        final long[] secondsCopy = Arrays.copyOf(sampleSecond, memorySampleCount);
        final long[] heapsCopy = Arrays.copyOf(sampleHeap, memorySampleCount);
        final long[] directsCopy = Arrays.copyOf(sampleDirect, memorySampleCount);
        final long[] gcTimeCopy = Arrays.copyOf(sampleGcMillis, memorySampleCount);
        final long[] gcCountCopy = Arrays.copyOf(sampleGcCount, memorySampleCount);
        final String backendCopy = backend;
        final String worldCopy = worldInfo;
        final String settingsCopy = gameSettings;
        final String warningCopy = warning + (interrupted ? "Capture interrupted; " : "");
        final long heapStart = initialHeap, directStart = initialDirect, heapPeak = peakHeap, directPeak = peakDirect,
            heapEnd = lastHeap, directEnd = lastDirect;
        final long gcTime = Math.max(0L, lastGcMillis - initialGcMillis);
        final long gcCount = Math.max(0L, lastGcCount - initialGcCount);
        final long passCount = totalPasses, passFrames = samplePassFrames;
        final long gpuWaitCopy = passGpuWait, acquireWaitCopy = passAcquireWait;
        final double elapsed = (System.nanoTime() - measuringSince) / 1_000_000_000.0;
        final String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        final File output = new File(
            new File(Minecraft.getMinecraft().mcDataDir, "logs"),
            "xt9ymetal-bench-" + timestamp + ".csv");

        Thread writer = new Thread(() -> {
            try {
                File dir = output.getParentFile();
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
                long[] sortedWall = wall.clone();
                long[] sortedCpu = cpu.clone();
                Arrays.sort(sortedWall);
                Arrays.sort(sortedCpu);
                try (BufferedWriter out = new BufferedWriter(new FileWriter(output))) {
                    out.write("metric,value\n");
                    out.write("backend," + backendCopy.replace(',', ' ') + "\n");
                    out.write("scene," + worldCopy.replace(',', ';') + "\n");
                    out.write("settings," + settingsCopy + "\n");
                    out.write("benchmark_cap_mode,temporarily_uncapped\n");
                    out.write(String.format(Locale.ROOT, "focus_paused_seconds,%.3f%n", focusSecondsCopy));
                    out.write("focus_pause_events," + focusEventsCopy + "\n");
                    out.write("focus_policy,foreground_only_automatic_pause\n");
                    out.write("benchmark_vsync_mode,temporarily_disabled_when_supported\n");
                    out.write("warnings," + warningCopy.replace(',', ';') + "\n");
                    out.write(String.format(Locale.ROOT, "duration_seconds,%.3f%n", elapsed));
                    out.write("frames," + count + "\n");
                    out.write(
                        String.format(
                            Locale.ROOT,
                            "fps_estimate,%.3f%n",
                            count * 1_000_000_000.0 / Math.max(1L, sum(wall))));
                    out.write(String.format(Locale.ROOT, "wall_average_ms,%.4f%n", sum(wall) / (count * 1_000_000.0)));
                    out.write(
                        String.format(Locale.ROOT, "wall_p95_ms,%.4f%n", percentile(sortedWall, 0.95) / 1_000_000.0));
                    out.write(
                        String.format(Locale.ROOT, "wall_p99_ms,%.4f%n", percentile(sortedWall, 0.99) / 1_000_000.0));
                    out.write(String.format(Locale.ROOT, "cpu_average_ms,%.4f%n", sum(cpu) / (count * 1_000_000.0)));
                    out.write(
                        String.format(Locale.ROOT, "cpu_p95_ms,%.4f%n", percentile(sortedCpu, 0.95) / 1_000_000.0));
                    out.write(
                        String.format(Locale.ROOT, "cpu_p99_ms,%.4f%n", percentile(sortedCpu, 0.99) / 1_000_000.0));
                    out.write("gc_collections," + gcCount + "\n");
                    out.write("gc_notifications," + gcNotificationsCopy.length + "\n");
                    out.write("gc_collection_millis," + gcTime + "\n");
                    out.write("heap_start_mib," + (heapStart / 1048576L) + "\n");
                    out.write("heap_end_mib," + (heapEnd / 1048576L) + "\n");
                    out.write("heap_peak_mib," + (heapPeak / 1048576L) + "\n");
                    out.write("direct_start_mib," + (directStart / 1048576L) + "\n");
                    out.write("direct_end_mib," + (directEnd / 1048576L) + "\n");
                    out.write("direct_peak_mib," + (directPeak / 1048576L) + "\n");
                    out.write("angelica_pass_metric_frames," + passFrames + "\n");
                    out.write("angelica_render_passes," + passCount + "\n");
                    out.write(
                        String.format(
                            Locale.ROOT,
                            "angelica_render_passes_per_frame,%.3f%n",
                            passCount / (double) Math.max(1, passFrames)));
                    out.write(
                        String.format(
                            Locale.ROOT,
                            "angelica_pass_gpu_wait_avg_ms,%.4f%n",
                            gpuWaitCopy / (Math.max(1L, passFrames) * 1_000_000.0)));
                    out.write(
                        String.format(
                            Locale.ROOT,
                            "angelica_pass_acquire_wait_avg_ms,%.4f%n",
                            acquireWaitCopy / (Math.max(1L, passFrames) * 1_000_000.0)));
                    String[] metricNames = AngelicaFrameProbe.metricNames();
                    for (int i = 0; i < passCopy.length; i++)
                        out.write("angelica_" + metricNames[i] + "," + passCopy[i] + "\n");
                    String[] causes = { "clear", "compute", "copy", "target", "uniform_block", "frame_end", "logic_op",
                        "readback" };
                    for (int i = 0; i < Math.min(causes.length, reasonCopy.length); i++)
                        out.write("angelica_pass_end_" + causes[i] + "," + reasonCopy[i] + "\n");
                    out.write("\nsecond,heap_mib,direct_mib,gc_count_delta,gc_millis_delta\n");
                    for (int i = 0; i < memorySampleCount; i++) {
                        out.write(
                            String.format(
                                Locale.ROOT,
                                "%d,%d,%d,%d,%d%n",
                                secondsCopy[i],
                                heapsCopy[i] / 1048576L,
                                directsCopy[i] / 1048576L,
                                gcCountCopy[i],
                                gcTimeCopy[i]));
                    }
                    out.write("\nGC_notification_received_second,gc_duration_ms,gc_name,gc_cause\n");
                    for (GcEventSampler.Event event : gcNotificationsCopy) {
                        double received = Math
                            .max(0.0, (event.receivedNanos - startNanosCopy - focusPausedNanosCopy) / 1_000_000_000.0);
                        out.write(
                            String.format(
                                Locale.ROOT,
                                "%.4f,%d,%s,%s%n",
                                received,
                                event.durationMillis,
                                event.name.replace(',', ' '),
                                event.cause.replace(',', ' ')));
                    }
                    out.write("\nframe,wall_ms,cpu_render_tick_ms,pacer_gpu_wait_ms,present_gate_ms\n");
                    for (int i = 0; i < count; i++) {
                        out.write(
                            String.format(
                                Locale.ROOT,
                                "%d,%.4f,%.4f,%.4f,%.4f%n",
                                i + 1,
                                wall[i] / 1_000_000.0,
                                cpu[i] / 1_000_000.0,
                                waits[i] / 1_000_000.0,
                                gates[i] / 1_000_000.0));
                    }
                }
                MetalImprovements.LOG.info("Completed GTNH Metal benchmark: {}", output.getAbsolutePath());
            } catch (IOException e) {
                MetalImprovements.LOG.error("Failed to write GTNH Metal benchmark", e);
            }
        }, "GTNHMetal-BenchmarkWriter");
        writer.setDaemon(false);
        writer.start();

        return "Recorded " + count
            + " frames ("
            + String.format(Locale.ROOT, "%.0f", elapsed)
            + "s). Report: logs/"
            + output.getName();
    }

    private static void sendChat(String message) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText("[GTNH Metal] " + message));
    }
}
