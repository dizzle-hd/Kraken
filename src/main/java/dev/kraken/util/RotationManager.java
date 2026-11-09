package dev.kraken.util;

import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Server-side ("silent") rotations. A module requests a rotation during the pre-tick; while the
 * player sends its movement packet the rotation is swapped in and restored right after, so the
 * camera of the player never moves.
 */
public final class RotationManager {
    private static boolean requested;
    private static float targetYaw, targetPitch;
    private static int requestPriority;

    private static boolean swapped;
    private static float savedYaw, savedPitch;

    private static float serverYaw, serverPitch;

    private RotationManager() {}

    /** Request a silent rotation for the current tick. Higher priority wins. */
    public static void request(float yaw, float pitch, int priority) {
        if (requested && priority < requestPriority) return;
        requested = true;
        requestPriority = priority;
        targetYaw = yaw;
        targetPitch = Math.max(-90f, Math.min(90f, pitch));
    }

    public static boolean isRequested() { return requested; }

    public static float getServerYaw() { return serverYaw; }
    public static float getServerPitch() { return serverPitch; }

    /** Called from the mixin at the head of ClientPlayerEntity#sendMovementPackets. */
    public static void preMotion(ClientPlayerEntity player) {
        if (requested) {
            savedYaw = player.getYaw();
            savedPitch = player.getPitch();
            player.setYaw(targetYaw);
            player.setPitch(targetPitch);
            swapped = true;
        }
    }

    /** Called from the mixin at the tail of ClientPlayerEntity#sendMovementPackets. */
    public static void postMotion(ClientPlayerEntity player) {
        serverYaw = player.getYaw();
        serverPitch = player.getPitch();
        if (swapped) {
            player.setYaw(savedYaw);
            player.setPitch(savedPitch);
            swapped = false;
        }
        requested = false;
        requestPriority = Integer.MIN_VALUE;
    }
}
