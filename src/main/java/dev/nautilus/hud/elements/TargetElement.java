package dev.nautilus.hud.elements;

import dev.nautilus.gui.Shapes;
import dev.nautilus.hud.HudElement;
import dev.nautilus.hud.HudStyle;
import dev.nautilus.util.CombatTracker;
import dev.nautilus.util.Friends;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Locale;

/**
 * Target HUD in the Nautilus ring design: the 3D head of your target (player or mob) in a round
 * glass disc, the health as a ring around it (absorption as a gold outer ring) with the HP number in
 * a badge, and name, distance, armor and totem pops next to it. Target = crosshair or last hit.
 */
public class TargetElement extends HudElement {
    private static final float W = 128f, H = 44f, R = 17f;
    private static final float TAU = (float) (Math.PI * 2);
    private float shownHp = -1f;   // the ring eases towards the real value
    private float hitFlash;        // white flash on the ring when the target loses health
    private float lastHp = -1f;
    private LivingEntity last;
    private float fade;

    public TargetElement() {
        super("target", "Target", true, CENTER, CENTER, 0, 50);
    }

    @Override public float width(boolean editor) { return W; }
    @Override public float height(boolean editor) { return H; }

    @Override
    public void render(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float x, float y, float dt, boolean editor) {
        LivingEntity t = editor ? null : CombatTracker.target();
        if (t != null) last = t;
        fade += ((t != null || editor ? 1f : 0f) - fade) * Math.min(1f, dt * 10f);
        if (fade < 0.02f) { shownHp = -1f; lastHp = -1f; return; }
        float a = editor ? 1f : fade;
        // pop in: a little smaller while it fades in
        float sc = editor ? 1f : 0.92f + 0.08f * dev.nautilus.hud.Anim.easeOutBack(fade);
        ctx.pose().pushMatrix();
        ctx.pose().translate(x + W / 2f, y + H / 2f);
        ctx.pose().scale(sc, sc);
        ctx.pose().translate(-(x + W / 2f), -(y + H / 2f));
        // own shapes + text, drawn right here inside the scaled pose (Shapes takes the pose at submit)
        Shapes own = new Shapes();
        List<Runnable> ownText = new java.util.ArrayList<>();
        try {
            renderBody(ctx, own, ownText, x, y, dt, editor, t, a);
            own.submit(ctx);
            for (Runnable r : ownText) r.run();
        } finally {
            ctx.pose().popMatrix();
        }
    }

