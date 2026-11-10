package dev.nautilus.module.visuals;

import dev.nautilus.gui.Shapes;
import dev.nautilus.hud.HudStyle;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.module.ModuleManager;
import dev.nautilus.setting.BoolSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Glass hotbar with square slots and a sliding accent highlight, plus ring gauges in the Nautilus
 * style instead of hearts and drumsticks: health (absorption as a gold outer ring, armor inside),
 * hunger (saturation inside), air while under water, and an XP line with the level in a small ring.
 */
public class Hotbar extends Module {
    private final BoolSetting rings = add(new BoolSetting("Ringe", "Leben, Hunger und Luft als Ringe statt Herzen und Keulen", true));
    private final BoolSetting xp = add(new BoolSetting("XP-Leiste", "Eigene XP-Leiste mit Level im Ring (ersetzt auch die Ortungsleiste)", true));
    private final BoolSetting numbers = add(new BoolSetting("Slot-Nummern", "Kleine Nummern 1–9 in den Slots", false));

    private static final float S = 20f, G = 2f, P = 3f, RR = 12f;

    private float selX = -1f;       // animated position of the selection highlight
    private float shownHp = -1f, shownFood = -1f;
    private long lastFrame;

    public Hotbar() {
        super("Hotbar", "Glas-Hotbar mit quadratischen Slots und Ring-Anzeigen für Leben, Hunger, Luft und XP – im Nautilus-Ringdesign.",
                Category.VISUALS, Items.CHEST);
        loadEnabled(true);
    }

    private static Hotbar active() {
        Hotbar h = ModuleManager.get().get(Hotbar.class);
        if (h == null || !h.isEnabled() || mc.player == null || mc.player.isSpectator()) return null;
        return h;
    }

    /** Vanilla elements replaced by this module (hotbar / status bars / XP): true = drawn by us. */
    public static boolean replacesHotbar() { return active() != null; }
    public static boolean replacesStatus() { Hotbar h = active(); return h != null && h.rings.isOn(); }
    public static boolean replacesXp() {
        Hotbar h = active();
        return h != null && h.xp.isOn() && mc.player.jumpableVehicle() == null; // keep the vanilla horse jump bar
    }

    private static boolean survival() { return mc.gameMode != null && mc.gameMode.canHurtPlayer(); }

    // ---- layout -----------------------------------------------------------------------------------

    private static float barW() { return 9 * S + 8 * G + 2 * P; }
    private static float barX(float w) { return w / 2f - barW() / 2f; }
    private static float barY(float h) { return h - S - 2 * P - 2f; }

    /** Called from the replaced vanilla hotbar element. */
    public static void renderHotbar(GuiGraphicsExtractor ctx) {
        Hotbar h = active();
        if (h != null) h.draw(ctx);
    }

