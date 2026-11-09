package dev.kraken.module.combat;

import dev.kraken.setting.BoolSetting;
import dev.kraken.setting.NumberSetting;
import dev.kraken.util.RotationManager;
import dev.kraken.util.RotationUtil;
import dev.kraken.util.TargetUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

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
        targets.clear();
        current = null;
        if (paused()) { rotating = false; return; }

        targets.addAll(TargetUtil.find(filter(), range.get(), fov.getFloat(), throughWalls.isOn(), sort.get()));
        if (targets.isEmpty()) { rotating = false; return; }
        current = targets.get(0);

        if (!rotating) {
            yaw = RotationManager.getServerYaw();
            pitch = RotationManager.getServerPitch();
            rotating = true;
        }
        Vec3d eyes = mc.player.getEyePos();
        float[] wanted = RotationUtil.rotationsTo(eyes, RotationUtil.aimPoint(eyes, current));
        float[] next = RotationUtil.limit(yaw, pitch, wanted, turnSpeed.getFloat());
        yaw = next[0];
        pitch = next[1];
        RotationManager.request(yaw, pitch, 10);
    }

    @Override
    public void onTick() {
        if (current == null || paused()) return;
        if (!current.isAlive()) return;
        if (mc.player.getAttackCooldownProgress(0.5f) < cooldown.get()) return;
        Vec3d eyes = mc.player.getEyePos();
        if (eyes.distanceTo(RotationUtil.closestPoint(eyes, current)) > range.get()) return;
        float[] wanted = RotationUtil.rotationsTo(eyes, RotationUtil.aimPoint(eyes, current));
        if (RotationUtil.angleDifference(RotationManager.getServerYaw(), RotationManager.getServerPitch(), wanted) > aimTolerance.get()) return;
        attack(current);
    }
}
