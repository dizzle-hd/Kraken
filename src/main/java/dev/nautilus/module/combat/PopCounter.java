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
    private final BoolSetting self = add(new BoolSetting("Eigene Pops", "Auch deine eigenen Totem-Pops melden", true));
    private final BoolSetting deaths = add(new BoolSetting("Tode melden", "Melden, wenn ein Spieler nach Pops stirbt", true));
    private final BoolSetting onlyNear = add(new BoolSetting("Nur in der Nähe", "Nur Spieler innerhalb von 32 Blöcken melden", false));

    private int total;

    public PopCounter() {
        super("PopCounter", "Zählt Totem-Pops anderer Spieler und meldet, nach wie vielen Pops jemand stirbt.",
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
        String who = p == mc.player ? "Du" : p.getName().getString();
        Notifications.push("Totem-Pop", who + (p == mc.player ? " hast " : " hat ") + count + (count == 1 ? " Totem" : " Totems") + " verbraucht",
                HudStyle.accent(), 2.5f);
    }

    public void onDeath(Player p, int count) {
        if (!deaths.isOn() || count <= 0 || !report(p)) return;
        String who = p == mc.player ? "Du bist" : p.getName().getString() + " ist";
        Notifications.push("Tod", who + " nach " + count + (count == 1 ? " Pop" : " Pops") + " gestorben", HudStyle.BAD, 4f);
    }

    @Override
    public String getInfo() {
        return total > 0 ? String.valueOf(total) : null;
    }
}
