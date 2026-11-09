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
    // --- targets
    private final BoolSetting players = add(new BoolSetting("Spieler", "Spieler angreifen", true));
    private final BoolSetting hostiles = add(new BoolSetting("Monster", "Feindliche Mobs angreifen", false));
    private final BoolSetting passives = add(new BoolSetting("Tiere", "Friedliche Mobs angreifen", false));
    private final NumberSetting targetRange = add(new NumberSetting("Ziel-Reichweite", "Wie weit Ziele entfernt sein dürfen", 10, 3, 16, 0.5));
    // --- place / break
    private final BoolSetting place = add(new BoolSetting("Platzieren", "Kristalle platzieren", true));
    private final BoolSetting breakOn = add(new BoolSetting("Zerstören", "Kristalle zerstören", true));
    private final NumberSetting placeRange = add(new NumberSetting("Platzier-Reichweite", "Reichweite zum Platzieren", 4.5, 1, 6, 0.1));
    private final NumberSetting breakRange = add(new NumberSetting("Zerstör-Reichweite", "Reichweite zum Zerstören", 4.5, 1, 6, 0.1));
    private final NumberSetting placeDelay = add(new NumberSetting("Platzier-Verzögerung", "Ticks zwischen zwei Platzierungen", 0, 0, 10, 1));
    private final NumberSetting breakDelay = add(new NumberSetting("Zerstör-Verzögerung", "Ticks zwischen zwei Schlägen", 0, 0, 10, 1));
    private final NumberSetting perTick = add(new NumberSetting("Pro Tick", "Wie viele Kristalle pro Tick platziert/zerstört werden", 1, 1, 3, 1));
    private final BoolSetting instant = add(new BoolSetting("Sofort zerstören", "Kristall-Spam: Kristall im selben Moment zerstören, in dem er erscheint", true));
    private final BoolSetting ownOnly = add(new BoolSetting("Nur eigene", "Nur Kristalle zerstören, die du selbst platziert hast", false));
    // --- damage
    private final NumberSetting minDamage = add(new NumberSetting("Min. Schaden", "Mindestschaden am Ziel (Herzen ×2)", 6, 0, 36, 0.5));
    private final NumberSetting maxSelf = add(new NumberSetting("Max. Eigenschaden", "Höchstens so viel Schaden an dir selbst", 8, 0, 36, 0.5));
    private final BoolSetting antiSuicide = add(new BoolSetting("Anti-Selbstmord", "Nie einen Kristall, der dich töten würde", true));
    private final NumberSetting facePlace = add(new NumberSetting("Face-Place", "Unter diesem Leben des Ziels reicht 2 Schaden (0 = aus)", 8, 0, 36, 1));
    // --- handling
    private final ModeSetting switchMode = add(new ModeSetting("Wechseln", "Keins = nur wenn du Kristalle hältst, Normal = auf Kristalle wechseln, Zurück = danach zurück", "Normal", "Keins", "Normal", "Zurück"));
    private final ModeSetting rotate = add(new ModeSetting("Rotation", "Silent = Server sieht dich zum Kristall schauen, Keine = gar nicht drehen", "Silent", "Silent", "Keine"));
    private final BoolSetting throughWalls = add(new BoolSetting("Durch Wände", "Auch ohne Sichtlinie platzieren/zerstören", false));
    private final BoolSetting pauseOnEat = add(new BoolSetting("Pause beim Essen", "Nicht während du isst / trinkst", true));
    private final BoolSetting render = add(new BoolSetting("Anzeigen", "Platzier-Position als Box anzeigen", true));

    private int placeWait, breakWait;
    private LivingEntity target;
    private BlockPos plannedPlace;
    private EndCrystal plannedBreak;
    private float plannedDamage;
    private BlockPos renderPos;
    private long renderUntil;
    private final Set<BlockPos> placedByUs = new HashSet<>();

    public AutoCrystal() {
        super("AutoCrystal", "Platziert und zerstört End-Kristalle automatisch am besten Punkt beim Ziel – mit Schadens- und Selbstschutz-Berechnung.",
                Category.CRYSTAL, Items.END_CRYSTAL);
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
        plannedPlace = null;
        plannedBreak = null;
        target = null;
        if (paused()) return;
        List<LivingEntity> list = TargetUtil.find(new TargetUtil.Filter(players.isOn(), hostiles.isOn(), passives.isOn(), true, false),
                targetRange.get(), 360f, true, "Distanz");
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
        return !switchMode.is("Keins") && InvUtil.hotbar(Items.END_CRYSTAL) >= 0;
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

    @Override
    public void onTick() {
        if (placeWait > 0) placeWait--;
        if (breakWait > 0) breakWait--;
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
            if (switchMode.is("Keins")) return false;
            int slot = InvUtil.hotbar(Items.END_CRYSTAL);
            if (slot < 0) return false;
            InvUtil.select(slot);
            hand = InteractionHand.MAIN_HAND;
        }
        boolean ok = WorldUtil.interact(base, Direction.UP, hand);
        if (ok) placedByUs.add(base.immutable());
        if (placedByUs.size() > 64) placedByUs.clear();
        if (switchMode.is("Zurück") && hand == InteractionHand.MAIN_HAND) InvUtil.select(prev);
        return ok;
    }

    /** Crystal spam: called as soon as a crystal entity appears on the client. */
    public void onCrystalSpawn(EndCrystal crystal) {
        if (!isEnabled() || !instant.isOn() || !breakOn.isOn() || paused() || target == null || mc.gui.screen() != null) return;
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
        r.box(new AABB(renderPos), 0xFFFF5A2A);
    }

    @Override
    public String getInfo() {
        return target == null ? null : target.getName().getString() + (plannedDamage > 0 ? String.format(java.util.Locale.ROOT, " %.1f", plannedDamage) : "");
    }
}
