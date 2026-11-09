package dev.kraken.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Arrays;
import java.util.List;

public class ModeSetting extends Setting<String> {
    private final List<String> modes;

    public ModeSetting(String name, String description, String defaultValue, String... modes) {
        super(name, description, defaultValue);
        this.modes = Arrays.asList(modes);
    }

    public List<String> getModes() { return modes; }
    public boolean is(String mode) { return value.equals(mode); }

    public void cycle(boolean forward) {
        int i = modes.indexOf(value);
        int n = modes.size();
        set(modes.get(((forward ? i + 1 : i - 1) % n + n) % n));
    }

    @Override public JsonElement toJson() { return new JsonPrimitive(value); }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive() && modes.contains(element.getAsString())) set(element.getAsString());
    }
}
