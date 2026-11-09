package dev.kraken.gui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

import java.util.Optional;

/**
 * "Liquid glass" for the GUI: right before the GUI is drawn, the world image is copied, scaled down
 * and blurred. Glass shapes then sample that texture with a shader that refracts the background at
 * their rim (lens effect), adds chromatic aberration, a specular rim light and a subtle grade.
 */
public final class LiquidGlass {
    private static final RenderPipeline BLIT = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("kraken", "pipeline/glass_blit"))
            .withVertexShader("core/screenquad")
            .withFragmentShader("core/blit_screen")
            .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();
    private static final RenderPipeline BLUR_H = blur("h");
    private static final RenderPipeline BLUR_V = blur("v");

    /** GUI pipeline of the glass shapes (vertex format POSITION_TEX_COLOR like textured GUI quads). */
    public static final RenderPipeline GLASS = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("kraken", "pipeline/liquid_glass"))
            .withVertexShader(Identifier.fromNamespaceAndPath("kraken", "core/liquid_glass"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("kraken", "core/liquid_glass"))
            .build();

    /** Must match BEVEL / OUTER in liquid_glass.fsh. */
    static final float BEVEL = 9f, OUTER = 2f;

    private static GpuTexture half, quarterA, quarterB;
    private static GpuTextureView halfView, quarterAView, quarterBView;
    private static int texW, texH;
    private static boolean requested;

    private LiquidGlass() {}

    /** Liquid glass on/off (setting of the HUD module). */
    public static boolean enabled() {
        dev.kraken.module.client.Hud hud = dev.kraken.module.ModuleManager.get().get(dev.kraken.module.client.Hud.class);
        return hud == null || hud.liquidGlass.isOn();
    }

    private static RenderPipeline blur(String dir) {
        return RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("kraken", "pipeline/glass_blur_" + dir))
                .withVertexShader("core/screenquad")
                .withFragmentShader(Identifier.fromNamespaceAndPath("kraken", "core/glass_blur_" + dir))
                .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .build();
    }

    private static GpuSampler linear() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    /** Makes sure the textures exist for the current window size and returns the blurred result. */
    private static GpuTextureView ensureTextures() {
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        int w = Math.max(1, main.width), h = Math.max(1, main.height);
        if (half == null || w != texW || h != texH) {
            close();
            var device = RenderSystem.getDevice();
            int usage = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC;
            half = device.createTexture("Kraken glass half", usage, GpuFormat.RGBA8_UNORM, Math.max(1, w / 2), Math.max(1, h / 2), 1, 1);
            quarterA = device.createTexture("Kraken glass blur A", usage, GpuFormat.RGBA8_UNORM, Math.max(1, w / 4), Math.max(1, h / 4), 1, 1);
            quarterB = device.createTexture("Kraken glass blur B", usage, GpuFormat.RGBA8_UNORM, Math.max(1, w / 4), Math.max(1, h / 4), 1, 1);
            halfView = device.createTextureView(half);
            quarterAView = device.createTextureView(quarterA);
            quarterBView = device.createTextureView(quarterB);
            texW = w;
            texH = h;
        }
        return quarterAView;
    }

    private static void close() {
        if (halfView != null) halfView.close();
        if (quarterAView != null) quarterAView.close();
        if (quarterBView != null) quarterBView.close();
        if (half != null) half.close();
        if (quarterA != null) quarterA.close();
        if (quarterB != null) quarterB.close();
        half = quarterA = quarterB = null;
        halfView = quarterAView = quarterBView = null;
    }

    /**
     * Called (via mixin) right before the GUI is drawn into the main target: at this moment the main
     * target only contains the world. Copies, downsamples and blurs it.
     */
    public static void prepareFrame() {
        if (!requested) return;
        requested = false;
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (main.getColorTextureView() == null) return;
        ensureTextures();
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        pass(encoder, BLIT, main.getColorTextureView(), halfView, "kraken glass downsample 1/2");
        pass(encoder, BLIT, halfView, quarterAView, "kraken glass downsample 1/4");
        for (int i = 0; i < 2; i++) {
            pass(encoder, BLUR_H, quarterAView, quarterBView, "kraken glass blur h");
            pass(encoder, BLUR_V, quarterBView, quarterAView, "kraken glass blur v");
        }
    }

    private static void pass(CommandEncoder encoder, RenderPipeline pipeline, GpuTextureView src, GpuTextureView dst, String label) {
        try (RenderPass pass = encoder.createRenderPass(() -> label, dst, Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", src, linear());
            pass.draw(3, 1, 0, 0);
        }
    }

    // =============================================================================================
    // Glass shapes
    // =============================================================================================

    /** A signed distance field in GUI px (negative inside). */
    @FunctionalInterface
    private interface Sdf {
        float at(float x, float y);
    }

    /**
     * A liquid-glass annular sector with rounded corners (same geometry as
     * {@link Shapes#roundedSector}). Angles in radians, clockwise from 12 o'clock.
     */
    public static void sector(GuiGraphicsExtractor ctx, float cx, float cy, float rIn, float rOut, float a0, float a1,
                              float gap, float corner, float alpha) {
        SectorShape shape = new SectorShape(cx, cy, rIn, rOut, a0, a1, gap, corner);
        float margin = OUTER + 1f;
        float r0 = Math.max(0, rIn - margin), r1 = rOut + margin;
        polar(ctx, shape::sdf, cx, cy, r0, r1, a0, a1, Math.min(BEVEL, (rOut - rIn) * 0.18f), alpha);
    }

    /** A liquid-glass disc. */
    public static void circle(GuiGraphicsExtractor ctx, float cx, float cy, float r, float alpha) {
        Sdf sdf = (x, y) -> (float) Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) - r;
        polar(ctx, sdf, cx, cy, 0, r + OUTER + 1f, 0, (float) (Math.PI * 2), Math.min(BEVEL, r * 0.25f), alpha);
    }

    /** A liquid-glass rounded rectangle (pills, cards). */
    public static void roundRect(GuiGraphicsExtractor ctx, float x1, float y1, float x2, float y2, float radius, float alpha) {
        float hw = (x2 - x1) / 2f, hh = (y2 - y1) / 2f, mx = x1 + hw, my = y1 + hh;
        float r = Math.min(radius, Math.min(hw, hh));
        Sdf sdf = (x, y) -> {
            float qx = Math.abs(x - mx) - (hw - r), qy = Math.abs(y - my) - (hh - r);
            float ox = Math.max(qx, 0), oy = Math.max(qy, 0);
            return (float) Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(qx, qy), 0f) - r;
        };
        float m = OUTER + 1f;
        float bevel = Math.min(BEVEL, Math.min(hw, hh) * 0.75f);
        float step = Math.max(0.75f, Math.min(1.5f, bevel / 3f));
        int cols = Math.max(2, (int) Math.ceil((x2 - x1 + 2 * m) / step));
        int rows = Math.max(2, (int) Math.ceil((y2 - y1 + 2 * m) / step));
        float[] xs = new float[(rows + 1) * (cols + 1)], ys = new float[xs.length];
        for (int i = 0; i <= rows; i++) {
            for (int j = 0; j <= cols; j++) {
                int k = i * (cols + 1) + j;
                xs[k] = x1 - m + (x2 - x1 + 2 * m) * j / cols;
                ys[k] = y1 - m + (y2 - y1 + 2 * m) * i / rows;
            }
        }
        emit(ctx, sdf, xs, ys, rows, cols, bevel, alpha, x1 - m, y1 - m, x2 + m, y2 + m);
    }

    private static void polar(GuiGraphicsExtractor ctx, Sdf sdf, float cx, float cy, float r0, float r1, float a0, float a1,
                              float bevel, float alpha) {
        float span = a1 - a0;
        float step = Math.max(0.75f, Math.min(1.5f, bevel / 3f));
        int radial = Math.max(8, (int) Math.ceil((r1 - r0) / step));
        int angular = Math.max(8, (int) Math.ceil(span * r1 / step));
        float[] xs = new float[(radial + 1) * (angular + 1)], ys = new float[xs.length];
        for (int i = 0; i <= radial; i++) {
            float r = r0 + (r1 - r0) * i / radial;
            for (int j = 0; j <= angular; j++) {
                float a = a0 + span * j / angular;
                int k = i * (angular + 1) + j;
                xs[k] = cx + (float) Math.sin(a) * r;
                ys[k] = cy - (float) Math.cos(a) * r;
            }
        }
        emit(ctx, sdf, xs, ys, radial, angular, bevel, alpha, cx - r1, cy - r1, cx + r1, cy + r1);
    }

    /**
     * Encodes signed distance + normal per grid vertex and submits the glass element. The distance is
     * rescaled so that this shape's bevel maps to the shader's fixed BEVEL; the shader converts the
     * refraction back to screen pixels with derivatives, so small pills bend proportionally less.
     */
    private static void emit(GuiGraphicsExtractor ctx, Sdf sdf, float[] xs, float[] ys, int rows, int cols, float bevel, float alpha,
                             float bx0, float by0, float bx1, float by1) {
        requested = true;
        GpuTextureView blurred = ensureTextures();
        Matrix3x2f pose = new Matrix3x2f(ctx.pose());
        float sw = ctx.guiWidth(), sh = ctx.guiHeight();
        float scale = BEVEL / Math.max(0.5f, bevel);
        int[] colors = new int[xs.length];
        float[] us = new float[xs.length], vs = new float[xs.length];
        Vector2f tmp = new Vector2f();
        int ai = (int) (Math.max(0f, Math.min(1f, alpha)) * 255f);
        for (int k = 0; k < xs.length; k++) {
            float x = xs[k], y = ys[k];
            float d = sdf.at(x, y) * scale;
            float e = 0.35f;
            float nx = sdf.at(x + e, y) - sdf.at(x - e, y);
            float ny = sdf.at(x, y + e) - sdf.at(x, y - e);
            float nl = (float) Math.sqrt(nx * nx + ny * ny);
            if (nl > 1e-5f) { nx /= nl; ny /= nl; }
            int enc = (int) (Math.max(0f, Math.min(1f, (d + BEVEL) / (BEVEL + OUTER))) * 255f);
            int gx = (int) ((nx * 0.5f + 0.5f) * 255f), gy = (int) ((ny * 0.5f + 0.5f) * 255f);
            colors[k] = (ai << 24) | (enc << 16) | (gx << 8) | gy;
            pose.transformPosition(tmp.set(x, y));
            us[k] = tmp.x / sw;
            vs[k] = 1f - tmp.y / sh;
        }
        ScreenRectangle bounds = new ScreenRectangle((int) Math.floor(bx0) - 1, (int) Math.floor(by0) - 1,
                (int) Math.ceil(bx1 - bx0) + 2, (int) Math.ceil(by1 - by0) + 2).transformMaxBounds(pose);
        ScreenRectangle scissor = ctx.scissorStack.peek();
        if (scissor != null) bounds = scissor.intersection(bounds);
        TextureSetup tex = TextureSetup.singleTexture(blurred, linear());
        ctx.guiRenderState.addGuiElement(new GlassState(pose, xs, ys, us, vs, colors, rows, cols, tex, scissor, bounds));
    }

    /** Signed distance (GUI px, negative inside) to a rounded annular sector. */
    private record SectorShape(float cx, float cy, float rIn, float rOut, float a0, float a1, float gap, float r) {
        float sdf(float x, float y) {
            float rr = Math.max(0.5f, Math.min(r, (rOut - rIn) / 2f - 0.5f));
            float ri = rIn + rr, ro = rOut - rr, off = gap / 2f + rr;
            float px = x - cx, py = y - cy;
            float rho = (float) Math.sqrt(px * px + py * py);
            // signed distances to the two (offset) side lines, positive inside
            float s0 = px * (float) Math.cos(a0) + py * (float) Math.sin(a0) - off;
            float s1 = -(px * (float) Math.cos(a1) + py * (float) Math.sin(a1)) - off;
            float dIn = ri - rho, dOut = rho - ro;
            // like a box SDF in (radial, side) coordinates
            float qr = Math.max(dIn, dOut);
            float qs = Math.max(-s0, -s1);
            float outside = (float) Math.sqrt(Math.max(qr, 0) * Math.max(qr, 0) + Math.max(qs, 0) * Math.max(qs, 0));
            float inside = Math.min(Math.max(qr, qs), 0f);
            return outside + inside - rr;
        }
    }

    private record GlassState(Matrix3x2fc pose, float[] xs, float[] ys, float[] us, float[] vs, int[] cols,
                              int radial, int angular, TextureSetup textureSetup,
                              @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
            implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer v) {
            int w = angular + 1;
            for (int i = 0; i < radial; i++) {
                for (int j = 0; j < angular; j++) {
                    int a = i * w + j, b = (i + 1) * w + j, c = (i + 1) * w + j + 1, d = i * w + j + 1;
                    // skip cells that are completely outside the shape
                    if (((cols[a] >> 16) & 0xFF) == 255 && ((cols[b] >> 16) & 0xFF) == 255
                            && ((cols[c] >> 16) & 0xFF) == 255 && ((cols[d] >> 16) & 0xFF) == 255) {
                        continue;
                    }
                    // same winding rule as Shapes.quad: never back-face culled
                    float area = cross(a, d) + cross(d, c) + cross(c, b) + cross(b, a);
                    if (area <= 0) {
                        vertex(v, a); vertex(v, d); vertex(v, c); vertex(v, b);
                    } else {
                        vertex(v, a); vertex(v, b); vertex(v, c); vertex(v, d);
                    }
                }
            }
        }

        private float cross(int p, int q) {
            return xs[p] * ys[q] - xs[q] * ys[p];
        }

        private void vertex(VertexConsumer v, int k) {
            v.addVertexWith2DPose(pose, xs[k], ys[k]).setUv(us[k], vs[k]).setColor(cols[k]);
        }

        @Override
        public RenderPipeline pipeline() {
            return GLASS;
        }
    }
}
