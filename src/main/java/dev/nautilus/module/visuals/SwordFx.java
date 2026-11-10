package dev.nautilus.module.visuals;

import dev.nautilus.fx.Effects;
import dev.nautilus.fx.Fx;
import dev.nautilus.hud.HudStyle;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.module.ModuleManager;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.NumberSetting;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Every swing with a weapon cuts a glowing crescent of air in front of you, alternating left/right
 * and slightly tilted; critical hits (while falling) get a bigger slash with sparks at the tip.
 */
public class SwordFx extends Module {
    private final NumberSetting size = add(new NumberSetting("Größe", "Radius des Luftschnitts in Blöcken", 1.6, 0.8, 3.0, 0.1));
    private final BoolSetting allItems = add(new BoolSetting("Alle Items", "Auch ohne Waffe (sonst nur Schwert, Axt, Speer, Streitkolben, Dreizack)", false));
    private final BoolSetting crits = add(new BoolSetting("Krit-Funken", "Größerer Schnitt mit Funken bei kritischen Treffern", true));

    private int dir = 1;
    private long lastSlash;

    public SwordFx() {
        super("Schwerthieb", "Jeder Schlag schneidet eine leuchtende Sichel durch die Luft – abwechselnd links/rechts, Krits mit Funken.",
                Category.VISUALS, Items.NETHERITE_SWORD);
    }

    private static boolean weapon(ItemStack s) {
        return s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(ItemTags.SPEARS) || s.is(Items.MACE) || s.is(Items.TRIDENT);
    }

    /** LivingEntityMixin: the local player swung its main hand. */
    public static void onSwing(LivingEntity who) {
        SwordFx m = ModuleManager.get().get(SwordFx.class);
        if (m == null || !m.isEnabled() || who != mc.player) return;
        m.slash();
    }

    private void slash() {
        var p = mc.player;
        long now = System.currentTimeMillis();
        if (now - lastSlash < 120) return; // mining swings every tick – only real swings
        if (mc.gameMode != null && mc.gameMode.isDestroying()) return;
        if (!allItems.isOn() && !weapon(p.getMainHandItem())) return;
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK && !weapon(p.getMainHandItem())) return;
        lastSlash = now;
        Vec3 eye = p.getEyePosition();
        Vec3 fwd = p.getLookAngle().normalize();
        Vec3 right = fwd.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(fwd).normalize();
        boolean crit = crits.isOn() && !p.onGround() && p.getDeltaMovement().y < 0 && !p.isInWater();
        float tilt = (float) Math.toRadians(-25 + Effects.RANDOM.nextFloat() * 50) * dir;
        float r = size.getFloat() * (crit ? 1.25f : 1f);
        int color = p.getMainHandItem().is(Items.MACE) ? 0xFFC23D : HudStyle.accent();
        Fx.add(Effects.slash(eye, fwd, right, up, tilt, dir, r, color, crit ? 1f : 0.85f, crit ? 380 : 300));
        if (crit) {
            Vec3 tip = eye.add(fwd.scale(r * 0.9));
            Effects.burst(tip, 12, 3f, 0xFFFFFF, color, 0.06f, 500);
            Fx.add(Effects.facingRing(tip, fwd, 0.1f, 0.8f, 0.08f, color, 0.9f, 300));
        }
        dir = -dir;
    }
}
