package dev.nautilus.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiny effect engine of the Visuals modules: additive glowing quads (soft glow sprites, rings,
 * ribbons, arcs) in world space. Hidden behind blocks, never written into the depth buffer.
 * Modules draw per-frame things in onRender3D through {@link #get()}, short-lived effects
 * (sparks, shock waves, slashes) are added with {@link #add} and animate themselves.
 */
public final class Fx {
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("nautilusclient", "pipeline/glow_fx"))
            .withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING)) // additive: overlapping glow gets brighter
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .build();
    private static final RenderType GLOW = RenderType.create("nautilusclient_glow_fx", RenderSetup.builder(PIPELINE).createRenderSetup());
    private static final int MAX_EFFECTS = 600;
    private static final Fx INSTANCE = new Fx();

    /** A short-lived effect; returns false once it is finished. */
    public interface Effect {
        boolean draw(Fx fx, long now);
    }

    private final List<Effect> effects = new ArrayList<>();
    private final FloatArrayList pos = new FloatArrayList();
    private final IntArrayList col = new IntArrayList();
    private Vec3 cam = Vec3.ZERO, left = new Vec3(1, 0, 0), up = new Vec3(0, 1, 0), forward = new Vec3(0, 0, 1);

    private Fx() {}

    public static Fx get() { return INSTANCE; }

    public static void add(Effect e) {
        if (INSTANCE.effects.size() < MAX_EFFECTS) INSTANCE.effects.add(e);
    }

    public static void clear() { INSTANCE.effects.clear(); }

    public Vec3 camera() { return cam; }
    public Vec3 cameraLeft() { return left; }
    public Vec3 cameraUp() { return up; }
    public Vec3 cameraForward() { return forward; }

    public void begin() {
        pos.clear();
        col.clear();
        var c = Minecraft.getInstance().gameRenderer.mainCamera();
        cam = c.position();
        left = new Vec3(c.leftVector().x(), c.leftVector().y(), c.leftVector().z());
        up = new Vec3(c.upVector().x(), c.upVector().y(), c.upVector().z());
        forward = new Vec3(c.forwardVector().x(), c.forwardVector().y(), c.forwardVector().z());
    }

    public void end(LevelRenderContext context) {
        long now = System.currentTimeMillis();
        effects.removeIf(e -> !e.draw(this, now));
        if (col.isEmpty()) return;
        float[] p = pos.toFloatArray();
        int[] c = col.toIntArray();
        PoseStack poseStack = context.poseStack();
        context.submitNodeCollector().submitCustomGeometry(poseStack, GLOW, (pose, buffer) -> {
            for (int i = 0; i < c.length; i++) buffer.addVertex(pose, p[i * 3], p[i * 3 + 1], p[i * 3 + 2]).setColor(c[i]);
        });
    }

    // ---- primitives (world coordinates) ---------------------------------------------------------

    public static int argb(int rgb, float a) {
        return ((int) (Math.max(0f, Math.min(1f, a)) * 255f) << 24) | (rgb & 0xFFFFFF);
    }

    private void vertex(Vec3 v, int color) {
        pos.add((float) (v.x - cam.x));
        pos.add((float) (v.y - cam.y));
        pos.add((float) (v.z - cam.z));
        col.add(color);
    }

    public void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int ca, int cb, int cc, int cd) {
        vertex(a, ca); vertex(b, cb); vertex(c, cc); vertex(d, cd);
    }

    /** Soft round glow facing the camera: bright centre fading to nothing at {@code size}. */
    public void glow(Vec3 p, float size, int rgb, float alpha) {
        if (alpha <= 0.003f || size <= 0) return;
        int center = argb(rgb, alpha), rim = argb(rgb, 0f);
        Vec3[] r = new Vec3[9];
        for (int i = 0; i <= 8; i++) {
            double a = i * Math.PI / 4;
            r[i] = p.add(left.scale(Math.cos(a) * size)).add(up.scale(Math.sin(a) * size));
        }
        for (int i = 0; i < 8; i += 2) quad(p, r[i], r[i + 1], r[i + 2], center, rim, rim, rim);
    }

    /** Glow with a small white-hot core – a "spark". */
    public void spark(Vec3 p, float size, int rgb, float alpha) {
        glow(p, size, rgb, alpha * 0.6f);
        glow(p, size * 0.32f, mix(rgb, 0xFFFFFF, 0.6f), alpha);
    }

    /**
     * Ring (or arc a0..a1, radians) of the given radius in the plane spanned by the unit vectors u, v,
     * with soft edges across its width.
     */
    public void ring(Vec3 center, Vec3 u, Vec3 v, float radius, float width, int rgb, float alpha, float a0, float a1, int seg) {
        if (alpha <= 0.003f) return;
        float[] offs = {-width / 2f, 0f, width / 2f};
        float[] al = {0f, alpha, 0f};
        for (int i = 0; i < seg; i++) {
            double t0 = a0 + (a1 - a0) * i / (double) seg, t1 = a0 + (a1 - a0) * (i + 1) / (double) seg;
            Vec3 d0 = u.scale(Math.cos(t0)).add(v.scale(Math.sin(t0))), d1 = u.scale(Math.cos(t1)).add(v.scale(Math.sin(t1)));
            for (int k = 0; k < 2; k++) {
                quad(center.add(d0.scale(radius + offs[k])), center.add(d1.scale(radius + offs[k])),
                        center.add(d1.scale(radius + offs[k + 1])), center.add(d0.scale(radius + offs[k + 1])),
                        argb(rgb, al[k]), argb(rgb, al[k]), argb(rgb, al[k + 1]), argb(rgb, al[k + 1]));
            }
        }
    }

    /** Flat horizontal ring around a point (e.g. at the feet). */
    public void flatRing(Vec3 center, float radius, float width, int rgb, float alpha) {
        ring(center, new Vec3(1, 0, 0), new Vec3(0, 0, 1), radius, width, rgb, alpha, 0f, (float) (Math.PI * 2), Math.max(24, (int) (radius * 28)));
    }

    /**
     * Ribbon along {@code pts}; {@code side[i]} is the half-width vector at point i, {@code alpha[i]}
     * its opacity (soft edges across the width).
     */
    public void ribbon(List<Vec3> pts, List<Vec3> side, int rgb, float[] alpha) {
        for (int i = 0; i + 1 < pts.size(); i++) {
            Vec3 p0 = pts.get(i), p1 = pts.get(i + 1), s0 = side.get(i), s1 = side.get(i + 1);
            int c0 = argb(rgb, alpha[i]), c1 = argb(rgb, alpha[i + 1]), z = argb(rgb, 0f);
            quad(p0.subtract(s0), p1.subtract(s1), p1, p0, z, z, c1, c0);
            quad(p0, p1, p1.add(s1), p0.add(s0), c0, c1, z, z);
        }
    }

    public static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }
}
