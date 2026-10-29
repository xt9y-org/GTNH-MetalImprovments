package com.xt9y.metal.mixin.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.xt9y.metal.AngelicaFrameProbe;
import com.xt9y.metal.AngelicaProbeGate;
import com.xt9y.metal.MetalImprovements;

/** Never load the profiler during startup or outside a benchmark capture. */
@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.sdlgpu.frame.FrameManager", remap = false)
public abstract class MixinAngelicaFrameManager {

    @Inject(
        method = "endFrame()V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtnewhorizons/angelica/sdlgpu/frame/FrameManager;drainWindowPresentCounters(Lcom/gtnewhorizons/angelica/sdlgpu/frame/FrameManager$FrameState;)V",
            shift = At.Shift.AFTER,
            remap = false),
        require = 0,
        remap = false)
    private void xt9ymetal$measurePasses(CallbackInfo ci) {
        if (!AngelicaProbeGate.enabled) return;
        try {
            AngelicaFrameProbe.INSTANCE.onFrameEnd(this);
        } catch (RuntimeException | LinkageError e) {
            // Optional profiling must never take the client down with it.
            AngelicaProbeGate.enabled = false;
            MetalImprovements.LOG.error("Disabling incompatible Angelica frame profiler", e);
        }
    }
}
