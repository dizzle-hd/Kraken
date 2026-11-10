package dev.nautilus.fx;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Reusable short-lived effects: sparks, shock-wave rings, rising rings, air slashes. */
public final class Effects {
    private Effects() {}

    public static final Random RANDOM = new Random();

    /** A glowing spark that flies, falls a bit and fades, leaving a short tail. */
    public static Fx.Effect spark(Vec3 start, Vec3 velocity, int rgb, float size, long lifeMs, float gravity) {
        long born = System.currentTimeMillis();
        return (fx, now) -> {
            float t = (now - born) / 1000f, life = lifeMs / 1000f;
            if (t > life) return false;
            Vec3 p = start.add(velocity.scale(t)).add(0, -gravity * t * t / 2, 0);
            Vec3 tail = start.add(velocity.scale(Math.max(0, t - 0.05f))).add(0, -gravity * Math.max(0, t - 0.05f) * Math.max(0, t - 0.05f) / 2, 0);
            float a = 1f - t / life;
            fx.spark(p, size, rgb, a);
            fx.glow(tail, size * 0.6f, rgb, a * 0.35f);
            return true;
        };
    }

    /** A burst of sparks in all directions (biased upwards). */
    public static void burst(Vec3 at, int count, float speed, int rgb1, int rgb2, float size, long lifeMs) {
        for (int i = 0; i < count; i++) {
            Vec3 dir = new Vec3(RANDOM.nextGaussian(), Math.abs(RANDOM.nextGaussian()) * 0.8 + 0.2, RANDOM.nextGaussian()).normalize();
            float sp = speed * (0.5f + RANDOM.nextFloat());
            Fx.add(spark(at, dir.scale(sp), RANDOM.nextBoolean() ? rgb1 : rgb2, size * (0.7f + RANDOM.nextFloat() * 0.6f),
                    (long) (lifeMs * (0.6f + RANDOM.nextFloat() * 0.6f)), 6f));
        }
    }

    /** A horizontal ring that expands from r0 to r1 and fades (optionally rising). */
    public static Fx.Effect wave(Vec3 center, float r0, float r1, float width, int rgb, float alpha, long lifeMs, float rise) {
        long born = System.currentTimeMillis();
        return (fx, now) -> {
            float t = (now - born) / (float) lifeMs;
            if (t > 1f) return false;
            float e = 1f - (1f - t) * (1f - t) * (1f - t);
            fx.flatRing(center.add(0, rise * e, 0), r0 + (r1 - r0) * e, width * (1f - t * 0.5f), rgb, alpha * (1f - t));
            return true;
        };
    }

    /** A ring facing the given direction (e.g. towards the attacker) that pops open and fades. */
    public static Fx.Effect facingRing(Vec3 center, Vec3 normal, float r0, float r1, float width, int rgb, float alpha, long lifeMs) {
        Vec3 n = normal.normalize();
        Vec3 u = n.cross(Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 v = n.cross(u).normalize();
        long born = System.currentTimeMillis();
        return (fx, now) -> {
            float t = (now - born) / (float) lifeMs;
            if (t > 1f) return false;
            float e = 1f - (1f - t) * (1f - t);
            fx.ring(center, u, v, r0 + (r1 - r0) * e, width, rgb, alpha * (1f - t), 0f, (float) (Math.PI * 2), 40);
            return true;
        };
    }

    /**
     * An air slash: a crescent in front of {@code eye}, swept from one side to the other, tapering at
     * both ends – like the blade cutting through the air.
     *
     * @param forward look direction, {@code right} its right-hand vector, {@code tilt} radians around forward
     * @param dir     +1 = sweep left→right, -1 = right→left
     */
    public static Fx.Effect slash(Vec3 eye, Vec3 forward, Vec3 right, Vec3 upv, float tilt, int dir, float radius, int rgb, float alpha, long lifeMs) {
        Vec3 r = right.scale(Math.cos(tilt)).add(upv.scale(Math.sin(tilt)));
        Vec3 center = eye.add(forward.scale(-0.15)).add(upv.scale(-0.12));
        float span = (float) Math.toRadians(150);
        long born = System.currentTimeMillis();
        return (fx, now) -> {
            float t = (now - born) / (float) lifeMs;
            if (t > 1f) return false;
            float reveal = Math.min(1f, t / 0.35f);
            float fade = t < 0.35f ? 1f : 1f - (t - 0.35f) / 0.65f;
            int seg = 36;
            List<Vec3> pts = new ArrayList<>(), sides = new ArrayList<>(), core = new ArrayList<>(), coreSides = new ArrayList<>();
            float[] al = new float[seg + 1], cal = new float[seg + 1];
            for (int i = 0; i <= seg; i++) {
                float u = i / (float) seg;
                float ang = dir * (-span / 2 + span * u * reveal);
                Vec3 radial = forward.scale(Math.cos(ang)).add(r.scale(Math.sin(ang)));
                float rr = radius * (1f + 0.15f * t);
                Vec3 p = center.add(radial.scale(rr));
                float taper = (float) Math.sin(Math.PI * u);
                float head = 0.35f + 0.65f * u;                            // brighter towards the blade
                pts.add(p);
                sides.add(radial.scale(0.32f * taper * rr / 1.6f));
                al[i] = alpha * fade * taper * head;
                core.add(p.add(radial.scale(0.08f)));
                coreSides.add(radial.scale(0.06f * taper));
                cal[i] = Math.min(1f, alpha * 1.3f) * fade * taper * head;
            }
            fx.ribbon(pts, sides, rgb, al);
            fx.ribbon(core, coreSides, 0xFFFFFF, cal);
            return true;
        };
    }
}
