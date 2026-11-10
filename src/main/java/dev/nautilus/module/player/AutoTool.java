package dev.nautilus.module.player;

import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.NumberSetting;
import dev.nautilus.util.InvUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** While you mine, holds the fastest tool from the hotbar for the targeted block, then switches back. */
public class AutoTool extends Module {
    private final BoolSetting switchBack = add(new BoolSetting("Zurückwechseln", "Nach dem Abbauen wieder den vorherigen Slot halten", true));
    private final BoolSetting saveTools = add(new BoolSetting("Werkzeug schonen", "Fast kaputte Werkzeuge nicht benutzen", true));
    private final NumberSetting minDurability = add(new NumberSetting("Min. Haltbarkeit", "Werkzeuge mit weniger Resthaltbarkeit werden übersprungen", 10, 1, 100, 1));
    private final BoolSetting keepSword = add(new BoolSetting("Nicht mit Schwert", "Nicht wechseln, solange du ein Schwert oder einen Streitkolben hältst", true));

    private int prevSlot = -1;

    public AutoTool() {
        super("AutoTool", "Nimmt beim Abbauen automatisch das schnellste Werkzeug aus der Hotbar und wechselt danach zurück.", Category.PLAYER, Items.DIAMOND_PICKAXE);
    }

    private boolean usable(ItemStack s) {
        if (!saveTools.isOn() || s.isEmpty() || !s.isDamageableItem()) return true;
        return s.getMaxDamage() - s.getDamageValue() > minDurability.getInt();
    }

    private void restore() {
        if (prevSlot >= 0 && switchBack.isOn()) InvUtil.select(prevSlot);
        prevSlot = -1;
    }

    @Override
    protected void onDisable() {
        if (mc.player != null) restore();
    }

    @Override
    public void onPreTick() {
        if (mc.player == null || mc.level == null) return;
        boolean mining = mc.gui.screen() == null && mc.options.keyAttack.isDown()
                && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK;
        if (!mining) {
            restore();
            return;
        }
        ItemStack held = mc.player.getMainHandItem();
        if (prevSlot < 0 && keepSword.isOn() && (held.is(net.minecraft.tags.ItemTags.SWORDS) || held.is(Items.MACE))) return;
        BlockState state = mc.level.getBlockState(((BlockHitResult) mc.hitResult).getBlockPos());
        if (state.isAir()) return;
        var inv = mc.player.getInventory();
        int cur = InvUtil.selected();
        int best = cur;
        float bestSpeed = usable(inv.getItem(cur)) ? inv.getItem(cur).getDestroySpeed(state) : 0f;
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (!usable(s)) continue;
            float speed = s.getDestroySpeed(state);
            if (speed > bestSpeed + 0.01f) { bestSpeed = speed; best = i; }
        }
        if (best != cur) {
            if (prevSlot < 0) prevSlot = cur;
            InvUtil.select(best);
        }
    }
}
