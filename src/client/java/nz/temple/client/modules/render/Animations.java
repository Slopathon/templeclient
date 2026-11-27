package nz.temple.client.modules.render;

import nz.temple.client.TempleNzClient;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;

public final class Animations extends Module {
	private static Animations instance;
	private final ModeSetting swing;
	private final NumberSetting height;
	private final BoolSetting fastSwing;
	private final NumberSetting duration;
	private final BoolSetting noSwing;
	private final BoolSetting noEquip;
	private final BoolSetting noCooldown;
	private final BoolSetting noEat;
	private final BoolSetting noHurtShake;
	private final BoolSetting hideHand;
	private final BoolSetting hideOffhand;
	private final NumberSetting scale;
	private final NumberSetting mainX;
	private final NumberSetting mainY;
	private final NumberSetting mainZ;
	private final NumberSetting offX;
	private final NumberSetting offY;
	private final NumberSetting offZ;
	private final BoolSetting freezeSwing;
	private final NumberSetting swingPose;
	private final NumberSetting equipSpeed;
	private final NumberSetting eatAmount;
	private final NumberSetting cooldownDip;
	private final NumberSetting mainSize;
	private final NumberSetting offSize;
	private final NumberSetting mainRotX;
	private final NumberSetting mainRotY;
	private final NumberSetting mainRotZ;
	private final NumberSetting offRotX;
	private final NumberSetting offRotY;
	private final NumberSetting offRotZ;
	private final NumberSetting stretchX;
	private final NumberSetting stretchY;
	private final NumberSetting stretchZ;
	private final BoolSetting afterimages;
	private final ColorSetting afterimageColor;
	private final NumberSetting afterimageLength;
	private final NumberSetting afterimageGap;
	public Animations() {
		super("Hand Tweaks", Category.COSMETICS, "Adjusts first-person hand animations: swing style and speed, which vanilla animations are "
				+ "disabled, and hand position.");
		instance = this;
		swing = add(new ModeSetting("Swing", List.of("Low", "Smooth", "Vanilla", "Custom", "Stab", "Slide", "Spin", "Tap"), "Low")
				.describe("Low barely lifts the hand. Smooth softens the vanilla swing. Vanilla is unchanged. Custom lets you set the "
						+ "height. Stab thrusts forward. Slide sweeps sideways. Spin rotates the item a full turn. Tap flicks downward."));
		height = add(new NumberSetting("Swing height", 0.4, 0.05, 1, 0.05, "x")
				.describe("How far the arm travels, as a fraction of the vanilla swing.")
				.visibleWhen(() -> swing.is("Custom")));
		fastSwing = add(new BoolSetting("Custom swing speed", true)
				.describe("Overrides how many ticks a swing lasts."));
		duration = add(new NumberSetting("Swing duration", 4, 1, 30, 1, " ticks")
				.describe("Ticks per swing. Vanilla is 6. Lower is faster; higher is slower (20 ticks is one second).")
				.visibleWhen(fastSwing::get));
		group();
		noSwing = add(new BoolSetting("Cancel swing", false)
				.describe("The arm does not move when you attack or use items."));
		noEquip = add(new BoolSetting("Cancel equip", false)
				.describe("Switching items is instant instead of the hand dipping down and back up."));
		noCooldown = add(new BoolSetting("Cancel cooldown dip", false)
				.describe("The hand stays up after attacking instead of lowering while the cooldown recharges."));
		noEat = add(new BoolSetting("Cancel eat/drink", false)
				.describe("Food and potions stay in place instead of being raised to your mouth."));
		noHurtShake = add(new BoolSetting("Cancel hurt shake", false)
				.describe("The view and hand do not shake when you take damage."));
		freezeSwing = add(new BoolSetting("Hold mid-swing", false)
				.describe("Holds the main hand partway through a swing at all times."));
		swingPose = add(new NumberSetting("Swing pose", 35, 0, 100, 1, "%")
				.describe("How far into the swing the hand is held.")
				.visibleWhen(freezeSwing::get));
		group();
		equipSpeed = add(new NumberSetting("Equip speed", 100, 5, 100, 5, "%")
				.describe("Speed of the dip when switching items. 100 is vanilla.")
				.visibleWhen(() -> !noEquip.get()));
		eatAmount = add(new NumberSetting("Eat animation", 100, 0, 100, 5, "%")
				.describe("How much of the eating and drinking animation plays. 0 disables it.")
				.visibleWhen(() -> !noEat.get()));
		cooldownDip = add(new NumberSetting("Cooldown dip", 100, 0, 100, 5, "%")
				.describe("How far the hand lowers while the attack cooldown recharges.")
				.visibleWhen(() -> !noCooldown.get()));
		group();
		hideHand = add(new BoolSetting("Hide hands", false)
				.describe("Hides first-person hands and held items."));
		hideOffhand = add(new BoolSetting("Hide offhand", false)
				.describe("Hides the offhand, for example to keep a totem or shield out of view.")
				.visibleWhen(() -> !hideHand.get()));
		scale = add(new NumberSetting("Hand scale", 1, 0.3, 1.5, 0.05, "x")
				.describe("Size of first-person hands and items.")
				.visibleWhen(() -> !hideHand.get()));
		mainX = add(offset("Main hand X", "Moves the main hand left (-) or right (+)."));
		mainY = add(offset("Main hand Y", "Moves the main hand down (-) or up (+)."));
		mainZ = add(offset("Main hand Z", "Moves the main hand closer (+) or further away (-)."));
		offX = add(offset("Offhand X", "Moves the offhand left (-) or right (+)."));
		offY = add(offset("Offhand Y", "Moves the offhand down (-) or up (+)."));
		offZ = add(offset("Offhand Z", "Moves the offhand closer (+) or further away (-)."));
		group();
		mainSize = add(itemSetting("Main item size", 1, 0.2, 2.5, 0.05, "x", "Size of the item in your main hand, without changing the arm."));
		offSize = add(itemSetting("Offhand item size", 1, 0.2, 2.5, 0.05, "x", "Size of the item in your offhand."));
		mainRotX = add(itemSetting("Main tilt", 0, -180, 180, 5, "°", "Tilts the main-hand item forward (+) or back (-)."));
		mainRotY = add(itemSetting("Main turn", 0, -180, 180, 5, "°", "Turns the main-hand item left or right."));
		mainRotZ = add(itemSetting("Main roll", 0, -180, 180, 5, "°", "Rolls the main-hand item clockwise or anticlockwise."));
		offRotX = add(itemSetting("Offhand tilt", 0, -180, 180, 5, "°", "Tilts the offhand item forward (+) or back (-)."));
		offRotY = add(itemSetting("Offhand turn", 0, -180, 180, 5, "°", "Turns the offhand item left or right."));
		offRotZ = add(itemSetting("Offhand roll", 0, -180, 180, 5, "°", "Rolls the offhand item."));
		stretchX = add(itemSetting("Stretch width", 1, 0.2, 3, 0.05, "x", "Stretches held items sideways."));
		stretchY = add(itemSetting("Stretch height", 1, 0.2, 3, 0.05, "x", "Stretches held items up and down."));
		stretchZ = add(itemSetting("Stretch depth", 1, 0.2, 3, 0.05, "x", "Stretches held items front to back."));
		group("Afterimages");
		afterimages = add(new BoolSetting("Afterimages", false)
				.describe("Swings, eating and item switches leave a fading trail of previous frames behind your hand. Uses the Hand Chams "
						+ "look when Hand Chams is on."));
		afterimageColor = add(new ColorSetting("Afterimage colour", 0xFFE9B949)
				.describe("Trail colour. Its opacity controls how visible the trail is.")
				.visibleWhen(afterimages::get));
		afterimageLength = add(new NumberSetting("Trail length", 0.35, 0.1, 1, 0.05, " s")
				.describe("How long each trail frame takes to fade.")
				.visibleWhen(afterimages::get));
		afterimageGap = add(new NumberSetting("Frame gap", 30, 0, 90, 5, " ms")
				.describe("Time between trail frames. Small values give a smooth smear (0 keeps every frame); large values give separate frames.")
				.visibleWhen(afterimages::get));
	}
	private NumberSetting offset(String name, String description) {
		NumberSetting setting = new NumberSetting(name, 0, -1, 1, 0.05, "");
		setting.describe(description).visibleWhen(() -> !hideHand.get());
		return setting;
	}
	private NumberSetting itemSetting(String name, double value, double min, double max, double step, String unit, String description) {
		NumberSetting setting = new NumberSetting(name, value, min, max, step, unit);
		setting.describe(description).visibleWhen(() -> !hideHand.get());
		return setting;
	}
	public static Animations instance() {
		return instance == null && TempleNzClient.modules() != null
				? TempleNzClient.modules().get(Animations.class) : instance;
	}
	public static Animations active() {
		Animations a = instance();
		return a != null && a.isEnabled() ? a : null;
	}
	public float swingScale() {
		if (noSwing.get()) {
			return 0f;
		}
		return switch (swing.get()) {
			case "Smooth" -> 0.7f;
			case "Low" -> 0.4f;
			case "Custom" -> height.getFloat();
			default -> 1f;
		};
	}
	public String style() {
		return swing.get();
	}

