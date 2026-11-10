package dev.nautilus.mixin;

import dev.nautilus.module.ModuleManager;
import dev.nautilus.module.smp.Nametags;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nametags: hide the vanilla name above players while our glass nametags are shown. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("HEAD"), cancellable = true)
    private void nautilus$hideVanillaName(Avatar entity, double distanceSquared, CallbackInfoReturnable<Boolean> cir) {
        Nametags n = ModuleManager.get().get(Nametags.class);
        if (n != null && n.isEnabled()) cir.setReturnValue(false);
    }
}
