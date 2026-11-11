package dev.nautilus.hud.elements;

import dev.nautilus.gui.Shapes;
import dev.nautilus.hud.HudElement;
import dev.nautilus.hud.HudStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** The 27 slots of your main inventory (without the hotbar) as a small glass grid. */
public class InventoryElement extends HudElement {
    private static final float CELL = 17f, PAD = 4f;
    private static ItemStack[] demo;

    public InventoryElement() {
        super("inventory", "Inventory", false, CENTER, START, 0, 52);
    }

    private static ItemStack demo(int i) {
        if (demo == null) {
            demo = new ItemStack[27];
            var items = new net.minecraft.world.item.Item[]{Items.END_CRYSTAL, Items.OBSIDIAN, Items.TOTEM_OF_UNDYING, Items.GOLDEN_APPLE,
                    Items.EXPERIENCE_BOTTLE, Items.ENDER_PEARL, Items.RESPAWN_ANCHOR, Items.GLOWSTONE, Items.COOKED_BEEF};
            for (int k = 0; k < 27; k++) {
                demo[k] = k % 4 == 3 ? ItemStack.EMPTY : new ItemStack(items[k % items.length]);
                if (!demo[k].isEmpty()) demo[k].setCount(Math.min(demo[k].getMaxStackSize(), 1 + (k * 7) % 64));
            }
        }
        return demo[i];
    }

    @Override public float width(boolean editor) { return 9 * CELL + 2 * PAD; }
    @Override public float height(boolean editor) { return 3 * CELL + 2 * PAD; }

    @Override
    public void render(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float x, float y, float dt, boolean editor) {
        HudStyle.pill(ctx, s, x, y, x + width(editor), y + height(editor), 1f);
        for (int i = 0; i < 27; i++) {
            float cx = x + PAD + (i % 9) * CELL, cy = y + PAD + (i / 9) * CELL;
            s.roundRect(cx + 0.5f, cy + 0.5f, cx + CELL - 0.5f, cy + CELL - 0.5f, 3f, 0x14FFFFFF);
        }
        text.add(() -> {
            var font = mc.font;
            for (int i = 0; i < 27; i++) {
                ItemStack st = editor || mc.player == null ? demo(i) : mc.player.getInventory().getItem(9 + i);
                if (st.isEmpty()) continue;
                float cx = x + PAD + (i % 9) * CELL, cy = y + PAD + (i / 9) * CELL;
                ctx.pose().pushMatrix();
                ctx.pose().translate(cx + 0.5f, cy + 0.5f);
                ctx.item(st, 0, 0);
                ctx.itemDecorations(font, st, 0, 0);
                ctx.pose().popMatrix();
            }
        });
    }
}
