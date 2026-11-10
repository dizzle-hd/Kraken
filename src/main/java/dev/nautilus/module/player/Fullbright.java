package dev.nautilus.module.player;

import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.module.ModuleManager;
import net.minecraft.world.item.Items;

/** Everything bright like with night vision – client-side only, the server sees nothing. */
public class Fullbright extends Module {
    public Fullbright() {
        super("Fullbright", "Macht alles hell wie mit Nachtsicht – nur bei dir, der Server merkt nichts.", Category.PLAYER, Items.GLOWSTONE);
    }

    public static boolean active() {
        Fullbright f = ModuleManager.get().get(Fullbright.class);
        return f != null && f.isEnabled();
    }
}
