package dev.nautilus.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The friend list (player names, case-insensitive). Saved in the config; managed by the "Freunde" module. */
public final class Friends {
    private static final Set<String> NAMES = new LinkedHashSet<>();

    private Friends() {}

    private static String key(String name) { return name.toLowerCase(Locale.ROOT); }

    public static boolean is(String name) { return name != null && NAMES.contains(key(name)); }

    public static boolean is(Entity e) { return e instanceof Player p && is(p.getGameProfile().name()); }

    /** Adds or removes a name; returns true when the player is a friend afterwards. */
    public static boolean toggle(String name) {
        String k = key(name);
        if (NAMES.remove(k)) return false;
        NAMES.add(k);
        return true;
    }

    public static void clear() { NAMES.clear(); }

    public static int size() { return NAMES.size(); }

    public static List<String> all() { return Collections.unmodifiableList(new ArrayList<>(NAMES)); }

    /** Do the combat modules have to leave friends alone right now? */
    public static boolean protects() {
        var m = dev.nautilus.module.ModuleManager.get().get(dev.nautilus.module.client.FriendsModule.class);
        return m != null && m.isEnabled() && m.protect.isOn();
    }

    public static JsonArray toJson() {
        JsonArray a = new JsonArray();
        for (String n : NAMES) a.add(n);
        return a;
    }

    public static void fromJson(JsonElement el) {
        NAMES.clear();
        if (el == null || !el.isJsonArray()) return;
        for (JsonElement e : el.getAsJsonArray()) if (e.isJsonPrimitive()) NAMES.add(key(e.getAsString()));
    }
}
