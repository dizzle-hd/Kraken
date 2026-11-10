package dev.nautilus.util;

import dev.nautilus.hud.HudStyle;
import dev.nautilus.hud.Notifications;
import net.minecraft.network.chat.Component;

/** Messages of the client – shown as HUD notifications instead of chat lines. */
public final class ChatUtil {
    private ChatUtil() {}

    public static void info(Component message) {
        Notifications.push("Nautilus", message.getString(), HudStyle.accent());
    }

    public static void info(String message) {
        Notifications.push("Nautilus", message, HudStyle.accent());
    }

    public static void toggle(String module, boolean on) {
        Notifications.push(module, on ? "aktiviert" : "deaktiviert", on ? HudStyle.GOOD : HudStyle.BAD, 2.2f);
    }
}
