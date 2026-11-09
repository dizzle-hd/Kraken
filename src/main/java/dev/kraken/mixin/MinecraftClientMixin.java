package dev.kraken.mixin;

import dev.kraken.module.ModuleManager;
import dev.kraken.module.combat.SilentAim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.MinecraftClient;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow
    protected int attackCooldown;

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void kraken$silentAim(CallbackInfoReturnable<Boolean> cir) {
        if (attackCooldown > 0) return;
        SilentAim aim = ModuleManager.get().get(SilentAim.class);
        if (aim != null && aim.isEnabled() && aim.handleAttack()) {
            cir.setReturnValue(false);
        }
    }
}
