package dev.nautilus.module.smp;

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
    private final NumberSetting range = add(new NumberSetting("Reichweite", "Maximale Entfernung in Blöcken", 64, 8, 256, 8));
    private final NumberSetting scale = add(new NumberSetting("Größe", "Größe der Schilder", 1.0, 0.5, 2.0, 0.1));
    private final BoolSetting health = add(new BoolSetting("Leben", "Leben (inkl. Absorption) anzeigen", true));
    private final BoolSetting distance = add(new BoolSetting("Distanz", "Entfernung in Blöcken anzeigen", true));
    private final BoolSetting ping = add(new BoolSetting("Ping", "Ping des Spielers anzeigen", false));
    private final BoolSetting pops = add(new BoolSetting("Pops", "Totem-Pops anzeigen (mit PopCounter)", true));
    private final BoolSetting armor = add(new BoolSetting("Rüstung", "Rüstung und Items in der Hand über dem Schild", true));
    private final BoolSetting self = add(new BoolSetting("Eigenes", "Auch dein eigenes Schild (nur in der dritten Person)", false));

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.OFFHAND};

    public Nametags() {
        super("Nametags", "Zeigt über Spielern ein Glas-Schild mit Name, Leben, Distanz, Pops und Rüstung – auch durch Wände.", Category.SMP, Items.NAME_TAG);
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
        Vec3 pos = p.getPosition(pt).add(0, p.getBbHeight() + (p.isCrouching() ? 0.35 : 0.55), 0);
        float[] sc = Projection.toScreen(pos);
        if (sc == null) return;
        double dist = mc.player.distanceTo(p);
        float k = scale.getFloat() * (float) Math.max(0.6, Math.min(1.0, 1.6 - dist / 40.0));

        boolean friend = Friends.is(p);
        String name = p.getGameProfile().name();
        StringBuilder info = new StringBuilder();
        float hp = p.getHealth() + p.getAbsorptionAmount();
        if (health.isOn()) info.append(String.format(Locale.ROOT, "%.0f", hp));
        if (distance.isOn()) info.append(info.length() > 0 ? "  " : "").append(String.format(Locale.ROOT, "%.0fm", dist));
        int ms = ping.isOn() ? ping(p) : -1;
        if (ms >= 0) info.append(info.length() > 0 ? "  " : "").append(ms).append("ms");
        int pc = pops.isOn() ? CombatTracker.pops(p) : 0;
        if (pc > 0) info.append(info.length() > 0 ? "  " : "").append("-").append(pc);
        String inf = info.toString();

        float ts = HudStyle.TEXT_SCALE * k;
        float nameW = HudStyle.width(name, true, ts), infoW = inf.isEmpty() ? 0f : HudStyle.width(inf, false, ts) + 5f * k;
        float h = HudStyle.PILL_H * k, w = HudStyle.PAD * k * 2 + nameW + infoW;
        float x1 = sc[0] - w / 2f, y2 = sc[1], y1 = y2 - h;
        HudStyle.pill(ctx, s, x1, y1, x1 + w, y2, 1f);
        if (friend) s.roundRectRim(x1, y1, x1 + w, y2, Math.min(h / 2f, 7f), 1f, HudStyle.GOOD);
        int nameColor = friend ? HudStyle.GOOD : p.isInvisible() ? HudStyle.TEXT_DIM : HudStyle.TEXT;
        float max = Math.max(1f, p.getMaxHealth());
        int hpColor = HudStyle.wear(p.getHealth() / max);
        float cy = (y1 + y2) / 2f;

        List<ItemStack> items = new ArrayList<>();
        if (armor.isOn()) for (EquipmentSlot slot : SLOTS) {
            ItemStack st = p.getItemBySlot(slot);
            if (!st.isEmpty()) items.add(st);
        }
        text.add(() -> {
            HudStyle.text(ctx, name, x1 + HudStyle.PAD * k, cy, ts, nameColor, true);
            if (!inf.isEmpty()) HudStyle.text(ctx, inf, x1 + HudStyle.PAD * k + nameW + 5f * k, cy, ts, health.isOn() ? hpColor : HudStyle.label(), false);
            float iz = 14f * k;
            float ix = sc[0] - items.size() * iz / 2f;
            for (int i = 0; i < items.size(); i++) HudStyle.item(ctx, items.get(i), ix + i * iz, y1 - iz - 1f, iz);
        });
    }
}
