package dev.nautilus.module.mace;

import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.module.ModuleManager;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.ModeSetting;
import dev.nautilus.setting.NumberSetting;
import dev.nautilus.util.AttackUtil;
import dev.nautilus.util.InvUtil;
import dev.nautilus.util.RotationManager;
import dev.nautilus.util.RotationUtil;
import dev.nautilus.util.TargetUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Hits for you while you fall onto a target: one smash per fall. With timing "Late" it waits for the
 * last tick in reach (or before you land), because the smash damage grows with every block of the fall.
 * The mace comes from the hand or – with MaceSwap on – from the hotbar.
 */
public class AutoSmash extends Module {
    private final BoolSetting players = add(new BoolSetting("Players", "Hit players", true));
    private final BoolSetting hostiles = add(new BoolSetting("Monsters", "Hit hostile mobs", true));
    private final BoolSetting passives = add(new BoolSetting("Animals", "Hit passive mobs", false));
    private final NumberSetting minFall = add(new NumberSetting("Min. fall height", "Only hit from this fall height on (blocks)", 3, 1.5, 30, 0.5));
    private final NumberSetting range = add(new NumberSetting("Range", "Maximum range in blocks", 3.0, 2.0, 4.5, 0.1));
    private final ModeSetting timing = add(new ModeSetting("Timing", "Late = in the last tick before impact / before the target leaves range (more damage), Instant = as soon as in range", "Late", "Late", "Instant"));
    private final ModeSetting aim = add(new ModeSetting("Aiming", "Crosshair = only what you aim at, Silent = nearest target, the rotation only goes to the server", "Crosshair", "Crosshair", "Silent"));
    private final BoolSetting needMace = add(new BoolSetting("Only with mace", "Only hit when a mace is in hand (or MaceSwap has one)", true));

    private boolean hitThisFall;
    private double lastFall;

    public AutoSmash() {
        super("AutoSmash", "Hits automatically with the mace while falling – at the last moment for maximum smash damage.",
                Category.MACE, Items.HEAVY_CORE);
        preset("Legit", LOW, "Only in the crosshair, normal range", "Aiming", "Crosshair", "Range", 2.9, "Timing", "Late", "Min. fall height", 3.0);
        preset("Normal", MEDIUM, "Crosshair, full range", "Aiming", "Crosshair", "Range", 3.0, "Timing", "Late", "Min. fall height", 2.0);
        preset("Rage", HIGH, "Silent on the nearest target, more range", "Aiming", "Silent", "Range", 4.0, "Timing", "Late", "Min. fall height", 1.5);
    }

    @Override
    protected void onEnable() {
        hitThisFall = false;
        lastFall = 0;
    }

    private boolean hasMace() {
        if (MaceUtil.isMace(mc.player.getMainHandItem())) return true;
        MaceSwap swap = ModuleManager.get().get(MaceSwap.class);
        return swap != null && swap.isEnabled() && InvUtil.hotbar(MaceUtil::isMace) >= 0;
    }

    private TargetUtil.Filter filter() {
        return new TargetUtil.Filter(players.isOn(), hostiles.isOn(), passives.isOn(), true, false);
    }

    @Override
    public void onPreTick() {
        var p = mc.player;
        if (p == null || mc.gameMode == null || mc.level == null) return;
        double fall = p.fallDistance;
        // a new fall starts after landing or when the fall distance was reset (e.g. wind burst)
        if (p.onGround() || fall < lastFall) hitThisFall = false;
        lastFall = fall;
        if (hitThisFall || mc.gui.screen() != null || p.isPassenger() || p.isInWater() || p.isUsingItem()) return;
        if (!MaceUtil.canSmash(p, minFall.get())) return;
        if (needMace.isOn() && !hasMace()) return;

        Vec3 eyes = p.getEyePosition();
        LivingEntity target = null;
        if (aim.is("Silent")) {
            List<LivingEntity> list = TargetUtil.find(filter(), range.get(), 360f, false, "Distance");
            if (!list.isEmpty()) target = list.get(0);
        } else if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity e
                && TargetUtil.isValid(e, filter())
                && eyes.distanceTo(RotationUtil.closestPoint(eyes, e)) <= range.get()) {
            target = e;
        }
        if (target == null) return;
        if (aim.is("Silent")) {
            // turn towards the target every tick while falling at it (with the movement packet), so the
            // server already has the rotation when the hit goes out – no extra rotation packet
            AABB area = RotationUtil.aimArea(RotationUtil.predictedBox(target, 1f, 1), "Auto");
            Vec3 point = RotationUtil.bestAimPoint(eyes, p.getYRot(), p.getXRot(), area, true);
            float[] rot = RotationUtil.rotationsTo(eyes, point);
            RotationManager.request(rot[0], rot[1], 25);
            if (!RotationManager.serverLooksAt(rot[0], rot[1], 10f)) return;
        }
        if (timing.is("Late") && !lastChance(target)) return;
        AttackUtil.attack(target);
        hitThisFall = true;
    }

    /** Is this the last tick to hit – next tick we land, sink into the target or it is out of reach? */
    private boolean lastChance(Entity target) {
        var p = mc.player;
        Vec3 v = p.getDeltaMovement();
        double vy = (v.y - 0.08) * 0.98;
        Vec3 next = new Vec3(v.x, vy, v.z);
        // landing on a block
        Vec3 feet = p.position();
        var clip = mc.level.clip(new ClipContext(feet, feet.add(next).add(0, -0.3, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (clip.getType() != HitResult.Type.MISS) return true;
        // landing on the target
        if (feet.y + vy <= target.getBoundingBox().maxY + 0.3) return true;
        // target out of reach next tick
        Vec3 nextEyes = p.getEyePosition().add(next);
        Vec3 tv = target.getDeltaMovement();
        AABB nextBox = target.getBoundingBox().move(tv.x, 0, tv.z);
        return nextEyes.distanceTo(RotationUtil.closestPoint(nextEyes, nextBox)) > range.get();
    }

    @Override
    public String getInfo() {
        return timing.get();
    }
}
