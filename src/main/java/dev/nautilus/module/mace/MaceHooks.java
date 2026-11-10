package dev.nautilus.module.mace;

import dev.nautilus.module.ModuleManager;
import dev.nautilus.util.InvUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Called around every {@code MultiPlayerGameMode#attack}. Order inside one attack:
 * <ol>
 *   <li>StunSlam: switch to an axe and hit first (disables the raised shield),</li>
 *   <li>MaceSwap: switch to the best mace – the real attack then goes out with it,</li>
 *   <li>after the attack: back to the slot that was held before.</li>
 * </ol>
 * The slot changes are sent by {@code attack} itself ({@code ensureHasSentCarriedItem}), so the server
 * sees: slot axe, attack, slot mace, attack – all in the same tick.
 */
public final class MaceHooks {
    private MaceHooks() {}

    private static boolean busy;
    private static int restoreSlot = -1;

    public static void preAttack(Player player, Entity target) {
        Minecraft mc = Minecraft.getInstance();
        if (busy || player != mc.player || mc.gameMode == null || !(target instanceof LivingEntity living)) return;
        busy = true;
        try {
            int before = InvUtil.selected();

            StunSlam stun = ModuleManager.get().get(StunSlam.class);
            if (stun != null && stun.isEnabled()) {
                int axe = stun.axeSlot(living);
                if (axe >= 0) {
                    InvUtil.select(axe);
                    mc.gameMode.attack(player, target); // re-enters here, ignored because of 'busy'
                    stun.onStunned();
                    InvUtil.select(before); // the real hit goes out with what you held (or the mace below)
                }
            }

            MaceSwap swap = ModuleManager.get().get(MaceSwap.class);
            if (swap != null && swap.isEnabled()) {
                int mace = swap.maceSlot(living);
                if (mace >= 0) InvUtil.select(mace);
            }

            if (InvUtil.selected() != before && wantsSwitchBack()) restoreSlot = before;
        } finally {
            busy = false;
        }
    }

    public static void postAttack() {
        if (busy || restoreSlot < 0) return;
        if (Minecraft.getInstance().player != null) InvUtil.select(restoreSlot);
        restoreSlot = -1;
    }

    private static boolean wantsSwitchBack() {
        MaceSwap swap = ModuleManager.get().get(MaceSwap.class);
        return swap == null || !swap.isEnabled() || swap.switchBack();
    }
}
