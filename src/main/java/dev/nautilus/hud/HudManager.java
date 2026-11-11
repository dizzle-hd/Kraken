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
                () -> mc.getFps() >= 60 ? HudStyle.TEXT : mc.getFps() >= 30 ? 0xFFFFD27A : HudStyle.BAD)
                .tile(Icons.MONITOR, "FPS", () -> Math.min(1.0, mc.getFps() / 240.0)));
        elements.add(new ValueElement("ping", "Ping", "Ping", true, START, START, 5, 55,
                () -> String.valueOf(ping()),
                () -> ping() < 80 ? HudStyle.TEXT : ping() < 180 ? 0xFFFFD27A : HudStyle.BAD)
                .tile(Icons.SIGNAL, "ms", () -> 1.0 - Math.min(1.0, ping() / 300.0)));
        elements.add(new ValueElement("tps", "TPS", "TPS", true, START, START, 5, 79,
                () -> String.format(Locale.ROOT, "%.1f", TpsTracker.tps()),
                () -> TpsTracker.tps() >= 18 ? HudStyle.TEXT : TpsTracker.tps() >= 12 ? 0xFFFFD27A : HudStyle.BAD)
                .tile(Icons.SERVER, "TPS", () -> TpsTracker.tps() / 20.0));
        elements.add(new ValueElement("speed", "Speed", "Speed", false, START, END, 5, 39,
                () -> String.format(Locale.ROOT, "%.1f", speed), () -> HudStyle.TEXT)
                .tile(Icons.BOLT, "b/s", () -> Math.min(1.0, speed / 20.0)));
        elements.add(new ValueElement("direction", "Direction", "Facing", false, CENTER, START, 0, 5,
                this::direction, () -> HudStyle.TEXT).tile(Icons.COMPASS, "", null));
        elements.add(new dev.nautilus.hud.elements.ClockElement());
        elements.add(new NotificationsElement());
        elements.add(new dev.nautilus.hud.elements.TargetElement());
        elements.add(new dev.nautilus.hud.elements.SmashElement());
        elements.add(new dev.nautilus.hud.elements.ArmorElement());
        elements.add(new dev.nautilus.hud.elements.ArmorHudElement());
        elements.add(new dev.nautilus.hud.elements.EffectsElement());
        elements.add(new dev.nautilus.hud.elements.SuppliesElement());
        elements.add(new dev.nautilus.hud.elements.KeystrokesElement());
        elements.add(new dev.nautilus.hud.elements.InventoryElement());
        elements.add(new ValueElement("cps", "CPS", "CPS", false, START, START, 5, 103,
                () -> ClickTracker.left() + " | " + ClickTracker.right(), () -> HudStyle.TEXT)
                .tile(Icons.STAR, "CPS", () -> Math.min(1.0, ClickTracker.left() / 16.0)));
        elements.add(new ValueElement("server", "Server", "Server", false, START, START, 5, 127,
                this::server, () -> HudStyle.TEXT).tile(Icons.SERVER, "", null));
        elements.add(new ValueElement("day", "Day", "Day", false, CENTER, START, 0, 28,
                () -> mc.level == null ? "1" : String.valueOf(mc.level.getOverworldClockTime() / 24000L + 1), () -> HudStyle.TEXT)
                .tile(Icons.CLOCK, "Day", null));
        elements.add(new ValueElement("death", "Death point", "Death", false, START, END, 5, 63,
                this::deathPoint, () -> HudStyle.TEXT).tile(Icons.SKULL, "", null));
        elements.add(new dev.nautilus.hud.elements.KeybindsElement());
    }

    public List<HudElement> elements() { return elements; }

    private static int ping() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return 0;
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : info.getLatency();
    }

    private String server() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isLocalServer() || mc.getCurrentServer() == null) return "Singleplayer";
        return mc.getCurrentServer().ip;
    }

    /** Coordinates of your last death (from the server), with the distance when you are in the same dimension. */
    private String deathPoint() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return "120 64 -340";
        var loc = mc.player.getLastDeathLocation();
        if (loc.isEmpty()) return "–";
        var p = loc.get().pos();
        String s = p.getX() + " " + p.getY() + " " + p.getZ();
        if (loc.get().dimension() == mc.level.dimension()) {
            s += "  (" + (int) Math.sqrt(mc.player.blockPosition().distSqr(p)) + " m)";
        } else {
            s += "  (" + loc.get().dimension().identifier().getPath() + ")";
        }
        return s;
    }

    private String direction() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "North";
        Direction d = mc.player.getDirection();
        String name = switch (d) {
            case NORTH -> "North  -Z";
            case SOUTH -> "South  +Z";
            case WEST -> "West  -X";
            case EAST -> "East  +X";
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
        ClickTracker.update();
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
