package dev.nautilus.hud.elements;

import dev.nautilus.gui.Shapes;
import dev.nautilus.hud.HudElement;
import dev.nautilus.hud.HudStyle;
import dev.nautilus.module.mace.MaceUtil;
import dev.nautilus.util.CombatTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * Mace smash preview: while you fall with a mace in the hotbar it shows the fall height and the damage a
 * smash would do now – on your target after its armor (best mace of the hotbar), otherwise without armor.
 */
public class SmashElement extends HudElement {
    private float fade;
    private String lastValue = "";
    private boolean lastReady;

    public SmashElement() {
        super("smash", "Smash-Vorschau", true, CENTER, CENTER, 0, -34);
    }

    @Override public float width(boolean editor) { return labelValueWidth("Smash", editor ? "5,0 m  ·  24,0 Schaden" : lastValue); }
    @Override public float height(boolean editor) { return HudStyle.PILL_H; }

    private String compute() {
        var p = mc.player;
        if (p == null || p.onGround() || p.isFallFlying() || p.fallDistance <= 0.6) return null;
        ItemStack best = null;
        LivingEntity target = CombatTracker.target();
        float dmg = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!MaceUtil.isMace(s)) continue;
            float d = target != null ? MaceUtil.estimate(s, target, p.fallDistance) : rawDamage(s, p.fallDistance);
            if (d > dmg) { dmg = d; best = s; }
        }
        if (best == null) return null;
        lastReady = MaceUtil.canSmash(p, MaceUtil.SMASH_THRESHOLD);
        return String.format(Locale.ROOT, "%.1f m  ·  %.1f Schaden", p.fallDistance, dmg).replace('.', ',');
    }

    private static float rawDamage(ItemStack mace, double fall) {
        return (float) (6 + MaceUtil.smashBonus(fall) + 0.5 * MaceUtil.density(mace) * fall);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float x, float y, float dt, boolean editor) {
        String v = editor ? "5,0 m  ·  24,0 Schaden" : compute();
        if (v != null) lastValue = v;
        fade += ((v != null ? 1f : 0f) - fade) * Math.min(1f, dt * 12f);
        if (fade < 0.02f || lastValue.isEmpty()) return;
        float a = editor ? 1f : fade;
        float w = labelValueWidth("Smash", lastValue);
        HudStyle.pill(ctx, s, x, y, x + w, y + HudStyle.PILL_H, a);
        // a dot in the accent colour lights up once the fall is high enough for a smash
        boolean ready = editor || lastReady;
        int lc = Shapes.alpha(HudStyle.label(), a);
        int vc = Shapes.alpha(ready ? HudStyle.TEXT : HudStyle.TEXT_DIM, a);
        String val = lastValue;
        float cy = y + HudStyle.PILL_H / 2f;
        text.add(() -> {
            float tx = x + HudStyle.PAD;
            HudStyle.text(ctx, "Smash", tx, cy, HudStyle.TEXT_SCALE, lc, true);
            HudStyle.text(ctx, val, tx + HudStyle.width("Smash", true, HudStyle.TEXT_SCALE) + 4f, cy, HudStyle.TEXT_SCALE, vc, false);
        });
        if (ready) s.circle(x + w - 4f, y + 4f, 1.6f, Shapes.alpha(HudStyle.accent(), a));
    }
}
