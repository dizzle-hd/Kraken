package dev.kraken.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fc;

/** A batch of coloured quads (4 vertices each) submitted to the GUI render state. */
public record ShapeRenderState(Matrix3x2fc pose, float[] xy, int[] colors, int vertexCount,
                               @Nullable ScreenRect scissorArea, @Nullable ScreenRect bounds)
        implements SimpleGuiElementRenderState {

    @Override
    public void setupVertices(VertexConsumer vertices) {
        for (int i = 0; i < vertexCount; i++) {
            vertices.vertex(pose, xy[i * 2], xy[i * 2 + 1]).color(colors[i]);
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.GUI;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.empty();
    }
}
