package dev.nautilus.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.nautilus.gui.Shapes;
import dev.nautilus.hud.elements.CoordsElement;
import dev.nautilus.hud.elements.ModuleListElement;
import dev.nautilus.hud.elements.NotificationsElement;
import dev.nautilus.hud.elements.ValueElement;
import dev.nautilus.hud.elements.WatermarkElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.Direction;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static dev.nautilus.hud.HudElement.CENTER;
import static dev.nautilus.hud.HudElement.END;
import static dev.nautilus.hud.HudElement.START;

/** All HUD elements, their layout and rendering. */
public final class HudManager {
    private static final HudManager INSTANCE = new HudManager();
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final List<HudElement> elements = new ArrayList<>();
    private long lastFrame = System.currentTimeMillis();
    private double lastX, lastZ;
    private boolean hasLast;
    private float speed;

    public static HudManager get() { return INSTANCE; }

    private static long frame;

    /** Increases once per HUD render: lets elements cache per-frame work. */
    public static long frame() { return frame; }

    private HudManager() {
        Minecraft mc = Minecraft.getInstance();
        elements.add(new WatermarkElement());
        elements.add(new ModuleListElement());
        elements.add(new CoordsElement());
        elements.add(new ValueElement("fps", "FPS", "FPS", true, START, START, 5, 31,
                () -> String.valueOf(mc.getFps()),
                () -> mc.getFps() >= 60 ? HudStyle.TEXT : mc.getFps() >= 30 ? 0xFFFFD27A : HudStyle.BAD));
        elements.add(new ValueElement("ping", "Ping", "Ping", true, START, START, 5, 48,
                () -> ping() + " ms",
                () -> ping() < 80 ? HudStyle.TEXT : ping() < 180 ? 0xFFFFD27A : HudStyle.BAD));
        elements.add(new ValueElement("tps", "TPS", "TPS", true, START, START, 5, 65,
                () -> String.format(Locale.ROOT, "%.1f", TpsTracker.tps()),
                () -> TpsTracker.tps() >= 18 ? HudStyle.TEXT : TpsTracker.tps() >= 12 ? 0xFFFFD27A : HudStyle.BAD));
        elements.add(new ValueElement("speed", "Geschwindigkeit", "Speed", false, START, END, 5, 39,
                () -> String.format(Locale.ROOT, "%.1f b/s", speed), () -> HudStyle.TEXT));
        elements.add(new ValueElement("direction", "Richtung", "Blick", false, CENTER, START, 0, 5,
                this::direction, () -> HudStyle.TEXT));
        elements.add(new ValueElement("clock", "Uhrzeit", "Zeit", false, END, END, 5, 5,
                () -> LocalTime.now().format(CLOCK), () -> HudStyle.TEXT));
        elements.add(new NotificationsElement());
        elements.add(new dev.nautilus.hud.elements.TargetElement());
        elements.add(new dev.nautilus.hud.elements.SmashElement());
        elements.add(new dev.nautilus.hud.elements.ArmorElement());
    }

    public List<HudElement> elements() { return elements; }

    private static int ping() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return 0;
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : info.getLatency();
    }

    private String direction() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "Norden";
        Direction d = mc.player.getDirection();
        String name = switch (d) {
            case NORTH -> "Norden  -Z";
            case SOUTH -> "Süden  +Z";
            case WEST -> "Westen  -X";
            case EAST -> "Osten  +X";
            default -> d.getName();
        };
        return name;
    }

    /** Every client tick: speed measurement. */
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            hasLast = false;
            return;
        }
        double x = mc.player.getX(), z = mc.player.getZ();
        if (hasLast) {
            double dx = x - lastX, dz = z - lastZ;
            float bps = (float) Math.sqrt(dx * dx + dz * dz) * 20f;
            speed += (bps - speed) * 0.35f;
        }
        lastX = x;
        lastZ = z;
        hasLast = true;
        for (HudElement e : elements) if (e.enabled) e.tick();
    }

    /** Renders every enabled element (in the editor: also returns positions, see HudEditorScreen). */
    public void render(GuiGraphicsExtractor ctx, boolean editor, HudElement skip) {
        frame++;
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        Shapes.setPixelSize(1f / Math.max(1, mc.getWindow().getGuiScale()));
        float sw = ctx.guiWidth(), sh = ctx.guiHeight();
        Shapes s = new Shapes();
        List<Runnable> text = new ArrayList<>();
        for (HudElement e : elements) {
            if (!e.enabled || e == skip) continue;
            e.render(ctx, s, text, e.x(sw, editor), e.y(sh, editor), dt, editor);
        }
        s.submit(ctx);
        for (Runnable r : text) r.run();
    }

    public void resetLayout() {
        for (HudElement e : elements) e.reset();
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        for (HudElement e : elements) o.add(e.id(), e.toJson());
        return o;
    }

    public void fromJson(JsonElement json) {
        if (json == null || !json.isJsonObject()) return;
        JsonObject o = json.getAsJsonObject();
        for (HudElement e : elements) {
            JsonElement el = o.get(e.id());
            if (el != null && el.isJsonObject()) e.fromJson(el.getAsJsonObject());
        }
    }
}
