package dev.kraken.module.combat;

import dev.kraken.setting.BoolSetting;
import dev.kraken.setting.ModeSetting;
import dev.kraken.setting.NumberSetting;
import dev.kraken.util.RotationManager;
import dev.kraken.util.RotationUtil;
import dev.kraken.util.TargetUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;

import java.util.concurrent.ThreadLocalRandom;

/**
 * No-holds-barred aura: large range, multiple targets per tick, hits through walls,
 * snaps your camera (or rotates silently) and can ignore the attack cooldown.
 */
public class BlatantKillAura extends KillAuraBase {
    private final ModeSetting rotation = add(new ModeSetting("Rotation", "Kamera = Kamera gleitet zum Ziel, Silent = nur Server, Keine = gar nicht drehen", "Kamera", "Kamera", "Silent", "Keine"));
    private final NumberSetting smoothness = add(new NumberSetting("Glätte", "Wie weich die Kamera zum Ziel gleitet (höher = weicher, langsamer)", 6, 1, 20, 1)
            .visibleWhen(() -> rotation.is("Kamera")));
    private final NumberSetting maxTurn = add(new NumberSetting("Max. Drehung", "Höchste Drehgeschwindigkeit der Kamera in Grad pro Sekunde", 540, 90, 1440, 30)
            .visibleWhen(() -> rotation.is("Kamera")));
    private final ModeSetting timing = add(new ModeSetting("Timing", "Abklingzeit abwarten oder mit fester CPS spammen", "CPS", "CPS", "Abklingzeit"));
    private final NumberSetting cps = add(new NumberSetting("CPS", "Schläge pro Sekunde im CPS-Modus", 12, 1, 20, 1)
            .visibleWhen(() -> timing.is("CPS")));
    private final NumberSetting maxTargets = add(new NumberSetting("Max. Ziele", "Wie viele Ziele pro Schlag gleichzeitig getroffen werden", 3, 1, 10, 1));
    private final BoolSetting throughWalls = add(new BoolSetting("Durch Wände", "Ziele hinter Blöcken angreifen", true));

    private double attackBudget;

    public BlatantKillAura() {
        super("BlatantKillAura", "Aggressive Aura: hohe Reichweite, mehrere Ziele, durch Wände, Kamera gleitet zum Ziel.",
                Items.NETHERITE_SWORD, 4.5, 6.0, 360);
    }

    @Override
    protected void onEnable() {
        attackBudget = 0;
    }

    @Override
    public void onPreTick() {
        if (paused()) { targets.clear(); return; }
        findTargets(throughWalls.isOn());
        if (targets.isEmpty()) return;

        if (rotation.is("Silent")) {
            float[] rot = aimAt(targets.get(0), RotationManager.getServerYaw(), RotationManager.getServerPitch(), 1f, !throughWalls.isOn());
            RotationManager.request(rot[0], rot[1], 20);
        }
        // "Kamera" is handled every frame in onFrame(): the camera glides instead of snapping
    }

    private long lastFrame;

    /**
     * Every rendered frame: turn the camera a bit towards the target. The step shrinks as the view
     * gets closer (exponential ease-out), is limited to a maximum angular speed and uses the
     * interpolated + predicted position of the target, so the motion is smooth at any frame rate.
     */
    @Override
    public void onFrame() {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        if (!rotation.is("Kamera") || targets.isEmpty() || paused() || mc.gui.screen() != null || dt <= 0f) return;
        LivingEntity target = targets.get(0);
        if (!target.isAlive()) return;

        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
        float[] want = aimAt(target, yaw, pitch, pt, !throughWalls.isOn());
        float[] step = RotationUtil.smoothStep(yaw, pitch, want, 30f / smoothness.getFloat(), maxTurn.getFloat(), dt);
        // Entity#turn moves rotation and previous rotation together, like mouse input: no jitter
        mc.player.turn(step[0] / 0.15, step[1] / 0.15);
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
