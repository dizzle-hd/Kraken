package dev.nautilus.mixin;

import dev.nautilus.module.visuals.Sky;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sky → Atmosphere: tint the fog (and with it the horizon) like the sky. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Inject(method = "computeFogColor", at = @At("TAIL"), require = 0)
    private void nautilus$fogColor(Camera camera, float partialTicks, ClientLevel level, int renderDistance, float darkenWorld, Vector4f color, CallbackInfo ci) {
        if (camera.getFluidInCamera() == net.minecraft.world.level.material.FogType.NONE) Sky.tintFog(color);
    }
}
