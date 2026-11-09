package dev.kraken.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class ChatUtil {
    private ChatUtil() {}

    private static MutableComponent prefix() {
        return Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("Kraken").withStyle(s -> s.withColor(0xFF5A2A)))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static void info(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.gui.hud.getChat().addClientSystemMessage(prefix().append(message));
    }

    public static void info(String message) {
        info(Component.literal(message).withStyle(ChatFormatting.GRAY));
    }

    public static void toggle(String module, boolean on) {
        info(Component.literal(module + " ").withStyle(ChatFormatting.WHITE)
                .append(on ? Component.literal("an").withStyle(ChatFormatting.GREEN) : Component.literal("aus").withStyle(ChatFormatting.RED)));
    }
}
