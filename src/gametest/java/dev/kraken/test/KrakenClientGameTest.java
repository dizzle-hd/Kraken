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
        // the first-start setup is tested on its own at the end
        ctx.runOnClient(c -> dev.kraken.config.ConfigManager.setOnboarded(true));
        // KRAKEN_GLASS=false → screenshots without liquid glass (website gallery)
        boolean glass = !"false".equalsIgnoreCase(System.getenv("KRAKEN_GLASS"));
        ctx.runOnClient(c -> ModuleManager.get().get(Hud.class).liquidGlass.set(glass));
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

            // close the settings card, then left click a module to enable it (SilentKillAura = index 2 of 5 → 144°)
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(10);
            clickWheel(ctx, 144, InputConstants.MOUSE_BUTTON_LEFT, false);
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
            // sound sources: a villager behind the chests and a comparator click
            sp.getServer().runCommand("execute at @a run summon minecraft:villager ~6 ~ ~");
            ctx.runOnClient(c -> {
                c.player.setYRot(0);
                c.player.setXRot(20);
                ModuleManager.get().get(BaseFinder.class).setEnabled(true);
            });
            ctx.waitTicks(20);
            sp.getServer().runCommand("execute at @a run playsound minecraft:block.comparator.click block @a ~-4 ~ ~3");
            sp.getServer().runCommand("execute as @e[type=minecraft:villager] at @s run playsound minecraft:entity.villager.ambient neutral @a ~ ~ ~");
            ctx.waitTicks(60);
            System.out.println("[KrakenTest] BaseFinder marks (incl. sounds): " + ctx.computeOnClient(c -> ModuleManager.get().get(BaseFinder.class).getInfo()));
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
            sp.getServer().runCommand("kill @e[type=!minecraft:player,type=!minecraft:armor_stand]");
            java.util.UUID testZombie = spawnZombie(sp, 2, 2, 20f);
            ctx.waitTicks(10);
            String before = zombieState(sp, testZombie);
            boolean auraOn = ctx.computeOnClient(c -> ModuleManager.get().get(dev.kraken.module.combat.SilentKillAura.class).isEnabled());
            float startYaw = ctx.computeOnClient(c -> c.player.getYRot());
            ctx.waitTicks(120);
            String after = zombieState(sp, testZombie);
            float endYaw = ctx.computeOnClient(c -> c.player.getYRot());
            System.out.println("[KrakenTest] SilentKillAura enabled=" + auraOn + " testZombie before=" + before + " after=" + after
                    + " cameraYaw " + startYaw + " -> " + endYaw);
            ctx.takeScreenshot("08-after-killaura");

            // --- BlatantKillAura: the camera must glide to the target, not snap ---
            ctx.runOnClient(c -> {
                ModuleManager.get().get(dev.kraken.module.combat.SilentKillAura.class).setEnabled(false);
                c.player.setYRot(0);
                c.player.setXRot(0);
            });
            // invulnerable target 3 blocks to the side (90 degrees away from the view)
            sp.getServer().runCommand("kill @e[type=!minecraft:player,type=!minecraft:armor_stand]");
            // tanky target 3 blocks to the west: expected camera yaw about +90
            java.util.UUID target = spawnZombie(sp, -3, 0, 1000f);
            ctx.waitTicks(5);
            ctx.runOnClient(c -> ModuleManager.get().get(dev.kraken.module.combat.BlatantKillAura.class).setEnabled(true));
            StringBuilder yaws = new StringBuilder();
            float maxJump = 0;
            float prev = ctx.computeOnClient(c -> c.player.getYRot());
            for (int i = 0; i < 30; i++) {
                ctx.waitTick();
                float y = ctx.computeOnClient(c -> c.player.getYRot());
                maxJump = Math.max(maxJump, Math.abs(y - prev));
                prev = y;
                yaws.append(String.format(java.util.Locale.ROOT, "%.1f ", y));
            }
            System.out.println("[KrakenTest] BlatantKillAura camera yaw per tick: " + yaws);
            System.out.println("[KrakenTest] BlatantKillAura largest yaw change in one tick: " + maxJump);
            String aimInfo = ctx.computeOnClient(c -> {
                var z = c.level.getEntity(target);
                if (z == null) return "target not found on the client";
                float[] rot = dev.kraken.util.RotationUtil.rotationsTo(c.player.getEyePosition(), dev.kraken.util.RotationUtil.aimPoint(c.player.getEyePosition(), z));
                return String.format(java.util.Locale.ROOT, "target dx=%.2f dz=%.2f expected yaw %.1f, camera yaw %.1f",
                        z.getX() - c.player.getX(), z.getZ() - c.player.getZ(), rot[0], c.player.getYRot());
            });
            System.out.println("[KrakenTest] " + aimInfo + ", target " + zombieState(sp, target));
            ctx.runOnClient(c -> ModuleManager.get().get(dev.kraken.module.combat.BlatantKillAura.class).setEnabled(false));

            // --- Aimbot: target 30 degrees to the side, it walks sideways; the crosshair must follow it ---
            sp.getServer().runCommand("kill @e[type=!minecraft:player,type=!minecraft:armor_stand]");
            ctx.runOnClient(c -> {
                c.player.setYRot(0);
                c.player.setXRot(0);
            });
            java.util.UUID moving = spawnZombie(sp, -2, 3.5, 1000f);
            ctx.waitTicks(5);
            ctx.runOnClient(c -> ModuleManager.get().get(dev.kraken.module.combat.Aimbot.class).setEnabled(true));
            int onTarget = 0;
            StringBuilder aimLog = new StringBuilder();
            for (int i = 0; i < 40; i++) {
                if (i >= 10) {
                    // move the zombie 0.15 blocks per tick along x (like a walking player)
                    sp.getServer().runOnServer(server -> {
                        var z = server.getPlayerList().getPlayers().get(0).level().getEntity(moving);
                        if (z != null) z.snapTo(z.getX() + 0.15, z.getY(), z.getZ());
                    });
                }
                ctx.waitTick();
                boolean hit = ctx.computeOnClient(c -> {
                    var z = c.level.getEntity(moving);
                    return z != null && dev.kraken.util.RotationUtil.rayHits(c.player.getEyePosition(), c.player.getYRot(), c.player.getXRot(), z.getBoundingBox(), 8);
                });
                if (i >= 10 && hit) onTarget++;
                float y = ctx.computeOnClient(c -> c.player.getYRot());
                aimLog.append(String.format(java.util.Locale.ROOT, "%.1f%s ", y, hit ? "*" : ""));
            }
            System.out.println("[KrakenTest] Aimbot yaw per tick (* = crosshair on target): " + aimLog);
            System.out.println("[KrakenTest] Aimbot crosshair on the moving target in " + onTarget + "/30 ticks");
            ctx.runOnClient(c -> ModuleManager.get().get(dev.kraken.module.combat.Aimbot.class).setEnabled(false));

            // --- Freecam: the camera flies away, the player stays ---
            double[] fc = ctx.computeOnClient(c -> {
                ModuleManager.get().get(dev.kraken.module.client.Freecam.class).setEnabled(true);
                return new double[]{c.player.getX(), c.player.getZ()};
            });
            ctx.getInput().holdKey(InputConstants.KEY_W);
            ctx.waitTicks(20);
            ctx.getInput().releaseKey(InputConstants.KEY_W);
            ctx.takeScreenshot("08b-freecam");
            System.out.println("[KrakenTest] Freecam: " + ctx.computeOnClient(c -> String.format(java.util.Locale.ROOT,
                    "player moved %.2f, camera is %.2f away from the player",
                    Math.hypot(c.player.getX() - fc[0], c.player.getZ() - fc[1]),
                    c.gameRenderer.mainCamera().position().distanceTo(c.player.getEyePosition()))));
            ctx.runOnClient(c -> ModuleManager.get().get(dev.kraken.module.client.Freecam.class).setEnabled(false));

            // --- Zoom: hold C ---
            ctx.getInput().holdKey(InputConstants.KEY_C);
            ctx.waitTicks(10);
            ctx.takeScreenshot("08c-zoom");
            System.out.println("[KrakenTest] Zoom fov while holding C: " + ctx.computeOnClient(c -> c.gameRenderer.mainCamera().getFov()));
            ctx.getInput().releaseKey(InputConstants.KEY_C);
            ctx.waitTicks(5);

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

            // --- first-start setup: all three pages ---
            ctx.setScreen(dev.kraken.gui.OnboardingScreen::new);
            ctx.waitTicks(10);
            ctx.takeScreenshot("13-onboarding-1");
            ctx.getInput().pressKey(InputConstants.KEY_RETURN);
            ctx.waitTicks(5);
            ctx.takeScreenshot("13-onboarding-2");
            ctx.getInput().pressKey(InputConstants.KEY_RETURN);
            ctx.waitTicks(5);
            ctx.takeScreenshot("13-onboarding-3");
            ctx.getInput().pressKey(InputConstants.KEY_RETURN);
            ctx.waitTicks(5);
        }
    }

    /** Spawns a zombie without AI next to the player on the server and returns its UUID. */
    private static java.util.UUID spawnZombie(TestSingleplayerContext sp, double dx, double dz, float health) {
        return sp.getServer().computeOnServer(server -> {
            var player = server.getPlayerList().getPlayers().get(0);
            var level = player.level();
            var z = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            z.snapTo(player.getX() + dx, player.getY(), player.getZ() + dz);
            z.setNoAi(true);
            z.setPersistenceRequired();
            z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(health);
            z.setHealth(health);
            level.addFreshEntity(z);
            return z.getUUID();
        });
    }

    /** "alive hp=..", "dead" or "missing" (never spawned / removed) – checked on the server. */
    private static String zombieState(TestSingleplayerContext sp, java.util.UUID id) {
        return sp.getServer().computeOnServer(server -> {
            var e = server.getPlayerList().getPlayers().get(0).level().getEntity(id);
            if (e == null) return "removed";
            if (e instanceof net.minecraft.world.entity.LivingEntity l) return l.isAlive() ? "alive hp=" + l.getHealth() : "dead";
            return "?";
        });
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
