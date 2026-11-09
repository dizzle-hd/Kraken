package dev.kraken.module.client;

import dev.kraken.module.Category;
import dev.kraken.module.Module;
import net.minecraft.world.item.Items;

/** Master switch of the HUD. The elements themselves are arranged in the HUD editor. */
public class Hud extends Module {
    public final dev.kraken.setting.BoolSetting liquidGlass = add(new dev.kraken.setting.BoolSetting("Liquid Glass",
            "Echtes Flüssigglas (Unschärfe + Lichtbrechung) für Rad und HUD. Aus = leichteres, einfaches Glas, bessere FPS", true));
    public final dev.kraken.setting.NumberSetting wheelSize = add(new dev.kraken.setting.NumberSetting("Rad-Größe",
            "Größe des Rads in Prozent", 100, 60, 140, 5));

    public Hud() {
        super("HUD", "Zeigt die HUD-Elemente (Logo, Module, Koordinaten, FPS, …). Anordnen im HUD-Editor.", Category.CLIENT, Items.ITEM_FRAME);
        loadEnabled(true);
    }
}
