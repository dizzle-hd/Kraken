package dev.kraken.mixin;

import dev.kraken.util.RotationManager;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void kraken$preMotion(CallbackInfo ci) {
        RotationManager.preMotion((ClientPlayerEntity) (Object) this);
    }

    @Inject(method = "sendMovementPackets", at = @At("RETURN"))
    private void kraken$postMotion(CallbackInfo ci) {
        RotationManager.postMotion((ClientPlayerEntity) (Object) this);
    }
}
