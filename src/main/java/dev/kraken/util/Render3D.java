package dev.kraken.util;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Collects ESP boxes / tracers during a frame and submits them as one custom geometry node
 * that renders through walls.
 */
public final class Render3D {
    private static final RenderPipeline ESP_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("kraken", "pipeline/esp_lines"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .build();
    private static final RenderType ESP_LINES = RenderType.create("kraken_esp_lines", RenderSetup.builder(ESP_PIPELINE).createRenderSetup());

    // x1 y1 z1 x2 y2 z2 per line (camera relative)
    private final FloatArrayList lines = new FloatArrayList();
    private final IntArrayList colors = new IntArrayList();
    private Vec3 cam = Vec3.ZERO;
    private float lineWidth = 2f;

    public void begin() {
        lines.clear();
        colors.clear();
        cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
    }

    public void end(LevelRenderContext context) {
        if (colors.isEmpty()) return;
        float[] data = lines.toFloatArray();
        int[] cols = colors.toIntArray();
        float width = lineWidth;
        PoseStack poseStack = context.poseStack();
        context.submitNodeCollector().submitCustomGeometry(poseStack, ESP_LINES, (pose, buffer) -> {
            for (int i = 0; i < cols.length; i++) {
                int o = i * 6;
                emit(pose, buffer, data[o], data[o + 1], data[o + 2], data[o + 3], data[o + 4], data[o + 5], cols[i], width);
            }
        });
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer buffer, float x1, float y1, float z1, float x2, float y2, float z2, int argb, float width) {
        float nx = x2 - x1, ny = y2 - y1, nz = z2 - z1;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-6f) { nx = 0; ny = 1; nz = 0; } else { nx /= len; ny /= len; nz /= len; }
        buffer.addVertex(pose, x1, y1, z1).setColor(argb).setNormal(pose, nx, ny, nz).setLineWidth(width);
        buffer.addVertex(pose, x2, y2, z2).setColor(argb).setNormal(pose, nx, ny, nz).setLineWidth(width);
    }

    public void lineWidth(float w) { this.lineWidth = w; }

    public void line(Vec3 a, Vec3 b, int argb) {
        line((float) (a.x - cam.x), (float) (a.y - cam.y), (float) (a.z - cam.z),
                (float) (b.x - cam.x), (float) (b.y - cam.y), (float) (b.z - cam.z), argb);
    }

    private void line(float x1, float y1, float z1, float x2, float y2, float z2, int argb) {
        lines.add(x1); lines.add(y1); lines.add(z1);
        lines.add(x2); lines.add(y2); lines.add(z2);
        colors.add(argb);
    }

    public void box(AABB box, int argb) {
        float x1 = (float) (box.minX - cam.x), y1 = (float) (box.minY - cam.y), z1 = (float) (box.minZ - cam.z);
        float x2 = (float) (box.maxX - cam.x), y2 = (float) (box.maxY - cam.y), z2 = (float) (box.maxZ - cam.z);
        line(x1, y1, z1, x2, y1, z1, argb); line(x2, y1, z1, x2, y1, z2, argb);
        line(x2, y1, z2, x1, y1, z2, argb); line(x1, y1, z2, x1, y1, z1, argb);
        line(x1, y2, z1, x2, y2, z1, argb); line(x2, y2, z1, x2, y2, z2, argb);
        line(x2, y2, z2, x1, y2, z2, argb); line(x1, y2, z2, x1, y2, z1, argb);
        line(x1, y1, z1, x1, y2, z1, argb); line(x2, y1, z1, x2, y2, z1, argb);
        line(x2, y1, z2, x2, y2, z2, argb); line(x1, y1, z2, x1, y2, z2, argb);
    }

    /** Line from slightly in front of the camera to {@code target}. */
    public void tracer(Vec3 target, int argb) {
        var camera = Minecraft.getInstance().gameRenderer.mainCamera();
        Vec3 look = Vec3.directionFromRotation(camera.xRot(), camera.yRot());
        line(cam.add(look), target, argb);
    }
}
