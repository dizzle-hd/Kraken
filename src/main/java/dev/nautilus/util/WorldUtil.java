package dev.nautilus.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Block interaction helpers: right-click a block face, place a block, crystal placement rules. */
public final class WorldUtil {
    private WorldUtil() {}

    private static Minecraft mc() { return Minecraft.getInstance(); }

    /** Right-clicks {@code pos} on {@code face} with the item in {@code hand} (like a vanilla use). */
    public static boolean interact(BlockPos pos, Direction face, InteractionHand hand) {
        Minecraft mc = mc();
        if (mc.player == null || mc.gameMode == null) return false;
        Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        InteractionResult r = mc.gameMode.useItemOn(mc.player, hand, new BlockHitResult(hit, face, pos, false));
        if (r.consumesAction()) mc.player.swing(hand, mc.player.getItemInHand(hand).getInteractAnimation(), false);
        return r.consumesAction();
    }

    public static boolean isReplaceable(BlockPos pos) {
        return mc().level.getBlockState(pos).canBeReplaced();
    }

    /**
     * A solid neighbour of {@code pos} that can be clicked to place a block into {@code pos}, as
     * {neighbour, face}; {@code avoid} is never used (e.g. an anchor that would be charged instead).
     */
    public static Object[] placeSide(BlockPos pos, BlockPos avoid) {
        Vec3 eyes = mc().player.getEyePosition();
        Object[] best = null;
        double bestDist = Double.MAX_VALUE;
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (n.equals(avoid)) continue;
            BlockState s = mc().level.getBlockState(n);
            if (s.isAir() || s.canBeReplaced() || !s.getFluidState().isEmpty()) continue;
            double dist = eyes.distanceToSqr(Vec3.atCenterOf(n));
            if (dist < bestDist) {
                bestDist = dist;
                best = new Object[]{n, d.getOpposite()};
            }
        }
        return best;
    }

    /** Places the held block (main hand) into {@code pos}; false if there is nothing to click against. */
    public static boolean place(BlockPos pos, BlockPos avoid) {
        if (!isReplaceable(pos)) return false;
        Object[] side = placeSide(pos, avoid);
        if (side == null) return false;
        // no entity may stand in the way
        if (!mc().level.getEntities((Entity) null, new AABB(pos), e -> e.blocksBuilding).isEmpty()) return false;
        return interact((BlockPos) side[0], (Direction) side[1], InteractionHand.MAIN_HAND);
    }

    /** Vanilla end crystal rules: obsidian/bedrock, free block above, no entity in the 1x2x1 space above. */
    public static boolean canPlaceCrystal(BlockPos base) {
        BlockState s = mc().level.getBlockState(base);
        if (!s.is(Blocks.OBSIDIAN) && !s.is(Blocks.BEDROCK)) return false;
        BlockPos up = base.above();
        if (!mc().level.isEmptyBlock(up)) return false;
        AABB box = new AABB(up.getX(), up.getY(), up.getZ(), up.getX() + 1, up.getY() + 2, up.getZ() + 1);
        return mc().level.getEntities((Entity) null, box, e -> !e.isSpectator()).isEmpty();
    }

    /** Point on {@code pos} closest to the eyes (for reach checks). */
    public static double reach(BlockPos pos) {
        Vec3 eyes = mc().player.getEyePosition();
        return eyes.distanceTo(RotationUtil.closestPoint(eyes, new AABB(pos)));
    }
}
