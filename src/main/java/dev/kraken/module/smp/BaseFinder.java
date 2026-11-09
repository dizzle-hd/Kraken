package dev.kraken.module.smp;

import dev.kraken.module.Category;
import dev.kraken.module.Module;
import dev.kraken.setting.BoolSetting;
import dev.kraken.setting.NumberSetting;
import dev.kraken.util.ChatUtil;
import dev.kraken.util.Render3D;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scans loaded chunks and server packets for signs of player activity. Every hint type can be
 * switched on/off on its own.
 */
public class BaseFinder extends Module {

    /** The different kinds of hints. */
    public enum Hint {
        AMETHYST("Amethyst-Wachstum", 0xFFC77DFF),
        KELP("Gewachsenes Kelp", 0xFF4CD964),
        STORAGE("Lager", 0xFFFFB347),
        PLAYER_BLOCK("Spieler-Block", 0xFF5AC8FA),
        PORTAL("Aktives Portal", 0xFFB45AFF),
        SOUND("Geräusch", 0xFFFF5A5A),
        ENTITY("Objekt", 0xFFFFE066);

        public final String label;
        public final int color;

        Hint(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    private record Mark(BlockPos pos, Hint hint) {}
    private record SoundMark(Vec3 pos, String name, long time) {}

    // --- sub modules (each can be turned on / off) --------------------------------------------
    private final BoolSetting amethyst = add(new BoolSetting("Amethyst-Grower",
            "Geoden mit vielen ausgewachsenen Amethyst-Clustern = Chunk war lange geladen (Spieler in der Nähe)", true));
    private final NumberSetting amethystRatio = add(new NumberSetting("Cluster-Anteil",
            "Ab welchem Anteil ausgewachsener Cluster ein Chunk markiert wird", 0.6, 0.2, 1.0, 0.05).visibleWhen(amethyst::isOn));
    private final BoolSetting sounds = add(new BoolSetting("Sound-Blöcke",
            "Markiert Blöcke, an denen der Server Geräusche abspielt (Kolben, Türen, Truhen, Öfen, Amboss …)", true));
    private final BoolSetting buildSounds = add(new BoolSetting("Bau-Geräusche",
            "Auch Abbau-/Platzier-Geräusche anderer Spieler markieren", true).visibleWhen(sounds::isOn));
    private final NumberSetting soundTime = add(new NumberSetting("Sound-Dauer",
            "Wie lange Sound-Markierungen sichtbar bleiben (Sekunden)", 60, 5, 600, 5).visibleWhen(sounds::isOn));
    private final BoolSetting storage = add(new BoolSetting("Lager",
            "Chunks mit vielen Truhen/Fässern/Öfen/Trichtern, sowie jede Shulker- und Endertruhe", true));
    private final NumberSetting storageMin = add(new NumberSetting("Min. Lager",
            "Ab wie vielen Lager-Blöcken ein Chunk markiert wird", 4, 1, 30, 1).visibleWhen(storage::isOn));
    private final BoolSetting playerBlocks = add(new BoolSetting("Spieler-Blöcke",
            "Blöcke, die fast nie natürlich vorkommen (Leuchtfeuer, Zaubertisch, Amboss, Schilder, Beobachter …)", true));
    private final BoolSetting portals = add(new BoolSetting("Portale",
            "Aktivierte Netherportale in der Oberwelt / im End", true));
    private final BoolSetting kelp = add(new BoolSetting("Kelp-Wachstum",
            "Kelp, das bis zur Wasseroberfläche gewachsen ist = Chunk war lange geladen", false));
    private final NumberSetting kelpMin = add(new NumberSetting("Min. Kelp",
            "Ab wie vielen ausgewachsenen Kelp-Spitzen ein Chunk markiert wird", 12, 3, 60, 1).visibleWhen(kelp::isOn));
    private final BoolSetting entities = add(new BoolSetting("Objekte",
            "Rahmen, Rüstungsständer, benannte & gezähmte Tiere, Kistenloren", true));
    private final BoolSetting chat = add(new BoolSetting("Chat-Meldungen", "Funde im Chat melden", true));
    private final BoolSetting tracers = add(new BoolSetting("Tracer", "Linien von dir zu den Funden zeichnen", false));
    private final NumberSetting scanSpeed = add(new NumberSetting("Scan-Tempo", "Chunks pro Tick (höher = schneller, mehr Last)", 2, 1, 10, 1));

    // --- state ----------------------------------------------------------------------------------
    private final Map<Long, List<Mark>> results = new ConcurrentHashMap<>();
    private final ArrayDeque<Long> queue = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();
    private final Set<String> notified = new HashSet<>();
    private final List<SoundMark> soundMarks = new ArrayList<>();
    private Level lastWorld;
    private int rescanTimer;

    private static final Set<Block> PLAYER_BLOCKS = Set.of(
            Blocks.BEACON, Blocks.ENCHANTING_TABLE, Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL,
            Blocks.RESPAWN_ANCHOR, Blocks.CONDUIT, Blocks.OBSERVER, Blocks.CRAFTER, Blocks.COMPARATOR,
            Blocks.DAYLIGHT_DETECTOR, Blocks.NOTE_BLOCK, Blocks.JUKEBOX, Blocks.TARGET,
            Blocks.CALIBRATED_SCULK_SENSOR, Blocks.DROPPER, Blocks.SLIME_BLOCK, Blocks.HONEY_BLOCK,
            Blocks.NETHERITE_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.EMERALD_BLOCK, Blocks.LODESTONE);

    /** Sound ids that are (almost) always caused by players or player-built machines. */
    private static final String[] ACTIVITY_SOUNDS = {
            "piston", "chest", "barrel", "shulker_box", "door", "trapdoor", "fence_gate", "note_block", "dispenser",
            "anvil", "furnace", "smoker", "blast_furnace", "brewing_stand", "enchantment_table", "beacon", "lever",
            "button", "pressure_plate", "crafter", "grindstone", "smithing_table", "lectern", "bell", "ender_chest",
            "jukebox", "respawn_anchor", "composter", "cartography_table", "loom", "stonecutter", "minecart",
            "comparator", "honey_block", "slime_block", "portal.trigger", "portal.travel", "copper_bulb", "chiseled_bookshelf",
            "decorated_pot", "vault", "item_frame", "armor_stand", "sign", "firework_rocket"};

    public BaseFinder() {
        super("BaseFinder", "Sucht Hinweise auf Basen: Amethyst-Wachstum, Sound-Blöcke, Lager, Spieler-Blöcke und mehr.",
                Category.SMP, Items.RECOVERY_COMPASS);
    }

    @Override
    protected void onEnable() {
        clear();
    }

    @Override
    protected void onDisable() {
        clear();
    }

    private void clear() {
        results.clear();
        queue.clear();
        queued.clear();
        notified.clear();
        synchronized (soundMarks) { soundMarks.clear(); }
        rescanTimer = 0;
    }

    @Override
    public String getInfo() {
        int n = 0;
        for (List<Mark> l : results.values()) n += l.size();
        synchronized (soundMarks) { n += soundMarks.size(); }
        return n == 0 ? null : String.valueOf(n);
    }

    // ---------------------------------------------------------------------------------------------
    // Chunk scanning
    // ---------------------------------------------------------------------------------------------

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        if (mc.level != lastWorld) {
            clear();
            lastWorld = mc.level;
        }

        // re-queue every loaded chunk every 10 seconds (picks up new chunks and changes)
        if (--rescanTimer <= 0) {
            rescanTimer = 200;
            int view = mc.options.getEffectiveRenderDistance();
            ChunkPos center = mc.player.chunkPosition();
            Set<Long> loaded = new HashSet<>();
            for (int dx = -view; dx <= view; dx++) {
                for (int dz = -view; dz <= view; dz++) {
                    int cx = center.x() + dx, cz = center.z() + dz;
                    if (mc.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false) == null) continue;
                    long key = ChunkPos.pack(cx, cz);
                    loaded.add(key);
                    if (queued.add(key)) queue.add(key);
                }
            }
            results.keySet().retainAll(loaded);
        }

