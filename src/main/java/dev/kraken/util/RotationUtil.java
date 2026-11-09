package dev.kraken.util;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class RotationUtil {
    private RotationUtil() {}

    /** @return {yaw, pitch} looking from {@code from} at {@code to}. */
    public static float[] rotationsTo(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
        return new float[]{MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -90f, 90f)};
    }

    /** Point of the hitbox closest to the eyes – always the shortest reach. */
    public static Vec3d closestPoint(Vec3d eyes, Entity target) {
        Box box = target.getBoundingBox();
        return new Vec3d(
                MathHelper.clamp(eyes.x, box.minX, box.maxX),
                MathHelper.clamp(eyes.y, box.minY, box.maxY),
                MathHelper.clamp(eyes.z, box.minZ, box.maxZ));
    }

    /** A natural looking aim point: upper body, clamped into the hitbox. */
    public static Vec3d aimPoint(Vec3d eyes, Entity target) {
        Box box = target.getBoundingBox();
        double y = MathHelper.clamp(eyes.y, box.minY + box.getLengthY() * 0.25, box.maxY - box.getLengthY() * 0.1);
        return new Vec3d(
                MathHelper.clamp(eyes.x, box.minX + 0.05, box.maxX - 0.05),
                y,
                MathHelper.clamp(eyes.z, box.minZ + 0.05, box.maxZ - 0.05));
    }

    /** Angle in degrees between the current view and the given rotation. */
    public static float angleDifference(float yaw, float pitch, float[] rotation) {
        float dy = Math.abs(MathHelper.wrapDegrees(rotation[0] - yaw));
        float dp = Math.abs(rotation[1] - pitch);
        return (float) Math.sqrt(dy * dy + dp * dp);
    }

    /** Move from {@code current} towards {@code target} by at most {@code maxStep} degrees. */
    public static float[] limit(float curYaw, float curPitch, float[] target, float maxStep) {
        float dYaw = MathHelper.wrapDegrees(target[0] - curYaw);
        float dPitch = target[1] - curPitch;
        float dist = (float) Math.sqrt(dYaw * dYaw + dPitch * dPitch);
        if (dist <= maxStep || dist < 1e-4f) return new float[]{curYaw + dYaw, target[1]};
        float f = maxStep / dist;
        return new float[]{curYaw + dYaw * f, curPitch + dPitch * f};
    }
}
