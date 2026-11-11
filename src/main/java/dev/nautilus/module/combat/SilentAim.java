package dev.nautilus.module.combat;

import dev.nautilus.util.AttackUtil;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.NumberSetting;
import dev.nautilus.util.RotationManager;
import dev.nautilus.util.RotationUtil;
import dev.nautilus.util.TargetUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * When you click and miss, the hit is redirected to the best target inside your FOV cone.
 * The rotation is only sent to the server – your camera never moves.
 */
public class SilentAim extends Module {
    private final NumberSetting fov = add(new NumberSetting("FOV", "Cone around your crosshair in which targets are picked up (degrees)", 40, 5, 180, 1));
    private final NumberSetting range = add(new NumberSetting("Range", "Maximum range in blocks", 3.0, 2.0, 6.0, 0.1));
    private final BoolSetting players = add(new BoolSetting("Players", "Aim at players", true));
    private final BoolSetting hostiles = add(new BoolSetting("Monsters", "Aim at hostile mobs", true));
    private final BoolSetting passives = add(new BoolSetting("Animals", "Aim at passive mobs", false));
    private final BoolSetting throughWalls = add(new BoolSetting("Through walls", "Also hit targets behind blocks", false));
    private final BoolSetting onlyMiss = add(new BoolSetting("Only on miss", "Only step in when you would otherwise miss", true));

    public SilentAim() {
        super("SilentAim", "Invisibly redirects your clicks to the nearest target in your view cone.", Category.COMBAT, Items.BOW);
        preset("Legit", LOW, "Only near misses, small cone",
                "FOV", 15, "Range", 3.0, "Through walls", false, "Only on miss", true);
        preset("Normal", MEDIUM, "Medium cone",
                "FOV", 40, "Range", 3.0, "Through walls", false, "Only on miss", true);
        preset("Rage", HIGH, "Large cone, more range, through walls",
                "FOV", 120, "Range", 4.5, "Through walls", true, "Only on miss", false);
    }

    /** @return true if the attack was handled (vanilla attack is cancelled). */
    public boolean handleAttack() {
        if (mc.player == null || mc.gameMode == null || mc.getConnection() == null) return false;
        if (mc.player.isPassenger() || mc.player.isSpectator()) return false;
        if (onlyMiss.isOn() && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY) return false;

        List<LivingEntity> targets = TargetUtil.find(
                new TargetUtil.Filter(players.isOn(), hostiles.isOn(), passives.isOn(), true, false),
                range.get(), fov.getFloat(), throughWalls.isOn(), "Angle");
        if (targets.isEmpty()) return false;

        LivingEntity target = targets.get(0);
        Vec3 eyes = mc.player.getEyePosition();
        // least rotation away from the crosshair, at the position the target will have on the server
        AABB area = RotationUtil.aimArea(RotationUtil.predictedBox(target, 1f, 1), "Auto");
        Vec3 point = RotationUtil.bestAimPoint(eyes, mc.player.getYRot(), mc.player.getXRot(), area, !throughWalls.isOn());
        float[] rot = RotationUtil.rotationsTo(eyes, point);

        // No extra rotation packet: the rotation goes out with this tick's movement packet, the hit
        // follows in the next pre-tick – like a player who turns and then clicks.
        RotationManager.request(rot[0], rot[1], 5);
        pending = target;
        pendingTicks = 3;
        return true;
    }

    private LivingEntity pending;
    private int pendingTicks;

    @Override
    public void onPreTick() {
        if (pending == null || mc.player == null) return;
        LivingEntity t = pending;
        if (--pendingTicks < 0 || !t.isAlive() || t.isRemoved()) { pending = null; return; }
        Vec3 eyes = mc.player.getEyePosition();
        if (eyes.distanceTo(RotationUtil.closestPoint(eyes, t)) > range.get()) { pending = null; return; }
        AABB area = RotationUtil.aimArea(RotationUtil.predictedBox(t, 1f, 1), "Auto");
        Vec3 point = RotationUtil.bestAimPoint(eyes, mc.player.getYRot(), mc.player.getXRot(), area, !throughWalls.isOn());
        float[] rot = RotationUtil.rotationsTo(eyes, point);
        RotationManager.request(rot[0], rot[1], 5); // keep looking at it in this tick's packet too
        if (!RotationManager.serverLooksAt(rot[0], rot[1], 8f)) return;
        AttackUtil.attack(t);
        pending = null;
    }

    @Override
    protected void onDisable() { pending = null; }
}
