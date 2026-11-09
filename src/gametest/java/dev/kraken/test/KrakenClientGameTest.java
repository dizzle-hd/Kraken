package dev.kraken.test;

import dev.kraken.gui.RadialScreen;
import dev.kraken.module.ModuleManager;
import dev.kraken.module.client.Hud;
import dev.kraken.module.smp.BaseFinder;
import dev.kraken.module.smp.XRay;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public class KrakenClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().setUseConsistentSettings(false).create()) {
            sp.getClientWorld().waitForChunksRender();
            ctx.runOnClient(c -> ModuleManager.get().get(Hud.class).loadEnabled(true));

            // open the wheel with the keybind
            ctx.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
            ctx.waitForScreen(RadialScreen.class);
            ctx.waitTicks(20);
            ctx.takeScreenshot("01-wheel");

            // hover + click "Combat" (segment 1 of 3 → 120° clockwise from the top)
            clickWheel(ctx, 120, GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
            ctx.waitTicks(15);
            ctx.takeScreenshot("02-combat");

            // right click the first module (top) → settings card
            clickWheel(ctx, 0, GLFW.GLFW_MOUSE_BUTTON_RIGHT, false);
            ctx.waitTicks(15);
            ctx.takeScreenshot("03-settings");

            // close the settings card, then left click a module to enable it (SilentKillAura = index 2 of 4 → 180°)
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            ctx.waitTicks(10);
            clickWheel(ctx, 180, GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
            ctx.waitTicks(10);
            ctx.takeScreenshot("04-enabled");

            // back to the main wheel, open SMP (index 2 → 240°) and its BaseFinder settings
            ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            ctx.waitTicks(10);
            clickWheel(ctx, 240, GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
            ctx.waitTicks(10);
            clickWheel(ctx, 180, GLFW.GLFW_MOUSE_BUTTON_RIGHT, true);
            ctx.waitTicks(15);
            ctx.takeScreenshot("05-basefinder");

            ctx.setScreen(() -> null);
            ctx.waitTicks(5);

            // --- BaseFinder: build a fake "base" in front of the player ---
            sp.getServer().runCommand("gamemode survival @a");
            sp.getServer().runCommand("difficulty easy");
            sp.getServer().runCommand("time set noon");
            sp.getServer().runCommand("execute at @a run fill ~3 ~ ~-2 ~3 ~1 ~2 minecraft:chest");
            sp.getServer().runCommand("execute at @a run setblock ~-3 ~ ~ minecraft:enchanting_table");
            sp.getServer().runCommand("execute at @a run setblock ~-3 ~ ~2 minecraft:beacon");
            sp.getServer().runCommand("execute at @a run setblock ~ ~ ~6 minecraft:purple_shulker_box");
            sp.getServer().runCommand("execute at @a run summon minecraft:armor_stand ~-2 ~ ~-4");
            ctx.runOnClient(c -> {
                c.player.setYaw(0);
                c.player.setPitch(20);
                ModuleManager.get().get(BaseFinder.class).setEnabled(true);
            });
            ctx.waitTicks(80);
            ctx.takeScreenshot("06-basefinder-esp");

            // --- XRay ---
            ctx.runOnClient(c -> {
                c.player.setPitch(55);
                ModuleManager.get().get(XRay.class).setEnabled(true);
            });
            ctx.waitTicks(40);
            sp.getClientWorld().waitForChunksRender();
            ctx.takeScreenshot("07-xray");
            ctx.runOnClient(c -> {
                ModuleManager.get().get(XRay.class).setEnabled(false);
                ModuleManager.get().get(BaseFinder.class).setEnabled(false);
            });
            ctx.waitTicks(20);
            sp.getClientWorld().waitForChunksRender();

            // --- SilentKillAura vs. a zombie (it is still enabled from the wheel click above) ---
            sp.getServer().runCommand("execute at @a run summon minecraft:zombie ~2 ~ ~2 {NoAI:1b,PersistenceRequired:1b,Health:20f,CustomName:'\"Testzombie\"'}");
            ctx.waitTicks(10);
            boolean auraOn = ctx.computeOnClient(c -> ModuleManager.get().get(dev.kraken.module.combat.SilentKillAura.class).isEnabled());
            float startYaw = ctx.computeOnClient(c -> c.player.getYaw());
            ctx.waitTicks(120);
            String zombies = ctx.computeOnClient(c -> {
                for (var e : c.world.getEntities()) {
                    if (e instanceof net.minecraft.entity.mob.ZombieEntity z && z.hasCustomName()
                            && z.getCustomName().getString().equals("Testzombie")) return "alive hp=" + z.getHealth();
                }
                return "dead";
            });
            float endYaw = ctx.computeOnClient(c -> c.player.getYaw());
            System.out.println("[KrakenTest] SilentKillAura enabled=" + auraOn + " testZombie=" + zombies
                    + " cameraYaw " + startYaw + " -> " + endYaw);
            ctx.takeScreenshot("08-after-killaura");
        }
    }

    /** Moves the cursor onto the wheel segment at {@code angle} (degrees, clockwise from top) and clicks. */
    private static void clickWheel(ClientGameTestContext ctx, double angle, int button, boolean panelOpen) {
        double[] pos = ctx.computeOnClient(c -> {
            var w = c.getWindow();
            double sw = w.getScaledWidth(), sh = w.getScaledHeight();
            double r = Math.min(Math.min((sh - 34) / 2 - 10, sw * 0.30), 165) * 0.75;
            double cx = sw / 2.0, cy = (sh - 26) / 2.0 + 4;
            double a = Math.toRadians(angle);
            double x = cx + Math.sin(a) * r, y = cy - Math.cos(a) * r;
            double f = w.getScaleFactor();
            return new double[]{x * f, y * f};
        });
        ctx.getInput().setCursorPos(pos[0], pos[1]);
        ctx.waitTicks(3);
        ctx.getInput().pressMouse(button);
    }
}
