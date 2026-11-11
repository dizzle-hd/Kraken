package dev.nautilus.module.visuals;

import dev.nautilus.gui.Shapes;
import dev.nautilus.hud.HudStyle;
import dev.nautilus.hud.Icons;
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
 * Panel hotbar: a dark bar with the items, the selected slot as a lighter square with an accent line.
 * Above it one status panel as a 2×2 grid – Health (segments, absorption in gold), Food, Armor and XP –
 * each with icon, label, value and bar; an Air row while under water; the held item's name as a small
 * pill above everything when you switch items.
 */
public class Hotbar extends Module {
    private final BoolSetting bars = add(new BoolSetting("Bars", "Health, food, armor and air in a status panel above the hotbar instead of hearts and drumsticks", true));
    private final BoolSetting xp = add(new BoolSetting("XP bar", "XP with level in the status panel (also replaces the locator bar)", true));
    private final BoolSetting itemName = add(new BoolSetting("Item name", "Name of the held item as a pill above the hotbar when you switch items", true));
    private final BoolSetting numbers = add(new BoolSetting("Slot numbers", "Small numbers 1–9 in the slots", false));

    private static final float S = 18f, G = 2f, P = 4f;
    private static final float CELL_H = 17f, PAD = 5f, GAP = 3f;

    private final dev.nautilus.hud.Anim.Spring sel = new dev.nautilus.hud.Anim.Spring(420f, 30f); // slides with a soft overshoot
    private int lastSel = -1;
    private long selChangedAt;
    private float shownHp = -1f, shownFood = -1f, hurtFlash, lastHp = -1f;
    // item name pill: the name shown (keeps fading out after switching to an empty slot)
    private String pillName = "";
    private long lastFrame;
    private ItemStack lastHeld = ItemStack.EMPTY;
    private int lastSlot = -1;
    private long heldSince;
    /** Top of everything drawn this frame (for the item name pill). */
    private float top;

    public Hotbar() {
        super("Hotbar", "Panel hotbar with a status grid for health, food, armor, XP and air, and the held item's name – in the Nautilus panel design.",
                Category.VISUALS, Items.CHEST);
        loadEnabled(true);
    }

    private static Hotbar active() {
        Hotbar h = ModuleManager.get().get(Hotbar.class);
        if (h == null || !h.isEnabled() || mc.player == null || mc.player.isSpectator()) return null;
        return h;
    }

    /** Vanilla elements replaced by this module: true = drawn by us. */
    public static boolean replacesHotbar() { return active() != null; }
    public static boolean replacesStatus() { Hotbar h = active(); return h != null && h.bars.isOn(); }
    public static boolean replacesXp() {
        Hotbar h = active();
        return h != null && h.xp.isOn() && mc.player.jumpableVehicle() == null; // keep the vanilla horse jump bar
    }
    public static boolean replacesItemName() { Hotbar h = active(); return h != null && h.itemName.isOn(); }

    private static boolean survival() { return mc.gameMode != null && mc.gameMode.canHurtPlayer(); }

    private static float barW() { return 9 * S + 8 * G + 2 * P; }

    public static void renderHotbar(GuiGraphicsExtractor ctx) {
        Hotbar h = active();
        if (h != null) h.draw(ctx);
    }

    public static void renderItemName(GuiGraphicsExtractor ctx) {
        Hotbar h = active();
        if (h != null) h.drawItemName(ctx);
    }

