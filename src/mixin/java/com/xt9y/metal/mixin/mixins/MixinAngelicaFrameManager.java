package com.xt9y.metal.mixin.mixins;

import com.xt9y.metal.AngelicaFrameProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pure instrumentation: does not change the render pass or attachment state. */
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
        AngelicaFrameProbe.INSTANCE.onFrameEnd(this);
    }
}
