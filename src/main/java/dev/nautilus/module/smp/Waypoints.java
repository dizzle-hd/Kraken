package dev.nautilus.module.smp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import dev.nautilus.gui.Shapes;
import dev.nautilus.hud.HudStyle;
import dev.nautilus.hud.Notifications;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.KeySetting;
import dev.nautilus.setting.NumberSetting;
import dev.nautilus.util.Projection;
import dev.nautilus.util.Render3D;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Waypoints per server and dimension: set one on a key, remove the nearest on another key, and an
 * automatic one where you died. Shown as a label with the distance (through walls) and a beam.
 */
public class Waypoints extends Module {
    public record Waypoint(String name, int x, int y, int z, String dimension, String server, boolean death) {
        Vec3 center() { return new Vec3(x + 0.5, y + 0.5, z + 0.5); }
    }

    private static final List<Waypoint> POINTS = new ArrayList<>();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final KeySetting setKey = add(new KeySetting("Setzen-Taste", "Setzt einen Wegpunkt an deiner Position", -1));
    private final KeySetting removeKey = add(new KeySetting("Löschen-Taste", "Löscht den nächsten Wegpunkt (in dieser Dimension)", -1));
    private final BoolSetting deathPoints = add(new BoolSetting("Todespunkte", "Setzt automatisch einen Wegpunkt, wo du stirbst (die letzten 3)", true));
    private final BoolSetting beam = add(new BoolSetting("Strahl", "Senkrechter Strahl und Box am Wegpunkt", true));
    private final NumberSetting maxDist = add(new NumberSetting("Max. Distanz", "Weiter entfernte Wegpunkte werden ausgeblendet (0 = alle)", 0, 0, 10000, 250));
    private final BoolSetting clearAll = add(new BoolSetting("Alle löschen", "Einschalten löscht alle Wegpunkte dieses Servers (springt danach wieder auf aus)", false));

    private boolean setWas, removeWas, wasDead;

    public Waypoints() {
        super("Waypoints", "Wegpunkte pro Server und Dimension mit Entfernung, durch Wände sichtbar. Tasten zum Setzen/Löschen, automatischer Todespunkt.",
                Category.SMP, Items.RECOVERY_COMPASS);
        loadEnabled(true);
    }

    // ---- storage ----------------------------------------------------------------------------------

    public static JsonArray toJson() {
        JsonArray a = new JsonArray();
        for (Waypoint w : POINTS) {
            JsonObject o = new JsonObject();
            o.addProperty("name", w.name());
            o.addProperty("x", w.x()); o.addProperty("y", w.y()); o.addProperty("z", w.z());
            o.addProperty("dim", w.dimension());
            o.addProperty("server", w.server());
            o.addProperty("death", w.death());
            a.add(o);
        }
        return a;
    }

    public static void fromJson(JsonElement el) {
        POINTS.clear();
        if (el == null || !el.isJsonArray()) return;
        for (JsonElement e : el.getAsJsonArray()) {
            try {
                JsonObject o = e.getAsJsonObject();
                POINTS.add(new Waypoint(o.get("name").getAsString(), o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt(),
                        o.get("dim").getAsString(), o.get("server").getAsString(), o.has("death") && o.get("death").getAsBoolean()));
            } catch (Exception ignored) {
            }
        }
    }

    private static String server() {
        if (mc.getSingleplayerServer() != null) return "sp:" + mc.getSingleplayerServer().getWorldData().getLevelName();
        return mc.getCurrentServer() == null ? "?" : mc.getCurrentServer().ip.toLowerCase(Locale.ROOT);
    }

    private static String dimension() { return mc.level.dimension().identifier().toString(); }

    /** Waypoints of this server and dimension. */
    private List<Waypoint> here() {
        String srv = server(), dim = dimension();
        List<Waypoint> list = new ArrayList<>();
        for (Waypoint w : POINTS) if (w.server().equals(srv) && w.dimension().equals(dim)) list.add(w);
        return list;
    }

