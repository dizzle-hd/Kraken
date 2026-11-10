package dev.nautilus.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What happens in a fight, for the HUD: the last entity you attacked and how many totems every player
 * popped. Only reads – it never sends a packet.
 */
public final class CombatTracker {
    private CombatTracker() {}

    private static final long TARGET_MEMORY_MS = 6000;

    private static LivingEntity lastTarget;
    private static long lastAttack;
    private static final Map<UUID, Integer> pops = new HashMap<>();
    private static Level popLevel;

    /** Every attack of the local player (from the attack hook). */
    public static void onAttack(Entity target) {
        if (target instanceof LivingEntity living) {
            lastTarget = living;
            lastAttack = System.currentTimeMillis();
        }
    }

    /**
     * The current target: the living entity in the crosshair, otherwise the one you hit last (for a few
     * seconds, while it is alive and near).
     */
    public static LivingEntity target() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity e && e.isAlive()) return e;
        LivingEntity t = lastTarget;
        if (t == null || !t.isAlive() || t.isRemoved() || t.level() != mc.player.level()
                || System.currentTimeMillis() - lastAttack > TARGET_MEMORY_MS || t.distanceTo(mc.player) > 16) return null;
        return t;
    }

    /** A totem of the player popped (entity event 35); returns the new count. */
    public static int onPop(Player p) {
        checkLevel(p.level());
        return pops.merge(p.getUUID(), 1, Integer::sum);
    }

    /** The player died (entity event 3); returns how many totems they popped before and forgets them. */
    public static int onDeath(Player p) {
        checkLevel(p.level());
        Integer n = pops.remove(p.getUUID());
        return n == null ? 0 : n;
    }

    public static int pops(Entity e) {
        return e == null ? 0 : pops.getOrDefault(e.getUUID(), 0);
    }

    /** New world / server: start counting from zero. */
    private static void checkLevel(Level level) {
        if (level != popLevel) {
            pops.clear();
            popLevel = level;
        }
    }
}
