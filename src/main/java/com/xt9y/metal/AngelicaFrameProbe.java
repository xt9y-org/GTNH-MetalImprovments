package com.xt9y.metal;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Read-only sampling of Angelica SDL GPU counters immediately before endFrame
 * resets them. Reflection avoids hard-linking against any Angelica version.
 */
public final class AngelicaFrameProbe {

    public static final AngelicaFrameProbe INSTANCE = new AngelicaFrameProbe();

    private static final String[] METRICS = { "submitsThisFrame", "midFrameSubmitsThisFrame", "copyPassesThisFrame",
        "fboPassesThisFrame", "swapchainPassesThisFrame", "clearPassesThisFrame", "inPassClearsThisFrame",
        "materializedClearPassesThisFrame", "uploadFlushSubmitsThisFrame", "arenaOverflowFlushesThisFrame",
        "mipGenSubmitsThisFrame", "presentSkipsThisFrame", "batchBreakPipelineThisFrame", "batchBreakSamplerThisFrame",
        "perFrameBlockPassBreaksThisFrame" };

    private Method frameMethod;
    private Field[] fields;
    private Field passEnds;
    private Field passGeneration;
    private Field gpuWait;
    private Field acquireWait;
    private Class<?> owner;
    private boolean failed;

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
            int[] counters = new int[fields.length];
            for (int i = 0; i < fields.length; i++) counters[i] = fields[i].getInt(frame);
            int[] causeCounts = ((int[]) passEnds.get(frame)).clone();
            bench.recordPassStats(
                passGeneration.getLong(frame),
                counters,
                causeCounts,
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
