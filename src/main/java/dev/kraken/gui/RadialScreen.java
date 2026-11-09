package dev.kraken.gui;

import com.mojang.blaze3d.platform.InputConstants;
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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Kraken wheel. Three levels live inside the same wheel:
 * <ol>
 *     <li>categories around the 3D player head,</li>
 *     <li>the modules of a category,</li>
 *     <li>the settings of a module (toggles, arc sliders, modes, keybind) – no external panel.</li>
 * </ol>
 * Segments are rounded "frosted glass" sectors over a blurred, tinted background. Every icon is a
 * real rotating 3D item model, the centre shows the 3D head of the player.
 */
public class RadialScreen extends Screen {
    // ---- palette ---------------------------------------------------------------------------------
    private static final int ACCENT = 0xFFFF5A2A;
    private static final int TINT = 0x382A0E06;
    /** Liquid glass proof of concept on the top segment of the main wheel. */
    private static final boolean LIQUID_GLASS_POC = true;
    private static final int GLASS_IN = 0x0CFFE2D6;
    private static final int GLASS_OUT = 0x17FFE2D6;
    private static final int GLASS_HOVER_IN = 0x1AFFE2D6;
    private static final int GLASS_HOVER_OUT = 0x2AFFE2D6;
    private static final int GLASS_ON_IN = 0x2CFF6A3A;
    private static final int GLASS_ON_OUT = 0x48FF6A3A;
    private static final int RIM = 0x22FFFFFF;
    private static final int RIM_HOVER = 0x5CFFFFFF;
    private static final int SHINE = 0x12FFFFFF;
    private static final int TEXT = 0xFFF6ECE8;
    private static final int TEXT_DIM = 0xB4F0D8D0;
    private static final int VALUE = 0xFFFFC2AE;

    private static final FontDescription CLEAN = new FontDescription.Resource(Identifier.fromNamespaceAndPath(Kraken.MOD_ID, "clean"));
    private static final FontDescription CLEAN_BOLD = new FontDescription.Resource(Identifier.fromNamespaceAndPath(Kraken.MOD_ID, "clean_bold"));

    private static final float TAU = (float) (Math.PI * 2);
    private static final int MAX_PER_PAGE = 9;
    // orientation of the PiP model space (verified with screenshots in the client game test)
    private static final float YAW_SIGN = 1f, PITCH_SIGN = 1f;

    // ---- navigation state ------------------------------------------------------------------------
    private enum Level { CATEGORIES, MODULES, SETTINGS }

    private Level level = Level.CATEGORIES;
    private Category category;
    private Module module;
    private int page;

    // ---- animation state -------------------------------------------------------------------------
    private final long openedAt = System.currentTimeMillis();
    private long levelChangedAt = openedAt;
    private long closingAt = -1;
    private boolean closed;
    private long lastFrame = openedAt;
    private final float[] hoverAnim = new float[MAX_PER_PAGE + 8];
    private final float[] activeAnim = new float[MAX_PER_PAGE + 8];
    private final float[] valueAnim = new float[MAX_PER_PAGE + 8];
    private float centerHover;
    private float headYaw, headPitch;           // current (smoothed) head rotation, degrees
    private boolean headInit;

    // ---- interaction -----------------------------------------------------------------------------
    private NumberSetting dragging;
    private int draggingIndex = -1;
    private KeySetting listening;
    private String hint;

    // ---- per-frame geometry ----------------------------------------------------------------------
    private float cx, cy, R, rIn, rOut, gap, corner, discR, ringR;
    private ItemStack headStack;

    /** One segment of the current level. */
    private record Entry(String label, ItemStack icon, String description, boolean active,
                         Category category, Module module, Setting<?> setting, boolean moduleToggle) {}

