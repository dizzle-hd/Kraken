package dev.nautilus.hud;

/** Estimates the server TPS from the interval of the time packets (sent every 20 ticks). */
public final class TpsTracker {
    private static final float[] samples = new float[8];
    private static int count, index;
    private static long last = -1;

    private TpsTracker() {}

    public static void onTimePacket() {
        long now = System.nanoTime();
        if (last > 0) {
            float seconds = (now - last) / 1_000_000_000f;
            if (seconds > 0.05f) {
                samples[index] = Math.min(20f, 20f / seconds);
                index = (index + 1) % samples.length;
                count = Math.min(samples.length, count + 1);
            }
        }
        last = now;
    }

    public static void reset() {
        count = 0;
        index = 0;
        last = -1;
    }

    public static float tps() {
        if (count == 0) return 20f;
        float sum = 0;
        for (int i = 0; i < count; i++) sum += samples[i];
        return sum / count;
    }
}
