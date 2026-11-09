package dev.kraken.util;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/** Tiny immediate-mode helper for ESP boxes and tracers that render through walls. */
public final class Render3D {
    private static final RenderPipeline ESP_PIPELINE = RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation(Identifier.of("kraken", "pipeline/esp_lines"))
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build();
    private static final RenderLayer ESP_LINES = RenderLayer.of("kraken_esp_lines", RenderSetup.builder(ESP_PIPELINE).build());

    private final BufferAllocator allocator = new BufferAllocator(1 << 16);
    private final VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);
    private final MatrixStack matrices = new MatrixStack();
    private Vec3d cam = Vec3d.ZERO;
    private VertexConsumer lines;
    private float lineWidth = 2f;

    public void begin() {
        cam = MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos();
        lines = immediate.getBuffer(ESP_LINES);
    }

    public void end() {
        immediate.draw();
        lines = null;
    }

    public void lineWidth(float w) { this.lineWidth = w; }

    public void line(Vec3d a, Vec3d b, int argb) {
        line((float) (a.x - cam.x), (float) (a.y - cam.y), (float) (a.z - cam.z),
                (float) (b.x - cam.x), (float) (b.y - cam.y), (float) (b.z - cam.z), argb);
    }

    private void line(float x1, float y1, float z1, float x2, float y2, float z2, int argb) {
        MatrixStack.Entry e = matrices.peek();
        Vector3f n = new Vector3f(x2 - x1, y2 - y1, z2 - z1);
        if (n.lengthSquared() < 1e-8f) n.set(0, 1, 0); else n.normalize();
        lines.vertex(e, x1, y1, z1).color(argb).normal(e, n).lineWidth(lineWidth);
        lines.vertex(e, x2, y2, z2).color(argb).normal(e, n).lineWidth(lineWidth);
    }

    public void box(Box box, int argb) {
        float x1 = (float) (box.minX - cam.x), y1 = (float) (box.minY - cam.y), z1 = (float) (box.minZ - cam.z);
        float x2 = (float) (box.maxX - cam.x), y2 = (float) (box.maxY - cam.y), z2 = (float) (box.maxZ - cam.z);
        // bottom
        line(x1, y1, z1, x2, y1, z1, argb); line(x2, y1, z1, x2, y1, z2, argb);
        line(x2, y1, z2, x1, y1, z2, argb); line(x1, y1, z2, x1, y1, z1, argb);
        // top
        line(x1, y2, z1, x2, y2, z1, argb); line(x2, y2, z1, x2, y2, z2, argb);
        line(x2, y2, z2, x1, y2, z2, argb); line(x1, y2, z2, x1, y2, z1, argb);
        // pillars
        line(x1, y1, z1, x1, y2, z1, argb); line(x2, y1, z1, x2, y2, z1, argb);
        line(x2, y1, z2, x2, y2, z2, argb); line(x1, y1, z2, x1, y2, z2, argb);
    }

    /** Line from slightly in front of the camera to {@code target}. */
    public void tracer(Vec3d target, int argb) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Vec3d look = Vec3d.fromPolar(mc.gameRenderer.getCamera().getPitch(), mc.gameRenderer.getCamera().getYaw());
        line(cam.add(look), target, argb);
    }
}
