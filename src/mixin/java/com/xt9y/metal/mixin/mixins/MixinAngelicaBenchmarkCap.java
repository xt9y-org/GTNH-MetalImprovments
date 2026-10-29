package com.xt9y.metal.mixin.mixins;

import com.xt9y.metal.MetalBenchmark;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bypass only Angelica's runtime FPS limiter during a benchmark. */
@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.FpsReducer", remap = false)
public abstract class MixinAngelicaBenchmarkCap {

    @Inject(method = "effectiveCap(IZ)I", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void xt9ymetal$uncapDuringBenchmark(int userLimit, boolean inMenu,
        CallbackInfoReturnable<Integer> cir) {
        if (MetalBenchmark.INSTANCE.active()) cir.setReturnValue(0);
    }
}
