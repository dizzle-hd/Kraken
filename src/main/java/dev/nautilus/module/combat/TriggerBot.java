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
    private final BoolSetting players = add(new BoolSetting("Spieler", "Spieler angreifen", true));
    private final BoolSetting hostiles = add(new BoolSetting("Monster", "Feindliche Mobs angreifen", true));
    private final BoolSetting passives = add(new BoolSetting("Tiere", "Friedliche Mobs angreifen", false));
    private final BoolSetting invisibles = add(new BoolSetting("Unsichtbare", "Unsichtbare Ziele angreifen", true));
    private final NumberSetting cooldown = add(new NumberSetting("Abklingzeit", "Angriff erst ab dieser Aufladung (1.0 = voll)", 0.95, 0.1, 1.0, 0.05));
    private final NumberSetting minDelay = add(new NumberSetting("Min. Verzögerung", "Extra-Ticks NACH dem Aufladen, bevor geschlagen wird (min)", 0, 0, 10, 1));
    private final NumberSetting maxDelay = add(new NumberSetting("Max. Verzögerung", "Extra-Ticks NACH dem Aufladen, bevor geschlagen wird (max)", 2, 0, 10, 1));
    private final NumberSetting minReaction = add(new NumberSetting("Min. Reaktion", "Ticks, bis auf ein neues Ziel im Fadenkreuz reagiert wird (min)", 1, 0, 10, 1));
    private final NumberSetting maxReaction = add(new NumberSetting("Max. Reaktion", "Ticks, bis auf ein neues Ziel im Fadenkreuz reagiert wird (max)", 3, 0, 10, 1));
    private final NumberSetting minReach = add(new NumberSetting("Min. Reichweite", "Zufällige Reichweite pro Schlag, untere Grenze (Blöcke)", 2.6, 1.0, 3.0, 0.05));
    private final NumberSetting maxReach = add(new NumberSetting("Max. Reichweite", "Zufällige Reichweite pro Schlag, obere Grenze (Blöcke)", 2.95, 1.0, 3.0, 0.05));
    private final NumberSetting skipChance = add(new NumberSetting("Auslassen", "Chance in %, eine Gelegenheit absichtlich auszulassen", 0, 0, 30, 1));
    private final BoolSetting onlyCrits = add(new BoolSetting("Nur Krits", "Nur zuschlagen während du fällst (kritischer Treffer)", false));
    private final BoolSetting pauseOnUse = add(new BoolSetting("Pause beim Benutzen", "Nicht angreifen während du isst / blockst", true));

    private Entity seen;          // target currently in the crosshair
    private int reaction = -1;    // ticks left until we react to it
    private int readyWait = -1;   // ticks left after the weapon is charged
    private double reachLimit = 3.0;

    public TriggerBot() {
        super("TriggerBot", "Schlägt automatisch zu, sobald dein Fadenkreuz auf einem Ziel liegt – mit Reaktionszeit und zufälliger Reichweite.",
                Category.COMBAT, Items.TRIPWIRE_HOOK);
        preset("Legit", LOW, "Volle Aufladung + 1–4 Ticks, Reaktion 3–6 Ticks, Reichweite 2,4–2,85",
                "Abklingzeit", 1.0, "Min. Verzögerung", 1, "Max. Verzögerung", 4, "Min. Reaktion", 3, "Max. Reaktion", 6,
                "Min. Reichweite", 2.4, "Max. Reichweite", 2.85, "Auslassen", 5, "Nur Krits", false);
        preset("Normal", MEDIUM, "Fast volle Aufladung + 0–2 Ticks, Reaktion 1–3 Ticks, Reichweite 2,6–2,95",
                "Abklingzeit", 0.95, "Min. Verzögerung", 0, "Max. Verzögerung", 2, "Min. Reaktion", 1, "Max. Reaktion", 3,
                "Min. Reichweite", 2.6, "Max. Reichweite", 2.95, "Auslassen", 0, "Nur Krits", false);
        preset("Schnell", HIGH, "Sofort, früh und immer bei voller Reichweite",
                "Abklingzeit", 0.8, "Min. Verzögerung", 0, "Max. Verzögerung", 0, "Min. Reaktion", 0, "Max. Reaktion", 0,
                "Min. Reichweite", 3.0, "Max. Reichweite", 3.0, "Auslassen", 0, "Nur Krits", false);
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
