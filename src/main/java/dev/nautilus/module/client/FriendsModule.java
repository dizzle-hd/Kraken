package dev.nautilus.module.client;

import dev.nautilus.hud.HudStyle;
import dev.nautilus.hud.Notifications;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.util.Friends;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Friend list: middle-click a player to add / remove them. While on, every combat module (Aimbot,
 * TriggerBot, SilentAim, AutoCrystal, AutoSmash …) ignores friends; ESP and nametags show them green.
 */
public class FriendsModule extends Module {
    public final BoolSetting protect = add(new BoolSetting("Schützen", "Combat-, Crystal- und Mace-Module greifen Freunde nie an", true));
    private final BoolSetting middleClick = add(new BoolSetting("Mittelklick", "Mittelklick auf einen Spieler fügt ihn hinzu bzw. entfernt ihn", true));
    private final BoolSetting clearAll = add(new BoolSetting("Alle entfernen", "Einschalten leert die Freundesliste (springt danach wieder auf aus)", false));

    private boolean wasDown;

    public FriendsModule() {
        super("Freunde", "Freundesliste: Mittelklick auf einen Spieler = Freund an/aus. Freunde werden von Combat-Modulen nicht angegriffen und grün markiert.",
                Category.CLIENT, Items.POPPY);
        loadEnabled(true);
    }

    @Override
    public void onPreTick() {
        if (clearAll.isOn()) {
            clearAll.set(false);
            int n = Friends.size();
            Friends.clear();
            Notifications.push("Freunde", n + (n == 1 ? " Freund" : " Freunde") + " entfernt", HudStyle.accent());
            dev.nautilus.config.ConfigManager.save();
        }
        if (mc.player == null) return;
        boolean down = middleClick.isOn() && mc.gui.screen() == null && mc.options.keyPickItem.isDown();
        if (down && !wasDown && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player p) {
            String name = p.getGameProfile().name();
            boolean now = Friends.toggle(name);
            Notifications.push("Freunde", name + (now ? " ist jetzt dein Freund" : " ist kein Freund mehr"), now ? HudStyle.GOOD : HudStyle.BAD);
            dev.nautilus.config.ConfigManager.save();
        }
        wasDown = down;
    }

    @Override
    public String getInfo() { return Friends.size() > 0 ? String.valueOf(Friends.size()) : null; }
}
