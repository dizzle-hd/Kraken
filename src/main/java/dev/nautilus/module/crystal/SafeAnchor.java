package dev.nautilus.module.crystal;

import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.ModeSetting;
import dev.nautilus.setting.NumberSetting;
import dev.nautilus.util.InvUtil;
import dev.nautilus.util.WorldUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Safe anchor: puts a shield block between you and a charged respawn anchor, so its explosion
 * hits the shield instead of you. Works on its own (for anchors you look at) and is used by the
 * AnchorMacro right before it detonates.
 */
public class SafeAnchor extends Module {
    final ModeSetting block = add(new ModeSetting("Schutzblock", "Welcher Block als Schild gesetzt wird", "Glowstone", "Glowstone", "Obsidian", "Beliebig"));
    final ModeSetting side = add(new ModeSetting("Seite", "Wo der Schild hin soll: zu dir hin (automatisch) oder zusätzlich auf Augenhöhe", "Zu dir", "Zu dir", "Zu dir + oben"));
    private final NumberSetting delay = add(new NumberSetting("Verzögerung", "Ticks zwischen zwei gesetzten Blöcken (alleine)", 1, 0, 10, 1));
    private final BoolSetting onlyCharged = add(new BoolSetting("Nur geladen", "Alleine nur geladene Anker absichern", true));
    private final BoolSetting switchBack = add(new BoolSetting("Zurückwechseln", "Danach wieder den vorherigen Slot halten", true));

    private int wait;

    public SafeAnchor() {
        super("SafeAnchor", "Setzt einen Schutzblock zwischen dich und den Anker, bevor er explodiert. Kombinierbar mit AnchorMacro.",
                Category.CRYSTAL, Items.GLOWSTONE);
    }

    /** Hotbar slot of a usable shield block, -1 if none. */
    int shieldSlot() {
        return InvUtil.hotbar(s -> isShield(s));
    }

    private boolean isShield(ItemStack s) {
        return switch (block.get()) {
            case "Glowstone" -> s.is(Items.GLOWSTONE);
            case "Obsidian" -> s.is(Items.OBSIDIAN) || s.is(Items.CRYING_OBSIDIAN);
            default -> s.getItem() instanceof BlockItem && !s.is(Items.RESPAWN_ANCHOR);
        };
    }

    /** Free positions next to the anchor that shield the player from it. */
    BlockPos[] shieldPositions(BlockPos anchor) {
        Vec3 eyes = mc.player.getEyePosition();
        Vec3 c = Vec3.atCenterOf(anchor);
        Direction toward = Direction.getApproximateNearest(eyes.x - c.x, 0, eyes.z - c.z);
        BlockPos main = anchor.relative(toward);
        if (side.is("Zu dir + oben")) return new BlockPos[]{main, main.above()};
        return new BlockPos[]{main};
    }

    /** First shield position that still needs a block, or null if the player is already shielded. */
    BlockPos missingShield(BlockPos anchor) {
        for (BlockPos p : shieldPositions(anchor)) {
            if (!WorldUtil.isReplaceable(p)) continue;
            // never build into yourself
            if (mc.player.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(p))) continue;
            return p;
        }
        return null;
    }

    /** Places one shield block for {@code anchor}. @return true if a block was placed. */
    boolean protect(BlockPos anchor) {
        BlockPos p = missingShield(anchor);
        if (p == null) return false;
        int slot = shieldSlot();
        if (slot < 0) return false;
        int prev = InvUtil.selected();
        InvUtil.select(slot);
        // never click the anchor itself while it can still be charged (glowstone would charge it)
        BlockState a = mc.level.getBlockState(anchor);
        boolean anchorClickable = a.is(Blocks.RESPAWN_ANCHOR) && a.getValue(RespawnAnchorBlock.CHARGE) >= RespawnAnchorBlock.MAX_CHARGES;
        boolean ok = WorldUtil.place(p, anchorClickable ? null : anchor);
        if (switchBack.isOn()) InvUtil.select(prev);
        return ok;
    }

    /** Pre-tick: interactions go out before the movement packet, like a real click (no "post" packets). */
    @Override
    public void onPreTick() {
        if (mc.player == null || mc.level == null || mc.gui.screen() != null) return;
        if (wait > 0) { wait--; return; }
        AnchorMacro macro = dev.nautilus.module.ModuleManager.get().get(AnchorMacro.class);
        if (macro != null && macro.isEnabled() && macro.busy()) return; // the macro asks for the shield itself
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockState s = mc.level.getBlockState(hit.getBlockPos());
        if (!s.is(Blocks.RESPAWN_ANCHOR)) return;
        if (onlyCharged.isOn() && s.getValue(RespawnAnchorBlock.CHARGE) == 0) return;
        if (protect(hit.getBlockPos())) wait = delay.getInt();
    }
}
