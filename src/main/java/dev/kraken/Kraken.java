package dev.kraken;

import dev.kraken.config.ConfigManager;
import dev.kraken.gui.RadialScreen;
import dev.kraken.module.Module;
import dev.kraken.module.ModuleManager;
import dev.kraken.module.client.Hud;
import dev.kraken.util.Render3D;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Kraken implements ClientModInitializer {
    public static final String MOD_ID = "kraken";
    public static final Logger LOGGER = LoggerFactory.getLogger("Kraken");

    public static final KeyBinding.Category KEY_CATEGORY = KeyBinding.Category.create(Identifier.of(MOD_ID, "kraken"));
    public static KeyBinding openWheel;

    private static Render3D render3D;

    @Override
    public void onInitializeClient() {
        ModuleManager.get().init();
        ConfigManager.load();

        openWheel = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.kraken.wheel", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, KEY_CATEGORY));

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            ModuleManager.get().onPreTick();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openWheel.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new RadialScreen());
            }
            if (client.player == null || client.world == null) return;
            ModuleManager.get().onTick(client);
        });

        WorldRenderEvents.END_MAIN.register(context -> {
            if (render3D == null) render3D = new Render3D();
            render3D.begin();
            for (Module m : ModuleManager.get().getModules()) {
                if (m.isEnabled()) m.onRender3D(context, render3D);
            }
            render3D.end();
        });

        HudElementRegistry.addLast(Identifier.of(MOD_ID, "hud"), (context, tickCounter) -> {
            Hud hud = ModuleManager.get().get(Hud.class);
            if (hud != null && hud.isEnabled()) hud.render(context);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ConfigManager.save());
        LOGGER.info("Kraken loaded – press Right Shift to open the wheel.");
    }
}
