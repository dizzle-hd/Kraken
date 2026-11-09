package dev.kraken.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.util.InputUtil;

/** GLFW key code, -1 = unbound. */
public class KeySetting extends Setting<Integer> {
    public KeySetting(String name, String description, int defaultValue) {
        super(name, description, defaultValue);
    }

    public boolean isBound() { return value >= 0; }

    public String display() {
        if (!isBound()) return "-";
        return InputUtil.Type.KEYSYM.createFromCode(value).getLocalizedText().getString();
    }

    @Override public JsonElement toJson() { return new JsonPrimitive(value); }

    @Override
    public void fromJson(JsonElement element) {
        if (element != null && element.isJsonPrimitive()) set(element.getAsInt());
    }
}
