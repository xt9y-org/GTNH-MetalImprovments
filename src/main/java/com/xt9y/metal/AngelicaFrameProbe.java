package com.xt9y.metal;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Read-only sampling of Angelica SDL GPU counters immediately before endFrame
 * resets them. Reflection avoids hard-linking against any Angelica version.
 */
public final class AngelicaFrameProbe {

    private static final String[] METRICS = { "submitsThisFrame", "midFrameSubmitsThisFrame", "copyPassesThisFrame",
        "fboPassesThisFrame", "swapchainPassesThisFrame", "clearPassesThisFrame", "inPassClearsThisFrame",
        "materializedClearPassesThisFrame", "uploadFlushSubmitsThisFrame", "arenaOverflowFlushesThisFrame",
        "mipGenSubmitsThisFrame", "presentSkipsThisFrame", "batchBreakPipelineThisFrame", "batchBreakSamplerThisFrame",
        "perFrameBlockPassBreaksThisFrame" };

    public static final AngelicaFrameProbe INSTANCE = new AngelicaFrameProbe();

    private Method frameMethod;
    private Field[] fields;
    private Field passEnds;
    private Field passGeneration;
    private Field gpuWait;
    private Field acquireWait;
    private Class<?> owner;
    private boolean failed;
    // Reused on the render thread: no short-lived arrays every rendered frame.
    private final int[] counterScratch = new int[METRICS.length];
    private final int[] causeScratch = new int[8];

    private AngelicaFrameProbe() {}

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field result = type.getField(name);
        result.setAccessible(true);
        return result;
    }

    private void initialize(Object manager) throws ReflectiveOperationException {
        Class<?> type = manager.getClass();
        frameMethod = type.getMethod("frame");
        Class<?> state = frameMethod.getReturnType();
        fields = new Field[METRICS.length];
        for (int i = 0; i < fields.length; i++) {
            fields[i] = field(state, METRICS[i]);
        }
        passEnds = field(state, "passEndCauseCountsThisFrame");
        passGeneration = field(state, "renderPassGeneration");
        gpuWait = field(state, "gpuWaitNanosThisFrame");
        acquireWait = field(state, "acquireWaitNanosThisFrame");
        owner = type;
    }

    public void onFrameEnd(Object manager) {
        MetalBenchmark bench = MetalBenchmark.INSTANCE;
        if (!bench.isCapturing() || failed) return;
        if (Thread.currentThread() != bench.renderThread()) return;

        try {
            if (owner != manager.getClass()) initialize(manager);
            Object frame = frameMethod.invoke(manager);
            for (int i = 0; i < fields.length; i++) counterScratch[i] = fields[i].getInt(frame);
            int[] causeCounts = (int[]) passEnds.get(frame);
            java.util.Arrays.fill(causeScratch, 0);
            System.arraycopy(causeCounts, 0, causeScratch, 0, Math.min(causeCounts.length, causeScratch.length));
            bench.recordPassStats(
                passGeneration.getLong(frame),
                counterScratch,
                causeScratch,
                gpuWait.getLong(frame),
                acquireWait.getLong(frame));
        } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
            failed = true;
            MetalImprovements.LOG.warn("Angelica SDL GPU frame counters unavailable", e);
        }
    }

    public static String[] metricNames() {
        return METRICS.clone();
    }
}
