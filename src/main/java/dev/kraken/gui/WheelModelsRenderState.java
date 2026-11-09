package dev.kraken.gui;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * All 3D models of one wheel frame. They are drawn together into a single picture-in-picture
 * texture that covers the whole screen (one renderer only owns one texture per frame).
 */
public record WheelModelsRenderState(int x0, int y0, int x1, int y1, List<Model> models,
                                     @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
        implements PictureInPictureRenderState {

    /**
     * @param x      GUI x of the model center
     * @param y      GUI y of the model center
     * @param size   edge length of the model in GUI pixels
     * @param yaw    rotation around the vertical axis (degrees)
     * @param pitch  tilt towards the viewer (degrees)
     */
    public record Model(ItemStack stack, float x, float y, float size, float yaw, float pitch) {}

    public WheelModelsRenderState(int width, int height, List<Model> models) {
        this(0, 0, width, height, models, null, PictureInPictureRenderState.getBounds(0, 0, width, height, null));
    }

    @Override
    public float scale() {
        return 1.0f;
    }
}
