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
    private final ModeSetting placeMode = add(new ModeSetting("Platzieren", "Mit Blick = Kamera dreht sich zu jedem Block und setzt ihn per Rechtsklick wie von Hand, Direkt = ohne hinzuschauen", "Mit Blick", "Mit Blick", "Direkt"));
    private final NumberSetting turnSpeed = add(new NumberSetting("Dreh-Tempo", "Wie schnell die Kamera zum nächsten Block schaut (Grad pro Sekunde)", 900, 180, 2000, 30).visibleWhen(() -> placeMode.is("Mit Blick")));
    private final NumberSetting smooth = add(new NumberSetting("Glätte", "Wie weich die Kamera dreht (höher = weicher)", 3, 1, 10, 0.5).visibleWhen(() -> placeMode.is("Mit Blick")));
    private final BoolSetting lookBack = add(new BoolSetting("Blick zurück", "Danach wieder dorthin schauen, wo du vorher hingeschaut hast", true).visibleWhen(() -> placeMode.is("Mit Blick")));

    private BlockPos start;
    /** Ticks left to walk to the block centre (0 = done). */
    private int centering;
    private int tap;
    private boolean pressing;
    private int wait;
    // "with view": the block that is being aimed at and the point on the face that gets clicked
    private BlockPos aimTarget;
    private net.minecraft.world.phys.Vec3 aimPoint;
    private float[] savedRot;
    private boolean returning;
    private long lastFrame;
    private int aimTicks;

    public Surround() {
        super("Surround", "Umgibt deine Füße mit Obsidian, damit niemand Kristalle direkt neben dir platzieren kann.", Category.CRYSTAL, Items.OBSIDIAN);
        preset("Legit", LOW, "Mit Blick, ruhige Kamera, ein Block nach dem anderen",
                "Platzieren", "Mit Blick", "Dreh-Tempo", 540, "Glätte", 4, "Blöcke pro Tick", 1, "Zentrieren", true);
        preset("Normal", MEDIUM, "Mit Blick, schnelle Kamera",
                "Platzieren", "Mit Blick", "Dreh-Tempo", 900, "Glätte", 3, "Blöcke pro Tick", 2, "Zentrieren", true);
        preset("Rage", HIGH, "Ohne hinzuschauen, alles auf einmal",
                "Platzieren", "Direkt", "Blöcke pro Tick", 4, "Zentrieren", true);
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
        aimTarget = null;
        aimPoint = null;
        returning = false;
        savedRot = null;
    }

    /** Every frame: turn the camera smoothly to the face that is clicked next (or back afterwards). */
    @Override
    public void onFrame() {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        if (mc.player == null || dt <= 0f || mc.gui.screen() != null) return;
        float[] want;
        if (aimPoint != null) want = dev.nautilus.util.RotationUtil.rotationsTo(mc.player.getEyePosition(), aimPoint);
        else if (returning && savedRot != null) want = savedRot;
        else return;
        float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
        if (aimPoint == null && dev.nautilus.util.RotationUtil.angleDifference(yaw, pitch, want) < 0.5f) {
            returning = false;
            savedRot = null;
            return;
        }
        float[] step = dev.nautilus.util.RotationUtil.smoothStep(yaw, pitch, want, 40f / smooth.getFloat(), turnSpeed.getFloat(), dt);
        mc.player.turn(step[0] / 0.15, step[1] / 0.15);
    }

    /** What the crosshair hits right now (fresh ray from the current camera rotation). */
    private net.minecraft.world.phys.BlockHitResult crosshair() {
        var eyes = mc.player.getEyePosition();
        var end = eyes.add(dev.nautilus.util.RotationUtil.direction(mc.player.getYRot(), mc.player.getXRot()).scale(4.5));
        return mc.level.clip(new net.minecraft.world.level.ClipContext(eyes, end, net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
    }

    /** Next free surround position that has something to click against, with the point to look at. */
    private boolean nextAim(BlockPos feet) {
        for (BlockPos p : positions(feet)) {
            if (!WorldUtil.isReplaceable(p) || WorldUtil.reach(p) > 4.5) continue;
            if (mc.player.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(p))) continue;
            Object[] side = WorldUtil.placeSide(p, null);
            if (side == null) continue;
            BlockPos n = (BlockPos) side[0];
            Direction face = (Direction) side[1];
            aimTarget = p;
            aimPoint = net.minecraft.world.phys.Vec3.atCenterOf(n).add(face.getStepX() * 0.499, face.getStepY() * 0.499, face.getStepZ() * 0.499);
            aimTicks = 0;
            return true;
        }
        return false;
    }

    /** "With view": place only when the crosshair really looks at a face that puts the block into the target. */
    private void placeWithView(BlockPos feet, int slot) {
        if (aimTarget != null && !WorldUtil.isReplaceable(aimTarget)) aimTarget = null;
        if (aimTarget == null && !nextAim(feet)) {
            // everything is placed: look back
            if (aimPoint != null) returning = lookBack.isOn();
            aimPoint = null;
            return;
        }
        if (savedRot == null) savedRot = new float[]{mc.player.getYRot(), mc.player.getXRot()};
        if (++aimTicks > 40) { aimTarget = null; return; } // cannot get a clear view: try the next one
        var hit = crosshair();
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return;
        if (!hit.getBlockPos().relative(hit.getDirection()).equals(aimTarget)) return;
        int prev = InvUtil.selected();
        InvUtil.select(slot);
        var r = mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        if (r.consumesAction()) mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, mc.player.getMainHandItem().getInteractAnimation(), false);
        if (switchBack.isOn()) InvUtil.select(prev);
        aimTarget = null;
        wait = delay.getInt();
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
        center();
        place();
    }

    private void center() {
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

    private void place() {
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
        if (placeMode.is("Mit Blick")) {
            placeWithView(feet, slot);
            return;
        }
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