    private void draw(GuiGraphicsExtractor ctx) {
        Player p = mc.player;
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        Shapes.setPixelSize(1f / Math.max(1, mc.getWindow().getGuiScale()));
        float w = ctx.guiWidth(), hgt = ctx.guiHeight();
        float x1 = w / 2f - barW() / 2f, x2 = x1 + barW();
        float y2 = hgt - 3f, y1 = y2 - S - 2 * P;
        Shapes s = new Shapes();
        List<Runnable> text = new ArrayList<>();

        // ---- hotbar
        HudStyle.panel(ctx, s, x1, y1, x2, y2, 1f);
        int sel = p.getInventory().getSelectedSlot();
        if (sel != lastSel) { lastSel = sel; selChangedAt = now; }
        float selX = this.sel.update(x1 + P + sel * (S + G), dt);
        float pop = dev.nautilus.hud.Anim.bump((now - selChangedAt) / 260f); // the new item pops a little
        s.roundRect(selX - 1f, y1 + P - 1f, selX + S + 1f, y1 + P + S + 1f, 3.5f, 0x26FFFFFF);
        s.roundRectRim(selX - 1f, y1 + P - 1f, selX + S + 1f, y1 + P + S + 1f, 3.5f, 0.6f, 0x30FFFFFF);
        s.roundRect(selX + 5f, y1 + P + S + 1.6f, selX + S - 5f, y1 + P + S + 2.6f, 0.5f, HudStyle.accent());

        ItemStack off = p.getOffhandItem();
        boolean leftHanded = p.getMainArm() == HumanoidArm.LEFT;
        float ow = S + 2 * P, ox = leftHanded ? x2 + 4f : x1 - 4f - ow;
        if (!off.isEmpty()) HudStyle.panel(ctx, s, ox, y1, ox + ow, y2, 1f);
        text.add(() -> {
            for (int i = 0; i < 9; i++) {
                float sx = x1 + P + i * (S + G) + 1f, sy = y1 + P + 1f;
                if (i == sel && pop > 0.01f) slotItem(ctx, p.getInventory().getItem(i), sx, sy, 1f + 0.14f * pop);
                else slotItem(ctx, p.getInventory().getItem(i), sx, sy);
                if (numbers.isOn()) HudStyle.text(ctx, String.valueOf(i + 1), sx - 0.5f, sy + 1.5f, 0.45f, i == sel ? HudStyle.accent() : 0x99FFFFFF, true);
            }
            if (!off.isEmpty()) slotItem(ctx, off, ox + P + 1f, y1 + P + 1f);
        });
        top = y1;

        // ---- status panel(s) above
        if (survival()) {
            float y = y1 - GAP;
            int air = p.getAirSupply(), maxAir = p.getMaxAirSupply();
            if (bars.isOn() && air < maxAir) { // Air row
                float ay2 = y, ay1 = ay2 - CELL_H - 2 * 3f;
                HudStyle.panel(ctx, s, x1, ay1, x2, ay2, 1f);
                float frac = Math.max(0f, air / (float) maxAir);
                cell(ctx, s, text, Icons.BUBBLE, "Air", Math.round(frac * 100) + "%", x1 + PAD, ay1 + 3f, x2 - PAD, frac, 0xFF7AC8FF);
                y = ay1 - GAP;
            }
            boolean showXp = xp.isOn() && mc.player.jumpableVehicle() == null;
            if (bars.isOn() || showXp) {
                int rowsN = bars.isOn() ? 2 : 1;
                float py2 = y, py1 = py2 - rowsN * CELL_H - (rowsN - 1) * 2f - 2 * 4f;
                HudStyle.panel(ctx, s, x1, py1, x2, py2, 1f);
                float half = (barW() - 2 * PAD - 8f) / 2f;
                float cx1 = x1 + PAD, cx2 = cx1 + half, dx1 = cx2 + 8f, dx2 = x2 - PAD;
                s.rect(cx2 + 3.8f, py1 + 5f, cx2 + 4.2f, py2 - 5f, 0x16FFFFFF); // column divider
                float ry = py1 + 4f;
                if (bars.isOn()) {
                    health(ctx, s, text, p, cx1, ry, cx2, dt);
                    food(ctx, s, text, p, dx1, ry, dx2, dt);
                    ry += CELL_H + 2f;
                    int armor = p.getArmorValue();
                    cell(ctx, s, text, Icons.SHIELD, "Armor", String.valueOf(armor), cx1, ry, cx2, armor / 20f, HudStyle.accent());
                    if (showXp) xpCell(ctx, s, text, p, dx1, ry, dx2);
                } else {
                    xpCell(ctx, s, text, p, cx1, ry, dx2);
                }
                top = py1;
            }
        }
        s.submit(ctx);
        for (Runnable r : text) r.run();
    }

