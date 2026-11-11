package dev.nautilus.module.smp;

import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.NumberSetting;
import dev.nautilus.hud.Notifications;
import dev.nautilus.util.Render3D;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.core.BlockPos;
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
        AMETHYST("Amethyst growth", 0xFFC77DFF),
        KELP("Grown kelp", 0xFF4CD964),
        STORAGE("Storage", 0xFFFFB347),
        PLAYER_BLOCK("Player block", 0xFF5AC8FA),
        PORTAL("Active portal", 0xFFB45AFF),
        SOUND("Sound", 0xFFFF5A5A),
        MOB_SOUND("Mob / step sound", 0xFFFF9F43),
        ENTITY("Object", 0xFFFFE066);

        public final String label;
        public final int color;

        Hint(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    private record Mark(BlockPos pos, Hint hint) {}
    private record SoundMark(BlockPos pos, String name, Hint kind, long time) {}

    // --- sub modules (each can be turned on / off) --------------------------------------------
    private final BoolSetting amethyst = add(new BoolSetting("Amethyst-Grower",
            "Geodes with many fully grown amethyst clusters = the chunk was loaded for a long time (players nearby)", true));
    private final NumberSetting amethystRatio = add(new NumberSetting("Cluster share",
            "From which share of grown clusters a chunk is marked", 0.6, 0.2, 1.0, 0.05).visibleWhen(amethyst::isOn));
    private final BoolSetting sounds = add(new BoolSetting("Sound blocks",
            "Marks blocks where the server plays sounds (pistons, doors, chests, furnaces, anvils …)", true));
    private final BoolSetting buildSounds = add(new BoolSetting("Building sounds",
            "Also mark break/place sounds of other players", true).visibleWhen(sounds::isOn));
    private final BoolSetting redstoneSounds = add(new BoolSetting("Redstone sounds",
            "Comparators, torches, tripwire, sculk, copper bulbs, dispensers, pistons …", true).visibleWhen(sounds::isOn));
    private final BoolSetting mobSounds = add(new BoolSetting("Mob sounds",
            "Sounds of villagers & other mobs: the block they stand on is marked", true).visibleWhen(sounds::isOn));
    private final BoolSetting stepSounds = add(new BoolSetting("Footsteps",
            "Step sounds of players and mobs: marks the block below them", true).visibleWhen(sounds::isOn));
    private final BoolSetting allSounds = add(new BoolSetting("All sounds",
            "Mark every sound from the server (except music, weather, ambience)", false).visibleWhen(sounds::isOn));
    private final NumberSetting soundTime = add(new NumberSetting("Sound duration",
            "How long sound marks stay visible (seconds)", 60, 5, 600, 5).visibleWhen(sounds::isOn));
    private final BoolSetting storage = add(new BoolSetting("Storage",
            "Chunks with many chests/barrels/furnaces/hoppers, plus every shulker box and ender chest", true));
    private final NumberSetting storageMin = add(new NumberSetting("Min. storage",
            "From how many storage blocks a chunk is marked", 4, 1, 30, 1).visibleWhen(storage::isOn));
    private final BoolSetting playerBlocks = add(new BoolSetting("Player blocks",
            "Blocks that almost never generate naturally (beacons, enchanting tables, anvils, signs, observers …)", true));
    private final BoolSetting portals = add(new BoolSetting("Portals",
            "Activated nether portals in the overworld / the End", true));
    private final BoolSetting kelp = add(new BoolSetting("Kelp growth",
            "Kelp grown up to the water surface = the chunk was loaded for a long time", false));
    private final NumberSetting kelpMin = add(new NumberSetting("Min. kelp",
            "From how many fully grown kelp tops a chunk is marked", 12, 3, 60, 1).visibleWhen(kelp::isOn));
    private final BoolSetting entities = add(new BoolSetting("Objects",
            "Item frames, armor stands, named & tamed animals, chest minecarts", true));
    private final BoolSetting chat = add(new BoolSetting("Notifications", "Report finds as HUD notifications", true));
    private final BoolSetting tracers = add(new BoolSetting("Tracer", "Draw lines from you to the finds", false));
    private final NumberSetting scanSpeed = add(new NumberSetting("Scan speed", "Chunks per tick (higher = faster, more load)", 2, 1, 10, 1));

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

    /** Sounds of redstone contraptions. */
    private static final String[] REDSTONE_SOUNDS = {
            "redstone", "comparator", "repeater", "tripwire", "sculk", "copper_bulb", "dispenser", "dropper", "piston",
            "lever", "button", "pressure_plate", "note_block", "crafter", "bell", "trial_spawner", "hopper", "rail",
            "daylight_detector", "observer", "target", "lightning_rod", "tnt"};

    /** Maximum number of sound marks kept at the same time (oldest are dropped). */
    private static final int MAX_SOUND_MARKS = 600;

    public BaseFinder() {
        super("BaseFinder", "Looks for signs of bases: amethyst growth, sound blocks, storage, player blocks and more.",
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
                notify(cp, Hint.STORAGE, storageBlocks.size() + " storage blocks", storageBlocks.get(0));
            }
        }

        // signs are block entities – cheap to find
        if (playerBlocks.isOn()) {
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (be instanceof SignBlockEntity) {
                    marks.add(new Mark(be.getBlockPos(), Hint.PLAYER_BLOCK));
                    notify(cp, Hint.PLAYER_BLOCK, "Sign", be.getBlockPos());
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
                                notify(cp, Hint.PORTAL, "Nether portal", mutable.immutable());
                            }
                        }
                    }
                }
            }

            int total = buds + clusters;
            if (wantAmethyst && total >= 6 && (double) clusters / total >= amethystRatio.get()) {
                for (BlockPos p : clusterPos) marks.add(new Mark(p, Hint.AMETHYST));
                notify(cp, Hint.AMETHYST, clusters + "/" + total + " clusters grown", clusterPos.isEmpty() ? cp.getWorldPosition() : clusterPos.get(0));
            }
            if (wantKelp && kelpTops >= kelpMin.getInt()) {
                for (BlockPos p : kelpPos) marks.add(new Mark(p, Hint.KELP));
                notify(cp, Hint.KELP, kelpTops + " kelp tops at the surface", kelpPos.get(0));
            }
        }
        return marks;
    }

    private void notify(ChunkPos cp, Hint hint, String what, BlockPos pos) {
        if (!chat.isOn()) return;
        String key = hint.name() + ":" + cp.pack();
        if (!notified.add(key)) return;
        Notifications.push(hint.label, what + " @ " + pos.getX() + " " + pos.getY() + " " + pos.getZ(), hint.color, 4f);
    }

    // ---------------------------------------------------------------------------------------------
    // Sounds (called from the network mixin on the render thread)
    // ---------------------------------------------------------------------------------------------

    /** A sound played at a position (ClientboundSoundPacket). */
    public void onSound(Identifier id, SoundSource category, double x, double y, double z) {
        handleSound(id, category, new Vec3(x, y, z), null);
    }

    /** A sound attached to an entity (ClientboundSoundEntityPacket): villager voices, mob steps … */
    public void onEntitySound(Identifier id, SoundSource category, int entityId) {
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(entityId);
        if (e == null || e == mc.player) return;
        handleSound(id, category, e.position(), e);
    }

    private void handleSound(Identifier id, SoundSource category, Vec3 pos, Entity source) {
        if (!sounds.isOn() || mc.player == null || mc.level == null) return;
        if (category == SoundSource.MUSIC || category == SoundSource.AMBIENT || category == SoundSource.WEATHER) return;
        String path = id.getPath();

        boolean step = path.endsWith(".step") || path.contains(".step.") || path.endsWith("_step");
        boolean mob = source != null && !(source instanceof Player)
                || category == SoundSource.NEUTRAL || category == SoundSource.HOSTILE || path.startsWith("entity.");
        boolean activity = contains(path, ACTIVITY_SOUNDS)
                || redstoneSounds.isOn() && contains(path, REDSTONE_SOUNDS)
                || buildSounds.isOn() && path.startsWith("block.") && (path.endsWith(".place") || path.endsWith(".break") || path.endsWith(".hit"));

        Hint kind;
        if (activity && !step) kind = Hint.SOUND;
        else if (step && stepSounds.isOn()) kind = Hint.MOB_SOUND;
        else if (mob && mobSounds.isOn()) kind = Hint.MOB_SOUND;
        else if (allSounds.isOn()) kind = step || mob ? Hint.MOB_SOUND : Hint.SOUND;
        else return;

        // steps and mob noises come from the feet of the entity: mark the block it stands on
        BlockPos block = kind == Hint.MOB_SOUND ? groundBelow(pos) : soundBlock(pos);
        addSound(block, pretty(path, source), kind);
    }

    private static boolean contains(String path, String[] keys) {
        for (String k : keys) if (path.contains(k)) return true;
        return false;
    }

    /** Block a block sound comes from: the block at the position, or the solid block right below. */
    private BlockPos soundBlock(Vec3 pos) {
        BlockPos bp = BlockPos.containing(pos);
        if (mc.level.getBlockState(bp).isAir() && !mc.level.getBlockState(bp.below()).isAir()) return bp.below();
        return bp;
    }

    /** The block an entity at {@code pos} (feet) stands on – searches up to 2 blocks down. */
    private BlockPos groundBelow(Vec3 pos) {
        BlockPos bp = BlockPos.containing(pos.x, pos.y - 0.2, pos.z);
        for (int i = 0; i < 3; i++) {
            if (!mc.level.getBlockState(bp).isAir()) return bp;
            bp = bp.below();
        }
        return BlockPos.containing(pos);
    }

    private static String pretty(String path, Entity source) {
        String who = source != null ? source.getName().getString() + ": " : "";
        return who + path;
    }

    public void onWorldEvent(int eventId, BlockPos pos) {
        if (!sounds.isOn()) return;
        String name = switch (eventId) {
            case 1000, 1001, 1002 -> "Dispenser/dropper";
            case 1010 -> "Jukebox";
            case 1029, 1030, 1031 -> "Anvil";
            case 1035 -> "Brewing stand";
            case 1042 -> "Grindstone";
            case 1044 -> "Smithing table";
            case 1043 -> "Lectern";
            case 1049 -> "Crafter";
            case 1032 -> "Portal";
            case 1004 -> "Firework";
            default -> null;
        };
        if (name != null) addSound(pos.immutable(), name, Hint.SOUND);
    }

    private void addSound(BlockPos pos, String name, Hint kind) {
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        synchronized (soundMarks) {
            for (int i = 0; i < soundMarks.size(); i++) {
                SoundMark s = soundMarks.get(i);
                if (s.pos.equals(pos)) {
                    // a block sound wins over a step / mob sound on the same block
                    Hint k = s.kind == Hint.SOUND ? Hint.SOUND : kind;
                    soundMarks.set(i, new SoundMark(pos, k == kind ? name : s.name, k, now));
                    return;
                }
            }
            if (soundMarks.size() >= MAX_SOUND_MARKS) soundMarks.remove(0);
            soundMarks.add(new SoundMark(pos, name, kind, now));
        }
        // toasts only for real block activity (a walking villager would spam one per block)
        if (kind == Hint.SOUND && chat.isOn() && notified.add("SOUND:" + pos.asLong())) {
            Notifications.push(Hint.SOUND.label, name + " @ " + pos.getX() + " " + pos.getY() + " " + pos.getZ(), Hint.SOUND.color, 4f);
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
                    int color = (s.kind.color & 0xFFFFFF) | alpha;
                    AABB box = new AABB(s.pos);
                    render.box(box, color);
                    // inner box makes the mark easier to spot through many blocks
                    render.box(box.deflate(0.2), color);
                    if (tracers.isOn()) render.tracer(box.getCenter(), color);
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
