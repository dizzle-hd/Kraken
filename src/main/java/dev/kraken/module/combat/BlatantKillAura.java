package dev.kraken.module.combat;

import dev.kraken.setting.BoolSetting;
import dev.kraken.setting.ModeSetting;
import dev.kraken.setting.NumberSetting;
import dev.kraken.util.RotationManager;
import dev.kraken.util.RotationUtil;
import dev.kraken.util.TargetUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * No-holds-barred aura: large range, multiple targets per tick, hits through walls,
 * snaps your camera (or rotates silently) and can ignore the attack cooldown.
 */
public class BlatantKillAura extends KillAuraBase {
    private final ModeSetting rotation = add(new ModeSetting("Rotation", "Kamera = sichtbares Einrasten, Silent = nur Server, Keine = gar nicht drehen", "Kamera", "Kamera", "Silent", "Keine"));
    private final ModeSetting timing = add(new ModeSetting("Timing", "Abklingzeit abwarten oder mit fester CPS spammen", "CPS", "CPS", "Abklingzeit"));
    private final NumberSetting cps = add(new NumberSetting("CPS", "Schläge pro Sekunde im CPS-Modus", 12, 1, 20, 1)
            .visibleWhen(() -> timing.is("CPS")));
    private final NumberSetting maxTargets = add(new NumberSetting("Max. Ziele", "Wie viele Ziele pro Schlag gleichzeitig getroffen werden", 3, 1, 10, 1));
    private final BoolSetting throughWalls = add(new BoolSetting("Durch Wände", "Ziele hinter Blöcken angreifen", true));

    private double attackBudget;

    public BlatantKillAura() {
        super("BlatantKillAura", "Aggressive Aura: hohe Reichweite, mehrere Ziele, durch Wände, Kamera rastet ein.",
                Items.NETHERITE_SWORD, 4.5, 6.0, 360);
    }

    @Override
    protected void onEnable() {
        attackBudget = 0;
    }

    @Override
    public void onPreTick() {
        targets.clear();
        if (paused()) return;
        targets.addAll(TargetUtil.find(filter(), range.get(), fov.getFloat(), throughWalls.isOn(), sort.get()));
        if (targets.isEmpty()) return;

        Vec3 eyes = mc.player.getEyePosition();
        float[] rot = RotationUtil.rotationsTo(eyes, RotationUtil.aimPoint(eyes, targets.get(0)));
        switch (rotation.get()) {
            case "Kamera" -> {
                mc.player.setYRot(rot[0]);
                mc.player.setXRot(rot[1]);
            }
            case "Silent" -> RotationManager.request(rot[0], rot[1], 20);
            default -> {}
        }
    }

    @Override
    public void onTick() {
        if (targets.isEmpty() || paused()) return;

        int swings;
        if (timing.is("CPS")) {
            // randomise a little so the clicks are not perfectly even
            attackBudget += cps.get() / 20.0 * (0.85 + ThreadLocalRandom.current().nextDouble() * 0.3);
            swings = (int) attackBudget;
            attackBudget -= swings;
            if (swings <= 0) return;
            swings = Math.min(swings, 2);
        } else {
            if (mc.player.getAttackStrengthScale(0.5f) < 1.0f) return;
            swings = 1;
        }

        for (int s = 0; s < swings; s++) {
            int n = Math.min(maxTargets.getInt(), targets.size());
            for (int i = 0; i < n; i++) {
                LivingEntity t = targets.get(i);
                if (t.isAlive()) attack(t);
            }
        }
    }
}
