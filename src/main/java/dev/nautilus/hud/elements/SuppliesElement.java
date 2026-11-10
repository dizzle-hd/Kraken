package dev.nautilus.hud.elements;

import dev.nautilus.gui.Shapes;
import dev.nautilus.hud.HudElement;
import dev.nautilus.hud.HudStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** PvP supplies: how many totems, crystals, obsidian, golden apples, pearls and XP bottles you carry. */
public class SuppliesElement extends HudElement {
    private static final Item[] ITEMS = {Items.TOTEM_OF_UNDYING, Items.END_CRYSTAL, Items.OBSIDIAN, Items.RESPAWN_ANCHOR, Items.GLOWSTONE,
            Items.GOLDEN_APPLE, Items.ENDER_PEARL, Items.EXPERIENCE_BOTTLE, Items.WIND_CHARGE};
    private static final int[] DEMO = {4, 64, 128, 0, 0, 12, 16, 64, 0};

    private final List<ItemStack> shown = new ArrayList<>();
    private long frame = -1;

    public SuppliesElement() {
        super("supplies", "Vorrat", false, CENTER, END, 0, 92);
    }

    private List<ItemStack> list(boolean editor) {
        if (frame == dev.nautilus.hud.HudManager.frame()) return shown;
        frame = dev.nautilus.hud.HudManager.frame();
        shown.clear();
        int[] counts = new int[ITEMS.length];
        if (editor || mc.player == null) counts = DEMO.clone();
        else {
            var inv = mc.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                for (int k = 0; k < ITEMS.length; k++) {
                    if (st.is(ITEMS[k]) || (k == 5 && st.is(Items.ENCHANTED_GOLDEN_APPLE))) counts[k] += st.getCount();
                }
            }
        }
        for (int k = 0; k < ITEMS.length; k++) {
            if (counts[k] <= 0) continue;
            ItemStack st = new ItemStack(ITEMS[k]);
            st.setCount(Math.min(counts[k], 9999));
            shown.add(st);
        }
        return shown;
    }

    @Override public float width(boolean editor) { return 6f + Math.max(1, list(editor).size()) * ArmorHudElement.CELL; }
    @Override public float height(boolean editor) { return ArmorHudElement.H; }

    @Override
    public void render(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float x, float y, float dt, boolean editor) {
        List<ItemStack> list = new ArrayList<>(list(editor));
        if (list.isEmpty()) return;
        ArmorHudElement.iconRow(ctx, s, text, x, y, width(editor), list, st -> String.valueOf(st.getCount()),
                st -> st.is(Items.TOTEM_OF_UNDYING) && st.getCount() <= 1 ? HudStyle.BAD : HudStyle.TEXT);
    }
}
