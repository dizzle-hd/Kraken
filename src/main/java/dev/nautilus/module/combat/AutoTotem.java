package dev.nautilus.module.combat;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nautilus.mixin.AbstractContainerScreenAccessor;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.ModeSetting;
import dev.nautilus.setting.NumberSetting;
import dev.nautilus.util.InvUtil;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Keeps a totem of undying in the off hand (always, or only when your health is low).
 * Like a player would do it: opens the inventory, puts the mouse on the totem and presses the
 * "swap with off hand" key (F by default, whatever it is bound to), then closes the inventory again.
 */
public class AutoTotem extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Always = keep a totem in the off hand, Health = only below the health threshold", "Always", "Always", "Health"));
    private final NumberSetting health = add(new NumberSetting("Health threshold", "Below this health (incl. absorption) the totem goes into the off hand", 12, 1, 36, 1)
            .visibleWhen(() -> mode.is("Health")));
    private final ModeSetting otherItem = add(new ModeSetting("Otherwise", "What goes into the off hand above the threshold", "Crystal", "Crystal", "Golden apple", "Nothing")
            .visibleWhen(() -> mode.is("Health")));
    private final BoolSetting openInventory = add(new BoolSetting("Open inventory", "Open the inventory and swap the totem with the off-hand key (F) – like by hand. Off = without a visible inventory", true));
    private final NumberSetting openDelay = add(new NumberSetting("Open delay", "Ticks between opening the inventory and pressing the key", 1, 0, 10, 1).visibleWhen(openInventory::isOn));
    private final NumberSetting closeDelay = add(new NumberSetting("Close delay", "Ticks between pressing the key and closing the inventory", 1, 0, 10, 1).visibleWhen(openInventory::isOn));
    private final NumberSetting randomDelay = add(new NumberSetting("Randomness", "Random extra ticks per step", 0, 0, 5, 1));
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks of pause after a swap", 0, 0, 20, 1));
    private final BoolSetting inInventory = add(new BoolSetting("In inventory", "Also swap while you have your inventory open yourself", true));

    private enum Step { IDLE, PRESS, CLOSE }

    private Step step = Step.IDLE;
    private int wait;
    private boolean openedByUs;
    private Item want;

    public AutoTotem() {
        super("AutoTotem", "Automatically puts a totem into your off hand – opens the inventory and presses the off-hand key (F).",
                Category.COMBAT, Items.TOTEM_OF_UNDYING);
        preset("Legit", LOW, "Open inventory, press F, with human pauses",
                "Open inventory", true, "Open delay", 2, "Close delay", 2, "Randomness", 2, "Delay", 2);
        preset("Normal", MEDIUM, "Open inventory, fast",
                "Open inventory", true, "Open delay", 1, "Close delay", 1, "Randomness", 1, "Delay", 0);
        preset("Rage", HIGH, "Invisible without inventory, instant",
                "Open inventory", false, "Randomness", 0, "Delay", 0);
    }

    @Override
    protected void onDisable() {
        if (step != Step.IDLE && openedByUs && mc.gui.screen() instanceof InventoryScreen) mc.gui.setScreen(null);
        step = Step.IDLE;
        openedByUs = false;
    }

    private int pause(int base) {
        int r = randomDelay.getInt();
        return base + (r > 0 ? ThreadLocalRandom.current().nextInt(r + 1) : 0);
    }

    /** Item the off hand should hold right now, null = nothing to do. */
    private Item wanted() {
        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        boolean needTotem = mode.is("Always") || hp <= health.get();
        Item w = needTotem ? Items.TOTEM_OF_UNDYING : switch (otherItem.get()) {
            case "Crystal" -> Items.END_CRYSTAL;
            case "Golden apple" -> Items.GOLDEN_APPLE;
            default -> null;
        };
        return w == null || mc.player.getOffhandItem().is(w) ? null : w;
    }

    private int findSlot(Item w) {
        int slot = InvUtil.inventory(w);
        if (slot < 0 && w == Items.GOLDEN_APPLE) slot = InvUtil.inventory(Items.ENCHANTED_GOLDEN_APPLE);
        return slot;
    }

    /** Pre-tick: interactions go out before the movement packet, like a real click (no "post" packets). */
    @Override
    public void onPreTick() {
        if (mc.player == null || mc.gameMode == null || mc.player.isCreative() || mc.player.isSpectator()) return;
        if (wait > 0) { wait--; return; }

        switch (step) {
            case IDLE -> {
                want = wanted();
                if (want == null || findSlot(want) < 0) return;
                if (!mc.player.inventoryMenu.getCarried().isEmpty()) return;
                if (mc.gui.screen() instanceof InventoryScreen) {
                    if (!inInventory.isOn()) return;
                    openedByUs = false;
                } else if (mc.gui.screen() != null) {
                    return; // a chest or another menu is open: different slot numbers
                } else if (openInventory.isOn()) {
                    mc.gui.setScreen(new InventoryScreen(mc.player));
                    openedByUs = true;
                    step = Step.PRESS;
                    wait = pause(openDelay.getInt());
                    return;
                } else {
                    // without a visible inventory: the same click the off-hand key sends
                    InvUtil.swapToOffhand(findSlot(want));
                    wait = pause(delay.getInt());
                    return;
                }
                step = Step.PRESS;
                onPreTick();
            }
            case PRESS -> {
                int slot = findSlot(want);
                if (!(mc.gui.screen() instanceof InventoryScreen screen) || slot < 0 || mc.player.getOffhandItem().is(want)) {
                    step = Step.CLOSE;
                    return;
                }
                pressSwapKey(screen, slot);
                step = Step.CLOSE;
                wait = pause(closeDelay.getInt());
            }
            case CLOSE -> {
                if (openedByUs && mc.gui.screen() instanceof InventoryScreen) mc.gui.setScreen(null);
                openedByUs = false;
                step = Step.IDLE;
                wait = pause(delay.getInt());
            }
        }
    }

    /** Hovers the totem slot and presses the "swap with off hand" key on the inventory screen. */
    private void pressSwapKey(InventoryScreen screen, int inventorySlot) {
        int menuSlot = inventorySlot < 9 ? 36 + inventorySlot : inventorySlot;
        Slot slot = mc.player.inventoryMenu.getSlot(menuSlot);
        ((AbstractContainerScreenAccessor) screen).nautilus$setHoveredSlot(slot);
        InputConstants.Key key = InputConstants.getKey(mc.options.keySwapOffhand.saveString());
        if (key.getType() == InputConstants.Type.KEYBOARD && key.getValue() >= 0) {
            screen.keyPressed(new KeyEvent(key.getValue(), 0, 0));
        } else {
            // off-hand key bound to a mouse button / unbound: send the same swap directly
            InvUtil.swapToOffhand(inventorySlot);
        }
        ((AbstractContainerScreenAccessor) screen).nautilus$setHoveredSlot(null);
    }

    @Override
    public String getInfo() {
        int n = 0;
        if (mc.player != null) {
            var inv = mc.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) n += inv.getItem(i).getCount();
        }
        return String.valueOf(n);
    }
}
