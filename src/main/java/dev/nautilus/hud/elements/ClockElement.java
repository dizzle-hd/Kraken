package dev.nautilus.hud.elements;

import dev.nautilus.gui.Shapes;
import dev.nautilus.hud.HudElement;
import dev.nautilus.hud.HudStyle;
import dev.nautilus.hud.Icons;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/** Clock tile: time (seconds small) and the weekday below; the classic pill in glass style. */
public class ClockElement extends HudElement {
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm"), SS = DateTimeFormatter.ofPattern("ss");

    public ClockElement() {
        super("clock", "Clock", false, END, END, 5, 5);
    }

    @Override public float width(boolean editor) { return HudStyle.panels() ? 74f : labelValueWidth("Time", LocalDateTime.now().format(HM)); }
    @Override public float height(boolean editor) { return HudStyle.panels() ? ValueElement.TILE_H + 2f : HudStyle.PILL_H; }

    @Override
    public void render(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float x, float y, float dt, boolean editor) {
        LocalDateTime now = LocalDateTime.now();
        if (!HudStyle.panels()) {
            labelValue(s, text, ctx, x, y, "Time", now.format(HM), HudStyle.TEXT);
            return;
        }
        float w = width(editor), h = height(editor);
        HudStyle.pill(ctx, s, x, y, x + w, y + h, 1f);
        HudStyle.iconBox(s, Icons.CLOCK, x + 3f, y + (h - ValueElement.BOX) / 2f, ValueElement.BOX, 1f);
        String hm = now.format(HM), ss = now.format(SS), day = now.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        float tx = x + 3f + ValueElement.BOX + 6f;
        text.add(() -> {
            HudStyle.text(ctx, hm, tx, y + 7.5f, 0.95f, HudStyle.TEXT, true);
            HudStyle.text(ctx, ss, tx + HudStyle.width(hm, true, 0.95f) + 3f, y + 7f, 0.55f, HudStyle.TEXT_DIM, false);
            HudStyle.text(ctx, day, tx, y + 16f, 0.58f, HudStyle.TEXT_DIM, false);
        });
    }
}
