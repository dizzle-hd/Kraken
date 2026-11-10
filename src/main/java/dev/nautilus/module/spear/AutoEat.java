package dev.nautilus.module.spear;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.NumberSetting;
import dev.nautilus.util.InvUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;

/**
 * Eats automatically when the hunger bar gets low – Lunge needs at least 7 hunger and every Lunge
 * costs hunger. Holds the use key on the best food in the hotbar like a player, then switches back.
 */
public class AutoEat extends Module {
    private final NumberSetting threshold = add(new NumberSetting("Hunger-Grenze", "Essen, sobald der Hunger auf diesen Wert fällt (20 = voll, Lunge braucht 7)", 12, 1, 19, 1));
    private final BoolSetting gapples = add(new BoolSetting("Goldäpfel", "Auch Goldäpfel essen (sonst nur normales Essen)", false));
    private final BoolSetting pauseInFight = add(new BoolSetting("Nicht im Kampf", "Nicht essen, solange du die Angriffstaste hältst", true));
    private final BoolSetting switchBack = add(new BoolSetting("Zurückwechseln", "Danach wieder den vorherigen Slot halten", true));

    private static final Set<Item> BAD = Set.of(Items.ROTTEN_FLESH, Items.SPIDER_EYE, Items.POISONOUS_POTATO, Items.PUFFERFISH,
            Items.CHORUS_FRUIT, Items.SUSPICIOUS_STEW, Items.CHICKEN);

    private boolean eating;
    private int prevSlot = -1;
    private int ticks;

    public AutoEat() {
        super("AutoEat", "Isst automatisch, wenn der Hunger niedrig ist – damit Lunge und Sprinten weiter funktionieren.", Category.SPEAR, Items.COOKED_BEEF);
    }

    public boolean isEating() {
        return isEnabled() && eating;
    }

    private boolean edible(ItemStack s) {
        if (s.isEmpty() || s.get(DataComponents.FOOD) == null || BAD.contains(s.getItem())) return false;
        boolean gold = s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE);
        return !gold || gapples.isOn();
    }

    private void stop() {
        var use = mc.options.keyUse;
        use.setDown(InputConstants.isKeyDown(InputConstants.getKey(use.saveString()).getValue()));
        if (switchBack.isOn() && prevSlot >= 0) InvUtil.select(prevSlot);
        eating = false;
        prevSlot = -1;
    }

    @Override
    protected void onDisable() {
        if (eating && mc.player != null) stop();
    }

    @Override
    public void onPreTick() {
        if (mc.player == null || mc.gameMode == null) return;
        var p = mc.player;
        if (eating) {
            ticks++;
            // done (food eaten or full) or interrupted
            if (ticks > 60 || (ticks > 3 && !p.isUsingItem()) || mc.gui.screen() != null) stop();
            else mc.options.keyUse.setDown(true);
            return;
        }
        if (mc.gui.screen() != null || p.isCreative() || !p.getFoodData().needsFood()) return;
        if (p.getFoodData().getFoodLevel() > threshold.getInt()) return;
        if (pauseInFight.isOn() && mc.options.keyAttack.isDown()) return;
        int slot = InvUtil.hotbar(this::edible);
        if (slot < 0) return;
        prevSlot = InvUtil.selected();
        InvUtil.select(slot);
        mc.options.keyUse.setDown(true);
        eating = true;
        ticks = 0;
    }

    @Override
    public String getInfo() {
        return eating ? "isst" : null;
    }
}
