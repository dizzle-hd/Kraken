package dev.nautilus.mixin;

import dev.nautilus.util.RotationManager;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void nautilus$preMotion(CallbackInfo ci) {
        RotationManager.preMotion((LocalPlayer) (Object) this);
    }

    @Inject(method = "sendPosition", at = @At("RETURN"))
    private void nautilus$postMotion(CallbackInfo ci) {
        RotationManager.postMotion((LocalPlayer) (Object) this);
    }
}
