package nz.temple.client.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.Motion;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

public final class NoSlow extends Module {
	private static volatile NoSlow instance;
	private final nz.temple.setting.ModeSetting mode;
	private final NumberSetting strength;
	private final nz.temple.setting.BoolSetting items;
	private final nz.temple.setting.BoolSetting liquids;
	private final nz.temple.setting.BoolSetting blocks;
	private static final double GRIM_CHECKED_MOVE = 0.04;
	private boolean lastSlowed;
	private double lastMove;
	public NoSlow() {
		super("No Slow", Category.MOVEMENT, "Removes the slowdown from using items, water and lava, and cobwebs.");
		mode = add(new nz.temple.setting.ModeSetting("Mode", java.util.List.of("Normal", "Grim"), "Normal")
				.describe("Grim removes the item slowdown on every other tick, which Grim accepts; water, lava and cobwebs are left alone. Normal removes it on every tick."));
		strength = add(new NumberSetting("Strength", 100, 0, 100, 1, "%")
				.describe("How much of your normal speed is kept while using items. 100% removes the slowdown."));
		items = add(new nz.temple.setting.BoolSetting("Items", true)
				.describe("No slowdown while eating, drinking, blocking or drawing a bow."));
		liquids = add(new nz.temple.setting.BoolSetting("Water and lava", true)
				.describe("Moves at your normal walking or sprinting speed in water and lava. Up and down stay as they are.")
				.visibleWhen(() -> !mode.is("Grim")));
		blocks = add(new nz.temple.setting.BoolSetting("Cobwebs", true)
				.describe("Cobwebs, sweet berry bushes and powder snow no longer slow you.")
				.visibleWhen(() -> !mode.is("Grim")));
		on(TickEvent.class, e -> tick());
		instance = this;
		nz.temple.client.util.UseSlowdown.setMultiplier(NoSlow::kept);
		nz.temple.client.util.UseSlowdown.setUnstuck(player -> isEnabled() && blocks.get() && !mode.is("Grim")
				&& player == Minecraft.getInstance().player);
	}
	public static Float kept(LocalPlayer player) {
		NoSlow m = instance;
		if (m == null || !m.isEnabled() || !m.items.get() || player != Minecraft.getInstance().player) {
			return null;
		}
		if (m.mode.is("Grim")) {
			boolean free = player.getTicksUsingItem() > 2 && m.lastSlowed && m.lastMove > GRIM_CHECKED_MOVE;
			m.lastSlowed = !free;
			if (!free) {
				return null;
			}
		}
		return (float) (0.2 + 0.8 * m.strength.get() / 100.0);
	}
	private void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		lastMove = Math.hypot(player.getX() - player.xo, player.getZ() - player.zo);
		if (!player.isUsingItem()) {
			lastSlowed = false;
		}
		if (liquids.get() && !mode.is("Grim") && (player.isInWater() || player.isInLava()) && !player.getAbilities().flying) {
			Vec3 dir = Motion.wishDirection(player);
			if (dir.lengthSqr() > 1e-6) {
				double base = (player.isSprinting() ? 0.2873 : 0.22) * strength.get() / 100.0;
				Vec3 v = player.getDeltaMovement();
				player.setDeltaMovement(dir.x * base, v.y, dir.z * base);
			}
			return;
		}
	}
}
