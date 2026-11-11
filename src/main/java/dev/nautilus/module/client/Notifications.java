package dev.nautilus.module.client;

import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import net.minecraft.world.item.Items;

/** Chat messages when modules are toggled. */
public class Notifications extends Module {
    public Notifications() {
        super("Notifications", "Shows in the HUD when a module is turned on or off.", Category.CLIENT, Items.BELL);
        loadEnabled(true);
    }
}
