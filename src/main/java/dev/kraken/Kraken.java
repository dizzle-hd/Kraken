package dev.kraken;

import com.mojang.blaze3d.platform.InputConstants;
import dev.kraken.config.ConfigManager;
import dev.kraken.gui.RadialScreen;
import dev.kraken.module.Module;
import dev.kraken.module.ModuleManager;
import dev.kraken.module.client.Hud;
import dev.kraken.util.Render3D;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Kraken implements ClientModInitializer {
    public static final String MOD_ID = "kraken";
    public static final Logger LOGGER = LoggerFactory.getLogger("Kraken");

    public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "kraken"));
    public static KeyMapping openWheel;

    private static final Render3D RENDER_3D = new Render3D();

    @Override
    public void onInitializeClient() {
        ModuleManager.get().init();
        ConfigManager.load();

        openWheel = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.kraken.wheel", InputConstants.Type.KEYBOARD, InputConstants.KEY_RSHIFT, KEY_CATEGORY));

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) return;
            ModuleManager.get().onPreTick();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openWheel.consumeClick()) {
                if (client.gui.screen() == null) client.gui.setScreen(new RadialScreen());
            }
            if (client.player == null || client.level == null) return;
            ModuleManager.get().onTick(client);
        });

        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            RENDER_3D.begin();
            for (Module m : ModuleManager.get().getModules()) {
                if (m.isEnabled()) m.onRender3D(context, RENDER_3D);
            }
            RENDER_3D.end(context);
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), (graphics, deltaTracker) -> {
            Hud hud = ModuleManager.get().get(Hud.class);
            if (hud != null && hud.isEnabled()) hud.render(graphics);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ConfigManager.save());
        LOGGER.info("Kraken loaded – press Right Shift to open the wheel.");
    }
}
