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
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!isValid(e, filter)) continue;
            Vec3 point = RotationUtil.closestPoint(eyes, e);
            if (eyes.distanceToSqr(point) > rangeSq) continue;
            if (fov < 360f) {
                float[] rot = RotationUtil.rotationsTo(eyes, RotationUtil.aimPoint(eyes, e));
                if (RotationUtil.angleDifference(mc.player.getYRot(), mc.player.getXRot(), rot) > fov / 2f) continue;
            }
            if (!throughWalls && !canSee(eyes, point) && !canSee(eyes, e.getBoundingBox().getCenter())) continue;
            list.add((LivingEntity) e);
        }
        Comparator<LivingEntity> cmp = switch (sort) {
            case "Leben" -> Comparator.comparingDouble(LivingEntity::getHealth);
            case "Winkel" -> Comparator.comparingDouble(e -> RotationUtil.angleDifference(mc.player.getYRot(), mc.player.getXRot(),
                    RotationUtil.rotationsTo(eyes, RotationUtil.aimPoint(eyes, e))));
            default -> Comparator.comparingDouble(e -> eyes.distanceToSqr(RotationUtil.closestPoint(eyes, e)));
        };
        list.sort(cmp);
        return list;
    }
}
