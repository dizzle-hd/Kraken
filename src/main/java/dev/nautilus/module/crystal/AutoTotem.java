package dev.nautilus.module.crystal;

import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.ModeSetting;
import dev.nautilus.setting.NumberSetting;
import dev.nautilus.util.InvUtil;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Keeps a totem of undying in the off hand (always, or only when your health is low). */
public class AutoTotem extends Module {
    private final ModeSetting mode = add(new ModeSetting("Modus", "Immer = Totem bleibt immer in der Zweithand, Leben = nur unter der Leben-Grenze", "Immer", "Immer", "Leben"));
    private final NumberSetting health = add(new NumberSetting("Leben-Grenze", "Unter diesem Leben (inkl. Absorption) kommt das Totem in die Zweithand", 12, 1, 36, 1)
            .visibleWhen(() -> mode.is("Leben")));
    private final ModeSetting otherItem = add(new ModeSetting("Sonst", "Was über der Grenze in die Zweithand kommt", "Kristall", "Kristall", "Goldapfel", "Nichts")
            .visibleWhen(() -> mode.is("Leben")));
    private final NumberSetting delay = add(new NumberSetting("Verzögerung", "Ticks zwischen zwei Wechseln", 0, 0, 10, 1));
    private final BoolSetting inInventory = add(new BoolSetting("Im Inventar", "Auch wechseln, während dein Inventar offen ist", true));

    private int wait;

    public AutoTotem() {
        super("AutoTotem", "Legt automatisch ein Totem der Unsterblichkeit in die Zweithand.", Category.CRYSTAL, Items.TOTEM_OF_UNDYING);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null || mc.player.isCreative() || mc.player.isSpectator()) return;
        if (wait > 0) { wait--; return; }
        // only with the player's own inventory menu (a chest etc. uses other slot numbers)
        if (mc.gui.screen() != null && !(inInventory.isOn() && mc.gui.screen() instanceof InventoryScreen)) return;
        if (!mc.player.inventoryMenu.getCarried().isEmpty()) return;

        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        boolean needTotem = mode.is("Immer") || hp <= health.get();
        Item want = needTotem ? Items.TOTEM_OF_UNDYING : switch (otherItem.get()) {
            case "Kristall" -> Items.END_CRYSTAL;
            case "Goldapfel" -> Items.GOLDEN_APPLE;
            default -> null;
        };
        if (want == null || mc.player.getOffhandItem().is(want)) return;
        int slot = InvUtil.inventory(want);
        if (slot < 0 && want == Items.GOLDEN_APPLE) slot = InvUtil.inventory(Items.ENCHANTED_GOLDEN_APPLE);
        if (slot < 0) return;
        InvUtil.swapToOffhand(slot);
        wait = delay.getInt();
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
