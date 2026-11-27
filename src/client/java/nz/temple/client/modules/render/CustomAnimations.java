package nz.temple.client.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import nz.temple.client.render.SwingAnimator;
import nz.temple.client.util.Inv;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;
import java.util.Map;

public final class CustomAnimations extends Module implements SwingAnimator {
	private record Swing(float[][] keys, boolean spin) {
		Swing(float[][] keys) {
			this(keys, false);
		}
	}
	private static final Map<String, Swing> SWORD = Map.of(
			"Crescent", new Swing(new float[][] {
					{0, 0, 0, 0, 0, 0, 0},
					{0.15f, 0.08f, 0.06f, 0.06f, 15, -35, 20},
					{0.45f, -0.22f, -0.04f, -0.18f, -25, 75, -45},
					{0.65f, -0.30f, -0.10f, -0.08f, -35, 95, -60},
					{0.85f, -0.04f, -0.02f, 0, -5, 10, -5},
					{1, 0, 0, 0, 0, 0, 0}}),
			"Backhand", new Swing(new float[][] {
					{0, 0, 0, 0, 0, 0, 0},
					{0.18f, -0.18f, 0.04f, -0.02f, 5, 55, -30},
					{0.50f, 0.16f, -0.04f, -0.16f, -25, -60, 40},
					{0.70f, 0.22f, -0.08f, -0.06f, -30, -75, 50},
					{0.88f, 0.03f, -0.01f, 0, -4, -8, 5},
					{1, 0, 0, 0, 0, 0, 0}}),
			"Spin Cut", new Swing(new float[][] {
					{0, 0, 0, 0, 0, 0, 0},
					{0.25f, 0.01f, 0.08f, -0.05f, 10, 0, -180},
					{0.50f, 0.02f, 0.10f, -0.08f, 25, 10, -360},
					{0.70f, -0.06f, -0.12f, -0.20f, -70, 20, -360},
					{0.88f, -0.02f, -0.04f, -0.06f, -20, 5, -360},
					{1, 0, 0, 0, 0, 0, -360}}, true),
			"Uppercut", new Swing(new float[][] {
					{0, 0, 0, 0, 0, 0, 0},
					{0.20f, 0.04f, -0.14f, 0.04f, 30, -15, 15},
					{0.50f, -0.14f, 0.16f, -0.18f, 40, 45, -35},
					{0.70f, -0.20f, 0.20f, -0.10f, 55, 60, -50},
					{0.90f, -0.03f, 0.03f, 0, 8, 8, -6},
					{1, 0, 0, 0, 0, 0, 0}}),
			"Execution", new Swing(new float[][] {
					{0, 0, 0, 0, 0, 0, 0},
					{0.30f, 0, 0.22f, 0.05f, 70, -5, 8},
					{0.42f, 0, 0.24f, 0.06f, 75, -5, 8},
					{0.60f, 0, -0.12f, -0.22f, -85, 5, -5},
					{0.70f, 0, -0.09f, -0.20f, -70, 5, -5},
					{0.88f, 0, -0.02f, -0.04f, -12, 0, 0},
					{1, 0, 0, 0, 0, 0, 0}}),
			"Feint", new Swing(new float[][] {
					{0, 0, 0, 0, 0, 0, 0},
					{0.20f, -0.10f, 0.02f, -0.06f, -10, 30, -15},
					{0.35f, 0.12f, 0.05f, 0.04f, 10, -45, 25},
					{0.60f, -0.20f, -0.05f, -0.18f, -25, 70, -40},
					{0.85f, -0.05f, -0.02f, -0.02f, -5, 12, -8},
					{1, 0, 0, 0, 0, 0, 0}}),
			"Lunge", new Swing(new float[][] {
					{0, 0, 0, 0, 0, 0, 0},
					{0.15f, 0.02f, 0.10f, 0.02f, 35, -10, 30},
					{0.35f, 0.04f, 0.02f, 0.10f, 0, -5, 10},
					{0.55f, -0.02f, 0.02f, -0.55f, -8, 4, -5},
					{0.70f, -0.02f, 0.01f, -0.50f, -6, 3, -4},
					{0.90f, 0, 0, -0.08f, -2, 0, 0},
					{1, 0, 0, 0, 0, 0, 0}}));
	private static final List<String> COMBO = List.of("Crescent", "Backhand", "Lunge");
	private static final Map<String, Swing> PICKAXE = Map.of(
			"Drive", new Swing(new float[][] {
					{0, 0, 0, 0, 0, 0, 0},
					{0.30f, 0, 0.12f, 0.04f, 45, 0, 0},
					{0.55f, 0, -0.08f, -0.18f, -70, 0, 0},
					{0.65f, 0, -0.04f, -0.14f, -50, 0, 0},
					{0.85f, 0, -0.01f, -0.03f, -10, 0, 0},
					{1, 0, 0, 0, 0, 0, 0}}),
			"Double Tap", new Swing(new float[][] {
					{0, 0, 0, 0, 0, 0, 0},
					{0.15f, 0, 0.05f, 0, 25, 0, 0},
					{0.30f, 0, -0.05f, -0.10f, -45, 0, 0},
					{0.45f, 0, 0.03f, -0.02f, 15, 0, 0},
					{0.62f, 0, -0.06f, -0.12f, -50, 0, 0},
					{0.85f, 0, -0.01f, -0.02f, -8, 0, 0},
					{1, 0, 0, 0, 0, 0, 0}}),
			"Drill", new Swing(new float[][] {
					{0, 0, 0, 0, 0, 0, 0},
					{0.40f, -0.04f, -0.02f, -0.25f, -30, 10, -180},
					{0.70f, -0.04f, -0.02f, -0.28f, -35, 10, -360},
					{0.90f, -0.01f, 0, -0.05f, -5, 2, -360},
					{1, 0, 0, 0, 0, 0, -360}}, true),
			"Side Swing", new Swing(new float[][] {
					{0, 0, 0, 0, 0, 0, 0},
					{0.25f, 0.08f, 0.10f, 0.04f, 35, -25, 25},
					{0.55f, -0.12f, -0.10f, -0.18f, -55, 30, -35},
					{0.80f, -0.04f, -0.03f, -0.04f, -12, 8, -8},
					{1, 0, 0, 0, 0, 0, 0}}));
	private static final Map<String, Swing> AXE = Map.of(
			"Lumberjack", new Swing(new float[][] {
					{0, 0, 0, 0, 0, 0, 0},
					{0.30f, 0.14f, 0.10f, 0.10f, 25, -60, 35},
					{0.55f, -0.20f, -0.06f, -0.20f, -35, 70, -30},
					{0.72f, -0.26f, -0.10f, -0.10f, -40, 85, -40},
					{0.90f, -0.04f, -0.02f, 0, -6, 12, -6},
					{1, 0, 0, 0, 0, 0, 0}}),
			"Executioner", new Swing(new float[][] {
					{0, 0, 0, 0, 0, 0, 0},
					{0.35f, 0, 0.25f, 0.08f, 80, 0, 0},
					{0.48f, 0, 0.26f, 0.08f, 85, 0, 0},
					{0.62f, 0, -0.16f, -0.20f, -90, 0, 0},
					{0.70f, 0, -0.14f, -0.19f, -82, 0, 0},
					{0.90f, 0, -0.03f, -0.04f, -15, 0, 0},
					{1, 0, 0, 0, 0, 0, 0}}),
			"Tomahawk", new Swing(new float[][] {
					{0, 0, 0, 0, 0, 0, 0},
					{0.35f, 0.06f, 0.14f, 0.18f, 60, -15, 10},
					{0.55f, -0.02f, -0.02f, -0.40f, -60, 10, -8},
					{0.70f, -0.02f, -0.04f, -0.34f, -50, 8, -6},
					{0.90f, 0, -0.01f, -0.06f, -8, 1, 0},
					{1, 0, 0, 0, 0, 0, 0}}),
			"Whirl", new Swing(new float[][] {
					{0, 0, 0, 0, 0, 0, 0},
					{0.50f, -0.06f, 0, -0.12f, -10, 180, -10},
					{0.80f, -0.02f, 0, -0.05f, -4, 330, -4},
					{1, 0, 0, 0, 0, 360, 0}}, true));
	private final ModeSetting swordAnim;
	private final NumberSetting swordIntensity;
	private final ModeSetting pickAnim;
	private final NumberSetting pickIntensity;
	private final ModeSetting axeAnim;
	private final NumberSetting axeIntensity;
	private float lastSwing;
	private int comboStep;
	public CustomAnimations() {
		super("Custom Animations", Category.COSMETICS, "Custom first-person swing animations, with separate sets for swords and "
				+ "general items, pickaxes and axes. The set is chosen from the item in your hand.");
		swordAnim = add(new ModeSetting("Sword swing", List.of("Crescent", "Backhand", "Spin Cut", "Uppercut", "Execution", "Feint", "Lunge",
				"Combo", "Off"), "Crescent")
				.describe("Swing used for swords and any item without its own set. Crescent sweeps wide across the view. Backhand cuts "
						+ "back the other way. Spin Cut twirls the blade around the wrist, then cuts down. Uppercut dips and rips upward. "
						+ "Execution raises the blade, holds it, then slams it down. Feint shows one side and strikes from the other. "
						+ "Lunge flicks up to parry, then stabs forward. Combo strings Crescent, Backhand and Lunge across consecutive "
						+ "hits."));
		swordIntensity = add(new NumberSetting("Sword intensity", 1, 0.2, 2, 0.05, "x")
				.describe("How far the sword swing travels.")
				.visibleWhen(() -> !swordAnim.is("Off")));
		group("Pickaxes");
		pickAnim = add(new ModeSetting("Pickaxe swing", List.of("Drive", "Double Tap", "Drill", "Side Swing", "Off"), "Drive")
				.describe("Swing used when holding a pickaxe. Drive raises the pick and drives it down with a recoil. Double Tap "
						+ "strikes twice in one swing. Drill pushes forward while turning a full circle. Side Swing strokes "
						+ "diagonally from high right to low left."));
		pickIntensity = add(new NumberSetting("Pickaxe intensity", 1, 0.2, 2, 0.05, "x")
				.describe("How far the pickaxe swing travels.")
				.visibleWhen(() -> !pickAnim.is("Off")));
		group("Axes");
		axeAnim = add(new ModeSetting("Axe swing", List.of("Lumberjack", "Executioner", "Tomahawk", "Whirl", "Off"), "Lumberjack")
				.describe("Swing used when holding an axe. Lumberjack winds up behind you and chops across. Executioner raises the axe "
						+ "overhead, holds it, and drops it. Tomahawk cocks back like a throw and snaps forward. Whirl spins the axe "
						+ "flat around the wrist."));
		axeIntensity = add(new NumberSetting("Axe intensity", 1, 0.2, 2, 0.05, "x")
				.describe("How far the axe swing travels.")
				.visibleWhen(() -> !axeAnim.is("Off")));
	}
	@Override
	public boolean applySwing(PoseStack pose, HumanoidArm arm, float swing, ItemStack stack) {
		int side = arm == HumanoidArm.RIGHT ? 1 : -1;
		String path = Inv.path(stack);
		if (path.contains("pickaxe")) {
			return play(pose, side, swing, PICKAXE.get(pickAnim.get()), pickIntensity.getFloat());
		}
		if (path.contains("axe")) {
			return play(pose, side, swing, AXE.get(axeAnim.get()), axeIntensity.getFloat());
		}
		String name = swordAnim.get();
		if (name.equals("Combo")) {
			if (swing + 0.05f < lastSwing) {
				comboStep = (comboStep + 1) % COMBO.size();
			}
			lastSwing = swing;
			name = COMBO.get(comboStep);
		}
		return play(pose, side, swing, SWORD.get(name), swordIntensity.getFloat());
	}
	private static boolean play(PoseStack pose, int side, float t, Swing swing, float k) {
		if (swing == null) {
			return false;
		}
		float[] v = sample(swing.keys, Math.max(0, Math.min(1, t)));
		float turnScale = swing.spin ? 1 : k;
		pose.translate(side * v[1] * k, v[2] * k, v[3] * k);
		pose.mulPose(Axis.YP.rotationDegrees(side * v[5] * turnScale));
		pose.mulPose(Axis.ZP.rotationDegrees(side * v[6] * turnScale));
		pose.mulPose(Axis.XP.rotationDegrees(v[4] * turnScale));
		return true;
	}
	private static float[] sample(float[][] keys, float t) {
		int i = 1;
		while (i < keys.length - 1 && keys[i][0] < t) {
			i++;
		}
		float[] a = keys[i - 1];
		float[] b = keys[i];
		float[] p0 = keys[Math.max(0, i - 2)];
		float[] p3 = keys[Math.min(keys.length - 1, i + 1)];
		float f = (t - a[0]) / Math.max(1e-5f, b[0] - a[0]);
		float[] out = new float[7];
		out[0] = t;
		for (int c = 1; c < 7; c++) {
			out[c] = catmullRom(p0[c], a[c], b[c], p3[c], f);
		}
		return out;
	}
	private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
		float t2 = t * t;
		float t3 = t2 * t;
		return 0.5f * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
	}
}
