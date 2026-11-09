package dev.kraken.module.combat;

import dev.kraken.module.Category;
import dev.kraken.module.Module;
import dev.kraken.setting.BoolSetting;
import dev.kraken.setting.ModeSetting;
import dev.kraken.setting.NumberSetting;
import dev.kraken.util.TargetUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;

/** Shared target settings for both kill auras. */
public abstract class KillAuraBase extends Module {
    protected final NumberSetting range;
    protected final NumberSetting fov;
    protected final ModeSetting sort = add(new ModeSetting("Sortierung", "Welches Ziel zuerst angegriffen wird", "Distanz", "Distanz", "Leben", "Winkel"));
    protected final BoolSetting players = add(new BoolSetting("Spieler", "Spieler angreifen", true));
    protected final BoolSetting hostiles = add(new BoolSetting("Monster", "Feindliche Mobs angreifen", true));
    protected final BoolSetting passives = add(new BoolSetting("Tiere", "Friedliche Mobs angreifen", false));
    protected final BoolSetting invisibles = add(new BoolSetting("Unsichtbare", "Unsichtbare Ziele angreifen", true));
    protected final BoolSetting teammates = add(new BoolSetting("Teammates", "Auch Spieler aus deinem Team angreifen", false));
    protected final BoolSetting pauseOnUse = add(new BoolSetting("Pause beim Benutzen", "Nicht angreifen während du isst / blockst", true));

    protected final List<LivingEntity> targets = new ArrayList<>();

    protected KillAuraBase(String name, String description, Item icon, double defRange, double maxRange, double defFov) {
        super(name, description, Category.COMBAT, icon);
        range = add(new NumberSetting("Reichweite", "Angriffsreichweite in Blöcken", defRange, 1.0, maxRange, 0.1));
        fov = add(new NumberSetting("FOV", "Nur Ziele innerhalb dieses Sichtwinkels (360 = rundum)", defFov, 10, 360, 5));
    }

    protected TargetUtil.Filter filter() {
        return new TargetUtil.Filter(players.isOn(), hostiles.isOn(), passives.isOn(), invisibles.isOn(), teammates.isOn());
    }

    protected boolean paused() {
        return mc.player == null || mc.interactionManager == null
                || pauseOnUse.isOn() && mc.player.isUsingItem() || mc.player.isSpectator() || mc.player.isDead();
    }

    protected void attack(LivingEntity target) {
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    @Override
    protected void onDisable() {
        targets.clear();
    }

    @Override
    public String getInfo() {
        return targets.isEmpty() ? null : targets.get(0).getName().getString();
    }
}
