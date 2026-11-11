package dev.nautilus.module.player;

import dev.nautilus.hud.HudStyle;
import dev.nautilus.hud.Notifications;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.NumberSetting;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Warns (glass toast) when a piece of armour gets low or you are running out of totems. Read-only. */
public class ArmorAlert extends Module {
    private final NumberSetting threshold = add(new NumberSetting("Threshold %", "Warn as soon as a piece of armor drops below this value", 20, 5, 60, 5));
    private final BoolSetting totems = add(new BoolSetting("Totem warning", "Also warn when only a few totems are left in the inventory", true));
    private final NumberSetting minTotems = add(new NumberSetting("Min. totems", "Warn from this count on (incl. off hand)", 2, 0, 10, 1));

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] NAMES = {"Helmet", "Chestplate", "Leggings", "Boots"};

    private final boolean[] warned = new boolean[SLOTS.length];
    private boolean totemWarned;

    public ArmorAlert() {
        super("ArmorAlert", "Warns with a notification when your armor is almost broken or you are running out of totems.", Category.PLAYER, Items.IRON_CHESTPLATE);
    }

    private static int percent(ItemStack s) {
        if (s.isEmpty() || !s.isDamageableItem() || s.getMaxDamage() <= 0) return -1;
        return Math.round(100f * (s.getMaxDamage() - s.getDamageValue()) / s.getMaxDamage());
    }

    @Override
    protected void onEnable() {
        java.util.Arrays.fill(warned, false);
        totemWarned = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        for (int i = 0; i < SLOTS.length; i++) {
            int p = percent(mc.player.getItemBySlot(SLOTS[i]));
            boolean low = p >= 0 && p < threshold.getInt();
            if (low && !warned[i]) Notifications.push("Armor", NAMES[i] + " at " + p + " %", HudStyle.BAD, 4f);
            warned[i] = low; // re-arms once repaired or swapped
        }
        if (!totems.isOn() || mc.player.isCreative()) return;
        var inv = mc.player.getInventory();
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) count += inv.getItem(i).getCount();
        boolean low = count <= minTotems.getInt();
        if (low && !totemWarned) Notifications.push("Totems", count == 0 ? "No totems left!" : "Only " + count + (count == 1 ? " totem" : " totems") + " left", 0xFFFFD27A, 4f);
        totemWarned = low;
    }
}
