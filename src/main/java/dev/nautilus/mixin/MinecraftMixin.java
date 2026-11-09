package dev.nautilus.mixin;

import dev.nautilus.module.ModuleManager;
import dev.nautilus.module.combat.SilentAim;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow
    public int missTime;

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void nautilus$silentAim(CallbackInfoReturnable<Boolean> cir) {
        if (missTime > 0) return;
        SilentAim aim = ModuleManager.get().get(SilentAim.class);
        if (aim != null && aim.isEnabled() && aim.handleAttack()) {
            cir.setReturnValue(false);
        }
    }
}
