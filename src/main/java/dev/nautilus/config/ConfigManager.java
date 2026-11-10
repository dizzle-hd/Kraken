package dev.nautilus.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nautilus.NautilusClient;
import dev.nautilus.module.Module;
import dev.nautilus.module.ModuleManager;
import dev.nautilus.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Saves module states, keybinds and settings to config/nautilusclient.json. */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("nautilusclient.json");
    /** Config of the client before it was renamed (Kraken): taken over once so no settings get lost. */
    private static final Path LEGACY_FILE = FabricLoader.getInstance().getConfigDir().resolve("kraken.json");

    private static int uid;
    private static boolean onboarded;

    /** Was the first-start setup (OnboardingScreen) completed? */
    public static boolean onboarded() { return onboarded; }

    public static void setOnboarded(boolean value) {
        onboarded = value;
        save();
    }

    private ConfigManager() {}

    /** A small id of this installation, created once and shown in the wheel ("UID"). */
    public static int uid() {
        if (uid <= 0) {
            uid = 1 + new java.util.Random().nextInt(9999);
            save();
        }
        return uid;
    }

    public static void save() {
        JsonObject root = new JsonObject();
        JsonObject modules = new JsonObject();
        for (Module m : ModuleManager.get().getModules()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("enabled", m.isEnabled());
            obj.add("key", m.keybind.toJson());
            JsonObject settings = new JsonObject();
            for (Setting<?> s : m.getSettings()) settings.add(s.getName(), s.toJson());
            obj.add("settings", settings);
            modules.add(m.getName(), obj);
        }
        root.add("modules", modules);
        if (uid > 0) root.addProperty("uid", uid);
        root.addProperty("onboarded", onboarded);
        root.add("hud", dev.nautilus.hud.HudManager.get().toJson());
        root.add("friends", dev.nautilus.util.Friends.toJson());
        root.add("waypoints", dev.nautilus.module.smp.Waypoints.toJson());
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            NautilusClient.LOGGER.error("Could not save Nautilus Client config", e);
        }
    }

    public static void load() {
        if (!Files.exists(FILE) && Files.exists(LEGACY_FILE)) {
            try {
                Files.copy(LEGACY_FILE, FILE);
                NautilusClient.LOGGER.info("Took over the settings of the old config (kraken.json)");
            } catch (IOException e) {
                NautilusClient.LOGGER.warn("Could not take over the old config", e);
            }
        }
        if (!Files.exists(FILE)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject();
            dev.nautilus.hud.HudManager.get().fromJson(root.get("hud"));
            dev.nautilus.util.Friends.fromJson(root.get("friends"));
            dev.nautilus.module.smp.Waypoints.fromJson(root.get("waypoints"));
            if (root.has("uid")) uid = root.get("uid").getAsInt();
            if (root.has("onboarded")) onboarded = root.get("onboarded").getAsBoolean();
            JsonObject modules = root.getAsJsonObject("modules");
            if (modules == null) return;
            for (Module m : ModuleManager.get().getModules()) {
                JsonElement el = modules.get(m.getName());
                if (el == null || !el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                JsonObject settings = obj.getAsJsonObject("settings");
                if (settings != null) {
                    for (Setting<?> s : m.getSettings()) {
                        try {
                            s.fromJson(settings.get(s.getName()));
                        } catch (Exception ignored) {
                        }
                    }
                }
                m.keybind.fromJson(obj.get("key"));
                if (obj.has("enabled")) m.loadEnabled(obj.get("enabled").getAsBoolean());
            }
        } catch (Exception e) {
            NautilusClient.LOGGER.error("Could not load Nautilus Client config", e);
        }
    }
}
