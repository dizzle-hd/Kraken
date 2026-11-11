package dev.nautilus.module.combat;

import dev.nautilus.hud.HudStyle;
import dev.nautilus.hud.Notifications;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.util.CombatTracker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * Counts the totems other players pop (the server tells every client with entity event 35) and says
 * how many a player popped before dying. The counts also show up in the target HUD.
 * Only reads incoming packets – it never sends anything.
 */
public class PopCounter extends Module {
    private final BoolSetting self = add(new BoolSetting("Own pops", "Also report your own totem pops", true));
    private final BoolSetting deaths = add(new BoolSetting("Report deaths", "Report when a player dies after popping", true));
    private final BoolSetting onlyNear = add(new BoolSetting("Only nearby", "Only report players within 32 blocks", false));

    private int total;

    public PopCounter() {
        super("PopCounter", "Counts the totem pops of other players and reports after how many pops someone dies.",
                Category.COMBAT, Items.TOTEM_OF_UNDYING);
    }

    @Override
    protected void onEnable() { total = 0; }

    private boolean report(Player p) {
        if (mc.player == null) return false;
        if (p == mc.player) return self.isOn();
        return !onlyNear.isOn() || p.distanceTo(mc.player) <= 32;
    }

    /** From the packet listener; counting itself always happens in {@link CombatTracker}. */
    public void onPop(Player p, int count) {
        if (!report(p)) return;
        total++;
        String who = p == mc.player ? "You" : p.getName().getString();
        Notifications.push("Totem pop", who + " popped " + count + (count == 1 ? " totem" : " totems"),
                HudStyle.accent(), 2.5f);
    }

    public void onDeath(Player p, int count) {
        if (!deaths.isOn() || count <= 0 || !report(p)) return;
        String who = p == mc.player ? "You" : p.getName().getString();
        Notifications.push("Death", who + " died after " + count + (count == 1 ? " pop" : " pops"), HudStyle.BAD, 4f);
    }

    @Override
    public String getInfo() {
        return total > 0 ? String.valueOf(total) : null;
    }
}
