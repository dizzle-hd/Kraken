package dev.nautilus.module.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.nautilus.hud.Anim;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.module.ModuleManager;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.ModeSetting;
import dev.nautilus.setting.NumberSetting;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * First-person hand and item animations: smoother swing curves and other swing styles, the swing
 * speed, a throw animation for throwables, a soft item switch instead of the hand dropping out of
 * view, a gentle idle sway and the item position/size. Purely visual – nothing is sent to the server.
 */
public class Animations extends Module {
    private final ModeSetting style = add(new ModeSetting("Swing", "Smooth = soft eased swing, Slide = diagonal cut, Stab = thrust forward, Spin = twirl, Vanilla = unchanged",
            "Smooth", "Smooth", "Slide", "Stab", "Spin", "Vanilla"));
    private final NumberSetting speed = add(new NumberSetting("Swing speed", "Speed of the swing animation in percent (lower = slower, smoother; only visual)", 85, 40, 200, 5));
    private final BoolSetting throwAnim = add(new BoolSetting("Throw", "Wind charges, pearls, snowballs, eggs and potions are thrown forward with a flick", true));
    private final ModeSetting switching = add(new ModeSetting("Item switch", "Smooth = the item glides in, Off = no animation, Vanilla = the hand dips down",
            "Smooth", "Smooth", "Off", "Vanilla"));
    private final BoolSetting sway = add(new BoolSetting("Idle sway", "The held item floats gently", true));
    private final NumberSetting x = add(new NumberSetting("X", "Item position: left / right", 0, -0.5, 0.5, 0.02));
    private final NumberSetting y = add(new NumberSetting("Y", "Item position: down / up", 0, -0.5, 0.5, 0.02));
    private final NumberSetting z = add(new NumberSetting("Z", "Item position: closer / further", 0, -0.5, 0.5, 0.02));
    private final NumberSetting size = add(new NumberSetting("Size", "Size of the held items in percent", 100, 50, 150, 5));

    private static long thrownAt;
    private static HumanoidArm thrownArm = HumanoidArm.RIGHT;

    public Animations() {
        super("Animations", "First-person animations: smooth swing styles and speed, a throw flick, soft item switching, idle sway and the item position.",
                Category.VISUALS, Items.BLAZE_ROD);
        loadEnabled(true);
    }

    private static Animations on() {
        Animations a = ModuleManager.get().get(Animations.class);
        return a != null && a.isEnabled() ? a : null;
    }

    private static boolean throwable(ItemStack s) {
        return s.is(Items.WIND_CHARGE) || s.is(Items.ENDER_PEARL) || s.is(Items.SNOWBALL) || s.is(Items.EGG) || s.is(Items.BLUE_EGG)
                || s.is(Items.BROWN_EGG) || s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION) || s.is(Items.EXPERIENCE_BOTTLE)
                || s.is(Items.FIRE_CHARGE);
    }

    /** LivingEntityMixin: the local player swung – remember throws for the flick. */
    public static void onSwing(LivingEntity who, net.minecraft.world.InteractionHand hand) {
        Animations a = on();
        if (a == null || who != mc.player || !a.throwAnim.isOn()) return;
        if (!throwable(mc.player.getItemInHand(hand))) return;
        thrownAt = System.currentTimeMillis();
        HumanoidArm main = mc.player.getMainArm();
        thrownArm = hand == net.minecraft.world.InteractionHand.MAIN_HAND ? main : main.getOpposite();
    }

    /** LivingEntityMixin: swing duration in ticks (visual only). */
    public static int swingDuration(int vanilla, LivingEntity who) {
        Animations a = on();
        if (a == null || who != mc.player) return vanilla;
        return Math.max(1, Math.round(vanilla * 100f / a.speed.getFloat()));
    }

    /** Equip progress (0 = fully raised): soft or no dip when switching items. */
    public static float equip(float vanilla) {
        Animations a = on();
        if (a == null) return vanilla;
        return switch (a.switching.get()) {
            case "Off" -> 0f;
            case "Smooth" -> Anim.easeInOutCubic(vanilla) * 0.35f;
            default -> vanilla;
        };
    }

    /** After vanilla placed the item: our offset, size and idle sway. */
    public static void afterArmTransform(PoseStack pose, HumanoidArm arm) {
        Animations a = on();
        if (a == null) return;
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        pose.translate(side * a.x.getFloat(), a.y.getFloat(), -a.z.getFloat());
        if (a.sway.isOn()) {
            float t = (System.currentTimeMillis() % 100_000L) / 1000f;
            pose.translate(Mth.sin(t * 1.3f) * 0.006f * side, Mth.sin(t * 2.2f) * 0.008f, 0f);
            pose.rotateDegrees(Axis.ZP, Mth.sin(t * 1.1f) * 0.8f * side);
        }
        float s = a.size.getFloat() / 100f;
        if (s != 1f) pose.scale(s, s, s);
    }

    /**
     * Replaces the vanilla swing rotation; returns false when vanilla should do it.
     * {@code p} is the swing progress 0-1.
     */
    public static boolean attackTransform(PoseStack pose, HumanoidArm arm, float p) {
        Animations a = on();
        if (a == null) return false;
        int i = arm == HumanoidArm.RIGHT ? 1 : -1;
        float throwT = (System.currentTimeMillis() - thrownAt) / 380f;
        if (a.throwAnim.isOn() && throwT < 1f && arm == thrownArm) {
            // flick forward and up, then back – like actually throwing it
            float f = Anim.bump(throwT);
            pose.translate(0f, 0.12f * f, -0.38f * f);
            pose.rotateDegrees(Axis.XP, -55f * f);
            pose.rotateDegrees(Axis.ZP, i * -10f * f);
            return true;
        }
        if (p <= 0f) return a.style.is("Vanilla") ? false : true;
        float g = Mth.sin((float) Math.PI * Anim.easeInOutCubic(p));   // smooth 0 → 1 → 0
        switch (a.style.get()) {
            case "Smooth" -> {
                pose.rotateDegrees(Axis.YP, i * (45f - 14f * g));
                pose.rotateDegrees(Axis.ZP, i * g * -16f);
                pose.rotateDegrees(Axis.XP, g * -62f);
                pose.rotateDegrees(Axis.YP, i * -45f);
            }
            case "Slide" -> {
                pose.translate(i * -0.08f * g, -0.12f * g, -0.05f * g);
                pose.rotateDegrees(Axis.ZP, i * -55f * g);
                pose.rotateDegrees(Axis.XP, -25f * g);
            }
            case "Stab" -> {
                pose.translate(0f, 0.04f * g, -0.42f * g);
                pose.rotateDegrees(Axis.XP, -12f * g);
            }
            case "Spin" -> pose.rotateDegrees(Axis.ZP, i * -360f * Anim.easeInOutCubic(p));
            default -> { return false; }
        }
        return true;
    }
}
