package dev.nautilus.test;

import dev.nautilus.gui.RadialScreen;
import dev.nautilus.module.ModuleManager;
import dev.nautilus.module.client.Hud;
import dev.nautilus.module.smp.BaseFinder;
import dev.nautilus.module.smp.XRay;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import com.mojang.blaze3d.platform.InputConstants;

public class NautilusClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        // the first-start setup is tested on its own at the end
        ctx.runOnClient(c -> dev.nautilus.config.ConfigManager.setOnboarded(true));
        // NAUTILUS_GLASS=false → screenshots without liquid glass (website gallery)
        boolean glass = !"false".equalsIgnoreCase(System.getenv("NAUTILUS_GLASS"));
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
                var screen = new dev.nautilus.gui.RadialScreen();
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

            // search: just type – the wheel shows the hits
            ctx.getInput().typeChars("auto");
            ctx.waitTicks(12);
            ctx.takeScreenshot("01e-search-auto");
            // regression: a click on the search bar must not end the search
            ctx.getInput().setCursorPos(ctx.computeOnClient(c -> c.getWindow().getWidth() * 0.5), ctx.computeOnClient(c -> 10.0 * c.getWindow().getGuiScale()));
            ctx.waitTicks(3);
            ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
            ctx.waitTicks(5);
            ctx.getInput().typeChars("ruest");
            ctx.waitTicks(12);
            ctx.takeScreenshot("01f-search-click-umlaut");
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(5);
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(10);

            // hover + click "Combat" (segment 1 of 8 → 45° clockwise from the top)
            clickWheel(ctx, 45, InputConstants.MOUSE_BUTTON_LEFT, false);
            ctx.waitTicks(15);
            ctx.takeScreenshot("02-combat");

            // right click the first module (top) → settings card
            clickWheel(ctx, 0, InputConstants.MOUSE_BUTTON_RIGHT, false);
            ctx.waitTicks(15);
            ctx.takeScreenshot("03-settings");

            // close the settings panel, then left click a module to enable it (SilentAim = index 1 of 6 → 60°)
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(10);
            clickWheel(ctx, 60, InputConstants.MOUSE_BUTTON_LEFT, false);
            ctx.waitTicks(10);
            ctx.takeScreenshot("04-enabled");

            // back to the main wheel, open SMP (index 2 of 8 → 90°) and its BaseFinder settings
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(10);
            clickWheel(ctx, 90, InputConstants.MOUSE_BUTTON_LEFT, false);
            ctx.waitTicks(10);
            clickWheel(ctx, 120, InputConstants.MOUSE_BUTTON_RIGHT, true); // BaseFinder = index 1 of 3 (XRay, BaseFinder, Waypoints)
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
            System.out.println("[NautilusTest] BaseFinder marks (incl. sounds): " + ctx.computeOnClient(c -> ModuleManager.get().get(BaseFinder.class).getInfo()));
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

            // the wheel click above switched SilentAim on
            boolean silentAimOn = ctx.computeOnClient(c -> ModuleManager.get().get(dev.nautilus.module.combat.SilentAim.class).isEnabled());
            System.out.println("[NautilusTest] Wheel click enabled SilentAim: " + silentAimOn);
            ctx.runOnClient(c -> ModuleManager.get().get(dev.nautilus.module.combat.SilentAim.class).setEnabled(false));

            // --- Aimbot: target 30 degrees to the side, it walks sideways; the crosshair must follow it ---
            sp.getServer().runCommand("kill @e[type=!minecraft:player,type=!minecraft:armor_stand]");
            ctx.runOnClient(c -> {
                c.player.setYRot(0);
                c.player.setXRot(0);
            });
            java.util.UUID moving = spawnZombie(sp, -2, 3.5, 1000f);
            ctx.waitTicks(5);
            ctx.runOnClient(c -> ModuleManager.get().get(dev.nautilus.module.combat.Aimbot.class).setEnabled(true));
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
                    return z != null && dev.nautilus.util.RotationUtil.rayHits(c.player.getEyePosition(), c.player.getYRot(), c.player.getXRot(), z.getBoundingBox(), 8);
                });
                if (i >= 10 && hit) onTarget++;
                float y = ctx.computeOnClient(c -> c.player.getYRot());
                aimLog.append(String.format(java.util.Locale.ROOT, "%.1f%s ", y, hit ? "*" : ""));
            }
            System.out.println("[NautilusTest] Aimbot yaw per tick (* = crosshair on target): " + aimLog);
            System.out.println("[NautilusTest] Aimbot crosshair on the moving target in " + onTarget + "/30 ticks");
            ctx.runOnClient(c -> ModuleManager.get().get(dev.nautilus.module.combat.Aimbot.class).setEnabled(false));

            // --- Freecam: the camera flies away, the player stays ---
            double[] fc = ctx.computeOnClient(c -> {
                ModuleManager.get().get(dev.nautilus.module.render.Freecam.class).setEnabled(true);
                return new double[]{c.player.getX(), c.player.getZ()};
            });
            ctx.getInput().holdKey(InputConstants.KEY_W);
            ctx.waitTicks(20);
            ctx.getInput().releaseKey(InputConstants.KEY_W);
            ctx.takeScreenshot("08b-freecam");
            System.out.println("[NautilusTest] Freecam: " + ctx.computeOnClient(c -> String.format(java.util.Locale.ROOT,
                    "player moved %.2f, camera is %.2f away from the player",
                    Math.hypot(c.player.getX() - fc[0], c.player.getZ() - fc[1]),
                    c.gameRenderer.mainCamera().position().distanceTo(c.player.getEyePosition()))));
            ctx.runOnClient(c -> ModuleManager.get().get(dev.nautilus.module.render.Freecam.class).setEnabled(false));

            // --- Zoom: hold C ---
            ctx.getInput().holdKey(InputConstants.KEY_C);
            ctx.waitTicks(10);
            ctx.takeScreenshot("08c-zoom");
            System.out.println("[NautilusTest] Zoom fov while holding C: " + ctx.computeOnClient(c -> c.gameRenderer.mainCamera().getFov()));
            ctx.getInput().releaseKey(InputConstants.KEY_C);
            ctx.waitTicks(5);

            // --- Crystal PvP ---
            crystalTests(ctx, sp);

            // --- Mace: MaceSwap + AutoSmash while falling onto a zombie, WindBoost ---
            maceTests(ctx, sp);

            // --- HUD: every element on, a clean scene for the website shots ---
            var srvHud = sp.getServer();
            srvHud.runCommand("kill @e[type=!minecraft:player]");
            srvHud.runCommand("time set noon");
            srvHud.runCommand("effect give @p minecraft:instant_health 1 4 true");
            srvHud.runCommand("effect give @p minecraft:saturation 1 10 true");
            srvHud.runCommand("item replace entity @p armor.head with minecraft:diamond_helmet[damage=300]");
            srvHud.runCommand("item replace entity @p armor.chest with minecraft:diamond_chestplate[damage=100]");
            srvHud.runCommand("item replace entity @p armor.legs with minecraft:diamond_leggings");
            srvHud.runCommand("item replace entity @p armor.feet with minecraft:diamond_boots[damage=250]");
            // effects + supplies for the effects / supplies / inventory elements
            srvHud.runCommand("effect give @p minecraft:strength 90 1");
            srvHud.runCommand("effect give @p minecraft:fire_resistance 300 0");
            srvHud.runCommand("give @p minecraft:end_crystal 64");
            srvHud.runCommand("give @p minecraft:totem_of_undying 3");
            srvHud.runCommand("give @p minecraft:golden_apple 12");
            ctx.runOnClient(c -> {
                for (var e : dev.nautilus.hud.HudManager.get().elements()) e.enabled = true;
                var mm = ModuleManager.get();
                mm.get(dev.nautilus.module.mace.MaceSwap.class).loadEnabled(true);
                mm.get(dev.nautilus.module.player.AutoSprint.class).loadEnabled(true);
                mm.get(dev.nautilus.module.combat.PopCounter.class).loadEnabled(true);
                c.player.setYRot(0);
                c.player.setXRot(15);
            });
            ctx.waitTicks(80); // let the toasts expire
            ctx.runOnClient(c -> c.gui.hud.getChat().clearMessages(false));
            ctx.waitTicks(2);
            ctx.takeScreenshot("09-hud-all");

            // --- HUD colour: blue, then the cycling rainbow, back to orange ---
            var hudColor = ctx.computeOnClient(c -> ModuleManager.get().get(Hud.class).color);
            ctx.runOnClient(c -> hudColor.set("Blau"));
            ctx.waitTicks(5);
            ctx.takeScreenshot("09b-hud-blue");
            // the wheel follows the HUD colour too (accent, glass tint, panels) – not only the HUD
            ctx.getInput().pressKey(InputConstants.KEY_RSHIFT);
            ctx.waitForScreen(RadialScreen.class);
            ctx.waitTicks(20);
            clickWheel(ctx, 45, InputConstants.MOUSE_BUTTON_LEFT, false);
            ctx.waitTicks(15);
            clickWheel(ctx, 0, InputConstants.MOUSE_BUTTON_RIGHT, false);
            ctx.waitTicks(15);
            ctx.takeScreenshot("09d-wheel-blue");
            ctx.setScreen(() -> null);
            ctx.waitTicks(5);
            ctx.runOnClient(c -> hudColor.set("Regenbogen"));
            ctx.waitTicks(5);
            ctx.takeScreenshot("09c-hud-rainbow");
            System.out.println("[NautilusTest] HUD colour: accent blue/rainbow/orange = " + ctx.computeOnClient(c -> { hudColor.set("Blau"); return String.format("%08X", dev.nautilus.hud.HudStyle.accent()); })
                    + " / " + ctx.computeOnClient(c -> { hudColor.set("Regenbogen"); return String.format("%08X", dev.nautilus.hud.HudStyle.accent()); })
                    + " / " + ctx.computeOnClient(c -> { hudColor.set("Orange"); return String.format("%08X", dev.nautilus.hud.HudStyle.accent()); }));

            // --- HUD editor: open it from the wheel (Client > HUD-Editor) and drag the coordinates ---
            ctx.setScreen(dev.nautilus.gui.HudEditorScreen::new);
            ctx.waitTicks(10);
            ctx.takeScreenshot("10-hud-editor");
            float[] drag = ctx.computeOnClient(c -> {
                var coords = dev.nautilus.hud.HudManager.get().elements().stream().filter(e -> e.id().equals("coords")).findFirst().orElseThrow();
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
            ctx.runOnClient(c -> {
                var mm = ModuleManager.get();
                mm.get(dev.nautilus.module.mace.MaceSwap.class).loadEnabled(false);
                mm.get(dev.nautilus.module.player.AutoSprint.class).loadEnabled(false);
                mm.get(dev.nautilus.module.combat.PopCounter.class).loadEnabled(false);
            });

            // --- first-start setup: all three pages ---
            ctx.setScreen(dev.nautilus.gui.OnboardingScreen::new);
            ctx.waitTicks(10);
            ctx.takeScreenshot("13-onboarding-1");
            ctx.getInput().pressKey(InputConstants.KEY_RETURN);
            ctx.waitTicks(5);
            ctx.takeScreenshot("13-onboarding-2");
            // the colour choice is shown live in the whole setup
            ctx.runOnClient(c -> hudColor.set("Lila"));
            ctx.waitTicks(3);
            ctx.takeScreenshot("13-onboarding-2-purple");
            ctx.runOnClient(c -> hudColor.set("Orange"));
            ctx.getInput().pressKey(InputConstants.KEY_RETURN);
            ctx.waitTicks(5);
            ctx.takeScreenshot("13-onboarding-3");
            ctx.getInput().pressKey(InputConstants.KEY_RETURN);
            ctx.waitTicks(5);
        }
    }

    private static void set(dev.nautilus.module.Module m, String name, Object value) {
        for (var st : m.getSettings()) {
            if (!st.getName().equals(name)) continue;
            if (st instanceof dev.nautilus.setting.BoolSetting b) b.set((Boolean) value);
            else if (st instanceof dev.nautilus.setting.NumberSetting n) n.set(((Number) value).doubleValue());
            else if (st instanceof dev.nautilus.setting.ModeSetting md) md.set((String) value);
            return;
        }
        throw new IllegalArgumentException("no setting " + name + " in " + m.getName());
    }

    private static float playerHp(TestSingleplayerContext sp) {
        return sp.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().get(0).getHealth());
    }

    private static int count(ClientGameTestContext ctx, net.minecraft.world.item.Item item) {
        return ctx.computeOnClient(c -> {
            int n = 0;
            var inv = c.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) n += inv.getItem(i).getCount();
            return n;
        });
    }

    private static void crystalTests(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        var mm = ModuleManager.get();
        var srv = sp.getServer();
        // all of these tests run on a platform high in the air: no water, no terrain in the way
        srv.runCommand("kill @e[type=!minecraft:player]");
        srv.runCommand("execute at @a run tp @a ~ ~40 ~");
        ctx.waitTicks(2);
        srv.runCommand("execute at @a run fill ~-7 ~-1 ~-7 ~7 ~-1 ~7 minecraft:stone");
        srv.runCommand("execute at @a run fill ~-7 ~ ~-7 ~7 ~5 ~7 minecraft:air");
        ctx.runOnClient(c -> c.player.setDeltaMovement(0, 0, 0));
        ctx.waitTicks(10);

        // --- TriggerBot with the Legit preset: how often does it really hit? ---
        srv.runCommand("kill @e[type=!minecraft:player]");
        srv.runCommand("item replace entity @a hotbar.4 with minecraft:iron_sword");
        ctx.runOnClient(c -> { c.player.getInventory().setSelectedSlot(4); c.player.setYRot(0); c.player.setXRot(10); });
        java.util.UUID tz = spawnZombie(sp, 0, 2, 1000f);
        ctx.waitTicks(5);
        ctx.runOnClient(c -> {
            var tb = mm.get(dev.nautilus.module.combat.TriggerBot.class);
            set(tb, "Monster", true);
            tb.applyPreset(tb.getPresets().get(0)); // Legit
            tb.setEnabled(true);
        });
        StringBuilder tlog = new StringBuilder();
        int swings = 0;
        float lastHp = 1000f;
        for (int i = 0; i < 100; i++) {
            ctx.waitTick();
            String st = ctx.computeOnClient(c -> String.format(java.util.Locale.ROOT, "%.2f%s", c.player.getAttackStrengthScale(0f),
                    c.hitResult instanceof net.minecraft.world.phys.EntityHitResult ? "" : "x"));
            float hp = sp.getServer().computeOnServer(server -> {
                var e = server.getPlayerList().getPlayers().get(0).level().getEntity(tz);
                return e instanceof net.minecraft.world.entity.LivingEntity l ? l.getHealth() : -1f;
            });
            if (hp < lastHp - 0.01f) { swings++; tlog.append("HIT@").append(i).append('(').append(st).append(") "); }
            else if (i % 20 == 0) tlog.append("t").append(i).append(':').append(st).append(' ');
            lastHp = hp;
        }
        ctx.runOnClient(c -> mm.get(dev.nautilus.module.combat.TriggerBot.class).setEnabled(false));
        System.out.println("[NautilusTest] TriggerBot Legit with iron sword, 100 ticks: " + swings + " hits: " + tlog);

        srv.runCommand("kill @e[type=!minecraft:player]");
        srv.runCommand("effect give @a minecraft:resistance 600 3 true"); // the player survives his own tests
        srv.runCommand("clear @a");
        srv.runCommand("item replace entity @a hotbar.0 with minecraft:end_crystal 64");
        srv.runCommand("item replace entity @a hotbar.1 with minecraft:glowstone 64");
        srv.runCommand("item replace entity @a hotbar.2 with minecraft:obsidian 64");
        srv.runCommand("item replace entity @a hotbar.3 with minecraft:totem_of_undying 1");
        srv.runCommand("execute at @a run fill ~-6 ~-1 ~-6 ~6 ~-1 ~6 minecraft:obsidian");
        srv.runCommand("execute at @a run fill ~-6 ~ ~-6 ~6 ~3 ~6 minecraft:air");
        ctx.runOnClient(c -> { c.player.setYRot(0); c.player.setXRot(0); c.player.getInventory().setSelectedSlot(5); });
        ctx.waitTicks(10);

        // AutoTotem
        ctx.runOnClient(c -> mm.get(dev.nautilus.module.combat.AutoTotem.class).setEnabled(true));
        StringBuilder totemLog = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            ctx.waitTick();
            if (i == 1) ctx.takeScreenshot("13b-autototem");
            String st = ctx.computeOnClient(c -> (c.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen ? "INV" : "-")
                    + (c.player.getOffhandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING) ? "+T" : ""));
            totemLog.append(st).append(' ');
        }
        System.out.println("[NautilusTest] AutoTotem per tick (INV = inventory open, +T = totem in off hand): " + totemLog);
        boolean totem = ctx.computeOnClient(c -> c.player.getOffhandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING));
        System.out.println("[NautilusTest] AutoTotem: totem in off hand = " + totem);

        // AutoCrystal vs. a tanky zombie 3 blocks south
        java.util.UUID z = spawnZombie(sp, 0, 3, 300f);
        ctx.waitTicks(5);
        int crystalsBefore = count(ctx, net.minecraft.world.item.Items.END_CRYSTAL);
        float hpBefore = playerHp(sp);
        ctx.runOnClient(c -> {
            var ac = mm.get(dev.nautilus.module.crystal.AutoCrystal.class);
            set(ac, "Modus", "Ziel");
            set(ac, "Monster", true);
            set(ac, "Min. Schaden", 4.0);
            ac.setEnabled(true);
        });
        ctx.waitTicks(40);
        ctx.takeScreenshot("14-autocrystal");
        ctx.runOnClient(c -> mm.get(dev.nautilus.module.crystal.AutoCrystal.class).setEnabled(false));
        System.out.println("[NautilusTest] AutoCrystal: zombie " + zombieState(sp, z) + ", crystals used "
                + (crystalsBefore - count(ctx, net.minecraft.world.item.Items.END_CRYSTAL)) + ", player hp " + hpBefore + " -> " + playerHp(sp));

        // AutoCrystal, crosshair mode: hold a crystal, look at obsidian -> it crystals through on its own
        srv.runCommand("kill @e[type=!minecraft:player]");
        sp.getServer().runOnServer(server -> server.getPlayerList().getPlayers().get(0).setHealth(20f)); // fresh for this test
        // a clean, flat platform away from the craters of the test above
        srv.runCommand("execute at @a run tp @a ~ ~30 ~");
        ctx.waitTicks(2);
        srv.runCommand("execute at @a run fill ~-5 ~-1 ~-5 ~5 ~-1 ~5 minecraft:stone");
        srv.runCommand("execute at @a run fill ~-5 ~ ~-5 ~5 ~4 ~5 minecraft:air");
        ctx.runOnClient(c -> c.player.setDeltaMovement(0, 0, 0));
        ctx.waitTicks(10);
        srv.runCommand("execute at @a run setblock ~ ~-1 ~3 minecraft:obsidian");
        ctx.waitTicks(3);
        int crystals2 = count(ctx, net.minecraft.world.item.Items.END_CRYSTAL);
        ctx.runOnClient(c -> {
            c.player.getInventory().setSelectedSlot(0); // crystals
            var eyes = c.player.getEyePosition();
            var base = c.player.blockPosition().below().south(3);
            float[] rot = dev.nautilus.util.RotationUtil.rotationsTo(eyes, net.minecraft.world.phys.Vec3.atCenterOf(base).add(0, 0.5, 0));
            c.player.setYRot(rot[0]);
            c.player.setXRot(rot[1]);
            var ac = mm.get(dev.nautilus.module.crystal.AutoCrystal.class);
            set(ac, "Modus", "Fadenkreuz");
            ac.setEnabled(true);
        });
        StringBuilder xlog = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            ctx.waitTick();
            String st = ctx.computeOnClient(c -> {
                var hr = c.hitResult;
                String h = hr instanceof net.minecraft.world.phys.EntityHitResult eh ? "E:" + eh.getEntity().getType().toShortString()
                        : hr instanceof net.minecraft.world.phys.BlockHitResult bh && bh.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                        ? "B:" + c.level.getBlockState(bh.getBlockPos()).getBlock().getName().getString().replace(" ", "") + (dev.nautilus.util.WorldUtil.canPlaceCrystal(bh.getBlockPos()) ? "+" : "-")
                        : "miss";
                long crystals = c.level.getEntities((net.minecraft.world.entity.Entity) null, c.player.getBoundingBox().inflate(8),
                        e -> e instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal).size();
                return h + "/" + crystals + "/" + c.player.getMainHandItem().getCount();
            });
            if (i % 2 == 0) xlog.append(st).append(' ');
        }
        System.out.println("[NautilusTest] AutoCrystal crosshair per 2 ticks (hit/crystals nearby/crystals in hand): " + xlog);
        ctx.takeScreenshot("14b-autocrystal-crosshair");
        ctx.runOnClient(c -> { mm.get(dev.nautilus.module.crystal.AutoCrystal.class).setEnabled(false); c.player.getInventory().setSelectedSlot(5); });
        System.out.println("[NautilusTest] AutoCrystal crosshair: crystals used in 2 s = " + (crystals2 - count(ctx, net.minecraft.world.item.Items.END_CRYSTAL))
                + ", player hp " + playerHp(sp));

        // AnchorMacro + SafeAnchor: anchor 3 blocks south at foot level, a zombie behind it
        srv.runCommand("kill @e[type=!minecraft:player]");
        srv.runCommand("execute at @a run fill ~-6 ~ ~-6 ~6 ~3 ~6 minecraft:air");
        java.util.UUID z2 = spawnZombie(sp, 0, 5, 300f);
        int glowBefore = count(ctx, net.minecraft.world.item.Items.GLOWSTONE);
        ctx.runOnClient(c -> {
            c.player.getInventory().setSelectedSlot(5);
            // look at the block where the anchor will appear
            var eyes = c.player.getEyePosition();
            var target = net.minecraft.world.phys.Vec3.atCenterOf(c.player.blockPosition().south(3)).add(0, 0.3, 0);
            float[] rot = dev.nautilus.util.RotationUtil.rotationsTo(eyes, target);
            c.player.setYRot(rot[0]);
            c.player.setXRot(rot[1]);
            mm.get(dev.nautilus.module.crystal.SafeAnchor.class).setEnabled(true);
            mm.get(dev.nautilus.module.crystal.AnchorMacro.class).setEnabled(true);
        });
        ctx.waitTicks(3);
        srv.runCommand("execute at @a run setblock ~ ~ ~3 minecraft:respawn_anchor");
        String anchorLog = "";
        for (int i = 0; i < 25; i++) {
            ctx.waitTick();
            if (i == 4) ctx.takeScreenshot("15-anchor-macro");
            anchorLog += ctx.computeOnClient(c -> {
                var p = c.player.blockPosition();
                var a = c.level.getBlockState(p.south(3));
                var sh = c.level.getBlockState(p.south(2));
                return (a.is(net.minecraft.world.level.block.Blocks.RESPAWN_ANCHOR) ? "A" + a.getValue(net.minecraft.world.level.block.RespawnAnchorBlock.CHARGE) : "-")
                        + (sh.is(net.minecraft.world.level.block.Blocks.GLOWSTONE) ? "S" : "") + " ";
            });
        }
        System.out.println("[NautilusTest] AnchorMacro states per tick (A<charge>, S = shield): " + anchorLog);
        System.out.println("[NautilusTest] AnchorMacro: glowstone used " + (glowBefore - count(ctx, net.minecraft.world.item.Items.GLOWSTONE))
                + ", zombie " + zombieState(sp, z2) + ", player hp " + playerHp(sp));
        ctx.runOnClient(c -> {
            mm.get(dev.nautilus.module.crystal.AnchorMacro.class).setEnabled(false);
            mm.get(dev.nautilus.module.crystal.SafeAnchor.class).setEnabled(false);
        });

        // Surround
        srv.runCommand("kill @e[type=!minecraft:player]");
        srv.runCommand("execute at @a run fill ~-6 ~ ~-6 ~6 ~3 ~6 minecraft:air");
        // a clean platform away from the craters of the explosions above
        srv.runCommand("execute at @a run tp @a ~40 ~6 ~");
        ctx.waitTicks(2);
        srv.runCommand("execute at @a run fill ~-4 ~-7 ~-4 ~4 ~-7 ~4 minecraft:stone");
        srv.runCommand("execute at @a run fill ~-4 ~-6 ~-4 ~4 ~3 ~4 minecraft:air");
        ctx.waitTicks(40);
        // stand off-centre first, so the surround has to walk to the middle
        ctx.runOnClient(c -> {
            var b = c.player.blockPosition();
            c.player.setPos(b.getX() + 0.78, c.player.getY(), b.getZ() + 0.27);
            c.player.setYRot(37);
            c.player.setXRot(30);
        });
        ctx.waitTicks(5);
        String before = ctx.computeOnClient(c -> String.format(java.util.Locale.ROOT, "%.2f/%.2f", c.player.getX() - Math.floor(c.player.getX()), c.player.getZ() - Math.floor(c.player.getZ())));
        float[] rotBefore = ctx.computeOnClient(c -> new float[]{c.player.getYRot(), c.player.getXRot()});
        ctx.runOnClient(c -> mm.get(dev.nautilus.module.crystal.Surround.class).setEnabled(true));
        StringBuilder look = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            ctx.waitTick();
            if (i == 18) ctx.takeScreenshot("16a-surround-looking");
            if (i % 4 == 0) {
                String r = ctx.computeOnClient(c -> String.format(java.util.Locale.ROOT, "%.0f/%.0f ", c.player.getYRot(), c.player.getXRot()));
                look.append(r);
            }
        }
        System.out.println("[NautilusTest] Surround camera yaw/pitch every 4 ticks (start " + rotBefore[0] + "/" + rotBefore[1] + "): " + look);
        System.out.println("[NautilusTest] Surround centering by key taps: position in block " + before + " -> "
                + ctx.computeOnClient(c -> String.format(java.util.Locale.ROOT, "%.2f/%.2f", c.player.getX() - Math.floor(c.player.getX()), c.player.getZ() - Math.floor(c.player.getZ()))));
        String around = ctx.computeOnClient(c -> {
            int n = 0;
            for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL)
                if (!c.level.getBlockState(c.player.blockPosition().relative(d)).canBeReplaced()) n++;
            return n + "/4";
        });
        ctx.takeScreenshot("16-surround");
        System.out.println("[NautilusTest] Surround: solid blocks around the feet " + around);
        ctx.runOnClient(c -> {
            mm.get(dev.nautilus.module.crystal.Surround.class).setEnabled(false);
            mm.get(dev.nautilus.module.combat.AutoTotem.class).setEnabled(false);
        });
        srv.runCommand("kill @e[type=!minecraft:player]");
    }

    private static boolean packetHook;
    private static volatile boolean postControl;

    private static void maceTests(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        var mm = ModuleManager.get();
        if (!packetHook) {
            packetHook = true;
            // registered after the client's own END_CLIENT_TICK hook, so this runs last in every tick
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c -> {
                if (postControl && c.player != null) {
                    postControl = false;
                    // control: a punch packet sent here comes after the movement packet = "post"
                    c.player.connection.send(net.minecraft.network.protocol.game.ServerboundPunchPacket.INSTANCE);
                }
                PacketLog.endTick();
            });
        }
        var srv = sp.getServer();
        srv.runCommand("kill @e[type=!minecraft:player]");
        srv.runCommand("clear @a");
        srv.runCommand("item replace entity @a hotbar.0 with minecraft:iron_sword");
        srv.runCommand("item replace entity @a hotbar.1 with minecraft:mace");
        srv.runCommand("item replace entity @a hotbar.2 with minecraft:wind_charge 16");
        srv.runCommand("execute at @a run fill ~-6 ~ ~-6 ~6 ~12 ~6 minecraft:air");
        ctx.runOnClient(c -> { c.player.getInventory().setSelectedSlot(0); c.player.setYRot(0); c.player.setXRot(60); c.player.setDeltaMovement(0, 0, 0); });
        ctx.waitTicks(10);

        // MaceSwap + AutoSmash: sword in the hand, fall 8 blocks next to a zombie
        java.util.UUID z = spawnZombie(sp, 0, 1, 1000f);
        ctx.waitTicks(5);
        ctx.runOnClient(c -> PacketLog.start());
        ctx.runOnClient(c -> {
            mm.get(dev.nautilus.module.mace.MaceSwap.class).setEnabled(true);
            var as = mm.get(dev.nautilus.module.mace.AutoSmash.class);
            set(as, "Monster", true);
            set(as, "Zielen", "Silent");
            set(as, "Min. Fallhöhe", 3.0);
            as.setEnabled(true);
        });
        srv.runCommand("execute at @a run tp @a ~ ~8 ~");
        ctx.runOnClient(c -> c.player.setDeltaMovement(0, 0, 0));
        StringBuilder mlog = new StringBuilder();
        float lastHp = 1000f;
        for (int i = 0; i < 40; i++) {
            ctx.waitTick();
            String st = ctx.computeOnClient(c -> String.format(java.util.Locale.ROOT, "%.1f/s%d", c.player.fallDistance, c.player.getInventory().getSelectedSlot()));
            float hp = srv.computeOnServer(server -> {
                var e = server.getPlayerList().getPlayers().get(0).level().getEntity(z);
                return e instanceof net.minecraft.world.entity.LivingEntity l ? l.getHealth() : -1f;
            });
            if (i == 10) ctx.takeScreenshot("17a-smash-preview");
            if (hp < lastHp - 0.01f) mlog.append("HIT@").append(i).append('(').append(st).append(String.format(java.util.Locale.ROOT, " -%.1f) ", lastHp - hp));
            else if (i % 5 == 0) mlog.append('t').append(i).append(':').append(st).append(' ');
            lastHp = hp;
        }
        int slotAfter = ctx.computeOnClient(c -> c.player.getInventory().getSelectedSlot());
        ctx.takeScreenshot("17-mace-smash");
        System.out.println("[NautilusTest] Mace smash (sword held, MaceSwap + AutoSmash): " + mlog + "| zombie " + zombieState(sp, z) + ", slot after " + slotAfter);
        ctx.runOnClient(c -> {
            mm.get(dev.nautilus.module.mace.AutoSmash.class).setEnabled(false);
            mm.get(dev.nautilus.module.mace.MaceSwap.class).setEnabled(false);
        });
        srv.runCommand("kill @e[type=!minecraft:player]");
        ctx.waitTicks(10);

        // WindBoost: one press of the module key -> jump + wind charge down
        double y0 = ctx.computeOnClient(c -> c.player.getY());
        int charges = count(ctx, net.minecraft.world.item.Items.WIND_CHARGE);
        ctx.runOnClient(c -> mm.get(dev.nautilus.module.mace.WindBoost.class).setEnabled(true));
        double maxY = y0;
        for (int i = 0; i < 30; i++) {
            ctx.waitTick();
            maxY = Math.max(maxY, ctx.computeOnClient(c -> c.player.getY()));
        }
        float pitch = ctx.computeOnClient(c -> c.player.getXRot());
        System.out.println("[NautilusTest] WindBoost: rose " + String.format(java.util.Locale.ROOT, "%.2f", maxY - y0) + " blocks, wind charges "
                + charges + " -> " + count(ctx, net.minecraft.world.item.Items.WIND_CHARGE) + ", camera pitch " + pitch + " (60 = untouched), module on: "
                + ctx.computeOnClient(c -> mm.get(dev.nautilus.module.mace.WindBoost.class).isEnabled()));
        System.out.println("[NautilusTest] Mace packet order (MaceSwap, AutoSmash, WindBoost): " + ctx.computeOnClient(c -> PacketLog.stop()));

        // control: the check really finds a packet sent after the movement packet
        ctx.runOnClient(c -> PacketLog.start());
        ctx.waitTicks(2);
        postControl = true;
        ctx.waitTicks(3);
        System.out.println("[NautilusTest] Packet order control (one punch packet sent post on purpose, expect post=1): " + ctx.computeOnClient(c -> PacketLog.stop()));

        // PopCounter: pop your own totem (the server sends entity event 35)
        ctx.runOnClient(c -> mm.get(dev.nautilus.module.combat.PopCounter.class).setEnabled(true));
        srv.runCommand("effect clear @a minecraft:resistance"); // from the crystal tests: the hit must be lethal
        srv.runCommand("item replace entity @a weapon.offhand with minecraft:totem_of_undying");
        ctx.waitTicks(5);
        int popsBefore = ctx.computeOnClient(c -> dev.nautilus.util.CombatTracker.pops(c.player));
        srv.runCommand("damage @p 1000 minecraft:generic"); // /damage takes exactly one target
        ctx.waitTicks(10);
        ctx.takeScreenshot("17c-popcounter");
        System.out.println("[NautilusTest] PopCounter: own pops " + popsBefore + " -> " + ctx.computeOnClient(c -> dev.nautilus.util.CombatTracker.pops(c.player))
                + ", totem still in off hand " + ctx.computeOnClient(c -> c.player.getOffhandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING))
                + ", toasts " + ctx.computeOnClient(c -> dev.nautilus.hud.Notifications.active().stream().map(t -> t.title() + ": " + t.message()).toList())
                + ", player alive " + ctx.computeOnClient(c -> c.player.isAlive()));
        ctx.runOnClient(c -> mm.get(dev.nautilus.module.combat.PopCounter.class).setEnabled(false));
        ctx.waitTicks(40);
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
            double r = dev.nautilus.gui.RadialScreen.wheelRadius((float) sw, (float) sh) * 0.70;
            double cx = sw / 2.0, cy = dev.nautilus.gui.RadialScreen.wheelCenterY((float) sh);
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
