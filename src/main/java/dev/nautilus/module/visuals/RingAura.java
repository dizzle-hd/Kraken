package dev.nautilus.module.visuals;

import dev.nautilus.fx.Fx;
import dev.nautilus.hud.HudStyle;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.util.Render3D;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** The Nautilus ring around your feet: two counter-rotating dashed rings and orbiting lights. */
public class RingAura extends Module {
    private final BoolSetting thirdPerson = add(new BoolSetting("Only 3rd person", "Only show when the camera is not in your head", false));
    private final BoolSetting orbit = add(new BoolSetting("Lights", "Three lights circle around you", true));

    public RingAura() {
        super("Ring aura", "The Nautilus trademark around your feet: two counter-rotating rings and circling lights.",
                Category.VISUALS, Items.HEART_OF_THE_SEA);
    }

    @Override
    public void onRender3D(LevelRenderContext context, Render3D render) {
        if (mc.player == null) return;
        if (thirdPerson.isOn() && mc.options.getCameraType().isFirstPerson()) return;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 feet = mc.player.getPosition(pt).add(0, 0.06, 0);
        float t = (System.currentTimeMillis() % 100_000L) / 1000f;
        Fx fx = Fx.get();
        Vec3 u = new Vec3(1, 0, 0), v = new Vec3(0, 0, 1);
        int c1 = HudStyle.accent(), c2 = HudStyle.accent2();
        float pulse = 0.75f + 0.25f * (float) Math.sin(t * 2.2);
        // outer dashed ring (6 dashes), turning clockwise
        for (int i = 0; i < 6; i++) {
            float a0 = t * 0.8f + i * (float) Math.PI / 3, a1 = a0 + 0.75f;
            fx.ring(feet, u, v, 0.95f, 0.08f, c1, 0.85f * pulse, a0, a1, 10);
        }
        // inner continuous ring turning the other way with a bright head
        fx.ring(feet, u, v, 0.7f, 0.05f, c2, 0.45f, 0f, (float) (Math.PI * 2), 48);
        float h = -t * 1.3f;
        fx.ring(feet, u, v, 0.7f, 0.07f, 0xFFFFFF, 0.8f, h, h + 0.9f, 12);
        if (orbit.isOn()) {
            for (int i = 0; i < 3; i++) {
                double a = t * 1.6 + i * Math.PI * 2 / 3;
                Vec3 p = feet.add(Math.cos(a) * 0.95, 0.35 + 0.25 * Math.sin(t * 2 + i * 2), Math.sin(a) * 0.95);
                fx.spark(p, 0.09f, i == 0 ? 0xFFFFFF : c1, 0.9f);
            }
        }
    }
}