    public RadialScreen() {
        super(Component.literal("Kraken"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // =============================================================================================
    // Entries
    // =============================================================================================

    private List<Entry> allEntries() {
        List<Entry> list = new ArrayList<>();
        switch (level) {
            case CATEGORIES -> {
                for (Category c : Category.values()) {
                    boolean any = false;
                    for (Module m : ModuleManager.get().getModules(c)) any |= m.isEnabled() && c != Category.CLIENT;
                    list.add(new Entry(c.getDisplayName(), c.getIcon(), c.getDisplayName() + " öffnen", any, c, null, null, false));
                }
            }
            case MODULES -> {
                for (Module m : ModuleManager.get().getModules(category)) {
                    list.add(new Entry(m.getName(), m.getIcon(), m.getDescription(), m.isEnabled(), null, m, null, false));
                }
            }
            case SETTINGS -> {
                list.add(new Entry("Aktiv", null, "Modul an- oder ausschalten", module.isEnabled(), null, module, null, true));
                for (Setting<?> s : module.getSettings()) {
                    if (!s.isVisible()) continue;
                    boolean on = s instanceof BoolSetting b && b.isOn();
                    list.add(new Entry(s.getName(), null, s.getDescription(), on, null, module, s, false));
                }
                list.add(new Entry(module.keybind.getName(), null, module.keybind.getDescription(), false, null, module, module.keybind, false));
            }
        }
        return list;
    }

    private int pageCount(int total) {
        return Math.max(1, (total + MAX_PER_PAGE - 1) / MAX_PER_PAGE);
    }

    /** Entries of the current page; pages are balanced (17 → 9 + 8). */
    private List<Entry> entries() {
        List<Entry> all = allEntries();
        int pages = pageCount(all.size());
        page = Math.max(0, Math.min(page, pages - 1));
        int per = (all.size() + pages - 1) / pages;
        int from = page * per;
        return all.subList(Math.min(from, all.size()), Math.min(from + per, all.size()));
    }

    // =============================================================================================
    // Geometry
    // =============================================================================================

    private void layout() {
        R = Math.min(Math.min((height - 30f) / 2f - 4f, width * 0.33f), 200f);
        rOut = R;
        rIn = R * 0.44f;
        gap = Math.max(3f, R * 0.028f);
        corner = R * 0.07f;
        ringR = R * 0.405f;
        discR = R * 0.375f;
        cx = width / 2f;
        cy = (height - 22f) / 2f + 3f;
    }

    /** -1 = nothing, -2 = centre, otherwise segment index. */
    private int hitTest(double mx, double my, int n) {
        double dx = mx - cx, dy = my - cy;
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d <= discR + 4) return -2;
        if (n == 0 || d < rIn || d > rOut + R * 0.05f) return -1;
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
        float c1 = 1.4f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    private float closeFactor(long now) {
        return closingAt < 0 ? 1f : 1f - ease((now - closingAt) / 160f);
    }

    // =============================================================================================
    // Text (clean TTF font) and label fitting
    // =============================================================================================

    private Component styled(String text, boolean bold) {
        return Component.literal(text).withStyle(s -> s.withFont(bold ? CLEAN_BOLD : CLEAN));
    }

    private int textWidth(String text, boolean bold) {
        return font.width(styled(text, bold));
    }

    /** Draws text centred horizontally and vertically around (x, y). */
    private void text(GuiGraphicsExtractor ctx, String text, float x, float y, float scale, int color, boolean bold) {
        // soft shadow keeps the labels readable over bright terrain now that there is no background
        UiText.centered(ctx, font, text, x, y, scale, color, bold, true);
    }

    /** Text centred in the disc; scaled down so it never crosses the disc border at its height. */
    private void centerText(GuiGraphicsExtractor ctx, String str, float y, float desired, int color, boolean bold) {
        float w = Math.max(1, textWidth(str, bold));
        float limit = discR - Math.max(3f, R * 0.04f);
        float s = desired;
        for (int it = 0; it < 4; it++) {
            float dy = Math.abs(y - cy) + 4.5f * s; // the text edge furthest from the centre
            float half = (float) Math.sqrt(Math.max(0f, limit * limit - dy * dy));
            s = Math.min(desired, 2f * half / w);
        }
        if (s < 0.3f) return;
        text(ctx, str, cx, y, s, color, bold);
    }

    private record Fit(String[] lines, float scale) {}

    /** Is the axis-aligned box completely inside the (padded) segment? */
    private boolean boxInside(float x0, float y0, float x1, float y1, float a0, float a1) {
        float pad = gap / 2f + R * 0.035f;
        for (int i = 0; i <= 2; i++) {
            for (int j = 0; j <= 2; j++) {
                float px = x0 + (x1 - x0) * i / 2f, py = y0 + (y1 - y0) * j / 2f;
                float dx = px - cx, dy = py - cy;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < rIn + pad || d > rOut - pad) return false;
                float a = (float) Math.atan2(dx, -dy);
                float mid = (a0 + a1) / 2f;
                float rel = a - mid;
                while (rel > Math.PI) rel -= TAU;
                while (rel < -Math.PI) rel += TAU;
                float half = (a1 - a0) / 2f - pad / Math.max(d, 1f);
                if (Math.abs(rel) > half) return false;
            }
        }
        return true;
    }

    /** Finds the largest scale (and optional 2-line split) at which a label fits into its segment. */
    private Fit fitLabel(String label, float x, float y, float base, float a0, float a1, boolean bold) {
        List<String[]> options = new ArrayList<>();
        options.add(new String[]{label});
        String[] split = splitLabel(label);
        if (split != null) options.add(split);
        for (float s = base; s >= base * 0.55f; s -= base * 0.05f) {
            for (String[] lines : options) {
                int w = 0;
                for (String l : lines) w = Math.max(w, textWidth(l, bold));
                float bw = w * s, bh = lines.length * 9f * s;
                if (boxInside(x - bw / 2f, y - bh / 2f, x + bw / 2f, y + bh / 2f, a0, a1)) return new Fit(lines, s);
            }
        }
        // last resort: shorten with an ellipsis
        float s = base * 0.55f;
        String t = label;
        while (t.length() > 2) {
            t = t.substring(0, t.length() - 1);
            float bw = textWidth(t + "…", bold) * s;
            if (boxInside(x - bw / 2f, y - 4.5f * s, x + bw / 2f, y + 4.5f * s, a0, a1)) return new Fit(new String[]{t + "…"}, s);
        }
        return new Fit(new String[]{label}, s);
    }

    /** "BlatantKillAura" → {"Blatant", "KillAura"}, "Min. Verzögerung" → {"Min.", "Verzögerung"}. */
    private static String[] splitLabel(String label) {
        int best = -1;
        for (int i = 1; i < label.length(); i++) {
            boolean camel = Character.isUpperCase(label.charAt(i)) && Character.isLowerCase(label.charAt(i - 1));
            boolean space = label.charAt(i - 1) == ' ' || label.charAt(i - 1) == '-';
            if (camel || space) {
                if (best < 0 || Math.abs(i - label.length() / 2) < Math.abs(best - label.length() / 2)) best = i;
            }
        }
        if (best < 0) return null;
        return new String[]{label.substring(0, best).trim(), label.substring(best).trim()};
    }

    private void drawFit(GuiGraphicsExtractor ctx, Fit fit, float x, float y, int color, boolean bold) {
        float lh = 9f * fit.scale();
        float yy = y - (fit.lines().length - 1) * lh / 2f;
        for (String l : fit.lines()) {
            text(ctx, l, x, yy, fit.scale(), color, bold);
            yy += lh;
        }
    }

    // =============================================================================================
    // Rendering
    // =============================================================================================

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // no background: the wheel floats directly over the game (no orange tint, no blur)
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        long now = System.currentTimeMillis();
        if (closingAt >= 0 && now - closingAt > 170) {
            if (!closed) {
                closed = true;
                minecraft.execute(RadialScreen.super::onClose);
            }
            return;
        }
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float time = (now - openedAt) / 1000f;
        layout();
        Shapes.setPixelSize(1f / Math.max(1, minecraft.getWindow().getGuiScale()));
        hint = null;

        List<Entry> entries = entries();
        int n = entries.size();
        int hovered = dragging == null ? hitTest(mouseX, mouseY, n) : draggingIndex;
        float close = closeFactor(now);
        float openT = easeBack((now - openedAt) / 420f) * close;

        // whole wheel scales in when the screen opens / closes
        ctx.pose().pushMatrix();
        float ws = 0.86f + 0.14f * openT;
        ctx.pose().translate(cx, cy);
        ctx.pose().scale(ws, ws);
        ctx.pose().translate(-cx, -cy);

        List<WheelModelsRenderState.Model> models = new ArrayList<>();
        float step = n > 0 ? TAU / n : TAU;

        // ---------------- glass segments ----------------
        float[] pop = new float[n];
        for (int i = 0; i < n; i++) {
            Entry e = entries.get(i);
            float k = Math.min(1f, dt * 14f);
            hoverAnim[i] += ((i == hovered ? 1f : 0f) - hoverAnim[i]) * k;
            activeAnim[i] += ((e.active() ? 1f : 0f) - activeAnim[i]) * Math.min(1f, dt * 10f);
            pop[i] = easeBack((now - levelChangedAt - i * 32L) / 340f) * close;

            float h = hoverAnim[i];
            float mid = i * step, a0 = mid - step / 2f, a1 = mid + step / 2f;
            float push = R * 0.035f * h;
            float ox = (float) Math.sin(mid) * push, oy = -(float) Math.cos(mid) * push;
            float scx = cx + (float) Math.sin(mid) * R * 0.7f, scy = cy - (float) Math.cos(mid) * R * 0.7f;

            ctx.pose().pushMatrix();
            ctx.pose().translate(scx + ox, scy + oy);
            float ps = 0.55f + 0.45f * pop[i];
            ctx.pose().scale(ps, ps);
            ctx.pose().translate(-scx, -scy);

            float alpha = Math.max(0f, Math.min(1f, pop[i]));
            float act = activeAnim[i];
            int in = Shapes.lerp(Shapes.lerp(GLASS_IN, GLASS_HOVER_IN, h), GLASS_ON_IN, act);
            int out = Shapes.lerp(Shapes.lerp(GLASS_OUT, GLASS_HOVER_OUT, h), GLASS_ON_OUT, act);
            if (LIQUID_GLASS_POC && level == Level.CATEGORIES && i == 0) {
                // proof of concept: this one segment is real liquid glass (blur + refraction shader)
                LiquidGlass.sector(ctx, cx, cy, rIn, rOut, a0, a1, gap, corner, alpha);
                ctx.pose().popMatrix();
                continue;
            }
            Shapes s = new Shapes();
            // faint warm tint (same as the HUD pills) so the glass stays visible over bright terrain
            s.roundedSector(cx, cy, rIn, rOut, a0, a1, gap, corner, Shapes.alpha(TINT, alpha), Shapes.alpha(TINT, alpha));
            s.roundedSector(cx, cy, rIn, rOut, a0, a1, gap, corner, Shapes.alpha(in, alpha), Shapes.alpha(out, alpha));
            // specular shine along the outer rim (frosted glass highlight)
            s.roundedSector(cx, cy, rOut - R * 0.09f, rOut - R * 0.012f, a0, a1, gap + R * 0.03f, R * 0.03f,
                    0x00FFFFFF, Shapes.alpha(SHINE, alpha * (0.7f + 0.6f * h)));
            int rim = Shapes.lerp(Shapes.lerp(RIM, RIM_HOVER, h), Shapes.alpha(ACCENT, 0.85f), act * 0.8f);
            s.roundedSectorOutline(cx, cy, rIn, rOut, a0, a1, gap, corner, Math.max(1f, R * 0.006f), Shapes.alpha(rim, alpha));
            s.submit(ctx);
            ctx.pose().popMatrix();
        }

        // ---------------- centre disc + 3 orange arcs ----------------
        centerHover += ((hovered == -2 && level != Level.CATEGORIES ? 1f : 0f) - centerHover) * Math.min(1f, dt * 14f);
        Shapes c = new Shapes();
        c.circle(cx, cy, discR, Shapes.lerp(0x46200805, 0x60301008, centerHover), 0x5A2A0C06);
        c.ring(cx, cy, discR, Math.max(1f, R * 0.005f), 0x26FFFFFF);
        c.ring(cx, cy, ringR, R * 0.03f, 0x10FFFFFF); // faint track
        float sweep = ease((now - openedAt) / 600f) * close;
        float rot = time * 0.32f;
        float[][] arcs = {{0.02f, 0.27f}, {0.36f, 0.61f}, {0.70f, 0.93f}}; // three long arcs
        float aw = Math.max(1.5f, R * 0.016f);
        for (float[] arc : arcs) {
            float a0 = arc[0] * TAU + rot;
            float a1 = a0 + (arc[1] - arc[0]) * TAU * sweep;
            c.arc(cx, cy, ringR, aw, a0, a1, ACCENT);
            // rounded arc caps
            c.circle(cx + (float) Math.sin(a0) * ringR, cy - (float) Math.cos(a0) * ringR, aw / 2f, ACCENT);
            c.circle(cx + (float) Math.sin(a1) * ringR, cy - (float) Math.cos(a1) * ringR, aw / 2f, ACCENT);
        }
        c.submit(ctx);

        // ---------------- setting widgets (shapes) ----------------
        if (level == Level.SETTINGS) {
            Shapes w = new Shapes();
            for (int i = 0; i < n; i++) widgetShapes(w, entries.get(i), i, step, pop[i], dt);
            w.submit(ctx);
        }

        // ---------------- 3D models ----------------
        float center = easeBack((now - levelChangedAt) / 360f) * close;
        float iconSize = R * 0.19f;
        for (int i = 0; i < n; i++) {
            Entry e = entries.get(i);
            if (e.icon() == null) continue;
            float h = hoverAnim[i];
            float mid = i * step;
            float push = R * 0.035f * h;
            float bx = cx + (float) Math.sin(mid) * (R * 0.71f + push);
            float by = cy - (float) Math.cos(mid) * (R * 0.71f + push);
            float spin = time * (55f + 140f * h) + i * 47f;
            float size = iconSize * pop[i] * (1f + 0.14f * h);
            if (size > 0.5f) models.add(new WheelModelsRenderState.Model(e.icon(), bx, by - R * 0.085f, size, spin, 12f));
        }
        float cSize = R * 0.34f * center;
        if (level == Level.CATEGORIES) {
            ItemStack head = head();
            if (head != null && cSize > 0.5f) {
                // the head always looks at the mouse cursor (like the player in the inventory)
                float hx = cx, hy = cy - R * 0.075f;
                float dx = mouseX - hx, dy = mouseY - hy;
                float reach = R * 0.9f;
                float targetYaw = (float) Math.toDegrees(Math.atan(dx / reach)) * 0.6f;
                float targetPitch = (float) Math.toDegrees(Math.atan(dy / reach)) * 0.45f;
                targetYaw = Math.max(-45f, Math.min(45f, targetYaw));
                targetPitch = Math.max(-28f, Math.min(28f, targetPitch));
                if (!headInit) { headYaw = targetYaw; headPitch = targetPitch; headInit = true; }
                float follow = Math.min(1f, dt * 12f);
                headYaw += (targetYaw - headYaw) * follow;
                headPitch += (targetPitch - headPitch) * follow;
                models.add(new WheelModelsRenderState.Model(head, hx, hy, cSize, 180f + YAW_SIGN * headYaw, PITCH_SIGN * headPitch));
            }
        } else {
            ItemStack icon = level == Level.MODULES ? category.getIcon() : module.getIcon();
            if (cSize > 0.5f) models.add(new WheelModelsRenderState.Model(icon, cx, cy - R * 0.085f, cSize * 0.8f, time * 45f, 12f));
        }

        ctx.pose().popMatrix();

        // models + text in their own strata so they are always on top of the glass
        ctx.nextStratum();
        if (!models.isEmpty()) ctx.guiRenderState.addPicturesInPictureState(new WheelModelsRenderState(width, height, models));
        ctx.nextStratum();

        ctx.pose().pushMatrix();
        ctx.pose().translate(cx, cy);
        ctx.pose().scale(ws, ws);
        ctx.pose().translate(-cx, -cy);

        // ---------------- labels ----------------
        float baseScale = Math.max(0.75f, Math.min(1.45f, R / 128f));
        for (int i = 0; i < n; i++) {
            Entry e = entries.get(i);
            float h = hoverAnim[i];
            float mid = i * step, a0 = mid - step / 2f, a1 = mid + step / 2f;
            float push = R * 0.035f * h;
            float bx = cx + (float) Math.sin(mid) * (R * 0.71f + push);
            float by = cy - (float) Math.cos(mid) * (R * 0.71f + push);
            float alpha = Math.max(0f, Math.min(1f, pop[i]));
            if (alpha < 0.05f) continue;
            int color = Shapes.alpha(Shapes.lerp(TEXT, 0xFFFFFFFF, h), alpha);
            if (level == Level.SETTINGS) {
                widgetText(ctx, e, i, mid, a0, a1, bx, by, baseScale, alpha, h);
            } else {
                float ly = by + R * 0.115f;
                Fit fit = fitLabel(e.label(), bx, ly, baseScale, a0, a1, false);
                drawFit(ctx, fit, bx, ly, color, false);
            }
            if (i == hovered) hint = e.description();
        }

        // ---------------- centre text (always fitted into the disc) ----------------
        int ca = Shapes.alpha(0xFFFFFFFF, Math.max(0f, Math.min(1f, center)));
        int backColor = Shapes.lerp(Shapes.alpha(TEXT_DIM, center * 0.7f), Shapes.alpha(VALUE, center), centerHover);
        float nameScale = Math.max(0.85f, Math.min(1.8f, R / 100f));
        switch (level) {
            case CATEGORIES -> centerText(ctx, minecraft.getUser().getName(), cy + R * 0.205f, nameScale * 0.92f, ca, false);
            case MODULES -> {
                centerText(ctx, category.getDisplayName(), cy + R * 0.165f, nameScale, ca, true);
                centerText(ctx, "‹ zurück", cy + R * 0.265f, nameScale * 0.62f, backColor, false);
            }
            case SETTINGS -> {
                centerText(ctx, module.getName(), cy + R * 0.12f, nameScale * 0.85f, ca, true);
                int pages = pageCount(allEntries().size());
                float backY = cy + R * 0.235f;
                if (pages > 1) {
                    // page dots instead of "Seite x/y"
                    Shapes dots = new Shapes();
                    float dr = Math.max(1.6f, R * 0.017f), sp = R * 0.07f;
                    float dx0 = cx - (pages - 1) * sp / 2f, dy = cy + R * 0.205f;
                    for (int p = 0; p < pages; p++) {
                        int col = p == page ? Shapes.alpha(ACCENT, center) : Shapes.alpha(0x8CFFFFFF, center);
                        dots.circle(dx0 + p * sp, dy, p == page ? dr * 1.35f : dr, col);
                    }
                    dots.submit(ctx);
                    backY = cy + R * 0.275f;
                }
                centerText(ctx, "‹ zurück", backY, nameScale * 0.62f, backColor, false);
            }
        }
        if (hovered == -2 && level != Level.CATEGORIES) hint = "Zurück";
        ctx.pose().popMatrix();

        renderHint(ctx, close);
    }

    private ItemStack head() {
        if (headStack == null && minecraft.player != null) {
            headStack = new ItemStack(Items.PLAYER_HEAD);
            headStack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(minecraft.player.getGameProfile()));
        }
        return headStack;
    }

