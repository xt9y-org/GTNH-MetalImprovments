package com.xt9y.metal;

/**
 * Separate, dependency-free class: SDL's splash renderer may call the
 * FrameManager mixin before Minecraft and benchmark classes can initialize.
 */
public final class AngelicaProbeGate {

    public static volatile boolean enabled;

    private AngelicaProbeGate() {}
}
