package dev.kraken.module.client;

import dev.kraken.module.Category;
import dev.kraken.module.Module;
import net.minecraft.item.Items;

/** Chat messages when modules are toggled. */
public class Notifications extends Module {
    public Notifications() {
        super("Notifications", "Zeigt im Chat an, wenn ein Modul an- oder ausgeschaltet wird.", Category.CLIENT, Items.BELL);
        loadEnabled(true);
    }
}
