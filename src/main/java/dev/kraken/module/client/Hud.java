package dev.kraken.module.client;

import dev.kraken.module.Category;
import dev.kraken.module.Module;
import net.minecraft.world.item.Items;

/** Master switch of the HUD. The elements themselves are arranged in the HUD editor. */
public class Hud extends Module {
    public final dev.kraken.setting.BoolSetting liquidGlass = add(new dev.kraken.setting.BoolSetting("Liquid Glass",
            "Echtes Flüssigglas (Unschärfe + Lichtbrechung) für Rad und HUD. Aus = leichteres, einfaches Glas", true));

    public Hud() {
        super("HUD", "Zeigt die HUD-Elemente (Logo, Module, Koordinaten, FPS, …). Anordnen im HUD-Editor.", Category.CLIENT, Items.ITEM_FRAME);
        loadEnabled(true);
    }
}
