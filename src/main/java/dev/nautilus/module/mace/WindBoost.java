package dev.nautilus.module.mace;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.KeySetting;
import dev.nautilus.setting.ModeSetting;
import dev.nautilus.util.InvUtil;
import dev.nautilus.util.RotationManager;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Wind charge jump for mace smashes: jumps and throws a wind charge straight down in the same tick –
 * it launches you high into the air. The throw only looks down for the server (the use packet carries
 * the rotation), your camera stays where it is.
 * Trigger "Einmal": every press of the module key boosts once (the module switches itself off again).
 * Trigger "Taste": the module stays on and boosts on every press of the boost key.
 */
public class WindBoost extends Module {
    private final ModeSetting trigger = add(new ModeSetting("Auslöser", "Einmal = Modul-Taste boostet einmal (Modul geht wieder aus), Taste = Modul bleibt an, die Boost-Taste boostet", "Einmal", "Einmal", "Taste"));
    private final KeySetting boostKey = add(new KeySetting("Boost-Taste", "Taste für den Auslöser 'Taste'", -1).visibleWhen(() -> trigger.is("Taste")));
    private final BoolSetting jump = add(new BoolSetting("Springen", "Am Boden im selben Tick springen (höher)", true));
    private final BoolSetting back = add(new BoolSetting("Zurückwechseln", "Danach wieder den vorherigen Slot halten", true));

    private boolean keyWas;
    private boolean pending;

    public WindBoost() {
        super("WindBoost", "Springt und wirft eine Windkugel senkrecht nach unten – katapultiert dich für einen Streitkolben-Smash hoch.",
                Category.MACE, Items.WIND_CHARGE);
    }

    @Override
    protected void onEnable() {
        keyWas = true; // a key held while enabling does not count as a press
        pending = trigger.is("Einmal");
    }

    @Override
    public void onPreTick() {
        var p = mc.player;
        if (p == null || mc.gameMode == null || mc.getConnection() == null) return;
        if (trigger.is("Taste")) {
            boolean down = boostKey.isBound() && mc.gui.screen() == null && InputConstants.isKeyDown(boostKey.get());
            if (down && !keyWas) pending = true;
            keyWas = down;
        }
        if (!pending) return;
        pending = false;
        boost();
        if (trigger.is("Einmal")) setEnabled(false);
    }

    private void boost() {
        var p = mc.player;
        if (p.isPassenger() || p.isUsingItem() || mc.gui.screen() != null) return;

        InteractionHand hand;
        int prev = -1;
        if (p.getOffhandItem().is(Items.WIND_CHARGE)) {
            hand = InteractionHand.OFF_HAND;
        } else {
            int slot = InvUtil.hotbar(Items.WIND_CHARGE);
            if (slot < 0) return;
            hand = InteractionHand.MAIN_HAND;
            if (slot != InvUtil.selected()) { prev = InvUtil.selected(); InvUtil.select(slot); }
        }
        ItemStack stack = p.getItemInHand(hand);
        if (p.getCooldowns().isOnCooldown(stack)) {
            if (prev >= 0) InvUtil.select(prev);
            return;
        }

        var swing = stack.getInteractAnimation();
        if (jump.isOn() && p.onGround()) p.jumpFromGround();

        // look straight down only for the server: rotation packet + the rotation inside the use packet
        float pitch = p.getXRot();
        mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(p.getYRot(), 90f, p.onGround(), p.horizontalCollision));
        p.setXRot(90f);
        try {
            mc.gameMode.useItem(p, hand);
        } finally {
            p.setXRot(pitch);
        }
        RotationManager.request(p.getYRot(), 90f, 40);
        p.swing(hand, swing, false);

        if (prev >= 0 && back.isOn()) InvUtil.select(prev);
    }

    @Override
    public String getInfo() {
        return trigger.get();
    }
}
