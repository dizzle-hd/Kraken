package dev.kraken.module;

import dev.kraken.module.client.Hud;
import dev.kraken.module.client.HudEditor;
import dev.kraken.module.client.Notifications;
import dev.kraken.module.combat.BlatantKillAura;
import dev.kraken.module.combat.SilentAim;
import dev.kraken.module.combat.SilentKillAura;
import dev.kraken.module.combat.TriggerBot;
import dev.kraken.module.smp.BaseFinder;
import dev.kraken.module.smp.XRay;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager {
    private static final ModuleManager INSTANCE = new ModuleManager();

    private final List<Module> modules = new ArrayList<>();
    private final Map<Class<? extends Module>, Module> byClass = new HashMap<>();
    private final Map<Integer, Boolean> keyStates = new HashMap<>();

    public static ModuleManager get() { return INSTANCE; }

    public void init() {
        // Combat
        register(new TriggerBot());
        register(new SilentAim());
        register(new SilentKillAura());
        register(new BlatantKillAura());
        // SMP
        register(new XRay());
        register(new BaseFinder());
        // Client
        register(new Hud());
        register(new Notifications());
        register(new HudEditor());
    }

    private void register(Module module) {
        modules.add(module);
        byClass.put(module.getClass(), module);
    }

    public List<Module> getModules() { return modules; }

    public List<Module> getModules(Category category) {
        List<Module> list = new ArrayList<>();
        for (Module m : modules) if (m.getCategory() == category) list.add(m);
        return list;
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> clazz) { return (T) byClass.get(clazz); }

    public Module getByName(String name) {
        for (Module m : modules) if (m.getName().equalsIgnoreCase(name)) return m;
        return null;
    }

    public boolean notificationsEnabled() {
        Notifications n = get(Notifications.class);
        return n != null && n.isEnabled();
    }

    public void onFrame() {
        for (Module m : modules) if (m.isEnabled()) m.onFrame();
    }

    public void onPreTick() {
        for (Module m : modules) if (m.isEnabled()) m.onPreTick();
    }

    public void onTick(Minecraft mc) {
        // module keybinds (only while no screen is open)
        if (mc.gui.screen() == null) {
            for (Module m : modules) {
                if (!m.keybind.isBound()) continue;
                int key = m.keybind.get();
                boolean down = InputConstants.isKeyDown(key);
                boolean was = keyStates.getOrDefault(key, false);
                if (down && !was) m.toggle();
            }
        }
        keyStates.clear();
        for (Module m : modules) {
            if (m.keybind.isBound()) keyStates.put(m.keybind.get(), InputConstants.isKeyDown(m.keybind.get()));
        }

        for (Module m : modules) if (m.isEnabled()) m.onTick();
    }
}
