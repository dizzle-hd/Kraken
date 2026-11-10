package dev.nautilus.module.smp;

import dev.nautilus.hud.HudStyle;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.ModeSetting;
import dev.nautilus.setting.NumberSetting;
import dev.nautilus.util.Render3D;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/** Boxes (and optional tracers) around other players, visible through walls. */
public class PlayerESP extends Module {
    private final NumberSetting range = add(new NumberSetting("Reichweite", "Maximale Entfernung in Blöcken", 128, 16, 512, 16));
    private final ModeSetting colorMode = add(new ModeSetting("Farbe", "Akzent = HUD-Farbe, Leben = grün → rot, Distanz = nah rot, fern grün",
            "Akzent", "Akzent", "Leben", "Distanz"));
    private final BoolSetting tracers = add(new BoolSetting("Tracer", "Linie von der Bildschirmmitte zu jedem Spieler", false));
    private final BoolSetting invisible = add(new BoolSetting("Unsichtbare", "Auch unsichtbare Spieler anzeigen", true));

    private int count;

    public PlayerESP() {
        super("PlayerESP", "Zeigt andere Spieler als Box durch Wände – Farbe nach HUD-Farbe, Leben oder Distanz.", Category.SMP, Items.ENDER_EYE);
    }

    private int color(Player p, double dist) {
        return switch (colorMode.get()) {
            case "Leben" -> {
                float f = Math.max(0f, Math.min(1f, p.getHealth() / p.getMaxHealth()));
                yield dev.nautilus.gui.Shapes.lerp(HudStyle.BAD, HudStyle.GOOD, f);
            }
            case "Distanz" -> dev.nautilus.gui.Shapes.lerp(HudStyle.BAD, HudStyle.GOOD, (float) Math.min(1.0, dist / 48.0));
            default -> HudStyle.accent();
        };
    }

    @Override
    public void onRender3D(LevelRenderContext context, Render3D render) {
        count = 0;
        if (mc.level == null || mc.player == null) return;
        double max = range.get();
        for (Player p : mc.level.players()) {
            if (p == mc.player || !p.isAlive() || p.isSpectator()) continue;
            if (!invisible.isOn() && p.isInvisible()) continue;
            double dist = p.distanceTo(mc.player);
            if (dist > max) continue;
            count++;
            AABB box = p.getBoundingBox().inflate(0.05);
            int c = color(p, dist);
            render.box(box, c);
            if (tracers.isOn()) render.tracer(box.getCenter(), c & 0x99FFFFFF);
        }
    }

    @Override
    public String getInfo() { return count > 0 ? String.valueOf(count) : null; }
}
