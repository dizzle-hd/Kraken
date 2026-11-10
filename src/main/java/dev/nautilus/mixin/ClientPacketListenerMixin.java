package dev.nautilus.mixin;

import dev.nautilus.module.ModuleManager;
import dev.nautilus.module.smp.BaseFinder;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
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

    /** Totem pops (event 35) and deaths (event 3) of players: counted for the target HUD and PopCounter. */
    @Inject(method = "handleEntityEvent", at = @At("TAIL"))
    private void nautilus$entityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        byte id = packet.getEventId();
        if (id != 35 && id != 3) return;
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null || !(packet.getEntity(mc.level) instanceof net.minecraft.world.entity.player.Player p)) return;
        var counter = ModuleManager.get().get(dev.nautilus.module.combat.PopCounter.class);
        boolean on = counter != null && counter.isEnabled();
        if (id == 35) {
            int n = dev.nautilus.util.CombatTracker.onPop(p);
            if (on) counter.onPop(p, n);
        } else {
            int n = dev.nautilus.util.CombatTracker.onDeath(p);
            if (on) counter.onDeath(p, n);
        }
    }
}
