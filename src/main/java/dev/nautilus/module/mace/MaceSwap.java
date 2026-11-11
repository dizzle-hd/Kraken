package dev.nautilus.module.mace;

import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import dev.nautilus.setting.ModeSetting;
import dev.nautilus.setting.NumberSetting;
import dev.nautilus.util.InvUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Keep your sword (or anything) in the hand: as soon as you hit while falling far enough for a smash,
 * the hit goes out with the mace – switched in the same tick, before the attack packet – and you are
 * back on your slot right after. Works for your own clicks and for every combat module.
 * With several maces it picks the best one for the target (Density for high falls, Breach against
 * armor) or the one you prefer.
 */
public class MaceSwap extends Module {
    private final NumberSetting minFall = add(new NumberSetting("Min. fall height", "Only switch to the mace from this fall height on (smash from 1.5 blocks)", 1.5, 1.5, 20, 0.5));
    private final ModeSetting choice = add(new ModeSetting("Choice", "Which mace: Auto = most damage for the target (Density/Breach), otherwise prefers the enchantment", "Auto", "Auto", "Density", "Breach", "Wind Burst"));
    private final BoolSetting withoutFall = add(new BoolSetting("Also without falling", "Also do normal hits with the mace (6 damage, slow)", false));
    private final BoolSetting onlyPlayers = add(new BoolSetting("Only players", "Only switch when you hit a player", false));
    private final BoolSetting back = add(new BoolSetting("Switch back", "Hold the previous slot again after the hit", true));

    public MaceSwap() {
        super("MaceSwap", "Hits with the mace automatically while falling and switches back afterwards – your sword stays in hand.",
                Category.MACE, Items.MACE);
        preset("Legit", LOW, "Only on real smashes from 3 blocks, players", "Min. fall height", 3.0, "Also without falling", false, "Only players", true);
        preset("Normal", MEDIUM, "Every smash from 1.5 blocks", "Min. fall height", 1.5, "Also without falling", false, "Only players", false);
        preset("Rage", HIGH, "Always with the mace", "Min. fall height", 1.5, "Also without falling", true, "Only players", false);
    }

    boolean switchBack() { return back.isOn(); }

    /** Hotbar slot of the mace to hit {@code target} with, or -1 to leave the slot alone. */
    int maceSlot(LivingEntity target) {
        var p = mc.player;
        if (p == null) return -1;
        if (onlyPlayers.isOn() && !(target instanceof Player)) return -1;
        boolean smash = MaceUtil.canSmash(p, minFall.get());
        if (!smash && !withoutFall.isOn()) return -1;

        int best = -1;
        float bestScore = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (!MaceUtil.isMace(s)) continue;
            float score = MaceUtil.estimate(s, target, smash ? p.fallDistance : 0);
            switch (choice.get()) {
                case "Density" -> score += MaceUtil.density(s) * 1000;
                case "Breach" -> score += MaceUtil.breach(s) * 1000;
                case "Wind Burst" -> score += MaceUtil.windBurst(s) * 1000;
                default -> {}
            }
            if (i == InvUtil.selected()) score += 0.01f; // a tie keeps the held one
            if (score > bestScore) { bestScore = score; best = i; }
        }
        return best;
    }

    @Override
    public String getInfo() {
        return choice.get();
    }
}
