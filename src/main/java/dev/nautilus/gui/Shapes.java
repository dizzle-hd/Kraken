package dev.nautilus.gui;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Matrix3x2f;

/**
 * Immediate-mode builder for anti-aliasing-free but smooth vector shapes (annular sectors,
 * circles, rings, rounded rectangles, thick lines) for the GUI.
 * Angles are in radians, measured clockwise from 12 o'clock.
 */
public final class Shapes {
    private static final float TAU_F = (float) (Math.PI * 2);
    /** When > 0 every sector uses exactly this many steps, so touching pieces share vertices (no cracks). */
    private int forcedSteps;

    private static int stepsFor(float angle, float radius) {
        return Math.max(Math.max(2, (int) Math.ceil(angle / (Math.PI * 2 / 48))), (int) Math.ceil(angle * radius / 3f));
    }
    private final FloatArrayList xy = new FloatArrayList();
    private final IntArrayList colors = new IntArrayList();
    private float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

    private void vertex(float x, float y, int color) {
        xy.add(x);
        xy.add(y);
        colors.add(color);
        if (x < minX) minX = x;
        if (y < minY) minY = y;
        if (x > maxX) maxX = x;
        if (y > maxY) maxY = y;
    }

    /** Adds a quad, fixing the winding so it is never back-face culled. */
    public Shapes quad(float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
        float area = (x0 * y1 - x1 * y0) + (x1 * y2 - x2 * y1) + (x2 * y3 - x3 * y2) + (x3 * y0 - x0 * y3);
        if (area <= 0) {
            vertex(x0, y0, c0); vertex(x1, y1, c1); vertex(x2, y2, c2); vertex(x3, y3, c3);
        } else {
            vertex(x0, y0, c0); vertex(x3, y3, c3); vertex(x2, y2, c2); vertex(x1, y1, c1);
        }
        return this;
    }

    public Shapes rect(float x1, float y1, float x2, float y2, int color) {
        return quad(x1, y1, color, x1, y2, color, x2, y2, color, x2, y1, color);
    }

    public Shapes gradient(float x1, float y1, float x2, float y2, int tl, int tr, int bl, int br) {
        return quad(x1, y1, tl, x1, y2, bl, x2, y2, br, x2, y1, tr);
    }

    private static float px(float cx, float r, float a) { return cx + (float) Math.sin(a) * r; }
    private static float py(float cy, float r, float a) { return cy - (float) Math.cos(a) * r; }

    /**
     * Annular sector between {@code rIn} and {@code rOut}. {@code gap} is a constant pixel gap
     * subtracted from both sides, so neighbouring sectors have parallel separators.
     */
    public Shapes sector(float cx, float cy, float rIn, float rOut, float a0, float a1, float gap, int colIn, int colOut) {
        float gIn = rIn > 0.5f ? Math.min((gap / 2f) / rIn, (a1 - a0) / 2f) : 0f;
        float gOut = Math.min((gap / 2f) / rOut, (a1 - a0) / 2f);
        float in0 = a0 + gIn, in1 = a1 - gIn, out0 = a0 + gOut, out1 = a1 - gOut;
        int steps = forcedSteps > 0 ? forcedSteps : stepsFor(a1 - a0, rOut);
        for (int i = 0; i < steps; i++) {
            float t0 = (float) i / steps, t1 = (float) (i + 1) / steps;
            float ai0 = in0 + (in1 - in0) * t0, ai1 = in0 + (in1 - in0) * t1;
            float ao0 = out0 + (out1 - out0) * t0, ao1 = out0 + (out1 - out0) * t1;
            quad(px(cx, rIn, ai0), py(cy, rIn, ai0), colIn,
                    px(cx, rOut, ao0), py(cy, rOut, ao0), colOut,
                    px(cx, rOut, ao1), py(cy, rOut, ao1), colOut,
                    px(cx, rIn, ai1), py(cy, rIn, ai1), colIn);
        }
        return this;
    }

