package dev.nautilus.module.player;

import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.NumberSetting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Puts on the best armor from your inventory (armor + toughness + protection), one click sequence at
 * a time with a delay. Never takes off an elytra you are wearing (optional) or puts on cursed armor.
 */
public class AutoArmor extends Module {
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks between two armor pieces", 3, 0, 20, 1));
    private final BoolSetting keepElytra = add(new BoolSetting("Keep elytra", "Don't swap a worn elytra for a chestplate", true));
    private final BoolSetting blastPrio = add(new BoolSetting("Blast protection", "Rate blast protection higher (crystal PvP)", false));
    private final BoolSetting onlyStill = add(new BoolSetting("Only standing still", "Only swap while you are not moving", false));

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int[] MENU_SLOT = {5, 6, 7, 8}; // armor slots of the player's inventory menu

    private int timer;

    public AutoArmor() {
        super("AutoArmor", "Automatically puts on the best armor from your inventory (armor, toughness, protection enchantments).", Category.PLAYER, Items.DIAMOND_CHESTPLATE);
    }

    private double score(ItemStack st, EquipmentSlot slot) {
        if (st.isEmpty()) return -1;
        var eq = st.get(DataComponents.EQUIPPABLE);
        if (eq == null || eq.slot() != slot) return -1;
        var mods = st.get(DataComponents.ATTRIBUTE_MODIFIERS);
        double armor = mods == null ? 0 : mods.compute(Attributes.ARMOR, 0, slot);
        double tough = mods == null ? 0 : mods.compute(Attributes.ARMOR_TOUGHNESS, 0, slot);
        if (armor <= 0 && tough <= 0) return -1; // elytra, carved pumpkin, heads: no armor
        double score = armor * 10 + tough * 4;
        var lookup = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        score += 3 * EnchantmentHelper.getItemEnchantmentLevel(lookup.getOrThrow(Enchantments.PROTECTION), st);
        score += (blastPrio.isOn() ? 4 : 1) * EnchantmentHelper.getItemEnchantmentLevel(lookup.getOrThrow(Enchantments.BLAST_PROTECTION), st);
        if (st.isDamageableItem() && st.getMaxDamage() > 0) score += 0.5 * (st.getMaxDamage() - st.getDamageValue()) / st.getMaxDamage();
        return score;
    }

    private boolean cursed(ItemStack st) {
        var lookup = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        return EnchantmentHelper.getItemEnchantmentLevel(lookup.getOrThrow(Enchantments.BINDING_CURSE), st) > 0;
    }

    @Override
    public void onPreTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.player.isCreative()) return;
        if (timer-- > 0) return;
        // only with the player's own inventory (no chest open) and nothing on the cursor
        if (mc.player.containerMenu != mc.player.inventoryMenu || !mc.player.containerMenu.getCarried().isEmpty()) return;
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)) return;
        if (onlyStill.isOn() && mc.player.getDeltaMovement().horizontalDistanceSqr() > 1e-4) return;
        var inv = mc.player.getInventory();
        int id = mc.player.inventoryMenu.containerId;
        for (int k = 0; k < SLOTS.length; k++) {
            EquipmentSlot slot = SLOTS[k];
            ItemStack worn = mc.player.getItemBySlot(slot);
            if (!worn.isEmpty() && cursed(worn)) continue;
            if (slot == EquipmentSlot.CHEST && keepElytra.isOn() && worn.is(Items.ELYTRA)) continue;
            double wornScore = score(worn, slot);
            int best = -1;
            double bestScore = wornScore;
            for (int i = 0; i < 36; i++) {
                ItemStack st = inv.getItem(i);
                double sc = score(st, slot);
                if (sc > bestScore + 0.01 && !cursed(st)) { bestScore = sc; best = i; }
            }
            if (best < 0) continue;
            int menu = best < 9 ? 36 + best : best;
            if (worn.isEmpty()) {
                mc.gameMode.handleContainerInput(id, menu, 0, ContainerInput.QUICK_MOVE, mc.player);
            } else { // pick the new piece, swap it with the worn one, put the old one where the new one was
                mc.gameMode.handleContainerInput(id, menu, 0, ContainerInput.PICKUP, mc.player);
                mc.gameMode.handleContainerInput(id, MENU_SLOT[k], 0, ContainerInput.PICKUP, mc.player);
                mc.gameMode.handleContainerInput(id, menu, 0, ContainerInput.PICKUP, mc.player);
            }
            timer = delay.getInt();
            return; // one piece per step
        }
    }
}
