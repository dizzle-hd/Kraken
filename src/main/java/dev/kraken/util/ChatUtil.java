package dev.kraken.util;

import dev.kraken.hud.HudStyle;
import dev.kraken.hud.Notifications;
import net.minecraft.network.chat.Component;

/** Messages of the client – shown as HUD notifications instead of chat lines. */
public final class ChatUtil {
    private ChatUtil() {}

    public static void info(Component message) {
        Notifications.push("Kraken", message.getString(), HudStyle.ACCENT);
    }

    public static void info(String message) {
        Notifications.push("Kraken", message, HudStyle.ACCENT);
    }

    public static void toggle(String module, boolean on) {
        Notifications.push(module, on ? "aktiviert" : "deaktiviert", on ? HudStyle.GOOD : HudStyle.BAD, 2.2f);
    }
}
