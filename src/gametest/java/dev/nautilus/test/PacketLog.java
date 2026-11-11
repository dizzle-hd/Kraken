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

    private static final String MOVE_POINT = "#sendPosition", TICK_START = "#tickStart";

    /** Start of a client tick (before every module's pre-tick): anything earlier was sent between ticks. */
    public static synchronized void tickStart() {
        if (recording) current.add(TICK_START);
    }

    /** End of LocalPlayer#sendPosition: everything after this in the same tick is "post". */
    public static synchronized void movementPoint() {
        if (recording) current.add(MOVE_POINT);
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
        int pre = 0, post = 0, between = 0, extraMoves = 0, noMark = 0;
        StringBuilder bad = new StringBuilder();
        for (int t = 0; t < ticks.size(); t++) {
            List<String> tick = ticks.get(t);
            int lastMove = tick.indexOf(MOVE_POINT), start = tick.indexOf(TICK_START), moves = 0;
            for (String n : tick) if (isMove(n)) moves++;
            if (moves > 1) extraMoves++;
            if (lastMove < 0) noMark++;
            for (int i = 0; i < tick.size(); i++) {
                if (!isInteraction(tick.get(i))) continue;
                String name = tick.get(i).substring(tick.get(i).lastIndexOf('.') + 1);
                if (lastMove >= 0 && i > lastMove) {
                    post++;
                    bad.append(" tick").append(t).append(':').append(name);
                } else if (start >= 0 && i < start) {
                    // sent in a frame between two ticks (e.g. from a packet/entity event): can land right
                    // after the movement packet – timing-based checks see that as "post"
                    between++;
                    bad.append(" between").append(t).append(':').append(name);
                } else pre++;
            }
        }
        return "ticks=" + ticks.size() + " interactions pre=" + pre + " post=" + post + " betweenTicks=" + between
                + " ticksWithExtraMovePacket=" + extraMoves + " ticksWithoutSendPosition=" + noMark + (post + between > 0 ? " BAD:" + bad : "");
    }
}