    /** Outline of a {@link #sector} with the same geometry. */
    public Shapes sectorOutline(float cx, float cy, float rIn, float rOut, float a0, float a1, float gap, float width, int color) {
        float gIn = rIn > 0.5f ? Math.min((gap / 2f) / rIn, (a1 - a0) / 2f) : 0f;
        float gOut = Math.min((gap / 2f) / rOut, (a1 - a0) / 2f);
        float in0 = a0 + gIn, in1 = a1 - gIn, out0 = a0 + gOut, out1 = a1 - gOut;
        float h = width / 2f;
        sector(cx, cy, rOut - h, rOut + h, out0, out1, 0, color, color);
        sector(cx, cy, Math.max(0, rIn - h), rIn + h, in0, in1, 0, color, color);
        line(px(cx, rIn, in0), py(cy, rIn, in0), px(cx, rOut, out0), py(cy, rOut, out0), width, color);
        line(px(cx, rIn, in1), py(cy, rIn, in1), px(cx, rOut, out1), py(cy, rOut, out1), width, color);
        return this;
    }

    // ---- anti-aliasing ----------------------------------------------------------------------------

    /** Width of the soft edge in GUI units – one physical pixel (set every frame from the GUI scale). */
    private static float aa = 0.5f;

    public static void setPixelSize(float guiUnitsPerPixel) {
        aa = Math.max(0.05f, guiUnitsPerPixel) * 1.1f;
    }

    /** Radial fringe between r0 (colour c) and r1 (transparent) – fades a circular edge out. */
    private void fringe(float cx, float cy, float rSolid, float rClear, float a0, float a1, int c) {
        int clear = c & 0x00FFFFFF;
        if (rClear > rSolid) sector(cx, cy, rSolid, rClear, a0, a1, 0, c, clear);
        else if (rClear >= 0) sector(cx, cy, rClear, rSolid, a0, a1, 0, clear, c);
    }

    public Shapes circle(float cx, float cy, float r, int color) {
        forcedSteps = stepsFor(TAU_F, r + aa);
        sector(cx, cy, 0, r, 0, TAU_F, 0, color, color);
        fringe(cx, cy, r, r + aa, 0, TAU_F, color);
        forcedSteps = 0;
        return this;
    }

    public Shapes circle(float cx, float cy, float r, int colCenter, int colEdge) {
        forcedSteps = stepsFor(TAU_F, r + aa);
        sector(cx, cy, 0, r, 0, TAU_F, 0, colCenter, colEdge);
        fringe(cx, cy, r, r + aa, 0, TAU_F, colEdge);
        forcedSteps = 0;
        return this;
    }

    public Shapes ring(float cx, float cy, float r, float width, int color) {
        return arc(cx, cy, r, width, 0, TAU_F, color);
    }

    public Shapes arc(float cx, float cy, float r, float width, float a0, float a1, int color) {
        float r0 = r - width / 2f, r1 = r + width / 2f;
        forcedSteps = stepsFor(a1 - a0, r1 + aa);
        sector(cx, cy, r0, r1, a0, a1, 0, color, color);
        fringe(cx, cy, r1, r1 + aa, a0, a1, color);
        fringe(cx, cy, r0, Math.max(0, r0 - aa), a0, a1, color);
        forcedSteps = 0;
        return this;
    }


