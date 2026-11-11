package dev.nautilus.hud.elements;

import dev.nautilus.hud.HudStyle;
import dev.nautilus.hud.Icons;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Resources panel: how many golden apples, totems, crystals, obsidian, pearls and XP bottles you carry (0 in red). */
public class SuppliesElement extends ListPanelElement {
    private static final Item[] ITEMS = {Items.GOLDEN_APPLE, Items.TOTEM_OF_UNDYING, Items.END_CRYSTAL, Items.OBSIDIAN, Items.ENDER_PEARL, Items.EXPERIENCE_BOTTLE};
    private static final String[] NAMES = {"Gapples", "Totems", "Crystals", "Obsidian", "Pearls", "Exp"};
    private static final int[] DEMO = {61, 2, 0, 128, 16, 0};
    private static final ItemStack[] ICONS = new ItemStack[ITEMS.length];

    public SuppliesElement() {
        super("supplies", "Resources", Icons.CUBE, "Resources", false, END, CENTER, 5, 60);
    }

    @Override protected boolean hideWhenEmpty() { return false; }

    @Override
    protected List<Row> rows(boolean editor) {
        int[] counts = new int[ITEMS.length];
        if (editor || mc.player == null) counts = DEMO.clone();
        else {
            var inv = mc.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                for (int k = 0; k < ITEMS.length; k++)
                    if (st.is(ITEMS[k]) || (k == 0 && st.is(Items.ENCHANTED_GOLDEN_APPLE))) counts[k] += st.getCount();
            }
        }
        List<Row> rows = new ArrayList<>();
        for (int k = 0; k < ITEMS.length; k++) {
            if (ICONS[k] == null) ICONS[k] = new ItemStack(ITEMS[k]);
            int color = counts[k] == 0 ? HudStyle.BAD : k == 1 && counts[k] <= 1 ? 0xFFFFD27A : HudStyle.TEXT;
            rows.add(new Row(ICONS[k], 0, NAMES[k], String.valueOf(counts[k]), color, false));
        }
        return rows;
    }
}