    private static void slotItem(GuiGraphicsExtractor ctx, ItemStack st, float x, float y) { slotItem(ctx, st, x, y, 1f); }

    /** Item with count/durability, scaled around its centre (for the selection pop). */
    private static void slotItem(GuiGraphicsExtractor ctx, ItemStack st, float x, float y, float scale) {
        if (st.isEmpty()) return;
        ctx.pose().pushMatrix();
        ctx.pose().translate(x + 8f, y + 8f);
        ctx.pose().scale(scale, scale);
        ctx.pose().translate(-8f, -8f);
        ctx.item(mc.player, st, 0, 0, 0);
        ctx.itemDecorations(mc.font, st, 0, 0);
        ctx.pose().popMatrix();
    }

    // ---- status cells: icon + label left, value right, bar below -------------------------------------

    private static void cellHead(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, Icons icon, String label, String value, int valueColor,
                                 float x1, float y, float x2) {
        float cy = y + 5f;
        icon.draw(s, x1 + 3.5f, cy, 6.5f, Shapes.lerp(HudStyle.accent(), 0xFFFFFFFF, 0.25f));
        text.add(() -> {
            HudStyle.text(ctx, label, x1 + 10f, cy, 0.62f, HudStyle.TEXT_DIM, false);
            HudStyle.text(ctx, value, x2 - HudStyle.width(value, false, 0.62f), cy, 0.62f, valueColor, false);
        });
    }

    private static void cell(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, Icons icon, String label, String value,
                             float x1, float y, float x2, float frac, int color) {
        cellHead(ctx, s, text, icon, label, value, HudStyle.TEXT, x1, y, x2);
        bar(s, x1, y + 11.5f, x2, 2.2f, frac, color);
    }

    private static void bar(Shapes s, float x1, float y, float x2, float h, float frac, int color) {
        s.roundRect(x1, y, x2, y + h, h / 2f, 0x22FFFFFF);
        frac = Math.max(0f, Math.min(1f, frac));
        if (frac > 0.003f) s.roundRect(x1, y, x1 + Math.max(h, (x2 - x1) * frac), y + h, h / 2f, color);
    }

