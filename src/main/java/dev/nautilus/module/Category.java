package dev.nautilus.module;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Segments of the main wheel. Order = clockwise starting at the top. */
public enum Category {
    CLIENT("Client", Items.COMMAND_BLOCK),
    COMBAT("Combat", Items.DIAMOND_SWORD),
    SMP("SMP", Items.SPYGLASS),
    CRYSTAL("Crystal", Items.END_CRYSTAL),
    SPEAR("Spear", Items.IRON_SPEAR);

    private final String displayName;
    private final Item icon;
    private ItemStack iconStack;

    Category(String displayName, Item icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() { return displayName; }

    public ItemStack getIcon() {
        if (iconStack == null) iconStack = new ItemStack(icon);
        return iconStack;
    }
}
