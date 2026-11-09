package dev.kraken.test;

import dev.kraken.gui.RadialScreen;
import dev.kraken.module.ModuleManager;
import dev.kraken.module.client.Hud;
import dev.kraken.module.smp.BaseFinder;
import dev.kraken.module.smp.XRay;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import com.mojang.blaze3d.platform.InputConstants;

public class KrakenClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().setUseConsistentSettings(false).create()) {
            // test like on a normal Full-HD monitor
            ctx.getInput().resizeWindow(1920, 1080);
            ctx.waitTicks(5);
            sp.getConnection().waitForChunksRender();
            ctx.runOnClient(c -> ModuleManager.get().get(Hud.class).loadEnabled(true));

            // open the wheel with the keybind
            // regression (crash in build 5): frames at the very start of the open animation and during
            // the close animation, where the centre has size 0
            ctx.runOnClient(c -> {
                // render frames by hand at t = 0 of the open animation (deterministic, independent of FPS)
                var screen = new dev.kraken.gui.RadialScreen();
                screen.init(c.getWindow().getGuiScaledWidth(), c.getWindow().getGuiScaledHeight());
                for (int i = 0; i < 3; i++) {
                    var g = new net.minecraft.client.gui.GuiGraphicsExtractor(c, new net.minecraft.client.renderer.state.gui.GuiRenderState(), 0, 0);
                    screen.extractRenderState(g, 0, 0, 0f);
                }
            });
            ctx.getInput().pressKey(InputConstants.KEY_RSHIFT);
            ctx.waitForScreen(RadialScreen.class);
            ctx.waitTicks(20);
            ctx.takeScreenshot("01-wheel");
            // the head follows the cursor: look to the top-right, then to the bottom-left
            ctx.getInput().setCursorPos(ctx.computeOnClient(c -> c.getWindow().getWidth() * 0.92), ctx.computeOnClient(c -> c.getWindow().getHeight() * 0.08));
            ctx.waitTicks(10);
            ctx.takeScreenshot("01b-head-top-right");
            ctx.getInput().setCursorPos(ctx.computeOnClient(c -> c.getWindow().getWidth() * 0.08), ctx.computeOnClient(c -> c.getWindow().getHeight() * 0.92));
            ctx.waitTicks(10);
            ctx.takeScreenshot("01c-head-bottom-left");
            ctx.getInput().setCursorPos(ctx.computeOnClient(c -> c.getWindow().getWidth() * 0.62), ctx.computeOnClient(c -> c.getWindow().getHeight() * 0.62));
            ctx.waitTicks(10);
            ctx.takeScreenshot("01d-head-combat");

            // hover + click "Combat" (segment 1 of 3 → 120° clockwise from the top)
            clickWheel(ctx, 120, InputConstants.MOUSE_BUTTON_LEFT, false);
            ctx.waitTicks(15);
            ctx.takeScreenshot("02-combat");

            // right click the first module (top) → settings card
            clickWheel(ctx, 0, InputConstants.MOUSE_BUTTON_RIGHT, false);
            ctx.waitTicks(15);
            ctx.takeScreenshot("03-settings");

            // close the settings card, then left click a module to enable it (SilentKillAura = index 2 of 4 → 180°)
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(10);
            clickWheel(ctx, 180, InputConstants.MOUSE_BUTTON_LEFT, false);
            ctx.waitTicks(10);
            ctx.takeScreenshot("04-enabled");

