package dev.nautilus.module.combat;

import dev.nautilus.util.AttackUtil;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.NumberSetting;
import dev.nautilus.util.TargetUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

import java.util.concurrent.ThreadLocalRandom;

/** Attacks automatically when the crosshair is on a valid target. */
public class TriggerBot extends Module {
    private final BoolSetting players = add(new BoolSetting("Spieler", "Spieler angreifen", true));
    private final BoolSetting hostiles = add(new BoolSetting("Monster", "Feindliche Mobs angreifen", true));
    private final BoolSetting passives = add(new BoolSetting("Tiere", "Friedliche Mobs angreifen", false));
    private final BoolSetting invisibles = add(new BoolSetting("Unsichtbare", "Unsichtbare Ziele angreifen", true));
    private final NumberSetting cooldown = add(new NumberSetting("Abklingzeit", "Angriff erst ab dieser Aufladung (1.0 = voll)", 0.95, 0.1, 1.0, 0.05));
    private final NumberSetting minDelay = add(new NumberSetting("Min. Verzögerung", "Zusätzliche Wartezeit in Ticks (min)", 0, 0, 10, 1));
    private final NumberSetting maxDelay = add(new NumberSetting("Max. Verzögerung", "Zusätzliche Wartezeit in Ticks (max)", 2, 0, 10, 1));
    private final BoolSetting onlyCrits = add(new BoolSetting("Nur Krits", "Nur zuschlagen während du fällst (kritischer Treffer)", false));
    private final BoolSetting pauseOnUse = add(new BoolSetting("Pause beim Benutzen", "Nicht angreifen während du isst / blockst", true));

    private int wait;

    public TriggerBot() {
        super("TriggerBot", "Schlägt automatisch zu, sobald dein Fadenkreuz auf einem Ziel liegt.", Category.COMBAT, Items.TRIPWIRE_HOOK);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;
        if (wait > 0) { wait--; return; }
        if (!(mc.hitResult instanceof EntityHitResult hit)) return;
        Entity target = hit.getEntity();
        if (!TargetUtil.isValid(target, new TargetUtil.Filter(players.isOn(), hostiles.isOn(), passives.isOn(), invisibles.isOn(), false))) return;
        if (pauseOnUse.isOn() && mc.player.isUsingItem()) return;
        if (mc.player.getAttackStrengthScale(0.5f) < cooldown.get()) return;
        if (onlyCrits.isOn() && !(mc.player.fallDistance > 0 && !mc.player.onGround() && !mc.player.onClimbable() && !mc.player.isInWater())) return;

        AttackUtil.attack(target);

        int lo = minDelay.getInt(), hi = Math.max(lo, maxDelay.getInt());
        wait = lo == hi ? lo : ThreadLocalRandom.current().nextInt(lo, hi + 1);
    }
}
