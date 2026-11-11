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
    private final ModeSetting style = add(new ModeSetting("Style", "Shape of the crosshair", "Cross", "Cross", "Cross + dot", "Dot", "Circle", "T"));
    private final NumberSetting size = add(new NumberSetting("Size", "Length of the lines or radius", 5, 1, 15, 0.5));
    private final NumberSetting gap = add(new NumberSetting("Gap", "Gap in the middle", 2, 0, 10, 0.5));
    private final NumberSetting thickness = add(new NumberSetting("Thickness", "Line thickness", 1, 0.5, 4, 0.25));
    private final ModeSetting color = add(new ModeSetting("Color", "Color of the crosshair", "White", "White", "HUD color", "Green", "Red", "Cyan", "Yellow"));
    private final BoolSetting outline = add(new BoolSetting("Outline", "Dark edge for better visibility", true));
    private final BoolSetting targetColor = add(new BoolSetting("Target color", "Red while you aim at a living entity", true));
    private final BoolSetting dynamic = add(new BoolSetting("Dynamic", "The gap grows while walking and jumping", false));
    private final BoolSetting cooldown = add(new BoolSetting("Cooldown", "Small bar with the attack cooldown below the crosshair", true));

    private float spread;

    public Crosshair() {
        super("Crosshair", "Your own crosshair instead of Minecraft's: style, size, gap, thickness, colour, outline and cooldown bar.",
                Category.RENDER, Items.TARGET);
    }

    private int baseColor() {
        return switch (color.get()) {
            case "HUD color" -> HudStyle.accent();
            case "Green" -> 0xFF5BE38A;
            case "Red" -> 0xFFFF5A5A;
            case "Cyan" -> 0xFF22D3EE;
            case "Yellow" -> 0xFFFFD27A;
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
            if (st.equals("Cross") || st.equals("Cross + dot") || st.equals("T")) {
                float h = t / 2f;
                if (!st.equals("T")) s.rect(cx - h - o, cy - g - l - o, cx + h + o, cy - g + o, c); // top
                s.rect(cx - h - o, cy + g - o, cx + h + o, cy + g + l + o, c);                     // bottom
                s.rect(cx - g - l - o, cy - h - o, cx - g + o, cy + h + o, c);                     // left
                s.rect(cx + g - o, cy - h - o, cx + g + l + o, cy + h + o, c);                     // right
            }
            if (st.equals("Dot") || st.equals("Cross + dot")) s.circle(cx, cy, Math.max(0.8f, t) + o, c);
            if (st.equals("Circle")) s.ring(cx, cy, l + g, t + o * 2f, c);
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
