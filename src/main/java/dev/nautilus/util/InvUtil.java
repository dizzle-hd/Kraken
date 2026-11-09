package dev.nautilus.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** Hotbar / inventory helpers shared by the crystal modules. */
public final class InvUtil {
    private InvUtil() {}

    /** Hotbar slot (0-8) holding a matching item, the selected slot first; -1 if none. */
    public static int hotbar(Predicate<ItemStack> filter) {
        var inv = Minecraft.getInstance().player.getInventory();
        int sel = inv.getSelectedSlot();
        if (filter.test(inv.getItem(sel))) return sel;
        for (int i = 0; i < 9; i++) if (filter.test(inv.getItem(i))) return i;
        return -1;
    }

    public static int hotbar(Item item) {
        return hotbar(s -> s.is(item));
    }

    /** Inventory slot (0-35) holding the item, -1 if none. */
    public static int inventory(Item item) {
        var inv = Minecraft.getInstance().player.getInventory();
        for (int i = 0; i < 36; i++) if (inv.getItem(i).is(item)) return i;
        return -1;
    }

    public static int selected() {
        return Minecraft.getInstance().player.getInventory().getSelectedSlot();
    }

    /** Selects a hotbar slot (the server is told with the next interaction). */
    public static void select(int slot) {
        if (slot >= 0 && slot < 9) Minecraft.getInstance().player.getInventory().setSelectedSlot(slot);
    }

    /** Swaps an inventory slot (0-35) with the off hand using the player's inventory menu. */
    public static void swapToOffhand(int inventorySlot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        int menuSlot = inventorySlot < 9 ? 36 + inventorySlot : inventorySlot;
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, menuSlot, 40, ContainerInput.SWAP, mc.player);
    }
}
