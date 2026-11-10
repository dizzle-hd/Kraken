package dev.nautilus.module.crystal;

import dev.nautilus.hud.HudStyle;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.NumberSetting;
import dev.nautilus.util.Render3D;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Marks "holes": one-block pits whose floor and four walls are blast-proof (bedrock / obsidian …).
 * Standing in one, crystals can only hit you from above – the classic safe spot in crystal PvP.
 * Read-only: scans the loaded world, sends nothing.
 */
public class HoleESP extends Module {
    private final NumberSetting range = add(new NumberSetting("Reichweite", "Suchradius um dich in Blöcken", 8, 3, 16, 1));
    private final NumberSetting height = add(new NumberSetting("Höhe", "Wie viele Blöcke über/unter dir gesucht wird", 4, 1, 8, 1));
    private final BoolSetting onlyBedrock = add(new BoolSetting("Nur Bedrock", "Nur Löcher aus reinem Bedrock zeigen (unzerstörbar)", false));
    private final BoolSetting flat = add(new BoolSetting("Flach", "Nur den Boden markieren statt eines ganzen Blocks", true));

    private record Hole(BlockPos pos, boolean bedrock) {}

    private final List<Hole> holes = new ArrayList<>();
    private int timer;

    public HoleESP() {
        super("HoleESP", "Zeigt sichere Löcher aus Bedrock (grün) oder Obsidian (Akzentfarbe) in deiner Nähe – darin treffen dich Kristalle nur von oben.",
                Category.CRYSTAL, Items.BEDROCK);
    }

    @Override
    protected void onDisable() { holes.clear(); }

    private static boolean blastProof(BlockState s) {
        return s.is(Blocks.BEDROCK) || s.is(Blocks.OBSIDIAN) || s.is(Blocks.CRYING_OBSIDIAN)
                || s.is(Blocks.RESPAWN_ANCHOR) || s.is(Blocks.ENDER_CHEST) || s.is(Blocks.NETHERITE_BLOCK);
    }

    /** 1 = bedrock hole, 0 = mixed / obsidian hole, -1 = no hole. */
    private int check(BlockPos p) {
        var level = mc.level;
        if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir() || !level.getBlockState(p.above(2)).isAir()) return -1;
        boolean bedrock = true;
        BlockState floor = level.getBlockState(p.below());
        if (!blastProof(floor)) return -1;
        bedrock &= floor.is(Blocks.BEDROCK);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockState s = level.getBlockState(p.relative(d));
            if (!blastProof(s)) return -1;
            bedrock &= s.is(Blocks.BEDROCK);
        }
        return bedrock ? 1 : 0;
    }

    @Override
    public void onTick() {
        if (mc.level == null || mc.player == null) return;
        if (timer-- > 0) return;
        timer = 5; // 4 scans per second are plenty
        holes.clear();
        BlockPos c = mc.player.blockPosition();
        int r = range.getInt(), h = height.getInt();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > r * r) continue;
                for (int y = -h; y <= h; y++) {
                    m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
                    int kind = check(m);
                    if (kind < 0 || (onlyBedrock.isOn() && kind == 0)) continue;
                    holes.add(new Hole(m.immutable(), kind == 1));
                }
            }
        }
    }

    @Override
    public void onRender3D(LevelRenderContext context, Render3D render) {
        for (Hole h : holes) {
            AABB box = new AABB(h.pos());
            if (flat.isOn()) box = new AABB(box.minX, box.minY, box.minZ, box.maxX, box.minY + 0.02, box.maxZ);
            render.box(box, h.bedrock() ? HudStyle.GOOD : HudStyle.accent(0xE0));
        }
    }

    @Override
    public String getInfo() { return String.valueOf(holes.size()); }
}
