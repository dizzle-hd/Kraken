package dev.nautilus.module.render;

import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.NumberSetting;
import dev.nautilus.util.RotationUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Detaches the camera from the player: fly freely (WASD, space, shift, sprint = faster), also through
 * blocks. The player stands still and stays visible; the mouse turns the camera instead of the player.
 */
public class Freecam extends Module {
    private final NumberSetting speed = add(new NumberSetting("Speed", "Flight speed in blocks per second", 12, 1, 60, 1));
    private final NumberSetting sprintFactor = add(new NumberSetting("Sprint factor", "How much faster with the sprint key held", 3, 1, 10, 0.5));
    private final BoolSetting vertical = add(new BoolSetting("Look direction", "Forward flies where you look (also up/down), off = horizontal only", true));

    private static Freecam instance;

    private Vec3 pos = Vec3.ZERO;
    private float yaw, pitch;
    private long lastFrame;
    private Level world;

    public Freecam() {
        super("Freecam", "The camera detaches from the player and flies freely – also through blocks. Your player stays in place.", Category.RENDER, Items.ENDER_EYE);
        instance = this;
    }

    @Override
    protected void onEnable() {
        if (mc.player == null) { loadEnabled(false); return; }
        pos = mc.gameRenderer.mainCamera().position();
        yaw = mc.player.getYRot();
        pitch = mc.player.getXRot();
        world = mc.level;
        lastFrame = 0;
    }

    @Override
    public void onTick() {
        // leave freecam when the world changes or the player dies
        if (mc.level != world || mc.player == null || mc.player.isDeadOrDying()) setEnabled(false);
    }

    public static boolean active() {
        return instance != null && instance.isEnabled() && mc.player != null;
    }

    /** Mouse movement turns the free camera (same scale as Entity#turn). */
    public static void turn(double dx, double dy) {
        Freecam f = instance;
        f.yaw += (float) dx * 0.15f;
        f.pitch = Mth.clamp(f.pitch + (float) dy * 0.15f, -90f, 90f);
    }

    /** Called every frame from the camera: moves the camera and returns its position. */
    public static Vec3 updatePosition() {
        Freecam f = instance;
        long now = System.nanoTime();
        float dt = f.lastFrame == 0 ? 0 : (float) Math.min(0.1, (now - f.lastFrame) / 1e9);
        f.lastFrame = now;
        if (mc.gui.screen() == null && dt > 0) {
            var o = mc.options;
            double fwd = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
            double side = (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
            double up = (o.keyJump.isDown() ? 1 : 0) - (o.keyShift.isDown() ? 1 : 0);
            Vec3 forward = f.vertical.isOn() ? RotationUtil.direction(f.yaw, f.pitch) : RotationUtil.direction(f.yaw, 0);
            Vec3 left = RotationUtil.direction(f.yaw - 90f, 0);
            Vec3 move = forward.scale(fwd).add(left.scale(side)).add(0, up, 0);
            if (move.lengthSqr() > 1e-6) {
                double s = f.speed.get() * (o.keySprint.isDown() ? f.sprintFactor.get() : 1);
                f.pos = f.pos.add(move.normalize().scale(s * dt));
            }
        }
        return f.pos;
    }

    public static float yaw() { return instance.yaw; }
    public static float pitch() { return instance.pitch; }
}
