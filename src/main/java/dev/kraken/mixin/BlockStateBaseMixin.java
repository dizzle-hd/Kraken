package dev.kraken.mixin;

import dev.kraken.module.smp.XRay;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** XRay: non-ore blocks report themselves as invisible to the chunk compiler. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {
    @Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true)
    private void kraken$xray(CallbackInfoReturnable<RenderShape> cir) {
        if (XRay.active() && !XRay.isVisible((BlockState) (Object) this)) {
            cir.setReturnValue(RenderShape.INVISIBLE);
        }
    }

    /** No ambient occlusion while XRay is on – ores surrounded by stone would otherwise be pitch black. */
    @Inject(method = "getShadeBrightness", at = @At("HEAD"), cancellable = true)
    private void kraken$noAo(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (XRay.active()) cir.setReturnValue(1.0f);
    }
}
