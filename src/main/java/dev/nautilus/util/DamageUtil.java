package dev.nautilus.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Estimates explosion damage (end crystal power 6, respawn anchor power 5) like vanilla does. */
public final class DamageUtil {
    private DamageUtil() {}

    public static final float CRYSTAL = 6f, ANCHOR = 5f;

    /**
     * Damage {@code target} would take from an explosion at {@code center}.
     *
     * @param ignore a block that is gone when the explosion happens (the anchor itself), may be null
     * @param extra  an additional block that blocks the rays (a shield that is about to be placed), may be null
     */
    public static float explosion(LivingEntity target, Vec3 center, float power, BlockPos ignore, BlockPos extra) {
        double diameter = power * 2.0;
        double dist = Math.sqrt(target.distanceToSqr(center)) / diameter;
        if (dist > 1.0) return 0f;
        double exposure = exposure(center, target.getBoundingBox(), ignore, extra);
        double impact = (1.0 - dist) * exposure;
        float damage = (float) ((impact * impact + impact) / 2.0 * 7.0 * diameter + 1.0);
        if (target instanceof Player) damage = scaleDifficulty(damage);
        return reduce(target, damage);
    }

    private static float scaleDifficulty(float damage) {
        Difficulty d = Minecraft.getInstance().level.getDifficulty();
        return switch (d) {
            case PEACEFUL -> 0f;
            case EASY -> Math.min(damage / 2f + 1f, damage);
            case HARD -> damage * 1.5f;
            default -> damage;
        };
    }

    /** Armor, toughness, protection enchantments and resistance. */
    private static float reduce(LivingEntity e, float damage) {
        float armor = e.getArmorValue();
        float toughness = (float) e.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float f = 2f + toughness / 4f;
        float g = Mth.clamp(armor - damage / f, armor * 0.2f, 20f);
        damage *= 1f - g / 25f;

        int epf = 0;
        try {
            var lookup = Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            var prot = lookup.getOrThrow(Enchantments.PROTECTION);
            var blast = lookup.getOrThrow(Enchantments.BLAST_PROTECTION);
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                var stack = e.getItemBySlot(slot);
                if (stack.isEmpty()) continue;
                epf += EnchantmentHelper.getItemEnchantmentLevel(prot, stack);
                epf += 2 * EnchantmentHelper.getItemEnchantmentLevel(blast, stack);
            }
        } catch (Exception ignored) {
        }
        damage *= 1f - Math.min(20, epf) / 25f;

        MobEffectInstance res = e.getEffect(MobEffects.RESISTANCE);
        if (res != null) damage *= Math.max(0f, 1f - (res.getAmplifier() + 1) * 0.2f);
        return Math.max(0f, damage);
    }

    /** Fraction of sample points of {@code box} that can see {@code center} (vanilla "seen percent"). */
    public static double exposure(Vec3 center, AABB box, BlockPos ignore, BlockPos extra) {
        Minecraft mc = Minecraft.getInstance();
        double sx = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double sy = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double sz = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
        double ox = (1.0 - Math.floor(1.0 / sx) * sx) / 2.0;
        double oz = (1.0 - Math.floor(1.0 / sz) * sz) / 2.0;
        if (sx < 0 || sy < 0 || sz < 0) return 0;
        int seen = 0, total = 0;
        for (double x = 0; x <= 1; x += sx) {
            for (double y = 0; y <= 1; y += sy) {
                for (double z = 0; z <= 1; z += sz) {
                    Vec3 p = new Vec3(Mth.lerp(x, box.minX, box.maxX) + ox, Mth.lerp(y, box.minY, box.maxY), Mth.lerp(z, box.minZ, box.maxZ) + oz);
                    if (clear(p, center, ignore, extra)) seen++;
                    total++;
                }
            }
        }
        return total == 0 ? 0 : (double) seen / total;
    }

    private static boolean clear(Vec3 from, Vec3 to, BlockPos ignore, BlockPos extra) {
        Minecraft mc = Minecraft.getInstance();
        if (extra != null && new AABB(extra).clip(from, to).isPresent()) return false;
        Vec3 start = from;
        for (int i = 0; i < 4; i++) {
            BlockHitResult hit = mc.level.clip(new ClipContext(start, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() == HitResult.Type.MISS) return true;
            if (ignore == null || !hit.getBlockPos().equals(ignore)) return false;
            // the ignored block (the anchor) is gone when it explodes: the ray continues behind it
            return true;
        }
        return true;
    }
}
