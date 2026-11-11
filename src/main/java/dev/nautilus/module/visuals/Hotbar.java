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
 * Glass hotbar with square slots and a sliding accent highlight. Above it, instead of hearts and
 * drumsticks: a health bar (absorption in gold) and a hunger bar (saturation as a line), armor and air
 * as thin bars above them, and the XP as a line with the level in a small badge.
 */
public class Hotbar extends Module {
    private final BoolSetting bars = add(new BoolSetting("Bars", "Health and hunger as glass bars above the hotbar instead of hearts and drumsticks", true));
    private final BoolSetting xp = add(new BoolSetting("XP bar", "Own XP line with level (also replaces the locator bar)", true));
    private final BoolSetting numbers = add(new BoolSetting("Slot numbers", "Small numbers 1–9 in the slots", false));

    private static final float S = 20f, G = 2f, P = 3f;
    private static final float BAR_H = 9f, THIN = 3f;

    private float selX = -1f;       // animated position of the selection highlight
    private float shownHp = -1f, shownFood = -1f, hurtFlash;
    private float lastHp = -1f;
    private long lastFrame;

    public Hotbar() {
        super("Hotbar", "Glass hotbar with square slots, with bars for health, hunger, armor, air and XP above it – in the Nautilus glass design.",
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
    public static boolean replacesStatus() { Hotbar h = active(); return h != null && h.bars.isOn(); }
    public static boolean replacesXp() {
        Hotbar h = active();
        return h != null && h.xp.isOn() && mc.player.jumpableVehicle() == null; // keep the vanilla horse jump bar
    }

    private static boolean survival() { return mc.gameMode != null && mc.gameMode.canHurtPlayer(); }

    private static float barW() { return 9 * S + 8 * G + 2 * P; }

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
        float x1 = w / 2f - barW() / 2f, x2 = x1 + barW();
        float y2 = hgt - 2f, y1 = y2 - S - 2 * P;
        Shapes s = new Shapes();
        List<Runnable> text = new ArrayList<>();

        // ---- hotbar: container, square slots, sliding selection
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
        s.roundRect(selX - 1f, y1 + P - 1f, selX + S + 1f, y1 + P + S + 1f, 4.5f, HudStyle.accent(0x46));
        s.roundRectRim(selX - 1f, y1 + P - 1f, selX + S + 1f, y1 + P + S + 1f, 4.5f, 1.1f, HudStyle.accent(0xF0));

        // offhand slot next to the hotbar, on the side of the off hand
        ItemStack off = p.getOffhandItem();
        boolean leftHanded = p.getMainArm() == HumanoidArm.LEFT;
        float ow = S + 2 * P, ox = leftHanded ? x2 + 4f : x1 - 4f - ow;
        if (!off.isEmpty()) {
            HudStyle.pill(ctx, s, ox, y1, ox + ow, y2, 1f);
            s.roundRect(ox + P, y1 + P, ox + P + S, y1 + P + S, 3.5f, 0x16FFFFFF);
            s.roundRectRim(ox + P, y1 + P, ox + P + S, y1 + P + S, 3.5f, 0.6f, 0x26FFFFFF);
        }
        text.add(() -> {
            for (int i = 0; i < 9; i++) {
                float sx = x1 + P + i * (S + G) + 2f, sy = y1 + P + 2f;
                slotItem(ctx, p.getInventory().getItem(i), sx, sy);
                if (numbers.isOn()) HudStyle.text(ctx, String.valueOf(i + 1), sx - 0.5f, sy + 1.5f, 0.45f, i == sel ? HudStyle.accent() : 0x99FFFFFF, true);
            }
            if (!off.isEmpty()) slotItem(ctx, off, ox + P + 2f, y1 + P + 2f);
        });

        // ---- above the hotbar: XP line, then health | hunger, then armor | air
        if (survival()) {
            float y = y1 - 3f;
            if (xp.isOn() && mc.player.jumpableVehicle() == null) {
                y = xpLine(s, text, ctx, p, x1, x2, y);
            }
            if (bars.isOn()) {
                float half = (barW() - 4f) / 2f;
                float hy2 = y - 2f, hy1 = hy2 - BAR_H;
                health(ctx, s, text, p, x1, hy1, x1 + half, hy2, dt);
                food(ctx, s, text, p, x2 - half, hy1, x2, hy2, dt);
                float ty2 = hy1 - 2f, ty1 = ty2 - THIN;
                int armor = p.getArmorValue();
                if (armor > 0) thinBar(s, x1, ty1, x1 + half, ty2, armor / 20f, 0xFFB8C7D9, false);
                int air = p.getAirSupply(), maxAir = p.getMaxAirSupply();
                if (air < maxAir) thinBar(s, x2 - half, ty1, x2, ty2, Math.max(0f, air / (float) maxAir), 0xFF5AB4FF, true);
            }
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

    // ---- bars -------------------------------------------------------------------------------------

    /** Glass bar with a coloured fill; {@code fromRight} fills from the right edge (hunger, like vanilla). */
    private static void bar(GuiGraphicsExtractor ctx, Shapes s, float x1, float y1, float x2, float y2, float frac, int color, boolean fromRight) {
        HudStyle.pill(ctx, s, x1, y1, x2, y2, 1f);
        frac = Math.max(0f, Math.min(1f, frac));
        float r = (y2 - y1) / 2f, inset = 1.2f;
        float fx1 = x1 + inset, fx2 = x2 - inset, fy1 = y1 + inset, fy2 = y2 - inset;
        float len = (fx2 - fx1) * frac;
        if (len < 0.5f) return;
        float a = fromRight ? fx2 - len : fx1, b = fromRight ? fx2 : fx1 + len;
        float rr = Math.min(r - inset, len / 2f);
        s.roundRectGradient(a, fy1, b, fy2, rr, Shapes.lerp(color, 0xFFFFFFFF, 0.18f), Shapes.lerp(color, 0xFF000000, 0.15f));
        s.roundRectGradient(a + 0.5f, fy1 + 0.4f, b - 0.5f, fy1 + (fy2 - fy1) * 0.45f, rr * 0.8f, 0x40FFFFFF, 0x00FFFFFF); // shine
    }

    private static void thinBar(Shapes s, float x1, float y1, float x2, float y2, float frac, int color, boolean fromRight) {
        s.roundRect(x1, y1, x2, y2, (y2 - y1) / 2f, 0x30000000);
        s.roundRect(x1, y1, x2, y2, (y2 - y1) / 2f, 0x18FFFFFF);
        float len = (x2 - x1) * Math.max(0f, Math.min(1f, frac));
        if (len < 0.5f) return;
        float a = fromRight ? x2 - len : x1, b = fromRight ? x2 : x1 + len;
        s.roundRect(a, y1, b, y2, Math.min((y2 - y1) / 2f, len / 2f), color);
    }

    private void health(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, Player p, float x1, float y1, float x2, float y2, float dt) {
        float max = Math.max(1f, p.getMaxHealth()), hp = p.getHealth(), abs = p.getAbsorptionAmount();
        if (shownHp < 0) shownHp = hp;
        if (lastHp >= 0 && hp < lastHp - 0.01f) hurtFlash = 1f;
        lastHp = hp;
        hurtFlash = Math.max(0f, hurtFlash - dt * 3f);
        shownHp += (hp - shownHp) * Math.min(1f, dt * 8f);
        int color = p.hasEffect(MobEffects.POISON) ? 0xFFA8C83A
                : p.hasEffect(MobEffects.WITHER) ? 0xFF6A6A6A
                : p.isFullyFrozen() ? 0xFF9AD8FF
                : 0xFFFF4D5E;
        float frac = shownHp / max;
        if (frac < 0.3f) color = Shapes.lerp(color, 0xFFFFFFFF, 0.2f + 0.2f * (float) Math.sin(System.currentTimeMillis() / 120.0));
        color = Shapes.lerp(color, 0xFFFFFFFF, hurtFlash * 0.6f);
        bar(ctx, s, x1, y1, x2, y2, frac, color, false);
        if (abs > 0) { // absorption: gold overlay from the left, over the red
            float inset = 1.2f, len = (x2 - x1 - 2 * inset) * Math.min(1f, abs / max);
            s.roundRect(x1 + inset, y1 + inset, x1 + inset + len, y1 + inset + 2f, 1f, 0xFFFFC23D);
        }
        String v = (hp + abs) >= 10 ? String.valueOf(Math.round(hp + abs)) : String.format(Locale.ROOT, "%.1f", hp + abs);
        float cy = (y1 + y2) / 2f;
        text.add(() -> {
            HudStyle.text(ctx, "HP", x1 + 5f, cy, 0.55f, 0xCCFFFFFF, true);
            HudStyle.text(ctx, v, x2 - 5f - HudStyle.width(v, true, 0.62f), cy, 0.62f, abs > 0 ? 0xFFFFE08A : 0xFFFFFFFF, true);
        });
    }

    private void food(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, Player p, float x1, float y1, float x2, float y2, float dt) {
        var food = p.getFoodData();
        float f = food.getFoodLevel();
        if (shownFood < 0) shownFood = f;
        shownFood += (f - shownFood) * Math.min(1f, dt * 8f);
        int color = p.hasEffect(MobEffects.HUNGER) ? 0xFF8BA33A : 0xFFFFA043;
        bar(ctx, s, x1, y1, x2, y2, shownFood / 20f, color, true);
        float sat = food.getSaturationLevel();
        if (sat > 0) { // saturation: a line along the bottom
            float inset = 1.2f, len = (x2 - x1 - 2 * inset) * Math.min(1f, sat / 20f);
            s.roundRect(x2 - inset - len, y2 - inset - 1.6f, x2 - inset, y2 - inset, 0.8f, 0xFFFFE08A);
        }
        String v = String.valueOf((int) f);
        float cy = (y1 + y2) / 2f;
        text.add(() -> {
            HudStyle.text(ctx, v, x1 + 5f, cy, 0.62f, 0xFFFFFFFF, true);
            String l = "Hunger";
            HudStyle.text(ctx, l, x2 - 5f - HudStyle.width(l, true, 0.55f), cy, 0.55f, 0xCCFFFFFF, true);
        });
    }

    /** XP: a thin line across the hotbar width with the level in a badge in the middle; returns its top. */
    private static float xpLine(Shapes s, List<Runnable> text, GuiGraphicsExtractor ctx, Player p, float x1, float x2, float bottom) {
        float y2 = bottom, y1 = y2 - 2.4f;
        float bx1 = x1 + 4f, bx2 = x2 - 4f;
        int c = 0xFF7CF06A;
        s.roundRect(bx1, y1, bx2, y2, 1.2f, 0x40000000);
        s.roundRect(bx1, y1, bx2, y2, 1.2f, 0x1CFFFFFF);
        float prog = Math.max(0f, Math.min(1f, p.experienceProgress));
        if (prog > 0.002f) s.roundRect(bx1, y1, bx1 + (bx2 - bx1) * prog, y2, 1.2f, c);
        int lvl = p.experienceLevel;
        if (lvl > 0) {
            String v = String.valueOf(lvl);
            float cx = (x1 + x2) / 2f, cy = (y1 + y2) / 2f;
            float bw = HudStyle.width(v, true, 0.58f) + 8f, bh = 8f;
            s.roundRect(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f, bh / 2f, HudStyle.shade(0xE6, 0.88f));
            s.roundRectRim(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f, bh / 2f, 0.8f, c);
            text.add(() -> HudStyle.textCentered(ctx, v, cx, cy, 0.58f, c, true));
            return cy - bh / 2f;
        }
        return y1;
    }
}
