package dev.nautilus.mixin;

import dev.nautilus.module.client.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freecam: mouse movement turns the free camera instead of the player. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void nautilus$freecamTurn(double xo, double yo, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player && Freecam.active()) {
            Freecam.turn(xo, yo);
            ci.cancel();
        }
    }
}
