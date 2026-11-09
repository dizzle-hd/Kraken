package dev.nautilus.mixin;

import dev.nautilus.module.smp.XRay;
import net.minecraft.client.renderer.block.FluidRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidRenderer.class)
public abstract class FluidRendererMixin {
    @Inject(method = "tesselate", at = @At("HEAD"), cancellable = true)
    private void nautilus$hideFluid(CallbackInfo ci) {
        if (XRay.active() && !XRay.showFluids()) ci.cancel();
    }
}
