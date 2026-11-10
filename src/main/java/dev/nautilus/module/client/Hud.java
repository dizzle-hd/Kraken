package dev.nautilus.module.client;

import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import net.minecraft.world.item.Items;

/** Master switch of the HUD. The elements themselves are arranged in the HUD editor. */
public class Hud extends Module {
    public final dev.nautilus.setting.BoolSetting liquidGlass = add(new dev.nautilus.setting.BoolSetting("Liquid Glass",
            "Echtes Flüssigglas (Unschärfe + Lichtbrechung) für Rad und HUD. Aus = leichteres, einfaches Glas, bessere FPS", true));
    public final dev.nautilus.setting.NumberSetting wheelSize = add(new dev.nautilus.setting.NumberSetting("Rad-Größe",
            "Größe des Rads in Prozent", 100, 60, 140, 5));
    public final dev.nautilus.setting.ModeSetting color = add(new dev.nautilus.setting.ModeSetting("HUD-Farbe",
            "Akzentfarbe des ganzen Clients: Rad, Einstellungen, HUD, Logo und Glas-Tönung (Regenbogen = wechselt langsam durch alle Farben)",
            dev.nautilus.hud.HudStyle.COLOR_NAMES[0], dev.nautilus.hud.HudStyle.COLOR_NAMES));

    public Hud() {
        super("HUD", "Zeigt die HUD-Elemente (Logo, Module, Koordinaten, FPS, …). Anordnen im HUD-Editor.", Category.CLIENT, Items.ITEM_FRAME);
        loadEnabled(true);
    }
}
