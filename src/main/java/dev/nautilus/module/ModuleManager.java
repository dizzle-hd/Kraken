package dev.nautilus.module;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nautilus.module.client.AutoReconnect;
import dev.nautilus.module.client.Crosshair;
import dev.nautilus.module.client.Freecam;
import dev.nautilus.module.client.FriendsModule;
import dev.nautilus.module.client.Hud;
import dev.nautilus.module.client.HudEditor;
import dev.nautilus.module.client.Notifications;
import dev.nautilus.module.client.Setup;
import dev.nautilus.module.client.Zoom;
import dev.nautilus.module.combat.Aimbot;
import dev.nautilus.module.combat.SilentAim;
import dev.nautilus.module.combat.TriggerBot;
import dev.nautilus.module.crystal.AnchorMacro;
import dev.nautilus.module.crystal.AutoCrystal;
import dev.nautilus.module.crystal.AutoTotem;
import dev.nautilus.module.crystal.HoleESP;
import dev.nautilus.module.crystal.HoleSnap;
import dev.nautilus.module.crystal.PopCounter;
import dev.nautilus.module.crystal.SafeAnchor;
import dev.nautilus.module.crystal.Surround;
import dev.nautilus.module.mace.AutoSmash;
import dev.nautilus.module.mace.MaceSwap;
import dev.nautilus.module.mace.StunSlam;
import dev.nautilus.module.mace.WindBoost;
import dev.nautilus.module.player.ArmorAlert;
import dev.nautilus.module.player.AutoArmor;
import dev.nautilus.module.player.AutoEat;
import dev.nautilus.module.player.AutoSprint;
import dev.nautilus.module.player.AutoTool;
import dev.nautilus.module.player.Fullbright;
import dev.nautilus.module.smp.BaseFinder;
import dev.nautilus.module.smp.Nametags;
import dev.nautilus.module.smp.PlayerESP;
import dev.nautilus.module.smp.Waypoints;
import dev.nautilus.module.smp.XRay;
import dev.nautilus.module.spear.SpearSwitch;
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

    /** Registers every module; inside a category the order here is the order in the wheel. */
    public void init() {
        // Combat
        register(new TriggerBot());
        register(new SilentAim());
        register(new Aimbot());
        // Crystal PvP
        register(new AutoCrystal());
        register(new AnchorMacro());
        register(new SafeAnchor());
        register(new AutoTotem());
        register(new Surround());
        register(new PopCounter());
        register(new HoleESP());
        register(new HoleSnap());
        // Spear
        register(new SpearSwitch());
        // Mace
        register(new MaceSwap());
        register(new AutoSmash());
        register(new StunSlam());
        register(new WindBoost());
        // Player
        register(new AutoSprint());
        register(new AutoEat());
        register(new AutoTool());
        register(new AutoArmor());
        register(new ArmorAlert());
        register(new Fullbright());
        // SMP
        register(new XRay());
        register(new BaseFinder());
        register(new PlayerESP());
        register(new Nametags());
        register(new Waypoints());
        // Client
        register(new Hud());
        register(new Notifications());
        register(new HudEditor());
        register(new Setup());
        register(new Zoom());
        register(new Freecam());
        register(new FriendsModule());
        register(new Crosshair());
        register(new AutoReconnect());
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

    public boolean notificationsEnabled() {
        Notifications n = get(Notifications.class);
        return n != null && n.isEnabled();
    }

    public void onFrame() {
        for (Module m : modules) if (m.isEnabled()) m.onFrame();
    }

    public void onRender2D(net.minecraft.client.gui.GuiGraphicsExtractor ctx, float partialTick) {
        dev.nautilus.gui.Shapes.setPixelSize(1f / Math.max(1, Minecraft.getInstance().getWindow().getGuiScale()));
        dev.nautilus.util.Projection.begin(ctx.guiWidth(), ctx.guiHeight());
        dev.nautilus.gui.Shapes s = new dev.nautilus.gui.Shapes();
        List<Runnable> text = new ArrayList<>();
        for (Module m : modules) if (m.isEnabled()) m.onRender2D(ctx, s, text, partialTick);
        s.submit(ctx);
        for (Runnable r : text) r.run();
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
