package dev.kraken.mixin;

import dev.kraken.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Per-frame hook right after the mouse turned the player: used for smooth camera aiming. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "handleAccumulatedMovement", at = @At("TAIL"))
    private void kraken$frame(CallbackInfo ci) {
        if (Minecraft.getInstance().player != null) ModuleManager.get().onFrame();
    }
}
