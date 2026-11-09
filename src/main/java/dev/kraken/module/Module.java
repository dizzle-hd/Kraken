package dev.kraken.module;

import dev.kraken.setting.KeySetting;
import dev.kraken.setting.Setting;
import dev.kraken.util.ChatUtil;
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

    /** World render pass. Use {@link dev.kraken.util.Render3D} for boxes / lines. */
    public void onRender3D(LevelRenderContext context, dev.kraken.util.Render3D render) {}

    /** Extra text shown next to the module name in the HUD list. */
    public String getInfo() { return null; }
}
