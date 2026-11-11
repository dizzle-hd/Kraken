package dev.nautilus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Item outline: the quads of one item layer and its model transform. */
@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState$LayerRenderState")
public interface LayerRenderStateAccessor {
    @Accessor("quads")
    ItemQuads nautilus$quads();

    @Invoker("applyTransform")
    void nautilus$applyTransform(PoseStack.Pose pose);
}
