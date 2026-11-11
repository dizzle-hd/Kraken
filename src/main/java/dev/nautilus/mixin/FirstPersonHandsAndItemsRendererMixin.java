package dev.nautilus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nautilus.module.visuals.ItemOutline;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Item outline: draw the glowing silhouette of a held item right before the item itself. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsAndItemsRendererMixin {
    @Redirect(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"),
            require = 0)
    private void nautilus$outline(ItemStackRenderState state, PoseStack pose, SubmitNodeCollector collector, int light, int overlay, int outline) {
        ItemOutline.submitSilhouette(state, pose, collector);
        state.submit(pose, collector, light, overlay, outline);
    }
}
