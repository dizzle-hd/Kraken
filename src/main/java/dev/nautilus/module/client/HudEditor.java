package dev.nautilus.module.client;

import dev.nautilus.gui.HudEditorScreen;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import net.minecraft.world.item.Items;

/** Not a toggle: clicking it in the wheel opens the HUD editor. */
public class HudEditor extends Module {
    public HudEditor() {
        super("HUD-Editor", "HUD-Elemente verschieben, ein- und ausblenden.", Category.CLIENT, Items.PAINTING);
    }

    @Override
    public void toggle() {
        mc.execute(() -> mc.gui.setScreen(new HudEditorScreen()));
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (enabled) toggle();
    }

    @Override
    public void loadEnabled(boolean enabled) {}
}
