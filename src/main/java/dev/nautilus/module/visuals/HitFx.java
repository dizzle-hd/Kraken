package dev.nautilus.module.visuals;

import dev.nautilus.fx.Effects;
import dev.nautilus.fx.Fx;
import dev.nautilus.hud.HudStyle;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.module.ModuleManager;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.NumberSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Hits explode into glowing sparks with a ring that pops open towards you (gold for crits), and a
 * target you killed dissolves into rising rings and a column of light.
 */
public class HitFx extends Module {
    private final NumberSetting amount = add(new NumberSetting("Funken", "Funken pro Treffer", 12, 4, 40, 2));
    private final BoolSetting rings = add(new BoolSetting("Trefferring", "Ring, der sich am Treffpunkt zu dir hin öffnet", true));
    private final BoolSetting kills = add(new BoolSetting("Kill-Effekt", "Getötete Ziele lösen sich in aufsteigende Ringe und eine Lichtsäule auf", true));

    /** Entities you hit recently (id → time), to notice when they die. */
    private final Map<Integer, Long> recent = new HashMap<>();

    public HitFx() {
        super("Treffereffekte", "Treffer sprühen leuchtende Funken mit einem Ring (Krits in Gold), Kills lösen sich in aufsteigende Ringe und eine Lichtsäule auf.",
                Category.VISUALS, Items.BLAZE_POWDER);
    }

    /** CombatTracker: the local player attacked {@code target}. */
    public static void onHit(Entity target) {
        HitFx m = ModuleManager.get().get(HitFx.class);
        if (m == null || !m.isEnabled() || mc.player == null || !(target instanceof LivingEntity)) return;
        m.hit(target);
    }

    private void hit(Entity target) {
        var p = mc.player;
        boolean crit = !p.onGround() && p.getDeltaMovement().y < 0 && !p.isInWater() && p.getAttackStrengthScale(0f) > 0.9f;
        Vec3 eye = p.getEyePosition();
        var box = target.getBoundingBox();
        Vec3 c = box.getCenter();
        // the point of the hitbox facing you, a bit inside
        Vec3 point = new Vec3(Math.max(box.minX, Math.min(box.maxX, eye.x)), Math.max(box.minY + 0.2, Math.min(box.maxY - 0.1, eye.y - 0.2)),
                Math.max(box.minZ, Math.min(box.maxZ, eye.z))).lerp(c, 0.35);
        int c1 = crit ? 0xFFC23D : HudStyle.accent(), c2 = crit ? 0xFFF3B0 : 0xFFFFFF;
        Effects.burst(point, amount.getInt() * (crit ? 2 : 1), crit ? 3.2f : 2.4f, c1, c2, 0.06f, 520);
        if (rings.isOn()) {
            Vec3 n = eye.subtract(point);
            Fx.add(Effects.facingRing(point, n, 0.15f, crit ? 1.1f : 0.75f, 0.09f, c1, 0.95f, 320));
            if (crit) Fx.add(Effects.facingRing(point, n, 0.1f, 0.6f, 0.05f, 0xFFFFFF, 0.8f, 240));
        }
        recent.put(target.getId(), System.currentTimeMillis());
    }

    @Override
    public void onTick() {
        if (mc.level == null) { recent.clear(); return; }
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<Integer, Long>> it = recent.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Entity ent = mc.level.getEntity(e.getKey());
            if (now - e.getValue() > 6000) { it.remove(); continue; }
            boolean dead = ent == null || !ent.isAlive() || (ent instanceof LivingEntity le && le.isDeadOrDying());
            if (!dead) continue;
            it.remove();
            if (ent != null && kills.isOn()) kill(ent.position(), ent.getBbHeight());
        }
    }

    private static void kill(Vec3 feet, float height) {
        int c1 = HudStyle.accent(), c2 = HudStyle.accent2();
        for (int i = 0; i < 3; i++) {
            final int k = i;
            long delay = i * 120L;
            long start = System.currentTimeMillis() + delay;
            Fx.Effect ring = Effects.wave(feet.add(0, 0.1, 0), 0.4f, 1.3f, 0.12f, k == 1 ? 0xFFFFFF : c1, 0.9f, 900, height + 1.2f);
            Fx.add((fx, now) -> now < start || ring.draw(fx, now));
        }
        // column of light
        long born = System.currentTimeMillis();
        Fx.add((fx, now) -> {
            float t = (now - born) / 900f;
            if (t > 1f) return false;
            float a = (float) Math.sin(t * Math.PI) * 0.8f;
            for (int i = 0; i <= 8; i++) fx.glow(feet.add(0, i * (height + 1.5) / 8.0, 0), 0.5f * (1f - i / 10f), c2, a * (1f - i / 9f));
            return true;
        });
        for (int i = 0; i < 24; i++) {
            Vec3 start = feet.add(Effects.RANDOM.nextGaussian() * 0.3, Effects.RANDOM.nextFloat() * height, Effects.RANDOM.nextGaussian() * 0.3);
            Vec3 vel = new Vec3(Effects.RANDOM.nextGaussian() * 0.4, 1.5 + Effects.RANDOM.nextFloat() * 1.5, Effects.RANDOM.nextGaussian() * 0.4);
            Fx.add(Effects.spark(start, vel, i % 3 == 0 ? 0xFFFFFF : c1, 0.07f, 900 + Effects.RANDOM.nextInt(400), -0.5f));
        }
    }
}
