package nz.temple.client.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.Motion;
import nz.temple.client.TempleNzClient;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;

public final class Noclip extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private final ModeSetting mode;
	private final NumberSetting speed;
	private final NumberSetting verticalSpeed;
	private final BoolSetting vehicles;
	private final BoolSetting hover;
	private final BoolSetting cancelFall;
	private final BoolSetting phaseDown;
	private Entity ridden;
	private boolean warned;
	private boolean wasSneaking;

	private static final int PHASE_DEPTH = 9;
	public Noclip() {
		super("Noclip", Category.MOVEMENT, "Fly through blocks. Works in singleplayer and on servers that do not check movement collisions.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Mines whatever you walk into, looking at it and using your best tool, instead of passing through."));
		mode = add(new ModeSetting("Mode", List.of("Fly", "Drift"), "Fly")
				.describe("Fly only moves you while you hold a movement key. Drift keeps your momentum."));
		speed = add(new NumberSetting("Speed", 0.5, 0.05, 3, 0.05, " b/t")
				.describe("Horizontal speed. 0.28 is about a sprint."));
		verticalSpeed = add(new NumberSetting("Vertical speed", 0.4, 0.05, 3, 0.05, " b/t")
				.describe("Speed when holding jump or sneak."));
		group();
		vehicles = add(new BoolSetting("Vehicles", true)
				.describe("Also applies to the boat, minecart or animal you are riding."));
		hover = add(new BoolSetting("Hover", true)
				.describe("Holds your position when you release the keys instead of falling."));
		cancelFall = add(new BoolSetting("Cancel fall damage", true)
				.describe("Keeps your fall distance at zero while active."));
		phaseDown = add(new BoolSetting("Phase down", true)
				.describe("Tap sneak while standing to drop through the floor into the first gap below that fits you, up to 9 blocks down. "
						+ "With Legit on, holding sneak mines the block under you instead."));
		instance = this;
		hideWhenLegit(legit, mode, speed, verticalSpeed, vehicles, hover, cancelFall);
		on(TickEvent.class, e -> tick());
	}
	private static Noclip instance;
	public static boolean applies(net.minecraft.world.entity.player.Player player) {
		Noclip noclip = instance;
		if (noclip == null || !noclip.isEnabled() || noclip.legit.get()) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer local = mc.player;
		if (local == null) {
			return false;
		}
		if (player == local) {
			return true;
		}
		return player instanceof net.minecraft.server.level.ServerPlayer && mc.hasSingleplayerServer()
				&& player.getUUID().equals(local.getUUID());
	}
	@Override
	protected void onDisable() {
		restoreCollision();
		warned = false;
	}
	private void restoreCollision() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.noPhysics = player.isSpectator();
		}
		if (ridden != null) {
			nz.temple.client.util.VehiclePhase.set(ridden, false);
			ridden = null;
		}
	}
	private static void mineAhead(Minecraft mc, LocalPlayer player) {
		Vec3 wish = Motion.wishDirection(player);
		if (wish.lengthSqr() < 1e-6 || mc.gui.screen() != null) {
			return;
		}
		net.minecraft.core.BlockPos feet = net.minecraft.core.BlockPos.containing(player.getX() + wish.x * 0.8, player.getY() + 0.2,
				player.getZ() + wish.z * 0.8);
		for (net.minecraft.core.BlockPos pos : new net.minecraft.core.BlockPos[] {feet, feet.above()}) {
			if (!mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty()) {
				nz.temple.client.util.LegitAim.mine(player, pos, 40);
				return;
			}
		}
	}
	private static void mineBelow(Minecraft mc, LocalPlayer player) {
		net.minecraft.core.BlockPos below = net.minecraft.core.BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
		if (!mc.level.getBlockState(below).getCollisionShape(mc.level, below).isEmpty()) {
			nz.temple.client.util.LegitAim.mine(player, below, 40);
		}
	}
	private static boolean phaseThrough(Minecraft mc, LocalPlayer player) {
		AABB box = player.getBoundingBox();
		if (player.getVehicle() != null || mc.level.noCollision(player, box.move(0, -0.1, 0))) {
			return false;
		}
		double feet = Math.floor(player.getY() + 1e-4);
		for (int d = 1; d <= PHASE_DEPTH && feet - d >= mc.level.getMinY(); d++) {
			AABB at = box.move(0, feet - d - player.getY(), 0);
			if (mc.level.noCollision(player, at)
					&& mc.level.getBlockStatesIfLoaded(at).noneMatch(state -> state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA))) {
				player.setPos(player.getX(), feet - d, player.getZ());
				player.setDeltaMovement(Vec3.ZERO);
				player.fallDistance = 0;
				return true;
			}
		}
		return false;
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			restoreCollision();
			return;
		}
		boolean sneaking = mc.options.keyShift.isDown() && mc.gui.screen() == null;
		boolean sneakTapped = sneaking && !wasSneaking;
		wasSneaking = sneaking;
		if (legit.get()) {
			restoreCollision();
			if (phaseDown.get() && sneaking) {
				mineBelow(mc, player);
			} else {
				mineAhead(mc, player);
			}
			return;
		}
		if (!mc.hasSingleplayerServer() && !player.isCreative() && !player.isSpectator() && !warned) {
			if (TempleNzClient.notifications() != null) {
				TempleNzClient.notifications().info("Noclip", "The server checks collisions itself; unless it allows noclip it will pull you back.");
			}
			warned = true;
		}
		player.noPhysics = true;
		player.setOnGround(false);
		if (cancelFall.get()) {
			player.fallDistance = 0;
		}
		Entity vehicle = player.getVehicle();
		if (ridden != null && (ridden != vehicle || !vehicles.get())) {
			nz.temple.client.util.VehiclePhase.set(ridden, false);
			ridden = null;
		}
		Entity moved = player;
		if (vehicles.get() && vehicle != null) {
			nz.temple.client.util.VehiclePhase.set(vehicle, true);
			ridden = vehicle;
			moved = vehicle;
		}
		if (mc.gui.screen() != null) {
			return;
		}
		if (phaseDown.get() && sneakTapped && moved == player && phaseThrough(mc, player)) {
			return;
		}
		Vec3 wish = Motion.wishDirection(player);
		double vertical = (mc.options.keyJump.isDown() ? 1 : 0) - (mc.options.keyShift.isDown() ? 1 : 0);
		boolean idle = wish.lengthSqr() < 1e-6 && vertical == 0;
		if (idle && mode.is("Drift")) {
			Vec3 v = moved.getDeltaMovement();
			moved.setDeltaMovement(v.scale(0.93));
			return;
		}
		if (idle) {
			moved.setDeltaMovement(hover.get() ? Vec3.ZERO : new Vec3(0, moved.getDeltaMovement().y * 0.6, 0));
			return;
		}
		Vec3 motion = wish.scale(speed.get()).add(0, vertical * verticalSpeed.get(), 0);
		if (mode.is("Drift")) {
			motion = moved.getDeltaMovement().scale(0.8).add(motion.scale(0.35));
		}
		moved.setDeltaMovement(motion);
	}
}
