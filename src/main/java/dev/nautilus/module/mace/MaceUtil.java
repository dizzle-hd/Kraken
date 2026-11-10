package dev.nautilus.module.mace;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/** Mace maths, the same formulas as vanilla 26.3 ({@code MaceItem#getAttackDamageBonus}). */
public final class MaceUtil {
    private MaceUtil() {}

    /** A smash attack needs more than this fall distance (and no elytra). */
    public static final double SMASH_THRESHOLD = 1.5;
    private static final float MACE_BASE_DAMAGE = 6f;

    public static boolean isMace(ItemStack s) {
        return !s.isEmpty() && s.is(Items.MACE);
    }

    public static int level(ItemStack s, ResourceKey<Enchantment> key) {
        var mc = Minecraft.getInstance();
        if (s.isEmpty() || mc.level == null) return 0;
        try {
            var holder = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
            return EnchantmentHelper.getItemEnchantmentLevel(holder, s);
        } catch (Exception e) {
            return 0;
        }
    }

    public static int density(ItemStack s) { return level(s, Enchantments.DENSITY); }
    public static int breach(ItemStack s) { return level(s, Enchantments.BREACH); }
    public static int windBurst(ItemStack s) { return level(s, Enchantments.WIND_BURST); }

    /** Can the player smash right now (fall distance, no elytra)? */
    public static boolean canSmash(LivingEntity e, double minFall) {
        return e.fallDistance > Math.max(SMASH_THRESHOLD, minFall) && !e.isFallFlying();
    }

    /** Extra damage of a smash attack without enchantments: 4 per block up to 3, 2 per block up to 8, then 1. */
    public static double smashBonus(double fall) {
        if (fall <= SMASH_THRESHOLD) return 0;
        if (fall <= 3) return 4 * fall;
        if (fall <= 8) return 12 + 2 * (fall - 3);
        return 22 + fall - 8;
    }

    /**
     * Estimated damage of a smash with {@code mace} on {@code target} after armor: Density adds 0.5 per
     * level and block, Breach lowers the armor effectiveness by 15 % per level.
     */
    public static float estimate(ItemStack mace, LivingEntity target, double fall) {
        double dmg = MACE_BASE_DAMAGE + smashBonus(fall) + 0.5 * density(mace) * fall;
        float armor = target.getArmorValue() * Math.max(0f, 1f - 0.15f * breach(mace));
        float toughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float g = Mth.clamp(armor - (float) dmg / (2f + toughness / 4f), armor * 0.2f, 20f);
        return (float) dmg * (1f - g / 25f);
    }
}