    // ---- setting widgets ------------------------------------------------------------------------

    private void widgetShapes(Shapes s, Entry e, int i, float step, float pop, float dt) {
        float alpha = Math.max(0f, Math.min(1f, pop));
        if (alpha < 0.05f) return;
        float mid = i * step, a0 = mid - step / 2f, a1 = mid + step / 2f;
        float push = R * 0.035f * hoverAnim[i];
        float bx = cx + (float) Math.sin(mid) * (R * 0.71f + push);
        float by = cy - (float) Math.cos(mid) * (R * 0.71f + push);
        boolean toggle = e.moduleToggle() || e.setting() instanceof BoolSetting;
        if (toggle) {
            float on = activeAnim[i];
            float pw = R * 0.15f, ph = R * 0.075f;
            float px = bx - pw / 2f, py = by + R * 0.04f;
            s.roundRect(px, py, px + pw, py + ph, ph / 2f, Shapes.alpha(Shapes.lerp(0x40FFFFFF, ACCENT, on), alpha));
            float kr = ph / 2f - Math.max(1f, R * 0.01f);
            float kx = px + ph / 2f + (pw - ph) * on;
            s.circle(kx, py + ph / 2f, kr, Shapes.alpha(0xFFFFFFFF, alpha));
        } else if (e.setting() instanceof NumberSetting num) {
            valueAnim[i] += ((float) num.getFraction() - valueAnim[i]) * Math.min(1f, dt * 18f);
            float off = (gap / 2f + R * 0.06f) / (rOut - R * 0.075f);
            float s0 = a0 + off, s1 = a1 - off;
            float r = rOut - R * 0.075f, tw = Math.max(2f, R * 0.022f);
            float ox = (float) Math.sin(mid) * push, oy = -(float) Math.cos(mid) * push;
            s.arc(cx + ox, cy + oy, r, tw, s0, s1, Shapes.alpha(0x38FFFFFF, alpha));
            float fe = s0 + (s1 - s0) * valueAnim[i];
            if (fe > s0 + 0.001f) s.arc(cx + ox, cy + oy, r, tw, s0, fe, Shapes.alpha(ACCENT, alpha));
            float kx = cx + ox + (float) Math.sin(fe) * r, ky = cy + oy - (float) Math.cos(fe) * r;
            s.circle(kx, ky, tw * (num == dragging ? 1.6f : 1.2f), Shapes.alpha(0xFFFFFFFF, alpha));
        }
    }

