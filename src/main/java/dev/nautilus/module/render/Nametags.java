package dev.nautilus.module.render;

import dev.nautilus.gui.Shapes;
import dev.nautilus.hud.HudStyle;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.NumberSetting;
import dev.nautilus.util.CombatTracker;
import dev.nautilus.util.Friends;
import dev.nautilus.util.Projection;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Glass nametags above players, visible through walls: name, health, distance, ping, totem pops and armor. */
public class Nametags extends Module {
    private final NumberSetting range = add(new NumberSetting("Range", "Maximum distance in blocks", 64, 8, 256, 8));
    private final NumberSetting scale = add(new NumberSetting("Size", "Size of the tags", 1.0, 0.5, 2.0, 0.1));
    private final BoolSetting health = add(new BoolSetting("Health", "Show health (incl. absorption)", true));
    private final BoolSetting distance = add(new BoolSetting("Distance", "Show distance in blocks", true));
    private final BoolSetting ping = add(new BoolSetting("Ping", "Show the player's ping", false));
    private final BoolSetting pops = add(new BoolSetting("Pops", "Show totem pops (with PopCounter)", true));
    private final BoolSetting armor = add(new BoolSetting("Armor", "Armor and held items above the tag", true));
    private final BoolSetting self = add(new BoolSetting("Own", "Also your own tag (only in third person)", false));

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.OFFHAND};

    public Nametags() {
        super("Nametags", "Shows a glass tag above players with name, health, distance, pops and armor – also through walls.", Category.RENDER, Items.NAME_TAG);
    }

    private static int ping(Player p) {
        if (mc.getConnection() == null) return -1;
        PlayerInfo info = mc.getConnection().getPlayerInfo(p.getUUID());
        return info == null ? -1 : info.getLatency();
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, float partialTick) {
        List<Player> players = new ArrayList<>();
        for (Player p : mc.level.players()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            if (p == mc.player && (!self.isOn() || mc.options.getCameraType().isFirstPerson())) continue;
            if (p.distanceTo(mc.player) > range.get()) continue;
            players.add(p);
        }
        // far ones first so near tags are drawn on top
        Vec3 cam = Projection.camera();
        players.sort(Comparator.comparingDouble((Player p) -> -p.position().distanceToSqr(cam)));
        for (Player p : players) tag(ctx, s, text, p, partialTick);
    }

    private void tag(GuiGraphicsExtractor ctx, Shapes s, List<Runnable> text, Player p, float pt) {
        Vec3 pos = p.getPosition(pt).add(0, p.getBbHeight() + (p.isCrouching() ? 0.3 : 0.45), 0);
        float[] sc = Projection.toScreen(pos);
        if (sc == null) return;
        double dist = mc.player.distanceTo(p);
        float k = scale.getFloat() * 0.8f * (float) Math.max(0.65, Math.min(1.0, 1.5 - dist / 40.0));

        boolean friend = Friends.is(p);
        String name = p.getGameProfile().name();
        // small grey text after the name: distance, ping, pops; the health is the bar under the name
        StringBuilder info = new StringBuilder();
        if (distance.isOn()) info.append(String.format(Locale.ROOT, "%.0fm", dist));
        int ms = ping.isOn() ? ping(p) : -1;
        if (ms >= 0) info.append(info.length() > 0 ? " · " : "").append(ms).append("ms");
        int pc = pops.isOn() ? CombatTracker.pops(p) : 0;
        if (pc > 0) info.append(info.length() > 0 ? " · " : "").append("-").append(pc);
        String inf = info.toString();

        float ts = 0.78f * k, is = 0.6f * k;
        float nameW = HudStyle.width(name, false, ts), infoW = inf.isEmpty() ? 0f : HudStyle.width(inf, false, is) + 5f * k;
        float pad = 5f * k, h = (health.isOn() ? 15f : 12f) * k, w = Math.max(40f * k, pad * 2 + nameW + infoW);
        float x1 = sc[0] - w / 2f, y2 = sc[1], y1 = y2 - h;
        HudStyle.panel(ctx, s, x1, y1, x1 + w, y2, 1f);
        if (friend) s.roundRectRim(x1, y1, x1 + w, y2, Math.min(h / 2f, 4.5f), 0.9f, HudStyle.GOOD);
        float max = Math.max(1f, p.getMaxHealth());
        if (health.isOn()) { // thin bar in the accent colour; absorption in gold after it
            float frac = Math.min(1f, p.getHealth() / max), abs = Math.min(1f - frac, p.getAbsorptionAmount() / max);
            float bx1 = x1 + pad, bx2 = x1 + w - pad, by = y2 - 4f * k;
            s.roundRect(bx1, by, bx2, by + 1.6f * k, 0.8f * k, 0x26FFFFFF);
            int c = frac < 0.3f ? HudStyle.BAD : HudStyle.accent();
            if (frac > 0.01f) s.roundRect(bx1, by, bx1 + (bx2 - bx1) * frac, by + 1.6f * k, 0.8f * k, c);
            if (abs > 0.01f) s.roundRect(bx1 + (bx2 - bx1) * frac, by, bx1 + (bx2 - bx1) * (frac + abs), by + 1.6f * k, 0.8f * k, 0xFFFFC23D);
        }
        int nameColor = friend ? HudStyle.GOOD : p.isInvisible() ? HudStyle.TEXT_DIM : HudStyle.TEXT;
        float cy = y1 + (health.isOn() ? 5.6f : 6f) * k;

        // items above the tag, each in its own small dark box
        List<ItemStack> items = new ArrayList<>();
        if (armor.isOn()) for (EquipmentSlot slot : SLOTS) {
            ItemStack st = p.getItemBySlot(slot);
            if (!st.isEmpty()) items.add(st);
        }
        float box = 11f * k, gap = 1.5f * k;
        float ix = sc[0] - (items.size() * (box + gap) - gap) / 2f, iy = y1 - box - 2f * k;
        for (int i = 0; i < items.size(); i++) { // small square boxes, slightly rounded
            float bx = ix + i * (box + gap);
            s.roundRect(bx, iy, bx + box, iy + box, 2f * k, HudStyle.panelColor(0xD4));
            s.roundRectRim(bx, iy, bx + box, iy + box, 2f * k, 0.5f, 0x26FFFFFF);
        }
        text.add(() -> {
            HudStyle.text(ctx, name, x1 + pad, cy, ts, nameColor, false);
            if (!inf.isEmpty()) HudStyle.text(ctx, inf, x1 + w - pad - HudStyle.width(inf, false, is), cy + 0.3f * k, is, HudStyle.TEXT_DIM, false);
            for (int i = 0; i < items.size(); i++) HudStyle.item(ctx, items.get(i), ix + i * (box + gap) + 1.2f * k, iy + 1.2f * k, box - 2.4f * k);
        });
    }
}
