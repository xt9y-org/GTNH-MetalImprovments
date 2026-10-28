package com.xt9y.metal.mixin.mixins;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.entity.player.EntityPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.xt9y.metal.ClientRuntime;
import com.xt9y.metal.ClientSettings;

@Mixin(value = EffectRenderer.class, priority = 900)
public abstract class MixinEffectRenderer {

    @Shadow
    private List[] fxLayers;

    @Inject(method = "addEffect(Lnet/minecraft/client/particle/EntityFX;)V", at = @At("HEAD"), cancellable = true)
    private void xt9ymetal$boundParticleWork(EntityFX particle, CallbackInfo ci) {
        if (particle == null || !ClientSettings.particleBudget) {
            return;
        }
        int layer = particle.getFXLayer();
        if (layer < 0 || layer >= fxLayers.length) {
            return;
        }
        if (fxLayers[layer].size() >= ClientSettings.effectiveParticleCap()) {
            ClientRuntime.INSTANCE.rejectedParticle();
            ci.cancel();
            return;
        }
        int maxDistance = ClientSettings.maxParticleDistance;
        if (maxDistance > 0) {
            EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            if (player != null) {
                double dx = particle.posX - player.posX;
                double dy = particle.posY - player.posY;
                double dz = particle.posZ - player.posZ;
                if (dx * dx + dy * dy + dz * dz > (double) maxDistance * maxDistance) {
                    ClientRuntime.INSTANCE.rejectedParticle();
                    ci.cancel();
                }
            }
        }
    }
}
