package dev.nautilus.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nautilus.NautilusClient;
import dev.nautilus.config.ConfigManager;
import dev.nautilus.hud.HudStyle;
import dev.nautilus.module.ModuleManager;
import dev.nautilus.module.client.Hud;
import dev.nautilus.module.smp.XRay;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * First-start setup, shown once when the player enters a world (again via Client → Einrichtung):
 * <ol>
 *   <li>welcome + warnings (alpha, experimental, Sodium / Iris found, server rules),</li>
 *   <li>settings with live preview: wheel key, wheel size, liquid glass, HUD, HUD colour,</li>
 *   <li>controls overview.</li>
 * </ol>
 */
public class OnboardingScreen extends Screen {
    private static final int PAGES = 3;
    private static final int ORANGE = 0xFFFFB347, RED = 0xFFFF5A5A, GREEN = 0xFF5BE38A;

    private int page;
    private boolean listening;
    private boolean draggingSize;
    private final long openedAt = System.currentTimeMillis();
    private final List<Button> buttons = new ArrayList<>();
    private float barX1, barX2, barY;

    private record Button(float x1, float y1, float x2, float y2, Runnable action) {
        boolean hit(double x, double y) { return x >= x1 && x <= x2 && y >= y1 && y <= y2; }
    }

    private record Warning(int color, String title, String text) {}

    private final Screen parent;

    public OnboardingScreen() {
        this(null);
    }

    /** @param parent screen to return to (the title screen on the first start), null = back to the game */
    public OnboardingScreen(Screen parent) {
        super(Component.literal("Nautilus Client – Einrichtung"));
        this.parent = parent;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static Hud hud() {
        return ModuleManager.get().get(Hud.class);
    }

    private static List<Warning> warnings() {
        List<Warning> list = new ArrayList<>();
        list.add(new Warning(ORANGE, "Alpha – noch in Entwicklung",
                "Funktionen können fehlen, sich ändern oder Fehler haben."));
        list.add(new Warning(ORANGE, "Alle Module sind experimentell",
                "Bitte zuerst im Einzelspieler ausprobieren, bevor du sie auf Servern nutzt."));
        if (XRay.SODIUM) {
            list.add(new Warning(RED, "Sodium gefunden",
                    "XRay funktioniert mit Sodium nicht und schaltet sich ab. Alles andere ist mit Sodium experimentell."));
        }
        if (FabricLoader.getInstance().isModLoaded("iris")) {
            list.add(new Warning(RED, "Iris gefunden",
                    "Shader können ESP-Boxen, HUD und Liquid Glass stören – bei Problemen Shader ausschalten."));
        }
        list.add(new Warning(RED, "Server-Regeln",
                "Combat-, XRay- und BaseFinder-Module sind auf vielen Servern verboten – Nutzung auf eigenes Risiko."));
        return list;
    }

    // =============================================================================================
    // Rendering
    // =============================================================================================

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        float open = Math.min(1f, (System.currentTimeMillis() - openedAt) / 250f);
        // on the title screen there is no world behind us: draw the menu panorama first
        if (minecraft.level == null) super.extractBackground(ctx, mouseX, mouseY, delta);
        Shapes s = new Shapes();
        s.rect(0, 0, width, height, Shapes.alpha(0x8C120604, open));
        // live preview of the wheel size on the settings page
        if (page == 1) {
            float r = RadialScreen.wheelRadius(width, height);
            float cx = width / 2f, cy = RadialScreen.wheelCenterY(height);
            s.ring(cx, cy, r, 1.2f, HudStyle.accent(0x66));
            s.ring(cx, cy, r * 0.44f, 1f, HudStyle.accent(0x33));
        }
        s.submit(ctx);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        buttons.clear();
        float pw = Math.min(width - 30f, 360f);
        float px = width / 2f - pw / 2f;
        Shapes s = new Shapes();
        List<Runnable> text = new ArrayList<>();

        float ph = switch (page) {
            case 0 -> 64f + warningsHeight(pw) + 34f;
            case 1 -> 64f + 5 * 30f + 34f;
            default -> 64f + 7 * 14f + 40f;
        };
        ph = Math.min(ph, height - 16f);
        float py = height / 2f - ph / 2f;

        s.roundRect(px, py, px + pw, py + ph, 12f, 0x8C1A0805);
        HudStyle.pill(ctx, s, px, py, px + pw, py + ph, 1f);

        // header: logo, title, page dots
        float t = (System.currentTimeMillis() % 600000L) / 1000f;
        NautilusLogo.draw(s, px + 22f, py + 22f, 20f, t, HudStyle.accent(), 0xFFFFFFFF, 0xFF2A0E08);
        String title = switch (page) {
            case 0 -> "Willkommen bei Nautilus Client";
            case 1 -> "Einstellungen";
            default -> "So funktioniert's";
        };
        String sub = switch (page) {
            case 0 -> "Kurz vorab ein paar wichtige Hinweise";
            case 1 -> "Alles kannst du später im Rad ändern";
            default -> "Die wichtigsten Tasten";
        };
        text.add(() -> HudStyle.text(ctx, title, px + 40f, py + 18f, 1.15f, HudStyle.TEXT, true));
        text.add(() -> HudStyle.text(ctx, sub, px + 40f, py + 30f, 0.75f, HudStyle.TEXT_DIM, false));
        for (int i = 0; i < PAGES; i++) {
            float dx = px + pw - 16f - (PAGES - 1 - i) * 9f;
            s.circle(dx, py + 20f, i == page ? 2.6f : 1.8f, i == page ? HudStyle.accent() : 0x66FFFFFF);
        }
        s.rect(px + 14f, py + 44f, px + pw - 14f, py + 44.5f, 0x26FFFFFF);

        float y = py + 54f;
        switch (page) {
            case 0 -> renderWarnings(ctx, s, text, px, y, pw);
            case 1 -> renderSettings(ctx, s, text, px, y, pw, mouseX, mouseY);
            default -> renderControls(ctx, s, text, px, y, pw);
        }

        // footer buttons
        float by = py + ph - 26f;
        if (page > 0) button(ctx, s, text, px + 14f, by, "Zurück", false, mouseX, mouseY, () -> page--);
        String next = page < PAGES - 1 ? "Weiter" : "Los geht's";
        float nw = HudStyle.width(next, true, 0.85f) + 26f;
        button(ctx, s, text, px + pw - 14f - nw, by, next, true, mouseX, mouseY, () -> {
            if (page < PAGES - 1) page++;
            else onClose();
        });

        s.submit(ctx);
        for (Runnable r : text) r.run();
    }

