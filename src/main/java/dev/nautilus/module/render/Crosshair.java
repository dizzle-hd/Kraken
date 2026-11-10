package dev.nautilus.module.render;

import dev.nautilus.gui.Shapes;
import dev.nautilus.hud.HudStyle;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.module.ModuleManager;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.ModeSetting;
import dev.nautilus.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;

/** Custom crosshair instead of the vanilla one: style, size, gap, thickness, colour, outline, attack cooldown. */
public class Crosshair extends Module {
    private final ModeSetting style = add(new ModeSetting("Stil", "Form des Fadenkreuzes", "Kreuz", "Kreuz", "Kreuz + Punkt", "Punkt", "Kreis", "T"));
    private final NumberSetting size = add(new NumberSetting("Größe", "Länge der Striche bzw. Radius", 5, 1, 15, 0.5));
    private final NumberSetting gap = add(new NumberSetting("Abstand", "Lücke in der Mitte", 2, 0, 10, 0.5));
    private final NumberSetting thickness = add(new NumberSetting("Dicke", "Strichstärke", 1, 0.5, 4, 0.25));
    private final ModeSetting color = add(new ModeSetting("Farbe", "Farbe des Fadenkreuzes", "Weiß", "Weiß", "HUD-Farbe", "Grün", "Rot", "Cyan", "Gelb"));
    private final BoolSetting outline = add(new BoolSetting("Umrandung", "Dunkler Rand für bessere Sichtbarkeit", true));
    private final BoolSetting targetColor = add(new BoolSetting("Ziel-Farbe", "Rot, solange du auf ein Lebewesen zielst", true));
    private final BoolSetting dynamic = add(new BoolSetting("Dynamisch", "Lücke wird beim Laufen und Springen größer", false));
    private final BoolSetting cooldown = add(new BoolSetting("Cooldown", "Kleiner Balken mit der Angriffs-Abklingzeit unter dem Fadenkreuz", true));

    private float spread;

    public Crosshair() {
        super("Fadenkreuz", "Eigenes Fadenkreuz statt dem von Minecraft: Stil, Größe, Abstand, Dicke, Farbe, Umrandung und Cooldown-Anzeige.",
                Category.RENDER, Items.TARGET);
    }

    private int baseColor() {
        return switch (color.get()) {
            case "HUD-Farbe" -> HudStyle.accent();
            case "Grün" -> 0xFF5BE38A;
            case "Rot" -> 0xFFFF5A5A;
            case "Cyan" -> 0xFF22D3EE;
            case "Gelb" -> 0xFFFFD27A;
            default -> 0xFFFFFFFF;
        };
    }

    /** Called instead of the vanilla crosshair; returns false when vanilla should draw it. */
    public static boolean render(GuiGraphicsExtractor ctx) {
        Crosshair c = ModuleManager.get().get(Crosshair.class);
        if (c == null || !c.isEnabled() || mc.player == null) return false;
        if (!mc.options.getCameraType().isFirstPerson() || mc.gui.hud.isHidden()) return true;
        c.draw(ctx);
        return true;
    }

    private void draw(GuiGraphicsExtractor ctx) {
        Shapes.setPixelSize(1f / Math.max(1, mc.getWindow().getGuiScale()));
        float cx = ctx.guiWidth() / 2f, cy = ctx.guiHeight() / 2f;
        float target = 0f;
        if (dynamic.isOn()) {
            var v = mc.player.getDeltaMovement();
            target = (float) Math.min(6.0, Math.sqrt(v.x * v.x + v.z * v.z) * 14.0 + (mc.player.onGround() ? 0 : 3));
        }
        spread += (target - spread) * 0.25f;
        float g = gap.getFloat() + spread, l = size.getFloat(), t = thickness.getFloat();
        boolean onTarget = targetColor.isOn() && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity;
        int col = onTarget ? 0xFFFF5A5A : baseColor();
        Shapes s = new Shapes();
        String st = style.get();
        for (int pass = 0; pass < (outline.isOn() ? 2 : 1); pass++) {
            boolean back = outline.isOn() && pass == 0;
            float o = back ? 0.6f : 0f;
            int c = back ? 0xB0000000 : col;
            if (st.equals("Kreuz") || st.equals("Kreuz + Punkt") || st.equals("T")) {
                float h = t / 2f;
                if (!st.equals("T")) s.rect(cx - h - o, cy - g - l - o, cx + h + o, cy - g + o, c); // top
                s.rect(cx - h - o, cy + g - o, cx + h + o, cy + g + l + o, c);                     // bottom
                s.rect(cx - g - l - o, cy - h - o, cx - g + o, cy + h + o, c);                     // left
                s.rect(cx + g - o, cy - h - o, cx + g + l + o, cy + h + o, c);                     // right
            }
            if (st.equals("Punkt") || st.equals("Kreuz + Punkt")) s.circle(cx, cy, Math.max(0.8f, t) + o, c);
            if (st.equals("Kreis")) s.ring(cx, cy, l + g, t + o * 2f, c);
        }
        if (cooldown.isOn()) {
            float f = mc.player.getAttackStrengthScale(0f);
            if (f < 1f) {
                float w = 16f, y = cy + g + l + 5f;
                s.roundRect(cx - w / 2f, y, cx + w / 2f, y + 2f, 1f, 0x80000000);
                s.roundRect(cx - w / 2f, y, cx - w / 2f + w * f, y + 2f, 1f, col);
            }
        }
        s.submit(ctx);
    }
}
