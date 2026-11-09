package dev.kraken.module.client;

import dev.kraken.module.Category;
import dev.kraken.module.Module;
import net.minecraft.world.item.Items;

/** Master switch of the HUD. The elements themselves are arranged in the HUD editor. */
public class Hud extends Module {
    public Hud() {
        super("HUD", "Zeigt die HUD-Elemente (Logo, Module, Koordinaten, FPS, …). Anordnen im HUD-Editor.", Category.CLIENT, Items.ITEM_FRAME);
        loadEnabled(true);
    }
}
