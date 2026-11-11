package dev.nautilus.hud.elements;

import dev.nautilus.gui.Shapes;
import dev.nautilus.hud.ClickTracker;
import dev.nautilus.hud.HudElement;
import dev.nautilus.hud.HudStyle;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/** Keystrokes: W A S D, left/right mouse with CPS and the space bar; pressed keys light up in the accent colour. */
public class KeystrokesElement extends HudElement {
    private static final float K = 18f, G = 2f, W = 3 * K + 2 * G, SPACE = 9f;
    private final float[] glow = new float[7];

    public KeystrokesElement() {
        super("keystrokes", "Keystrokes", false, START, END, 5, 87);
    }

    @Override public float width(boolean editor) { return W; }
    @Override public float height(boolean editor) { return 3 * K + 3 * G + SPACE; }

    private void key(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, int idx, KeyMapping key, boolean editor,
                     float x1, float y1, float x2, float y2, String label, String sub, float dt) {
        boolean down = !editor && key.isDown() && mc.gui.screen() == null;
        glow[idx] += ((down ? 1f : 0f) - glow[idx]) * Math.min(1f, dt * 18f);
        float r = Math.min(5f, (y2 - y1) / 2f);
        HudStyle.pill(ctx, s, x1, y1, x2, y2, 1f);
        if (glow[idx] > 0.02f) s.roundRect(x1, y1, x2, y2, r, Shapes.alpha(HudStyle.accent(0xC0), glow[idx]));
        int color = Shapes.lerp(HudStyle.TEXT, HudStyle.onAccent(), glow[idx]);
        float cx = (x1 + x2) / 2f, cy = (y1 + y2) / 2f;
        text.add(() -> {
            if (sub == null) HudStyle.textCentered(ctx, label, cx, cy, label.length() > 1 ? 0.7f : 0.85f, color, true);
            else {
                HudStyle.textCentered(ctx, label, cx, cy - 3f, 0.7f, color, true);
                HudStyle.textCentered(ctx, sub, cx, cy + 4f, 0.5f, color, false);
            }
        });
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float x, float y, float dt, boolean editor) {
        var o = mc.options;
        float c2 = x + K + G, c3 = x + 2 * (K + G);
        key(ctx, s, text, 0, o.keyUp, editor, c2, y, c2 + K, y + K, "W", null, dt);
        float r2 = y + K + G;
        key(ctx, s, text, 1, o.keyLeft, editor, x, r2, x + K, r2 + K, "A", null, dt);
        key(ctx, s, text, 2, o.keyDown, editor, c2, r2, c2 + K, r2 + K, "S", null, dt);
        key(ctx, s, text, 3, o.keyRight, editor, c3, r2, c3 + K, r2 + K, "D", null, dt);
        float r3 = r2 + K + G, half = (W - G) / 2f;
        key(ctx, s, text, 4, o.keyAttack, editor, x, r3, x + half, r3 + K, "LMB", (editor ? 9 : ClickTracker.left()) + " CPS", dt);
        key(ctx, s, text, 5, o.keyUse, editor, x + half + G, r3, x + W, r3 + K, "RMB", (editor ? 4 : ClickTracker.right()) + " CPS", dt);
        float r4 = r3 + K + G;
        key(ctx, s, text, 6, o.keyJump, editor, x, r4, x + W, r4 + SPACE, "—", null, dt);
    }
}
