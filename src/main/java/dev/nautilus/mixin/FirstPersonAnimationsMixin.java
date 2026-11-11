package dev.nautilus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nautilus.module.visuals.Animations;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Animations module: swing styles, throw flick, item switch, position and idle sway (first person). */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonAnimationsMixin {
    @Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true, require = 0)
    private void nautilus$swing(PoseStack pose, HumanoidArm arm, float progress, CallbackInfo ci) {
        if (Animations.attackTransform(pose, arm, progress)) ci.cancel();
    }

    @ModifyVariable(method = "applyItemArmTransform", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private float nautilus$equip(float inverseArmHeight) {
        return Animations.equip(inverseArmHeight);
    }

    @Inject(method = "applyItemArmTransform", at = @At("TAIL"), require = 0)
    private void nautilus$position(PoseStack pose, HumanoidArm arm, float inverseArmHeight, CallbackInfo ci) {
        Animations.afterArmTransform(pose, arm);
    }
}