    /**
     * Tapered, anti-aliased stroke through the given points (x, y, width triples) with a round tip.
     */
    public Shapes stroke(float[] pts, int color) {
        int n = pts.length / 3;
        if (n < 2) return this;
        int clear = color & 0x00FFFFFF;
        float[] nx = new float[n], ny = new float[n];
        for (int i = 0; i < n; i++) {
            int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
            float dx = pts[b * 3] - pts[a * 3], dy = pts[b * 3 + 1] - pts[a * 3 + 1];
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1e-5f) len = 1;
            nx[i] = -dy / len;
            ny[i] = dx / len;
        }
        for (int i = 0; i < n - 1; i++) {
            int j = i + 1;
            float xi = pts[i * 3], yi = pts[i * 3 + 1], hi = pts[i * 3 + 2] / 2f;
            float xj = pts[j * 3], yj = pts[j * 3 + 1], hj = pts[j * 3 + 2] / 2f;
            // body
            quad(xi + nx[i] * hi, yi + ny[i] * hi, color, xj + nx[j] * hj, yj + ny[j] * hj, color,
                    xj - nx[j] * hj, yj - ny[j] * hj, color, xi - nx[i] * hi, yi - ny[i] * hi, color);
            // soft edges on both sides
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                float ai = hi * sgn, aj = hj * sgn, bi = (hi + aa) * sgn, bj = (hj + aa) * sgn;
                quad(xi + nx[i] * ai, yi + ny[i] * ai, color, xj + nx[j] * aj, yj + ny[j] * aj, color,
                        xj + nx[j] * bj, yj + ny[j] * bj, clear, xi + nx[i] * bi, yi + ny[i] * bi, clear);
            }
        }
        circle(pts[(n - 1) * 3], pts[(n - 1) * 3 + 1], pts[(n - 1) * 3 + 2] / 2f, color);
        return this;
    }

    public Shapes line(float x1, float y1, float x2, float y2, float width, int color) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) return this;
        float nx = -dy / len * width / 2f, ny = dx / len * width / 2f;
        return quad(x1 + nx, y1 + ny, color, x2 + nx, y2 + ny, color, x2 - nx, y2 - ny, color, x1 - nx, y1 - ny, color);
    }

    public Shapes roundRect(float x1, float y1, float x2, float y2, float r, int color) {
        r = Math.min(r, Math.min((x2 - x1) / 2f, (y2 - y1) / 2f));
        rect(x1 + r, y1, x2 - r, y2, color);
        rect(x1, y1 + r, x1 + r, y2 - r, color);
        rect(x2 - r, y1 + r, x2, y2 - r, color);
        float q = (float) (Math.PI / 2);
        int clear = color & 0x00FFFFFF;
        float[][] corners = {{x1 + r, y1 + r, 3 * q}, {x2 - r, y1 + r, 0}, {x2 - r, y2 - r, q}, {x1 + r, y2 - r, 2 * q}};
        for (float[] c : corners) {
            sector(c[0], c[1], 0, r, c[2], c[2] + q, 0, color, color);
            fringe(c[0], c[1], r, r + aa, c[2], c[2] + q, color);
        }
        // soft straight edges
        quad(x1 + r, y1 - aa, clear, x1 + r, y1, color, x2 - r, y1, color, x2 - r, y1 - aa, clear);
        quad(x1 + r, y2, color, x1 + r, y2 + aa, clear, x2 - r, y2 + aa, clear, x2 - r, y2, color);
        quad(x1 - aa, y1 + r, clear, x1 - aa, y2 - r, clear, x1, y2 - r, color, x1, y1 + r, color);
        quad(x2, y1 + r, color, x2, y2 - r, color, x2 + aa, y2 - r, clear, x2 + aa, y1 + r, clear);
        return this;
    }


    /**
     * Band around a rounded rectangle at distance [dIn, dOut] from its border (positive = outside),
     * alpha multiplied by mulIn at dIn and mulOut at dOut. Used for rims and soft edges.
     */
    private void roundRectBand(float x1, float y1, float x2, float y2, float r, float dIn, float dOut, int color, float mulIn, float mulOut) {
        int cIn = alpha(color, mulIn), cOut = alpha(color, mulOut);
        float q = (float) (Math.PI / 2);
        float k0 = Math.max(0, r + dIn), k1 = Math.max(0, r + dOut);
        // straight edges
        quad(x1 + r, y1 + r - k0, cIn, x2 - r, y1 + r - k0, cIn, x2 - r, y1 + r - k1, cOut, x1 + r, y1 + r - k1, cOut);
        quad(x1 + r, y2 - r + k0, cIn, x2 - r, y2 - r + k0, cIn, x2 - r, y2 - r + k1, cOut, x1 + r, y2 - r + k1, cOut);
        quad(x1 + r - k0, y1 + r, cIn, x1 + r - k0, y2 - r, cIn, x1 + r - k1, y2 - r, cOut, x1 + r - k1, y1 + r, cOut);
        quad(x2 - r + k0, y1 + r, cIn, x2 - r + k0, y2 - r, cIn, x2 - r + k1, y2 - r, cOut, x2 - r + k1, y1 + r, cOut);
        // corners
        int saved = forcedSteps;
        forcedSteps = stepsFor(q, r + 1f);
        sector(x1 + r, y1 + r, k0, k1, 3 * q, 4 * q, 0, cIn, cOut);
        sector(x2 - r, y1 + r, k0, k1, 0, q, 0, cIn, cOut);
        sector(x2 - r, y2 - r, k0, k1, q, 2 * q, 0, cIn, cOut);
        sector(x1 + r, y2 - r, k0, k1, 2 * q, 3 * q, 0, cIn, cOut);
        forcedSteps = saved;
    }

    /** Anti-aliased rim (inside the border, width w) of a rounded rectangle. */
    public Shapes roundRectRim(float x1, float y1, float x2, float y2, float r, float w, int color) {
        r = Math.min(r, Math.min((x2 - x1) / 2f, (y2 - y1) / 2f));
        w = Math.min(w, r);
        roundRectBand(x1, y1, x2, y2, r, -w, 0, color, 1, 1);
        roundRectBand(x1, y1, x2, y2, r, 0, aa, color, 1, 0);
        if (r - w - aa >= 0) roundRectBand(x1, y1, x2, y2, r, -w - aa, -w, color, 0, 1);
        return this;
    }

    /** Vertical gradient inside a rounded rectangle (glass shine); corners follow the rounding. */
    public Shapes roundRectGradient(float x1, float y1, float x2, float y2, float r, int top, int bottom) {
        r = Math.min(r, Math.min((x2 - x1) / 2f, (y2 - y1) / 2f));
        float h = y2 - y1;
        int midTop = lerp(top, bottom, r / h), midBottom = lerp(top, bottom, (h - r) / h);
        gradient(x1 + r, y1, x2 - r, y1 + r, top, top, midTop, midTop);
        gradient(x1, y1 + r, x2, y2 - r, midTop, midTop, midBottom, midBottom);
        gradient(x1 + r, y2 - r, x2 - r, y2, midBottom, midBottom, bottom, bottom);
        float q = (float) (Math.PI / 2);
        sector(x1 + r, y1 + r, 0, r, 3 * q, 4 * q, 0, midTop, top);
        sector(x2 - r, y1 + r, 0, r, 0, q, 0, midTop, top);
        sector(x2 - r, y2 - r, 0, r, q, 2 * q, 0, midBottom, bottom);
        sector(x1 + r, y2 - r, 0, r, 2 * q, 3 * q, 0, midBottom, bottom);
        return this;
    }


    // ---- rounded annular sectors (glass segments) ------------------------------------------------

    private static float angleOf(float dx, float dy) {
        return (float) Math.atan2(dx, -dy);
    }

    /** Wedge with a radial colour gradient from c0 (at r0) to c1 (at r1). */
    private void wedgeGrad(float px, float py, float r0, float r1, float t1, float t2, int c0, int c1) {
        float d = t2 - t1;
        while (d > Math.PI) d -= TAU_F;
        while (d < -Math.PI) d += TAU_F;
        float a = d >= 0 ? t1 : t1 + d, b = d >= 0 ? t1 + d : t1;
        sector(px, py, r0, r1, a, b, 0, c0, c1);
    }

    private static float clampCorner(float r, float rIn, float rOut) {
        return Math.max(0.5f, Math.min(r, (rOut - rIn) / 2f - 0.5f));
    }

    /**
     * The band of points whose distance to the border of a rounded sector lies in [dIn, dOut]
     * (positive = outside). Colours: {@code colInner} near the inner radius, {@code colOuter} near the
     * outer radius; alpha is multiplied by mulIn at dIn and mulOut at dOut. Used for rims and
     * anti-aliased edges.
     */
    private void roundedBand(float cx, float cy, float rIn, float rOut, float a0, float a1, float gap, float r,
                             float dIn, float dOut, int colInner, int colOuter, float mulIn, float mulOut) {
        float off = gap / 2f + r;
        float ri = rIn + r, ro = rOut - r;
        float oa0 = a0 + off / ro, oa1 = a1 - off / ro;
        float ia0 = a0 + off / ri, ia1 = a1 - off / ri;
        int oIn = alpha(colOuter, mulIn), oOut = alpha(colOuter, mulOut);
        int iIn = alpha(colInner, mulIn), iOut = alpha(colInner, mulOut);
        float dA = Math.max(-r, dIn), dB = Math.max(-r, dOut);
        sector(cx, cy, rOut + dA, rOut + dB, oa0, oa1, 0, oIn, oOut);
        if (rIn - dB >= 0) sector(cx, cy, rIn - dB, rIn - dA, ia0, ia1, 0, iOut, iIn);
        for (int side = 0; side < 2; side++) {
            float a = side == 0 ? a0 : a1;
            float tin = side == 0 ? ia0 : ia1, tout = side == 0 ? oa0 : oa1;
            float pix = px(cx, ri, tin), piy = py(cy, ri, tin);
            float pox = px(cx, ro, tout), poy = py(cy, ro, tout);
            float nx = (float) Math.cos(a) * (side == 0 ? -1 : 1), ny = (float) Math.sin(a) * (side == 0 ? -1 : 1);
            float k0 = r + dA, k1 = r + dB;
            quad(pix + nx * k0, piy + ny * k0, iIn, pox + nx * k0, poy + ny * k0, oIn,
                    pox + nx * k1, poy + ny * k1, oOut, pix + nx * k1, piy + ny * k1, iOut);
            float tn = angleOf(nx, ny);
            int saved = forcedSteps;
            forcedSteps = stepsFor(TAU_F / 4f, r + 1f); // same for every band of this shape
            wedgeGrad(pox, poy, k0, k1, tn, angleOf(pox - cx, poy - cy), oIn, oOut);
            wedgeGrad(pix, piy, k0, k1, tn, angleOf(cx - pix, cy - piy), iIn, iOut);
            forcedSteps = saved;
        }
    }

    /**
     * Annular sector with rounded corners: the Minkowski sum of a shrunken sector with a disk of
     * radius {@code r}. Built from non-overlapping pieces so translucent colours stay even, with a
     * one-pixel anti-aliased edge.
     */
    public Shapes roundedSector(float cx, float cy, float rIn, float rOut, float a0, float a1, float gap, float r, int colIn, int colOut) {
        r = clampCorner(r, rIn, rOut);
        float off = gap / 2f + r;
        float ri = rIn + r, ro = rOut - r;
        if (off / ri * 2 >= a1 - a0) return sector(cx, cy, rIn, rOut, a0, a1, gap, colIn, colOut);
        int cri = lerp(colIn, colOut, (ri - rIn) / (rOut - rIn));
        int cro = lerp(colIn, colOut, (ro - rIn) / (rOut - rIn));
        forcedSteps = stepsFor(a1 - a0, rOut + aa);
        sector(cx, cy, ri, ro, a0, a1, off * 2, cri, cro);                  // core
        roundedBand(cx, cy, rIn, rOut, a0, a1, gap, r, -r, 0, cri, cro, 1, 1); // rounded border zone
        roundedBand(cx, cy, rIn, rOut, a0, a1, gap, r, 0, aa, colIn, colOut, 1, 0); // anti-aliasing
        forcedSteps = 0;
        return this;
    }

    /** Rim of {@link #roundedSector} with the same geometry, anti-aliased on both sides. */
    public Shapes roundedSectorOutline(float cx, float cy, float rIn, float rOut, float a0, float a1, float gap, float r, float w, int color) {
        r = clampCorner(r, rIn, rOut);
        float off = gap / 2f + r;
        if (off / (rIn + r) * 2 >= a1 - a0) return sectorOutline(cx, cy, rIn, rOut, a0, a1, gap, w, color);
        forcedSteps = stepsFor(a1 - a0, rOut + aa);
        roundedBand(cx, cy, rIn, rOut, a0, a1, gap, r, -w, 0, color, color, 1, 1);
        roundedBand(cx, cy, rIn, rOut, a0, a1, gap, r, 0, aa, color, color, 1, 0);
        roundedBand(cx, cy, rIn, rOut, a0, a1, gap, r, -w - aa, -w, color, color, 0, 1);
        forcedSteps = 0;
        return this;
    }

    /**
     * Even glow around an arc with round ends (the shape of a "capsule" bent along a circle): fades from
     * {@code color} at the arc border to transparent {@code width} further out, on both sides and
     * around both ends, without overlapping pieces (so the glow is equally strong everywhere).
     */
    public Shapes ringGlow(float cx, float cy, float r, float lineWidth, float a0, float a1, float width, int color) {
        if (a1 - a0 < 1e-4f) return this;
        int clear = color & 0x00FFFFFF;
        float h = lineWidth / 2f;
        // outside and inside of the arc
        sector(cx, cy, r + h, r + h + width, a0, a1, 0, color, clear);
        sector(cx, cy, Math.max(0, r - h - width), r - h, a0, a1, 0, clear, color);
        // half-discs around the two ends, facing away from the arc
        float sx = cx + (float) Math.sin(a0) * r, sy = cy - (float) Math.cos(a0) * r;
        float ex = cx + (float) Math.sin(a1) * r, ey = cy - (float) Math.cos(a1) * r;
        float pi = (float) Math.PI;
        sector(sx, sy, h, h + width, a0 - pi, a0, 0, color, clear);
        sector(ex, ey, h, h + width, a1, a1 + pi, 0, color, clear);
        return this;
    }

    /** Sends everything to the GUI renderer using the current matrix and scissor of {@code ctx}. */
    public void submit(GuiGraphicsExtractor ctx) {
        if (colors.isEmpty()) return;
        Matrix3x2f pose = new Matrix3x2f(ctx.pose());
        ScreenRectangle scissor = ctx.scissorStack.peek();
        int bx = (int) Math.floor(minX), by = (int) Math.floor(minY);
        ScreenRectangle bounds = new ScreenRectangle(bx, by, Math.max(1, (int) Math.ceil(maxX) - bx), Math.max(1, (int) Math.ceil(maxY) - by))
                .transformMaxBounds(pose);
        if (scissor != null) bounds = scissor.intersection(bounds);
        ctx.guiRenderState.addGuiElement(new ShapeRenderState(pose, xy.toFloatArray(), colors.toIntArray(), colors.size(), scissor, bounds));
        xy.clear();
        colors.clear();
        minX = minY = Float.MAX_VALUE;
        maxX = maxY = -Float.MAX_VALUE;
    }

    // ---- colour helpers --------------------------------------------------------------------------

    public static int alpha(int argb, float mul) {
        int a = (int) (((argb >>> 24) & 0xFF) * Math.max(0, Math.min(1, mul)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int lerp(int c1, int c2, float t) {
        t = Math.max(0, Math.min(1, t));
        int a = (int) (((c1 >>> 24) & 0xFF) + ((((c2 >>> 24) & 0xFF) - ((c1 >>> 24) & 0xFF)) * t));
        int r = (int) (((c1 >> 16) & 0xFF) + ((((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t));
        int g = (int) (((c1 >> 8) & 0xFF) + ((((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t));
        int b = (int) ((c1 & 0xFF) + (((c2 & 0xFF) - (c1 & 0xFF)) * t));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
