package dev.kraken.hud;

import com.google.gson.JsonObject;
import dev.kraken.gui.Shapes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * A movable HUD element. The position is stored relative to the nearest screen edge (or the centre)
 * so the layout survives window resizes.
 */
public abstract class HudElement {
    public static final int START = 0, CENTER = 1, END = 2;
    protected static final Minecraft mc = Minecraft.getInstance();

    private final String id;
    private final String name;
    public boolean enabled;
    public int anchorX, anchorY;
    public float offX, offY;

    private final boolean defEnabled;
    private final int defAnchorX, defAnchorY;
    private final float defOffX, defOffY;

    protected HudElement(String id, String name, boolean enabled, int anchorX, int anchorY, float offX, float offY) {
        this.id = id;
        this.name = name;
        this.defEnabled = enabled;
        this.defAnchorX = anchorX;
        this.defAnchorY = anchorY;
        this.defOffX = offX;
        this.defOffY = offY;
        reset();
    }

    public String id() { return id; }
    public String name() { return name; }

    public void reset() {
        enabled = defEnabled;
        anchorX = defAnchorX;
        anchorY = defAnchorY;
        offX = defOffX;
        offY = defOffY;
    }

    /** Size in GUI pixels (editor = true: size of the preview content). */
    public abstract float width(boolean editor);
    public abstract float height(boolean editor);

    /**
     * Draws the element with its top-left corner at (x, y). Shapes go into {@code s}, text is deferred
     * into {@code text} so it is drawn on top of all glass.
     */
    public abstract void render(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float x, float y, float dt, boolean editor);

    /** Called every client tick (for measurements). */
    public void tick() {}

    public boolean rightAligned() { return anchorX == END; }
    public boolean bottomAligned() { return anchorY == END; }

    public float x(float screenW, boolean editor) {
        float w = width(editor);
        return switch (anchorX) {
            case END -> screenW - offX - w;
            case CENTER -> screenW / 2f + offX - w / 2f;
            default -> offX;
        };
    }

    public float y(float screenH, boolean editor) {
        float h = height(editor);
        return switch (anchorY) {
            case END -> screenH - offY - h;
            case CENTER -> screenH / 2f + offY - h / 2f;
            default -> offY;
        };
    }

    /** Re-anchors the element to the closest edge/centre for a top-left position (x, y). */
    public void setPosition(float x, float y, float screenW, float screenH, boolean editor) {
        float w = width(editor), h = height(editor);
        float cx = x + w / 2f, cy = y + h / 2f;
        anchorX = cx < screenW / 3f ? START : cx > screenW * 2f / 3f ? END : CENTER;
        anchorY = cy < screenH / 3f ? START : cy > screenH * 2f / 3f ? END : CENTER;
        offX = switch (anchorX) {
            case END -> screenW - x - w;
            case CENTER -> cx - screenW / 2f;
            default -> x;
        };
        offY = switch (anchorY) {
            case END -> screenH - y - h;
            case CENTER -> cy - screenH / 2f;
            default -> y;
        };
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", enabled);
        o.addProperty("ax", anchorX);
        o.addProperty("ay", anchorY);
        o.addProperty("x", offX);
        o.addProperty("y", offY);
        return o;
    }

    public void fromJson(JsonObject o) {
        if (o.has("enabled")) enabled = o.get("enabled").getAsBoolean();
        if (o.has("ax")) anchorX = o.get("ax").getAsInt();
        if (o.has("ay")) anchorY = o.get("ay").getAsInt();
        if (o.has("x")) offX = o.get("x").getAsFloat();
        if (o.has("y")) offY = o.get("y").getAsFloat();
    }

    /** Helper for simple one-pill elements "LABEL value". */
    protected static float labelValueWidth(String label, String value) {
        float w = HudStyle.PAD;
        if (label != null && !label.isEmpty()) w += HudStyle.width(label, true, HudStyle.TEXT_SCALE) + 4f;
        w += HudStyle.width(value, false, HudStyle.TEXT_SCALE) + HudStyle.PAD;
        return w;
    }

    protected static void labelValue(Shapes s, List<Runnable> text, GuiGraphicsExtractor ctx, float x, float y, String label, String value, int valueColor) {
        float w = labelValueWidth(label, value);
        HudStyle.pill(ctx, s, x, y, x + w, y + HudStyle.PILL_H, 1f);
        float cy = y + HudStyle.PILL_H / 2f;
        text.add(() -> {
            float tx = x + HudStyle.PAD;
            if (label != null && !label.isEmpty()) {
                HudStyle.text(ctx, label, tx, cy, HudStyle.TEXT_SCALE, HudStyle.LABEL, true);
                tx += HudStyle.width(label, true, HudStyle.TEXT_SCALE) + 4f;
            }
            HudStyle.text(ctx, value, tx, cy, HudStyle.TEXT_SCALE, valueColor, false);
        });
    }
}