    private void draw(GuiGraphicsExtractor ctx) {
        Player p = mc.player;
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        Shapes.setPixelSize(1f / Math.max(1, mc.getWindow().getGuiScale()));
        float w = ctx.guiWidth(), hgt = ctx.guiHeight();
        float x1 = barX(w), y1 = barY(hgt), x2 = x1 + barW(), y2 = y1 + S + 2 * P;
        Shapes s = new Shapes();
        List<Runnable> text = new ArrayList<>();

        // container + slots
        HudStyle.pill(ctx, s, x1, y1, x2, y2, 1f);
        int sel = p.getInventory().getSelectedSlot();
        float target = x1 + P + sel * (S + G);
        if (selX < 0) selX = target;
        selX += (target - selX) * Math.min(1f, dt * 22f);
        for (int i = 0; i < 9; i++) {
            float sx = x1 + P + i * (S + G), sy = y1 + P;
            s.roundRect(sx, sy, sx + S, sy + S, 3.5f, 0x16FFFFFF);
            s.roundRectRim(sx, sy, sx + S, sy + S, 3.5f, 0.6f, 0x26FFFFFF);
        }
        // selection: accent glass square that slides between the slots
        s.roundRect(selX - 1f, y1 + P - 1f, selX + S + 1f, y1 + P + S + 1f, 4.5f, HudStyle.accent(0x46));
        s.roundRectRim(selX - 1f, y1 + P - 1f, selX + S + 1f, y1 + P + S + 1f, 4.5f, 1.1f, HudStyle.accent(0xF0));
        s.circle(selX + S / 2f, y1 - 2.2f, 1.3f, HudStyle.accent()); // small dot above the selected slot

        // offhand slot (on the side of the off hand)
        ItemStack off = p.getOffhandItem();
        boolean leftHanded = p.getMainArm() == HumanoidArm.LEFT;
        float ringGap = rings.isOn() && survival() ? RR * 2 + 10f : 4f;
        float ox = leftHanded ? x2 + ringGap : x1 - ringGap - S - 2 * P;
        if (!off.isEmpty()) {
            HudStyle.pill(ctx, s, ox, y1, ox + S + 2 * P, y2, 1f);
            s.roundRect(ox + P, y1 + P, ox + P + S, y1 + P + S, 3.5f, 0x16FFFFFF);
        }

        text.add(() -> {
            for (int i = 0; i < 9; i++) {
                float sx = x1 + P + i * (S + G) + 2f, sy = y1 + P + 2f;
                slotItem(ctx, p.getInventory().getItem(i), sx, sy);
                if (numbers.isOn()) HudStyle.text(ctx, String.valueOf(i + 1), sx - 0.5f, sy + 1.5f, 0.45f, i == sel ? HudStyle.accent() : 0x99FFFFFF, true);
            }
            if (!off.isEmpty()) slotItem(ctx, off, ox + P + 2f, y1 + P + 2f);
        });

        if (survival()) {
            float cy = (y1 + y2) / 2f;
            if (rings.isOn()) {
                float lx = (leftHanded ? x2 + 6f + RR : x1 - 6f - RR), rx = (leftHanded ? x1 - 6f - RR : x2 + 6f + RR);
                healthRing(ctx, s, text, p, lx, cy, dt);
                foodRing(ctx, s, text, p, rx, cy, dt);
                int air = p.getAirSupply(), maxAir = p.getMaxAirSupply();
                if (air < maxAir) airRing(s, text, ctx, rx, cy - RR * 2 - 6f, Math.max(0f, air / (float) maxAir));
            }
            if (xp.isOn() && mc.player.jumpableVehicle() == null) xpBar(ctx, s, text, p, x1, x2, y1);
        }
        s.submit(ctx);
        for (Runnable r : text) r.run();
    }

    private static void slotItem(GuiGraphicsExtractor ctx, ItemStack st, float x, float y) {
        if (st.isEmpty()) return;
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.item(mc.player, st, 0, 0, 0);
        ctx.itemDecorations(mc.font, st, 0, 0);
        ctx.pose().popMatrix();
    }

    // ---- gauges -------------------------------------------------------------------------------------

    private static final float TAU = (float) (Math.PI * 2);

    /** Glass disc with a track ring and a value arc starting at the top, clockwise. */
    private static void gauge(GuiGraphicsExtractor ctx, Shapes s, float cx, float cy, float r, float width, float frac, int color) {
        HudStyle.pill(ctx, s, cx - r - 3f, cy - r - 3f, cx + r + 3f, cy + r + 3f, 1f);
        s.ring(cx, cy, r, width, 0x22FFFFFF);
        frac = Math.max(0f, Math.min(1f, frac));
        if (frac > 0.002f) {
            s.ringGlow(cx, cy, r, width, 0f, TAU * frac, width * 2.2f, Shapes.alpha(color, 0.35f));
            s.arc(cx, cy, r, width, 0f, TAU * frac, color);
            s.circle(cx + (float) Math.sin(TAU * frac) * r, cy - (float) Math.cos(TAU * frac) * r, width / 2f, color);
            s.circle(cx, cy - r, width / 2f, color);
        }
    }

