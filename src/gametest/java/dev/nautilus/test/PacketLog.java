package dev.nautilus.test;

import java.util.ArrayList;
import java.util.List;

/**
 * Records the packets the client sends, tick by tick, to prove that every interaction goes out
 * pre-tick: before the movement packet of its tick, like a real click – never after it ("post").
 */
public final class PacketLog {
    private PacketLog() {}

    public static volatile boolean recording;
    private static final List<List<String>> ticks = new ArrayList<>();
    private static List<String> current = new ArrayList<>();

    public static synchronized void sent(Object packet) {
        if (recording) current.add(packet.getClass().getName());
    }

    /** Called at the very end of a client tick (after the movement packet and all END_CLIENT_TICK hooks). */
    public static synchronized void endTick() {
        if (!recording) return;
        ticks.add(current);
        current = new ArrayList<>();
    }

    public static synchronized void start() {
        ticks.clear();
        current = new ArrayList<>();
        recording = true;
    }

    private static boolean isMove(String n) { return n.contains("ServerboundMovePlayerPacket"); }

    private static boolean isInteraction(String n) {
        return n.endsWith("ServerboundAttackPacket") || n.endsWith("ServerboundSetCarriedItemPacket")
                || n.endsWith("ServerboundUseItemPacket") || n.endsWith("ServerboundUseItemOnPacket")
                || n.endsWith("ServerboundInteractPacket") || n.endsWith("ServerboundPlayerActionPacket")
                || n.endsWith("ServerboundPunchPacket");
    }

    /** Stops recording and returns a summary; "post=0" means every interaction was sent pre-tick. */
    public static synchronized String stop() {
        recording = false;
        int pre = 0, post = 0, extraMoves = 0;
        StringBuilder bad = new StringBuilder();
        for (int t = 0; t < ticks.size(); t++) {
            List<String> tick = ticks.get(t);
            int lastMove = -1, moves = 0;
            for (int i = 0; i < tick.size(); i++) if (isMove(tick.get(i))) { lastMove = i; moves++; }
            if (moves > 1) extraMoves++;
            for (int i = 0; i < tick.size(); i++) {
                if (!isInteraction(tick.get(i))) continue;
                if (lastMove >= 0 && i > lastMove) {
                    post++;
                    bad.append(" tick").append(t).append(':').append(tick.get(i).substring(tick.get(i).lastIndexOf('.') + 1));
                } else pre++;
            }
        }
        return "ticks=" + ticks.size() + " interactions pre=" + pre + " post=" + post
                + " ticksWithExtraMovePacket=" + extraMoves + (post > 0 ? " POST:" + bad : "");
    }
}
