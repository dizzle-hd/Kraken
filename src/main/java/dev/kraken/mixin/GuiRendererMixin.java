package dev.kraken.mixin;

import dev.kraken.gui.LiquidGlass;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right before the GUI is drawn the main target only holds the world: capture it for liquid glass. */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    @Inject(method = "draw", at = @At("HEAD"))
    private void kraken$captureForGlass(CallbackInfo ci) {
        LiquidGlass.prepareFrame();
    }
}
