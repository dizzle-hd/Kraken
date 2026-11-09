package dev.kraken.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

/** Attacks exactly like a vanilla left click does in 26.x (attack, swing, punch packet). */
public final class AttackUtil {
    private AttackUtil() {}

    public static void attack(Entity target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getAttackAnimation(), false);
        mc.player.connection.send(ServerboundPunchPacket.INSTANCE);
    }
}
