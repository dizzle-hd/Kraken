package dev.kraken.mixin;

import dev.kraken.module.smp.XRay;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** XRay: every block is lit at full brightness (ores inside stone have no light of their own). */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Inject(method = "getLightmapCoordinates(Lnet/minecraft/client/render/WorldRenderer$BrightnessGetter;Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)I",
            at = @At("HEAD"), cancellable = true)
    private static void kraken$fullLight(WorldRenderer.BrightnessGetter getter, BlockRenderView world, BlockState state, BlockPos pos,
                                         CallbackInfoReturnable<Integer> cir) {
        if (XRay.active()) cir.setReturnValue(0xF000F0);
    }
}
