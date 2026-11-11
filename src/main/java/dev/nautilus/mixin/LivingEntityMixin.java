package dev.nautilus.mixin;

import dev.nautilus.module.visuals.SwordFx;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Visuals: the air slash of SwordFx starts with every main-hand swing of the local player. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z", at = @At("RETURN"))
    private void nautilus$swing(InteractionHand hand, SwingAnimation animation, boolean sendPacket, CallbackInfoReturnable<Boolean> cir) {
        if (hand == InteractionHand.MAIN_HAND) SwordFx.onSwing((LivingEntity) (Object) this);
        dev.nautilus.module.visuals.Animations.onSwing((LivingEntity) (Object) this, hand);
    }

    /** Animations: swing speed of the local player (only the animation, the server never sees it). */
    @Inject(method = "getModifiedSwingDuration", at = @At("RETURN"), cancellable = true, require = 0)
    private void nautilus$swingDuration(net.minecraft.world.item.component.SwingAnimation animation, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(dev.nautilus.module.visuals.Animations.swingDuration(cir.getReturnValue(), (LivingEntity) (Object) this));
    }
}
