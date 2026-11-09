package dev.kraken.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class ChatUtil {
    private ChatUtil() {}

    private static MutableText prefix() {
        return Text.literal("[").formatted(Formatting.DARK_GRAY)
                .append(Text.literal("Kraken").styled(s -> s.withColor(0xFF5A2A)))
                .append(Text.literal("] ").formatted(Formatting.DARK_GRAY));
    }

    public static void info(Text message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        mc.inGameHud.getChatHud().addMessage(prefix().append(message));
    }

    public static void info(String message) {
        info(Text.literal(message).formatted(Formatting.GRAY));
    }

    public static void toggle(String module, boolean on) {
        info(Text.literal(module + " ").formatted(Formatting.WHITE)
                .append(on ? Text.literal("an").formatted(Formatting.GREEN) : Text.literal("aus").formatted(Formatting.RED)));
    }
}
