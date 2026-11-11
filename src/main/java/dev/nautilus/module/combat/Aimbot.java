package dev.nautilus.module.combat;

import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.ModeSetting;
import dev.nautilus.setting.NumberSetting;
import dev.nautilus.util.RotationUtil;
import dev.nautilus.util.TargetUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

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
 * It only moves the camera – the server sees the rotation in the normal movement packet, no extra packets.
 */
public class Aimbot extends Module {
    // aiming
    private final NumberSetting range = add(new NumberSetting("Range", "Aim at targets up to this distance (blocks)", 4.5, 1.0, 8.0, 0.1));
    private final NumberSetting fov = add(new NumberSetting("FOV", "Only targets within this view angle (360 = all around)", 90, 10, 360, 5));
    private final NumberSetting smoothness = add(new NumberSetting("Smoothness", "How smoothly the camera glides to the target (higher = smoother, slower)", 5, 1, 20, 0.5));
    private final NumberSetting maxTurn = add(new NumberSetting("Max. turn speed", "Highest turn speed in degrees per second", 360, 30, 1440, 30));
    private final ModeSetting aimPart = add(new ModeSetting("Aim point", "Where to aim: Auto = smallest turn, otherwise head / chest / legs", "Auto", "Auto", "Head", "Chest", "Legs"));
    private final NumberSetting prediction = add(new NumberSetting("Prediction", "Lead the target's movement (ticks, 0 = off)", 1, 0, 4, 0.5));
    private final BoolSetting vertical = add(new BoolSetting("Vertical", "Also aim up/down (off = sideways only)", true));
    // targets
    private final ModeSetting sort = add(new ModeSetting("Sort by", "Which target is aimed at first", "Distance", "Distance", "Health", "Angle"));
    private final BoolSetting players = add(new BoolSetting("Players", "Aim at players", true));
    private final BoolSetting hostiles = add(new BoolSetting("Monsters", "Aim at hostile mobs", true));
    private final BoolSetting passives = add(new BoolSetting("Animals", "Aim at passive mobs", false));
    private final BoolSetting invisibles = add(new BoolSetting("Invisibles", "Aim at invisible targets", true));
    private final BoolSetting teammates = add(new BoolSetting("Teammates", "Also aim at players of your team", false));
    private final BoolSetting throughWalls = add(new BoolSetting("Through walls", "Also aim at targets behind blocks", false));
    // behaviour
    private final BoolSetting lockTarget = add(new BoolSetting("Target lock", "Stay on the current target instead of switching all the time", true));
    private final BoolSetting onlyClick = add(new BoolSetting("Only while clicking", "Only aim while the attack key is held", false));
    private final BoolSetting pauseOnUse = add(new BoolSetting("Pause while using", "Don't aim while eating / blocking", true));

    private final List<LivingEntity> targets = new ArrayList<>();
    private long lastFrame;

    // the visibility search costs up to 45 ray casts: per-frame aiming re-uses the result of the tick
    private LivingEntity visTarget;
    private long visTick = -1;
    private boolean visHidden;
    private Vec3 visOffset;

    public Aimbot() {
        super("Aimbot", "Smoothly pulls your crosshair onto the best target in your view cone – with prediction and target lock.",
                Category.COMBAT, Items.SPYGLASS);
        preset("Legit", LOW, "Light aim assist: small cone, slow, only while clicking",
                "Smoothness", 14, "Max. turn speed", 120, "Only while clicking", true, "FOV", 35, "Prediction", 0.5, "Through walls", false);
        preset("Normal", MEDIUM, "Clear aim assist, medium speed",
                "Smoothness", 6, "Max. turn speed", 360, "Only while clicking", false, "FOV", 70, "Prediction", 1, "Through walls", false);
        preset("Rage", HIGH, "Snaps on instantly, also through walls",
                "Smoothness", 2, "Max. turn speed", 1080, "Only while clicking", false, "FOV", 180, "Prediction", 1.5, "Through walls", true);
    }

    @Override
    protected void onEnable() {
        lastFrame = 0;
    }

    @Override
    protected void onDisable() {
        targets.clear();
    }

    private boolean paused() {
        return mc.player == null || mc.gameMode == null
                || pauseOnUse.isOn() && mc.player.isUsingItem() || mc.player.isSpectator() || mc.player.isDeadOrDying();
    }

    /** Every tick: the sorted targets (with target lock). */
    @Override
    public void onPreTick() {
        if (paused()) { targets.clear(); return; }
        LivingEntity previous = targets.isEmpty() ? null : targets.get(0);
        targets.clear();
        var filter = new TargetUtil.Filter(players.isOn(), hostiles.isOn(), passives.isOn(), invisibles.isOn(), teammates.isOn());
        targets.addAll(TargetUtil.find(filter, range.get(), fov.getFloat(), throughWalls.isOn(), sort.get()));
        if (lockTarget.isOn()) TargetUtil.stick(targets, previous);
    }

    /** Every rendered frame: turn the camera a step towards the target. */
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

    /**
     * Rotation to aim at {@code target}: the cheapest point of the chosen body part at the predicted
     * position of the target.
     */
    private float[] aimAt(LivingEntity target, float yaw, float pitch, float partialTick, boolean needVisible) {
        Vec3 eyes = mc.player.getEyePosition(partialTick);
        AABB area = RotationUtil.aimArea(RotationUtil.predictedBox(target, partialTick, prediction.get()), aimPart.get());
        Vec3 cheap = RotationUtil.bestAimPoint(eyes, yaw, pitch, area, false);
        if (!needVisible) return RotationUtil.rotationsTo(eyes, cheap);

        long tick = mc.level.getGameTime();
        if (visTarget == target && visTick == tick && visOffset != null) {
            Vec3 p = visHidden ? new Vec3(area.minX, area.minY, area.minZ).add(visOffset) : cheap;
            return RotationUtil.rotationsTo(eyes, p);
        }
        Vec3 p = RotationUtil.bestAimPoint(eyes, yaw, pitch, area, true);
        visTarget = target;
        visTick = tick;
        visHidden = p.distanceToSqr(cheap) > 1e-6;
        visOffset = p.subtract(area.minX, area.minY, area.minZ);
        return RotationUtil.rotationsTo(eyes, p);
    }

    @Override
    public String getInfo() {
        return targets.isEmpty() ? null : targets.get(0).getName().getString();
    }
}
