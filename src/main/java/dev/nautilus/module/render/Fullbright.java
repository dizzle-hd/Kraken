package dev.nautilus.module.render;

import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.module.ModuleManager;
import net.minecraft.world.item.Items;

/** Everything bright like with night vision – client-side only, the server sees nothing. */
public class Fullbright extends Module {
    public Fullbright() {
        super("Fullbright", "Makes everything bright like night vision – only for you, the server notices nothing.", Category.RENDER, Items.GLOWSTONE);
    }

    public static boolean active() {
        Fullbright f = ModuleManager.get().get(Fullbright.class);
        return f != null && f.isEnabled();
    }
}
