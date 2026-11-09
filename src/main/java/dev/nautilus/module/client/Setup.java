package dev.nautilus.module.client;

import dev.nautilus.gui.OnboardingScreen;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import net.minecraft.world.item.Items;

/** Not a toggle: clicking it in the wheel opens the first-start setup again. */
public class Setup extends Module {
    public Setup() {
        super("Einrichtung", "Ersteinrichtung erneut öffnen: Rad-Taste, Rad-Größe, Liquid Glass, Hinweise.", Category.CLIENT, Items.WRITABLE_BOOK);
    }

    @Override
    public void toggle() {
        mc.execute(() -> mc.gui.setScreen(new OnboardingScreen()));
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (enabled) toggle();
    }

    @Override
    public void loadEnabled(boolean enabled) {}
}
