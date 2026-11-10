package dev.nautilus.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** World → GUI coordinates for 2D overlays (nametags, waypoint labels), using the camera of this frame. */
public final class Projection {
    private static final Matrix4f MATRIX = new Matrix4f();
    private static final Vector4f TMP = new Vector4f();
    private static Vec3 cam = Vec3.ZERO;
    private static float guiW, guiH;

    private Projection() {}

    /** Call once per frame before {@link #toScreen}. */
    public static void begin(float width, float height) {
        var camera = Minecraft.getInstance().gameRenderer.mainCamera();
        camera.getViewRotationProjectionMatrix(MATRIX);
        cam = camera.position();
        guiW = width;
        guiH = height;
    }

    public static Vec3 camera() { return cam; }

    /** {x, y, depth} in GUI pixels, or null when the point is behind the camera. */
    public static float[] toScreen(Vec3 p) {
        TMP.set((float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z), 1f);
        MATRIX.transform(TMP);
        if (TMP.w <= 0.05f) return null;
        float nx = TMP.x / TMP.w, ny = TMP.y / TMP.w;
        return new float[]{(nx + 1f) / 2f * guiW, (1f - ny) / 2f * guiH, TMP.w};
    }
}