    private void widgetText(GuiGraphicsExtractor ctx, Entry e, int i, float mid, float a0, float a1, float bx, float by,
                            float base, float alpha, float h) {
        int color = Shapes.alpha(Shapes.lerp(TEXT, 0xFFFFFFFF, h), alpha);
        int value = Shapes.alpha(VALUE, alpha);
        float labelY = by - R * 0.06f;
        Fit fit = fitLabel(e.label(), bx, labelY, base * 0.9f, a0, a1, false);
        drawFit(ctx, fit, bx, labelY, color, false);
        String v = null;
        if (e.setting() instanceof NumberSetting num) v = num.display();
        else if (e.setting() instanceof ModeSetting mode) v = "‹ " + mode.get() + " ›";
        else if (e.setting() instanceof KeySetting key) v = key == listening ? "Taste drücken …" : key.display();
        if (v != null) {
            float vy = by + R * 0.075f;
            Fit vf = fitLabel(v, bx, vy, base * 1.05f, a0, a1, true);
            drawFit(ctx, vf, bx, vy, value, true);
        }
    }

    private void renderHint(GuiGraphicsExtractor ctx, float close) {
        if (hint == null) return; // like the reference: no permanent help text
        float s = Math.max(0.75f, Math.min(1.1f, R / 140f));
        int w = textWidth(hint, false);
        if (w * s > width - 20) s = (width - 20f) / w;
        text(ctx, hint, width / 2f, height - 11f, s, Shapes.alpha(TEXT_DIM, close), false);
    }

