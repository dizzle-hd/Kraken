package dev.kraken.module.combat;

import dev.kraken.setting.BoolSetting;
import dev.kraken.setting.NumberSetting;
import dev.kraken.util.RotationManager;
import dev.kraken.util.RotationUtil;
import dev.kraken.util.TargetUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Legit-looking aura: one target, waits for the full attack cooldown, rotates smoothly and only
 * server-side (your camera stays where it is), needs line of sight.
 */
public class SilentKillAura extends KillAuraBase {
    private final NumberSetting cooldown = add(new NumberSetting("Abklingzeit", "Angriff erst ab dieser Aufladung (1.0 = voll)", 0.95, 0.5, 1.0, 0.05));
    private final NumberSetting turnSpeed = add(new NumberSetting("Drehgeschwindigkeit", "Max. Grad pro Tick für die stille Rotation", 60, 10, 180, 5));
    private final NumberSetting aimTolerance = add(new NumberSetting("Toleranz", "Wie genau gezielt sein muss bevor geschlagen wird (Grad)", 15, 1, 60, 1));
    private final BoolSetting throughWalls = add(new BoolSetting("Durch Wände", "Ziele hinter Blöcken angreifen", false));

    private float yaw, pitch;
    private boolean rotating;
    private LivingEntity current;

    public SilentKillAura() {
        super("SilentKillAura", "Greift Ziele automatisch an – mit unsichtbarer Server-Rotation, deine Kamera bleibt ruhig.",
                Items.IRON_SWORD, 3.1, 4.5, 360);
    }

    @Override
    protected void onEnable() {
        rotating = false;
    }

    @Override
    public void onPreTick() {
        current = null;
        if (paused()) { targets.clear(); rotating = false; return; }

        findTargets(throughWalls.isOn());
        if (targets.isEmpty()) { rotating = false; return; }
        current = targets.get(0);

        if (!rotating) {
            yaw = RotationManager.getServerYaw();
            pitch = RotationManager.getServerPitch();
            rotating = true;
        }
        float[] wanted = aimAt(current, yaw, pitch, 1f, !throughWalls.isOn());
        // ease-out (60 % of the remaining angle per tick), capped by the turn speed
        float dYaw = net.minecraft.util.Mth.wrapDegrees(wanted[0] - yaw), dPitch = wanted[1] - pitch;
        float dist = (float) Math.sqrt(dYaw * dYaw + dPitch * dPitch);
        float step = Math.min(turnSpeed.getFloat(), Math.max(dist * 0.6f, Math.min(dist, 4f)));
        float[] next = RotationUtil.limit(yaw, pitch, wanted, step);
        yaw = next[0];
        pitch = next[1];
        RotationManager.request(yaw, pitch, 10);
    }

    @Override
    public void onTick() {
        if (current == null || paused()) return;
        if (!current.isAlive()) return;
        if (mc.player.getAttackStrengthScale(0.5f) < cooldown.get()) return;
        Vec3 eyes = mc.player.getEyePosition();
        if (eyes.distanceTo(RotationUtil.closestPoint(eyes, current)) > range.get()) return;
        float sYaw = RotationManager.getServerYaw(), sPitch = RotationManager.getServerPitch();
        // hit when the server rotation really points at the hitbox, or is at least close enough
        boolean onTarget = RotationUtil.rayHits(eyes, sYaw, sPitch, current.getBoundingBox().inflate(current.getPickRadius()), range.get());
        if (!onTarget) {
            float[] wanted = aimAt(current, sYaw, sPitch, 1f, false);
            if (RotationUtil.angleDifference(sYaw, sPitch, wanted) > aimTolerance.get()) return;
        }
        attack(current);
    }
}
