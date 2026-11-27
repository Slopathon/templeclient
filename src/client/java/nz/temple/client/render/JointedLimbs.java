package nz.temple.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;

public final class JointedLimbs {
	public static float elbowR;
	public static float elbowL;
	public static float kneeR;
	public static float kneeL;
	public static boolean active;
	private static boolean hidden;
	private static Set wide;
	private static Set slim;
	private JointedLimbs() {
	}

	public static void reset() {
		elbowR = elbowL = kneeR = kneeL = 0;
		active = false;
	}
	public static void bend(float rightElbow, float leftElbow, float rightKnee, float leftKnee) {
		elbowR = rightElbow;
		elbowL = leftElbow;
		kneeR = rightKnee;
		kneeL = leftKnee;
		active = true;
	}
	public static void hideVanilla(HumanoidModel<?> model, boolean hide) {
		if (!hide && !hidden) {
			return;
		}
		hidden = hide;
		model.rightArm.visible = !hide;
		model.leftArm.visible = !hide;
		model.rightLeg.visible = !hide;
		model.leftLeg.visible = !hide;
	}
	public static void submit(HumanoidModel<?> model, AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector,
			int light, int overlay) {
		boolean isSlim = state.skin.model() == PlayerModelType.SLIM;
		Set set = isSlim ? slim() : wide();
		Identifier skin = state.skin.body().texturePath();
		var type = RenderTypes.entityTranslucent(skin);
		place(set.rightArm, model.rightArm, -elbowR, state.showRightSleeve);
		place(set.leftArm, model.leftArm, -elbowL, state.showLeftSleeve);
		place(set.rightLeg, model.rightLeg, kneeR, state.showRightPants);
		place(set.leftLeg, model.leftLeg, kneeL, state.showLeftPants);
		collector.submitModelPart(set.rightArm, pose, type, light, overlay, null);
		collector.submitModelPart(set.leftArm, pose, type, light, overlay, null);
		collector.submitModelPart(set.rightLeg, pose, type, light, overlay, null);
		collector.submitModelPart(set.leftLeg, pose, type, light, overlay, null);
	}
	private static void place(ModelPart upper, ModelPart vanilla, float bend, boolean overlayLayer) {
		upper.x = vanilla.x;
		upper.y = vanilla.y;
		upper.z = vanilla.z;
		upper.xRot = vanilla.xRot;
		upper.yRot = vanilla.yRot;
		upper.zRot = vanilla.zRot;
		upper.xScale = vanilla.xScale;
		upper.yScale = vanilla.yScale;
		upper.zScale = vanilla.zScale;
		upper.visible = true;
		ModelPart lower = upper.getChild("lower");
		lower.xRot = bend;
		upper.getChild("outer").visible = overlayLayer;
		lower.getChild("outer").visible = overlayLayer;
	}
	private static final class Set {
		final ModelPart rightArm;
		final ModelPart leftArm;
		final ModelPart rightLeg;
		final ModelPart leftLeg;
		Set(boolean slimArms) {
			int armW = slimArms ? 3 : 4;
			rightArm = limb(40, 16, 40, 32, slimArms ? -2 : -3, -2, armW, 6, 4);
			leftArm = limb(32, 48, 48, 48, -1, -2, armW, 6, 4);
			rightLeg = limb(0, 16, 0, 32, -2, 0, 4, 6, 6);
			leftLeg = limb(16, 48, 0, 48, -2, 0, 4, 6, 6);
		}
	}
	private static ModelPart limb(int u, int v, int ou, int ov, float x, float top, int w, int h, float joint) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition upper = root.addOrReplaceChild("upper", CubeListBuilder.create().texOffs(u, v).addBox(x, top, -2, w, h, 4),
				PartPose.ZERO);
		upper.addOrReplaceChild("outer", CubeListBuilder.create().texOffs(ou, ov).addBox(x, top, -2, w, h, 4, new CubeDeformation(0.25f)),
				PartPose.ZERO);
		PartDefinition lower = upper.addOrReplaceChild("lower",
				CubeListBuilder.create().texOffs(u, v + 5).addBox(x, -1, -2, w, 7, 4), PartPose.offset(0, joint, 0));
		lower.addOrReplaceChild("outer", CubeListBuilder.create().texOffs(ou, ov + 5).addBox(x, -1, -2, w, 7, 4, new CubeDeformation(0.25f)),
				PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("upper");
	}
	private static Set wide() {
		if (wide == null) {
			wide = new Set(false);
		}
		return wide;
	}
	private static Set slim() {
		if (slim == null) {
			slim = new Set(true);
		}
		return slim;
	}
}
