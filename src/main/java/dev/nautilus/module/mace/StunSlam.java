package dev.nautilus.module.mace;

import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.util.InvUtil;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Stun slam: the target blocks with a shield → in the same tick first an axe hit (disables the shield
 * for 5 seconds), then your real hit (with MaceSwap: the mace smash). The blocked axe hit does no
 * damage, so the second hit is not reduced by the hurt cooldown.
 */
public class StunSlam extends Module {
    private final BoolSetting onlyFalling = add(new BoolSetting("Only while falling", "Only on a smash (fall height > 1.5), otherwise on every hit", true));
    private final BoolSetting checkFacing = add(new BoolSetting("Shield direction", "Only when the shield really faces you", true));

    private int count;

    public StunSlam() {
        super("StunSlam", "Target blocks with a shield: axe first (shield disabled), the mace hit in the same tick.",
                Category.MACE, Items.SHIELD);
    }

    @Override
    protected void onEnable() { count = 0; }

    void onStunned() { count++; }

    private static boolean isAxe(ItemStack s) {
        return !s.isEmpty() && s.is(ItemTags.AXES);
    }

    /** Hotbar slot of an axe to break the target's shield with first, or -1. */
    int axeSlot(LivingEntity target) {
        var p = mc.player;
        if (p == null || !target.isBlocking()) return -1;
        if (onlyFalling.isOn() && !MaceUtil.canSmash(p, MaceUtil.SMASH_THRESHOLD)) return -1;
        if (checkFacing.isOn()) {
            Vec3 toMe = p.position().subtract(target.position()).multiply(1, 0, 1);
            Vec3 look = target.getViewVector(1f).multiply(1, 0, 1);
            if (toMe.lengthSqr() > 1e-4 && look.lengthSqr() > 1e-4 && toMe.normalize().dot(look.normalize()) <= 0) return -1;
        }
        return InvUtil.hotbar(StunSlam::isAxe);
    }

    @Override
    public String getInfo() {
        return count > 0 ? String.valueOf(count) : null;
    }
}
