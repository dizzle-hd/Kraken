package dev.nautilus.module.crystal;

import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.ModeSetting;
import dev.nautilus.setting.NumberSetting;
import dev.nautilus.util.AttackUtil;
import dev.nautilus.util.DamageUtil;
import dev.nautilus.util.InvUtil;
import dev.nautilus.util.Render3D;
import dev.nautilus.util.RotationManager;
import dev.nautilus.util.RotationUtil;
import dev.nautilus.util.TargetUtil;
import dev.nautilus.util.WorldUtil;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Auto crystal: places end crystals on obsidian/bedrock where they hurt the target most and breaks
 * them – also the instant they spawn ("crystal spam"). Damage is estimated like vanilla (distance,
 * exposure, armor, protection, resistance); self damage and suicide protection are checked for
 * every crystal.
 */
public class AutoCrystal extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Crosshair = as soon as you hold a crystal and look at obsidian/bedrock it crystals instantly; Target = finds enemies and the best spot itself", "Crosshair", "Crosshair", "Target"));
    // --- targets
    private final BoolSetting players = add(new BoolSetting("Players", "Attack players", true).visibleWhen(this::targetMode));
    private final BoolSetting hostiles = add(new BoolSetting("Monsters", "Attack hostile mobs", false).visibleWhen(this::targetMode));
    private final BoolSetting passives = add(new BoolSetting("Animals", "Attack passive mobs", false).visibleWhen(this::targetMode));
    private final NumberSetting targetRange = add(new NumberSetting("Target range", "How far away targets may be", 10, 3, 16, 0.5).visibleWhen(this::targetMode));
    // --- place / break
    private final BoolSetting place = add(new BoolSetting("Place", "Place crystals", true));
    private final BoolSetting breakOn = add(new BoolSetting("Break", "Break crystals", true));
    private final NumberSetting placeRange = add(new NumberSetting("Place range", "Range for placing", 4.5, 1, 6, 0.1));
    private final NumberSetting breakRange = add(new NumberSetting("Break range", "Range for breaking", 4.5, 1, 6, 0.1));
    private final NumberSetting placeDelay = add(new NumberSetting("Place delay", "Ticks between two placements", 0, 0, 10, 1));
    private final NumberSetting breakDelay = add(new NumberSetting("Break delay", "Ticks between two hits", 0, 0, 10, 1));
    private final NumberSetting perTick = add(new NumberSetting("Per tick", "How many crystals are placed/broken per tick", 1, 1, 3, 1));
    private final BoolSetting instant = add(new BoolSetting("Instant break", "Crystal spam: break a crystal the moment it appears", true));
    private final BoolSetting confirm = add(new BoolSetting("Wait for confirmation", "On = only place the next crystal after the server removed the old one (safer). Off = place again instantly – faster, but higher flag risk", true));
    private final BoolSetting ownOnly = add(new BoolSetting("Only own", "Only break crystals you placed yourself", false));
    // --- damage
    private final NumberSetting minDamage = add(new NumberSetting("Min. damage", "Minimum damage to the target (hearts ×2)", 6, 0, 36, 0.5).visibleWhen(this::targetMode));
    private final NumberSetting maxSelf = add(new NumberSetting("Max. self damage", "At most this much damage to yourself (Target mode only)", 8, 0, 36, 0.5).visibleWhen(this::targetMode));
    private final BoolSetting antiSuicide = add(new BoolSetting("Anti-suicide", "Never a crystal that would kill you", true));
    private final NumberSetting facePlace = add(new NumberSetting("Face-Place", "Below this target health 2 damage is enough (0 = off)", 8, 0, 36, 1).visibleWhen(this::targetMode));
    // --- handling
    private final ModeSetting switchMode = add(new ModeSetting("Switch", "None = only while you hold crystals, Normal = switch to crystals, Back = switch back afterwards", "Normal", "None", "Normal", "Back"));
    private final ModeSetting rotate = add(new ModeSetting("Rotation", "Silent = the server sees you look at the crystal, Off = don't turn at all", "Silent", "Silent", "Off").visibleWhen(this::targetMode));
    private final BoolSetting throughWalls = add(new BoolSetting("Through walls", "Also place/break without line of sight", false));
    private final BoolSetting pauseOnEat = add(new BoolSetting("Pause while eating", "Not while you eat / drink", true));
    private final BoolSetting render = add(new BoolSetting("Render", "Show the place position as a box", true));

    private int placeWait, breakWait;
    private LivingEntity target;
    private BlockPos plannedPlace;
    private EndCrystal plannedBreak;
    private float plannedDamage;
    private BlockPos renderPos;
    private long renderUntil;
    private final Set<BlockPos> placedByUs = new HashSet<>();
    /** Crosshair mode: the obsidian/bedrock block that is being crystalled. */
    private BlockPos lookBase;

    private boolean targetMode() {
        return mode.is("Target");
    }

    private boolean crosshairMode() {
        return mode.is("Crosshair");
    }

    public AutoCrystal() {
        super("AutoCrystal", "Hold a crystal + look at obsidian = instant crystalling. Target mode: finds enemies and the best spot itself.",
                Category.CRYSTAL, Items.END_CRYSTAL);
        preset("Legit", LOW, "Crosshair, small pauses, no instant break",
                "Mode", "Crosshair", "Place delay", 2, "Break delay", 2, "Per tick", 1, "Instant break", false, "Wait for confirmation", true);
        preset("Normal", MEDIUM, "Crosshair, crystal spam with a short pause",
                "Mode", "Crosshair", "Place delay", 1, "Break delay", 0, "Per tick", 1, "Instant break", true, "Wait for confirmation", true);
        preset("Rage", HIGH, "Finds targets itself, no pauses, several per tick, through walls",
                "Mode", "Target", "Place delay", 0, "Break delay", 0, "Per tick", 2, "Instant break", true, "Through walls", true, "Wait for confirmation", false);
    }

    @Override
    protected void onDisable() {
        target = null;
        plannedPlace = null;
        plannedBreak = null;
        placedByUs.clear();
    }

    private boolean paused() {
        return mc.player == null || mc.level == null || mc.gameMode == null || mc.player.isSpectator()
                || pauseOnEat.isOn() && mc.player.isUsingItem();
    }

    // =============================================================================================
    // Planning (pre-tick: rotations go out with this tick's movement packet)
    // =============================================================================================

    @Override
    public void onPreTick() {
        act();
        plan();
    }

    private void plan() {
        plannedPlace = null;
        plannedBreak = null;
        target = null;
        if (paused() || crosshairMode()) return;
        List<LivingEntity> list = TargetUtil.find(new TargetUtil.Filter(players.isOn(), hostiles.isOn(), passives.isOn(), true, false),
                targetRange.get(), 360f, true, "Distance");
        if (list.isEmpty()) return;
        target = list.get(0);

        if (breakOn.isOn()) plannedBreak = bestCrystal();
        if (place.isOn() && hasCrystals()) planPlace();

        if (rotate.is("Silent")) {
            Vec3 look = plannedBreak != null ? plannedBreak.getBoundingBox().getCenter()
                    : plannedPlace != null ? Vec3.atCenterOf(plannedPlace).add(0, 0.5, 0) : null;
            if (look != null) {
                float[] r = RotationUtil.rotationsTo(mc.player.getEyePosition(), look);
                RotationManager.request(r[0], r[1], 30);
            }
        }
    }

    private boolean hasCrystals() {
        if (mc.player.getOffhandItem().is(Items.END_CRYSTAL) || mc.player.getMainHandItem().is(Items.END_CRYSTAL)) return true;
        return !switchMode.is("None") && InvUtil.hotbar(Items.END_CRYSTAL) >= 0;
    }

    private float needed() {
        float hp = target.getHealth() + target.getAbsorptionAmount();
        return facePlace.get() > 0 && hp <= facePlace.get() ? 2f : minDamage.getFloat();
    }

    private boolean selfOk(float self) {
        if (self > maxSelf.get()) return false;
        return !antiSuicide.isOn() || self < mc.player.getHealth() + mc.player.getAbsorptionAmount() - 1f;
    }

    private EndCrystal bestCrystal() {
        Vec3 eyes = mc.player.getEyePosition();
        double r = breakRange.get();
        EndCrystal best = null;
        float bestDmg = 0;
        for (Entity e : mc.level.getEntities((Entity) null, mc.player.getBoundingBox().inflate(r + 1), x -> x instanceof EndCrystal && x.isAlive())) {
            EndCrystal c = (EndCrystal) e;
            if (eyes.distanceTo(RotationUtil.closestPoint(eyes, c)) > r) continue;
            if (ownOnly.isOn() && !placedByUs.contains(c.blockPosition().below())) continue;
            if (!throughWalls.isOn() && !TargetUtil.canSee(eyes, c.getBoundingBox().getCenter())) continue;
            float dmg = DamageUtil.explosion(target, c.position(), DamageUtil.CRYSTAL, null, null);
            float self = DamageUtil.explosion(mc.player, c.position(), DamageUtil.CRYSTAL, null, null);
            if (dmg < needed() || !selfOk(self)) continue;
            if (dmg > bestDmg) {
                bestDmg = dmg;
                best = c;
            }
        }
        return best;
    }

    private void planPlace() {
        Vec3 eyes = mc.player.getEyePosition();
        double r = placeRange.get();
        int ir = (int) Math.ceil(r);
        BlockPos center = mc.player.blockPosition();
        BlockPos best = null;
        float bestDmg = 0;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-ir, -ir, -ir), center.offset(ir, ir, ir))) {
            if (WorldUtil.reach(p) > r) continue;
            if (!WorldUtil.canPlaceCrystal(p)) continue;
            Vec3 boom = new Vec3(p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5);
            if (target.distanceToSqr(boom) > 12 * 12) continue;
            if (!throughWalls.isOn() && !TargetUtil.canSee(eyes, Vec3.atCenterOf(p).add(0, 0.5, 0))) continue;
            float dmg = DamageUtil.explosion(target, boom, DamageUtil.CRYSTAL, null, null);
            if (dmg < needed() || dmg <= bestDmg) continue;
            float self = DamageUtil.explosion(mc.player, boom, DamageUtil.CRYSTAL, null, null);
            if (!selfOk(self)) continue;
            bestDmg = dmg;
            best = p.immutable();
        }
        plannedPlace = best;
        plannedDamage = bestDmg;
        if (best != null) {
            renderPos = best;
            renderUntil = System.currentTimeMillis() + 500;
        }
    }

    // =============================================================================================
    // Acting (after the movement packet with the rotation was sent)
    // =============================================================================================

    /** Attacks of this tick, sent before the movement packet (pre), aimed with the rotation sent last tick. */
    private void act() {
        if (placeWait > 0) placeWait--;
        if (breakWait > 0) breakWait--;
        if (crosshairMode() || target == null) {
            // crosshair mode – and target mode while nobody is around: crystal where you look
            crosshairTick();
            return;
        }
        if (paused() || target == null || mc.gui.screen() != null) return;

        for (int i = 0; i < perTick.getInt(); i++) {
            boolean did = false;
            if (plannedBreak != null && breakWait <= 0 && plannedBreak.isAlive()) {
                AttackUtil.attack(plannedBreak);
                breakWait = breakDelay.getInt();
                plannedBreak = null;
                did = true;
            }
            if (plannedPlace != null && placeWait <= 0 && WorldUtil.canPlaceCrystal(plannedPlace)) {
                if (placeCrystal(plannedPlace)) {
                    placeWait = placeDelay.getInt();
                    did = true;
                }
                plannedPlace = i + 1 < perTick.getInt() ? nextPlace() : null;
            }
            if (!did) break;
        }
    }

    // =============================================================================================
    // Crosshair mode: hold a crystal, look at obsidian/bedrock -> place, break the moment it
    // appears, place again ... (no target search)
    // =============================================================================================

    private InteractionHand crystalHand() {
        if (mc.player.getMainHandItem().is(Items.END_CRYSTAL)) return InteractionHand.MAIN_HAND;
        if (mc.player.getOffhandItem().is(Items.END_CRYSTAL)) return InteractionHand.OFF_HAND;
        return null;
    }

    /** Would this explosion kill us? (anti suicide is the only self check in crosshair mode) */
    private boolean suicide(Vec3 boom) {
        if (!antiSuicide.isOn()) return false;
        float self = DamageUtil.explosion(mc.player, boom, DamageUtil.CRYSTAL, null, null);
        return self >= mc.player.getHealth() + mc.player.getAbsorptionAmount() - 1f;
    }

    private void crosshairTick() {
        lookBase = null;
        if (paused() || mc.gui.screen() != null) return;
        InteractionHand hand = crystalHand();
        if (hand == null) return;
        var hr = mc.hitResult;
        if (hr == null) return;

        for (int i = 0; i < perTick.getInt(); i++) {
            // looking at a crystal: break it
            if (hr instanceof net.minecraft.world.phys.EntityHitResult eh && eh.getEntity() instanceof EndCrystal c && c.isAlive()) {
                lookBase = c.blockPosition().below();
                if (breakOn.isOn() && breakWait <= 0 && !suicide(c.position())) breakAndRefill(c, lookBase, hand);
                return;
            }
            if (!(hr instanceof net.minecraft.world.phys.BlockHitResult bh) || bh.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return;
            BlockPos base = bh.getBlockPos();
            var state = mc.level.getBlockState(base);
            if (!state.is(net.minecraft.world.level.block.Blocks.OBSIDIAN) && !state.is(net.minecraft.world.level.block.Blocks.BEDROCK)) return;
            lookBase = base;
            renderPos = base;
            renderUntil = System.currentTimeMillis() + 300;
            // a crystal is already standing there: break it first
            EndCrystal existing = crystalOn(base);
            if (existing != null) {
                if (breakOn.isOn() && breakWait <= 0 && !suicide(existing.position())) breakAndRefill(existing, base, hand);
                return;
            }
            if (!place.isOn() || placeWait > 0 || !WorldUtil.canPlaceCrystal(base)) return;
            if (suicide(new Vec3(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5))) return;
            var r = mc.gameMode.useItemOn(mc.player, hand, bh);
            if (r.consumesAction()) {
                mc.player.swing(hand, mc.player.getItemInHand(hand).getInteractAnimation(), false);
                placedByUs.add(base.immutable());
                if (placedByUs.size() > 64) placedByUs.clear();
                placeWait = placeDelay.getInt();
            }
            if (placeWait > 0) return;
        }
    }

    /**
     * Breaks the crystal and – without waiting for the server to remove it – places the next one on
     * the same block right away (the attack and the new placement go out together).
     */
    private void breakAndRefill(EndCrystal c, BlockPos base, InteractionHand hand) {
        AttackUtil.attack(c);
        breakWait = breakDelay.getInt();
        // default: wait for the server to remove the crystal (onCrystalRemoved places the next one)
        if (confirm.isOn() || !place.isOn() || hand == null) return;
        // not waiting for the server: forget it on the client right away and place the next one immediately
        mc.level.removeEntity(c.getId(), Entity.RemovalReason.KILLED);
        placeOn(base, hand);
    }

    /** Places a crystal on top of {@code base} with the given hand (if allowed and not suicidal). */
    private void placeOn(BlockPos base, InteractionHand hand) {
        placeOn(base, hand, null);
    }

    private void placeOn(BlockPos base, InteractionHand hand, Entity leaving) {
        if (!WorldUtil.canPlaceCrystal(base, leaving) || suicide(new Vec3(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5))) return;
        Vec3 hit = new Vec3(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
        var r = mc.gameMode.useItemOn(mc.player, hand, new net.minecraft.world.phys.BlockHitResult(hit, Direction.UP, base, false));
        if (r.consumesAction()) {
            mc.player.swing(hand, mc.player.getItemInHand(hand).getInteractAnimation(), false);
            placedByUs.add(base.immutable());
            placeWait = placeDelay.getInt();
        }
    }

    private EndCrystal crystalOn(BlockPos base) {
        AABB box = new AABB(base.above());
        for (Entity e : mc.level.getEntities((Entity) null, box.inflate(0.1), x -> x instanceof EndCrystal && x.isAlive())) return (EndCrystal) e;
        return null;
    }

    private BlockPos nextPlace() {
        planPlace();
        return plannedPlace;
    }

    private boolean placeCrystal(BlockPos base) {
        InteractionHand hand;
        int prev = InvUtil.selected();
        if (mc.player.getOffhandItem().is(Items.END_CRYSTAL)) hand = InteractionHand.OFF_HAND;
        else if (mc.player.getMainHandItem().is(Items.END_CRYSTAL)) hand = InteractionHand.MAIN_HAND;
        else {
            if (switchMode.is("None")) return false;
            int slot = InvUtil.hotbar(Items.END_CRYSTAL);
            if (slot < 0) return false;
            InvUtil.select(slot);
            hand = InteractionHand.MAIN_HAND;
        }
        boolean ok = WorldUtil.interact(base, Direction.UP, hand);
        if (ok) placedByUs.add(base.immutable());
        if (placedByUs.size() > 64) placedByUs.clear();
        if (switchMode.is("Back") && hand == InteractionHand.MAIN_HAND) InvUtil.select(prev);
        return ok;
    }

    /** Crystal spam: called as soon as a crystal entity appears on the client. */
    /**
     * The server confirmed that a crystal is gone: if it stood on the block you are crystalling,
     * place the next one right now (no guessing ahead of the server).
     */
    public void onCrystalRemoved(EndCrystal crystal) {
        if (!isEnabled() || !place.isOn() || paused() || mc.gui.screen() != null || lookBase == null) return;
        if (!(crosshairMode() || target == null)) return;
        if (!crystal.blockPosition().below().equals(lookBase)) return;
        InteractionHand hand = crystalHand();
        if (hand == null || placeWait > 0) return;
        placeOn(lookBase, hand, crystal); // the removed crystal is still in the world for this moment
    }

    public void onCrystalSpawn(EndCrystal crystal) {
        if (!isEnabled() || !instant.isOn() || !breakOn.isOn() || paused() || mc.gui.screen() != null) return;
        if (crosshairMode() || target == null) {
            // only the crystal on the block you are crystalling
            if (lookBase == null || !crystal.blockPosition().below().equals(lookBase) || crystalHand() == null) return;
            Vec3 eyes = mc.player.getEyePosition();
            if (eyes.distanceTo(RotationUtil.closestPoint(eyes, crystal)) > breakRange.get() || suicide(crystal.position())) return;
            placeWait = 0;
            breakAndRefill(crystal, lookBase, crystalHand());
            return;
        }
        if (target == null) return;
        if (!placedByUs.contains(crystal.blockPosition().below()) && ownOnly.isOn()) return;
        Vec3 eyes = mc.player.getEyePosition();
        if (eyes.distanceTo(RotationUtil.closestPoint(eyes, crystal)) > breakRange.get()) return;
        float dmg = DamageUtil.explosion(target, crystal.position(), DamageUtil.CRYSTAL, null, null);
        float self = DamageUtil.explosion(mc.player, crystal.position(), DamageUtil.CRYSTAL, null, null);
        if (dmg < needed() || !selfOk(self)) return;
        AttackUtil.attack(crystal);
        breakWait = breakDelay.getInt();
    }

    @Override
    public void onRender3D(LevelRenderContext context, Render3D r) {
        if (!render.isOn() || renderPos == null || System.currentTimeMillis() > renderUntil) return;
        r.box(new AABB(renderPos), dev.nautilus.hud.HudStyle.accent(0xFF));
    }

    @Override
    public String getInfo() {
        if (crosshairMode()) return "Crosshair";
        return target == null ? null : target.getName().getString() + (plannedDamage > 0 ? String.format(java.util.Locale.ROOT, " %.1f", plannedDamage) : "");
    }
}
