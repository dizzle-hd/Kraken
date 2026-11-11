package dev.nautilus.module.player;

import com.mojang.blaze3d.platform.InputConstants;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.setting.BoolSetting;
import net.minecraft.world.item.Items;

/** Sprints for you whenever you walk forward (like holding the sprint key). */
public class AutoSprint extends Module {
    private final BoolSetting allDirections = add(new BoolSetting("All directions", "Also hold the sprint key sideways/backwards (vanilla still only sprints forwards)", false));

    public AutoSprint() {
        super("AutoSprint", "Automatically holds the sprint key while walking forwards – for more knockback in fights.", Category.PLAYER, Items.FEATHER);
    }

    @Override
    public void onPreTick() {
        if (mc.player == null) return;
        var o = mc.options;
        boolean moving = o.keyUp.isDown() || allDirections.isOn() && (o.keyLeft.isDown() || o.keyRight.isDown() || o.keyDown.isDown());
        o.keySprint.setDown(moving && !o.keyShift.isDown() || physical());
    }

    private boolean physical() {
        var k = mc.options.keySprint;
        return InputConstants.isKeyDown(InputConstants.getKey(k.saveString()).getValue());
    }

    @Override
    protected void onDisable() {
        if (mc.player != null) mc.options.keySprint.setDown(physical());
    }
}
