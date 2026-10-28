package com.xt9y.metal.mixin.mixins;

import net.minecraft.client.Minecraft;

import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.xt9y.metal.ClientSettings;

@Mixin(value = Minecraft.class, priority = 900)
public abstract class MixinMinecraftBackground {

    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void xt9ymetal$throttleBackground(CallbackInfo ci) {
        int fps = ClientSettings.backgroundFps;
        if (fps <= 0 || !Display.isCreated()) {
            return;
        }
        if (!Display.isActive() || !Display.isVisible()) {
            try {
                Thread.sleep(Math.max(1, 1000 / fps - 1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
