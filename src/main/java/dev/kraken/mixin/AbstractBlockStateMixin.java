package dev.kraken.mixin;

import dev.kraken.module.smp.XRay;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * XRay: non-ore blocks report themselves as invisible. Works with the vanilla chunk builder
 * and with Fabric's Indigo renderer (both ask the state for its render type).
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin {
    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void kraken$xray(CallbackInfoReturnable<BlockRenderType> cir) {
        if (XRay.active() && !XRay.isVisible((BlockState) (Object) this)) {
            cir.setReturnValue(BlockRenderType.INVISIBLE);
        }
    }

    /** No ambient occlusion while XRay is on – ores surrounded by stone would otherwise be pitch black. */
    @Inject(method = "getAmbientOcclusionLightLevel", at = @At("HEAD"), cancellable = true)
    private void kraken$noAo(BlockView world, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (XRay.active()) cir.setReturnValue(1.0f);
    }
}