        int budget = scanSpeed.getInt();
        while (budget-- > 0 && !queue.isEmpty()) {
            long key = queue.poll();
            queued.remove(key);
            LevelChunk chunk = mc.level.getChunkSource().getChunk(ChunkPos.getX(key), ChunkPos.getZ(key), ChunkStatus.FULL, false);
            if (chunk == null) { results.remove(key); continue; }
            List<Mark> marks = scan(chunk);
            if (marks.isEmpty()) results.remove(key); else results.put(key, marks);
        }

        long now = System.currentTimeMillis();
        long maxAge = (long) (soundTime.get() * 1000);
        synchronized (soundMarks) { soundMarks.removeIf(s -> now - s.time > maxAge); }
    }

    private List<Mark> scan(LevelChunk chunk) {
        List<Mark> marks = new ArrayList<>();
        ChunkPos cp = chunk.getPos();
        boolean overworldOrEnd = mc.level.dimension() != Level.NETHER;

        // ---- block entities (fast) ----
        if (storage.isOn()) {
            List<BlockPos> storageBlocks = new ArrayList<>();
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (be instanceof ShulkerBoxBlockEntity || be instanceof EnderChestBlockEntity) {
                    // never natural → always mark
                    marks.add(new Mark(be.getBlockPos(), Hint.STORAGE));
                } else if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity
                        || be instanceof HopperBlockEntity || be instanceof AbstractFurnaceBlockEntity) {
                    storageBlocks.add(be.getBlockPos());
                }
            }
            if (storageBlocks.size() >= storageMin.getInt()) {
                for (BlockPos p : storageBlocks) marks.add(new Mark(p, Hint.STORAGE));
                notify(cp, Hint.STORAGE, storageBlocks.size() + " Lager-Blöcke", storageBlocks.get(0));
            }
        }

        // signs are block entities – cheap to find
        if (playerBlocks.isOn()) {
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (be instanceof SignBlockEntity) {
                    marks.add(new Mark(be.getBlockPos(), Hint.PLAYER_BLOCK));
                    notify(cp, Hint.PLAYER_BLOCK, "Schild", be.getBlockPos());
                }
            }
        }

        // ---- block scan (only sections that contain something interesting) ----
        boolean wantAmethyst = amethyst.isOn(), wantKelp = kelp.isOn(), wantPlayer = playerBlocks.isOn(), wantPortal = portals.isOn() && overworldOrEnd;
        if (wantAmethyst || wantKelp || wantPlayer || wantPortal) {
            int buds = 0, clusters = 0, kelpTops = 0;
            List<BlockPos> clusterPos = new ArrayList<>();
            List<BlockPos> kelpPos = new ArrayList<>();
            LevelChunkSection[] sections = chunk.getSections();
            int bottom = chunk.getMinSectionY();
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
            for (int i = 0; i < sections.length; i++) {
                LevelChunkSection section = sections[i];
                if (section == null || section.hasOnlyAir()) continue;
                boolean any = section.maybeHas(s -> {
                    Block b = s.getBlock();
                    return (wantAmethyst && b instanceof AmethystClusterBlock)
                            || (wantKelp && b == Blocks.KELP)
                            || (wantPlayer && PLAYER_BLOCKS.contains(b))
                            || (wantPortal && b == Blocks.NETHER_PORTAL);
                });
                if (!any) continue;
                int baseY = (bottom + i) << 4;
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            BlockState state = section.getBlockState(x, y, z);
                            Block b = state.getBlock();
                            if (b == Blocks.AIR || b == Blocks.STONE || b == Blocks.DEEPSLATE || b == Blocks.WATER) continue;
                            mutable.set(cp.getMinBlockX() + x, baseY + y, cp.getMinBlockZ() + z);
                            if (wantAmethyst && b instanceof AmethystClusterBlock) {
                                if (b == Blocks.AMETHYST_CLUSTER) {
                                    clusters++;
                                    clusterPos.add(mutable.immutable());
                                } else {
                                    buds++;
                                }
                            } else if (wantKelp && b == Blocks.KELP) {
                                // top of a kelp column that reached the water surface
                                BlockState above = mc.level.getBlockState(mutable.above());
                                if (above.getFluidState().isEmpty()) {
                                    kelpTops++;
                                    kelpPos.add(mutable.immutable());
                                }
                            } else if (wantPlayer && PLAYER_BLOCKS.contains(b)) {
                                marks.add(new Mark(mutable.immutable(), Hint.PLAYER_BLOCK));
                                notify(cp, Hint.PLAYER_BLOCK, b.getName().getString(), mutable.immutable());
                            } else if (wantPortal && b == Blocks.NETHER_PORTAL) {
                                marks.add(new Mark(mutable.immutable(), Hint.PORTAL));
                                notify(cp, Hint.PORTAL, "Netherportal", mutable.immutable());
                            }
                        }
                    }
                }
            }

            int total = buds + clusters;
            if (wantAmethyst && total >= 6 && (double) clusters / total >= amethystRatio.get()) {
                for (BlockPos p : clusterPos) marks.add(new Mark(p, Hint.AMETHYST));
                notify(cp, Hint.AMETHYST, clusters + "/" + total + " Cluster ausgewachsen", clusterPos.isEmpty() ? cp.getWorldPosition() : clusterPos.get(0));
            }
            if (wantKelp && kelpTops >= kelpMin.getInt()) {
                for (BlockPos p : kelpPos) marks.add(new Mark(p, Hint.KELP));
                notify(cp, Hint.KELP, kelpTops + " Kelp-Spitzen an der Oberfläche", kelpPos.get(0));
            }
        }
        return marks;
    }

    private void notify(ChunkPos cp, Hint hint, String what, BlockPos pos) {
        if (!chat.isOn()) return;
        String key = hint.name() + ":" + cp.pack();
        if (!notified.add(key)) return;
        ChatUtil.info(Component.literal(hint.label + ": ").withStyle(s -> s.withColor(hint.color & 0xFFFFFF))
                .append(Component.literal(what + " ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("@ " + pos.getX() + " " + pos.getY() + " " + pos.getZ()).withStyle(ChatFormatting.GRAY)));
    }

    // ---------------------------------------------------------------------------------------------
    // Sounds (called from the network mixin on the render thread)
    // ---------------------------------------------------------------------------------------------

    public void onSound(Identifier id, SoundSource category, double x, double y, double z) {
        if (!sounds.isOn() || mc.player == null) return;
        if (category == SoundSource.MUSIC || category == SoundSource.AMBIENT || category == SoundSource.WEATHER
                || category == SoundSource.HOSTILE || category == SoundSource.NEUTRAL || category == SoundSource.VOICE) return;
        String path = id.getPath();
        boolean activity = false;
        for (String s : ACTIVITY_SOUNDS) {
            if (path.contains(s)) { activity = true; break; }
        }
        if (!activity && buildSounds.isOn() && path.startsWith("block.") && (path.endsWith(".place") || path.endsWith(".break"))) {
            activity = true;
        }
        if (!activity) return;
        addSound(new Vec3(x, y, z), path);
    }

    public void onWorldEvent(int eventId, BlockPos pos) {
        if (!sounds.isOn()) return;
        String name = switch (eventId) {
            case 1000, 1001, 1002 -> "Werfer/Spender";
            case 1010 -> "Plattenspieler";
            case 1029, 1030, 1031 -> "Amboss";
            case 1035 -> "Braustand";
            case 1042 -> "Schleifstein";
            case 1044 -> "Schmiedetisch";
            case 1043 -> "Lesepult";
            case 1049 -> "Werkbank (Crafter)";
            case 1032 -> "Portal";
            case 1004 -> "Feuerwerk";
            default -> null;
        };
        if (name != null) addSound(Vec3.atCenterOf(pos), name);
    }

    private void addSound(Vec3 pos, String name) {
        if (mc.player == null) return;
        // ignore our own noise
        if (mc.player.getEyePosition().distanceToSqr(pos) < 4 * 4) return;
        long now = System.currentTimeMillis();
        synchronized (soundMarks) {
            for (int i = 0; i < soundMarks.size(); i++) {
                SoundMark s = soundMarks.get(i);
                if (s.pos.distanceToSqr(pos) < 1.5 * 1.5) {
                    soundMarks.set(i, new SoundMark(s.pos, name, now));
                    return;
                }
            }
            soundMarks.add(new SoundMark(pos, name, now));
        }
        BlockPos bp = BlockPos.containing(pos);
        if (chat.isOn() && notified.add("SOUND:" + bp.asLong())) {
            ChatUtil.info(Component.literal(Hint.SOUND.label + ": ").withStyle(s -> s.withColor(Hint.SOUND.color & 0xFFFFFF))
                    .append(Component.literal(name + " ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("@ " + bp.getX() + " " + bp.getY() + " " + bp.getZ()).withStyle(ChatFormatting.GRAY)));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------------

    @Override
    public void onRender3D(LevelRenderContext context, Render3D render) {
        if (mc.level == null || mc.player == null) return;
        EnumMap<Hint, Boolean> on = new EnumMap<>(Hint.class);
        on.put(Hint.AMETHYST, amethyst.isOn());
        on.put(Hint.KELP, kelp.isOn());
        on.put(Hint.STORAGE, storage.isOn());
        on.put(Hint.PLAYER_BLOCK, playerBlocks.isOn());
        on.put(Hint.PORTAL, portals.isOn());

        for (List<Mark> list : results.values()) {
            for (Mark m : list) {
                if (!on.getOrDefault(m.hint, false)) continue;
                AABB box = new AABB(m.pos);
                render.box(box, m.hint.color);
                if (tracers.isOn()) render.tracer(box.getCenter(), m.hint.color & 0x80FFFFFF);
            }
        }

        if (sounds.isOn()) {
            long now = System.currentTimeMillis();
            double maxAge = soundTime.get() * 1000;
            synchronized (soundMarks) {
                for (SoundMark s : soundMarks) {
                    float fade = (float) Math.max(0.15, 1.0 - (now - s.time) / maxAge);
                    int alpha = (int) (fade * 255) << 24;
                    int color = (Hint.SOUND.color & 0xFFFFFF) | alpha;
                    render.box(new AABB(BlockPos.containing(s.pos)), color);
                    if (tracers.isOn()) render.tracer(s.pos, color);
                }
            }
        }

        if (entities.isOn()) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof Player) continue;
                boolean hit = e instanceof ItemFrame || e instanceof ArmorStand || e instanceof MinecartChest
                        || (e instanceof TamableAnimal t && t.isTame())
                        || (e.hasCustomName() && !(e instanceof ItemEntity));
                if (!hit) continue;
                render.box(e.getBoundingBox(), Hint.ENTITY.color);
                if (tracers.isOn()) render.tracer(e.getBoundingBox().getCenter(), Hint.ENTITY.color & 0x80FFFFFF);
            }
        }
    }
}
