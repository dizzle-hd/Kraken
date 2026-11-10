package dev.nautilus.module.visuals;

import dev.nautilus.fx.Effects;
import dev.nautilus.fx.Fx;
import dev.nautilus.hud.HudStyle;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.NumberSetting;
import dev.nautilus.util.Render3D;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Elytra flight: glowing wind ribbons from both wing tips and streaks of wind rushing past you. */
public class ElytraFx extends Module {
    private final BoolSetting trails = add(new BoolSetting("Flügelspuren", "Leuchtende Bänder von beiden Flügelspitzen (in der 3. Person sichtbar)", true));
    private final BoolSetting wind = add(new BoolSetting("Windlinien", "Windstreifen, die an dir vorbeirauschen – je schneller, desto mehr", true));
    private final NumberSetting length = add(new NumberSetting("Länge", "Wie lange die Spuren bleiben (Sekunden)", 1.2, 0.3, 3.0, 0.1));

    private record Sample(long time, Vec3 left, Vec3 right) {}

    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private long lastWind;

    public ElytraFx() {
        super("Elytra-Spuren", "Beim Elytra-Flug ziehen beide Flügelspitzen leuchtende Windbänder hinter sich her, Windstreifen rauschen vorbei.",
                Category.VISUALS, Items.ELYTRA);
    }

    @Override
    protected void onDisable() { samples.clear(); }

    @Override
    public void onRender3D(LevelRenderContext context, Render3D render) {
        if (mc.player == null) return;
        var p = mc.player;
        long now = System.currentTimeMillis();
        long keep = (long) (length.get() * 1000);
        while (!samples.isEmpty() && now - samples.peekFirst().time() > keep) samples.removeFirst();
        if (p.isFallFlying()) {
            float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            Vec3 pos = p.getPosition(pt);
            Vec3 fwd = p.getViewVector(pt).normalize();
            Vec3 right = fwd.cross(new Vec3(0, 1, 0));
            right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
            Vec3 up = right.cross(fwd).normalize();
            Vec3 body = pos.add(0, 0.3, 0).add(up.scale(0.2)).add(fwd.scale(-0.3));
            if (samples.isEmpty() || now - samples.peekLast().time() > 16)
                samples.addLast(new Sample(now, body.add(right.scale(-0.95)), body.add(right.scale(0.95))));

            if (wind.isOn()) {
                double speed = p.getDeltaMovement().length() * 20; // blocks per second
                long every = (long) Math.max(15, 220 - speed * 6);
                if (speed > 8 && now - lastWind > every) {
                    lastWind = now;
                    Vec3 off = up.scale(Effects.RANDOM.nextGaussian() * 1.4).add(right.scale(Effects.RANDOM.nextGaussian() * 1.8));
                    Vec3 start = p.getEyePosition(pt).add(fwd.scale(6 + Effects.RANDOM.nextFloat() * 4)).add(off);
                    Vec3 vel = p.getDeltaMovement().scale(-20 * 0.6);
                    Fx.add(windStreak(start, vel, 350));
                }
            }
        }
        if (trails.isOn() && samples.size() > 2) {
            List<Vec3> l = new ArrayList<>(), r = new ArrayList<>(), sideL = new ArrayList<>(), sideR = new ArrayList<>();
            float[] al = new float[samples.size()];
            int i = 0;
            for (Sample s : samples) {
                float age = (now - s.time()) / (float) keep;
                al[i++] = (1f - age) * 0.8f;
                l.add(s.left());
                r.add(s.right());
                float w = 0.04f + 0.18f * age; // ribbons widen as they fade
                sideL.add(new Vec3(0, w, 0));
                sideR.add(new Vec3(0, w, 0));
            }
            Fx fx = Fx.get();
            fx.ribbon(l, sideL, HudStyle.accent(), al);
            fx.ribbon(r, sideR, HudStyle.accent2(), al);
        }
    }

    private static Fx.Effect windStreak(Vec3 start, Vec3 vel, long lifeMs) {
        long born = System.currentTimeMillis();
        return (fx, now) -> {
            float t = (now - born) / (float) lifeMs;
            if (t > 1f) return false;
            Vec3 head = start.add(vel.scale(t * lifeMs / 1000f));
            Vec3 tail = head.subtract(vel.normalize().scale(1.6));
            Vec3 side = fx.cameraUp().scale(0.015);
            float a = (float) Math.sin(t * Math.PI) * 0.7f;
            fx.quad(head.add(side), tail, tail, head.subtract(side), Fx.argb(0xFFFFFF, a), 0, 0, Fx.argb(0xFFFFFF, a));
            return true;
        };
    }
}
