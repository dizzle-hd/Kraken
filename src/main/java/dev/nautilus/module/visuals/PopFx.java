package dev.nautilus.module.visuals;

import dev.nautilus.fx.Effects;
import dev.nautilus.fx.Fx;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.module.ModuleManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Totem pops: golden-green rings rush up around the player, with a burst of sparks. */
public class PopFx extends Module {
    public PopFx() {
        super("Totem effect", "When someone pops a totem: golden and green rings shoot up around them, with a shower of sparks.",
                Category.VISUALS, Items.TOTEM_OF_UNDYING);
    }

    /** CombatTracker: {@code p} just popped a totem. */
    public static void onPop(Player p) {
        PopFx m = ModuleManager.get().get(PopFx.class);
        if (m == null || !m.isEnabled() || mc.player == null || p.distanceTo(mc.player) > 64) return;
        Vec3 feet = p.position();
        float h = p.getBbHeight();
        int[] colors = {0xFFC23D, 0x7CF06A, 0xFFF3B0, 0xFFC23D};
        for (int i = 0; i < colors.length; i++) {
            long start = System.currentTimeMillis() + i * 90L;
            Fx.Effect ring = Effects.wave(feet.add(0, 0.1, 0), 0.9f, 0.45f, 0.14f, colors[i], 0.95f, 700, h + 0.6f);
            Fx.add((fx, now) -> now < start || ring.draw(fx, now));
        }
        Effects.burst(feet.add(0, h * 0.6, 0), 30, 3.5f, 0xFFC23D, 0x7CF06A, 0.07f, 900);
    }
}
