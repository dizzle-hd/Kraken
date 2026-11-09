package dev.kraken.hud.elements;

import dev.kraken.gui.Shapes;
import dev.kraken.hud.HudElement;
import dev.kraken.hud.HudStyle;
import dev.kraken.hud.Notifications;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Toasts: glass pills with a coloured dot, bold title and message; slide in, fade out. */
public class NotificationsElement extends HudElement {
    private static final float H = 18f, GAP = 4f, MAX_W = 260f;

    public NotificationsElement() {
        super("notifications", "Benachrichtigungen", true, END, END, 5, 30);
    }

    private List<Notifications.Toast> toasts(boolean editor) {
        if (!editor) return Notifications.active();
        List<Notifications.Toast> demo = new ArrayList<>();
        long now = System.currentTimeMillis();
        demo.add(new Notifications.Toast("SilentKillAura", "aktiviert", HudStyle.GOOD, now, 999));
        demo.add(new Notifications.Toast("Lager", "10 Lager-Blöcke @ 10 122 11", 0xFFFFB347, now, 999));
        return demo;
    }

    private static float toastWidth(Notifications.Toast t) {
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
        return Math.max(H, toasts(editor).size() * (H + GAP) - GAP);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float x, float y, float dt, boolean editor) {
        List<Notifications.Toast> list = toasts(editor);
        long now = System.currentTimeMillis();
        boolean right = rightAligned(), up = bottomAligned();
        float total = width(editor);
        float yy = up ? y + height(editor) - H : y;
        for (int i = list.size() - 1; i >= 0; i--) { // newest closest to the anchor edge
            Notifications.Toast t = list.get(i);
            float age = (now - t.created()) / 1000f;
            float in = editor ? 1f : Math.min(1f, age / 0.25f);
            float out = editor ? 1f : Math.max(0f, Math.min(1f, (t.duration() + 0.35f - age) / 0.35f));
            float e = (1 - (float) Math.pow(1 - in, 3)) * out;
            float w = toastWidth(t);
            float slide = (1 - (1 - (float) Math.pow(1 - in, 3))) * (w + 12f) * (right ? 1 : -1);
            float x1 = (right ? x + total - w : x) + slide, y1 = yy;
            HudStyle.pill(s, x1, y1, x1 + w, y1 + H, e);
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

    private static String ellipsize(String s, float max) {
        if (HudStyle.width(s, false, HudStyle.TEXT_SCALE) <= max) return s;
        String t = s;
        while (t.length() > 1 && HudStyle.width(t + "…", false, HudStyle.TEXT_SCALE) > max) t = t.substring(0, t.length() - 1);
        return t + "…";
    }
}
