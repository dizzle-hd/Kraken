package dev.kraken.mixin;

import dev.kraken.module.smp.XRay;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class BlockMixin {
    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
    private static void kraken$xray(BlockState state, BlockState otherState, Direction side, CallbackInfoReturnable<Boolean> cir) {
        if (XRay.active()) {
            // ore faces are always drawn, unless the neighbour is the same ore (keeps veins clean)
            cir.setReturnValue(XRay.isVisible(state) && otherState.getBlock() != state.getBlock());
        }
    }
}
