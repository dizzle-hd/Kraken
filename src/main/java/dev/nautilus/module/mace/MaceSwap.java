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
    private final NumberSetting minFall = add(new NumberSetting("Min. Fallhöhe", "Erst ab dieser Fallhöhe auf den Streitkolben wechseln (Smash ab 1,5 Blöcken)", 1.5, 1.5, 20, 0.5));
    private final ModeSetting choice = add(new ModeSetting("Auswahl", "Welcher Streitkolben: Auto = meister Schaden fürs Ziel (Dichte/Bresche), sonst bevorzugt die Verzauberung", "Auto", "Auto", "Dichte", "Bresche", "Windstoß"));
    private final BoolSetting withoutFall = add(new BoolSetting("Auch ohne Fall", "Auch normale Schläge mit dem Streitkolben ausführen (6 Schaden, langsam)", false));
    private final BoolSetting onlyPlayers = add(new BoolSetting("Nur Spieler", "Nur wechseln, wenn du einen Spieler schlägst", false));
    private final BoolSetting back = add(new BoolSetting("Zurückwechseln", "Nach dem Schlag wieder den vorherigen Slot halten", true));

    public MaceSwap() {
        super("MaceSwap", "Schlägt beim Fallen automatisch mit dem Streitkolben zu und wechselt danach zurück – Schwert bleibt in der Hand.",
                Category.MACE, Items.MACE);
        preset("Legit", LOW, "Nur bei richtigen Smashes ab 3 Blöcken, Spieler", "Min. Fallhöhe", 3.0, "Auch ohne Fall", false, "Nur Spieler", true);
        preset("Normal", MEDIUM, "Jeder Smash ab 1,5 Blöcken", "Min. Fallhöhe", 1.5, "Auch ohne Fall", false, "Nur Spieler", false);
        preset("Rage", HIGH, "Immer mit dem Streitkolben", "Min. Fallhöhe", 1.5, "Auch ohne Fall", true, "Nur Spieler", false);
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
                case "Dichte" -> score += MaceUtil.density(s) * 1000;
                case "Bresche" -> score += MaceUtil.breach(s) * 1000;
                case "Windstoß" -> score += MaceUtil.windBurst(s) * 1000;
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
