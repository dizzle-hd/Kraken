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

    /**
     * A liquid-glass annular sector with rounded corners (same geometry as
     * {@link Shapes#roundedSector}). Angles in radians, clockwise from 12 o'clock.
     */
    public static void sector(GuiGraphicsExtractor ctx, float cx, float cy, float rIn, float rOut, float a0, float a1,
                              float gap, float corner, float alpha) {
        requested = true;
        GpuTextureView blurred = ensureTextures();
        Matrix3x2f pose = new Matrix3x2f(ctx.pose());
        float sw = ctx.guiWidth(), sh = ctx.guiHeight();
        SectorShape shape = new SectorShape(cx, cy, rIn, rOut, a0, a1, gap, corner);

        // polar grid that covers the shape plus the anti-aliasing margin
        float margin = OUTER + 1f;
        float r0 = Math.max(0, rIn - margin), r1 = rOut + margin;
        float span = a1 - a0;
        int radial = Math.max(8, (int) Math.ceil((r1 - r0) / 1.5f));
        int angular = Math.max(8, (int) Math.ceil(span * r1 / 1.5f));
        float[] xs = new float[(radial + 1) * (angular + 1)];
        float[] ys = new float[xs.length];
        int[] cols = new int[xs.length];
        float[] us = new float[xs.length], vs = new float[xs.length];
        Vector2f tmp = new Vector2f();
        int ai = (int) (Math.max(0f, Math.min(1f, alpha)) * 255f);
        for (int i = 0; i <= radial; i++) {
            float r = r0 + (r1 - r0) * i / radial;
            for (int j = 0; j <= angular; j++) {
                float a = a0 + span * j / angular;
                float x = cx + (float) Math.sin(a) * r, y = cy - (float) Math.cos(a) * r;
                int k = i * (angular + 1) + j;
                xs[k] = x;
                ys[k] = y;
                float d = shape.sdf(x, y);
                float e = 0.35f;
                float nx = shape.sdf(x + e, y) - shape.sdf(x - e, y);
                float ny = shape.sdf(x, y + e) - shape.sdf(x, y - e);
                float nl = (float) Math.sqrt(nx * nx + ny * ny);
                if (nl > 1e-5f) { nx /= nl; ny /= nl; }
                int enc = (int) (Math.max(0f, Math.min(1f, (d + BEVEL) / (BEVEL + OUTER))) * 255f);
                int gx = (int) ((nx * 0.5f + 0.5f) * 255f), gy = (int) ((ny * 0.5f + 0.5f) * 255f);
                cols[k] = (ai << 24) | (enc << 16) | (gx << 8) | gy;
                pose.transformPosition(tmp.set(x, y));
                us[k] = tmp.x / sw;
                vs[k] = 1f - tmp.y / sh;
            }
        }
        ScreenRectangle bounds = new ScreenRectangle((int) (cx - r1 - 1), (int) (cy - r1 - 1), (int) (2 * r1 + 2), (int) (2 * r1 + 2))
                .transformMaxBounds(pose);
        ScreenRectangle scissor = ctx.scissorStack.peek();
        if (scissor != null) bounds = scissor.intersection(bounds);
        TextureSetup tex = TextureSetup.singleTexture(blurred, linear());
        ctx.guiRenderState.addGuiElement(new GlassState(pose, xs, ys, us, vs, cols, radial, angular, tex, scissor, bounds));
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
