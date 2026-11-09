package dev.nautilus.gui;

/**
 * The Nautilus Client logo: a nautilus shell (logarithmic spiral, growth x3 per turn) with white
 * chamber walls and a dark opening, drawn as anti-aliased vector shapes. It rocks gently.
 */
public final class NautilusLogo {
    private NautilusLogo() {}

    private static final double B = Math.log(3.0) / (2 * Math.PI);
    private static final double T = 2 * Math.PI * 2.0 + 0.35;
    private static final int OUTER_STEPS = 72, COIL_STEPS = 90, SEPTA = 9;

    // geometry in a unit box (-0.5..0.5), computed once
    private static final float[] OX = new float[OUTER_STEPS + 1], OY = new float[OUTER_STEPS + 1];
    private static final float[] CX = new float[COIL_STEPS + 1], CY = new float[COIL_STEPS + 1], CW = new float[COIL_STEPS + 1];
    private static final float[][] SEPTUM = new float[SEPTA - 1][];
    private static final float CENTER_X, CENTER_Y;
    private static final float[] MOUTH;

    private static double r(double t) { return Math.exp(B * t); }

    static {
        double minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
        double[] xs = new double[OUTER_STEPS + 1], ys = new double[OUTER_STEPS + 1];
        for (int i = 0; i <= OUTER_STEPS; i++) {
            double t = T - 2 * Math.PI + 2 * Math.PI * i / OUTER_STEPS;
            xs[i] = r(t) * Math.cos(t);
            ys[i] = -r(t) * Math.sin(t);
            minX = Math.min(minX, xs[i]); maxX = Math.max(maxX, xs[i]);
            minY = Math.min(minY, ys[i]); maxY = Math.max(maxY, ys[i]);
        }
        double k = 1.0 / Math.max(maxX - minX, maxY - minY);
        double ox = (maxX + minX) / 2, oy = (maxY + minY) / 2;
        for (int i = 0; i <= OUTER_STEPS; i++) {
            OX[i] = (float) ((xs[i] - ox) * k);
            OY[i] = (float) ((ys[i] - oy) * k);
        }
        CENTER_X = (float) (-ox * k);
        CENTER_Y = (float) (-oy * k);
        double r0 = r(T - 2 * Math.PI);
        for (int i = 0; i <= COIL_STEPS; i++) {
            double t = T - 2 * Math.PI - 4 * Math.PI * i / COIL_STEPS;
            CX[i] = (float) ((r(t) * Math.cos(t) - ox) * k);
            CY[i] = (float) ((-r(t) * Math.sin(t) - oy) * k);
            CW[i] = (float) Math.max(0.006, 0.024 * r(t) / r0);
        }
        for (int j = 1; j < SEPTA; j++) {
            double tk = T - 2 * Math.PI + (2 * Math.PI - 0.5) * j / SEPTA;
            double x0 = r(tk - 2 * Math.PI) * Math.cos(tk), y0 = -r(tk - 2 * Math.PI) * Math.sin(tk);
            double x1 = r(tk) * Math.cos(tk), y1 = -r(tk) * Math.sin(tk);
            double bx = 0.62 * r(tk - 0.35) * Math.cos(tk - 0.35), by = -0.62 * r(tk - 0.35) * Math.sin(tk - 0.35);
            double qx = ((x0 + x1) / 2 + bx) / 2, qy = ((y0 + y1) / 2 + by) / 2;
            int n = 10;
            float[] pts = new float[(n + 1) * 3];
            float w = (float) (0.017 * (0.55 + 0.45 * j / SEPTA));
            for (int s = 0; s <= n; s++) {
                double u = (double) s / n;
                double x = (1 - u) * (1 - u) * x0 + 2 * (1 - u) * u * qx + u * u * x1;
                double y = (1 - u) * (1 - u) * y0 + 2 * (1 - u) * u * qy + u * u * y1;
                pts[s * 3] = (float) ((x - ox) * k);
                pts[s * 3 + 1] = (float) ((y - oy) * k);
                pts[s * 3 + 2] = w;
            }
            SEPTUM[j - 1] = pts;
        }
        // opening: flat ellipse across the line between the last two edge points
        float ax = OX[0], ay = OY[0], bx = OX[OUTER_STEPS], by = OY[OUTER_STEPS];
        MOUTH = new float[]{(ax + bx) / 2, (ay + by) / 2, (float) Math.hypot(bx - ax, by - ay) / 2, (float) Math.atan2(by - ay, bx - ax)};
    }

