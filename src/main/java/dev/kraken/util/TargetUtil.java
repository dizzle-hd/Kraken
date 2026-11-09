package dev.kraken.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TargetUtil {
    private TargetUtil() {}

    public record Filter(boolean players, boolean hostiles, boolean passives, boolean invisibles, boolean teammates) {}

    public static boolean isValid(Entity e, Filter f) {
        Minecraft mc = Minecraft.getInstance();
        if (!(e instanceof LivingEntity living) || e == mc.player) return false;
        if (!living.isAlive() || living.getHealth() <= 0 || e instanceof ArmorStand) return false;
        if (e.isInvisible() && !f.invisibles()) return false;
        if (e instanceof Player p) {
            if (!f.players() || p.isSpectator() || p.isCreative()) return false;
            if (!f.teammates() && mc.player != null && mc.player.isAlliedTo(p)) return false;
            return true;
        }
        if (e instanceof TamableAnimal t && t.isTame() && mc.player != null && t.isOwnedBy(mc.player)) return false;
        if (e instanceof Enemy) return f.hostiles();
        if (e instanceof AgeableMob) return f.passives();
        return f.hostiles();
    }

    public static boolean canSee(Vec3 eyes, Vec3 point) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;
        return mc.level.clip(new ClipContext(eyes, point, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, mc.player)).getType() == HitResult.Type.MISS;
    }

    /** Can any useful part of the hitbox (centre, head, closest point, feet) be seen from the eyes? */
    public static boolean canSeeAny(Vec3 eyes, Entity e) {
        AABB box = e.getBoundingBox();
        Vec3 c = box.getCenter();
        return canSee(eyes, RotationUtil.closestPoint(eyes, box))
                || canSee(eyes, c)
                || canSee(eyes, new Vec3(c.x, box.maxY - box.getYsize() * 0.1, c.z))
                || canSee(eyes, new Vec3(c.x, box.minY + box.getYsize() * 0.15, c.z));
    }

    /** Angle between the current view and the cheapest point of the target to aim at. */
    public static float viewAngle(Vec3 eyes, Entity e) {
        Minecraft mc = Minecraft.getInstance();
        float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
        Vec3 p = RotationUtil.bestAimPoint(eyes, yaw, pitch, RotationUtil.aimArea(e.getBoundingBox(), "Auto"), false);
        return RotationUtil.angleDifference(yaw, pitch, RotationUtil.rotationsTo(eyes, p));
    }

    /**
     * All valid targets within {@code range} (measured to the closest hitbox point), inside the given
     * FOV (degrees, 360 = everywhere), sorted by the given order.
     */
    public static List<LivingEntity> find(Filter filter, double range, float fov, boolean throughWalls, String sort) {
        Minecraft mc = Minecraft.getInstance();
        List<LivingEntity> list = new ArrayList<>();
        if (mc.level == null || mc.player == null) return list;
        Vec3 eyes = mc.player.getEyePosition();
        double rangeSq = range * range;
        Map<LivingEntity, Float> angles = new HashMap<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!isValid(e, filter)) continue;
            Vec3 point = RotationUtil.closestPoint(eyes, e);
            if (eyes.distanceToSqr(point) > rangeSq) continue;
            float angle = viewAngle(eyes, e);
            if (fov < 360f && angle > fov / 2f) continue;
            if (!throughWalls && !canSeeAny(eyes, e)) continue;
            list.add((LivingEntity) e);
            angles.put((LivingEntity) e, angle);
        }
        Comparator<LivingEntity> cmp = switch (sort) {
            case "Leben" -> Comparator.comparingDouble((LivingEntity e) -> e.getHealth() + e.getAbsorptionAmount())
                    .thenComparingDouble(e -> eyes.distanceToSqr(RotationUtil.closestPoint(eyes, e)));
            case "Winkel" -> Comparator.comparingDouble(angles::get);
            // distance, but a target right in front of you wins over one slightly closer behind you
            default -> Comparator.comparingDouble(e -> eyes.distance(RotationUtil.closestPoint(eyes, e)) + angles.get(e) / 90.0);
        };
        list.sort(cmp);
        return list;
    }

    /**
     * Target lock: keeps the previous target at the front while it is still valid, so the aim does not
     * flick back and forth between two targets with almost the same score.
     */
    public static void stick(List<LivingEntity> sorted, LivingEntity previous) {
        if (previous == null || sorted.isEmpty() || sorted.get(0) == previous) return;
        if (sorted.remove(previous)) sorted.add(0, previous);
    }
}
