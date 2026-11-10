package dev.nautilus.mixin;

import dev.nautilus.module.mace.MaceHooks;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every attack (vanilla left click and all modules) passes through here. Before the attack the mace
 * modules may switch the hotbar slot – {@code attack} itself sends the carried item first – and switch
 * back afterwards.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "attack", at = @At("HEAD"))
    private void nautilus$preAttack(Player player, Entity target, CallbackInfo ci) {
        MaceHooks.preAttack(player, target);
    }

    @Inject(method = "attack", at = @At("RETURN"))
    private void nautilus$postAttack(Player player, Entity target, CallbackInfo ci) {
        MaceHooks.postAttack();
    }
}
