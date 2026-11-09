package dev.kraken.gui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fc;

/** A batch of coloured quads (4 vertices each) submitted to the GUI render state. */
public record ShapeRenderState(Matrix3x2fc pose, float[] xy, int[] colors, int vertexCount,
                               @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
        implements GuiElementRenderState {

    @Override
    public void buildVertices(VertexConsumer vertexConsumer) {
        for (int i = 0; i < vertexCount; i++) {
            vertexConsumer.addVertexWith2DPose(pose, xy[i * 2], xy[i * 2 + 1]).setColor(colors[i]);
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.GUI;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }
}
