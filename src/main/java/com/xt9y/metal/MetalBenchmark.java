package com.xt9y.metal;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

public final class MetalBenchmark {

    public static final MetalBenchmark INSTANCE = new MetalBenchmark();
    private static final int MAX_FRAMES = 120_000;

    private final long[] frameIntervals = new long[MAX_FRAMES];
    private final long[] cpuTicks = new long[MAX_FRAMES];
    private long started;
    private long durationNanos;
    private int frames;
    private String backend;
    private boolean active;

    private MetalBenchmark() {}

    public boolean active() {
        return active;
    }

    public String status() {
        if (!active) return "No benchmark running";
        return "Recording " + frames + " frames (" + ((System.nanoTime() - started) / 1_000_000_000L) + "s)";
    }

    public String start(int seconds, String renderer) {
        if (active) return "Benchmark already running: " + status();
        if (seconds < 10 || seconds > 120) return "Duration must be 10-120 seconds";
        if (Minecraft.getMinecraft().theWorld == null) return "Join a world before benchmarking";
        started = System.nanoTime();
        durationNanos = seconds * 1_000_000_000L;
        frames = 0;
        backend = renderer;
        active = true;
        return "Capturing " + seconds + "s of frame intervals and CPU render ticks. Don't move the camera.";
    }

    public void record(long now, long wallNanos, long cpuNanos) {
        if (!active) return;
        if (wallNanos > 0L && wallNanos < 1_000_000_000L && cpuNanos > 0L) {
            frameIntervals[frames] = wallNanos;
            cpuTicks[frames] = cpuNanos;
            frames++;
        }
        if (now - started >= durationNanos || frames == MAX_FRAMES) {
            finishChat();
        }
    }

    public String stop() {
        if (!active) return "No benchmark running";
        return finish();
    }

    private String finish() {
        active = false;
        final int count = frames;
        if (count == 0) return "No frames collected";

        final long[] wall = Arrays.copyOf(frameIntervals, count);
        final long[] cpu = Arrays.copyOf(cpuTicks, count);
        final String renderer = backend;
        final double elapsedSeconds = (System.nanoTime() - started) / 1_000_000_000.0;
        final String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        final File output = new File(new File(Minecraft.getMinecraft().mcDataDir, "logs"),
            "xt9ymetal-bench-" + stamp + ".csv");

        Thread writer = new Thread(() -> {
            try {
                if (!output.getParentFile().isDirectory() && !output.getParentFile().mkdirs()) {
                    throw new IOException("Cannot create benchmark directory");
                }
                final long[] sortedWall = wall.clone();
                final long[] sortedCpu = cpu.clone();
                Arrays.sort(sortedWall);
                Arrays.sort(sortedCpu);
                long sumWall = 0;
                long sumCpu = 0;
                for (int i = 0; i < count; i++) {
                    sumWall += wall[i];
                    sumCpu += cpu[i];
                }
                try (BufferedWriter out = new BufferedWriter(new FileWriter(output))) {
                    out.write("metric,value\n");
                    out.write("backend,\\\"" + renderer.replace("\\"", "") + "\\\"\n");
                    out.write(String.format(Locale.ROOT, "duration_seconds,%.3f%n", elapsedSeconds));
                    out.write("frames," + count + "\n");
                    out.write(String.format(Locale.ROOT, "wall_fps_estimate,%.3f%n",
                        1_000_000_000.0 * count / sumWall));
                    out.write(String.format(Locale.ROOT, "wall_average_ms,%.4f%n", sumWall / (count * 1_000_000.0)));
                    out.write(String.format(Locale.ROOT, "wall_p95_ms,%.4f%n",
                        percentile(sortedWall, 0.95) / 1_000_000.0));
                    out.write(String.format(Locale.ROOT, "wall_p99_ms,%.4f%n",
                        percentile(sortedWall, 0.99) / 1_000_000.0));
                    out.write(String.format(Locale.ROOT, "cpu_average_ms,%.4f%n", sumCpu / (count * 1_000_000.0)));
                    out.write(String.format(Locale.ROOT, "cpu_p95_ms,%.4f%n",
                        percentile(sortedCpu, 0.95) / 1_000_000.0));
                    out.write(String.format(Locale.ROOT, "cpu_p99_ms,%.4f%n",
                        percentile(sortedCpu, 0.99) / 1_000_000.0));
                    out.write("\nframe,wall_ms,cpu_render_tick_ms\n");
                    for (int i = 0; i < count; i++) {
                        out.write(String.format(Locale.ROOT, "%d,%.4f,%.4f%n",
                            i + 1, wall[i] / 1_000_000.0, cpu[i] / 1_000_000.0));
                    }
                }
                MetalImprovements.LOG.info("Benchmark CSV saved: {}", output.getAbsolutePath());
            } catch (IOException e) {
                MetalImprovements.LOG.error("Cannot save benchmark to {}", output, e);
            }
        }, "GTNHMetal-BenchmarkWriter");
        writer.setDaemon(true);
        writer.start();
        return "Captured " + count + " frames. Saving CSV to logs/" + output.getName();
    }

    private static long percentile(long[] sorted, double fraction) {
        return sorted[Math.max(0, (int) Math.ceil(sorted.length * fraction) - 1)];
    }

    private void finishChat() {
        final String message = finish();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText("[GTNH Metal] " + message));
    }
}
