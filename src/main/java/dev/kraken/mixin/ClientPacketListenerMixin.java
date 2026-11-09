package dev.kraken.mixin;

import dev.kraken.module.ModuleManager;
import dev.kraken.module.smp.BaseFinder;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleSoundEvent", at = @At("TAIL"))
    private void kraken$sound(ClientboundSoundPacket packet, CallbackInfo ci) {
        BaseFinder bf = ModuleManager.get().get(BaseFinder.class);
        if (bf != null && bf.isEnabled()) {
            bf.onSound(packet.getSound().value().location(), packet.getSource(), packet.getX(), packet.getY(), packet.getZ());
        }
    }

    @Inject(method = "handleLevelEvent", at = @At("TAIL"))
    private void kraken$levelEvent(ClientboundLevelEventPacket packet, CallbackInfo ci) {
        BaseFinder bf = ModuleManager.get().get(BaseFinder.class);
        if (bf != null && bf.isEnabled() && !packet.isGlobalEvent()) {
            bf.onWorldEvent(packet.getType(), packet.getPos());
        }
    }
}
