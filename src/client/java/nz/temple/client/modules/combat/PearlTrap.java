package nz.temple.client.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.Place;
import nz.temple.client.util.ProjectileSim;
import nz.temple.client.util.Teleport;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;
import nz.temple.social.FriendManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PearlTrap extends Module {
	private enum Stage { IDLE, TRAVEL, WAIT, RETURN }
	private final FriendManager friends;
	private final NumberSetting maxDistance;
	private final NumberSetting minThrow;
	private final NumberSetting standOff;
	private final BoolSetting tryAnyway;
	private final BoolSetting ignoreFriends;
	private final ModeSetting trap;
	private final BoolSetting rotate;
	private final BoolSetting pickUpLava;
	private final NumberSetting pickUpAfter;
	private final BoolSetting hit;
	private final BoolSetting returnHome;
	private final NumberSetting stayFor;
	private final BoolSetting announce;
	private final BoolSetting legit;
	private final Set<Integer> handled = new HashSet<>();
	private Stage stage = Stage.IDLE;
	private Vec3 home;
	private Vec3 spot;
	private BlockPos landing;
	private Player thrower;
	private int pearlId;
	private int arrivesIn;
	private int waited;
	private boolean trapped;
	private int lavaTicks = -1;
	public PearlTrap(FriendManager friends) {
		super("Pearl Trap", Category.HVH, "Predicts where an enemy's ender pearl will land and teleports there first. Uses the vanilla "
				+ "movement allowance; servers with stricter movement checks may pull you back.");
		this.friends = friends;
		legit = add(new BoolSetting("Legit", false)
				.describe("No teleporting. Sprints to the landing spot with your movement keys (so only pearls you can reach in time "
						+ "count), places the trap, attacks the thrower by looking at them, and walks back if Return is on."));
		maxDistance = add(new NumberSetting("Max distance", 60, 10, 160, 5, " m")
				.describe("Ignore pearls landing further away than this. Each 19.5 blocks takes one tick to travel."));
		minThrow = add(new NumberSetting("Min throw", 6, 0, 30, 1, " m")
				.describe("Ignore short throws, such as pearls thrown at the ground to cancel fall damage."));
		standOff = add(new NumberSetting("Stand-off", 2.2, 1, 4, 0.1, " m")
				.describe("How far from the landing spot to wait."));
		tryAnyway = add(new BoolSetting("Go if late", false)
				.describe("Go even if the pearl will land before you arrive."));
		ignoreFriends = add(new BoolSetting("Ignore friends", true).describe("Leave your friends' pearls alone."));
		group();
		trap = add(new ModeSetting("Trap", List.of("None", "Cobweb", "Lava", "Both"), "None")
				.describe("What to place on the landing spot. The item must be in your hotbar. Both places a web and pours lava next to it."));
		rotate = add(new BoolSetting("Rotate to place", true)
				.describe("Face the block before placing it. Some servers reject placements you are not looking at.")
				.visibleWhen(() -> !trap.is("None")));
		pickUpLava = add(new BoolSetting("Pick lava back up", true)
				.describe("Picks the lava back up afterwards.")
				.visibleWhen(() -> trap.is("Lava") || trap.is("Both")));
		pickUpAfter = add(new NumberSetting("Pick up after", 30, 5, 100, 5, " ticks")
				.describe("How long the lava stays before being picked up.")
				.visibleWhen(() -> (trap.is("Lava") || trap.is("Both")) && pickUpLava.get()));
		hit = add(new BoolSetting("Hit on arrival", true)
				.describe("Attacks the thrower as soon as they land within reach."));
		returnHome = add(new BoolSetting("Return", false)
				.describe("Teleports back to your original position afterwards."));
		stayFor = add(new NumberSetting("Stay for", 3, 0.5, 20, 0.5, " s")
				.describe("How long to wait at the landing spot before returning.")
				.visibleWhen(returnHome::get));
		announce = add(new BoolSetting("Notify", true).describe("Sends a chat message when a pearl is being intercepted."));
		hideWhenLegit(legit, rotate);
		on(TickEvent.class, e -> tick());
	}
	@Override
	protected void onDisable() {
		stage = Stage.IDLE;
		handled.clear();
		thrower = null;
		lavaTicks = -1;
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.gameMode == null) {
			return;
		}
		if (player.isDeadOrDying()) {
			stage = Stage.IDLE;
			return;
		}
		handled.removeIf(id -> mc.level.getEntity(id) == null);
		tickLava(player);
		switch (stage) {
			case IDLE -> watch(mc, player);
			case TRAVEL -> travel(player);
			case WAIT -> waitThere(mc, player);
			case RETURN -> goHome(player);
		}
	}
	private void watch(Minecraft mc, LocalPlayer player) {
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (!(entity instanceof ThrownEnderpearl pearl) || !handled.add(pearl.getId())
					|| !(pearl.getOwner() instanceof Player owner) || owner == player
					|| ignoreFriends.get() && friends.isFriend(owner.getName().getString())) {
				continue;
			}
			ProjectileSim.Flight flight = ProjectileSim.predict(pearl, true);
			if (flight == null || flight.hitEntity() == player || flight.end().distanceTo(owner.position()) < minThrow.get()) {
				continue;
			}
			BlockPos land = landingBlock(flight);
			Vec3 feet = Vec3.atBottomCenterOf(land);
			if (feet.distanceTo(player.position()) > maxDistance.get()) {
				continue;
			}
			Vec3 wait = Teleport.spotNear(player, feet, standOff.get(), owner.position());
			if (wait == null) {
				continue;
			}
			int need = legit.get() ? (int) Math.ceil(player.position().distanceTo(wait) / 0.28) + nz.temple.client.util.Human.reactionTicks()
					: Teleport.ticksFor(player.position().distanceTo(wait));
			if (need >= flight.ticks() && !tryAnyway.get()) {
				continue;
			}
			home = player.position();
			spot = wait;
			landing = land;
			thrower = owner;
			pearlId = pearl.getId();
			arrivesIn = flight.ticks();
			waited = 0;
			trapped = false;
			stage = Stage.TRAVEL;
			if (announce.get()) {
				nz.temple.client.compat.Players.message(player, Component.literal("§6Pearl Trap§r: " + owner.getName().getString() + " lands at "
						+ nz.temple.client.modules.client.StreamerMode.alert(land.getX(), land.getY(), land.getZ()) + " in " + flight.ticks() + " ticks"));
			}
			travel(player);
			return;
		}
	}
	private static BlockPos landingBlock(ProjectileSim.Flight flight) {
		List<Vec3> points = flight.points();
		Vec3 end = flight.end();
		Vec3 before = points.size() > 1 ? points.get(points.size() - 2) : end;
		Vec3 back = before.subtract(end);
		Vec3 nudge = back.lengthSqr() > 1.0E-6 ? back.normalize().scale(0.05) : Vec3.ZERO;
		return BlockPos.containing(end.add(nudge).add(0, 0.01, 0));
	}
	private void travel(LocalPlayer player) {
		arrivesIn--;
		if (legit.get()) {
			nz.temple.client.util.LegitAim.walk(player, spot, true);
			if (player.position().distanceTo(spot) < 0.9) {
				nz.temple.client.util.Steering.stop();
				stage = Stage.WAIT;
			}
			return;
		}
		Vec3 now = Teleport.step(player, spot);
		if (now == null) {
			stage = returnHome.get() ? Stage.RETURN : Stage.IDLE;
			return;
		}
		if (now.distanceTo(spot) < 0.1) {
			stage = Stage.WAIT;
			placeTrap(player);
		}
	}
	private void waitThere(Minecraft mc, LocalPlayer player) {
		waited++;
		arrivesIn--;
		if (!trapped && arrivesIn > 0) {
			placeTrap(player);
		}
		boolean pearlGone = mc.level.getEntity(pearlId) == null;
		if (hit.get() && pearlGone && thrower != null && thrower.isAlive() && player.getAttackStrengthScale(0.5f) >= 0.9f
				&& thrower.getBoundingBox().distanceToSqr(player.getEyePosition()) <= 9) {
			if (legit.get()) {
				nz.temple.client.util.LegitAim.hit(player, thrower, 40, 0.9f);
			} else {
				mc.gameMode.attack(player, thrower);
				nz.temple.client.compat.Players.swing(player, net.minecraft.world.InteractionHand.MAIN_HAND);
			}
		}
		if (returnHome.get() && waited >= stayFor.get() * 20) {
			stage = Stage.RETURN;
		} else if (!returnHome.get() && pearlGone && waited > 40) {
			stage = Stage.IDLE;
		}
	}
	private void goHome(LocalPlayer player) {
		if (legit.get()) {
			if (home == null || player.position().distanceTo(home) < 0.9) {
				nz.temple.client.util.Steering.stop();
				stage = Stage.IDLE;
			} else {
				nz.temple.client.util.LegitAim.walk(player, home, true);
			}
			return;
		}
		Vec3 now = home == null ? null : Teleport.step(player, home);
		if (now == null || now.distanceTo(home) < 0.1) {
			stage = Stage.IDLE;
		}
	}
	private void placeTrap(LocalPlayer player) {
		if (trap.is("None") || landing == null) {
			trapped = true;
			return;
		}
		boolean web = trap.is("Cobweb") || trap.is("Both");
		boolean lava = trap.is("Lava") || trap.is("Both");
		if (legit.get()) {
			if (web && Minecraft.getInstance().level.getBlockState(landing).canBeReplaced()) {
				if (nz.temple.client.util.LegitAim.place(player, landing, Place.item("cobweb"), 40)
						== nz.temple.client.util.LegitAim.Step.WORKING) {
					return;
				}
			}
			BlockPos into = lava ? web ? besideLanding(player) : landing : null;
			if (into != null && Minecraft.getInstance().level.getBlockState(into).canBeReplaced()) {
				int slot = Place.hotbarSlot(player, Place.item("lava_bucket"));
				if (nz.temple.client.util.LegitAim.useToward(player, slot, Vec3.atBottomCenterOf(into), 40, 4)
						== nz.temple.client.util.LegitAim.Step.WORKING) {
					return;
				}
				if (pickUpLava.get()) {
					lavaTicks = (int) Math.round(pickUpAfter.get());
					lavaAt = into;
				}
			}
			trapped = true;
			return;
		}
		if (web) {
			Place.placeWith(player, landing, Place.item("cobweb"), rotate.get(), true);
		}
		if (lava) {
			BlockPos into = web ? besideLanding(player) : landing;
			if (into != null) {
				pour(player, into);
			}
		}
		trapped = true;
	}
	private BlockPos besideLanding(LocalPlayer player) {
		Minecraft mc = Minecraft.getInstance();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			BlockPos p = landing.relative(d);
			if (mc.level.getBlockState(p).canBeReplaced() && p.distToCenterSqr(player.getEyePosition()) > 2.5) {
				return p;
			}
		}
		return null;
	}
	private void pour(LocalPlayer player, BlockPos into) {
		Minecraft mc = Minecraft.getInstance();
		int slot = Place.hotbarSlot(player, Place.item("lava_bucket"));
		if (slot < 0 || !mc.level.getBlockState(into).canBeReplaced()) {
			return;
		}
		for (Direction d : new Direction[] {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
			BlockPos support = into.relative(d);
			if (mc.level.getBlockState(support).isCollisionShapeFullBlock(mc.level, support)) {
				Vec3 face = Vec3.atCenterOf(support).add(d.getStepX() * -0.5, d.getStepY() * -0.5, d.getStepZ() * -0.5);
				Place.useFacing(player, slot, face);
				if (pickUpLava.get()) {
					lavaTicks = (int) Math.round(pickUpAfter.get());
					lavaAt = into;
				}
				return;
			}
		}
	}
	private BlockPos lavaAt;
	private void tickLava(LocalPlayer player) {
		if (lavaTicks < 0 || lavaAt == null) {
			return;
		}
		if (lavaTicks-- > 0) {
			return;
		}
		lavaTicks = -1;
		int slot = Place.hotbarSlot(player, Place.item("bucket"));
		Minecraft mc = Minecraft.getInstance();
		if (legit.get() && slot >= 0 && mc.level.getFluidState(lavaAt).isSource()) {
			if (nz.temple.client.util.LegitAim.useToward(player, slot, Vec3.atCenterOf(lavaAt), 40, 4)
					== nz.temple.client.util.LegitAim.Step.WORKING) {
				lavaTicks = 0;
				return;
			}
		} else if (slot >= 0 && mc.level.getFluidState(lavaAt).isSource()
				&& lavaAt.distToCenterSqr(player.getEyePosition()) <= 4.5 * 4.5) {
			Place.useFacing(player, slot, Vec3.atCenterOf(lavaAt).add(0, 0.3, 0));
		}
		lavaAt = null;
	}
}
