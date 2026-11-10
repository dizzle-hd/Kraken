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
    private final ModeSetting trigger = add(new ModeSetting("Auslöser", "An = lunget, solange das Modul an ist (per Modul-Taste toggeln), Halten = nur solange die Lunge-Taste gedrückt ist, Angriff = solange die Angriffstaste gedrückt ist", "An", "An", "Halten", "Angriff"));
    private final KeySetting lungeKey = add(new KeySetting("Lunge-Taste", "Taste für den Auslöser 'Halten'", -1).visibleWhen(() -> trigger.is("Halten")));
    private final NumberSetting interval = add(new NumberSetting("Intervall", "Ticks zwischen zwei Lunges (leere Hand: 5 = voll aufgeladen)", 5, 1, 20, 1));
    private final NumberSetting random = add(new NumberSetting("Zufall", "Zufällige Extra-Ticks pro Lunge", 0, 0, 4, 1));
    private final ModeSetting park = add(new ModeSetting("Parken auf", "Welcher Slot zwischen den Lunges gehalten wird", "Leerer Slot", "Leerer Slot", "Vorheriger Slot"));
    private final BoolSetting onlyLunge = add(new BoolSetting("Nur mit Lunge", "Nur Speere mit der Lunge-Verzauberung benutzen", true));
    private final BoolSetting hungerGuard = add(new BoolSetting("Hunger-Schutz", "Pausieren unter 7 Hunger (dort wirkt Lunge nicht)", true));

    private int wait;
    private int startSlot = -1;

    public SpearSwitch() {
        super("SpearSwitch", "Lunge im Dauerfeuer: wechselt pro Stoß kurz auf den Speer und zurück. Per Modul-Taste an/aus.",
                Category.COMBAT, Items.IRON_SPEAR);
        preset("Legit", LOW, "Volle Aufladung der leeren Hand + Zufall", "Intervall", 6, "Zufall", 2);
        preset("Normal", MEDIUM, "Alle 5 Ticks, wenig Zufall", "Intervall", 5, "Zufall", 1);
        preset("Rage", HIGH, "So schnell wie möglich", "Intervall", 3, "Zufall", 0);
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
            case "Halten" -> lungeKey.isBound() && InputConstants.isKeyDown(lungeKey.get());
            case "Angriff" -> mc.options.keyAttack.isDown();
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
        int parkSlot = park.is("Leerer Slot") ? InvUtil.hotbar(ItemStack::isEmpty) : -1;
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