    private void addPoint(String name, BlockPos p, boolean death) {
        POINTS.add(new Waypoint(name, p.getX(), p.getY(), p.getZ(), dimension(), server(), death));
        if (death) { // keep the last three deaths of this server
            String srv = server();
            List<Waypoint> deaths = new ArrayList<>();
            for (Waypoint w : POINTS) if (w.death() && w.server().equals(srv)) deaths.add(w);
            while (deaths.size() > 3) POINTS.remove(deaths.remove(0));
        }
        dev.nautilus.config.ConfigManager.save();
    }

    // ---- logic ------------------------------------------------------------------------------------

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;
        if (clearAll.isOn()) {
            clearAll.set(false);
            String srv = server();
            POINTS.removeIf(w -> w.server().equals(srv));
            Notifications.push("Waypoints", "Alle Wegpunkte gelöscht", HudStyle.accent());
            dev.nautilus.config.ConfigManager.save();
        }
        boolean free = mc.gui.screen() == null;
        boolean s = free && setKey.isBound() && InputConstants.isKeyDown(setKey.get());
        if (s && !setWas) {
            String name = "WP " + (here().size() + 1);
            addPoint(name, mc.player.blockPosition(), false);
            Notifications.push("Waypoints", name + " gesetzt", HudStyle.GOOD);
        }
        setWas = s;
        boolean r = free && removeKey.isBound() && InputConstants.isKeyDown(removeKey.get());
        if (r && !removeWas) {
            Waypoint nearest = null;
            double best = Double.MAX_VALUE;
            for (Waypoint w : here()) {
                double d = w.center().distanceToSqr(mc.player.position());
                if (d < best) { best = d; nearest = w; }
            }
            if (nearest != null) {
                POINTS.remove(nearest);
                dev.nautilus.config.ConfigManager.save();
                Notifications.push("Waypoints", nearest.name() + " gelöscht", HudStyle.BAD);
            }
        }
        removeWas = r;
        boolean dead = mc.player.isDeadOrDying();
        if (dead && !wasDead && deathPoints.isOn()) addPoint("Tod " + LocalTime.now().format(TIME), mc.player.blockPosition(), true);
        wasDead = dead;
    }

    private boolean visible(Waypoint w) {
        return maxDist.getInt() <= 0 || w.center().distanceTo(mc.player.position()) <= maxDist.get();
    }

    private static int color(Waypoint w) { return w.death() ? HudStyle.BAD : HudStyle.accent(); }

    @Override
    public void onRender3D(LevelRenderContext context, Render3D render) {
        if (!beam.isOn() || mc.player == null || mc.level == null) return;
        for (Waypoint w : here()) {
            if (!visible(w)) continue;
            int c = color(w);
            render.box(new AABB(new BlockPos(w.x(), w.y(), w.z())), c);
            Vec3 m = w.center();
            render.line(new Vec3(m.x, w.y(), m.z), new Vec3(m.x, w.y() + 64, m.z), c & 0x99FFFFFF);
        }
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float partialTick) {
        for (Waypoint w : here()) {
            if (!visible(w)) continue;
            float[] sc = Projection.toScreen(w.center().add(0, 1.2, 0));
            if (sc == null) continue;
            int dist = (int) Math.round(w.center().distanceTo(mc.player.position()));
            String label = w.name() + "  " + dist + "m";
            float ts = HudStyle.TEXT_SCALE * 0.9f;
            float tw = HudStyle.width(label, true, ts), h = HudStyle.PILL_H * 0.9f, wd = tw + 2 * HudStyle.PAD + 6f;
            float x1 = sc[0] - wd / 2f, y1 = sc[1] - h / 2f;
            HudStyle.pill(ctx, s, x1, y1, x1 + wd, y1 + h, 1f);
            s.circle(x1 + HudStyle.PAD + 1f, y1 + h / 2f, 2.4f, color(w));
            text.add(() -> HudStyle.text(ctx, label, x1 + HudStyle.PAD + 6f, y1 + h / 2f, ts, HudStyle.TEXT, true));
        }
    }

    @Override
    public String getInfo() { return mc.level == null ? null : String.valueOf(here().size()); }
}
