package dev.nautilus.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;

/** GLFW key code, -1 = unbound. */
public class KeySetting extends Setting<Integer> {
    public KeySetting(String name, String description, int defaultValue) {
        super(name, description, defaultValue);
    }

    public boolean isBound() { return value >= 0; }

    public String display() {
        if (!isBound()) return "-";
        return InputConstants.Type.KEYBOARD.getOrCreate(value).getDisplayName().getString();
    }

    @Override public JsonElement toJson() { return new JsonPrimitive(value); }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) set(element.getAsInt());
    }
}
