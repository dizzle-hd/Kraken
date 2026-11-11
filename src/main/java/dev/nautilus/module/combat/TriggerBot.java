package dev.nautilus.module.combat;

import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.NumberSetting;
import dev.nautilus.util.AttackUtil;
import dev.nautilus.util.RotationUtil;
import dev.nautilus.util.TargetUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Attacks when the crosshair is on a valid target – like a human would:
 * <ul>
 *   <li>a reaction time after the target comes into the crosshair,</li>
 *   <li>the extra delay starts when the weapon is charged (not right after the hit, where it was
 *       hidden by the cooldown and every hit landed exactly at 100 %),</li>
 *   <li>a random reach limit per hit, so it does not always hit at exactly 3 blocks,</li>
 *   <li>optionally a few missed opportunities.</li>
 * </ul>
 */
public class TriggerBot extends Module {
    private final BoolSetting players = add(new BoolSetting("Players", "Attack players", true));
    private final BoolSetting hostiles = add(new BoolSetting("Monsters", "Attack hostile mobs", true));
    private final BoolSetting passives = add(new BoolSetting("Animals", "Attack passive mobs", false));
    private final BoolSetting invisibles = add(new BoolSetting("Invisibles", "Attack invisible targets", true));
    private final NumberSetting cooldown = add(new NumberSetting("Cooldown", "Only attack from this charge on (1.0 = full)", 0.95, 0.1, 1.0, 0.05));
    private final NumberSetting minDelay = add(new NumberSetting("Min. delay", "Extra ticks AFTER charging before hitting (min)", 0, 0, 10, 1));
    private final NumberSetting maxDelay = add(new NumberSetting("Max. delay", "Extra ticks AFTER charging before hitting (max)", 2, 0, 10, 1));
    private final NumberSetting minReaction = add(new NumberSetting("Min. reaction", "Ticks until reacting to a new target in the crosshair (min)", 1, 0, 10, 1));
    private final NumberSetting maxReaction = add(new NumberSetting("Max. reaction", "Ticks until reacting to a new target in the crosshair (max)", 3, 0, 10, 1));
    private final NumberSetting minReach = add(new NumberSetting("Min. range", "Random range per hit, lower limit (blocks)", 2.6, 1.0, 3.0, 0.05));
    private final NumberSetting maxReach = add(new NumberSetting("Max. range", "Random range per hit, upper limit (blocks)", 2.95, 1.0, 3.0, 0.05));
    private final NumberSetting skipChance = add(new NumberSetting("Skip chance", "Chance in % to skip an opportunity on purpose", 0, 0, 30, 1));
    private final BoolSetting onlyCrits = add(new BoolSetting("Only crits", "Only hit while you are falling (critical hit)", false));
    private final BoolSetting pauseOnUse = add(new BoolSetting("Pause while using", "Don't attack while eating / blocking", true));

    private Entity seen;          // target currently in the crosshair
    private int reaction = -1;    // ticks left until we react to it
    private int readyWait = -1;   // ticks left after the weapon is charged
    private double reachLimit = 3.0;

    public TriggerBot() {
        super("TriggerBot", "Hits automatically as soon as your crosshair is on a target – with reaction time and random range.",
                Category.COMBAT, Items.TRIPWIRE_HOOK);
        preset("Legit", LOW, "Full charge + 1–4 ticks, reaction 3–6 ticks, range 2.4–2.85",
                "Cooldown", 1.0, "Min. delay", 1, "Max. delay", 4, "Min. reaction", 3, "Max. reaction", 6,
                "Min. range", 2.4, "Max. range", 2.85, "Skip chance", 5, "Only crits", false);
        preset("Normal", MEDIUM, "Almost full charge + 0–2 ticks, reaction 1–3 ticks, range 2.6–2.95",
                "Cooldown", 0.95, "Min. delay", 0, "Max. delay", 2, "Min. reaction", 1, "Max. reaction", 3,
                "Min. range", 2.6, "Max. range", 2.95, "Skip chance", 0, "Only crits", false);
        preset("Rage", HIGH, "Instant, early and always at full range",
                "Cooldown", 0.8, "Min. delay", 0, "Max. delay", 0, "Min. reaction", 0, "Max. reaction", 0,
                "Min. range", 3.0, "Max. range", 3.0, "Skip chance", 0, "Only crits", false);
    }

    private static int between(int lo, int hi) {
        if (hi <= lo) return lo;
        return ThreadLocalRandom.current().nextInt(lo, hi + 1);
    }

    private void newReach() {
        double lo = Math.min(minReach.get(), maxReach.get()), hi = Math.max(minReach.get(), maxReach.get());
        reachLimit = lo + ThreadLocalRandom.current().nextDouble() * (hi - lo);
    }

    @Override
    protected void onEnable() {
        seen = null;
        reaction = -1;
        readyWait = -1;
        newReach();
    }

    /** Pre-tick: interactions go out before the movement packet, like a real click (no "post" packets). */
    @Override
    public void onPreTick() {
        if (mc.player == null || mc.gameMode == null) return;
        Entity target = mc.hitResult instanceof EntityHitResult hit ? hit.getEntity() : null;
        if (target != null && !TargetUtil.isValid(target, new TargetUtil.Filter(players.isOn(), hostiles.isOn(), passives.isOn(), invisibles.isOn(), false))) {
            target = null;
        }
        // reaction time: only for a target that just came into the crosshair
        if (target != seen) {
            seen = target;
            reaction = target == null ? -1 : between(minReaction.getInt(), maxReaction.getInt());
        }
        if (target == null) { readyWait = -1; return; }
        if (reaction > 0) { reaction--; return; }

        if (pauseOnUse.isOn() && mc.player.isUsingItem()) return;
        if (mc.player.getAttackStrengthScale(0.5f) < cooldown.get()) { readyWait = -1; return; }
        // the extra delay starts once the weapon is charged
        if (readyWait < 0) readyWait = between(minDelay.getInt(), maxDelay.getInt());
        if (readyWait > 0) { readyWait--; return; }

        Vec3 eyes = mc.player.getEyePosition();
        if (eyes.distanceTo(RotationUtil.closestPoint(eyes, target)) > reachLimit) return; // wait until it is closer
        if (onlyCrits.isOn() && !(mc.player.fallDistance > 0 && !mc.player.onGround() && !mc.player.onClimbable() && !mc.player.isInWater())) return;

        readyWait = -1;
        newReach();
        if (ThreadLocalRandom.current().nextInt(100) < skipChance.getInt()) {
            // a human sometimes does not click: wait for the next charge
            mc.player.resetOnlyAttackStrengthTicker();
            return;
        }
        AttackUtil.attack(target);
    }
}
