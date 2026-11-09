package dev.kraken.gui;

import dev.kraken.Kraken;
import dev.kraken.config.ConfigManager;
import dev.kraken.module.Category;
import dev.kraken.module.Module;
import dev.kraken.module.ModuleManager;
import dev.kraken.setting.BoolSetting;
import dev.kraken.setting.KeySetting;
import dev.kraken.setting.ModeSetting;
import dev.kraken.setting.NumberSetting;
import dev.kraken.setting.Setting;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The Kraken wheel: categories arranged in a ring around the player's head, spinning item icons,
 * an animated status ring. Clicking a category turns the wheel into a module wheel, right-clicking
 * a module opens its settings card.
 */
public class RadialScreen extends Screen {
    // ---- palette ---------------------------------------------------------------------------------
    private static final int ACCENT = 0xFFFF5A2A;
    private static final int SEG_FILL = 0x1FFFC8B4;
    private static final int SEG_FILL_HOVER = 0x38FFC8B4;
    private static final int SEG_FILL_ON = 0x55FF5A2A;
    private static final int SEG_BORDER = 0x26FFDCCF;
    private static final int CENTER_FILL = 0x66301008;
    private static final int TEXT = 0xFFF4E6E0;
    private static final int TEXT_DIM = 0xFFB89A90;
    private static final int PANEL = 0xEE2A0F09;
    private static final int PANEL_BORDER = 0x40FFB49C;

    private static final float GAP = 5f;
    private static final float TAU = (float) (Math.PI * 2);

    // ---- state -----------------------------------------------------------------------------------
    private final long openedAt = System.currentTimeMillis();
    private long viewChangedAt = openedAt;
    private Category category;              // null = main wheel
    private Module settingsModule;          // module whose settings card is open
    private long panelChangedAt;
    private float panelAnim;                // 0..1 slide-in of the settings card
    private final float[] hoverAnim = new float[32];
    private long lastFrame = System.currentTimeMillis();

    // settings card interaction
    private float panelScroll;
    private NumberSetting dragging;
    private float dragX, dragW;
    private KeySetting listening;
    private final List<Row> rows = new ArrayList<>();
    private String hint;

    // geometry of the current frame
    private float cx, cy, rOuter, rInner, rCenter;

    private record Row(Setting<?> setting, float x, float y, float w, float h, float trackX, float trackW) {}

