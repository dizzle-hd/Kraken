package dev.nautilus.module.visuals;

import dev.nautilus.fx.Fx;
import dev.nautilus.hud.HudStyle;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** A trail of glowing motes behind your feet while you run. */
public class TrailFx extends Module {
    private final BoolSetting onlySprint = add(new BoolSetting("Only sprinting", "Only while sprinting (otherwise on every movement)", true));

    public TrailFx() {
        super("Run trail", "While running, a trail of glowing dots stays behind your feet, slowly rising and fading.",
                Category.VISUALS, Items.GLOW_INK_SAC);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        var p = mc.player;
        double speed = p.getDeltaMovement().horizontalDistance();
        if (speed < 0.08 || (onlySprint.isOn() && !p.isSprinting()) || p.isFallFlying()) return;
        Vec3 at = p.position().add((Math.random() - 0.5) * 0.4, 0.08, (Math.random() - 0.5) * 0.4);
        int color = Math.random() < 0.6 ? HudStyle.accent() : HudStyle.accent2();
        long born = System.currentTimeMillis();
        Fx.add((fx, now) -> {
            float t = (now - born) / 900f;
            if (t > 1f) return false;
            fx.spark(at.add(0, t * 0.35, 0), 0.08f * (1f - t * 0.5f), color, (1f - t) * 0.8f);
            return true;
        });
    }
}
