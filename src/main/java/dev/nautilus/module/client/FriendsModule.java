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
    public final BoolSetting protect = add(new BoolSetting("Protect", "Combat, crystal and mace modules never attack friends", true));
    private final BoolSetting middleClick = add(new BoolSetting("Middle-click", "Middle-click a player to add or remove them", true));
    private final BoolSetting clearAll = add(new BoolSetting("Remove all", "Turning it on clears the friend list (then switches back off)", false));

    private boolean wasDown;

    public FriendsModule() {
        super("Friends", "Friend list: middle-click a player to add/remove them. Combat modules never attack friends, and they are marked green.",
                Category.CLIENT, Items.POPPY);
        loadEnabled(true);
    }

    @Override
    public void onPreTick() {
        if (clearAll.isOn()) {
            clearAll.set(false);
            int n = Friends.size();
            Friends.clear();
            Notifications.push("Friends", n + (n == 1 ? " friend" : " friends") + " removed", HudStyle.accent());
            dev.nautilus.config.ConfigManager.save();
        }
        if (mc.player == null) return;
        boolean down = middleClick.isOn() && mc.gui.screen() == null && mc.options.keyPickItem.isDown();
        if (down && !wasDown && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player p) {
            String name = p.getGameProfile().name();
            boolean now = Friends.toggle(name);
            Notifications.push("Friends", name + (now ? " is now your friend" : " is no longer your friend"), now ? HudStyle.GOOD : HudStyle.BAD);
            dev.nautilus.config.ConfigManager.save();
        }
        wasDown = down;
    }

    @Override
    public String getInfo() { return Friends.size() > 0 ? String.valueOf(Friends.size()) : null; }
}
