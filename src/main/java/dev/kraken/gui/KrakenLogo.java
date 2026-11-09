package dev.kraken.gui;

/**
 * The Kraken logo: an octopus with a round head, big eyes and six tentacles whose tips curl
 * outwards. Drawn as anti-aliased vector shapes, the tentacles wiggle slowly.
 */
public final class KrakenLogo {
    private KrakenLogo() {}

    // base x, curl direction, length, curl strength (in logo units)
    private static final float[][] TENTACLES = {
            {-3.4f, -1, 9.0f, 1.25f}, {-2.0f, -1, 10.0f, 1.0f}, {-0.7f, -1, 10.5f, 0.8f},
            {0.7f, 1, 10.5f, 0.8f}, {2.0f, 1, 10.0f, 1.0f}, {3.4f, 1, 9.0f, 1.25f}};

    /**
     * @param cx,cy centre of the logo
     * @param size  height of the logo in GUI pixels
     * @param time  seconds, drives the tentacle wiggle
     */
    public static void draw(Shapes s, float cx, float cy, float size, float time, int body, int eye, int pupil) {
        float u = size / 17.5f;
        float oy = cy - 0.75f * u; // the logo spans y = -7.8 .. 9.3 units
        // tentacles first, the head covers their roots
        for (int i = 0; i < TENTACLES.length; i++) {
            float[] t = TENTACLES[i];
            int steps = 24;
            float[] pts = new float[(steps + 1) * 3];
            float x = t[0], y = 0.5f, a = (float) Math.PI, ds = t[2] / steps;
            for (int k = 0; k <= steps; k++) {
                float f = (float) k / steps;
                pts[k * 3] = cx + x * u;
                pts[k * 3 + 1] = oy + y * u;
                pts[k * 3 + 2] = (1.5f * (1 - f) + 0.35f * f) * u;
                float wig = (float) Math.sin(time * 2.0 + i * 1.1 + f * 3) * 0.035f;
                float kap = 0.015f + t[3] * (float) Math.pow(f, 3.0) + wig;
                a -= t[1] * kap * ds * 1.7f;
                x += (float) Math.sin(a) * ds;
                y -= (float) Math.cos(a) * ds;
            }
            s.stroke(pts, body);
        }
        // head: dome + body
        s.circle(cx, oy - 3.2f * u, 4.6f * u, body);
        s.roundRect(cx - 4.6f * u, oy - 3.2f * u, cx + 4.6f * u, oy + 1.2f * u, 0.9f * u, body);
        // eyes
        for (int sx = -1; sx <= 1; sx += 2) {
            s.circle(cx + sx * 1.9f * u, oy - 1.4f * u, 1.25f * u, eye);
            s.circle(cx + sx * 1.9f * u + 0.25f * u, oy - 1.2f * u, 0.65f * u, pupil);
        }
    }
}
