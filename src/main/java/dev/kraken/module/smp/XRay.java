package dev.kraken.module.smp;

import dev.kraken.module.Category;
import dev.kraken.module.Module;
import dev.kraken.module.ModuleManager;
import dev.kraken.setting.BoolSetting;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/** Hides every block except the selected ores. Chunks are rebuilt when toggled. */
public class XRay extends Module {
    private static XRay instance;
    private static volatile Set<Block> visible = Set.of();
    private static volatile boolean active;

    private final BoolSetting diamonds = add(new BoolSetting("Diamant", "Diamanterz anzeigen", true).onChange(this::rebuild));
    private final BoolSetting debris = add(new BoolSetting("Antiker Schrott", "Antiken Schrott anzeigen", true).onChange(this::rebuild));
    private final BoolSetting emerald = add(new BoolSetting("Smaragd", "Smaragderz anzeigen", true).onChange(this::rebuild));
    private final BoolSetting gold = add(new BoolSetting("Gold", "Golderz (inkl. Nether-Gold) anzeigen", true).onChange(this::rebuild));
    private final BoolSetting iron = add(new BoolSetting("Eisen", "Eisenerz anzeigen", true).onChange(this::rebuild));
    private final BoolSetting redstone = add(new BoolSetting("Redstone", "Redstoneerz anzeigen", false).onChange(this::rebuild));
    private final BoolSetting lapis = add(new BoolSetting("Lapis", "Lapiserz anzeigen", false).onChange(this::rebuild));
    private final BoolSetting copper = add(new BoolSetting("Kupfer", "Kupfererz anzeigen", false).onChange(this::rebuild));
    private final BoolSetting coal = add(new BoolSetting("Kohle", "Kohleerz anzeigen", false).onChange(this::rebuild));
    private final BoolSetting quartz = add(new BoolSetting("Quarz", "Netherquarz anzeigen", false).onChange(this::rebuild));
    private final BoolSetting storage = add(new BoolSetting("Kisten & Spawner", "Truhen, Fässer, Shulker, Spawner anzeigen", true).onChange(this::rebuild));
    private final BoolSetting fluids = add(new BoolSetting("Flüssigkeiten", "Wasser und Lava weiterhin anzeigen", false).onChange(this::rebuild));
    private final BoolSetting bright = add(new BoolSetting("Fullbright", "Alles hell machen", true));

    public XRay() {
        super("XRay", "Blendet alle Blöcke außer Erzen und Kisten aus. (Funktioniert nicht mit Sodium)", Category.SMP, Items.DIAMOND_ORE);
        instance = this;
        updateBlocks();
    }

    public static boolean active() { return active; }
    public static boolean isVisible(BlockState state) { return visible.contains(state.getBlock()); }
    public static boolean showFluids() { return instance != null && instance.fluids.isOn(); }
    public static boolean fullbright() { return active && instance.bright.isOn(); }

    private void updateBlocks() {
        Set<Block> set = new HashSet<>();
        if (diamonds.isOn()) { set.add(Blocks.DIAMOND_ORE); set.add(Blocks.DEEPSLATE_DIAMOND_ORE); }
        if (debris.isOn()) set.add(Blocks.ANCIENT_DEBRIS);
        if (emerald.isOn()) { set.add(Blocks.EMERALD_ORE); set.add(Blocks.DEEPSLATE_EMERALD_ORE); }
        if (gold.isOn()) { set.add(Blocks.GOLD_ORE); set.add(Blocks.DEEPSLATE_GOLD_ORE); set.add(Blocks.NETHER_GOLD_ORE); set.add(Blocks.RAW_GOLD_BLOCK); }
        if (iron.isOn()) { set.add(Blocks.IRON_ORE); set.add(Blocks.DEEPSLATE_IRON_ORE); set.add(Blocks.RAW_IRON_BLOCK); }
        if (redstone.isOn()) { set.add(Blocks.REDSTONE_ORE); set.add(Blocks.DEEPSLATE_REDSTONE_ORE); }
        if (lapis.isOn()) { set.add(Blocks.LAPIS_ORE); set.add(Blocks.DEEPSLATE_LAPIS_ORE); }
        if (copper.isOn()) { set.add(Blocks.COPPER_ORE); set.add(Blocks.DEEPSLATE_COPPER_ORE); set.add(Blocks.RAW_COPPER_BLOCK); }
        if (coal.isOn()) { set.add(Blocks.COAL_ORE); set.add(Blocks.DEEPSLATE_COAL_ORE); }
        if (quartz.isOn()) set.add(Blocks.NETHER_QUARTZ_ORE);
        if (storage.isOn()) {
            set.add(Blocks.CHEST); set.add(Blocks.TRAPPED_CHEST); set.add(Blocks.BARREL); set.add(Blocks.ENDER_CHEST);
            set.add(Blocks.SPAWNER); set.add(Blocks.TRIAL_SPAWNER); set.add(Blocks.VAULT);
            set.add(Blocks.SHULKER_BOX);
            set.addAll(Blocks.DYED_SHULKER_BOX.asList());
        }
        visible = Set.copyOf(set);
    }

    private void rebuild() {
        updateBlocks();
        if (isEnabled()) reloadChunks();
    }

    private static void reloadChunks() {
        if (mc.levelExtractor != null && mc.level != null) mc.execute(() -> mc.levelExtractor.allChanged());
    }

    /** Sodium builds the chunk meshes itself, the XRay hooks have no effect there. */
    public static final boolean SODIUM = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium");

    @Override
    protected void onEnable() {
        if (SODIUM) {
            dev.kraken.hud.Notifications.push("XRay", "Funktioniert nicht mit Sodium", 0xFFFF5A5A, 4f);
            setEnabled(false);
            return;
        }
        updateBlocks();
        active = true;
        reloadChunks();
    }

    @Override
    protected void onDisable() {
        active = false;
        reloadChunks();
    }

    @Override
    public String getInfo() {
        return String.valueOf(visible.size());
    }

    /** used by other modules / mixins that need the manager without a direct reference */
    public static XRay get() { return ModuleManager.get().get(XRay.class); }
}