	public boolean customMotion() {
		return swing.is("Stab") || swing.is("Slide") || swing.is("Spin") || swing.is("Tap");
	}
	public boolean overrideSpeed() {
		return fastSwing.get();
	}

	public int swingDuration() {
		return (int) Math.round(duration.get());
	}

	public boolean noEquip() {
		return noEquip.get();
	}

	public boolean noCooldown() {
		return noCooldown.get();
	}

	public boolean noEat() {
		return noEat.get();
	}

	public boolean noHurtShake() {
		return noHurtShake.get();
	}

	public boolean hideHand() {
		return hideHand.get();
	}

	public boolean hideOffhand() {
		return hideOffhand.get();
	}

	public float handScale() {
		return scale.getFloat();
	}

	public float[] offset(boolean mainHand) {
		return mainHand ? new float[] {mainX.getFloat(), mainY.getFloat(), mainZ.getFloat()}
				: new float[] {offX.getFloat(), offY.getFloat(), offZ.getFloat()};
	}
	public float frozenSwing() {
		return freezeSwing.get() ? swingPose.getFloat() / 100f : -1f;
	}
	public float equipSpeed() {
		return equipSpeed.getFloat() / 100f;
	}
	public float eatAmount() {
		return noEat.get() ? 0f : eatAmount.getFloat() / 100f;
	}
	public float cooldownDip() {
		return noCooldown.get() ? 0f : cooldownDip.getFloat() / 100f;
	}
	public boolean transformsItems() {
		return mainSize.get() != 1 || offSize.get() != 1 || mainRotX.get() != 0 || mainRotY.get() != 0 || mainRotZ.get() != 0
				|| offRotX.get() != 0 || offRotY.get() != 0 || offRotZ.get() != 0
				|| stretchX.get() != 1 || stretchY.get() != 1 || stretchZ.get() != 1;
	}
	public float itemSize(boolean mainHand) {
		return (mainHand ? mainSize : offSize).getFloat();
	}

	public float[] itemRotation(boolean mainHand) {
		return mainHand ? new float[] {mainRotX.getFloat(), mainRotY.getFloat(), mainRotZ.getFloat()}
				: new float[] {offRotX.getFloat(), offRotY.getFloat(), offRotZ.getFloat()};
	}
	public boolean afterimages() {
		return afterimages.get() && !hideHand.get();
	}

	public int afterimageColor() {
		return afterimageColor.get();
	}

	public double afterimageLength() {
		return afterimageLength.get();
	}
	public double afterimageGap() {
		return afterimageGap.get() / 1000.0;
	}
	public float[] stretch() {
		return new float[] {stretchX.getFloat(), stretchY.getFloat(), stretchZ.getFloat()};
	}
}
