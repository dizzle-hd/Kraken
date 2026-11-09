package dev.nautilus.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** Renders the rotating 3D item models and the 3D player head of the wheel. */
public class WheelModelsRenderer extends PictureInPictureRenderer<WheelModelsRenderState> {
    private final List<ItemStackRenderState> pool = new ArrayList<>();

    @Override
    public Class<WheelModelsRenderState> getRenderStateClass() {
        return WheelModelsRenderState.class;
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0f;
    }

    @Override
    protected void renderToTexture(WheelModelsRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
        // origin = screen centre, 1 unit = 1 GUI pixel; flip so +y is up and +z points to the viewer
        poseStack.scale(1.0f, -1.0f, -1.0f);
        float cx = (state.x0() + state.x1()) / 2f, cy = (state.y0() + state.y1()) / 2f;

        List<WheelModelsRenderState.Model> models = state.models();
        while (pool.size() < models.size()) pool.add(new ItemStackRenderState());
        for (int i = 0; i < models.size(); i++) {
            WheelModelsRenderState.Model m = models.get(i);
            ItemStackRenderState item = pool.get(i);
            mc.getItemModelResolver().updateForTopItem(item, m.stack(), ItemDisplayContext.FIXED, mc.level, null, i);
            if (item.isEmpty()) continue;

            // normalise every model to the same visual size and centre it on its pivot
            AABB box = item.getModelBoundingBox();
            double ext = Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize()));
            if (ext <= 1e-4) ext = 1;
            float s = (float) (m.size() / ext);

            poseStack.pushPose();
            poseStack.translate(m.x() - cx, -(m.y() - cy), 0f);
            poseStack.rotateDegrees(Axis.XP, m.pitch());
            poseStack.rotateDegrees(Axis.YP, m.yaw());
            poseStack.scale(s, s, s);
            poseStack.translate((float) -box.getCenter().x, (float) -box.getCenter().y, (float) -box.getCenter().z);
            item.submit(poseStack, collector, 15728880, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    @Override
    protected String getTextureLabel() {
        return "nautilus wheel models";
    }
}
