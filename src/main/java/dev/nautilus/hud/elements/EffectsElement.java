package dev.nautilus.hud.elements;

import dev.nautilus.gui.Shapes;
import dev.nautilus.hud.HudElement;
import dev.nautilus.hud.HudStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Active potion effects: coloured dot, name + level and the remaining time (red in the last 10 s). */
public class EffectsElement extends HudElement {
    private record Row(String name, String time, int color, boolean ending) {}

    private static final String[] ROMAN = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};
    private static final List<Row> DEMO = List.of(
            new Row("Strength II", "1:24", 0xFFFFC700, false),
            new Row("Fire Resistance", "6:02", 0xFFFF9900, false),
            new Row("Absorption", "0:07", 0xFF2552A5, true));

    public EffectsElement() {
        super("effects", "Effects", true, START, START, 5, 116);
    }

    private static String time(MobEffectInstance e) {
        if (e.isInfiniteDuration()) return "∞";
        int sec = e.getDuration() / 20;
        return sec / 60 + ":" + String.format("%02d", sec % 60);
    }

    private List<Row> rows(boolean editor) {
        if (editor || mc.player == null) return DEMO;
        List<MobEffectInstance> effects = new ArrayList<>(mc.player.getActiveEffects());
        effects.sort(Comparator.comparingInt(MobEffectInstance::getDuration));
        List<Row> rows = new ArrayList<>();
        for (MobEffectInstance e : effects) {
            int amp = e.getAmplifier();
            String name = e.getEffect().value().getDisplayName().getString() + (amp > 0 && amp < ROMAN.length ? ROMAN[amp] : amp > 0 ? " " + (amp + 1) : "");
            rows.add(new Row(name, time(e), 0xFF000000 | e.getEffect().value().getColor(), !e.isInfiniteDuration() && e.getDuration() < 200));
        }
        return rows;
    }

    private static float rowWidth(Row r) {
        return HudStyle.PAD + 7f + HudStyle.width(r.name(), true, HudStyle.TEXT_SCALE) + 5f + HudStyle.width(r.time(), false, HudStyle.TEXT_SCALE) + HudStyle.PAD;
    }

    @Override
    public float width(boolean editor) {
        float w = 40f;
        for (Row r : rows(editor)) w = Math.max(w, rowWidth(r));
        return w;
    }

    @Override public float height(boolean editor) { return Math.max(1, rows(editor).size()) * (HudStyle.PILL_H + 2f) - 2f; }

    @Override
    public void render(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float x, float y, float dt, boolean editor) {
        List<Row> rows = rows(editor);
        boolean right = rightAligned();
        float total = width(editor);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            float w = rowWidth(r);
            float x1 = right ? x + total - w : x, y1 = y + i * (HudStyle.PILL_H + 2f);
            float cy = y1 + HudStyle.PILL_H / 2f;
            HudStyle.pill(ctx, s, x1, y1, x1 + w, y1 + HudStyle.PILL_H, 1f);
            s.circle(x1 + HudStyle.PAD + 2f, cy, 2.4f, r.color());
            text.add(() -> {
                float tx = x1 + HudStyle.PAD + 7f;
                HudStyle.text(ctx, r.name(), tx, cy, HudStyle.TEXT_SCALE, HudStyle.TEXT, true);
                tx += HudStyle.width(r.name(), true, HudStyle.TEXT_SCALE) + 5f;
                HudStyle.text(ctx, r.time(), tx, cy, HudStyle.TEXT_SCALE, r.ending() ? HudStyle.BAD : HudStyle.label(), false);
            });
        }
    }
}
