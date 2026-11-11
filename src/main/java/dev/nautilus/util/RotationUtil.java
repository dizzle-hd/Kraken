package dev.nautilus.util;

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

    /** Unit vector of the view direction for the given rotation (same maths as vanilla). */
    public static Vec3 direction(float yaw, float pitch) {
        float p = pitch * Mth.DEG_TO_RAD;
        float y = -yaw * Mth.DEG_TO_RAD;
        float cp = Mth.cos(p);
        return new Vec3(Mth.sin(y) * cp, -Mth.sin(p), Mth.cos(y) * cp);
    }

    /** Point of the hitbox closest to the eyes – always the shortest reach. */
    public static Vec3 closestPoint(Vec3 eyes, Entity target) {
        return closestPoint(eyes, target.getBoundingBox());
    }

    public static Vec3 closestPoint(Vec3 eyes, AABB box) {
        return new Vec3(
                Mth.clamp(eyes.x, box.minX, box.maxX),
                Mth.clamp(eyes.y, box.minY, box.maxY),
                Mth.clamp(eyes.z, box.minZ, box.maxZ));
    }

    // =============================================================================================
    // Aim engine
    // =============================================================================================

    /** Movement of the entity during the last tick (works for other players too, unlike getDeltaMovement). */
    public static Vec3 velocity(Entity target) {
        return target.position().subtract(target.getPosition(0f));
    }

    /**
     * Hitbox of the target, moved to where it is expected to be after {@code leadTicks} ticks, plus the
     * partial-tick offset of the current frame ({@code partialTick} = 1 → tick position).
     */
    public static AABB predictedBox(Entity target, float partialTick, double leadTicks) {
        Vec3 frame = target.getPosition(partialTick).subtract(target.position());
        Vec3 v = velocity(target);
        // ignore vertical prediction while the target stands on the ground (no jitter from step-ups)
        double vy = target.onGround() ? 0 : v.y;
        return target.getBoundingBox().move(frame.add(v.x * leadTicks, vy * leadTicks, v.z * leadTicks));
    }

    /** Inner part of the hitbox an aim point is chosen from (keeps away from edges and feet). */
    public static AABB aimArea(AABB box, String part) {
        double h = box.getYsize();
        double mx = box.getXsize() * 0.2, mz = box.getZsize() * 0.2;
        double minX = box.minX + mx, maxX = box.maxX - mx, minZ = box.minZ + mz, maxZ = box.maxZ - mz;
        return switch (part) {
            case "Head" -> new AABB(minX, box.maxY - h * 0.2, minZ, maxX, box.maxY - h * 0.05, maxZ);
            case "Chest" -> new AABB(minX, box.minY + h * 0.55, minZ, maxX, box.minY + h * 0.7, maxZ);
            case "Legs" -> new AABB(minX, box.minY + h * 0.15, minZ, maxX, box.minY + h * 0.35, maxZ);
            default -> new AABB(minX, box.minY + h * 0.2, minZ, maxX, box.maxY - h * 0.08, maxZ);
        };
    }

    /**
     * The aim point inside {@code area} that needs the least rotation from the current view: the point
     * of the view ray at the depth of the box, clamped into the area. If the crosshair already lies on
     * the area, this is a point on the crosshair → no rotation at all (no needless jitter).
     * If {@code needVisible} is set and that point is hidden behind blocks, other points of the box are
     * tried (closest to the view first).
     */
    public static Vec3 bestAimPoint(Vec3 eyes, float yaw, float pitch, AABB area, boolean needVisible) {
        Vec3 dir = direction(yaw, pitch);
        Vec3 center = area.getCenter();
        double t = Math.max(0, center.subtract(eyes).dot(dir));
        Vec3 onRay = eyes.add(dir.scale(t));
        Vec3 best = closestPoint(onRay, area);
        if (!needVisible || TargetUtil.canSee(eyes, best)) return best;

        Vec3 found = null;
        float foundAngle = Float.MAX_VALUE;
        for (int ix = 0; ix <= 2; ix++) {
            for (int iy = 0; iy <= 4; iy++) {
                for (int iz = 0; iz <= 2; iz++) {
                    Vec3 p = new Vec3(
                            Mth.lerp(ix / 2.0, area.minX, area.maxX),
                            Mth.lerp(iy / 4.0, area.minY, area.maxY),
                            Mth.lerp(iz / 2.0, area.minZ, area.maxZ));
                    float a = angleDifference(yaw, pitch, rotationsTo(eyes, p));
                    if (a < foundAngle && TargetUtil.canSee(eyes, p)) {
                        found = p;
                        foundAngle = a;
                    }
                }
            }
        }
        return found != null ? found : best;
    }

    /** Does the view ray of this rotation hit {@code box} within {@code range}? */
    public static boolean rayHits(Vec3 eyes, float yaw, float pitch, AABB box, double range) {
        if (box.contains(eyes)) return true;
        Vec3 end = eyes.add(direction(yaw, pitch).scale(range));
        return box.clip(eyes, end).isPresent();
    }

    /** Angle in degrees between the current view and the given rotation. */
    public static float angleDifference(float yaw, float pitch, float[] rotation) {
        float dy = Math.abs(Mth.wrapDegrees(rotation[0] - yaw));
        float dp = Math.abs(rotation[1] - pitch);
        return (float) Math.sqrt(dy * dy + dp * dp);
    }

    /**
     * Smooth step towards {@code target} for a time step of {@code dt} seconds: exponential ease-out
     * ({@code speed} = 1/s, higher = faster), capped at {@code maxDegPerSec}. Returns {dYaw, dPitch}.
     */
    public static float[] smoothStep(float curYaw, float curPitch, float[] target, float speed, float maxDegPerSec, float dt) {
        float dYaw = Mth.wrapDegrees(target[0] - curYaw);
        float dPitch = target[1] - curPitch;
        float k = 1f - (float) Math.exp(-speed * dt);
        float sYaw = dYaw * k, sPitch = dPitch * k;
        // a minimum speed so the last fraction of a degree does not take forever
        float dist = (float) Math.sqrt(dYaw * dYaw + dPitch * dPitch);
        float len = (float) Math.sqrt(sYaw * sYaw + sPitch * sPitch);
        float minLen = Math.min(dist, 8f * dt);
        if (len < minLen && len > 1e-5f) {
            sYaw *= minLen / len;
            sPitch *= minLen / len;
            len = minLen;
        }
        float limit = maxDegPerSec * dt;
        if (len > limit && len > 1e-5f) {
            sYaw *= limit / len;
            sPitch *= limit / len;
        }
        return new float[]{sYaw, sPitch};
    }
}