    private float button(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float x, float y, String label, boolean primary,
                         int mouseX, int mouseY, Runnable action) {
        float w = HudStyle.width(label, primary, 0.85f) + 26f, h = 16f;
        boolean hot = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        int fill = primary ? (hot ? HudStyle.accentHot(0xE6) : HudStyle.accent(0xCC)) : (hot ? 0x40FFFFFF : 0x22FFFFFF);
        s.roundRect(x, y, x + w, y + h, h / 2f, fill);
        s.roundRectRim(x, y, x + w, y + h, h / 2f, 0.7f, 0x40FFFFFF);
        text.add(() -> HudStyle.text(ctx, label, x + 13f, y + h / 2f, 0.85f, primary ? HudStyle.onAccent() : HudStyle.TEXT, primary));
        buttons.add(new Button(x, y, x + w, y + h, action));
        return w;
    }

    // ---- page 1: warnings -----------------------------------------------------------------------

    private float warningsHeight(float pw) {
        float h = 0;
        for (Warning w : warnings()) h += 13f + wrap(w.text(), pw - 46f, 0.72f).size() * 9f + 6f;
        return h;
    }

    private void renderWarnings(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float px, float y, float pw) {
        for (Warning w : warnings()) {
            List<String> lines = wrap(w.text(), pw - 46f, 0.72f);
            float h = 13f + lines.size() * 9f;
            s.roundRect(px + 14f, y - 4f, px + pw - 14f, y + h, 6f, Shapes.alpha(w.color(), 0.10f));
            s.roundRectRim(px + 14f, y - 4f, px + pw - 14f, y + h, 6f, 0.6f, Shapes.alpha(w.color(), 0.45f));
            s.circle(px + 24f, y + 4f, 2.4f, w.color());
            float ty = y;
            text.add(() -> HudStyle.text(ctx, w.title(), px + 32f, ty + 4f, 0.8f, HudStyle.TEXT, true));
            for (int i = 0; i < lines.size(); i++) {
                String l = lines.get(i);
                float ly = ty + 14f + i * 9f;
                text.add(() -> HudStyle.text(ctx, l, px + 32f, ly, 0.72f, HudStyle.TEXT_DIM, false));
            }
            y += h + 6f;
        }
    }

    // ---- page 2: settings -----------------------------------------------------------------------

