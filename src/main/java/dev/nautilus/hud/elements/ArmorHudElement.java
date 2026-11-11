package dev.nautilus.hud.elements;

import dev.nautilus.gui.Shapes;
import dev.nautilus.hud.HudElement;
import dev.nautilus.hud.HudStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Armor HUD: icons of helmet, chestplate, leggings, boots and both hands, durability (or count) below each. */
public class ArmorHudElement extends HudElement {
    static final float CELL = 20f, H = 31f;
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private static List<ItemStack> demo;

    public ArmorHudElement() {
        super("armorhud", "Armor HUD", true, CENTER, END, 0, 58);
    }

    private static List<ItemStack> demo() {
        if (demo == null) {
            demo = new ArrayList<>();
            int[] dmg = {40, 300, 120, 380, 500, 0};
            ItemStack[] st = {new ItemStack(Items.NETHERITE_HELMET), new ItemStack(Items.NETHERITE_CHESTPLATE), new ItemStack(Items.NETHERITE_LEGGINGS),
                    new ItemStack(Items.NETHERITE_BOOTS), new ItemStack(Items.NETHERITE_SWORD), new ItemStack(Items.TOTEM_OF_UNDYING)};
            for (int i = 0; i < st.length; i++) { if (dmg[i] > 0) st[i].setDamageValue(dmg[i]); demo.add(st[i]); }
        }
        return demo;
    }

    private List<ItemStack> stacks(boolean editor) {
        if (editor || mc.player == null) return demo();
        List<ItemStack> list = new ArrayList<>();
        for (EquipmentSlot slot : SLOTS) {
            ItemStack s = mc.player.getItemBySlot(slot);
            if (!s.isEmpty()) list.add(s);
        }
        return list;
    }

    /** Text under an icon: durability in percent, otherwise the stack size (nothing for single items). */
    static String caption(ItemStack s) {
        if (s.isDamageableItem() && s.getMaxDamage() > 0)
            return Math.round(100f * (s.getMaxDamage() - s.getDamageValue()) / s.getMaxDamage()) + "%";
        return s.getCount() > 1 ? String.valueOf(s.getCount()) : "";
    }

    static int captionColor(ItemStack s) {
        if (!s.isDamageableItem() || s.getMaxDamage() <= 0) return HudStyle.TEXT;
        return HudStyle.wear((s.getMaxDamage() - s.getDamageValue()) / (float) s.getMaxDamage());
    }

    @Override public float width(boolean editor) { return 6f + Math.max(1, stacks(editor).size()) * CELL; }
    @Override public float height(boolean editor) { return H; }

    @Override
    public void render(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float x, float y, float dt, boolean editor) {
        List<ItemStack> list = stacks(editor);
        if (list.isEmpty()) return;
        iconRow(ctx, s, text, x, y, width(editor), list, ArmorHudElement::caption, ArmorHudElement::captionColor);
    }

    /** Shared layout of icon rows (armor, supplies): glass pill, icons on top, captions below. */
    static void iconRow(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float x, float y, float w, List<ItemStack> list,
                        java.util.function.Function<ItemStack, String> caption, java.util.function.ToIntFunction<ItemStack> color) {
        HudStyle.pill(ctx, s, x, y, x + w, y + H, 1f);
        text.add(() -> {
            for (int i = 0; i < list.size(); i++) {
                ItemStack st = list.get(i);
                float cx = x + 3f + i * CELL + CELL / 2f;
                HudStyle.item(ctx, st, cx - 8f, y + 3f, 16f);
                String c = caption.apply(st);
                if (!c.isEmpty()) HudStyle.textCentered(ctx, c, cx, y + 25f, 0.62f, color.applyAsInt(st), true);
            }
        });
    }
}
