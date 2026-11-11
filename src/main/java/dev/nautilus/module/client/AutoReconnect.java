package dev.nautilus.module.client;

import dev.nautilus.gui.UiText;
import dev.nautilus.hud.HudStyle;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.module.ModuleManager;
import dev.nautilus.setting.NumberSetting;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.item.Items;

import java.util.Locale;

/** Reconnects to the last server after a kick / disconnect, with a countdown on the disconnect screen. */
public class AutoReconnect extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", "Seconds until reconnecting", 5, 1, 60, 1));
    private final NumberSetting attempts = add(new NumberSetting("Attempts", "How many times in a row to try (then give up)", 5, 1, 20, 1));

    private static ServerData lastServer;
    private static int tries;

    public AutoReconnect() {
        super("AutoReconnect", "Reconnects automatically after a kick or lost connection – with a countdown, the back button cancels.",
                Category.CLIENT, Items.ENDER_PEARL);
    }

    /** Hooks: remember the server on join, count down on the disconnect screen. */
    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.getCurrentServer() != null) lastServer = client.getCurrentServer();
            tries = 0;
        });
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof DisconnectedScreen)) return;
            AutoReconnect m = ModuleManager.get().get(AutoReconnect.class);
            if (m == null || !m.isEnabled() || lastServer == null || tries >= m.attempts.getInt()) return;
            long start = System.currentTimeMillis();
            long ms = (long) (m.delay.get() * 1000);
            ScreenEvents.afterTick(screen).register(s -> {
                if (client.gui.screen() != s || System.currentTimeMillis() - start < ms) return;
                tries++;
                ServerData data = lastServer;
                ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), client, ServerAddress.parseString(data.ip), data, false, null);
            });
            ScreenEvents.afterExtract(screen).register((s, ctx, mx, my, delta) -> {
                float left = Math.max(0f, (ms - (System.currentTimeMillis() - start)) / 1000f);
                String text = String.format(Locale.ROOT, "Reconnecting to %s in %.1f s  (attempt %d/%d · Back = cancel)",
                        lastServer.ip, left, tries + 1, m.attempts.getInt());
                UiText.centered(ctx, Minecraft.getInstance().font, text, s.width / 2f, 12f, 0.9f, HudStyle.accent(), false, true);
            });
        });
    }

    @Override
    public String getInfo() { return lastServer == null ? null : lastServer.ip; }
}
