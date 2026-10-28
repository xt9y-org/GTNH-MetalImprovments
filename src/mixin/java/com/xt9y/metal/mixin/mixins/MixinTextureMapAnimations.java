package com.xt9y.metal.mixin.mixins;

import net.minecraft.client.renderer.texture.TextureMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.xt9y.metal.ClientSettings;

@Mixin(value = TextureMap.class, priority = 900)
public abstract class MixinTextureMapAnimations {

    @Unique
    private int xt9ymetal$animationTick;

    @Inject(method = "updateAnimations", at = @At("HEAD"), cancellable = true)
    private void xt9ymetal$limitAnimationUploads(CallbackInfo ci) {
        int interval = ClientSettings.animationTickInterval;
        if (interval > 1 && ++xt9ymetal$animationTick % interval != 0) {
            ci.cancel();
        }
    }
}
