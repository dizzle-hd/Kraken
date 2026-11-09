package dev.nautilus.module.client;

import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import net.minecraft.world.item.Items;

/** Chat messages when modules are toggled. */
public class Notifications extends Module {
    public Notifications() {
        super("Notifications", "Zeigt im HUD an, wenn ein Modul an- oder ausgeschaltet wird.", Category.CLIENT, Items.BELL);
        loadEnabled(true);
    }
}
