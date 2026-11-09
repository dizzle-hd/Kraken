package dev.nautilus.gui;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Unit;

/**
 * The player model as a bust (head, torso, arms – no legs). The head can be turned independently,
 * e.g. to look at the mouse cursor. Rendered through vanilla's GUI skin renderer.
 */
public class PlayerBustModel extends Model.Simple {
    private final ModelPart head;
    private final ModelPart leftLeg, rightLeg;
    public float headYawDeg, headPitchDeg;

    public PlayerBustModel(ModelPart root) {
        super(root, RenderTypes::entityTranslucent);
        this.head = root.getChild("head");
        this.leftLeg = root.getChild("left_leg");
        this.rightLeg = root.getChild("right_leg");
    }

    @Override
    public void setupAnim(Unit state) {
        super.setupAnim(state); // resets the pose
        head.yRot = (float) Math.toRadians(headYawDeg);
        head.xRot = (float) Math.toRadians(headPitchDeg);
        leftLeg.visible = false;
        rightLeg.visible = false;
    }
}
