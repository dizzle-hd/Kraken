package dev.kraken.util;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class RotationUtil {
    private RotationUtil() {}

    /** @return {yaw, pitch} looking from {@code from} at {@code to}. */
    public static float[] rotationsTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
        return new float[]{Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90f, 90f)};
    }

    /** Point of the hitbox closest to the eyes – always the shortest reach. */
    public static Vec3 closestPoint(Vec3 eyes, Entity target) {
        AABB box = target.getBoundingBox();
        return new Vec3(
                Mth.clamp(eyes.x, box.minX, box.maxX),
                Mth.clamp(eyes.y, box.minY, box.maxY),
                Mth.clamp(eyes.z, box.minZ, box.maxZ));
    }

    /** A natural looking aim point: upper body, clamped into the hitbox. */
    public static Vec3 aimPoint(Vec3 eyes, Entity target) {
        AABB box = target.getBoundingBox();
        double y = Mth.clamp(eyes.y, box.minY + box.getYsize() * 0.25, box.maxY - box.getYsize() * 0.1);
        return new Vec3(
                Mth.clamp(eyes.x, box.minX + 0.05, box.maxX - 0.05),
                y,
                Mth.clamp(eyes.z, box.minZ + 0.05, box.maxZ - 0.05));
    }

    /** Angle in degrees between the current view and the given rotation. */
    public static float angleDifference(float yaw, float pitch, float[] rotation) {
        float dy = Math.abs(Mth.wrapDegrees(rotation[0] - yaw));
        float dp = Math.abs(rotation[1] - pitch);
        return (float) Math.sqrt(dy * dy + dp * dp);
    }

    /** Move from {@code current} towards {@code target} by at most {@code maxStep} degrees. */
    public static float[] limit(float curYaw, float curPitch, float[] target, float maxStep) {
        float dYaw = Mth.wrapDegrees(target[0] - curYaw);
        float dPitch = target[1] - curPitch;
        float dist = (float) Math.sqrt(dYaw * dYaw + dPitch * dPitch);
        if (dist <= maxStep || dist < 1e-4f) return new float[]{curYaw + dYaw, target[1]};
        float f = maxStep / dist;
        return new float[]{curYaw + dYaw * f, curPitch + dPitch * f};
    }
}
