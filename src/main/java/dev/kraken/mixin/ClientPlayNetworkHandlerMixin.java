package dev.kraken.mixin;

import dev.kraken.module.ModuleManager;
import dev.kraken.module.smp.BaseFinder;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onPlaySound", at = @At("TAIL"))
    private void kraken$sound(PlaySoundS2CPacket packet, CallbackInfo ci) {
        BaseFinder bf = ModuleManager.get().get(BaseFinder.class);
        if (bf != null && bf.isEnabled()) {
            bf.onSound(packet.getSound().value().id(), packet.getCategory(), packet.getX(), packet.getY(), packet.getZ());
        }
    }

    @Inject(method = "onWorldEvent", at = @At("TAIL"))
    private void kraken$worldEvent(WorldEventS2CPacket packet, CallbackInfo ci) {
        BaseFinder bf = ModuleManager.get().get(BaseFinder.class);
        if (bf != null && bf.isEnabled() && !packet.isGlobal()) {
            bf.onWorldEvent(packet.getEventId(), packet.getPos());
        }
    }
}
