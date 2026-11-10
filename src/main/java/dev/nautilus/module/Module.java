package dev.nautilus.module;

import dev.nautilus.setting.KeySetting;
import dev.nautilus.setting.Setting;
import dev.nautilus.util.ChatUtil;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
    protected static final Minecraft mc = Minecraft.getInstance();

    private final String name;
    private final String description;
    private final Category category;
    private final Item icon;
    private ItemStack iconStack;
    private boolean enabled;
    private final List<Setting<?>> settings = new ArrayList<>();
    public final KeySetting keybind = new KeySetting("Taste", "Taste zum An-/Ausschalten (Rechtsklick im Feld = entfernen)", -1);

    protected Module(String name, String description, Category category, Item icon) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.icon = icon;
    }

    // ---- presets --------------------------------------------------------------------------------

    /** Ban risk of a preset: shown as a traffic light in the wheel. A rough estimate, no guarantee. */
    public static final int LOW = 0, MEDIUM = 1, HIGH = 2;

    /** A named set of setting values; {@code values} maps setting names to Boolean / Number / String. */
    public record Preset(String name, int risk, String note, java.util.Map<String, Object> values) {}

    private final List<Preset> presets = new ArrayList<>();

    /** Adds a preset: name, risk, note, then pairs of setting name and value. */
    protected void preset(String name, int risk, String note, Object... nameValuePairs) {
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < nameValuePairs.length; i += 2) values.put((String) nameValuePairs[i], nameValuePairs[i + 1]);
        presets.add(new Preset(name, risk, note, values));
    }

    public List<Preset> getPresets() { return Collections.unmodifiableList(presets); }

    private Setting<?> setting(String name) {
        for (Setting<?> s : settings) if (s.getName().equals(name)) return s;
        return null;
    }

    /** Loads all values of a preset into the settings. */
    public void applyPreset(Preset p) {
        for (var e : p.values().entrySet()) {
            Setting<?> s = setting(e.getKey());
            Object v = e.getValue();
            if (s instanceof dev.nautilus.setting.BoolSetting b && v instanceof Boolean bv) b.set(bv);
            else if (s instanceof dev.nautilus.setting.NumberSetting n && v instanceof Number nv) n.set(nv.doubleValue());
            else if (s instanceof dev.nautilus.setting.ModeSetting m && v instanceof String sv) m.set(sv);
        }
    }

    /** Do the current settings equal the preset? */
    public boolean matches(Preset p) {
        for (var e : p.values().entrySet()) {
            Setting<?> s = setting(e.getKey());
            if (s == null) return false;
            Object cur = s.get(), v = e.getValue();
            if (cur instanceof Double d && v instanceof Number n) {
                if (Math.abs(d - n.doubleValue()) > 1e-6) return false;
            } else if (!cur.equals(v)) return false;
        }
        return true;
    }

    protected <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public List<Setting<?>> getSettings() { return Collections.unmodifiableList(settings); }
    public boolean isEnabled() { return enabled; }

    public ItemStack getIcon() {
        if (iconStack == null) iconStack = new ItemStack(icon);
        return iconStack;
    }

    public void toggle() { setEnabled(!enabled); }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) onEnable(); else onDisable();
        if (mc.player != null && ModuleManager.get().notificationsEnabled()) {
            ChatUtil.toggle(name, enabled);
        }
    }

    /** Silently restore the state from the config (calls the hooks, no chat message). */
    public void loadEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) onEnable(); else onDisable();
    }

    protected void onEnable() {}
    protected void onDisable() {}

    /** Called at the start of every client tick (before the player moves / sends packets). */
    public void onPreTick() {}

    /** Called at the end of every client tick (after the movement packets were sent). */
    public void onTick() {}

    /** Called once per rendered frame (after the mouse input of the frame was applied). */
    public void onFrame() {}

    /** World render pass. Use {@link dev.nautilus.util.Render3D} for boxes / lines. */
    public void onRender3D(LevelRenderContext context, dev.nautilus.util.Render3D render) {}

    /**
     * 2D overlay over the world (nametags, waypoint labels), drawn below the HUD. Positions come from
     * {@link dev.nautilus.util.Projection}; shapes go into {@code s}, text/items into {@code text}.
     */
    public void onRender2D(net.minecraft.client.gui.GuiGraphicsExtractor ctx, dev.nautilus.gui.Shapes s, List<Runnable> text, float partialTick) {}

    /** Extra text shown next to the module name in the HUD list. */
    public String getInfo() { return null; }
}