    private void health(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, Player p, float x1, float y, float x2, float dt) {
        float max = Math.max(1f, p.getMaxHealth()), hp = p.getHealth(), abs = p.getAbsorptionAmount();
        if (shownHp < 0) shownHp = hp;
        if (lastHp >= 0 && hp < lastHp - 0.01f) hurtFlash = 1f;
        lastHp = hp;
        hurtFlash = Math.max(0f, hurtFlash - dt * 3f);
        shownHp = dev.nautilus.hud.Anim.approach(shownHp, hp, dt, 9f);
        int color = p.hasEffect(MobEffects.POISON) ? 0xFFA8C83A
                : p.hasEffect(MobEffects.WITHER) ? 0xFF8A8A8A
                : p.isFullyFrozen() ? 0xFF9AD8FF
                : HudStyle.accent();
        if (shownHp / max < 0.3f) color = Shapes.lerp(color, 0xFFFF4D5E, 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 140.0));
        color = Shapes.lerp(color, 0xFFFFFFFF, hurtFlash * 0.6f);
        // the number counts towards the real value together with the segments
        String value = Math.round(shownHp) + (abs > 0 ? " +" + Math.round(abs) : "");
        cellHead(ctx, s, text, Icons.HEART, "Health", value, HudStyle.TEXT, x1, y, x2);
        // one segment per heart (2 HP), partly filled segments for half hearts
        int n = Math.max(1, (int) Math.ceil(max / 2f));
        float gap = n > 15 ? 0.6f : 1f, sw = ((x2 - x1) - (n - 1) * gap) / n, sy = y + 10.6f, sh = 3f;
        for (int i = 0; i < n; i++) {
            float sx = x1 + i * (sw + gap);
            float fill = Math.max(0f, Math.min(1f, (shownHp - i * 2f) / 2f));
            s.roundRect(sx, sy, sx + sw, sy + sh, 0.8f, 0x22FFFFFF);
            if (fill > 0.01f) s.roundRect(sx, sy, sx + sw * fill, sy + sh, 0.8f, color);
        }
        // absorption: small gold segments below
        int an = (int) Math.ceil(abs / 2f);
        for (int i = 0; i < Math.min(an, n); i++) {
            float sx = x1 + i * (sw + gap);
            float fill = Math.max(0f, Math.min(1f, (abs - i * 2f) / 2f));
            s.roundRect(sx, sy + sh + 0.8f, sx + sw * fill, sy + sh + 1.9f, 0.5f, 0xFFFFC23D);
        }
    }

    private void food(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, Player p, float x1, float y, float x2, float dt) {
        var food = p.getFoodData();
        float f = food.getFoodLevel();
        if (shownFood < 0) shownFood = f;
        shownFood = dev.nautilus.hud.Anim.approach(shownFood, f, dt, 8f);
        int color = p.hasEffect(MobEffects.HUNGER) ? 0xFF8BA33A : HudStyle.accent();
        cellHead(ctx, s, text, Icons.FOOD, "Food", String.valueOf(Math.round(shownFood)), HudStyle.TEXT, x1, y, x2);
        bar(s, x1, y + 11.5f, x2, 2.2f, shownFood / 20f, color);
        float sat = food.getSaturationLevel();
        if (sat > 0) s.roundRect(x1, y + 14.4f, x1 + (x2 - x1) * Math.min(1f, sat / 20f), y + 15.2f, 0.4f, 0x80FFE08A);
    }

    private static void xpCell(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, Player p, float x1, float y, float x2) {
        cell(ctx, s, text, Icons.STAR, "XP", "Lv " + p.experienceLevel, x1, y, x2, p.experienceProgress, HudStyle.accent());
    }

    // ---- held item name ---------------------------------------------------------------------------------

    private void drawItemName(GuiGraphicsExtractor ctx) {
        Player p = mc.player;
        ItemStack held = p.getMainHandItem();
        int slot = p.getInventory().getSelectedSlot();
        long now = System.currentTimeMillis();
        if (slot != lastSlot || !ItemStack.isSameItemSameComponents(held, lastHeld)) {
            lastSlot = slot;
            lastHeld = held.copy();
            if (!held.isEmpty()) {
                // switching while the pill is visible: no fade-out/in, just a quick pop of the new name
                float age0 = (now - heldSince) / 1000f;
                heldSince = age0 < 2.2f && !pillName.isEmpty() ? now - 90 : now;
                pillName = held.getHoverName().getString();
            }
        }
        if (pillName.isEmpty()) return;
        float age = (now - heldSince) / 1000f;
        float in = dev.nautilus.hud.Anim.easeOutBack(age / 0.28f);
        float out = age < 2f ? 0f : dev.nautilus.hud.Anim.easeInCubic((age - 2f) / 0.45f);
        float a = Math.min(1f, age / 0.15f) * (1f - out);
        if (a <= 0.01f) { if (age > 2.5f) pillName = ""; return; }
        String name = pillName;
        Shapes.setPixelSize(1f / Math.max(1, mc.getWindow().getGuiScale()));
        float tw = HudStyle.width(name, false, 0.8f), h = 15f, w = tw + 12f + 10f;
        float lift = (1f - in) * 7f + out * 5f, scale = 0.9f + 0.1f * in;
        float cx = ctx.guiWidth() / 2f, y2 = (top > 0 ? top : ctx.guiHeight() - 60f) - 4f + lift, y1 = y2 - h, cy = (y1 + y2) / 2f;
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx, cy);
        ctx.pose().scale(scale, scale);
        ctx.pose().translate(-cx, -cy);
        Shapes s = new Shapes();
        HudStyle.panel(ctx, s, cx - w / 2f, y1, cx + w / 2f, y2, a);
        Icons.CUBE.draw(s, cx - w / 2f + 9f, cy, 7f, Shapes.alpha(Shapes.lerp(HudStyle.accent(), 0xFFFFFFFF, 0.25f), a));
        s.submit(ctx);
        HudStyle.text(ctx, name, cx - w / 2f + 16f, cy, 0.8f, Shapes.alpha(HudStyle.TEXT, a), false);
        ctx.pose().popMatrix();
    }
}
