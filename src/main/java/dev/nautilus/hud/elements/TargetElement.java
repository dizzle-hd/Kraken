package dev.nautilus.hud.elements;

import dev.nautilus.gui.Shapes;
import dev.nautilus.hud.HudElement;
import dev.nautilus.hud.HudStyle;
import dev.nautilus.util.CombatTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Locale;

/**
 * Target HUD: name, distance, health bar (absorption in gold), armor and totem pops of the entity in
 * your crosshair or the one you hit last. Hidden while there is no target.
 */
public class TargetElement extends HudElement {
    private static final float W = 132f, H = 34f;
    private float shownHp = -1f;   // health bar eases towards the real value
    private LivingEntity last;
    private float fade;

    public TargetElement() {
        super("target", "Ziel", true, CENTER, CENTER, 0, 46);
    }

    @Override public float width(boolean editor) { return W; }
    @Override public float height(boolean editor) { return H; }

    @Override
    public void render(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float x, float y, float dt, boolean editor) {
        LivingEntity t = editor ? null : CombatTracker.target();
        if (t != null) last = t;
        fade += ((t != null || editor ? 1f : 0f) - fade) * Math.min(1f, dt * 10f);
        if (fade < 0.02f) { shownHp = -1f; return; }
        float a = editor ? 1f : fade;
        LivingEntity e = t != null ? t : last;

        String name;
        float hp, abs, max;
        int armor, pops;
        double dist;
        if (e == null) { // editor preview
            name = "Gegner"; hp = 13f; abs = 4f; max = 20f; armor = 20; pops = 2; dist = 3.2;
        } else {
            name = e.getName().getString();
            hp = e.getHealth(); abs = e.getAbsorptionAmount(); max = Math.max(1f, e.getMaxHealth());
            armor = e.getArmorValue(); pops = e instanceof Player ? CombatTracker.pops(e) : 0;
            dist = mc.player == null ? 0 : mc.player.distanceTo(e);
        }
        if (shownHp < 0) shownHp = hp;
        shownHp += (hp - shownHp) * Math.min(1f, dt * 8f);

        HudStyle.pill(ctx, s, x, y, x + W, y + H, a);
        // health bar: accent, absorption on top in gold
        float bx1 = x + 7f, bx2 = x + W - 7f, by = y + 22f;
        float frac = Math.max(0f, Math.min(1f, shownHp / max));
        float absFrac = Math.max(0f, Math.min(1f - frac, abs / max));
        s.roundRect(bx1, by, bx2, by + 4f, 2f, Shapes.alpha(0x33FFFFFF, a));
        if (frac > 0) s.roundRect(bx1, by, bx1 + (bx2 - bx1) * frac, by + 4f, 2f, Shapes.alpha(HudStyle.accent(), a));
        if (absFrac > 0) s.roundRect(bx1 + (bx2 - bx1) * frac, by, bx1 + (bx2 - bx1) * (frac + absFrac), by + 4f, 2f, Shapes.alpha(0xFFFFC23D, a));

        String hpText = String.format(Locale.ROOT, "%.1f", hp + abs).replace('.', ',') + " HP";
        String distText = String.format(Locale.ROOT, "%.1f m", dist).replace('.', ',');
        StringBuilder extra = new StringBuilder();
        if (armor > 0) extra.append("Rüstung ").append(armor);
        if (pops > 0) extra.append(extra.isEmpty() ? "" : "  ·  ").append(pops).append(pops == 1 ? " Pop" : " Pops");
        String extraText = extra.toString();
        int tc = Shapes.alpha(HudStyle.TEXT, a), dc = Shapes.alpha(HudStyle.TEXT_DIM, a), lc = Shapes.alpha(HudStyle.label(), a);
        float hpRatio = (hp + abs) / max;
        int hc = Shapes.alpha(hpRatio > 0.5f ? HudStyle.GOOD : hpRatio > 0.25f ? 0xFFFFD27A : HudStyle.BAD, a);
        text.add(() -> {
            float maxName = W - 14f - HudStyle.width(distText, false, 0.75f) - 6f;
            String n = name;
            while (n.length() > 3 && HudStyle.width(n, true, HudStyle.TEXT_SCALE) > maxName) n = n.substring(0, n.length() - 2) + "…";
            HudStyle.text(ctx, n, x + 7f, y + 8f, HudStyle.TEXT_SCALE, tc, true);
            HudStyle.text(ctx, distText, x + W - 7f - HudStyle.width(distText, false, 0.75f), y + 8f, 0.75f, dc, false);
            HudStyle.text(ctx, hpText, x + 7f, y + 16f, 0.72f, hc, true);
            if (!extraText.isEmpty())
                HudStyle.text(ctx, extraText, x + W - 7f - HudStyle.width(extraText, false, 0.68f), y + 16f, 0.68f, lc, false);
        });
    }
}
