package dev.nautilus.hud.elements;

import dev.nautilus.gui.Shapes;
import dev.nautilus.hud.HudElement;
import dev.nautilus.hud.HudStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Durability of your armor (H helmet, C chestplate, L leggings, B boots) and of the item in your hand (W), in percent, coloured by wear. */
public class ArmorElement extends HudElement {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
    private static final String[] SHORT = {"H", "C", "L", "B", "W"};
    private static final int[] DEMO = {92, 64, 100, 18, 77};

    public ArmorElement() {
        super("armor", "Armor", false, END, END, 5, 22);
    }

    /** Durability in percent, -1 = empty slot or an item without durability. */
    private static int percent(ItemStack s) {
        if (s.isEmpty() || !s.isDamageableItem() || s.getMaxDamage() <= 0) return -1;
        return Math.round(100f * (s.getMaxDamage() - s.getDamageValue()) / s.getMaxDamage());
    }

    private int[] values(boolean editor) {
        int[] v = new int[SLOTS.length];
        for (int i = 0; i < SLOTS.length; i++) v[i] = editor || mc.player == null ? DEMO[i] : percent(mc.player.getItemBySlot(SLOTS[i]));
        return v;
    }

    private static String part(int i, int v) { return SHORT[i] + " " + (v < 0 ? "–" : v + "%"); }

    @Override
    public float width(boolean editor) {
        int[] v = values(editor);
        float w = HudStyle.PAD + HudStyle.width("Armor", true, HudStyle.TEXT_SCALE) + 4f;
        for (int i = 0; i < v.length; i++) w += HudStyle.width(part(i, v[i]), false, HudStyle.TEXT_SCALE) + 6f;
        return w - 6f + HudStyle.PAD;
    }

    @Override public float height(boolean editor) { return HudStyle.PILL_H; }

    @Override
    public void render(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float x, float y, float dt, boolean editor) {
        int[] v = values(editor);
        HudStyle.pill(ctx, s, x, y, x + width(editor), y + HudStyle.PILL_H, 1f);
        float cy = y + HudStyle.PILL_H / 2f;
        text.add(() -> {
            float tx = x + HudStyle.PAD;
            HudStyle.text(ctx, "Armor", tx, cy, HudStyle.TEXT_SCALE, HudStyle.label(), true);
            tx += HudStyle.width("Armor", true, HudStyle.TEXT_SCALE) + 4f;
            for (int i = 0; i < v.length; i++) {
                int c = v[i] < 0 ? HudStyle.TEXT_DIM : v[i] > 50 ? HudStyle.TEXT : v[i] > 20 ? 0xFFFFD27A : HudStyle.BAD;
                String p = part(i, v[i]);
                HudStyle.text(ctx, p, tx, cy, HudStyle.TEXT_SCALE, c, false);
                tx += HudStyle.width(p, false, HudStyle.TEXT_SCALE) + 6f;
            }
        });
    }
}
