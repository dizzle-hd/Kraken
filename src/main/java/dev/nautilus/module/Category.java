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
    RENDER("Render", Items.ENDER_EYE),
    VISUALS("Visuals", Items.NETHER_STAR, true),
    MACE("Mace", Items.MACE),
    PLAYER("Player", Items.PLAYER_HEAD);

    private final String displayName;
    private final Item icon;
    private final boolean experimental;
    private ItemStack iconStack;

    Category(String displayName, Item icon) {
        this(displayName, icon, false);
    }

    Category(String displayName, Item icon, boolean experimental) {
        this.displayName = displayName;
        this.icon = icon;
        this.experimental = experimental;
    }

    /** Still experimental (shown in the wheel and on the website). */
    public boolean isExperimental() { return experimental; }

    public String getDisplayName() { return displayName; }

    public ItemStack getIcon() {
        if (iconStack == null) iconStack = new ItemStack(icon);
        return iconStack;
    }
}
