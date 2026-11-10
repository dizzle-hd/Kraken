package dev.nautilus.mixin;

import dev.nautilus.module.smp.XRay;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fullbright (module or XRay option): full night vision in the lightmap. */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapRenderStateExtractorMixin {
    @Inject(method = "extract", at = @At("TAIL"))
    private void nautilus$fullbright(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
        if (XRay.fullbright() || dev.nautilus.module.player.Fullbright.active()) renderState.nightVisionEffectIntensity = 1.0f;
    }
}
