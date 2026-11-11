package dev.nautilus.hud.elements;

import dev.nautilus.gui.Shapes;
import dev.nautilus.hud.HudElement;
import dev.nautilus.hud.HudStyle;
import dev.nautilus.hud.Notifications;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Toasts: glass pills with a coloured dot, bold title and message; slide in, fade out. */
public class NotificationsElement extends HudElement {
    private static final float H = 18f, GAP = 4f, MAX_W = 260f;

    public NotificationsElement() {
        super("notifications", "Notifications", true, END, END, 5, 32);
    }

    private List<Notifications.Toast> toasts(boolean editor) {
        if (!editor) return Notifications.active();
        List<Notifications.Toast> demo = new ArrayList<>();
        long now = System.currentTimeMillis();
        demo.add(new Notifications.Toast("TriggerBot", "enabled", HudStyle.GOOD, now, 999));
        demo.add(new Notifications.Toast("Storage", "10 storage blocks @ 10 122 11", 0xFFFFB347, now, 999));
        return demo;
    }

    /** Panel toast: icon tile, title, message below – taller than the glass pill. */
    private static final float PH = 24f;

    private static float h() { return HudStyle.panels() ? PH : H; }

    private static float toastWidth(Notifications.Toast t) {
        if (HudStyle.panels()) {
            float tw = Math.max(HudStyle.width(t.title(), true, 0.82f), HudStyle.width(t.message(), false, 0.66f));
            return Math.min(MAX_W + 20f, Math.max(110f, 4f + 16f + 7f + tw + 10f));
        }
        float w = 14f + HudStyle.width(t.title(), true, HudStyle.TEXT_SCALE) + 5f + HudStyle.width(t.message(), false, HudStyle.TEXT_SCALE) + 9f;
        return Math.min(MAX_W, w);
    }

    @Override
    public float width(boolean editor) {
        float w = 120f;
        for (Notifications.Toast t : toasts(editor)) w = Math.max(w, toastWidth(t));
        return w;
    }

    @Override
    public float height(boolean editor) {
        return Math.max(h(), toasts(editor).size() * (h() + GAP) - GAP);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float x, float y, float dt, boolean editor) {
        List<Notifications.Toast> list = toasts(editor);
        long now = System.currentTimeMillis();
        boolean right = rightAligned(), up = bottomAligned();
        float total = width(editor);
        float H = h();
        float yy = up ? y + height(editor) - H : y;
        for (int i = list.size() - 1; i >= 0; i--) { // newest closest to the anchor edge
            Notifications.Toast t = list.get(i);
            float age = (now - t.created()) / 1000f;
            float in = editor ? 1f : dev.nautilus.hud.Anim.clamp01(age / 0.42f);
            float outT = editor ? 0f : dev.nautilus.hud.Anim.clamp01((age - t.duration()) / 0.4f);
            float w = toastWidth(t);
            // in: slides in with a soft overshoot; out: glides back out to the edge
            float slideIn = 1f - dev.nautilus.hud.Anim.easeOutBack(in), slideOut = dev.nautilus.hud.Anim.easeInCubic(outT);
            float slide = (slideIn + slideOut) * (w + 12f) * (right ? 1 : -1);
            float e = Math.min(1f, in * 3f) * (1f - dev.nautilus.hud.Anim.easeInCubic(outT));
            // the space it takes grows/shrinks smoothly, so the others glide up/down
            float space = editor ? 1f : dev.nautilus.hud.Anim.easeOutCubic(in * 1.6f) * (1f - dev.nautilus.hud.Anim.easeInOutCubic((outT - 0.35f) / 0.65f));
            float x1 = (right ? x + total - w : x) + slide, y1 = yy;
            HudStyle.pill(ctx, s, x1, y1, x1 + w, y1 + H, e);
            if (HudStyle.panels()) {
                panelToast(ctx, s, text, t, x1, y1, w, e, editor, age);
                yy += (up ? -1 : 1) * (H + GAP) * space;
                continue;
            }
            s.circle(x1 + 8f, y1 + H / 2f, 2.6f, Shapes.alpha(t.color(), e));
            // progress line showing how long the toast stays
            if (!editor) {
                float p = Math.max(0f, 1f - age / t.duration());
                s.roundRect(x1 + 6f, y1 + H - 2.4f, x1 + 6f + (w - 12f) * p, y1 + H - 1.4f, 0.5f, Shapes.alpha(t.color() & 0x80FFFFFF, e));
            }
            String title = t.title(), msg = t.message();
            float cy = y1 + H / 2f - 0.5f;
            int tc = Shapes.alpha(HudStyle.TEXT, e), mc2 = Shapes.alpha(HudStyle.TEXT_DIM, e);
            float maxMsg = MAX_W - (14f + HudStyle.width(title, true, HudStyle.TEXT_SCALE) + 5f + 9f);
            String shown = ellipsize(msg, maxMsg);
            text.add(() -> {
                float tx = x1 + 14f;
                HudStyle.text(ctx, title, tx, cy, HudStyle.TEXT_SCALE, tc, true);
                HudStyle.text(ctx, shown, tx + HudStyle.width(title, true, HudStyle.TEXT_SCALE) + 5f, cy, HudStyle.TEXT_SCALE, mc2, false);
            });
            yy += (up ? -1 : 1) * (H + GAP) * e;
        }
    }

    private static void panelToast(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, Notifications.Toast t,
                                   float x1, float y1, float w, float e, boolean editor, float age) {
        boolean on = t.message().equals("enabled"), off = t.message().equals("disabled");
        dev.nautilus.hud.Icons icon = on ? dev.nautilus.hud.Icons.CHECK : off ? dev.nautilus.hud.Icons.CROSS : dev.nautilus.hud.Icons.DOT;
        float bx = x1 + 4f, by = y1 + (PH - 16f) / 2f;
        s.roundRect(bx, by, bx + 16f, by + 16f, 3.5f, Shapes.alpha(0x16FFFFFF, e));
        int ic = on ? 0xFF7CE6A0 : off ? 0xFFB9B4C2 : t.color();
        icon.draw(s, bx + 8f, by + 8f, 9f, Shapes.alpha(ic, e));
        if (!editor) {
            float p = Math.max(0f, 1f - age / t.duration());
            s.roundRect(x1 + w - 4f - (w - 28f) * p, y1 + PH - 2.2f, x1 + w - 4f, y1 + PH - 1.4f, 0.4f, Shapes.alpha(0x40FFFFFF, e));
        }
        String title = t.title(), msg = on ? "Enabled" : off ? "Disabled" : t.message();
        float tx = bx + 16f + 7f, maxW = w - (tx - x1) - 6f;
        String shown = ellipsize(msg, maxW / 0.66f * HudStyle.TEXT_SCALE);
        int tc = Shapes.alpha(HudStyle.TEXT, e), mc2 = Shapes.alpha(HudStyle.TEXT_DIM, e);
        text.add(() -> {
            HudStyle.text(ctx, title, tx, y1 + 8f, 0.82f, tc, true);
            HudStyle.text(ctx, shown, tx, y1 + 16.5f, 0.66f, mc2, false);
        });
    }

    private static String ellipsize(String s, float max) {
        if (HudStyle.width(s, false, HudStyle.TEXT_SCALE) <= max) return s;
        String t = s;
        while (t.length() > 1 && HudStyle.width(t + "…", false, HudStyle.TEXT_SCALE) > max) t = t.substring(0, t.length() - 1);
        return t + "…";
    }
}