    private void healthRing(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, Player p, float cx, float cy, float dt) {
        float max = Math.max(1f, p.getMaxHealth()), hp = p.getHealth();
        if (shownHp < 0) shownHp = hp;
        shownHp += (hp - shownHp) * Math.min(1f, dt * 8f);
        int color = p.hasEffect(MobEffects.POISON) ? 0xFFA8C83A
                : p.hasEffect(MobEffects.WITHER) ? 0xFF6A6A6A
                : p.isFullyFrozen() ? 0xFF9AD8FF
                : 0xFFFF4D5E;
        float frac = shownHp / max;
        if (frac < 0.3f) color = Shapes.lerp(color, 0xFFFFFFFF, 0.25f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 120.0)); // low: pulse
        gauge(ctx, s, cx, cy, RR, 3f, frac, color);
        float abs = p.getAbsorptionAmount();
        if (abs > 0) s.arc(cx, cy, RR + 3.4f, 1.4f, 0f, TAU * Math.min(1f, abs / max), 0xFFFFC23D);
        int armor = p.getArmorValue();
        if (armor > 0) {
            s.ring(cx, cy, RR - 3.6f, 1f, 0x14FFFFFF);
            s.arc(cx, cy, RR - 3.6f, 1f, 0f, TAU * Math.min(1f, armor / 20f), 0xFFB8C7D9);
        }
        String v = hp + p.getAbsorptionAmount() >= 10 ? String.valueOf(Math.round(hp + abs)) : String.format(Locale.ROOT, "%.1f", hp + abs);
        int vc = abs > 0 ? 0xFFFFD27A : HudStyle.TEXT;
        text.add(() -> HudStyle.textCentered(ctx, v, cx, cy, v.length() > 3 ? 0.62f : 0.75f, vc, true));
    }

    private void foodRing(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, Player p, float cx, float cy, float dt) {
        var food = p.getFoodData();
        float f = food.getFoodLevel();
        if (shownFood < 0) shownFood = f;
        shownFood += (f - shownFood) * Math.min(1f, dt * 8f);
        int color = p.hasEffect(MobEffects.HUNGER) ? 0xFF8BA33A : 0xFFFFA043;
        gauge(ctx, s, cx, cy, RR, 3f, shownFood / 20f, color);
        float sat = food.getSaturationLevel();
        if (sat > 0) {
            s.ring(cx, cy, RR - 3.6f, 1f, 0x14FFFFFF);
            s.arc(cx, cy, RR - 3.6f, 1f, 0f, TAU * Math.min(1f, sat / 20f), 0xFFFFE08A);
        }
        String v = String.valueOf((int) f);
        text.add(() -> HudStyle.textCentered(ctx, v, cx, cy, 0.75f, HudStyle.TEXT, true));
    }

    private static void airRing(Shapes s, List<Runnable> text, GuiGraphicsExtractor ctx, float cx, float cy, float frac) {
        gauge(ctx, s, cx, cy, RR * 0.7f, 2.4f, frac, 0xFF5AB4FF);
        String v = Math.round(frac * 100) + "";
        text.add(() -> HudStyle.textCentered(ctx, v, cx, cy, 0.55f, HudStyle.TEXT, true));
    }

    private static void xpBar(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, Player p, float x1, float x2, float y1) {
        float y = y1 - 5f;
        float bx1 = x1 + 6f, bx2 = x2 - 6f;
        s.roundRect(bx1, y, bx2, y + 2.2f, 1.1f, 0x30FFFFFF);
        float prog = Math.max(0f, Math.min(1f, p.experienceProgress));
        int c = 0xFF7CF06A;
        if (prog > 0.002f) s.roundRect(bx1, y, bx1 + (bx2 - bx1) * prog, y + 2.2f, 1.1f, c);
        int lvl = p.experienceLevel;
        if (lvl > 0) {
            float cx = (x1 + x2) / 2f, cy = y - 7f, r = 6.5f;
            s.circle(cx, cy, r + 1f, HudStyle.shade(0xC0, 0.85f));
            s.ring(cx, cy, r, 1.3f, c);
            String v = String.valueOf(lvl);
            text.add(() -> HudStyle.textCentered(ctx, v, cx, cy, v.length() > 2 ? 0.5f : 0.62f, c, true));
        }
    }
}