    // =============================================================================================
    // Input
    // =============================================================================================

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (closingAt >= 0) return true;
        int button = click.button();
        if (listening != null) {
            if (button == InputConstants.MOUSE_BUTTON_RIGHT) listening.set(-1);
            listening = null;
            return true;
        }
        List<Entry> entries = entries();
        int hit = hitTest(click.x(), click.y(), entries.size());
        if (hit == -2) {
            back();
            return true;
        }
        if (hit < 0) {
            back();
            return true;
        }
        Entry e = entries.get(hit);
        boolean right = button == InputConstants.MOUSE_BUTTON_RIGHT;
        switch (level) {
            case CATEGORIES -> enter(Level.MODULES, e.category(), null);
            case MODULES -> {
                if (right || button == InputConstants.MOUSE_BUTTON_MIDDLE) enter(Level.SETTINGS, category, e.module());
                else e.module().toggle();
            }
            case SETTINGS -> {
                if (e.moduleToggle()) module.toggle();
                else if (e.setting() instanceof BoolSetting b) b.toggle();
                else if (e.setting() instanceof ModeSetting m) m.cycle(!right);
                else if (e.setting() instanceof KeySetting k) {
                    if (right) k.set(-1); else listening = k;
                } else if (e.setting() instanceof NumberSetting num) {
                    if (right) num.set(num.get() - num.getStep());
                    else {
                        dragging = num;
                        draggingIndex = hit;
                        dragTo(num, hit, entries.size(), click.x(), click.y());
                    }
                }
            }
        }
        return true;
    }

    private void dragTo(NumberSetting num, int index, int n, double mx, double my) {
        float step = TAU / n;
        float mid = index * step, a0 = mid - step / 2f, a1 = mid + step / 2f;
        float off = (gap / 2f + R * 0.06f) / (rOut - R * 0.075f);
        float s0 = a0 + off, s1 = a1 - off;
        float a = (float) Math.atan2(mx - cx, -(my - cy));
        float rel = a - mid;
        while (rel > Math.PI) rel -= TAU;
        while (rel < -Math.PI) rel += TAU;
        num.setFraction((mid + rel - s0) / (s1 - s0));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
        if (dragging != null) {
            dragTo(dragging, draggingIndex, entries().size(), click.x(), click.y());
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        dragging = null;
        draggingIndex = -1;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (level == Level.SETTINGS) {
            List<Entry> entries = entries();
            int hit = hitTest(mouseX, mouseY, entries.size());
            if (hit >= 0 && entries.get(hit).setting() instanceof NumberSetting num) {
                num.set(num.get() + Math.signum(verticalAmount) * num.getStep());
                return true;
            }
            int pages = pageCount(allEntries().size());
            if (pages > 1) {
                int np = Math.max(0, Math.min(pages - 1, page - (int) Math.signum(verticalAmount)));
                if (np != page) {
                    page = np;
                    restartAnimations();
                }
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        int key = input.key();
        if (listening != null) {
            if (key == InputConstants.KEY_ESCAPE) {
                // cancel
            } else if (key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE) {
                listening.set(-1);
            } else {
                listening.set(key);
            }
            listening = null;
            return true;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            if (level == Level.CATEGORIES) onClose(); else back();
            return true;
        }
        if (Kraken.openWheel.matches(input)) {
            onClose();
            return true;
        }
        return super.keyPressed(input);
    }

    private void back() {
        switch (level) {
            case SETTINGS -> enter(Level.MODULES, category, null);
            case MODULES -> enter(Level.CATEGORIES, null, null);
            case CATEGORIES -> {}
        }
    }

    private void enter(Level l, Category c, Module m) {
        level = l;
        category = c;
        module = m;
        page = 0;
        dragging = null;
        listening = null;
        restartAnimations();
    }

    private void restartAnimations() {
        levelChangedAt = System.currentTimeMillis();
        Arrays.fill(hoverAnim, 0f);
        Arrays.fill(activeAnim, 0f);
        Arrays.fill(valueAnim, 0f);
        centerHover = 0f;
    }

    @Override
    public void onClose() {
        // play the closing animation first; the screen is really closed after it finished
        if (closingAt < 0) closingAt = System.currentTimeMillis();
    }

    @Override
    public void removed() {
        ConfigManager.save();
        super.removed();
    }
}
