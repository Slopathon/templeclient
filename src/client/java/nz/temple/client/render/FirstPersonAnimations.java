package nz.temple.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.render.Animations;
import nz.temple.client.security.LazyPayloadBridges;
import nz.temple.module.Module;

public final class FirstPersonAnimations {
	public static final class Equip {
		public float mainHeight;
		public float offHeight;
		public float oOffHeight;
		public ItemStack mainItem;
		public ItemStack offItem;
	}
	private static Matrix4f beforeEat;

	private FirstPersonAnimations() {
	}

	public static void equip(Equip e, float mainBefore, float offBefore) {
		Animations a = Animations.active();
		LocalPlayer player = Minecraft.getInstance().player;
		if (a == null || player == null) {
			return;
		}
		ItemStack main = player.getMainHandItem();
		if (!a.noEquip() && a.equipSpeed() < 1) {
			e.mainHeight = mainBefore + (e.mainHeight - mainBefore) * a.equipSpeed();
			e.offHeight = offBefore + (e.offHeight - offBefore) * a.equipSpeed();
		}
		if (!a.noEquip() && !a.noCooldown() && a.cooldownDip() < 1 && ItemStack.isSameItemSameComponents(e.mainItem, main)) {
			float strength = player.getAttackStrengthScale(1f);
			float target = 1 - (1 - strength * strength * strength) * a.cooldownDip();
			e.mainHeight = Math.max(e.mainHeight, Math.min(1, target));
		}
		if (a.noEquip()) {
			e.mainItem = main;
			e.offItem = player.getOffhandItem();
			e.offHeight = 1;
			e.oOffHeight = 1;
			float strength = player.getAttackStrengthScale(1f);
			e.mainHeight = a.noCooldown() ? 1 : 1 - (1 - strength * strength * strength) * a.cooldownDip();
		} else if (a.noCooldown() && ItemStack.isSameItemSameComponents(e.mainItem, main)) {
			e.mainHeight = 1;
		}
	}
	public static boolean swingStyle(PoseStack pose, HumanoidArm arm, float swing) {

		Module custom = TempleNzClient.customAnimations();
		if (custom != null && custom.isEnabled()) {
			LocalPlayer player = Minecraft.getInstance().player;
			if (player != null) {
				ItemStack held = arm == player.getMainArm() ? player.getMainHandItem() : player.getOffhandItem();
				if (LazyPayloadBridges.applySwing(custom, pose, arm, swing, held)) {
					return true;
				}
			}
		}
		Animations a = Animations.active();
		if (a == null || !a.customMotion()) {
			return false;
		}
		int side = arm == HumanoidArm.RIGHT ? 1 : -1;
		float p = Mth.sin(Mth.sqrt(swing) * Mth.PI);
		switch (a.style()) {
			case "Stab" -> {
				pose.translate(0f, 0.06f * p, -0.45f * p);
				pose.mulPose(Axis.XP.rotationDegrees(-12f * p));
			}
			case "Slide" -> {
				pose.translate(side * -0.28f * p, -0.12f * p, -0.05f * p);
				pose.mulPose(Axis.ZP.rotationDegrees(side * -28f * p));
			}
			case "Spin" -> {
				float eased = swing * swing * (3 - 2 * swing);
				pose.mulPose(Axis.YP.rotationDegrees(side * -360f * eased));
			}
			default -> {
				pose.translate(0f, -0.06f * p, 0f);
				pose.mulPose(Axis.XP.rotationDegrees(-38f * p));
			}
		}
		return true;
	}
	public static boolean beforeEat(PoseStack pose) {
		Animations a = Animations.active();
		if (a == null) {
			return false;
		}
		if (a.eatAmount() <= 0) {
			return true;
		}
		if (beforeEat == null) {
			beforeEat = new Matrix4f();
		}
		beforeEat.set(pose.last().pose());
		return false;
	}
	public static void afterEat(PoseStack pose) {
		Animations a = Animations.active();
		if (a == null || a.eatAmount() <= 0 || a.eatAmount() >= 1 || beforeEat == null) {
			return;
		}
		Matrix4f after = pose.last().pose();
		Matrix4f added = new Matrix4f(beforeEat).invert().mul(after);
		Matrix4f partial = new Matrix4f().lerp(added, a.eatAmount());
		after.set(beforeEat).mul(partial);
	}
	public static boolean beforeItem(PoseStack pose, boolean right, boolean mainHand) {
		Animations a = Animations.active();
		if (a == null || !a.transformsItems()) {
			return false;
		}
		float[] rot = a.itemRotation(mainHand);
		float[] stretch = a.stretch();
		float size = a.itemSize(mainHand);
		int side = right ? 1 : -1;
		pose.pushPose();
		pose.mulPose(Axis.XP.rotationDegrees(-rot[0]));
		pose.mulPose(Axis.YP.rotationDegrees(side * rot[1]));
		pose.mulPose(Axis.ZP.rotationDegrees(side * -rot[2]));
		pose.scale(size * stretch[0], size * stretch[1], size * stretch[2]);
		return true;
	}
	public static void afterItem(PoseStack pose, boolean pushed) {
		if (pushed) {
			pose.popPose();
		}
	}

	public static boolean placeHand(PoseStack pose, InteractionHand hand) {
		Animations a = Animations.active();
		if (a == null) {
			return false;
		}
		if (hand == InteractionHand.OFF_HAND && a.hideOffhand()) {
			return true;
		}
		float[] o = a.offset(hand == InteractionHand.MAIN_HAND);
		float s = a.handScale();
		pose.translate(o[0], o[1], o[2]);
		pose.scale(s, s, s);
		return false;
	}
	public static void restoreHand(PoseStack pose, InteractionHand hand) {
		Animations a = Animations.active();
		if (a == null) {
			return;
		}
		float[] o = a.offset(hand == InteractionHand.MAIN_HAND);
		float s = a.handScale();
		pose.scale(1 / s, 1 / s, 1 / s);
		pose.translate(-o[0], -o[1], -o[2]);
	}
}
