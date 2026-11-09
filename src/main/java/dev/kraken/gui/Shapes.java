package dev.kraken.gui;

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
        int steps = Math.max(Math.max(2, (int) Math.ceil((a1 - a0) / (Math.PI * 2 / 40))), (int) Math.ceil((a1 - a0) * rOut / 4f));
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

    public Shapes circle(float cx, float cy, float r, int color) {
        return sector(cx, cy, 0, r, 0, (float) (Math.PI * 2), 0, color, color);
    }

    public Shapes circle(float cx, float cy, float r, int colCenter, int colEdge) {
        return sector(cx, cy, 0, r, 0, (float) (Math.PI * 2), 0, colCenter, colEdge);
    }

    public Shapes ring(float cx, float cy, float r, float width, int color) {
        return sector(cx, cy, r - width / 2f, r + width / 2f, 0, (float) (Math.PI * 2), 0, color, color);
    }

    public Shapes arc(float cx, float cy, float r, float width, float a0, float a1, int color) {
        return sector(cx, cy, r - width / 2f, r + width / 2f, a0, a1, 0, color, color);
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
        sector(x1 + r, y1 + r, 0, r, 3 * q, 4 * q, 0, color, color);
        sector(x2 - r, y1 + r, 0, r, 0, q, 0, color, color);
        sector(x2 - r, y2 - r, 0, r, q, 2 * q, 0, color, color);
        sector(x1 + r, y2 - r, 0, r, 2 * q, 3 * q, 0, color, color);
        return this;
    }

    public Shapes roundRectOutline(float x1, float y1, float x2, float y2, float r, float w, int color) {
        r = Math.min(r, Math.min((x2 - x1) / 2f, (y2 - y1) / 2f));
        rect(x1 + r, y1, x2 - r, y1 + w, color);
        rect(x1 + r, y2 - w, x2 - r, y2, color);
        rect(x1, y1 + r, x1 + w, y2 - r, color);
        rect(x2 - w, y1 + r, x2, y2 - r, color);
        float q = (float) (Math.PI / 2);
        sector(x1 + r, y1 + r, r - w, r, 3 * q, 4 * q, 0, color, color);
        sector(x2 - r, y1 + r, r - w, r, 0, q, 0, color, color);
        sector(x2 - r, y2 - r, r - w, r, q, 2 * q, 0, color, color);
        sector(x1 + r, y2 - r, r - w, r, 2 * q, 3 * q, 0, color, color);
        return this;
    }


    // ---- rounded annular sectors (glass segments) ------------------------------------------------

    private static float angleOf(float dx, float dy) {
        return (float) Math.atan2(dx, -dy);
    }

    /** Adds the wedge of radius r around (px,py) between the directions of angles t1 and t2 (shortest way). */
    private void wedge(float px, float py, float rIn, float rOut, float t1, float t2, int color) {
        float d = t2 - t1;
        while (d > Math.PI) d -= (float) (Math.PI * 2);
        while (d < -Math.PI) d += (float) (Math.PI * 2);
        float a = d >= 0 ? t1 : t1 + d, b = d >= 0 ? t1 + d : t1;
        sector(px, py, rIn, rOut, a, b, 0, color, color);
    }

    /**
     * Annular sector with rounded corners: the Minkowski sum of a shrunken sector with a disk of
     * radius {@code r}. Built from non-overlapping pieces so translucent colours stay even.
     */
    public Shapes roundedSector(float cx, float cy, float rIn, float rOut, float a0, float a1, float gap, float r, int colIn, int colOut) {
        r = Math.max(0.5f, Math.min(r, (rOut - rIn) / 2f - 0.5f));
        float off = gap / 2f + r;
        float ri = rIn + r, ro = rOut - r;
        float span = a1 - a0;
        if (off / ri * 2 >= span) { // too thin for rounded corners
            return sector(cx, cy, rIn, rOut, a0, a1, gap, colIn, colOut);
        }
        int cri = lerp(colIn, colOut, (ri - rIn) / (rOut - rIn));
        int cro = lerp(colIn, colOut, (ro - rIn) / (rOut - rIn));
        // core
        sector(cx, cy, ri, ro, a0, a1, off * 2, cri, cro);
        // outer & inner bands
        float oa0 = a0 + off / ro, oa1 = a1 - off / ro;
        float ia0 = a0 + off / ri, ia1 = a1 - off / ri;
        sector(cx, cy, ro, rOut, oa0, oa1, 0, cro, colOut);
        sector(cx, cy, rIn, ri, ia0, ia1, 0, colIn, cri);
        // side bands + corner wedges
        for (int side = 0; side < 2; side++) {
            float a = side == 0 ? a0 : a1;
            float tin = side == 0 ? ia0 : ia1, tout = side == 0 ? oa0 : oa1;
            float pix = px(cx, ri, tin), piy = py(cy, ri, tin);
            float pox = px(cx, ro, tout), poy = py(cy, ro, tout);
            // outward normal of this side (perpendicular to the radial line)
            float nx = (float) Math.cos(a) * (side == 0 ? -1 : 1), ny = (float) Math.sin(a) * (side == 0 ? -1 : 1);
            quad(pix, piy, cri, pox, poy, cro, pox + nx * r, poy + ny * r, cro, pix + nx * r, piy + ny * r, cri);
            float tn = angleOf(nx, ny);
            wedge(pox, poy, 0, r, tn, angleOf(pox - cx, poy - cy), cro);
            wedge(pix, piy, 0, r, tn, angleOf(cx - pix, cy - piy), cri);
        }
        return this;
    }

    /** Outline (rim) of {@link #roundedSector} with the same geometry. */
    public Shapes roundedSectorOutline(float cx, float cy, float rIn, float rOut, float a0, float a1, float gap, float r, float w, int color) {
        r = Math.max(0.5f, Math.min(r, (rOut - rIn) / 2f - 0.5f));
        float off = gap / 2f + r;
        float ri = rIn + r, ro = rOut - r;
        if (off / ri * 2 >= a1 - a0) return sectorOutline(cx, cy, rIn, rOut, a0, a1, gap, w, color);
        float oa0 = a0 + off / ro, oa1 = a1 - off / ro;
        float ia0 = a0 + off / ri, ia1 = a1 - off / ri;
        sector(cx, cy, rOut - w, rOut, oa0, oa1, 0, color, color);
        sector(cx, cy, rIn, rIn + w, ia0, ia1, 0, color, color);
        for (int side = 0; side < 2; side++) {
            float a = side == 0 ? a0 : a1;
            float tin = side == 0 ? ia0 : ia1, tout = side == 0 ? oa0 : oa1;
            float pix = px(cx, ri, tin), piy = py(cy, ri, tin);
            float pox = px(cx, ro, tout), poy = py(cy, ro, tout);
            float nx = (float) Math.cos(a) * (side == 0 ? -1 : 1), ny = (float) Math.sin(a) * (side == 0 ? -1 : 1);
            quad(pix + nx * (r - w), piy + ny * (r - w), color, pox + nx * (r - w), poy + ny * (r - w), color,
                    pox + nx * r, poy + ny * r, color, pix + nx * r, piy + ny * r, color);
            float tn = angleOf(nx, ny);
            wedge(pox, poy, r - w, r, tn, angleOf(pox - cx, poy - cy), color);
            wedge(pix, piy, r - w, r, tn, angleOf(cx - pix, cy - piy), color);
        }
        return this;
    }

    /** Soft radial glow: a ring fading from {@code color} at r to transparent at r + width. */
    public Shapes glow(float cx, float cy, float r, float width, float a0, float a1, int color) {
        int clear = color & 0x00FFFFFF;
        sector(cx, cy, r, r + width, a0, a1, 0, color, clear);
        sector(cx, cy, Math.max(0, r - width), r, a0, a1, 0, clear, color);
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
