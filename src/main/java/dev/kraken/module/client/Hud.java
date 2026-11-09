package dev.kraken.module.client;

import dev.kraken.module.Category;
import dev.kraken.module.Module;
import dev.kraken.module.ModuleManager;
import dev.kraken.setting.BoolSetting;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Watermark + list of active modules in the top right corner. */
public class Hud extends Module {
    private final BoolSetting watermark = add(new BoolSetting("Wasserzeichen", "\"Kraken\" oben links anzeigen", true));
    private final BoolSetting moduleList = add(new BoolSetting("Modulliste", "Aktive Module oben rechts anzeigen", true));

    public Hud() {
        super("HUD", "Wasserzeichen und Liste der aktiven Module.", Category.CLIENT, Items.ITEM_FRAME);
        loadEnabled(true);
    }

    public void render(DrawContext ctx) {
        if (mc.options.hudHidden || mc.getDebugHud().shouldShowDebugHud()) return;
        TextRenderer tr = mc.textRenderer;
        if (watermark.isOn()) {
            ctx.fill(2, 2, 6 + tr.getWidth("Kraken") + 4, 15, 0x90200C08);
            ctx.fill(2, 2, 4, 15, 0xFFFF5A2A);
            ctx.drawTextWithShadow(tr, "Kraken", 7, 5, 0xFFFFFFFF);
        }
        if (!moduleList.isOn()) return;

        List<String> lines = new ArrayList<>();
        for (Module m : ModuleManager.get().getModules()) {
            if (!m.isEnabled() || m.getCategory() == Category.CLIENT) continue;
            String info = m.getInfo();
            lines.add(info == null ? m.getName() : m.getName() + " §7" + info);
        }
        lines.sort(Comparator.comparingInt((String l) -> tr.getWidth(l)).reversed());
        int y = 2;
        int sw = ctx.getScaledWindowWidth();
        for (String line : lines) {
            int w = tr.getWidth(line);
            ctx.fill(sw - w - 6, y, sw, y + 11, 0x90200C08);
            ctx.fill(sw - 2, y, sw, y + 11, 0xFFFF5A2A);
            ctx.drawTextWithShadow(tr, line, sw - w - 4, y + 2, 0xFFFFD2C2);
            y += 11;
        }
    }
}