    private void renderSettings(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float px, float y, float pw,
                                int mouseX, int mouseY) {
        Hud hud = hud();
        float right = px + pw - 14f;

        // wheel key
        label(ctx, text, px, y, "Rad-Taste", "Öffnet das Nautilus-Rad");
        String key = listening ? "Taste drücken …" : NautilusClient.openWheel.getTranslatedKeyMessage().getString();
        float kw = Math.max(70f, HudStyle.width(key, true, 0.85f) + 26f);
        chip(ctx, s, text, right - kw, y - 2f, kw, key, listening, mouseX, mouseY, () -> listening = !listening);
        y += 30f;

        // wheel size: slider
        label(ctx, text, px, y, "Rad-Größe", "Ziehen oder −/+ (Ring im Hintergrund = Vorschau)");
        String pct = hud.wheelSize.getInt() + " %";
        float bw = 110f;
        float bx2 = right - 22f, bx1 = bx2 - bw;
        chip(ctx, s, text, right - 16f, y - 2f, 16f, "+", false, mouseX, mouseY, () -> hud.wheelSize.set(hud.wheelSize.get() + 5));
        chip(ctx, s, text, bx1 - 22f, y - 2f, 16f, "−", false, mouseX, mouseY, () -> hud.wheelSize.set(hud.wheelSize.get() - 5));
        float frac = (float) hud.wheelSize.getFraction();
        s.roundRect(bx1, y + 4f, bx2, y + 8f, 2f, 0x33FFFFFF);
        s.roundRect(bx1, y + 4f, bx1 + bw * frac, y + 8f, 2f, HudStyle.accent());
        s.circle(bx1 + bw * frac, y + 6f, 4.5f, 0xFFFFFFFF);
        barX1 = bx1;
        barX2 = bx2;
        barY = y + 6f;
        float pctW = HudStyle.width(pct, true, 0.75f);
        text.add(() -> HudStyle.text(ctx, pct, bx1 + bw / 2f - pctW / 2f, barY + 9f, 0.75f, HudStyle.TEXT_DIM, true));
        y += 30f;

        // liquid glass
        label(ctx, text, px, y, "Liquid Glass", "Echte Glas-Unschärfe – aus = deutlich bessere FPS");
        toggle(ctx, s, text, right, y, hud.liquidGlass.isOn(), mouseX, mouseY, hud.liquidGlass::toggle);
        y += 30f;

        // HUD
        label(ctx, text, px, y, "HUD anzeigen", "Logo, Modulliste, Koordinaten, FPS …");
        toggle(ctx, s, text, right, y, hud.isEnabled(), mouseX, mouseY, hud::toggle);
        y += 30f;

        // HUD colour: one swatch per colour, the whole setup shows the choice live
        label(ctx, text, px, y, "HUD-Farbe", "Akzent für HUD, Logo und Glas");
        String[] names = HudStyle.COLOR_NAMES;
        float step = 13f, sy = y + 6f;
        float sx0 = right - 5f - (names.length - 1) * step;
        String hovered = null;
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            float sx = sx0 + i * step;
            boolean hot = Math.abs(mouseX - sx) <= 6f && Math.abs(mouseY - sy) <= 6f;
            boolean sel = hud.color.is(name);
            if (sel) s.ring(sx, sy, 6.6f, 1.2f, 0xFFFFFFFF);
            else if (hot) s.ring(sx, sy, 6.4f, 0.8f, 0x99FFFFFF);
            s.circle(sx, sy, sel || hot ? 5f : 4.4f, HudStyle.colorOf(name));
            if (HudStyle.RAINBOW.equals(name)) s.circle(sx, sy, 1.6f, 0xFFFFFFFF); // marks the cycling one
            if (hot) hovered = name;
            buttons.add(new Button(sx - 6f, sy - 6f, sx + 6f, sy + 6f, () -> hud.color.set(name)));
        }
        String shown = hovered != null ? hovered : hud.color.get();
        float nw = HudStyle.width(shown, true, 0.68f);
        float ny = sy + 11f;
        text.add(() -> HudStyle.text(ctx, shown, right - nw, ny, 0.68f, HudStyle.TEXT_DIM, true));
    }

    private void label(GuiGraphicsExtractor ctx, List<Runnable> text, float px, float y, String title, String hint) {
        text.add(() -> HudStyle.text(ctx, title, px + 16f, y + 2f, 0.9f, HudStyle.TEXT, true));
        text.add(() -> HudStyle.text(ctx, hint, px + 16f, y + 13f, 0.68f, HudStyle.TEXT_DIM, false));
    }

    private void chip(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float x, float y, float w, String label, boolean active,
                      int mouseX, int mouseY, Runnable action) {
        float h = 16f;
        boolean hot = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        int fill = active ? HudStyle.accent(0xCC) : hot ? 0x40FFFFFF : 0x22FFFFFF;
        s.roundRect(x, y, x + w, y + h, h / 2f, fill);
        s.roundRectRim(x, y, x + w, y + h, h / 2f, 0.7f, 0x40FFFFFF);
        float lw = HudStyle.width(label, true, 0.85f);
        text.add(() -> HudStyle.text(ctx, label, x + w / 2f - lw / 2f, y + h / 2f, 0.85f, active ? HudStyle.onAccent() : HudStyle.TEXT, true));
        buttons.add(new Button(x, y, x + w, y + h, action));
    }

    private void toggle(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float right, float y, boolean on,
                        int mouseX, int mouseY, Runnable action) {
        float w = 30f, h = 15f, x = right - w, ty = y - 1f;
        boolean hot = mouseX >= x && mouseX <= x + w && mouseY >= ty && mouseY <= ty + h;
        s.roundRect(x, ty, x + w, ty + h, h / 2f, on ? (hot ? HudStyle.accentHot(0xE6) : HudStyle.accent(0xCC)) : (hot ? 0x40FFFFFF : 0x26FFFFFF));
        s.circle(on ? x + w - h / 2f : x + h / 2f, ty + h / 2f, h / 2f - 2f, 0xFFFFFFFF);
        String st = on ? "An" : "Aus";
        float sw = HudStyle.width(st, false, 0.72f);
        text.add(() -> HudStyle.text(ctx, st, x - 6f - sw, ty + h / 2f, 0.72f, on ? GREEN : HudStyle.TEXT_DIM, false));
        buttons.add(new Button(x, ty, x + w, ty + h, action));
    }

    // ---- page 3: controls -----------------------------------------------------------------------

    private void renderControls(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float px, float y, float pw) {
        String[][] rows = {
                {NautilusClient.openWheel.getTranslatedKeyMessage().getString(), "Rad öffnen / schließen"},
                {"Links-Klick", "Kategorie öffnen, Modul an/aus"},
                {"Rechts-Klick", "Einstellungen eines Moduls"},
                {"Presets (oben)", "In den Einstellungen: fertige Presets mit Ampel fürs Bann-Risiko"},
                {"ESC / Mitte", "Zurück"},
                {"Tippen im Rad", "Module suchen"},
                {"Client → Einrichtung", "Diese Einrichtung erneut öffnen"},
        };
        for (String[] r : rows) {
            float kw = HudStyle.width(r[0], true, 0.75f) + 14f;
            float ky = y;
            s.roundRect(px + 16f, ky - 1f, px + 16f + kw, ky + 11f, 4f, 0x30FFFFFF);
            s.roundRectRim(px + 16f, ky - 1f, px + 16f + kw, ky + 11f, 4f, 0.6f, 0x40FFFFFF);
            text.add(() -> HudStyle.text(ctx, r[0], px + 23f, ky + 5f, 0.75f, HudStyle.TEXT, true));
            text.add(() -> HudStyle.text(ctx, r[1], px + 16f + Math.max(kw, 104f) + 8f, ky + 5f, 0.78f, HudStyle.TEXT_DIM, false));
            y += 14f;
        }
    }

    // ---- text wrapping --------------------------------------------------------------------------

    private static List<String> wrap(String textIn, float maxWidth, float scale) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : textIn.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && HudStyle.width(candidate, false, scale) > maxWidth) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    // =============================================================================================
    // Interaction
    // =============================================================================================

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (page == 1 && mx >= barX1 - 5 && mx <= barX2 + 5 && Math.abs(my - barY) <= 7) {
            draggingSize = true;
            setSize(mx);
            return true;
        }
        for (Button b : List.copyOf(buttons)) {
            if (b.hit(mx, my)) {
                b.action().run();
                return true;
            }
        }
        if (listening) listening = false;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (draggingSize) {
            setSize(click.x());
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        draggingSize = false;
        return super.mouseReleased(click);
    }

    private void setSize(double mx) {
        hud().wheelSize.setFraction((mx - barX1) / Math.max(1f, barX2 - barX1));
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        int key = input.key();
        if (listening) {
            listening = false;
            if (key != InputConstants.KEY_ESCAPE) {
                NautilusClient.openWheel.setKey(InputConstants.Type.KEYBOARD.getOrCreate(key));
                KeyMapping.resetMapping();
                minecraft.options.save();
            }
            return true;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_RIGHT) {
            if (page < PAGES - 1) page++;
            else onClose();
            return true;
        }
        if (key == InputConstants.KEY_LEFT && page > 0) {
            page--;
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void removed() {
        ConfigManager.setOnboarded(true); // also saves the settings
        super.removed();
    }
}
