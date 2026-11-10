package dev.nautilus.module.crystal;

import dev.nautilus.hud.HudStyle;
import dev.nautilus.hud.Notifications;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Pulls you into the nearest safe hole (see HoleESP) and switches itself off once you stand in it.
 * Best on a key: press, get pulled in, done.
 */
public class HoleSnap extends Module {
    private final NumberSetting range = add(new NumberSetting("Reichweite", "Maximale Entfernung zum Loch", 4, 1, 8, 0.5));
    private final NumberSetting speed = add(new NumberSetting("Tempo", "Geschwindigkeit in Blöcken pro Tick (Gehen ≈ 0,22)", 0.25, 0.1, 0.6, 0.05));
    private final BoolSetting onlyBedrock = add(new BoolSetting("Nur Bedrock", "Nur Löcher aus reinem Bedrock", false));
    private final NumberSetting timeout = add(new NumberSetting("Timeout", "Nach so vielen Ticks ohne Erfolg aufgeben", 30, 5, 100, 5));

    private BlockPos hole;
    private int ticks;

    public HoleSnap() {
        super("HoleSnap", "Zieht dich ins nächste sichere Loch (Bedrock/Obsidian) und schaltet sich danach selbst aus – am besten auf eine Taste legen.",
                Category.CRYSTAL, Items.CRYING_OBSIDIAN);
    }

    private BlockPos find() {
        BlockPos c = mc.player.blockPosition();
        int r = (int) Math.ceil(range.get());
        BlockPos best = null;
        double bestD = range.get() * range.get() + 0.01;
        Vec3 feet = mc.player.position();
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) for (int y = -3; y <= 1; y++) {
            BlockPos p = c.offset(x, y, z);
            int kind = HoleESP.check(p);
            if (kind < 0 || (onlyBedrock.isOn() && kind == 0)) continue;
            double d = new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5).distanceToSqr(feet);
            if (d < bestD) { bestD = d; best = p.immutable(); }
        }
        return best;
    }

    @Override
    protected void onEnable() {
        ticks = 0;
        hole = mc.player == null || mc.level == null ? null : find();
        if (hole == null && mc.player != null) {
            Notifications.push("HoleSnap", "Kein Loch in Reichweite", HudStyle.BAD);
            setEnabled(false);
        }
    }

    @Override
    public void onPreTick() {
        if (mc.player == null || hole == null) return;
        Vec3 target = new Vec3(hole.getX() + 0.5, mc.player.getY(), hole.getZ() + 0.5);
        Vec3 diff = target.subtract(mc.player.position());
        double dist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        boolean inside = dist < 0.2 && mc.player.getY() - hole.getY() < 0.3;
        if (inside || ++ticks > timeout.getInt() || HoleESP.check(hole) < 0) {
            if (inside) mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0);
            setEnabled(false);
            return;
        }
        double v = Math.min(speed.get(), dist);
        mc.player.setDeltaMovement(diff.x / dist * v, mc.player.getDeltaMovement().y, diff.z / dist * v);
    }
}