    public RadialScreen() {
        super(Text.literal("Kraken"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // =============================================================================================
    // Layout helpers
    // =============================================================================================

    private int segmentCount() {
        return category == null ? Category.values().length : ModuleManager.get().getModules(category).size();
    }

    private void layout() {
        float panelW = panelWidth();
        rOuter = Math.min(Math.min(height * 0.42f, width * 0.30f), 165f);
        rInner = rOuter * 0.50f;
        rCenter = rInner - 11f;
        float shift = panelAnim * (panelW + 24f) / 2f;
        // only shift if there is room for both
        if (width < rOuter * 2 + panelW + 60) shift = 0;
        cx = width / 2f - shift;
        cy = height / 2f;
    }

    private float panelWidth() {
        return Math.min(210f, width - 20f);
    }

    /** -1 = none, -2 = center, otherwise segment index. */
    private int hitTest(double mx, double my) {
        double dx = mx - cx, dy = my - cy;
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d <= rCenter) return -2;
        int n = segmentCount();
        if (n == 0 || d < rInner || d > rOuter + 8) return -1;
        double a = Math.atan2(dx, -dy);
        if (a < 0) a += TAU;
        float step = TAU / n;
        return (int) Math.floor((a + step / 2) / step) % n;
    }

    private static float ease(float t) {
        t = Math.max(0, Math.min(1, t));
        return 1 - (float) Math.pow(1 - t, 3);
    }

    private static float easeBack(float t) {
        t = Math.max(0, Math.min(1, t));
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    // =============================================================================================
    // Rendering
    // =============================================================================================

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float open = ease((System.currentTimeMillis() - openedAt) / 300f);
        int a = (int) (0xE6 * open);
        Shapes s = new Shapes();
        float w = width, h = height, mx = w * 0.55f, my = h * 0.5f;
        int tl = (a << 24) | 0xC63C12, tm = (a << 24) | 0xB83511, tr = (a << 24) | 0x8E2A10;
        int ml = (a << 24) | 0x7A2412, mm = (a << 24) | 0x5A1B0E, mr = (a << 24) | 0x3E130B;
        int bl = (a << 24) | 0xA62E10, bm = (a << 24) | 0x5C1C0E, br = (a << 24) | 0x220C08;
        s.gradient(0, 0, mx, my, tl, tm, ml, mm);
        s.gradient(mx, 0, w, my, tm, tr, mm, mr);
        s.gradient(0, my, mx, h, ml, mm, bl, bm);
        s.gradient(mx, my, w, h, mm, mr, bm, br);
        s.submit(ctx);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float time = (now - openedAt) / 1000f;

        float panelTarget = settingsModule != null ? 1f : 0f;
        panelAnim += (panelTarget - panelAnim) * Math.min(1f, dt * 14f);
        if (Math.abs(panelAnim - panelTarget) < 0.002f) panelAnim = panelTarget;

        layout();
        hint = null;

        float open = easeBack((now - openedAt) / 380f);
        float swap = easeBack((now - viewChangedAt) / 320f);
        float scale = 0.82f + 0.18f * Math.min(open, swap);

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(cx, cy);
        ctx.getMatrices().scale(scale, scale);
        ctx.getMatrices().translate(-cx, -cy);

        int hovered = (dragging == null && !insidePanel(mouseX, mouseY)) ? hitTest(mouseX, mouseY) : -1;
        renderWheel(ctx, hovered, time, dt);
        renderCenter(ctx, hovered == -2, time);

        ctx.getMatrices().popMatrix();

        if (panelAnim > 0.01f && settingsModule != null) {
            ctx.createNewRootLayer();
            renderPanel(ctx, mouseX, mouseY);
        }

        renderHint(ctx);
    }

    private void renderWheel(DrawContext ctx, int hovered, float time, float dt) {
        int n = segmentCount();
        List<Module> modules = category == null ? List.of() : ModuleManager.get().getModules(category);
        if (n == 0) return;
        float step = TAU / n;

        Shapes s = new Shapes();
        for (int i = 0; i < n && i < hoverAnim.length; i++) {
            float target = i == hovered ? 1f : 0f;
            hoverAnim[i] += (target - hoverAnim[i]) * Math.min(1f, dt * 16f);

            float h = hoverAnim[i];
            float mid = i * step;
            float a0 = mid - step / 2f, a1 = mid + step / 2f;
            // hovered segments pop out a little
            float push = 5f * h;
            float ox = (float) Math.sin(mid) * push, oy = -(float) Math.cos(mid) * push;

            boolean on = category != null && modules.get(i).isEnabled();
            boolean catActive = category == null && anyEnabled(Category.values()[i]);
            int fill = on ? Shapes.lerp(SEG_FILL_ON, 0x70FF6A3A, h) : Shapes.lerp(SEG_FILL, SEG_FILL_HOVER, h);
            int fillIn = Shapes.alpha(fill, 0.75f);
            s.sector(cx + ox, cy + oy, rInner, rOuter, a0, a1, GAP, fillIn, fill);
            int border = on ? Shapes.alpha(ACCENT, 0.55f + 0.45f * h) : Shapes.lerp(SEG_BORDER, 0x66FFDCCF, h);
            s.sectorOutline(cx + ox, cy + oy, rInner, rOuter, a0, a1, GAP, 1f, border);

            // small accent tick on categories that have something enabled
            if (catActive) {
                s.arc(cx + ox, cy + oy, rOuter - 3.5f, 2f, mid - 0.12f, mid + 0.12f, ACCENT);
            }
        }
        s.submit(ctx);

        // icons + labels
        float iconScale = Math.max(1.2f, rOuter / 165f * 2.2f);
        float rIcon = (rInner + rOuter) / 2f;
        for (int i = 0; i < n; i++) {
            float h = i < hoverAnim.length ? hoverAnim[i] : 0f;
            float mid = i * step;
            float push = 5f * h;
            float x = cx + (float) Math.sin(mid) * (rIcon + push);
            float y = cy - (float) Math.cos(mid) * (rIcon + push);

            ItemStack icon;
            String label;
            boolean on = false;
            if (category == null) {
                Category c = Category.values()[i];
                icon = c.getIcon();
                label = c.getDisplayName();
            } else {
                Module m = modules.get(i);
                icon = m.getIcon();
                label = m.getName();
                on = m.isEnabled();
            }

            // spinning item: a horizontal squash by cos(angle) looks exactly like a Y-axis rotation
            float spin = (float) Math.cos(time * (1.1f + h * 1.6f) + i * 0.9f);
            if (Math.abs(spin) < 0.06f) spin = Math.copySign(0.06f, spin);
            float sc = iconScale * (1f + 0.12f * h);
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(x, y - 6f * iconScale / 2.2f);
            ctx.getMatrices().scale(sc * spin, sc);
            ctx.drawItem(icon, -8, -8);
            ctx.getMatrices().popMatrix();

            // label (scaled down if it does not fit into the segment)
            float maxW = Math.min(rOuter - rInner, 2f * rIcon * (float) Math.sin(step / 2f)) - 10f;
            int tw = textRenderer.getWidth(label);
            float ts = tw > maxW ? Math.max(0.55f, maxW / tw) : 1f;
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(x, y + 9f * iconScale / 2.2f + 4f);
            ctx.getMatrices().scale(ts, ts);
            int color = on ? 0xFFFFB49C : Shapes.lerp(TEXT, 0xFFFFFFFF, h);
            ctx.drawText(textRenderer, label, -tw / 2, 0, color, true);
            ctx.getMatrices().popMatrix();
        }

        if (hovered >= 0) {
            if (category == null) {
                hint = Category.values()[hovered].getDisplayName() + " – Klicken zum Öffnen";
            } else {
                Module m = modules.get(hovered);
                hint = m.getDescription() + "   §8[Links: an/aus · Rechts: Einstellungen]";
            }
        } else if (hovered == -2 && category != null) {
            hint = "Zurück zur Übersicht";
        }
    }

    private boolean anyEnabled(Category c) {
        for (Module m : ModuleManager.get().getModules(c)) if (m.isEnabled() && c != Category.CLIENT) return true;
        return false;
    }

    private void renderCenter(DrawContext ctx, boolean hovered, float time) {
        Shapes s = new Shapes();
        // soft glow + disc
        s.circle(cx, cy, rCenter + 10f, 0x00000000, 0x00000000);
        s.circle(cx, cy, rCenter, hovered && category != null ? 0x80401409 : CENTER_FILL, 0x80250A05);
        s.ring(cx, cy, rCenter, 1f, 0x30FFC8B4);

        // animated status ring made of arcs with gaps
        float ringR = rCenter + 5.5f;
        float rot = time * 0.35f;
        float[][] arcs = {{0.00f, 0.17f}, {0.21f, 0.41f}, {0.45f, 0.53f}, {0.57f, 0.86f}, {0.90f, 0.96f}};
        for (float[] arc : arcs) {
            float a0 = arc[0] * TAU + rot, a1 = arc[1] * TAU + rot;
            s.arc(cx, cy, ringR, 6f, a0, a1, 0x22FF5A2A);
            s.arc(cx, cy, ringR, 2.5f, a0, a1, ACCENT);
        }
        s.submit(ctx);

        float headSize = Math.max(24f, rCenter * 0.95f);
        if (category == null) {
            // player head + name
            if (client != null && client.player != null) {
                int hs = Math.round(headSize);
                PlayerSkinDrawer.draw(ctx, client.player.getSkin(), Math.round(cx - hs / 2f), Math.round(cy - hs / 2f - 7), hs);
            }
            String name = client != null ? client.getSession().getUsername() : "Kraken";
            float ts = rCenter > 50 ? 1.4f : 1.1f;
            drawCentered(ctx, name, cx, cy + headSize / 2f - 2f, ts, 0xFFFFFFFF);
        } else {
            float spin = (float) Math.cos(time * 1.3f);
            if (Math.abs(spin) < 0.06f) spin = Math.copySign(0.06f, spin);
            float sc = headSize / 16f * 0.9f;
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(cx, cy - 8f);
            ctx.getMatrices().scale(sc * spin, sc);
            ctx.drawItem(category.getIcon(), -8, -8);
            ctx.getMatrices().popMatrix();
            drawCentered(ctx, category.getDisplayName(), cx, cy + headSize / 2f - 4f, 1.3f, 0xFFFFFFFF);
            drawCentered(ctx, hovered ? "‹ Zurück" : "‹", cx, cy + headSize / 2f + 9f, 0.8f, hovered ? 0xFFFFB49C : TEXT_DIM);
        }
    }

    private void drawCentered(DrawContext ctx, String text, float x, float y, float scale, int color) {
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x, y);
        ctx.getMatrices().scale(scale, scale);
        ctx.drawText(textRenderer, text, -textRenderer.getWidth(text) / 2, 0, color, true);
        ctx.getMatrices().popMatrix();
    }

    private void renderHint(DrawContext ctx) {
        if (hint == null) {
            hint = category == null ? "Kategorie wählen  §8·  ESC / " + Kraken.openWheel.getBoundKeyLocalizedText().getString() + " schließt"
                    : "Links-Klick: Modul an/aus  §8·  Rechts-Klick: Einstellungen  §8·  ESC: zurück";
        }
        List<OrderedText> lines = textRenderer.wrapLines(Text.literal(hint), Math.min(width - 40, 420));
        int y = height - 14 - (lines.size() - 1) * 10;
        for (OrderedText line : lines) {
            int w = textRenderer.getWidth(line);
            ctx.drawText(textRenderer, line, width / 2 - w / 2, y, TEXT_DIM, false);
            y += 10;
        }
    }

    // ---- settings card ---------------------------------------------------------------------------

    private float panelX() {
        float w = panelWidth();
        float x = cx + rOuter + 24f;
        if (x + w > width - 8) x = width - w - 8;
        return x + (1f - panelAnim) * 30f;
    }

    private float panelTop() { return 20f; }
    private float panelBottom() { return height - 30f; }

    private boolean insidePanel(double mx, double my) {
        if (settingsModule == null) return false;
        float x = panelX();
        return mx >= x && mx <= x + panelWidth() && my >= panelTop() && my <= panelBottom();
    }

    private void renderPanel(DrawContext ctx, int mouseX, int mouseY) {
        Module m = settingsModule;
        float a = panelAnim;
        float x = panelX(), w = panelWidth();
        float top = panelTop();

        // content height
        List<OrderedText> desc = textRenderer.wrapLines(Text.literal(m.getDescription()), (int) w - 20);
        float header = 34 + desc.size() * 10 + 8;
        List<Setting<?>> visible = new ArrayList<>();
        for (Setting<?> s : m.getSettings()) if (s.isVisible()) visible.add(s);
        visible.add(m.keybind);
        float contentH = header;
        for (Setting<?> s : visible) contentH += rowHeight(s);
        contentH += 8;
        float maxH = panelBottom() - top;
        float h = Math.min(contentH, maxH);
        float y = cy - h / 2f;
        if (y < top) y = top;
        float maxScroll = Math.max(0, contentH - h);
        panelScroll = Math.max(0, Math.min(panelScroll, maxScroll));

        Shapes s = new Shapes();
        s.roundRect(x, y, x + w, y + h, 10, Shapes.alpha(PANEL, a));
        s.roundRectOutline(x, y, x + w, y + h, 10, 1, Shapes.alpha(PANEL_BORDER, a));
        s.submit(ctx);

        ctx.enableScissor((int) x, (int) y + 1, (int) (x + w), (int) (y + h) - 1);
        float yy = y + 10 - panelScroll;

        // header: icon, name, on/off pill
        ctx.drawItem(m.getIcon(), (int) x + 10, (int) yy);
        ctx.drawText(textRenderer, m.getName(), (int) x + 32, (int) yy + 4, 0xFFFFFFFF, true);
        float pillW = 30, pillX = x + w - pillW - 10, pillY = yy + 3;
        Shapes hs = new Shapes();
        hs.roundRect(pillX, pillY, pillX + pillW, pillY + 11, 5.5f, m.isEnabled() ? ACCENT : 0x40FFFFFF);
        hs.submit(ctx);
        String pill = m.isEnabled() ? "AN" : "AUS";
        ctx.drawText(textRenderer, pill, (int) (pillX + pillW / 2 - textRenderer.getWidth(pill) / 2f), (int) pillY + 2, 0xFFFFFFFF, false);
        rows.clear();
        rows.add(new Row(null, pillX, pillY, pillW, 11, 0, 0)); // null setting = module toggle

        yy += 22;
        for (OrderedText line : desc) {
            ctx.drawText(textRenderer, line, (int) x + 10, (int) yy, TEXT_DIM, false);
            yy += 10;
        }
        yy += 4;
        Shapes div = new Shapes();
        div.rect(x + 10, yy, x + w - 10, yy + 1, 0x30FFC8B4);
        div.submit(ctx);
        yy += 6;

        Shapes shapes = new Shapes();
        List<Runnable> texts = new ArrayList<>();
        for (Setting<?> setting : visible) {
            float rh = rowHeight(setting);
            float rx = x + 8, rw = w - 16;
            boolean hover = mouseX >= rx && mouseX <= rx + rw && mouseY >= yy && mouseY <= yy + rh && mouseY >= y && mouseY <= y + h;
            if (hover) {
                shapes.roundRect(rx, yy, rx + rw, yy + rh, 4, 0x14FFFFFF);
                hint = setting.getDescription();
            }
            final float ry = yy;
            final String name = setting.getName();
            texts.add(() -> ctx.drawText(textRenderer, name, (int) rx + 4, (int) ry + 5, TEXT, false));

            float trackX = 0, trackW = 0;
            if (setting instanceof BoolSetting b) {
                float sw = 20, sh = 10, sx = rx + rw - sw - 4, sy = yy + (rh - sh) / 2;
                shapes.roundRect(sx, sy, sx + sw, sy + sh, 5, b.isOn() ? ACCENT : 0x40FFFFFF);
                float knob = b.isOn() ? sx + sw - 5 : sx + 5;
                shapes.circle(knob, sy + 5, 3.5f, 0xFFFFFFFF);
            } else if (setting instanceof NumberSetting num) {
                String val = num.display();
                texts.add(() -> ctx.drawText(textRenderer, val, (int) (rx + rw - 4 - textRenderer.getWidth(val)), (int) ry + 5, 0xFFFFB49C, false));
                trackX = rx + 4;
                trackW = rw - 8;
                float ty = yy + 18;
                shapes.roundRect(trackX, ty, trackX + trackW, ty + 3, 1.5f, 0x40FFFFFF);
                float fx = trackX + trackW * (float) num.getFraction();
                shapes.roundRect(trackX, ty, fx, ty + 3, 1.5f, ACCENT);
                shapes.circle(fx, ty + 1.5f, num == dragging ? 4.5f : 3.5f, 0xFFFFFFFF);
            } else if (setting instanceof ModeSetting mode) {
                String val = "‹ " + mode.get() + " ›";
                texts.add(() -> ctx.drawText(textRenderer, val, (int) (rx + rw - 4 - textRenderer.getWidth(val)), (int) ry + 5, 0xFFFFB49C, false));
            } else if (setting instanceof KeySetting key) {
                String val = key == listening ? "[ … ]" : "[" + key.display() + "]";
                texts.add(() -> ctx.drawText(textRenderer, val, (int) (rx + rw - 4 - textRenderer.getWidth(val)), (int) ry + 5,
                        key == listening ? ACCENT : 0xFFFFB49C, false));
            }
            rows.add(new Row(setting, rx, yy, rw, rh, trackX, trackW));
            yy += rh;
        }
        shapes.submit(ctx);
        for (Runnable r : texts) r.run();
        ctx.disableScissor();

        // scroll bar
        if (maxScroll > 0) {
            float barH = h * h / contentH;
            float barY = y + (h - barH) * (panelScroll / maxScroll);
            Shapes sb = new Shapes();
            sb.roundRect(x + w - 4, barY + 4, x + w - 2, barY + barH - 4, 1, 0x50FFFFFF);
            sb.submit(ctx);
        }
        // remember visible area for clicks
        panelVisibleTop = y;
        panelVisibleBottom = y + h;
    }

    private float panelVisibleTop, panelVisibleBottom;

    private static float rowHeight(Setting<?> s) {
        return s instanceof NumberSetting ? 26 : 18;
    }

    // =============================================================================================
    // Input
    // =============================================================================================

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int button = click.button();

        if (listening != null) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) listening.set(-1);
            listening = null;
            return true;
        }

        if (settingsModule != null && insidePanel(mx, my)) {
            if (my < panelVisibleTop || my > panelVisibleBottom) return true;
            for (Row row : rows) {
                if (mx < row.x || mx > row.x + row.w || my < row.y || my > row.y + row.h) continue;
                Setting<?> s = row.setting;
                if (s == null) settingsModule.toggle();
                else if (s instanceof BoolSetting b) b.toggle();
                else if (s instanceof ModeSetting mode) mode.cycle(button != GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                else if (s instanceof KeySetting key) {
                    if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) key.set(-1); else listening = key;
                } else if (s instanceof NumberSetting num) {
                    dragging = num;
                    dragX = row.trackX;
                    dragW = row.trackW;
                    num.setFraction((mx - dragX) / dragW);
                }
                return true;
            }
            return true;
        }

        int hit = hitTest(mx, my);
        if (hit == -2) {
            if (category != null) switchView(null);
            return true;
        }
        if (hit >= 0) {
            if (category == null) {
                switchView(Category.values()[hit]);
            } else {
                Module m = ModuleManager.get().getModules(category).get(hit);
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                    openSettings(m == settingsModule ? null : m);
                } else {
                    m.toggle();
                }
            }
            return true;
        }

        // click into the void
        if (settingsModule != null) openSettings(null);
        else if (category != null) switchView(null);
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (dragging != null) {
            dragging.setFraction((click.x() - dragX) / dragW);
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (insidePanel(mouseX, mouseY)) {
            panelScroll -= (float) verticalAmount * 14f;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (listening != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                // cancel
            } else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                listening.set(-1);
            } else {
                listening.set(key);
            }
            listening = null;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (settingsModule != null) openSettings(null);
            else if (category != null) switchView(null);
            else close();
            return true;
        }
        if (Kraken.openWheel.matchesKey(input)) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    private void switchView(Category c) {
        category = c;
        viewChangedAt = System.currentTimeMillis();
        java.util.Arrays.fill(hoverAnim, 0f);
        openSettings(null);
    }

    private void openSettings(Module m) {
        settingsModule = m;
        panelScroll = 0;
        dragging = null;
        listening = null;
        panelChangedAt = System.currentTimeMillis();
        if (m != null && panelAnim < 0.05f) panelAnim = 0.001f;
    }

    @Override
    public void removed() {
        ConfigManager.save();
        super.removed();
    }
}
