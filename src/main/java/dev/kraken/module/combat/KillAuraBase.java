package dev.kraken.module.combat;

import dev.kraken.util.AttackUtil;
import dev.kraken.module.Category;
import dev.kraken.module.Module;
import dev.kraken.setting.BoolSetting;
import dev.kraken.setting.ModeSetting;
import dev.kraken.setting.NumberSetting;
import dev.kraken.util.RotationUtil;
import dev.kraken.util.TargetUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Shared target and aim settings for the kill auras and the aimbot. */
public abstract class KillAuraBase extends Module {
    protected final NumberSetting range;
    protected final NumberSetting fov;
    protected final ModeSetting sort = add(new ModeSetting("Sortierung", "Welches Ziel zuerst angegriffen wird", "Distanz", "Distanz", "Leben", "Winkel"));
    protected final BoolSetting players = add(new BoolSetting("Spieler", "Spieler angreifen", true));
    protected final BoolSetting hostiles = add(new BoolSetting("Monster", "Feindliche Mobs angreifen", true));
    protected final BoolSetting passives = add(new BoolSetting("Tiere", "Friedliche Mobs angreifen", false));
    protected final BoolSetting invisibles = add(new BoolSetting("Unsichtbare", "Unsichtbare Ziele angreifen", true));
    protected final BoolSetting teammates = add(new BoolSetting("Teammates", "Auch Spieler aus deinem Team angreifen", false));
    protected final ModeSetting aimPart = add(new ModeSetting("Zielpunkt", "Wohin gezielt wird: Auto = kleinste Drehung, sonst Kopf / Brust / Beine", "Auto", "Auto", "Kopf", "Brust", "Beine"));
    protected final NumberSetting prediction = add(new NumberSetting("Vorhersage", "Bewegung des Ziels vorhalten (Ticks, 0 = aus)", 1, 0, 4, 0.5));
    protected final BoolSetting lockTarget = add(new BoolSetting("Ziel halten", "Beim aktuellen Ziel bleiben statt ständig zu wechseln", true));
    protected final BoolSetting pauseOnUse = add(new BoolSetting("Pause beim Benutzen", "Nicht angreifen während du isst / blockst", true));

    protected final List<LivingEntity> targets = new ArrayList<>();

    protected KillAuraBase(String name, String description, Item icon, double defRange, double maxRange, double defFov) {
        super(name, description, Category.COMBAT, icon);
        range = add(new NumberSetting("Reichweite", "Angriffsreichweite in Blöcken", defRange, 1.0, maxRange, 0.1));
        fov = add(new NumberSetting("FOV", "Nur Ziele innerhalb dieses Sichtwinkels (360 = rundum)", defFov, 10, 360, 5));
    }

    /** Finds the targets for this tick (sorted, with target lock) into {@link #targets}. */
    protected void findTargets(boolean throughWalls) {
        LivingEntity previous = targets.isEmpty() ? null : targets.get(0);
        targets.clear();
        targets.addAll(TargetUtil.find(filter(), range.get(), fov.getFloat(), throughWalls, sort.get()));
        if (lockTarget.isOn()) TargetUtil.stick(targets, previous);
    }

    /**
     * Rotation to aim at {@code target} from the given view: the cheapest point of the chosen body part,
     * at the predicted position of the target ({@code partialTick} for per-frame aiming, 1 per tick).
     */
    protected float[] aimAt(LivingEntity target, float yaw, float pitch, float partialTick, boolean needVisible) {
        Vec3 eyes = mc.player.getEyePosition(partialTick);
        AABB area = RotationUtil.aimArea(RotationUtil.predictedBox(target, partialTick, prediction.get()), aimPart.get());
        return RotationUtil.rotationsTo(eyes, RotationUtil.bestAimPoint(eyes, yaw, pitch, area, needVisible));
    }

    protected TargetUtil.Filter filter() {
        return new TargetUtil.Filter(players.isOn(), hostiles.isOn(), passives.isOn(), invisibles.isOn(), teammates.isOn());
    }

    protected boolean paused() {
        return mc.player == null || mc.gameMode == null
                || pauseOnUse.isOn() && mc.player.isUsingItem() || mc.player.isSpectator() || mc.player.isDeadOrDying();
    }

    protected void attack(LivingEntity target) {
        AttackUtil.attack(target);
    }

    @Override
    protected void onDisable() {
        targets.clear();
    }

    @Override
    public String getInfo() {
        return targets.isEmpty() ? null : targets.get(0).getName().getString();
    }
}
