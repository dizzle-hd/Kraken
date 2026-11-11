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
import dev.nautilus.setting.ModeSetting;
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
 * Northern lights over the overworld: several glowing curtains with moving rays and folds, green at
 * the bottom edge fading into the HUD colour / violet at the top – plus shooting stars, more stars and
 * a sky tinted in the HUD colour.
 *
 * <p>Only ever on the sky itself: the curtains sit far behind the loaded terrain and are depth-tested
 * against everything (blocks, entities, clouds) – a block in front always covers them, and they are
 * never drawn on top of or through blocks.
 */
public class Sky extends Module {
    private final BoolSetting aurora = add(new BoolSetting("Northern lights", "Waving curtains of northern lights in the sky", true));
    private final ModeSetting time = add(new ModeSetting("Time", "When the northern lights are visible", "At night", "At night", "Always"));
    private final ModeSetting colors = add(new ModeSetting("Colors", "Colors of the northern lights", "Classic", "Classic", "HUD color", "Rainbow"));
    private final NumberSetting intensity = add(new NumberSetting("Strength", "Brightness of the northern lights", 70, 10, 100, 5));
    private final BoolSetting shootingStars = add(new BoolSetting("Shooting stars", "Now and then a shooting star – at night", true));
    private final NumberSetting stars = add(new NumberSetting("Stars", "More stars: already at dusk and brighter (0 = vanilla)", 40, 0, 100, 10));
    private final NumberSetting tint = add(new NumberSetting("Atmosphere", "Soft pastel haze in the HUD color: sky, sunset and fog are tinted (0 = vanilla)", 40, 0, 100, 5));

    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("nautilusclient", "pipeline/sky_fx"))
            .withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING)) // additive light, like a real aurora
            // reversed Z (far = 0): passes only where nothing is in front – i.e. only on the empty sky
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .build();
    private static final RenderType QUADS = RenderType.create("nautilusclient_sky_fx", RenderSetup.builder(PIPELINE).createRenderSetup());

    private static boolean overworld;
    private static float night, clear = 1f;

    private final FloatArrayList pos = new FloatArrayList();
    private final IntArrayList col = new IntArrayList();
    private final Random random = new Random();
    private long nextStar;
    private float starYaw, starPitch, starDir;
    private long starStart = -1;

    public Sky() {
        super("Sky", "Northern lights in the night sky, shooting stars, more stars and optionally a sky in the HUD colour – only on the sky, never on blocks.",
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
            float[] p = pastel(c.x(), c.y(), c.z());
            float k = t * 0.6f;
            state.skyColor = new Vector3f(c.x() + (p[0] - c.x()) * k, c.y() + (p[1] - c.y()) * k, c.z() + (p[2] - c.z()) * k);
        }
        if (t > 0 && state.sunriseAndSunsetColor != null) {
            var c = state.sunriseAndSunsetColor;
            float k = t * 0.5f;
            state.sunriseAndSunsetColor = new Vector4f(c.x() + (ar - c.x()) * k, c.y() + (ag - c.y()) * k, c.z() + (ab - c.z()) * k, c.w());
        }
        float st = s.stars.getFloat() / 100f;
        if (st > 0) state.starBrightness = Math.min(1f, Math.max(state.starBrightness * (1f + st), state.starBrightness + st * 0.15f * state.rainBrightness));
    }

    /** A light pastel of the accent with the brightness of the given colour (day stays bright, night dark). */
    private static float[] pastel(float r, float g, float b) {
        int acc = dev.nautilus.gui.Shapes.lerp(HudStyle.accent(), 0xFFFFFFFF, 0.45f);
        float ar = ((acc >> 16) & 0xFF) / 255f, ag = ((acc >> 8) & 0xFF) / 255f, ab = (acc & 0xFF) / 255f;
        float lum = 0.299f * r + 0.587f * g + 0.114f * b, alum = 0.299f * ar + 0.587f * ag + 0.114f * ab;
        float m = alum > 0.01f ? lum / alum : 0f;
        return new float[]{Math.min(1f, ar * m), Math.min(1f, ag * m), Math.min(1f, ab * m)};
    }

    /** FogRendererMixin: the fog (and the horizon) get the same pastel haze as the sky. */
    public static void tintFog(org.joml.Vector4f color) {
        Sky s = instance();
        if (s == null || !overworld) return;
        float t = s.tint.getFloat() / 100f;
        if (t <= 0) return;
        float[] p = pastel(color.x, color.y, color.z);
        float k = t * 0.55f;
        color.set(color.x + (p[0] - color.x) * k, color.y + (p[1] - color.y) * k, color.z + (p[2] - color.z) * k, color.w);
    }

    // ---- geometry (camera-relative, on a sphere far behind the terrain) ----------------------------

    private void vertex(float[] p, int c) {
        pos.add(p[0]); pos.add(p[1]); pos.add(p[2]); col.add(c);
    }

    private static int argb(int rgb, float a) {
        return ((int) (Math.max(0f, Math.min(1f, a)) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** Point on the sky sphere: azimuth (0 = north, clockwise) and elevation in radians. */
    private static float[] sky(float d, double az, double el) {
        return new float[]{(float) (Math.sin(az) * Math.cos(el) * d), (float) (Math.sin(el) * d), (float) (-Math.cos(az) * Math.cos(el) * d)};
    }

    /** Smooth pseudo-noise from a few sines: the folds and rays of the curtains. */
    private static double wave(double x, double t, double seed) {
        return Math.sin(x * 3.1 + t * 0.35 + seed) * 0.5 + Math.sin(x * 7.3 - t * 0.6 + seed * 2.1) * 0.3 + Math.sin(x * 17.0 + t * 0.9 + seed * 3.7) * 0.2;
    }

    private int[] palette(double u, float t) {
        return switch (colors.get()) {
            case "HUD color" -> new int[]{HudStyle.accent(), HudStyle.hueShift(HudStyle.accent(), 0.15f)};
            case "Rainbow" -> {
                float h = (float) ((u * 0.6 + t * 0.02) % 1.0);
                yield new int[]{net.minecraft.util.Mth.hsvToRgb(h, 0.75f, 1f), net.minecraft.util.Mth.hsvToRgb((h + 0.15f) % 1f, 0.6f, 1f)};
            }
            default -> new int[]{0x3DFFA0, Shapes.lerp(0xFF7A5CFF, 0xFFFF5AB4, (float) (0.5 + 0.5 * Math.sin(u * 4 + t * 0.1))) & 0xFFFFFF};
        };
    }

    /**
     * One curtain: follows an azimuth range at a base elevation; its lower edge folds and drifts, bright
     * vertical rays run along it. Each column is two quads: bright lower edge → mid → transparent top.
     */
    private void curtain(float d, double az0, double az1, double baseEl, double height, double seed, float alpha, float t) {
        int seg = 90;
        float[][] lo = new float[seg + 1][], mid = new float[seg + 1][], hi = new float[seg + 1][];
        int[] cLo = new int[seg + 1], cMid = new int[seg + 1], cHi = new int[seg + 1];
        for (int i = 0; i <= seg; i++) {
            double u = i / (double) seg;
            double az = az0 + (az1 - az0) * u + 0.05 * wave(u * 2, t, seed);
            double el = baseEl + Math.toRadians(4) * wave(u * 1.5, t * 0.8, seed + 5);
            double h = height * (0.75 + 0.35 * (0.5 + 0.5 * wave(u * 3, t * 0.5, seed + 9)));
            // rays: thin bright streaks moving along the curtain
            double rays = Math.pow(0.5 + 0.5 * Math.sin(u * 140 + t * 1.2 + seed * 13 + 2 * Math.sin(u * 31 + t * 0.4)), 3);
            float edge = (float) Math.min(1.0, Math.min(u, 1 - u) * 7);         // soft curtain ends
            float pulse = (float) (0.55 + 0.45 * (0.5 + 0.5 * wave(u * 2.3, t * 1.3, seed + 3)));
            float a = alpha * edge * pulse * (float) (0.45 + 0.55 * rays);
            int[] pal = palette(u, t);
            lo[i] = sky(d, az, el);
            mid[i] = sky(d, az + 0.02 * Math.sin(t * 0.3 + u * 9), el + h * 0.35);
            hi[i] = sky(d, az + 0.04 * Math.sin(t * 0.3 + u * 9), el + h);
            cLo[i] = argb(pal[0], a * 0.95f);
            cMid[i] = argb(Shapes.lerp(0xFF000000 | pal[0], 0xFF000000 | pal[1], 0.5f), a * 0.55f);
            cHi[i] = argb(pal[1], 0f);
        }
        for (int i = 0; i < seg; i++) {
            // thin glow below the lower edge
            float[] lo0b = new float[]{lo[i][0], lo[i][1] - d * 0.012f, lo[i][2]}, lo1b = new float[]{lo[i + 1][0], lo[i + 1][1] - d * 0.012f, lo[i + 1][2]};
            vertex(lo0b, cLo[i] & 0x00FFFFFF); vertex(lo1b, cLo[i + 1] & 0x00FFFFFF); vertex(lo[i + 1], cLo[i + 1]); vertex(lo[i], cLo[i]);
            vertex(lo[i], cLo[i]); vertex(lo[i + 1], cLo[i + 1]); vertex(mid[i + 1], cMid[i + 1]); vertex(mid[i], cMid[i]);
            vertex(mid[i], cMid[i]); vertex(mid[i + 1], cMid[i + 1]); vertex(hi[i + 1], cHi[i + 1]); vertex(hi[i], cHi[i]);
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
            nextStar = now + 5000 + random.nextInt(12000);
            return;
        }
        float a = alpha * (float) Math.sin(t * Math.PI);
        float[] head = sky(d * 0.95f, starYaw + starDir * t * 0.5f, starPitch - t * 0.25f);
        float[] tail = sky(d * 0.95f, starYaw + starDir * (t * 0.5f - 0.25f), starPitch - (t - 0.5f) * 0.25f);
        float w = d * 0.0025f;
        vertex(new float[]{head[0], head[1] + w, head[2]}, argb(0xFFFFFF, a));
        vertex(tail, 0);
        vertex(tail, 0);
        vertex(new float[]{head[0], head[1] - w, head[2]}, argb(0xFFFFFF, a));
    }

    @Override
    public void onRender3D(LevelRenderContext context, Render3D render) {
        if (!overworld || mc.level == null) return;
        pos.clear();
        col.clear();
        long now = System.currentTimeMillis();
        float t = (now % 3_600_000L) / 1000f;
        // far behind the loaded terrain, well inside the far plane (≥ 4 × render distance)
        float d = Math.max(96f, mc.options.getEffectiveRenderDistance() * 16f * 1.8f);
        float visible = time.is("Always") ? Math.max(0.35f, night) : night;
        float k = intensity.getFloat() / 100f * clear * visible;
        if (aurora.isOn() && k > 0.01f) {
            // three curtains: a big one in the north, two smaller, higher ones drifting around
            curtain(d, Math.toRadians(-80), Math.toRadians(70), Math.toRadians(14), Math.toRadians(26), 1.0, k * 0.85f, t);
            curtain(d, Math.toRadians(-40) + 0.1 * Math.sin(t * 0.02), Math.toRadians(110), Math.toRadians(30), Math.toRadians(20), 4.0, k * 0.55f, t * 1.1f);
            curtain(d, Math.toRadians(150), Math.toRadians(250), Math.toRadians(22), Math.toRadians(18), 7.0, k * 0.4f, t * 0.9f);
        }
        if (shootingStars.isOn() && night > 0.3f) shootingStar(d, intensity.getFloat() / 100f * night * clear, now);
        if (col.isEmpty()) return;
        float[] p = pos.toFloatArray();
        int[] c = col.toIntArray();
        PoseStack poseStack = context.poseStack();
        context.submitNodeCollector().submitCustomGeometry(poseStack, QUADS, (pose, buffer) -> {
            for (int i = 0; i < c.length; i++) buffer.addVertex(pose, p[i * 3], p[i * 3 + 1], p[i * 3 + 2]).setColor(c[i]);
        });
    }
}
