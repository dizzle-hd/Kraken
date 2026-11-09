package dev.kraken.module.client;

import dev.kraken.gui.Shapes;
import dev.kraken.gui.UiText;
import dev.kraken.module.Category;
import dev.kraken.module.Module;
import dev.kraken.module.ModuleManager;
import dev.kraken.setting.BoolSetting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Watermark + list of active modules in the style of the wheel: rounded frosted-glass pills with a
 * light rim, the clean Nunito font and orange accents. Modules slide in and out when toggled.
 */
public class Hud extends Module {
    private static final int GLASS_TOP = 0xAE4E1E10;
    private static final int GLASS_BOTTOM = 0xC42C0F08;
    private static final int RIM = 0x30FFFFFF;
    private static final int SHINE = 0x16FFFFFF;
    private static final int ACCENT = 0xFFFF5A2A;
    private static final int TEXT = 0xFFF6ECE8;
    private static final int TEXT_DIM = 0xB4F0D8D0;

    private final BoolSetting watermark = add(new BoolSetting("Wasserzeichen", "\"Kraken\" oben links anzeigen", true));
    private final BoolSetting moduleList = add(new BoolSetting("Modulliste", "Aktive Module oben rechts anzeigen", true));

    /** 0..1 slide-in progress per module (1 = fully visible). */
    private final Map<Module, Float> anim = new HashMap<>();
    private long lastFrame = System.currentTimeMillis();

    public Hud() {
        super("HUD", "Wasserzeichen und Liste der aktiven Module.", Category.CLIENT, Items.ITEM_FRAME);
        loadEnabled(true);
    }

    /** A glass pill: vertical gradient body, shine on the upper half, thin light rim. */
    private static void pill(Shapes s, float x1, float y1, float x2, float y2) {
        float r = (y2 - y1) / 2f;
        s.roundRectGradient(x1, y1, x2, y2, r, GLASS_TOP, GLASS_BOTTOM);
        s.roundRectGradient(x1 + 1f, y1 + 1f, x2 - 1f, y1 + (y2 - y1) * 0.5f, r * 0.8f, SHINE, 0x00FFFFFF);
        s.roundRectRim(x1, y1, x2, y2, r, 0.75f, RIM);
    }

    public void render(GuiGraphicsExtractor ctx) {
        if (mc.gui.hud.isHidden() || mc.getDebugOverlay().showDebugScreen()) return;
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        Shapes.setPixelSize(1f / Math.max(1, mc.getWindow().getGuiScale()));
        Font font = mc.font;
        float margin = 5f;

        // ---------------- watermark ----------------
        if (watermark.isOn()) {
            float h = 17f, x = margin, y = margin;
            float tw = UiText.width(font, "Kraken", true);
            float w = 8f + 9f + 5f + tw + 9f;
            Shapes s = new Shapes();
            pill(s, x, y, x + w, y + h);
            // little logo: orange ring segment around a dot, like the wheel centre
            float lx = x + 8f + 4.5f, ly = y + h / 2f;
            float t = (now % 6000L) / 6000f * (float) (Math.PI * 2);
            s.arc(lx, ly, 4.2f, 1.3f, t, t + 1.9f, ACCENT);
            s.arc(lx, ly, 4.2f, 1.3f, t + 2.6f, t + 4.4f, ACCENT);
            s.circle(lx, ly, 1.6f, 0xFFFFFFFF);
            s.submit(ctx);
            UiText.left(ctx, font, "Kraken", x + 8f + 9f + 5f, ly + 0.5f, 1f, TEXT, true);
        }
        if (!moduleList.isOn()) return;

        // ---------------- module list ----------------
        List<Module> shown = new ArrayList<>();
        for (Module m : ModuleManager.get().getModules()) {
            if (m.getCategory() == Category.CLIENT) continue;
            float a = anim.getOrDefault(m, 0f);
            float target = m.isEnabled() ? 1f : 0f;
            a += (target - a) * Math.min(1f, dt * 12f);
            if (Math.abs(a - target) < 0.005f) a = target;
            anim.put(m, a);
            if (a > 0.001f) shown.add(m);
        }
        shown.sort((m1, m2) -> Float.compare(pillWidth(font, m2), pillWidth(font, m1)));

        float sw = ctx.guiWidth();
        float h = 14f, gap = 3f;
        float y = margin;
        Shapes s = new Shapes();
        List<Runnable> texts = new ArrayList<>();
        for (Module m : shown) {
            float a = anim.get(m);
            float e = 1 - (float) Math.pow(1 - a, 3);   // ease out
            float w = pillWidth(font, m);
            float x2 = sw - margin + (1 - e) * (w + margin + 4f); // slide in from the right
            float x1 = x2 - w;
            float y1 = y, y2 = y + h;
            pill(s, x1, y1, x2, y2);
            s.circle(x1 + 7.5f, y1 + h / 2f, 2f, Shapes.alpha(ACCENT, e));
            String name = m.getName();
            String info = m.getInfo();
            float nx = x1 + 13f;
            float ty = y1 + h / 2f + 0.5f;
            int nameColor = Shapes.alpha(TEXT, e), infoColor = Shapes.alpha(TEXT_DIM, e);
            texts.add(() -> UiText.left(ctx, font, name, nx, ty, 0.9f, nameColor, false));
            if (info != null) {
                float ix = nx + UiText.width(font, name, false) * 0.9f + 4f;
                texts.add(() -> UiText.left(ctx, font, info, ix, ty, 0.9f, infoColor, false));
            }
            y += (h + gap) * e;
        }
        s.submit(ctx);
        for (Runnable r : texts) r.run();
    }

    private static float pillWidth(Font font, Module m) {
        float w = UiText.width(font, m.getName(), false) * 0.9f;
        String info = m.getInfo();
        if (info != null) w += 4f + UiText.width(font, info, false) * 0.9f;
        return 13f + w + 8f;
    }
}
