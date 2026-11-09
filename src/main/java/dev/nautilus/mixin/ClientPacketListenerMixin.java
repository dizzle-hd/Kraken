package dev.nautilus.mixin;

import dev.nautilus.module.ModuleManager;
import dev.nautilus.module.smp.BaseFinder;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import dev.nautilus.hud.TpsTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleSoundEvent", at = @At("TAIL"))
    private void nautilus$sound(ClientboundSoundPacket packet, CallbackInfo ci) {
        BaseFinder bf = ModuleManager.get().get(BaseFinder.class);
        if (bf != null && bf.isEnabled()) {
            bf.onSound(packet.getSound().value().location(), packet.getSource(), packet.getX(), packet.getY(), packet.getZ());
        }
    }

    @Inject(method = "handleSoundEntityEvent", at = @At("TAIL"))
    private void nautilus$entitySound(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
        BaseFinder bf = ModuleManager.get().get(BaseFinder.class);
        if (bf != null && bf.isEnabled()) {
            bf.onEntitySound(packet.getSound().value().location(), packet.getSource(), packet.getId());
        }
    }

    @Inject(method = "handleSetTime", at = @At("TAIL"))
    private void nautilus$tps(ClientboundSetTimePacket packet, CallbackInfo ci) {
        TpsTracker.onTimePacket();
    }

    @Inject(method = "handleLevelEvent", at = @At("TAIL"))
    private void nautilus$levelEvent(ClientboundLevelEventPacket packet, CallbackInfo ci) {
        BaseFinder bf = ModuleManager.get().get(BaseFinder.class);
        if (bf != null && bf.isEnabled() && !packet.isGlobalEvent()) {
            bf.onWorldEvent(packet.getType(), packet.getPos());
        }
    }
}
