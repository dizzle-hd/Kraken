package dev.kraken.mixin;

import dev.kraken.module.smp.XRay;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** XRay: every block is lit at full brightness (ores inside stone have no light of their own). */
@Mixin(LightCoordsUtil.class)
public abstract class LightCoordsUtilMixin {
    @Inject(method = "getLightCoords(Lnet/minecraft/util/LightCoordsUtil$BrightnessGetter;Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            at = @At("HEAD"), cancellable = true)
    private static void kraken$fullLight(LightCoordsUtil.BrightnessGetter getter, BlockAndLightGetter level, BlockState state, BlockPos pos,
                                         CallbackInfoReturnable<Integer> cir) {
        if (XRay.active()) cir.setReturnValue(0xF000F0);
    }
}
