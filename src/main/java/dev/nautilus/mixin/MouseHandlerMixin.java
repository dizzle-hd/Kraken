package dev.nautilus.mixin;

import dev.nautilus.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import dev.nautilus.module.render.Zoom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Per-frame hook right after the mouse turned the player: used for smooth camera aiming. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    /** Zoom: slower mouse while zoomed in. */
    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void nautilus$zoomSensitivity(double movementTime, CallbackInfo ci) {
        double s = Zoom.mouseScale();
        if (s != 1) {
            accumulatedDX *= s;
            accumulatedDY *= s;
        }
    }

    /** Zoom: the mouse wheel changes the zoom instead of the hotbar slot. */
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void nautilus$zoomScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.gui.screen() == null && Zoom.onScroll(yOffset)) ci.cancel();
    }

    @Inject(method = "handleAccumulatedMovement", at = @At("TAIL"))
    private void nautilus$frame(CallbackInfo ci) {
        if (Minecraft.getInstance().player != null) ModuleManager.get().onFrame();
    }
}
