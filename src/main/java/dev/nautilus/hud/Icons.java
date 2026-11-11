package dev.nautilus.hud;

import dev.nautilus.gui.Shapes;

/** Small vector icons for the panel HUD, drawn with {@link Shapes} (size = edge length in GUI pixels). */
public enum Icons {
    HEART, FOOD, SHIELD, STAR, CLOCK, MONITOR, SIGNAL, SERVER, CUBE, BUBBLE, CHECK, CROSS, KEY, FLASK, BOLT, COMPASS, SKULL, DOT;

    public void draw(Shapes s, float cx, float cy, float size, int c) {
        float h = size / 2f, w = Math.max(0.8f, size * 0.11f);
        switch (this) {
            case HEART -> {
                float r = h * 0.48f;
                s.circle(cx - r * 0.95f, cy - h * 0.2f, r, c);
                s.circle(cx + r * 0.95f, cy - h * 0.2f, r, c);
                s.quad(cx - h * 0.93f, cy - h * 0.05f, c, cx + h * 0.93f, cy - h * 0.05f, c, cx, cy + h * 0.85f, c, cx, cy + h * 0.85f, c);
            }
            case FOOD -> {
                s.circle(cx + h * 0.22f, cy - h * 0.22f, h * 0.48f, c);
                s.line(cx - h * 0.1f, cy + h * 0.1f, cx - h * 0.62f, cy + h * 0.62f, w * 1.2f, c);
                s.circle(cx - h * 0.72f, cy + h * 0.5f, h * 0.17f, c);
                s.circle(cx - h * 0.5f, cy + h * 0.72f, h * 0.17f, c);
            }
            case SHIELD -> {
                float t = cy - h * 0.8f, m = cy + h * 0.1f, b = cy + h * 0.85f, x1 = cx - h * 0.7f, x2 = cx + h * 0.7f;
                s.line(x1, t, x2, t, w, c);
                s.line(x1, t, x1, m, w, c);
                s.line(x2, t, x2, m, w, c);
                s.line(x1, m, cx, b, w, c);
                s.line(x2, m, cx, b, w, c);
            }
            case STAR -> {
                s.quad(cx, cy - h * 0.9f, c, cx + h * 0.22f, cy, c, cx, cy + h * 0.9f, c, cx - h * 0.22f, cy, c);
                s.quad(cx - h * 0.9f, cy, c, cx, cy - h * 0.22f, c, cx + h * 0.9f, cy, c, cx, cy + h * 0.22f, c);
            }
            case CLOCK -> {
                s.ring(cx, cy, h * 0.78f, w, c);
                s.line(cx, cy, cx, cy - h * 0.45f, w, c);
                s.line(cx, cy, cx + h * 0.35f, cy + h * 0.2f, w, c);
            }
            case MONITOR -> {
                s.roundRectRim(cx - h * 0.85f, cy - h * 0.7f, cx + h * 0.85f, cy + h * 0.35f, h * 0.15f, w, c);
                s.line(cx, cy + h * 0.35f, cx, cy + h * 0.7f, w, c);
                s.line(cx - h * 0.4f, cy + h * 0.75f, cx + h * 0.4f, cy + h * 0.75f, w, c);
            }
            case SIGNAL -> {
                for (int i = 0; i < 3; i++) {
                    float bx = cx - h * 0.6f + i * h * 0.55f, top = cy + h * 0.7f - (i + 1) * h * 0.45f;
                    s.roundRect(bx - w * 0.8f, top, bx + w * 0.8f, cy + h * 0.7f, w * 0.6f, c);
                }
            }
            case SERVER -> {
                s.roundRectRim(cx - h * 0.8f, cy - h * 0.75f, cx + h * 0.8f, cy - h * 0.05f, h * 0.15f, w, c);
                s.roundRectRim(cx - h * 0.8f, cy + h * 0.05f, cx + h * 0.8f, cy + h * 0.75f, h * 0.15f, w, c);
                s.circle(cx - h * 0.45f, cy - h * 0.4f, w, c);
                s.circle(cx - h * 0.45f, cy + h * 0.4f, w, c);
            }
            case CUBE -> {
                float r = h * 0.8f;
                float[] xs = new float[6], ys = new float[6];
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI / 6 + i * Math.PI / 3;
                    xs[i] = cx + (float) Math.cos(a) * r;
                    ys[i] = cy + (float) Math.sin(a) * r;
                }
                for (int i = 0; i < 6; i++) s.line(xs[i], ys[i], xs[(i + 1) % 6], ys[(i + 1) % 6], w, c);
                s.line(cx, cy, xs[1], ys[1], w, c);   // down
                s.line(cx, cy, xs[3], ys[3], w, c);   // up-left
                s.line(cx, cy, xs[5], ys[5], w, c);   // up-right
            }
            case BUBBLE -> {
                s.ring(cx - h * 0.15f, cy + h * 0.1f, h * 0.55f, w, c);
                s.ring(cx + h * 0.5f, cy - h * 0.5f, h * 0.25f, w, c);
            }
            case CHECK -> {
                s.line(cx - h * 0.6f, cy, cx - h * 0.15f, cy + h * 0.45f, w * 1.3f, c);
                s.line(cx - h * 0.15f, cy + h * 0.45f, cx + h * 0.65f, cy - h * 0.45f, w * 1.3f, c);
            }
            case CROSS -> {
                s.line(cx - h * 0.5f, cy - h * 0.5f, cx + h * 0.5f, cy + h * 0.5f, w * 1.2f, c);
                s.line(cx - h * 0.5f, cy + h * 0.5f, cx + h * 0.5f, cy - h * 0.5f, w * 1.2f, c);
            }
            case KEY -> {
                s.ring(cx - h * 0.4f, cy, h * 0.35f, w, c);
                s.line(cx - h * 0.05f, cy, cx + h * 0.8f, cy, w, c);
                s.line(cx + h * 0.55f, cy, cx + h * 0.55f, cy + h * 0.35f, w, c);
                s.line(cx + h * 0.8f, cy, cx + h * 0.8f, cy + h * 0.3f, w, c);
            }
            case FLASK -> {
                s.ring(cx, cy + h * 0.25f, h * 0.55f, w, c);
                s.line(cx - h * 0.2f, cy - h * 0.3f, cx - h * 0.2f, cy - h * 0.8f, w, c);
                s.line(cx + h * 0.2f, cy - h * 0.3f, cx + h * 0.2f, cy - h * 0.8f, w, c);
                s.line(cx - h * 0.35f, cy - h * 0.8f, cx + h * 0.35f, cy - h * 0.8f, w, c);
            }
            case BOLT -> {
                s.quad(cx + h * 0.15f, cy - h * 0.9f, c, cx + h * 0.05f, cy - h * 0.05f, c, cx - h * 0.55f, cy + h * 0.05f, c, cx - h * 0.55f, cy + h * 0.05f, c);
                s.quad(cx + h * 0.55f, cy - h * 0.05f, c, cx - h * 0.15f, cy + h * 0.9f, c, cx - h * 0.05f, cy + h * 0.05f, c, cx - h * 0.05f, cy + h * 0.05f, c);
                s.quad(cx - h * 0.55f, cy + h * 0.05f, c, cx + h * 0.05f, cy - h * 0.15f, c, cx + h * 0.55f, cy - h * 0.05f, c, cx - h * 0.05f, cy + h * 0.15f, c);
            }
            case COMPASS -> {
                s.ring(cx, cy, h * 0.8f, w, c);
                s.quad(cx, cy - h * 0.55f, c, cx + h * 0.2f, cy, c, cx, cy + h * 0.55f, Shapes.alpha(c, 0.4f), cx - h * 0.2f, cy, c);
            }
            case SKULL -> {
                s.circle(cx, cy - h * 0.15f, h * 0.65f, c);
                s.roundRect(cx - h * 0.4f, cy + h * 0.2f, cx + h * 0.4f, cy + h * 0.75f, h * 0.15f, c);
                s.circle(cx - h * 0.25f, cy - h * 0.1f, h * 0.17f, 0xFF15131A);
                s.circle(cx + h * 0.25f, cy - h * 0.1f, h * 0.17f, 0xFF15131A);
            }
            case DOT -> s.circle(cx, cy, h * 0.4f, c);
        }
    }
}
