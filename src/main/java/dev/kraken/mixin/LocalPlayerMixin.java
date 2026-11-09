package dev.kraken.mixin;

import dev.kraken.util.RotationManager;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void kraken$preMotion(CallbackInfo ci) {
        RotationManager.preMotion((LocalPlayer) (Object) this);
    }

    @Inject(method = "sendPosition", at = @At("RETURN"))
    private void kraken$postMotion(CallbackInfo ci) {
        RotationManager.postMotion((LocalPlayer) (Object) this);
    }
}
