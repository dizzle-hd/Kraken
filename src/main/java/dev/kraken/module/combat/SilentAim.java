package dev.kraken.module.combat;

import dev.kraken.util.AttackUtil;
import dev.kraken.module.Category;
import dev.kraken.module.Module;
import dev.kraken.setting.BoolSetting;
import dev.kraken.setting.NumberSetting;
import dev.kraken.util.RotationManager;
import dev.kraken.util.RotationUtil;
import dev.kraken.util.TargetUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * When you click and miss, the hit is redirected to the best target inside your FOV cone.
 * The rotation is only sent to the server – your camera never moves.
 */
public class SilentAim extends Module {
    private final NumberSetting fov = add(new NumberSetting("FOV", "Kegel um dein Fadenkreuz, in dem Ziele erfasst werden (Grad)", 40, 5, 180, 1));
    private final NumberSetting range = add(new NumberSetting("Reichweite", "Maximale Reichweite in Blöcken", 3.0, 2.0, 6.0, 0.1));
    private final BoolSetting players = add(new BoolSetting("Spieler", "Spieler anvisieren", true));
    private final BoolSetting hostiles = add(new BoolSetting("Monster", "Feindliche Mobs anvisieren", true));
    private final BoolSetting passives = add(new BoolSetting("Tiere", "Friedliche Mobs anvisieren", false));
    private final BoolSetting throughWalls = add(new BoolSetting("Durch Wände", "Auch Ziele hinter Blöcken treffen", false));
    private final BoolSetting onlyMiss = add(new BoolSetting("Nur bei Fehlschlag", "Nur eingreifen wenn du sonst danebenschlagen würdest", true));

    public SilentAim() {
        super("SilentAim", "Lenkt deine Klicks unsichtbar auf das nächste Ziel in deinem Sichtkegel.", Category.COMBAT, Items.BOW);
    }

    /** @return true if the attack was handled (vanilla attack is cancelled). */
    public boolean handleAttack() {
        if (mc.player == null || mc.gameMode == null || mc.getConnection() == null) return false;
        if (mc.player.isPassenger() || mc.player.isSpectator()) return false;
        if (onlyMiss.isOn() && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY) return false;

        List<LivingEntity> targets = TargetUtil.find(
                new TargetUtil.Filter(players.isOn(), hostiles.isOn(), passives.isOn(), true, false),
                range.get(), fov.getFloat(), throughWalls.isOn(), "Winkel");
        if (targets.isEmpty()) return false;

        LivingEntity target = targets.get(0);
        Vec3 eyes = mc.player.getEyePosition();
        float[] rot = RotationUtil.rotationsTo(eyes, RotationUtil.aimPoint(eyes, target));

        // Tell the server we look at the target, then hit it.
        mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(rot[0], rot[1], mc.player.onGround(), mc.player.horizontalCollision));
        AttackUtil.attack(target);
        // keep the server rotation consistent for the next movement packet as well
        RotationManager.request(rot[0], rot[1], 5);
        return true;
    }
}
