package dev.kraken.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TargetUtil {
    private TargetUtil() {}

    public record Filter(boolean players, boolean hostiles, boolean passives, boolean invisibles, boolean teammates) {}

    public static boolean isValid(Entity e, Filter f) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!(e instanceof LivingEntity living) || e == mc.player) return false;
        if (!living.isAlive() || living.getHealth() <= 0 || e instanceof ArmorStandEntity) return false;
        if (e.isInvisible() && !f.invisibles()) return false;
        if (e instanceof PlayerEntity p) {
            if (!f.players() || p.isSpectator() || p.isCreative()) return false;
            if (!f.teammates() && mc.player != null && mc.player.isTeammate(p)) return false;
            return true;
        }
        if (e instanceof TameableEntity t && t.isTamed() && mc.player != null && t.isOwner(mc.player)) return false;
        if (e instanceof Monster) return f.hostiles();
        if (e instanceof PassiveEntity) return f.passives();
        return f.hostiles();
    }

    public static boolean canSee(Vec3d eyes, Vec3d point) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return false;
        return mc.world.raycast(new RaycastContext(eyes, point, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, mc.player)).getType() == HitResult.Type.MISS;
    }

    /**
     * All valid targets within {@code range} (measured to the closest hitbox point), inside the given
     * FOV (degrees, 360 = everywhere), sorted by the given order.
     */
    public static List<LivingEntity> find(Filter filter, double range, float fov, boolean throughWalls, String sort) {
        MinecraftClient mc = MinecraftClient.getInstance();
        List<LivingEntity> list = new ArrayList<>();
        if (mc.world == null || mc.player == null) return list;
        Vec3d eyes = mc.player.getEyePos();
        double rangeSq = range * range;
        for (Entity e : mc.world.getEntities()) {
            if (!isValid(e, filter)) continue;
            Vec3d point = RotationUtil.closestPoint(eyes, e);
            if (eyes.squaredDistanceTo(point) > rangeSq) continue;
            if (fov < 360f) {
                float[] rot = RotationUtil.rotationsTo(eyes, RotationUtil.aimPoint(eyes, e));
                if (RotationUtil.angleDifference(mc.player.getYaw(), mc.player.getPitch(), rot) > fov / 2f) continue;
            }
            if (!throughWalls && !canSee(eyes, point) && !canSee(eyes, e.getBoundingBox().getCenter())) continue;
            list.add((LivingEntity) e);
        }
        Comparator<LivingEntity> cmp = switch (sort) {
            case "Leben" -> Comparator.comparingDouble(LivingEntity::getHealth);
            case "Winkel" -> Comparator.comparingDouble(e -> RotationUtil.angleDifference(mc.player.getYaw(), mc.player.getPitch(),
                    RotationUtil.rotationsTo(eyes, RotationUtil.aimPoint(eyes, e))));
            default -> Comparator.comparingDouble(e -> eyes.squaredDistanceTo(RotationUtil.closestPoint(eyes, e)));
        };
        list.sort(cmp);
        return list;
    }
}
