package dev.nautilus.util;

import net.minecraft.client.player.LocalPlayer;

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
    /** Rotation of the last movement packet – what the server currently thinks we look at. */
    private static float sentYaw, sentPitch;
    private static boolean sentValid;

    private RotationManager() {}

    /**
     * Does the server already have (about) this rotation from the last movement packet? Modules hit only
     * then, so the hit never needs an extra rotation packet and never arrives before the rotation.
     */
    public static boolean serverLooksAt(float yaw, float pitch, float tolerance) {
        if (!sentValid) return false;
        float dy = Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - sentYaw)), dp = Math.abs(pitch - sentPitch);
        return dy <= tolerance && dp <= tolerance;
    }

    /** Request a silent rotation for the current tick. Higher priority wins. */
    public static void request(float yaw, float pitch, int priority) {
        if (requested && priority < requestPriority) return;
        requested = true;
        requestPriority = priority;
        targetYaw = yaw;
        targetPitch = Math.max(-90f, Math.min(90f, pitch));
    }

    /** Called from the mixin at the head of LocalPlayer#sendPosition. */
    public static void preMotion(LocalPlayer player) {
        if (requested) {
            savedYaw = player.getYRot();
            savedPitch = player.getXRot();
            player.setYRot(targetYaw);
            player.setXRot(targetPitch);
            swapped = true;
        }
    }

    /** Called from the mixin at the tail of LocalPlayer#sendPosition. */
    public static void postMotion(LocalPlayer player) {
        sentYaw = player.getYRot();   // still the swapped rotation here: exactly what was just sent
        sentPitch = player.getXRot();
        sentValid = true;
        if (swapped) {
            player.setYRot(savedYaw);
            player.setXRot(savedPitch);
            swapped = false;
        }
        requested = false;
        requestPriority = Integer.MIN_VALUE;
    }
}
