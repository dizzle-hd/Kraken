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
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import org.joml.Vector3fc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A thin glowing outline around the items in your hands (first person). The item's silhouette is
 * drawn in a flat colour (Minecraft's outline shader: colour wherever the texture is not transparent),
 * shifted a little in every direction and drawn before the item – only the rim stays visible.
 */
public class ItemOutline extends Module {
    private final ModeSetting color = add(new ModeSetting("Color", "Color of the outline", "Pastel", "Pastel", "HUD color", "White"));
    private final NumberSetting width = add(new NumberSetting("Width", "Thickness of the outline", 1.0, 0.5, 3.0, 0.25));
    private final NumberSetting opacity = add(new NumberSetting("Opacity", "How strong the outline is", 85, 20, 100, 5));

    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.OUTLINE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("nautilusclient", "pipeline/item_silhouette"))
            .withCull(false)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)) // reversed Z, never written
            .build();
    private static final Map<Identifier, RenderType> TYPES = new HashMap<>();

    /** 8 directions in the item plane: the outline is the silhouette shifted around the item. */
    private static final float[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {0.7f, 0.7f}, {-0.7f, 0.7f}, {0.7f, -0.7f}, {-0.7f, -0.7f}};

    public ItemOutline() {
        super("Item outline", "Thin glowing outline around the items in your hands – in a soft pastel of the HUD color, the HUD color or white.",
                Category.VISUALS, Items.GLOW_ITEM_FRAME);
        loadEnabled(true);
    }

    private static RenderType type(Identifier atlas) {
        return TYPES.computeIfAbsent(atlas, id -> RenderType.create("nautilusclient_item_silhouette",
                RenderSetup.builder(PIPELINE).withTexture("Sampler0", id).createRenderSetup()));
    }

    private int argb() {
        int c = switch (color.get()) {
            case "HUD color" -> HudStyle.accent();
            case "White" -> 0xFFFFFFFF;
            default -> Shapes.lerp(HudStyle.accent(), 0xFFFFFFFF, 0.55f);
        };
        return ((int) (opacity.getFloat() / 100f * 255) << 24) | (c & 0xFFFFFF);
    }

    /** FirstPersonHandsAndItemsRendererMixin: right before a held item is submitted. */
    public static void submitSilhouette(ItemStackRenderState state, PoseStack pose, SubmitNodeCollector collector) {
        ItemOutline m = ModuleManager.get().get(ItemOutline.class);
        if (m == null || !m.isEnabled()) return;
        var acc = (ItemStackRenderStateAccessor) state;
        int n = acc.nautilus$layerCount();
        var layers = acc.nautilus$layers();
        int c = m.argb();
        float d = 0.011f * m.width.getFloat(); // model units (1 = 16 texture pixels)
        for (int i = 0; i < n && i < layers.length; i++) {
            var layer = (LayerRenderStateAccessor) (Object) layers[i];
            var quads = layer.nautilus$quads();
            if (quads == null || quads.isEmpty()) continue;
            List<BakedQuad> all = quads.all();
            // flat (generated) items have dozens of thin side strips – they would show up as stripes;
            // their front and back faces alone give the clean silhouette
            if (all.size() > 12) {
                List<BakedQuad> faces = new java.util.ArrayList<>();
                for (BakedQuad q : all)
                    if (q.direction() == net.minecraft.core.Direction.NORTH || q.direction() == net.minecraft.core.Direction.SOUTH) faces.add(q);
                if (!faces.isEmpty()) all = faces;
            }
            List<BakedQuad> drawn = all;
            Identifier atlas = drawn.get(0).materialInfo().sprite().atlasLocation();
            pose.pushPose();
            layer.nautilus$applyTransform(pose.last());
            org.joml.Matrix4f base = new org.joml.Matrix4f(pose.last().pose());
            pose.popPose();
            // scaled from the camera (= origin of this space): exactly the same footprint on screen, just a
            // little deeper – the item in front always wins the depth test, the rim stays even all around
            org.joml.Matrix4f pushed = new org.joml.Matrix4f().scaling(1.04f).mul(base);
            float[] xyz = new float[drawn.size() * 12], uvs = new float[drawn.size() * 8];
            for (float[] dir : DIRS) {
                org.joml.Matrix4f mtx = new org.joml.Matrix4f(pushed).translate(dir[0] * d, dir[1] * d, 0f);
                org.joml.Vector3f tmp = new org.joml.Vector3f();
                for (int qi = 0; qi < drawn.size(); qi++) {
                    BakedQuad q = drawn.get(qi);
                    for (int v = 0; v < 4; v++) {
                        Vector3fc pos = q.position(v);
                        mtx.transformPosition(pos.x(), pos.y(), pos.z(), tmp);
                        int o = (qi * 4 + v) * 3;
                        xyz[o] = tmp.x; xyz[o + 1] = tmp.y; xyz[o + 2] = tmp.z;
                        long uv = q.packedUV(v);
                        uvs[(qi * 4 + v) * 2] = UVPair.unpackU(uv);
                        uvs[(qi * 4 + v) * 2 + 1] = UVPair.unpackV(uv);
                    }
                }
                float[] fx = xyz.clone(), fu = uvs.clone();
                // own identity pose (positions are already transformed): nothing can change it afterwards;
                // order -1: drawn before the items (order 0) without writing depth
                collector.order(-1).submitCustomGeometry(new PoseStack(), type(atlas), (p, buffer) -> {
                    for (int k = 0; k < fx.length / 3; k++)
                        buffer.addVertex(p, fx[k * 3], fx[k * 3 + 1], fx[k * 3 + 2]).setColor(c).setUv(fu[k * 2], fu[k * 2 + 1]);
                });
            }
        }
    }
}
