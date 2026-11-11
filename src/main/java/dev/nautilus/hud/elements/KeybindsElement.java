package dev.nautilus.hud.elements;

import dev.nautilus.hud.HudStyle;
import dev.nautilus.hud.Icons;
import dev.nautilus.module.Module;
import dev.nautilus.module.ModuleManager;

import java.util.ArrayList;
import java.util.List;

/** Keybinds panel: every module with a key, the key in a chip (accent while the module is on). */
public class KeybindsElement extends ListPanelElement {
    public KeybindsElement() {
        super("keybinds", "Keybinds", Icons.KEY, "Keybinds", false, END, CENTER, 5, -40);
    }

    @Override protected boolean hideWhenEmpty() { return false; }

    @Override
    protected List<Row> rows(boolean editor) {
        List<Row> rows = new ArrayList<>();
        if (editor && ModuleManager.get().getModules().stream().noneMatch(m -> m.keybind.isBound())) {
            rows.add(new Row(null, 0, "Zoom", "C", HudStyle.TEXT, true));
            rows.add(new Row(null, 0, "AutoCrystal", "R", HudStyle.accent(), true));
            return rows;
        }
        for (Module m : ModuleManager.get().getModules()) {
            if (!m.keybind.isBound()) continue;
            rows.add(new Row(null, 0, m.getName(), m.keybind.display(), m.isEnabled() ? HudStyle.accent() : HudStyle.TEXT, true));
        }
        return rows;
    }
}
