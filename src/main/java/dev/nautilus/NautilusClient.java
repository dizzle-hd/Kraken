package dev.nautilus;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nautilus.config.ConfigManager;
import dev.nautilus.gui.HudEditorScreen;
import dev.nautilus.gui.RadialScreen;
import dev.nautilus.hud.HudManager;
import dev.nautilus.hud.TpsTracker;
import net.minecraft.client.Minecraft;
import dev.nautilus.gui.WheelModelsRenderer;
import dev.nautilus.module.Module;
import dev.nautilus.module.ModuleManager;
import dev.nautilus.module.client.Hud;
import dev.nautilus.util.Render3D;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NautilusClient implements ClientModInitializer {
    public static final String MOD_ID = "nautilusclient";
    public static final Logger LOGGER = LoggerFactory.getLogger("Nautilus Client");

    public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
    public static KeyMapping openWheel;

    private static final Render3D RENDER_3D = new Render3D();
    private static boolean onboardingShown;

    /** Draws {@code ours} instead of a vanilla HUD element while {@code active} says so, otherwise the vanilla one. */
    private static void replaceVanilla(Identifier id, java.util.function.BooleanSupplier active,
                                       java.util.function.Consumer<net.minecraft.client.gui.GuiGraphicsExtractor> ours) {
        HudElementRegistry.replaceElement(id, vanilla -> (graphics, deltaTracker) -> {
            if (active.getAsBoolean()) ours.accept(graphics);
            else vanilla.extractRenderState(graphics, deltaTracker);
        });
    }

    @Override
    public void onInitializeClient() {
        ModuleManager.get().init();
        ConfigManager.load();

        openWheel = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.nautilusclient.wheel", InputConstants.Type.KEYBOARD, InputConstants.KEY_RSHIFT, KEY_CATEGORY));

        // 3D item models + player head inside the wheel
        PictureInPictureRendererRegistry.register(ctx -> new WheelModelsRenderer());

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) return;
            ModuleManager.get().onPreTick();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // first start of the game: the setup screen on top of the title screen
            if (!ConfigManager.onboarded() && !onboardingShown && client.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen title
                    && !FabricLoader.getInstance().isModLoaded("nautilusclient-gametest")) {
                onboardingShown = true;
                client.gui.setScreen(new dev.nautilus.gui.OnboardingScreen(title));
            }
            while (openWheel.consumeClick()) {
                if (client.gui.screen() == null) client.gui.setScreen(new RadialScreen());
            }
            if (client.player == null || client.level == null) {
                TpsTracker.reset();
                return;
            }
            ModuleManager.get().onTick(client);
            HudManager.get().tick();
        });

        // the server removed a crystal: AutoCrystal places the next one on that block right away
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal crystal) {
                dev.nautilus.module.crystal.AutoCrystal ac = ModuleManager.get().get(dev.nautilus.module.crystal.AutoCrystal.class);
                if (ac != null && ac.isEnabled()) ac.onCrystalRemoved(crystal);
            }
        });

        // crystal spam: break a crystal the moment it appears
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal crystal) {
                dev.nautilus.module.crystal.AutoCrystal ac = ModuleManager.get().get(dev.nautilus.module.crystal.AutoCrystal.class);
                if (ac != null && ac.isEnabled()) ac.onCrystalSpawn(crystal);
            }
        });

        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            RENDER_3D.begin();
            dev.nautilus.fx.Fx.get().begin();
            for (Module m : ModuleManager.get().getModules()) {
                if (m.isEnabled()) m.onRender3D(context, RENDER_3D);
            }
            RENDER_3D.end(context);
            dev.nautilus.fx.Fx.get().end(context);
        });

        // 2D world overlays of the modules (nametags, waypoints) – below the HUD, hidden with F1
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "world_overlay"), (graphics, deltaTracker) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) return;
            ModuleManager.get().onRender2D(graphics, deltaTracker.getGameTimeDeltaPartialTick(false));
        });

        // custom crosshair (Client → Fadenkreuz) instead of the vanilla one
        HudElementRegistry.replaceElement(net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CROSSHAIR,
                vanilla -> (graphics, deltaTracker) -> {
                    if (!dev.nautilus.module.render.Crosshair.render(graphics)) vanilla.extractRenderState(graphics, deltaTracker);
                });

        dev.nautilus.module.client.AutoReconnect.register();

        // Hotbar module: glass hotbar + ring gauges instead of the vanilla hotbar, hearts, food, armor, air and XP
        replaceVanilla(net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.HOTBAR,
                dev.nautilus.module.visuals.Hotbar::replacesHotbar, dev.nautilus.module.visuals.Hotbar::renderHotbar);
        for (Identifier id : new Identifier[]{net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.HEALTH_BAR,
                net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.FOOD_BAR,
                net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.ARMOR_BAR,
                net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.AIR_BAR}) {
            replaceVanilla(id, dev.nautilus.module.visuals.Hotbar::replacesStatus, g -> {});
        }
        replaceVanilla(net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.HELD_ITEM_TOOLTIP,
                dev.nautilus.module.visuals.Hotbar::replacesItemName, dev.nautilus.module.visuals.Hotbar::renderItemName);
        for (Identifier id : new Identifier[]{net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.INFO_BAR,
                net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.EXPERIENCE_LEVEL}) {
            replaceVanilla(id, dev.nautilus.module.visuals.Hotbar::replacesXp, g -> {});
        }

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), (graphics, deltaTracker) -> {
            Minecraft mc = Minecraft.getInstance();
            Hud hud = ModuleManager.get().get(Hud.class);
            if (hud == null || !hud.isEnabled() || mc.gui.hud.isHidden() || mc.getDebugOverlay().showDebugScreen()) return;
            if (mc.gui.screen() instanceof HudEditorScreen) return; // the editor draws its own preview
            HudManager.get().render(graphics, false, null);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ConfigManager.save());
        LOGGER.info("Nautilus Client loaded – press Right Shift to open the wheel.");
    }
}
