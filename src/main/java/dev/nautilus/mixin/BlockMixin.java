package dev.nautilus.mixin;

import dev.nautilus.module.smp.XRay;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class BlockMixin {
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private static void nautilus$xray(BlockState state, BlockState neighborState, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (XRay.active()) {
            // ore faces are always drawn, unless the neighbour is the same ore (keeps veins clean)
            cir.setReturnValue(XRay.isVisible(state) && neighborState.getBlock() != state.getBlock());
        }
    }
}
