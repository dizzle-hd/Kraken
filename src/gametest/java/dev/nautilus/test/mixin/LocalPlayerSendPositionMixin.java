package dev.nautilus.test.mixin;

import dev.nautilus.test.PacketLog;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks the point of the tick where the movement packet goes out (even when nothing changed and none is sent). */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerSendPositionMixin {
    @Inject(method = "sendPosition", at = @At("RETURN"))
    private void nautilusTest$moved(CallbackInfo ci) {
        PacketLog.movementPoint();
    }
}
