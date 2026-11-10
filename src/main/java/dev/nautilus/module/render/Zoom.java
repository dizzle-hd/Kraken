package dev.nautilus.module.render;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.KeySetting;
import dev.nautilus.setting.ModeSetting;
import dev.nautilus.setting.NumberSetting;
import net.minecraft.world.item.Items;

/**
 * Zoom on its own key (hold or toggle). Smooth in/out, mouse wheel changes the zoom while zooming,
 * the mouse gets slower the further you zoom in.
 */
public class Zoom extends Module {
    private final KeySetting zoomKey = add(new KeySetting("Zoom-Taste", "Taste zum Zoomen (Rechtsklick im Feld = entfernen)", 67 /* C */));
    private final ModeSetting mode = add(new ModeSetting("Modus", "Halten = zoomt solange die Taste gedrückt ist, Umschalten = ein Druck an, ein Druck aus", "Halten", "Halten", "Umschalten"));
    private final NumberSetting strength = add(new NumberSetting("Stärke", "Vergrößerung (3 = dreifach)", 4, 1.5, 50, 0.5));
    private final BoolSetting scroll = add(new BoolSetting("Mausrad", "Beim Zoomen mit dem Mausrad näher/weiter zoomen", true));
    private final BoolSetting smooth = add(new BoolSetting("Weich", "Weiches Ein- und Auszoomen", true));
    private final BoolSetting slowMouse = add(new BoolSetting("Langsame Maus", "Maus beim Zoomen entsprechend langsamer", true));

    private boolean toggled, wasDown;
    private double scrollFactor = 1;
    private double current = 1;
    private long lastFrame;

    private static Zoom instance;

    public Zoom() {
        super("Zoom", "Zoomt auf Tastendruck heran (Standard: C) – Taste, Stärke und Modus im Rad einstellbar.", Category.RENDER, Items.SPYGLASS);
        instance = this;
        loadEnabled(true); // on by default: only the zoom key does something
    }

    private boolean keyDown() {
        return zoomKey.isBound() && mc.gui.screen() == null && InputConstants.isKeyDown(zoomKey.get());
    }

    @Override
    public void onTick() {
        boolean down = keyDown();
        if (mode.is("Umschalten") && down && !wasDown) toggled = !toggled;
        if (mode.is("Halten")) toggled = false;
        wasDown = down;
        if (!isZooming()) scrollFactor = 1;
    }

    @Override
    protected void onDisable() {
        toggled = false;
        current = 1;
        scrollFactor = 1;
    }

    private boolean isZooming() {
        return isEnabled() && (mode.is("Halten") ? keyDown() : toggled);
    }

    /** Current zoom factor (1 = none), animated per frame. */
    private double factor() {
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;
        double target = isZooming() ? Math.max(1, strength.get() * scrollFactor) : 1;
        if (!smooth.isOn()) current = target;
        else current += (target - current) * (1 - Math.exp(-14 * dt));
        if (Math.abs(current - target) < 0.001) current = target;
        return current;
    }

    // ---- hooks (called from mixins) ------------------------------------------------------------

    /** FOV of the world camera. */
    public static float modifyFov(float fov) {
        if (instance == null) return fov;
        double f = instance.factor();
        return f <= 1.0001 ? fov : (float) (fov / f);
    }

    /** Mouse speed multiplier. */
    public static double mouseScale() {
        if (instance == null || !instance.slowMouse.isOn() || instance.current <= 1.0001) return 1;
        return 1 / instance.current;
    }

    /** @return true if the scroll was used for zooming (hotbar must not scroll). */
    public static boolean onScroll(double amount) {
        if (instance == null || !instance.scroll.isOn() || !instance.isZooming() || amount == 0) return false;
        instance.scrollFactor = Math.max(0.25, Math.min(10, instance.scrollFactor * (amount > 0 ? 1.25 : 0.8)));
        return true;
    }

    @Override
    public String getInfo() {
        return current > 1.01 ? String.format(java.util.Locale.ROOT, "%.1fx", current) : null;
    }
}
