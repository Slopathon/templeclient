package nz.temple.client.modules.cosmetic;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import nz.temple.client.render.JointedLimbs;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;

public final class WalkStyle extends Module {
	private static volatile WalkStyle instance;
	private final ModeSetting style;
	private final NumberSetting strength;
	public WalkStyle() {
		super("Walk Style", Category.COSMETICS, "Alternative walking animations with bending knees and elbows. Only visible to you, in "
				+ "third person.");
		style = add(new ModeSetting("Style", List.of("Natural", "Swagger", "Robot", "Sneaky", "Hands in pockets", "Ninja run",
				"Zombie", "Bouncy", "Runway"), "Natural")
				.describe("Natural is the vanilla walk with bending knees and elbows. Swagger has a wide arm swing. Robot is stiff with "
						+ "forearms held out. Sneaky is crouched with hands up. Hands in pockets is a relaxed walk. Ninja run leans "
						+ "forward with arms back. Zombie walks with arms out. Bouncy uses high knees. Runway places one foot in front of "
						+ "the other."));
		strength = add(new NumberSetting("Strength", 100, 30, 150, 5, "%").describe("How exaggerated the animation is."));
		instance = this;
	}
	public static void pose(HumanoidModel<?> model, AvatarRenderState state) {
		WalkStyle m = instance;
		if (m == null || !m.isEnabled() || state.isPassenger || state.isFallFlying || state.isVisuallySwimming) {
			return;
		}
		float sp = Math.min(1f, state.walkAnimationSpeed) * m.strength.getFloat() / 100f;
		if (sp < 0.02f) {
			return;
		}
		float phase = state.walkAnimationPos * 0.6662f;
		float s = (float) Math.sin(phase);
		float c = (float) Math.cos(phase);
		ModelPart head = model.head;
		ModelPart body = model.body;
		ModelPart ra = model.rightArm;
		ModelPart la = model.leftArm;
		ModelPart rl = model.rightLeg;
		ModelPart ll = model.leftLeg;
		float liftR = Math.max(0, s);
		float liftL = Math.max(0, -s);
		switch (m.style.get()) {
			case "Swagger" -> {
				body.yRot += s * 0.28f * sp;
				ra.xRot = c * 1.25f * sp;
				la.xRot = -c * 1.25f * sp;
				ra.zRot += 0.18f * sp;
				la.zRot -= 0.18f * sp;
				head.zRot += s * 0.08f * sp;
				JointedLimbs.bend((0.45f + Math.max(0, -c) * 0.4f) * sp, (0.45f + Math.max(0, c) * 0.4f) * sp,
						liftR * 0.9f * sp, liftL * 0.9f * sp);
			}
			case "Robot" -> {
				float q = (float) Math.round(c * 2) / 2f;
				ra.xRot = q * 0.8f * sp;
				la.xRot = -q * 0.8f * sp;
				ra.zRot = 0;
				la.zRot = 0;
				rl.xRot = -q * 0.7f * sp;
				ll.xRot = q * 0.7f * sp;
				head.yRot = 0;
				head.xRot = 0;
				JointedLimbs.bend(1.57f * sp, 1.57f * sp, Math.max(0, q) * 0.6f * sp, Math.max(0, -q) * 0.6f * sp);
			}
			case "Sneaky" -> {
				ra.xRot = -0.7f * sp + c * 0.25f * sp;
				la.xRot = -0.7f * sp - c * 0.25f * sp;
				ra.zRot -= 0.1f * sp;
				la.zRot += 0.1f * sp;
				body.xRot += 0.2f * sp;
				head.xRot += 0.15f * sp;
				rl.xRot = c * 0.7f * sp;
				ll.xRot = -c * 0.7f * sp;
				JointedLimbs.bend(1.5f * sp, 1.5f * sp, (0.7f + liftR * 0.5f) * sp, (0.7f + liftL * 0.5f) * sp);
			}
			case "Hands in pockets" -> {
				ra.xRot = 0.2f * sp;
				la.xRot = 0.2f * sp;
				ra.zRot = 0.05f;
				la.zRot = -0.05f;
				rl.xRot *= 0.75f;
				ll.xRot *= 0.75f;
				head.zRot += s * 0.05f * sp;
				JointedLimbs.bend(0.9f * sp, 0.9f * sp, liftR * 0.6f * sp, liftL * 0.6f * sp);
			}
			case "Ninja run" -> {
				ra.xRot = 1.25f * sp;
				la.xRot = 1.25f * sp;
				ra.zRot = 0.2f * sp;
				la.zRot = -0.2f * sp;
				body.xRot += 0.35f * sp;
				head.xRot -= 0.2f * sp;
				rl.xRot = c * 1.6f * sp;
				ll.xRot = -c * 1.6f * sp;
				JointedLimbs.bend(0.1f * sp, 0.1f * sp, liftR * 1.5f * sp, liftL * 1.5f * sp);
			}
			case "Zombie" -> {
				ra.xRot = -1.45f + s * 0.06f;
				la.xRot = -1.45f - s * 0.06f;
				rl.xRot *= 0.45f;
				ll.xRot *= 0.45f;
				head.zRot += s * 0.12f * sp;
				body.zRot += s * 0.05f * sp;
				JointedLimbs.bend(0.15f * sp, 0.15f * sp, liftR * 0.3f * sp, liftL * 0.3f * sp);
			}
			case "Bouncy" -> {
				ra.xRot = c * 1.3f * sp;
				la.xRot = -c * 1.3f * sp;
				rl.xRot = c * 1.5f * sp;
				ll.xRot = -c * 1.5f * sp;
				head.xRot += Math.abs(s) * 0.12f * sp;
				JointedLimbs.bend(0.8f * sp, 0.8f * sp, liftR * 1.6f * sp, liftL * 1.6f * sp);
			}
			case "Runway" -> {
				body.zRot += s * 0.1f * sp;
				body.yRot += s * 0.12f * sp;
				ra.xRot = c * 0.45f * sp;
				la.xRot = -c * 0.45f * sp;
				rl.yRot = -0.18f * sp;
				ll.yRot = 0.18f * sp;
				head.zRot -= s * 0.04f * sp;
				JointedLimbs.bend(0.25f * sp, 0.25f * sp, liftR * 0.7f * sp, liftL * 0.7f * sp);
			}
			default -> {
				JointedLimbs.bend((0.3f + Math.max(0, -c) * 0.4f) * sp, (0.3f + Math.max(0, c) * 0.4f) * sp,
						liftR * 1.1f * sp, liftL * 1.1f * sp);
			}
		}
	}
}
