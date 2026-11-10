package dev.nautilus.hud;

import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;

/** Counts left/right clicks of the last second (CPS). Polled once per frame from the HUD. */
public final class ClickTracker {
    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>(), RIGHT = new ArrayDeque<>();
    private static boolean wasLeft, wasRight;

    private ClickTracker() {}

    public static void update() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        boolean free = mc.gui.screen() == null;
        boolean l = free && mc.options.keyAttack.isDown(), r = free && mc.options.keyUse.isDown();
        if (l && !wasLeft) LEFT.addLast(now);
        if (r && !wasRight) RIGHT.addLast(now);
        wasLeft = l;
        wasRight = r;
        while (!LEFT.isEmpty() && now - LEFT.peekFirst() > 1000) LEFT.removeFirst();
        while (!RIGHT.isEmpty() && now - RIGHT.peekFirst() > 1000) RIGHT.removeFirst();
    }

    public static int left() { return LEFT.size(); }
    public static int right() { return RIGHT.size(); }
}
