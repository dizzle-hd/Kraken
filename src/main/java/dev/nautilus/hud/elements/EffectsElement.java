package dev.nautilus.hud.elements;

import dev.nautilus.hud.HudStyle;
import dev.nautilus.hud.Icons;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Potions panel: active effects with a coloured dot, name + level and the remaining time in a chip (last 10 s in red). */
public class EffectsElement extends ListPanelElement {
    private static final String[] ROMAN = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};
    private static final List<Row> DEMO = List.of(
            new Row(null, 0xFFFFC700, "Strength II", "1:24", HudStyle.TEXT, true),
            new Row(null, 0xFFFF9900, "Fire Resistance", "6:02", HudStyle.TEXT, true),
            new Row(null, 0xFF2552A5, "Absorption", "0:07", HudStyle.BAD, true));

    public EffectsElement() {
        super("effects", "Effects", Icons.FLASK, "Potions", true, START, START, 5, 151);
    }

    private static String time(MobEffectInstance e) {
        if (e.isInfiniteDuration()) return "∞";
        int sec = e.getDuration() / 20;
        return sec / 60 + ":" + String.format("%02d", sec % 60);
    }

    @Override
    protected List<Row> rows(boolean editor) {
        if (editor || mc.player == null) return DEMO;
        List<MobEffectInstance> effects = new ArrayList<>(mc.player.getActiveEffects());
        effects.sort(Comparator.comparingInt(MobEffectInstance::getDuration));
        List<Row> rows = new ArrayList<>();
        for (MobEffectInstance e : effects) {
            int amp = e.getAmplifier();
            String name = e.getEffect().value().getDisplayName().getString() + (amp > 0 && amp < ROMAN.length ? ROMAN[amp] : amp > 0 ? " " + (amp + 1) : "");
            boolean ending = !e.isInfiniteDuration() && e.getDuration() < 200;
            rows.add(new Row(null, 0xFF000000 | e.getEffect().value().getColor(), name, time(e), ending ? HudStyle.BAD : HudStyle.TEXT, true));
        }
        return rows;
    }
}
