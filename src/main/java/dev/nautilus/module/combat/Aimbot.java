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
    private final NumberSetting range = add(new NumberSetting("Reichweite", "Ziele bis zu dieser Entfernung anvisieren (Blöcke)", 4.5, 1.0, 8.0, 0.1));
    private final NumberSetting fov = add(new NumberSetting("FOV", "Nur Ziele innerhalb dieses Sichtwinkels (360 = rundum)", 90, 10, 360, 5));
    private final NumberSetting smoothness = add(new NumberSetting("Glätte", "Wie weich die Kamera zum Ziel gleitet (höher = weicher, langsamer)", 5, 1, 20, 0.5));
    private final NumberSetting maxTurn = add(new NumberSetting("Max. Drehung", "Höchste Drehgeschwindigkeit in Grad pro Sekunde", 360, 30, 1440, 30));
    private final ModeSetting aimPart = add(new ModeSetting("Zielpunkt", "Wohin gezielt wird: Auto = kleinste Drehung, sonst Kopf / Brust / Beine", "Auto", "Auto", "Kopf", "Brust", "Beine"));
    private final NumberSetting prediction = add(new NumberSetting("Vorhersage", "Bewegung des Ziels vorhalten (Ticks, 0 = aus)", 1, 0, 4, 0.5));
    private final BoolSetting vertical = add(new BoolSetting("Vertikal", "Auch nach oben/unten zielen (aus = nur seitlich)", true));
    // targets
    private final ModeSetting sort = add(new ModeSetting("Sortierung", "Welches Ziel zuerst anvisiert wird", "Distanz", "Distanz", "Leben", "Winkel"));
    private final BoolSetting players = add(new BoolSetting("Spieler", "Spieler anvisieren", true));
    private final BoolSetting hostiles = add(new BoolSetting("Monster", "Feindliche Mobs anvisieren", true));
    private final BoolSetting passives = add(new BoolSetting("Tiere", "Friedliche Mobs anvisieren", false));
    private final BoolSetting invisibles = add(new BoolSetting("Unsichtbare", "Unsichtbare Ziele anvisieren", true));
    private final BoolSetting teammates = add(new BoolSetting("Teammates", "Auch Spieler aus deinem Team anvisieren", false));
    private final BoolSetting throughWalls = add(new BoolSetting("Durch Wände", "Auch Ziele hinter Blöcken anvisieren", false));
    // behaviour
    private final BoolSetting lockTarget = add(new BoolSetting("Ziel halten", "Beim aktuellen Ziel bleiben statt ständig zu wechseln", true));
    private final BoolSetting onlyClick = add(new BoolSetting("Nur beim Klicken", "Nur zielen, während die Angriffstaste gedrückt ist", false));
    private final BoolSetting pauseOnUse = add(new BoolSetting("Pause beim Benutzen", "Nicht zielen während du isst / blockst", true));

    private final List<LivingEntity> targets = new ArrayList<>();
    private long lastFrame;

    // the visibility search costs up to 45 ray casts: per-frame aiming re-uses the result of the tick
    private LivingEntity visTarget;
    private long visTick = -1;
    private boolean visHidden;
    private Vec3 visOffset;

    public Aimbot() {
        super("Aimbot", "Zieht dein Fadenkreuz weich auf das beste Ziel in deinem Sichtkegel – mit Vorhersage und Ziel-Lock.",
                Category.COMBAT, Items.SPYGLASS);
        preset("Legit", LOW, "Leichte Zielhilfe: kleiner Kegel, langsam, nur beim Klicken",
                "Glätte", 14, "Max. Drehung", 120, "Nur beim Klicken", true, "FOV", 35, "Vorhersage", 0.5, "Durch Wände", false);
        preset("Normal", MEDIUM, "Deutliche Zielhilfe, mittleres Tempo",
                "Glätte", 6, "Max. Drehung", 360, "Nur beim Klicken", false, "FOV", 70, "Vorhersage", 1, "Durch Wände", false);
        preset("Rage", HIGH, "Rastet sofort ein, auch durch Wände",
                "Glätte", 2, "Max. Drehung", 1080, "Nur beim Klicken", false, "FOV", 180, "Vorhersage", 1.5, "Durch Wände", true);
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
