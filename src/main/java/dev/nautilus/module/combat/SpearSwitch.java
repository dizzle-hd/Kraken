package dev.nautilus.module.combat;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.module.ModuleManager;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.KeySetting;
import dev.nautilus.setting.ModeSetting;
import dev.nautilus.setting.NumberSetting;
import dev.nautilus.module.player.AutoEat;
import dev.nautilus.util.InvUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Spear switch: Lunge again and again. You stay on an empty (or other) hotbar slot; every few
 * ticks it switches to the spear, jabs (Lunge pushes you forward) and switches straight back – all
 * in the same tick, before the movement packet. The spear's long cooldown never starts, only the
 * short one of the empty hand counts (the known attribute-swap behaviour, MC-28289).
 * Lunge only works on foot (not riding, not gliding, not in water) with at least 7 hunger.
 */
public class SpearSwitch extends Module {
    private final ModeSetting trigger = add(new ModeSetting("Trigger", "On = lunges while the module is on (toggle with the module key), Hold = only while the lunge key is held, Attack = while the attack key is held", "On", "On", "Hold", "Attack"));
    private final KeySetting lungeKey = add(new KeySetting("Lunge key", "Key for the 'Hold' trigger", -1).visibleWhen(() -> trigger.is("Hold")));
    private final NumberSetting interval = add(new NumberSetting("Interval", "Ticks between two lunges (empty hand: 5 = fully charged)", 5, 1, 20, 1));
    private final NumberSetting random = add(new NumberSetting("Randomness", "Random extra ticks per lunge", 0, 0, 4, 1));
    private final ModeSetting park = add(new ModeSetting("Park on", "Which slot is held between the lunges", "Empty slot", "Empty slot", "Previous slot"));
    private final BoolSetting onlyLunge = add(new BoolSetting("Only with Lunge", "Only use spears with the Lunge enchantment", true));
    private final BoolSetting hungerGuard = add(new BoolSetting("Hunger guard", "Pause below 7 hunger (Lunge does not work there)", true));

    private int wait;
    private int startSlot = -1;

    public SpearSwitch() {
        super("SpearSwitch", "Lunge on repeat: swaps to the spear for each thrust and back. Toggle with the module key.",
                Category.COMBAT, Items.IRON_SPEAR);
        preset("Legit", LOW, "Full charge of the empty hand + randomness", "Interval", 6, "Randomness", 2);
        preset("Normal", MEDIUM, "Every 5 ticks, little randomness", "Interval", 5, "Randomness", 1);
        preset("Rage", HIGH, "As fast as possible", "Interval", 3, "Randomness", 0);
    }

    @Override
    protected void onEnable() {
        wait = 0;
        startSlot = mc.player == null ? -1 : InvUtil.selected();
    }

    @Override
    protected void onDisable() {
        if (mc.player != null && startSlot >= 0) InvUtil.select(startSlot);
        startSlot = -1;
    }

    private boolean isSpear(ItemStack s) {
        if (s.isEmpty() || s.get(DataComponents.PIERCING_WEAPON) == null) return false;
        if (!onlyLunge.isOn()) return true;
        try {
            var lunge = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LUNGE);
            return EnchantmentHelper.getItemEnchantmentLevel(lunge, s) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean active() {
        return switch (trigger.get()) {
            case "Hold" -> lungeKey.isBound() && InputConstants.isKeyDown(lungeKey.get());
            case "Attack" -> mc.options.keyAttack.isDown();
            default -> true;
        };
    }

    /** Pre-tick: switch, jab and switch back before the movement packet. */
    @Override
    public void onPreTick() {
        if (mc.player == null || mc.gameMode == null || mc.gui.screen() != null) return;
        if (wait > 0) { wait--; return; }
        if (!active()) return;
        AutoEat eat = ModuleManager.get().get(AutoEat.class);
        if (eat != null && eat.isEating()) return;
        var p = mc.player;
        if (p.isPassenger() || p.isFallFlying() || p.isInWater() || p.isUsingItem()) return;
        if (hungerGuard.isOn() && !p.isCreative() && p.getFoodData().getFoodLevel() < 7) return;

        int spear = InvUtil.hotbar(this::isSpear);
        if (spear < 0) return;
        int parkSlot = park.is("Empty slot") ? InvUtil.hotbar(ItemStack::isEmpty) : -1;
        if (parkSlot < 0) parkSlot = InvUtil.selected() != spear ? InvUtil.selected() : InvUtil.hotbar(s -> !isSpear(s));
        if (parkSlot < 0) return;

        ItemStack stack = p.getInventory().getItem(spear);
        var piercing = stack.get(DataComponents.PIERCING_WEAPON);
        if (piercing == null) return;
        InvUtil.select(spear);
        mc.gameMode.piercingAttack(stack.getAttackAnimation(), piercing);
        InvUtil.select(parkSlot);
        int r = random.getInt();
        wait = interval.getInt() - 1 + (r > 0 ? ThreadLocalRandom.current().nextInt(r + 1) : 0);
    }

    @Override
    public String getInfo() {
        return trigger.get();
    }
}
