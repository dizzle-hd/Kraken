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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Surround: places blast-proof blocks around your feet so crystals cannot be placed next to you. */
public class Surround extends Module {
    private final ModeSetting blockMode = add(new ModeSetting("Block", "Welcher Block gesetzt wird", "Obsidian", "Obsidian", "Weinender Obsidian", "Beide"));
    private final NumberSetting perTick = add(new NumberSetting("Blöcke pro Tick", "Wie viele Blöcke pro Tick gesetzt werden", 2, 1, 4, 1));
    private final NumberSetting delay = add(new NumberSetting("Verzögerung", "Ticks zwischen zwei Durchgängen", 0, 0, 10, 1));
    private final BoolSetting center = add(new BoolSetting("Zentrieren", "Beim Einschalten per Tastendruck (W/A/S/D antippen, schleichend) zur Blockmitte laufen, damit alle vier Seiten frei sind", true));
    private final NumberSetting tapTicks = add(new NumberSetting("Tipp-Länge", "Wie viele Ticks eine Bewegungstaste beim Zentrieren gedrückt wird", 1, 1, 4, 1).visibleWhen(center::isOn));
    private final BoolSetting support = add(new BoolSetting("Stützblöcke", "Auch Blöcke unter den Ringblöcken setzen, falls dort Luft ist", true));
    private final BoolSetting doubleHeight = add(new BoolSetting("Doppelt", "Zweite Reihe auf Kopfhöhe (gegen Kristalle auf Kopfhöhe)", false));
    private final BoolSetting disableOnMove = add(new BoolSetting("Aus beim Verlassen", "Ausschalten, sobald du den Block verlässt oder springst", true));
    private final BoolSetting switchBack = add(new BoolSetting("Zurückwechseln", "Danach wieder den vorherigen Slot halten", true));

    private BlockPos start;
    /** Ticks left to walk to the block centre (0 = done). */
    private int centering;
    private int tap;
    private boolean pressing;
    private int wait;

    public Surround() {
        super("Surround", "Umgibt deine Füße mit Obsidian, damit niemand Kristalle direkt neben dir platzieren kann.", Category.CRYSTAL, Items.OBSIDIAN);
    }

    @Override
    protected void onEnable() {
        start = mc.player == null ? null : mc.player.blockPosition();
        wait = 0;
        centering = start != null && center.isOn() && mc.player.onGround() ? 30 : 0;
        tap = 0;
    }

    private boolean isBlock(ItemStack s) {
        return switch (blockMode.get()) {
            case "Obsidian" -> s.is(Items.OBSIDIAN);
            case "Weinender Obsidian" -> s.is(Items.CRYING_OBSIDIAN);
            default -> s.is(Items.OBSIDIAN) || s.is(Items.CRYING_OBSIDIAN);
        };
    }

    private List<BlockPos> positions(BlockPos feet) {
        List<BlockPos> list = new ArrayList<>();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = feet.relative(d);
            if (support.isOn() && WorldUtil.isReplaceable(p.below())) list.add(p.below());
            list.add(p);
            if (doubleHeight.isOn()) list.add(p.above());
        }
        return list;
    }

    @Override
    protected void onDisable() {
        releaseKeys();
        centering = 0;
    }

    private void releaseKeys() {
        if (!pressing) return;
        var o = mc.options;
        for (var k : new net.minecraft.client.KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyShift}) {
            k.setDown(com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.getKey(k.saveString()).getValue()));
        }
        pressing = false;
    }

    /** Offset from the player to the centre of the start block, x/z. */
    private double[] offset() {
        return new double[]{start.getX() + 0.5 - mc.player.getX(), start.getZ() + 0.5 - mc.player.getZ()};
    }

    private boolean centered() {
        double[] o = offset();
        return Math.abs(o[0]) <= 0.19 && Math.abs(o[1]) <= 0.19;
    }

    /**
     * Before the player moves this tick: walk towards the block centre by tapping the movement keys
     * (like pressing W / A / S / D shortly) while sneaking, so there is no overshoot.
     */
    @Override
    public void onPreTick() {
        if (centering <= 0 || mc.player == null || start == null) return;
        if (centered() || --centering <= 0 || mc.gui.screen() != null) {
            centering = 0;
            releaseKeys();
            return;
        }
        var o = mc.options;
        if (tap > 0) {
            tap--;
            if (tap == 0) {
                // release between taps so the player stops instead of running past the centre
                o.keyUp.setDown(false); o.keyDown.setDown(false); o.keyLeft.setDown(false); o.keyRight.setDown(false);
            }
            return;
        }
        double[] off = offset();
        double yaw = Math.toRadians(mc.player.getYRot());
        // forward = (-sin, cos), left = (cos, sin) for Minecraft's yaw
        double fwd = off[0] * -Math.sin(yaw) + off[1] * Math.cos(yaw);
        double left = off[0] * Math.cos(yaw) + off[1] * Math.sin(yaw);
        double tol = 0.12;
        o.keyShift.setDown(true);
        o.keyUp.setDown(fwd > tol);
        o.keyDown.setDown(fwd < -tol);
        o.keyLeft.setDown(left > tol);
        o.keyRight.setDown(left < -tol);
        pressing = true;
        tap = tapTicks.getInt() + 1;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null) return;
        BlockPos feet = mc.player.blockPosition();
        if (disableOnMove.isOn() && (start == null || !feet.equals(start) || mc.options.keyJump.isDown() && centering <= 0)) {
            setEnabled(false);
            return;
        }
        if (centering > 0) return; // place once the player stands in the middle
        if (wait > 0) { wait--; return; }
        int slot = InvUtil.hotbar(this::isBlock);
        if (slot < 0) return;
        int prev = InvUtil.selected();
        int placed = 0;
        for (BlockPos p : positions(feet)) {
            if (placed >= perTick.getInt()) break;
            if (!WorldUtil.isReplaceable(p) || WorldUtil.reach(p) > 4.5) continue;
            InvUtil.select(slot);
            if (WorldUtil.place(p, null)) placed++;
        }
        if (switchBack.isOn()) InvUtil.select(prev);
        if (placed > 0) wait = delay.getInt();
    }

    @Override
    public String getInfo() {
        if (mc.player == null) return null;
        int missing = 0;
        for (BlockPos p : positions(mc.player.blockPosition())) if (WorldUtil.isReplaceable(p)) missing++;
        return missing == 0 ? "sicher" : missing + " fehlen";
    }
}
