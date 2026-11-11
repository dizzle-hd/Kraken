package dev.nautilus.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Item outline: read the layers of a held item's render state. */
@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccessor {
    @Accessor("activeLayerCount")
    int nautilus$layerCount();

    @Accessor("layers")
    ItemStackRenderState.LayerRenderState[] nautilus$layers();
}
