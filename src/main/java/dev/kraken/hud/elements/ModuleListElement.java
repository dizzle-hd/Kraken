package dev.kraken.hud.elements;

import dev.kraken.gui.Shapes;
import dev.kraken.hud.HudElement;
import dev.kraken.hud.HudStyle;
import dev.kraken.module.Category;
import dev.kraken.module.Module;
import dev.kraken.module.ModuleManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Active modules as glass pills, sorted by width, sliding in/out from the screen edge. */
public class ModuleListElement extends HudElement {
    private static final float GAP = 3f;
    private final Map<Module, Float> anim = new HashMap<>();

    public ModuleListElement() {
        super("modules", "Modulliste", true, END, START, 5, 5);
    }

    private List<Module> shown(boolean editor) {
        List<Module> list = new ArrayList<>();
        for (Module m : ModuleManager.get().getModules()) {
            if (m.getCategory() == Category.CLIENT) continue;
            if (editor ? true : anim.getOrDefault(m, 0f) > 0.001f) list.add(m);
        }
        if (editor) {
            List<Module> preview = new ArrayList<>();
            for (Module m : list) if (m.isEnabled()) preview.add(m);
            if (preview.isEmpty()) preview.addAll(list.subList(0, Math.min(3, list.size())));
            list = preview;
        }
        list.sort((a, b) -> Float.compare(pillWidth(b), pillWidth(a)));
        return list;
    }

    private static float pillWidth(Module m) {
        float w = HudStyle.width(m.getName(), false, HudStyle.TEXT_SCALE);
        String info = m.getInfo();
        if (info != null) w += 4f + HudStyle.width(info, false, HudStyle.TEXT_SCALE);
        return 13f + w + HudStyle.PAD;
    }

    @Override
    public float width(boolean editor) {
        float w = 40f;
        for (Module m : shown(editor)) w = Math.max(w, pillWidth(m));
        return w;
    }

    @Override
    public float height(boolean editor) {
        float h = 0;
        for (Module m : shown(editor)) h += (HudStyle.PILL_H + GAP) * (editor ? 1f : ease(anim.getOrDefault(m, 0f)));
        return Math.max(HudStyle.PILL_H, h - GAP);
    }

    private static float ease(float a) {
        return 1 - (float) Math.pow(1 - a, 3);
    }

    @Override
    public void tick() {}

    private void animate(float dt) {
        for (Module m : ModuleManager.get().getModules()) {
            if (m.getCategory() == Category.CLIENT) continue;
            float a = anim.getOrDefault(m, 0f);
            float target = m.isEnabled() ? 1f : 0f;
            a += (target - a) * Math.min(1f, dt * 12f);
            if (Math.abs(a - target) < 0.005f) a = target;
            anim.put(m, a);
        }
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float x, float y, float dt, boolean editor) {
        animate(dt);
        List<Module> list = shown(editor);
        float total = width(editor);
        boolean right = rightAligned();
        boolean up = bottomAligned();
        float yy = up ? y + height(editor) - HudStyle.PILL_H : y;
        for (Module m : list) {
            float e = editor ? 1f : ease(anim.getOrDefault(m, 0f));
            float w = pillWidth(m);
            float slide = (1 - e) * (w + 12f) * (right ? 1 : -1);
            float x1 = (right ? x + total - w : x) + slide;
            float y1 = yy;
            HudStyle.pill(ctx, s, x1, y1, x1 + w, y1 + HudStyle.PILL_H, e);
            s.circle(x1 + 7.5f, y1 + HudStyle.PILL_H / 2f, 2f, Shapes.alpha(HudStyle.ACCENT, e));
            String name = m.getName(), info = m.getInfo();
            float cy = y1 + HudStyle.PILL_H / 2f;
            int nc = Shapes.alpha(HudStyle.TEXT, e), ic = Shapes.alpha(HudStyle.TEXT_DIM, e);
            text.add(() -> {
                float tx = x1 + 13f;
                HudStyle.text(ctx, name, tx, cy, HudStyle.TEXT_SCALE, nc, false);
                if (info != null) HudStyle.text(ctx, info, tx + HudStyle.width(name, false, HudStyle.TEXT_SCALE) + 4f, cy, HudStyle.TEXT_SCALE, ic, false);
            });
            yy += (up ? -1 : 1) * (HudStyle.PILL_H + GAP) * e;
        }
    }
}
