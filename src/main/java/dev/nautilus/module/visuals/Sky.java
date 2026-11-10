package dev.nautilus.module.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.nautilus.gui.Shapes;
import dev.nautilus.hud.HudStyle;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.module.ModuleManager;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.NumberSetting;
import dev.nautilus.util.Render3D;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Random;

/**
 * A livelier overworld sky in the Nautilus look: the trademark ring arching across the sky, a
 * northern-lights curtain and shooting stars at night, more stars and a sky tinted in the HUD colour.
 * Everything is client-side geometry far behind the terrain – it never hides blocks or entities.
 */
public class Sky extends Module {
    private final BoolSetting ring = add(new BoolSetting("Himmelsring", "Das Nautilus-Markenzeichen: ein leuchtender Ring quer über den Himmel", true));
    private final BoolSetting aurora = add(new BoolSetting("Polarlicht", "Wehende Lichtvorhänge im Norden – nachts", true));
    private final BoolSetting shootingStars = add(new BoolSetting("Sternschnuppen", "Ab und zu eine Sternschnuppe – nachts", true));
    private final NumberSetting tint = add(new NumberSetting("Himmelsfarbe", "Wie stark der Himmel in der HUD-Farbe getönt wird (0 = Vanilla)", 25, 0, 100, 5));
    private final NumberSetting stars = add(new NumberSetting("Sterne", "Mehr Sterne: schon in der Dämmerung und heller (0 = Vanilla)", 50, 0, 100, 10));
    private final NumberSetting intensity = add(new NumberSetting("Stärke", "Deckkraft von Ring, Polarlicht und Sternschnuppen", 70, 10, 100, 5));

    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("nautilusclient", "pipeline/sky_fx"))
            .withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            // behind terrain and clouds, but never written into the depth buffer
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .build();
    private static final RenderType QUADS = RenderType.create("nautilusclient_sky_fx", RenderSetup.builder(PIPELINE).createRenderSetup());

    // values of the vanilla sky of this frame (from SkyRendererMixin)
    private static boolean overworld;
    private static float night, clear = 1f;

    private final FloatArrayList pos = new FloatArrayList();
    private final IntArrayList col = new IntArrayList();
    private final Random random = new Random();
    private long nextStar;
    private float starYaw, starPitch, starDir;
    private long starStart = -1;

    public Sky() {
        super("Himmel", "Lebendiger Himmel: leuchtender Nautilus-Ring, Polarlicht, Sternschnuppen, mehr Sterne und Tönung in der HUD-Farbe.",
                Category.VISUALS, Items.FIREWORK_STAR);
        loadEnabled(true);
    }

    private static Sky instance() {
        Sky s = ModuleManager.get().get(Sky.class);
        return s != null && s.isEnabled() ? s : null;
    }

    /** SkyRendererMixin: adjust the vanilla sky (colour, sunrise, stars) and remember day/night. */
    public static void onSkyExtracted(SkyRenderState state) {
        overworld = state.skybox == DimensionType.Skybox.OVERWORLD;
        Sky s = instance();
        if (s == null || !overworld) return;
        night = Math.min(1f, state.starBrightness * 2f);
        clear = state.rainBrightness;
        float t = s.tint.getFloat() / 100f;
        int acc = HudStyle.accent();
        float ar = ((acc >> 16) & 0xFF) / 255f, ag = ((acc >> 8) & 0xFF) / 255f, ab = (acc & 0xFF) / 255f;
        if (t > 0 && state.skyColor != null) {
            var c = state.skyColor;
            // day: a soft pastel of the accent, night: a deep shade of it
            float lum = 0.299f * c.x() + 0.587f * c.y() + 0.114f * c.z();
            float k = t * 0.45f;
            state.skyColor = new Vector3f(c.x() + (ar * lum * 1.15f - c.x()) * k, c.y() + (ag * lum * 1.15f - c.y()) * k, c.z() + (ab * lum * 1.15f - c.z()) * k);
        }
        if (t > 0 && state.sunriseAndSunsetColor != null) {
            var c = state.sunriseAndSunsetColor;
            float k = t * 0.5f;
            state.sunriseAndSunsetColor = new Vector4f(c.x() + (ar - c.x()) * k, c.y() + (ag - c.y()) * k, c.z() + (ab - c.z()) * k, c.w());
        }
        float st = s.stars.getFloat() / 100f;
        if (st > 0) state.starBrightness = Math.min(1f, Math.max(state.starBrightness * (1f + st), state.starBrightness + st * 0.15f * state.rainBrightness));
    }

    // ---- geometry ---------------------------------------------------------------------------------

    private void quad(float[] a, float[] b, float[] c, float[] d, int ca, int cb, int cc, int cd) {
        pos.add(a[0]); pos.add(a[1]); pos.add(a[2]); col.add(ca);
        pos.add(b[0]); pos.add(b[1]); pos.add(b[2]); col.add(cb);
        pos.add(c[0]); pos.add(c[1]); pos.add(c[2]); col.add(cc);
        pos.add(d[0]); pos.add(d[1]); pos.add(d[2]); col.add(cd);
    }

    /** Fades everything out towards the horizon (the vanilla fog would hide it there anyway). */
    private static float horizon(float y, float dist) {
        float e = y / dist;
        return Math.max(0f, Math.min(1f, (e - 0.02f) / 0.18f));
    }

    private static int argb(int rgb, float a) {
        return ((int) (Math.max(0f, Math.min(1f, a)) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** Point on the tilted ring: angle t around it, offset w across the band (radians of sky). */
    private static float[] ringPoint(float d, float t, float w, float tilt, float yaw) {
        // circle in the XZ plane, lifted by the band offset, then tilted around X and turned around Y
        double x = Math.cos(t) * Math.cos(w), y = Math.sin(w), z = Math.sin(t) * Math.cos(w);
        double y1 = y * Math.cos(tilt) - z * Math.sin(tilt), z1 = y * Math.sin(tilt) + z * Math.cos(tilt);
        double x2 = x * Math.cos(yaw) + z1 * Math.sin(yaw), z2 = -x * Math.sin(yaw) + z1 * Math.cos(yaw);
        return new float[]{(float) (x2 * d), (float) (y1 * d), (float) (z2 * d)};
    }

    private void band(float d, float tilt, float yaw, float width, float alpha, int c1, int c2) {
        int seg = 160;
        float[] offs = {-width, -width * 0.25f, 0f, width * 0.25f, width};
        float[] alp = {0f, 0.55f, 1f, 0.55f, 0f};
        for (int i = 0; i < seg; i++) {
            float t0 = (float) (i * Math.PI * 2 / seg), t1 = (float) ((i + 1) * Math.PI * 2 / seg);
            int ca = Shapes.lerp(c1, c2, 0.5f + 0.5f * (float) Math.sin(t0 * 2)), cb = Shapes.lerp(c1, c2, 0.5f + 0.5f * (float) Math.sin(t1 * 2));
            for (int k = 0; k < offs.length - 1; k++) {
                float[] p00 = ringPoint(d, t0, offs[k], tilt, yaw), p01 = ringPoint(d, t0, offs[k + 1], tilt, yaw);
                float[] p11 = ringPoint(d, t1, offs[k + 1], tilt, yaw), p10 = ringPoint(d, t1, offs[k], tilt, yaw);
                quad(p00, p10, p11, p01,
                        argb(ca, alpha * alp[k] * horizon(p00[1], d)), argb(cb, alpha * alp[k] * horizon(p10[1], d)),
                        argb(cb, alpha * alp[k + 1] * horizon(p11[1], d)), argb(ca, alpha * alp[k + 1] * horizon(p01[1], d)));
            }
        }
    }

    private void aurora(float d, float alpha, float time) {
        int seg = 70;
        int c1 = HudStyle.accent(), c2 = HudStyle.hueShift(HudStyle.accent(), 0.35f);
        if (c2 == c1) c2 = 0xFF3DDCB0; // white accent: no hue to turn, use teal
        for (int i = 0; i < seg; i++) {
            float u0 = i / (float) seg, u1 = (i + 1) / (float) seg;
            float[][] bottom = new float[2][], top = new float[2][];
            float[] us = {u0, u1};
            int[] cols = new int[2];
            float[] fade = new float[2];
            for (int k = 0; k < 2; k++) {
                float u = us[k];
                double az = Math.toRadians(-70 + 140 * u) + 0.08 * Math.sin(time * 0.6 + u * 9);   // around north (-Z)
                double wave = 0.06 * Math.sin(time * 0.9 + u * 14) + 0.03 * Math.sin(time * 1.7 + u * 31);
                double low = Math.toRadians(8), high = Math.toRadians(30 + 8 * Math.sin(time * 0.5 + u * 7));
                double x = Math.sin(az), z = -Math.cos(az);
                bottom[k] = new float[]{(float) (x * Math.cos(low) * d + wave * d), (float) (Math.sin(low) * d), (float) (z * Math.cos(low) * d)};
                top[k] = new float[]{(float) (x * Math.cos(high) * d + wave * d * 1.4), (float) (Math.sin(high) * d), (float) (z * Math.cos(high) * d)};
                cols[k] = Shapes.lerp(c1, c2, 0.5f + 0.5f * (float) Math.sin(time * 0.4 + u * 6));
                float edge = Math.min(u, 1 - u) * 6f;
                fade[k] = Math.min(1f, edge) * (0.6f + 0.4f * (float) Math.sin(time * 1.3 + u * 20));
            }
            quad(bottom[0], bottom[1], top[1], top[0],
                    argb(cols[0], alpha * fade[0]), argb(cols[1], alpha * fade[1]), argb(cols[1], 0f), argb(cols[0], 0f));
        }
    }

    private void shootingStar(float d, float alpha, long now) {
        if (starStart < 0 && now > nextStar) {
            starStart = now;
            starYaw = random.nextFloat() * (float) Math.PI * 2;
            starPitch = (float) Math.toRadians(35 + random.nextFloat() * 35);
            starDir = random.nextBoolean() ? 1 : -1;
        }
        if (starStart < 0) return;
        float t = (now - starStart) / 900f;
        if (t > 1f) {
            starStart = -1;
            nextStar = now + 4000 + random.nextInt(9000);
            return;
        }
        float a = alpha * (float) Math.sin(t * Math.PI);
        float len = 0.25f;
        float[] head = sphere(d * 0.95f, starYaw + starDir * t * 0.5f, starPitch - t * 0.25f);
        float[] tail = sphere(d * 0.95f, starYaw + starDir * (t * 0.5f - len), starPitch - (t - len * 2) * 0.25f);
        float w = d * 0.003f;
        quad(new float[]{head[0], head[1] + w, head[2]}, new float[]{tail[0], tail[1], tail[2]},
                new float[]{tail[0], tail[1], tail[2]}, new float[]{head[0], head[1] - w, head[2]},
                argb(0xFFFFFF, a), argb(HudStyle.accent(), 0f), argb(HudStyle.accent(), 0f), argb(0xFFFFFF, a));
    }

    private static float[] sphere(float d, float yaw, float pitch) {
        return new float[]{(float) (Math.cos(pitch) * Math.sin(yaw) * d), (float) (Math.sin(pitch) * d), (float) (-Math.cos(pitch) * Math.cos(yaw) * d)};
    }

    @Override
    public void onRender3D(LevelRenderContext context, Render3D render) {
        if (!overworld || mc.level == null) return;
        pos.clear();
        col.clear();
        long now = System.currentTimeMillis();
        float time = (now % 3_600_000L) / 1000f;
        // far behind the terrain, well inside the far plane (≥ 4 × render distance)
        float d = Math.max(96f, mc.options.getEffectiveRenderDistance() * 16f * 1.8f);
        float k = intensity.getFloat() / 100f * clear;
        if (ring.isOn()) {
            float a = k * (0.22f + 0.38f * night);
            int c1 = HudStyle.accent(), c2 = HudStyle.accent2();
            float yaw = time * 0.01f;
            band(d, (float) Math.toRadians(62), yaw, 0.11f, a, c1, c2);                       // wide glow
            band(d, (float) Math.toRadians(62), yaw, 0.018f, Math.min(1f, a * 1.8f), 0xFFFFFFFF, c1); // bright core
            band(d, (float) Math.toRadians(-48), -yaw * 0.7f + 1.3f, 0.035f, a * 0.6f, c2, c1); // second, thinner ring
        }
        if (aurora.isOn() && night > 0.05f) aurora(d, k * 0.55f * night, time);
        if (shootingStars.isOn() && night > 0.3f) shootingStar(d, k * night, now);
        if (col.isEmpty()) return;
        float[] p = pos.toFloatArray();
        int[] c = col.toIntArray();
        PoseStack poseStack = context.poseStack();
        context.submitNodeCollector().submitCustomGeometry(poseStack, QUADS, (pose, buffer) -> {
            for (int i = 0; i < c.length; i++) buffer.addVertex(pose, p[i * 3], p[i * 3 + 1], p[i * 3 + 2]).setColor(c[i]);
        });
    }
}
