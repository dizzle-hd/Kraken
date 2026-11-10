package dev.nautilus.module.visuals;

import dev.nautilus.fx.Fx;
import dev.nautilus.hud.HudStyle;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.ModeSetting;
import dev.nautilus.setting.NumberSetting;
import dev.nautilus.util.Render3D;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/** Glowing motes floating around you – slowly rising, swaying and twinkling like fireflies. */
public class GlowParticles extends Module {
    private final NumberSetting count = add(new NumberSetting("Anzahl", "Wie viele Lichter gleichzeitig schweben", 60, 10, 250, 10));
    private final NumberSetting radius = add(new NumberSetting("Radius", "Bereich um dich in Blöcken", 10, 4, 24, 1));
    private final NumberSetting size = add(new NumberSetting("Größe", "Größe der Lichter", 1.0, 0.4, 3.0, 0.1));
    private final ModeSetting colors = add(new ModeSetting("Farben", "Farbe der Lichter", "HUD-Farbe", "HUD-Farbe", "Regenbogen", "Glühwürmchen", "Weiß"));

    private final Random random = new Random();
    private double[] x = new double[0], y, z, phase, rise, born, life;
    private float[] hue;
    private long last;

    public GlowParticles() {
        super("Lichtpartikel", "Leuchtende Lichter schweben um dich herum, steigen langsam auf, schwanken und funkeln – wie Glühwürmchen.",
                Category.VISUALS, Items.GLOWSTONE_DUST);
    }

    private void spawn(int i, Vec3 c, double now, boolean anywhere) {
        double r = radius.get();
        double ang = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * r;
        x[i] = c.x + Math.cos(ang) * d;
        z[i] = c.z + Math.sin(ang) * d;
        y[i] = c.y - 2 + (anywhere ? random.nextDouble() * 7 : random.nextDouble() * 2);
        phase[i] = random.nextDouble() * 100;
        rise[i] = 0.12 + random.nextDouble() * 0.25;
        born[i] = now;
        life[i] = 6 + random.nextDouble() * 8;
        hue[i] = random.nextFloat();
    }

    private int color(int i) {
        return switch (colors.get()) {
            case "Regenbogen" -> net.minecraft.util.Mth.hsvToRgb(hue[i], 0.6f, 1f) & 0xFFFFFF;
            case "Glühwürmchen" -> hue[i] < 0.5f ? 0xD8FF6A : 0xFFE27A;
            case "Weiß" -> 0xFFFFFF;
            default -> (hue[i] < 0.55f ? HudStyle.accent() : hue[i] < 0.85f ? HudStyle.accent2() : 0xFFFFFF) & 0xFFFFFF;
        };
    }

    @Override
    public void onRender3D(LevelRenderContext context, Render3D render) {
        if (mc.player == null) return;
        int n = count.getInt();
        double now = System.currentTimeMillis() / 1000.0;
        float dt = last == 0 ? 0f : Math.min(0.1f, (System.currentTimeMillis() - last) / 1000f);
        last = System.currentTimeMillis();
        Vec3 c = mc.player.position();
        if (x.length != n) {
            x = new double[n]; y = new double[n]; z = new double[n]; phase = new double[n]; rise = new double[n];
            born = new double[n]; life = new double[n]; hue = new float[n];
            for (int i = 0; i < n; i++) spawn(i, c, now - random.nextDouble() * 6, true);
        }
        Fx fx = Fx.get();
        double r2 = radius.get() * radius.get() * 1.4;
        for (int i = 0; i < n; i++) {
            double age = now - born[i];
            double dx = x[i] - c.x, dz = z[i] - c.z;
            if (age > life[i] || dx * dx + dz * dz > r2 || Math.abs(y[i] - c.y) > 12) { spawn(i, c, now, false); continue; }
            y[i] += rise[i] * dt;
            x[i] += Math.sin(now * 0.7 + phase[i]) * 0.25 * dt;
            z[i] += Math.cos(now * 0.6 + phase[i] * 1.3) * 0.25 * dt;
            float fade = (float) Math.min(1.0, Math.min(age / 1.5, (life[i] - age) / 1.5));
            float twinkle = 0.55f + 0.45f * (float) Math.sin(now * 3 + phase[i] * 5);
            fx.spark(new Vec3(x[i], y[i], z[i]), 0.09f * size.getFloat(), color(i), fade * twinkle * 0.9f);
        }
    }
}
