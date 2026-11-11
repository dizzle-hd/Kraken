package dev.nautilus.module.client;

import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import net.minecraft.world.item.Items;

/** Master switch of the HUD. The elements themselves are arranged in the HUD editor. */
public class Hud extends Module {
    public final dev.nautilus.setting.BoolSetting liquidGlass = add(new dev.nautilus.setting.BoolSetting("Liquid Glass",
            "Real liquid glass (blur + refraction) for the wheel and HUD. Off = lighter, simple glass, better FPS", true));
    public final dev.nautilus.setting.ModeSetting style = add(new dev.nautilus.setting.ModeSetting("Style",
            "Panel = dark panels with icons and thin bars, Glass = round glass pills", "Panel", "Panel", "Glass"));
    public final dev.nautilus.setting.NumberSetting wheelSize = add(new dev.nautilus.setting.NumberSetting("Wheel size",
            "Size of the wheel in percent", 100, 60, 140, 5));
    public final dev.nautilus.setting.ModeSetting color = add(new dev.nautilus.setting.ModeSetting("HUD color",
            "Accent colour of the whole client: wheel, settings, HUD, logo and glass tint (Rainbow = slowly cycles through all colours)",
            dev.nautilus.hud.HudStyle.COLOR_NAMES[0], dev.nautilus.hud.HudStyle.COLOR_NAMES));

    public Hud() {
        super("HUD", "Shows the HUD elements (logo, modules, coordinates, FPS, …). Arrange them in the HUD editor.", Category.CLIENT, Items.ITEM_FRAME);
        loadEnabled(true);
    }
}
