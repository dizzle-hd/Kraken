package dev.nautilus.hud;

/**
 * Small animation toolkit for the HUD: frame-rate independent smoothing, easing curves and a damped
 * spring (slight overshoot, settles softly) – so nothing on screen jumps.
 */
public final class Anim {
    private Anim() {}

    /** Exponential approach: frame-rate independent ("speed" ≈ 1 / time constant in seconds). */
    public static float approach(float current, float target, float dt, float speed) {
        return current + (target - current) * (1f - (float) Math.exp(-speed * dt));
    }

    public static float clamp01(float t) { return Math.max(0f, Math.min(1f, t)); }

    public static float easeOutCubic(float t) { t = clamp01(t); float u = 1f - t; return 1f - u * u * u; }

    public static float easeInCubic(float t) { t = clamp01(t); return t * t * t; }

    public static float easeInOutCubic(float t) {
        t = clamp01(t);
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    /** Overshoots a little and settles – the "pop" of things that appear. */
    public static float easeOutBack(float t) {
        t = clamp01(t);
        float c1 = 1.7f, c3 = c1 + 1f, u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    /** A short bump 0 → 1 → 0 over the time t (0-1): for pops and flashes. */
    public static float bump(float t) {
        t = clamp01(t);
        return (float) Math.sin(Math.PI * easeOutCubic(t));
    }

    /** Damped spring towards a target: smooth like approach(), but with a soft overshoot. */
    public static final class Spring {
        public float value = Float.NaN, velocity;
        private final float stiffness, damping;

        public Spring(float stiffness, float damping) {
            this.stiffness = stiffness;
            this.damping = damping;
        }

        public float update(float target, float dt) {
            if (Float.isNaN(value)) { value = target; return value; }
            // sub-steps keep it stable at low frame rates
            int steps = Math.max(1, (int) Math.ceil(dt / 0.008f));
            float h = dt / steps;
            for (int i = 0; i < steps; i++) {
                float a = stiffness * (target - value) - damping * velocity;
                velocity += a * h;
                value += velocity * h;
            }
            return value;
        }
    }
}
