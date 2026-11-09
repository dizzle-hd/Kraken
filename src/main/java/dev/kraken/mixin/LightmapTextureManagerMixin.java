package dev.kraken.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.kraken.module.smp.XRay;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Fullbright for XRay: pretend the player has night vision at full strength. */
@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixin {
    @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z",
            ordinal = 0))
    private boolean kraken$nightVision(boolean original) {
        return original || XRay.fullbright();
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/GameRenderer;getNightVisionStrength(Lnet/minecraft/entity/LivingEntity;F)F"))
    private float kraken$nightVisionStrength(LivingEntity entity, float tickProgress, Operation<Float> original) {
        if (XRay.fullbright()) return 1.0f;
        return original.call(entity, tickProgress);
    }
}
