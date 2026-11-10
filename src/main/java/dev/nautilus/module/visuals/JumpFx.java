package dev.nautilus.module.visuals;

import dev.nautilus.fx.Effects;
import dev.nautilus.fx.Fx;
import dev.nautilus.hud.HudStyle;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A glowing ring at your feet when you jump, and a shock wave with sparks when you land from a height. */
public class JumpFx extends Module {
    private final BoolSetting jump = add(new BoolSetting("Sprung", "Ring an den Füßen beim Abspringen", true));
    private final BoolSetting land = add(new BoolSetting("Landung", "Druckwelle und Funken beim Landen (stärker, je tiefer der Fall)", true));
    private final BoolSetting others = add(new BoolSetting("Andere Spieler", "Auch bei anderen Spielern in der Nähe", false));

    private record State(boolean ground, double fall) {}

    private final Map<UUID, State> states = new HashMap<>();

    public JumpFx() {
        super("Sprungeffekte", "Leuchtender Ring beim Springen, Druckwelle mit Funken beim Landen – je höher der Fall, desto größer.",
                Category.VISUALS, Items.SLIME_BALL);
    }

    @Override
    protected void onDisable() { states.clear(); }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        for (Player p : mc.level.players()) {
            if (p != mc.player && (!others.isOn() || p.distanceTo(mc.player) > 48)) continue;
            State old = states.get(p.getUUID());
            boolean ground = p.onGround();
            if (old != null) {
                Vec3 feet = p.position().add(0, 0.05, 0);
                if (jump.isOn() && old.ground() && !ground && p.getDeltaMovement().y > 0.2) {
                    Fx.add(Effects.wave(feet, 0.25f, 1.3f, 0.18f, HudStyle.accent(), 0.9f, 450, 0f));
                    Fx.add(Effects.wave(feet, 0.1f, 0.8f, 0.1f, 0xFFFFFF, 0.6f, 350, 0f));
                    Effects.burst(feet, 6, 1.6f, HudStyle.accent(), 0xFFFFFF, 0.06f, 450);
                }
                if (land.isOn() && !old.ground() && ground && old.fall() > 1.5) {
                    float f = (float) Math.min(20, old.fall());
                    Fx.add(Effects.wave(feet, 0.3f, 1.4f + f * 0.25f, 0.3f, HudStyle.accent(), 1f, 600, 0f));
                    Fx.add(Effects.wave(feet, 0.2f, 0.9f + f * 0.15f, 0.15f, HudStyle.accent2(), 0.8f, 500, 0.15f));
                    Fx.add(Effects.wave(feet, 0.1f, 0.6f + f * 0.08f, 0.1f, 0xFFFFFF, 0.7f, 380, 0f));
                    Effects.burst(feet, 8 + (int) (f * 2), 2f + f * 0.15f, HudStyle.accent(), 0xFFFFFF, 0.07f, 600);
                }
            }
            states.put(p.getUUID(), new State(ground, p.fallDistance));
        }
    }
}
