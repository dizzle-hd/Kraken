package dev.nautilus.mixin;

import dev.nautilus.module.render.Freecam;
import dev.nautilus.module.render.Zoom;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom (FOV) and Freecam (camera position / rotation). */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private boolean detached;

    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow protected abstract void setPosition(Vec3 position);

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void nautilus$zoom(float partialTicks, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(Zoom.modifyFov(cir.getReturnValueF()));
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void nautilus$freecam(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!Freecam.active()) return;
        setPosition(Freecam.updatePosition());
        setRotation(Freecam.yaw(), Freecam.pitch());
        detached = true; // render the own player
    }
}