    private void renderBody(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float x, float y, float dt, boolean editor, LivingEntity t, float a) {
        // editor preview: yourself as the target
        LivingEntity e = t != null ? t : editor ? mc.player : last;

        String name;
        float hp, abs, max;
        int armor, pops;
        double dist;
        if (e == null) {
            name = "Enemy"; hp = 13f; abs = 4f; max = 20f; armor = 20; pops = 2; dist = 3.2;
        } else {
            name = e instanceof Player p ? p.getGameProfile().name() : e.getName().getString();
            hp = e.getHealth(); abs = e.getAbsorptionAmount(); max = Math.max(1f, e.getMaxHealth());
            armor = e.getArmorValue(); pops = e instanceof Player ? CombatTracker.pops(e) : 0;
            dist = mc.player == null || e == mc.player ? 0 : mc.player.distanceTo(e);
        }
        if (shownHp < 0) shownHp = hp;
        shownHp += (hp - shownHp) * Math.min(1f, dt * 8f);
        if (lastHp >= 0 && hp < lastHp - 0.01f) hitFlash = 1f;
        lastHp = hp;
        hitFlash = Math.max(0f, hitFlash - dt * 3.5f);

        HudStyle.pill(ctx, s, x, y, x + W, y + H, a);
        float cx = x + 5f + R + 2f, cy = y + H / 2f;

        // round glass disc behind the head
        s.circle(cx, cy, R - 1f, Shapes.alpha(HudStyle.shade(0x9A, 0.8f), a), Shapes.alpha(HudStyle.shade(0xD0, 0.9f), a));
        // HP ring: track, glow, value arc (colour from green to red), white flash on damage
        float frac = Math.max(0f, Math.min(1f, shownHp / max));
        int ringColor = Shapes.lerp(HudStyle.wear(frac), 0xFFFFFFFF, hitFlash * 0.7f);
        s.ring(cx, cy, R + 1.5f, 2.6f, Shapes.alpha(0x26FFFFFF, a));
        if (frac > 0.002f) {
            s.ringGlow(cx, cy, R + 1.5f, 2.6f, 0f, TAU * frac, 6f, Shapes.alpha(ringColor, a * (0.35f + hitFlash * 0.4f)));
            s.arc(cx, cy, R + 1.5f, 2.6f, 0f, TAU * frac, Shapes.alpha(ringColor, a));
            s.circle(cx, cy - R - 1.5f, 1.3f, Shapes.alpha(ringColor, a));
            s.circle(cx + (float) Math.sin(TAU * frac) * (R + 1.5f), cy - (float) Math.cos(TAU * frac) * (R + 1.5f), 1.3f, Shapes.alpha(ringColor, a));
        }
        // absorption: thin gold ring outside
        if (abs > 0) s.arc(cx, cy, R + 5f, 1.4f, 0f, TAU * Math.min(1f, abs / max), Shapes.alpha(0xFFFFC23D, a));
        if (e != null && Friends.is(e)) s.ring(cx, cy, R - 1f, 0.8f, Shapes.alpha(HudStyle.GOOD, a));

        // HP badge at the bottom of the ring
        String hpText = (hp + abs) >= 10 ? String.valueOf(Math.round(hp + abs)) : String.format(Locale.ROOT, "%.1f", hp + abs);
        float bw = HudStyle.width(hpText, true, 0.62f) + 7f, bh = 8.5f, by = cy + R - 2f;
        s.roundRect(cx - bw / 2f, by, cx + bw / 2f, by + bh, bh / 2f, Shapes.alpha(HudStyle.shade(0xF0, 0.85f), a));
        s.roundRectRim(cx - bw / 2f, by, cx + bw / 2f, by + bh, bh / 2f, 0.8f, Shapes.alpha(ringColor, a));

        // text column
        float tx = cx + R + 9f, maxW = x + W - 6f - tx;
        String distText = String.format(Locale.ROOT, "%.1f m", dist);
        StringBuilder extra = new StringBuilder();
        if (armor > 0) extra.append("⛨ ").append(armor);
        if (pops > 0) extra.append(extra.isEmpty() ? "" : "   ").append("✦ ").append(pops).append(pops == 1 ? " Pop" : " Pops");
        String extraText = extra.toString();
        int tc = Shapes.alpha(HudStyle.TEXT, a), dc = Shapes.alpha(HudStyle.TEXT_DIM, a), lc = Shapes.alpha(HudStyle.label(), a);
        int hpc = Shapes.alpha(HudStyle.wear(frac), a);
        String maxText = "/ " + Math.round(max);

        text.add(() -> {
            // 3D head inside the disc (cut to the square inside the circle)
            if (e != null) head(ctx, e, cx, cy);
            HudStyle.textCentered(ctx, hpText, cx, by + bh / 2f, 0.62f, Shapes.alpha(0xFFFFFFFF, a), true);
            String n = name;
            while (n.length() > 3 && HudStyle.width(n, true, HudStyle.TEXT_SCALE) > maxW) n = n.substring(0, n.length() - 2) + "…";
            HudStyle.text(ctx, n, tx, y + 11f, HudStyle.TEXT_SCALE, tc, true);
            float hx = tx;
            HudStyle.text(ctx, hpText + " HP", hx, y + 21f, 0.72f, hpc, true);
            hx += HudStyle.width(hpText + " HP", true, 0.72f) + 3f;
            HudStyle.text(ctx, maxText, hx, y + 21f, 0.6f, dc, false);
            HudStyle.text(ctx, distText, x + W - 7f - HudStyle.width(distText, false, 0.6f), y + 21f, 0.6f, dc, false);
            if (!extraText.isEmpty()) HudStyle.text(ctx, extraText, tx, y + 31f, 0.62f, lc, false);
        });
    }

    /** Vanilla's inventory entity renderer, zoomed onto the eyes and cut to the square inside the disc. */
    private static void head(GuiGraphicsExtractor ctx, LivingEntity e, float cx, float cy) {
        int half = Math.round(R * 0.74f);
        int x0 = Math.round(cx) - half, y0 = Math.round(cy) - half, x1 = Math.round(cx) + half, y1 = Math.round(cy) + half;
        float scale = Math.max(0.05f, e.getScale());
        float bbH = e.getBbHeight(), bbW = e.getBbWidth();
        // pixels per block: the head (about the width of the entity) fills the disc
        int size = Math.max(4, Math.round(R * 2.2f / Math.max(0.55f, Math.min(bbW, bbH) * 1.05f)));
        // vanilla centres the bounding box; move it so the eyes are in the middle
        float yOffset = (e.getEyeHeight() - bbH / 2f) / scale;
        // look slightly to the side of the viewer so the head reads as 3D
        InventoryScreen.extractEntityInInventoryFollowsMouse(ctx, x0, y0, x1, y1, size, yOffset, cx + 14f, cy - 2f, e);
    }
}
