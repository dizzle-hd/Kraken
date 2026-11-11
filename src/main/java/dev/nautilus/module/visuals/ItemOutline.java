package dev.nautilus.module.visuals;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.nautilus.gui.Shapes;
import dev.nautilus.hud.HudStyle;
import dev.nautilus.mixin.ItemStackRenderStateAccessor;
import dev.nautilus.mixin.LayerRenderStateAccessor;
import dev.nautilus.module.Category;
import dev.nautilus.module.Module;
import dev.nautilus.module.ModuleManager;
import dev.nautilus.setting.ModeSetting;
import dev.nautilus.setting.NumberSetting;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A thin glowing outline around the items in your hands (first person) – our own geometry, no texture:
 * the outer edges of the item's visible pixels are worked out once per sprite (on the CPU) and drawn as
 * thin coloured strips just outside the item, on its front and back, with a soft glow around them.
 * Works for flat items (swords, totems, wind charges, food …); 3D block models get no outline.
 */
public class ItemOutline extends Module {
    private final ModeSetting color = add(new ModeSetting("Color", "Color of the outline", "Pastel", "Pastel", "HUD color", "White"));
    private final NumberSetting width = add(new NumberSetting("Width", "Thickness of the outline", 1.0, 0.5, 3.0, 0.25));
    private final NumberSetting opacity = add(new NumberSetting("Opacity", "How strong the outline is", 90, 20, 100, 5));
    private final NumberSetting glow = add(new NumberSetting("Glow", "Soft glow around the line (0 = none)", 50, 0, 100, 10));

    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("nautilusclient", "pipeline/item_outline"))
            .withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)) // reversed Z, never written
            .build();
    private static final RenderType TYPE = RenderType.create("nautilusclient_item_outline", RenderSetup.builder(PIPELINE).createRenderSetup());

    /** Outer pixel edges per sprite: x1, y1, x2, y2, nx, ny (pixel units, normal pointing outwards). */
    private static final Map<SpriteContents, float[]> EDGES = new WeakHashMap<>();

    public ItemOutline() {
        super("Item outline", "Thin glowing outline around the items in your hands – our own geometry along the item's pixel edges, no texture.",
                Category.VISUALS, Items.GLOW_ITEM_FRAME);
        loadEnabled(true);
    }

    private int rgb() {
        return switch (color.get()) {
            case "HUD color" -> HudStyle.accent();
            case "White" -> 0xFFFFFFFF;
            default -> Shapes.lerp(HudStyle.accent(), 0xFFFFFFFF, 0.55f);
        } & 0xFFFFFF;
    }

    private static float[] edges(SpriteContents c) {
        return EDGES.computeIfAbsent(c, sc -> {
            int w = sc.width(), h = sc.height();
            FloatArrayList out = new FloatArrayList();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (sc.isTransparent(0, x, y)) continue;
                    if (x == 0 || sc.isTransparent(0, x - 1, y)) out.addElements(out.size(), new float[]{x, y, x, y + 1, -1, 0});
                    if (x == w - 1 || sc.isTransparent(0, x + 1, y)) out.addElements(out.size(), new float[]{x + 1, y, x + 1, y + 1, 1, 0});
                    if (y == 0 || sc.isTransparent(0, x, y - 1)) out.addElements(out.size(), new float[]{x, y, x + 1, y, 0, -1});
                    if (y == h - 1 || sc.isTransparent(0, x, y + 1)) out.addElements(out.size(), new float[]{x, y + 1, x + 1, y + 1, 0, 1});
                }
            }
            return out.toFloatArray();
        });
    }

    /** FirstPersonHandsAndItemsRendererMixin: right before a held item is submitted. */
    public static void submitSilhouette(ItemStackRenderState state, PoseStack pose, SubmitNodeCollector collector) {
        ItemOutline m = ModuleManager.get().get(ItemOutline.class);
        if (m == null || !m.isEnabled()) return;
        var acc = (ItemStackRenderStateAccessor) state;
        int n = acc.nautilus$layerCount();
        var layers = acc.nautilus$layers();
        for (int i = 0; i < n && i < layers.length; i++) {
            var layer = (LayerRenderStateAccessor) (Object) layers[i];
            var quads = layer.nautilus$quads();
            if (quads == null || quads.isEmpty()) continue;
            // a flat (generated) item: one full-size front and back face, everything else thin side strips
            BakedQuad front = null, back = null;
            for (BakedQuad q : quads.all()) {
                if (q.direction() == Direction.SOUTH && fullFace(q)) front = q;
                else if (q.direction() == Direction.NORTH && fullFace(q)) back = q;
            }
            if (front == null || back == null) continue;
            SpriteContents sprite = front.materialInfo().sprite().contents();
            float[] e = edges(sprite);
            if (e.length == 0) continue;
            pose.pushPose();
            layer.nautilus$applyTransform(pose.last());
            Matrix4f mtx = new Matrix4f(pose.last().pose());
            pose.popPose();
            float zf = front.position(0).z() + 0.002f, zb = back.position(0).z() - 0.002f;
            float[] map = pixelToModel(front, sprite.width(), sprite.height());
            if (map == null) continue;
            float[] geo = m.build(e, map, zf, zb, mtx);
            int[] cols = m.colors(e.length / 6);
            // own identity pose: the positions are already transformed, nothing can change them afterwards
            collector.submitCustomGeometry(new PoseStack(), TYPE, (p, buffer) -> {
                for (int v = 0; v < geo.length / 3; v++) buffer.addVertex(p, geo[v * 3], geo[v * 3 + 1], geo[v * 3 + 2]).setColor(cols[v]);
            });
        }
    }

    /**
     * Affine map sprite pixel (px, py) → model (x, y), read from the front face's own positions and UVs
     * (so mirrored or rotated item models are handled too): x = a*px + b*py + c, y = d*px + e*py + f.
     */
    private static float[] pixelToModel(BakedQuad q, int w, int h) {
        var sp = q.materialInfo().sprite();
        float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1();
        float[] px = new float[3], py = new float[3], mx = new float[3], my = new float[3];
        for (int i = 0; i < 3; i++) {
            long uv = q.packedUV(i);
            px[i] = (net.minecraft.client.model.geom.builders.UVPair.unpackU(uv) - u0) / (u1 - u0) * w;
            py[i] = (net.minecraft.client.model.geom.builders.UVPair.unpackV(uv) - v0) / (v1 - v0) * h;
            mx[i] = q.position(i).x();
            my[i] = q.position(i).y();
        }
        float d1x = px[1] - px[0], d1y = py[1] - py[0], d2x = px[2] - px[0], d2y = py[2] - py[0];
        float det = d1x * d2y - d1y * d2x;
        if (Math.abs(det) < 1e-6f) return null;
        float e1x = mx[1] - mx[0], e1y = my[1] - my[0], e2x = mx[2] - mx[0], e2y = my[2] - my[0];
        float a = (e1x * d2y - e2x * d1y) / det, b = (e2x * d1x - e1x * d2x) / det;
        float d = (e1y * d2y - e2y * d1y) / det, e = (e2y * d1x - e1y * d2x) / det;
        float c = mx[0] - a * px[0] - b * py[0], f = my[0] - d * px[0] - e * py[0];
        return new float[]{a, b, c, d, e, f};
    }

    private static boolean fullFace(BakedQuad q) {
        float minX = 9, maxX = -9, minY = 9, maxY = -9;
        for (int v = 0; v < 4; v++) {
            minX = Math.min(minX, q.position(v).x()); maxX = Math.max(maxX, q.position(v).x());
            minY = Math.min(minY, q.position(v).y()); maxY = Math.max(maxY, q.position(v).y());
        }
        return maxX - minX > 0.9f && maxY - minY > 0.9f;
    }

    /** Per edge and side (front/back): a glow strip and a line strip, 4 vertices each. */
    private float[] build(float[] e, float[] map, float zf, float zb, Matrix4f mtx) {
        int segs = e.length / 6;
        float line = 0.55f * width.getFloat(), soft = line + 1.1f * width.getFloat(); // in texture pixels
        FloatArrayList out = new FloatArrayList(segs * 2 * 2 * 4 * 3);
        Vector3f t = new Vector3f();
        for (float z : new float[]{zf, zb}) {
            for (int s = 0; s < segs; s++) {
                float x1 = e[s * 6], y1 = e[s * 6 + 1], x2 = e[s * 6 + 2], y2 = e[s * 6 + 3], nx = e[s * 6 + 4], ny = e[s * 6 + 5];
                for (float wd : new float[]{soft, line}) {
                    // strip outside the edge, a bit longer than the edge so corners close
                    float ex = (x2 - x1) == 0 ? 0 : wd, ey = (y2 - y1) == 0 ? 0 : wd;
                    float ax = x1 - ex, ay = y1 - ey, bx = x2 + ex, by = y2 + ey;
                    float[][] c = {{ax, ay}, {bx, by}, {bx + nx * wd, by + ny * wd}, {ax + nx * wd, ay + ny * wd}};
                    for (float[] p : c) {
                        mtx.transformPosition(map[0] * p[0] + map[1] * p[1] + map[2], map[3] * p[0] + map[4] * p[1] + map[5], z, t);
                        out.add(t.x); out.add(t.y); out.add(t.z);
                    }
                }
            }
        }
        return out.toFloatArray();
    }

    /** Vertex colours matching build(): glow strips faint, line strips strong. */
    private int[] colors(int segs) {
        int rgb = rgb();
        float a = opacity.getFloat() / 100f;
        int lineC = ((int) (a * 255) << 24) | rgb, glowC = ((int) (a * glow.getFloat() / 100f * 0.45f * 255) << 24) | rgb;
        int[] c = new int[segs * 2 * 2 * 4];
        int k = 0;
        for (int side = 0; side < 2; side++)
            for (int s = 0; s < segs; s++) {
                // glow: full at the edge (vertices 0, 1), fading to nothing outside (2, 3)
                c[k++] = glowC; c[k++] = glowC; c[k++] = rgb; c[k++] = rgb;
                for (int v = 0; v < 4; v++) c[k++] = lineC;
            }
        return c;
    }
}