    /**
     * @param cx,cy centre of the logo
     * @param size  size of the logo in GUI pixels
     * @param time  seconds, drives the gentle rocking
     * @param body  shell colour, {@code lines} chamber walls, {@code mouth} opening
     */
    public static void draw(Shapes s, float cx, float cy, float size, float time, int body, int lines, int mouth) {
        float a = (float) Math.sin(time * 1.3) * 0.06f;
        float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
        // shell: triangle fan from the spiral centre (the outline is star-shaped around it)
        float px = tx(CENTER_X, CENTER_Y, cos, sin, size, cx), py = ty(CENTER_X, CENTER_Y, cos, sin, size, cy);
        for (int i = 0; i < OUTER_STEPS; i++) {
            float x1 = tx(OX[i], OY[i], cos, sin, size, cx), y1 = ty(OX[i], OY[i], cos, sin, size, cy);
            float x2 = tx(OX[i + 1], OY[i + 1], cos, sin, size, cx), y2 = ty(OX[i + 1], OY[i + 1], cos, sin, size, cy);
            s.quad(px, py, body, x1, y1, body, x2, y2, body, px, py, body);
        }
        // inner coils
        float[] coil = new float[(COIL_STEPS + 1) * 3];
        for (int i = 0; i <= COIL_STEPS; i++) {
            coil[i * 3] = tx(CX[i], CY[i], cos, sin, size, cx);
            coil[i * 3 + 1] = ty(CX[i], CY[i], cos, sin, size, cy);
            coil[i * 3 + 2] = Math.max(0.5f, CW[i] * size);
        }
        s.stroke(coil, lines);
        // chamber walls
        for (float[] sp : SEPTUM) {
            float[] p = new float[sp.length];
            for (int i = 0; i < sp.length; i += 3) {
                p[i] = tx(sp[i], sp[i + 1], cos, sin, size, cx);
                p[i + 1] = ty(sp[i], sp[i + 1], cos, sin, size, cy);
                p[i + 2] = Math.max(0.45f, sp[i + 2] * size);
            }
            s.stroke(p, lines);
        }
        // opening
        float mx = MOUTH[0], my = MOUTH[1], rx = MOUTH[2] * 0.98f, ry = MOUTH[2] * 0.3f, ang = MOUTH[3];
        int n = 24;
        float ecx = tx(mx, my, cos, sin, size, cx), ecy = ty(mx, my, cos, sin, size, cy);
        float prevX = 0, prevY = 0;
        for (int i = 0; i <= n; i++) {
            double u = 2 * Math.PI * i / n;
            float ex = (float) (rx * Math.cos(u)), ey = (float) (ry * Math.sin(u));
            float lx = mx + ex * (float) Math.cos(ang) - ey * (float) Math.sin(ang);
            float ly = my + ex * (float) Math.sin(ang) + ey * (float) Math.cos(ang);
            float x = tx(lx, ly, cos, sin, size, cx), y = ty(lx, ly, cos, sin, size, cy);
            if (i > 0) s.quad(ecx, ecy, mouth, prevX, prevY, mouth, x, y, mouth, ecx, ecy, mouth);
            prevX = x;
            prevY = y;
        }
    }

    private static float tx(float x, float y, float cos, float sin, float size, float cx) {
        return cx + (x * cos - y * sin) * size;
    }

    private static float ty(float x, float y, float cos, float sin, float size, float cy) {
        return cy + (x * sin + y * cos) * size;
    }
}
