package dev.nautilus.module.combat;

import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.NumberSetting;
import dev.nautilus.util.RotationUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;

/**
 * Camera aimbot: smoothly pulls your crosshair onto the best target inside the FOV cone. Does not
 * attack by itself – combine it with your own clicks or the TriggerBot.
 *
 * <ul>
 *   <li>aims at the point of the chosen body part that needs the least rotation (no movement at all
 *       while the crosshair already rests on the target),</li>
 *   <li>leads moving targets by their velocity,</li>
 *   <li>keeps the current target instead of flicking between two,</li>
 *   <li>turns every rendered frame with an ease-out curve and a speed cap, like a real mouse.</li>
 * </ul>
 */
public class Aimbot extends KillAuraBase {
    private final NumberSetting smoothness = add(new NumberSetting("Glätte", "Wie weich die Kamera zum Ziel gleitet (höher = weicher, langsamer)", 5, 1, 20, 0.5));
    private final NumberSetting maxTurn = add(new NumberSetting("Max. Drehung", "Höchste Drehgeschwindigkeit in Grad pro Sekunde", 360, 30, 1440, 30));
    private final BoolSetting onlyClick = add(new BoolSetting("Nur beim Klicken", "Nur zielen, während die Angriffstaste gedrückt ist", false));
    private final BoolSetting vertical = add(new BoolSetting("Vertikal", "Auch nach oben/unten zielen (aus = nur seitlich)", true));
    private final BoolSetting throughWalls = add(new BoolSetting("Durch Wände", "Auch Ziele hinter Blöcken anvisieren", false));

    private long lastFrame;

    public Aimbot() {
        super("Aimbot", "Zieht dein Fadenkreuz weich auf das beste Ziel in deinem Sichtkegel – mit Vorhersage und Ziel-Lock.",
                Items.SPYGLASS, 4.5, 8.0, 90);
    }

    @Override
    protected void onEnable() {
        lastFrame = 0;
    }

    @Override
    public void onPreTick() {
        if (paused()) { targets.clear(); return; }
        findTargets(throughWalls.isOn());
    }

    @Override
    public void onFrame() {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        if (dt <= 0f || targets.isEmpty() || paused() || mc.gui.screen() != null) return;
        if (onlyClick.isOn() && !mc.options.keyAttack.isDown()) return;
        LivingEntity target = targets.get(0);
        if (!target.isAlive()) return;

        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
        float[] want = aimAt(target, yaw, pitch, pt, !throughWalls.isOn());
        if (!vertical.isOn()) want[1] = pitch;
        float[] step = RotationUtil.smoothStep(yaw, pitch, want, 30f / smoothness.getFloat(), maxTurn.getFloat(), dt);
        // same path as mouse input (rotation and previous rotation move together → no jitter)
        mc.player.turn(step[0] / 0.15, step[1] / 0.15);
    }
}