            // back to the main wheel, open SMP (index 2 → 240°) and its BaseFinder settings
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(10);
            clickWheel(ctx, 240, InputConstants.MOUSE_BUTTON_LEFT, false);
            ctx.waitTicks(10);
            clickWheel(ctx, 180, InputConstants.MOUSE_BUTTON_RIGHT, true);
            ctx.waitTicks(15);
            ctx.takeScreenshot("05-basefinder");
            // second settings page
            ctx.getInput().scroll(-1);
            ctx.waitTicks(15);
            ctx.takeScreenshot("05b-basefinder-page2");

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
                c.player.setYRot(0);
                c.player.setXRot(20);
                ModuleManager.get().get(BaseFinder.class).setEnabled(true);
            });
            ctx.waitTicks(80);
            ctx.takeScreenshot("06-basefinder-esp");

            // --- XRay ---
            ctx.runOnClient(c -> {
                c.player.setXRot(55);
                ModuleManager.get().get(XRay.class).setEnabled(true);
            });
            ctx.waitTicks(12);
            ctx.takeScreenshot("07a-toasts");
            ctx.waitTicks(28);
            sp.getConnection().waitForChunksRender();
            ctx.takeScreenshot("07-xray");
            ctx.runOnClient(c -> {
                ModuleManager.get().get(XRay.class).setEnabled(false);
                ModuleManager.get().get(BaseFinder.class).setEnabled(false);
            });
            ctx.waitTicks(20);
            sp.getConnection().waitForChunksRender();

            // --- SilentKillAura vs. a zombie (it is still enabled from the wheel click above) ---
            sp.getServer().runCommand("execute at @a run summon minecraft:zombie ~2 ~ ~2 {NoAI:1b,PersistenceRequired:1b,Health:20f,CustomName:'\"Testzombie\"'}");
            ctx.waitTicks(10);
            boolean auraOn = ctx.computeOnClient(c -> ModuleManager.get().get(dev.kraken.module.combat.SilentKillAura.class).isEnabled());
            float startYaw = ctx.computeOnClient(c -> c.player.getYRot());
            ctx.waitTicks(120);
            String zombies = ctx.computeOnClient(c -> {
                for (var e : c.level.entitiesForRendering()) {
                    if (e instanceof net.minecraft.world.entity.monster.zombie.Zombie z && z.hasCustomName()
                            && z.getCustomName().getString().equals("Testzombie")) return "alive hp=" + z.getHealth();
                }
                return "dead";
            });
            float endYaw = ctx.computeOnClient(c -> c.player.getYRot());
            System.out.println("[KrakenTest] SilentKillAura enabled=" + auraOn + " testZombie=" + zombies
                    + " cameraYaw " + startYaw + " -> " + endYaw);
            ctx.takeScreenshot("08-after-killaura");

            // --- HUD: every element on ---
            ctx.runOnClient(c -> {
                for (var e : dev.kraken.hud.HudManager.get().elements()) e.enabled = true;
            });
            ctx.waitTicks(80); // let the toasts expire
            ctx.takeScreenshot("09-hud-all");

            // --- HUD editor: open it from the wheel (Client > HUD-Editor) and drag the coordinates ---
            ctx.setScreen(dev.kraken.gui.HudEditorScreen::new);
            ctx.waitTicks(10);
            ctx.takeScreenshot("10-hud-editor");
            float[] drag = ctx.computeOnClient(c -> {
                var coords = dev.kraken.hud.HudManager.get().elements().stream().filter(e -> e.id().equals("coords")).findFirst().orElseThrow();
                float sw = c.getWindow().getGuiScaledWidth(), sh = c.getWindow().getGuiScaledHeight();
                float f = c.getWindow().getGuiScale();
                float x = coords.x(sw, true) + 10, y = coords.y(sh, true) + 5;
                return new float[]{x * f, y * f, (sw / 2f - 60) * f, (sh * 0.62f) * f};
            });
            ctx.getInput().setCursorPos(drag[0], drag[1]);
            ctx.waitTicks(2);
            ctx.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
            for (int i = 1; i <= 10; i++) {
                float t = i / 10f;
                ctx.getInput().setCursorPos(drag[0] + (drag[2] - drag[0]) * t, drag[1] + (drag[3] - drag[1]) * t);
                ctx.waitTick();
            }
            ctx.takeScreenshot("11-hud-editor-drag");
            ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
            ctx.waitTicks(3);
            ctx.setScreen(() -> null);
            ctx.waitTicks(5);
            ctx.takeScreenshot("12-hud-after-edit");
        }
    }

    /** Moves the cursor onto the wheel segment at {@code angle} (degrees, clockwise from top) and clicks. */
    private static void clickWheel(ClientGameTestContext ctx, double angle, int button, boolean panelOpen) {
        double[] pos = ctx.computeOnClient(c -> {
            var w = c.getWindow();
            double sw = w.getGuiScaledWidth(), sh = w.getGuiScaledHeight();
            double r = Math.min(Math.min((sh - 30) / 2 - 4, sw * 0.33), 200) * 0.70;
            double cx = sw / 2.0, cy = (sh - 22) / 2.0 + 3;
            double a = Math.toRadians(angle);
            double x = cx + Math.sin(a) * r, y = cy - Math.cos(a) * r;
            double f = w.getGuiScale();
            return new double[]{x * f, y * f};
        });
        ctx.getInput().setCursorPos(pos[0], pos[1]);
        ctx.waitTicks(3);
        ctx.getInput().pressMouse(button);
    }
}
