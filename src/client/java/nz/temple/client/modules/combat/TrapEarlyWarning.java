package nz.temple.client.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.movement.Noclip;
import nz.temple.client.render.WorldEsp;
import nz.temple.client.util.Place;
import nz.temple.client.util.ProjectileSim;
import nz.temple.client.util.Targets;
import nz.temple.client.util.Teleport;
import nz.temple.client.util.WorldEvents;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;
import nz.temple.social.FriendManager;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class TrapEarlyWarning extends Module {
	private enum State { OPEN, FORMING, LAST_OPENING, SEALED }

	private final FriendManager friends;
	private final NumberSetting warnAfter;
	private final NumberSetting enemyRange;
	private final BoolSetting requireEnemy;
	private final BoolSetting notify;
	private final ModeSetting escape;
	private final NumberSetting actionDelay;
	private final NumberSetting noclipSpeed;
	private final ColorSetting blockedColor;
	private final ColorSetting openingColor;

	private final EnumMap<Direction, BlockPos> openings = new EnumMap<>(Direction.class);
	private List<BlockPos> blocked = List.of();
	private int closedSides;
	private State state = State.OPEN;
	private BlockPos trapOrigin;
	private int threatTicks;
	private int actionWait;
	private boolean acted;
	private final BoolSetting legit;
	private boolean noclipByUs;

	public TrapEarlyWarning(FriendManager friends) {
		super("Trap Early Warning", Category.HVH, "Warns while blocks are being placed around you, marks the remaining exit, and can run an "
				+ "escape action if you enable one.");
		this.friends = friends;
		warnAfter = add(new NumberSetting("Warn after", 2, 1, 4, 1, " sides").describe("How many blocked sides count as a trap forming."));
		enemyRange = add(new NumberSetting("Enemy range", 7, 3, 12, 0.5, " m").describe("Range used to link nearby block changes to an "
				+ "enemy."));
		requireEnemy = add(new BoolSetting("Require nearby enemy", true).describe("Only warns when an enemy is close enough to be placing "
				+ "the trap."));
		notify = add(new BoolSetting("Notify", true).describe("Shows a notification when the trap reaches a more dangerous stage."));
		group();
		legit = add(new BoolSetting("Legit", false)
				.describe("Teleport and Noclip become walking out through the gap with your movement keys, and Pearl turns your camera and "
						+ "throws the pearl normally."));
		escape = add(new ModeSetting("Automatic escape", List.of("Off", "Pearl", "Teleport", "Noclip"), "Off")
				.describe("Action to take through the remaining gap. Off only warns. Each automatic option has to be chosen explicitly."));
		actionDelay = add(new NumberSetting("Action delay", 0, 0, 20, 1, " ticks")
				.describe("Ticks to wait after the last gap is detected before acting.")
				.visibleWhen(() -> !escape.is("Off")));
		noclipSpeed = add(new NumberSetting("Noclip speed", 0.9, 0.2, 3, 0.1, " b/t")
				.describe("Movement speed toward the gap while using Noclip.")
				.visibleWhen(() -> escape.is("Noclip")));
		group();
		blockedColor = add(new ColorSetting("Blocked color", 0xFFFF4D5E).describe("Colour of the cage blocks."));
		openingColor = add(new ColorSetting("Opening color", 0xFF43D17B).describe("Colour of the remaining gap."));
		WorldEvents.onBlockChanged(this::blockChanged);
		hideWhenLegit(legit, noclipSpeed);
		on(TickEvent.class, e -> tick());
	}

	@Override
	protected void onDisable() {
		clear(true);
	}

	private void blockChanged(BlockPos pos, net.minecraft.world.level.block.state.BlockState oldState,
			net.minecraft.world.level.block.state.BlockState newState) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (!isEnabled() || player == null || mc.level == null) {
			return;
		}
		BlockPos feet = Targets.feet(player);
		if (!inShell(feet, pos)) {
			return;
		}
		boolean wasBlocking = !oldState.getCollisionShape(mc.level, pos).isEmpty();
		boolean nowBlocking = !newState.getCollisionShape(mc.level, pos).isEmpty();
		if (!wasBlocking && nowBlocking) {
			threatTicks = 40;
			trapOrigin = feet.immutable();
		}
	}

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || player.isDeadOrDying()) {
			clear(true);
			return;
		}
		if (threatTicks > 0) {
			threatTicks--;
		}
		if (actionWait > 0) {
			actionWait--;
		}
		BlockPos feet = Targets.feet(player);
		if (trapOrigin != null && horizontalDistance(feet, trapOrigin) > 2) {
			clear(true);
			return;
		}
		recalculate(player, feet);
		boolean enemyNear = Targets.nearestEnemy(player, enemyRange.get(), friends, false) != null;
		boolean credible = (threatTicks > 0 || state != State.OPEN) && (!requireEnemy.get() || enemyNear);
		State next = !credible || closedSides < warnAfter.get() ? State.OPEN
				: openings.isEmpty() ? State.SEALED
				: openings.size() == 1 ? State.LAST_OPENING : State.FORMING;
		if (next != state) {
			state = next;
			if (next == State.OPEN) {
				acted = false;
				actionWait = 0;
				stopNoclip();
			} else {
				actionWait = (int) Math.round(actionDelay.get());
				announce(next);
			}
		}
		if (state == State.LAST_OPENING && !escape.is("Off")) {
			Direction direction = openings.keySet().iterator().next();
			Vec3 target = Vec3.atBottomCenterOf(openings.get(direction));
			act(player, direction, target);
		} else if (state != State.LAST_OPENING) {
			stopNoclip();
		}
	}

	private void recalculate(LocalPlayer player, BlockPos feet) {
		Minecraft mc = Minecraft.getInstance();
		List<BlockPos> walls = new ArrayList<>();
		closedSides = 0;
		openings.clear();
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos low = feet.relative(direction);
			BlockPos high = low.above();
			if (blocking(low)) {
				walls.add(low.immutable());
			}
			if (blocking(high)) {
				walls.add(high.immutable());
			}
			if (blocking(low) || blocking(high)) {
				closedSides++;
			} else {
				BlockPos target = escapeTarget(player, feet, direction);
				if (target != null) {
					openings.put(direction, target);
				}
			}
		}
		blocked = List.copyOf(walls);
	}

	private static boolean blocking(BlockPos pos) {
		Minecraft mc = Minecraft.getInstance();
		return !mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty();
	}

	private static BlockPos escapeTarget(LocalPlayer player, BlockPos feet, Direction direction) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 from = player.position();
		for (int distance = 2; distance <= 5; distance++) {
			for (int dy : new int[] {0, -1, 1}) {
				BlockPos target = feet.relative(direction, distance).offset(0, dy, 0);
				BlockPos below = target.below();
				Vec3 at = Vec3.atBottomCenterOf(target);
				if (Teleport.fits(player, at)
						&& mc.level.getBlockState(below).isFaceSturdy(mc.level, below, Direction.UP)
						&& Teleport.clear(player, from, at)) {
					return target.immutable();
				}
			}
		}
		return null;
	}

	private void act(LocalPlayer player, Direction direction, Vec3 target) {
		if (actionWait > 0 || acted) {
			return;
		}
		if (legit.get()) {
			if (escape.is("Pearl")) {
				int slot = Place.hotbarSlot(player, Place.item("ender_pearl"));
				Vec3 at = slot < 0 || player.getCooldowns().isOnCooldown(player.getInventory().getItem(slot)) ? null : pearlAim(player, direction);
				acted = at == null || nz.temple.client.util.LegitAim.useToward(player, slot, at, 45, 2)
						!= nz.temple.client.util.LegitAim.Step.WORKING;
			} else if (player.position().distanceTo(target) > 0.6) {
				nz.temple.client.util.LegitAim.walk(player, target, true);
			} else {
				nz.temple.client.util.Steering.stop();
				acted = true;
			}
			return;
		}
		if (escape.is("Noclip")) {
			Noclip noclip = TempleNzClient.modules().get(Noclip.class);
			if (noclip != null && !noclip.isEnabled()) {
				noclip.setEnabled(true);
				noclipByUs = true;
			}
			Vec3 delta = target.subtract(player.position());
			if (delta.lengthSqr() > 0.16) {
				player.setDeltaMovement(delta.normalize().scale(noclipSpeed.get()));
			} else {
				stopNoclip();
				acted = true;
			}
			return;
		}
		if (escape.is("Teleport")) {
			acted = Teleport.hop(player, player.position(), target, true);
		} else if (escape.is("Pearl")) {
			acted = throwPearl(player, direction);
		}
	}

	private static boolean throwPearl(LocalPlayer player, Direction opening) {
		int slot = Place.hotbarSlot(player, Place.item("ender_pearl"));
		if (slot < 0) {
			return false;
		}
		ItemStack pearl = player.getInventory().getItem(slot);
		if (player.getCooldowns().isOnCooldown(pearl)) {
			return false;
		}
		Vec3 at = pearlAim(player, opening);
		if (at == null) {
			return false;
		}
		Place.useFacing(player, slot, at);
		return true;
	}

	private static Vec3 pearlAim(LocalPlayer player, Direction opening) {
		Vec3 eye = player.getEyePosition();
		Vec3 forward = new Vec3(opening.getStepX(), 0, opening.getStepZ());
		float baseYaw = Targets.rotations(eye, eye.add(forward))[0];
		Vec3 motion = player.getDeltaMovement();
		Vec3 inherited = new Vec3(motion.x, player.onGround() ? 0 : motion.y, motion.z);
		Vec3 bestLook = null;
		int bestTicks = Integer.MAX_VALUE;
		for (int yawOffset = -12; yawOffset <= 12; yawOffset += 4) {
			for (int pitch = 4; pitch <= 44; pitch += 4) {
				Vec3 look = Vec3.directionFromRotation(pitch, baseYaw + yawOffset);
				ProjectileSim.Flight flight = ProjectileSim.simulate(eye.subtract(0, 0.1, 0), look.scale(1.5).add(inherited),
						ProjectileSim.Physics.THROWN, player, null, 80, false);
				Vec3 travel = flight.end().subtract(player.position());
				double through = travel.x * opening.getStepX() + travel.z * opening.getStepZ();
				if (flight.hitBlock() != null && through >= 2.0 && through <= 14 && flight.ticks() < bestTicks) {
					bestTicks = flight.ticks();
					bestLook = look;
				}
			}
		}
		return bestLook == null ? null : eye.add(bestLook.scale(20));
	}

	private void announce(State stage) {
		if (!notify.get() || TempleNzClient.notifications() == null) {
			return;
		}
		String detail = switch (stage) {
			case FORMING -> openings.size() + " exits remain";
			case LAST_OPENING -> openings.keySet().iterator().next().getName() + " is the last opening";
			case SEALED -> "No walking exit remains";
			default -> "";
		};
		TempleNzClient.notifications().info(stage == State.SEALED ? "Trap sealed" : "Trap warning", detail);
	}

	private void stopNoclip() {
		if (!noclipByUs || TempleNzClient.modules() == null) {
			return;
		}
		Noclip noclip = TempleNzClient.modules().get(Noclip.class);
		if (noclip != null) {
			noclip.setEnabled(false);
		}
		noclipByUs = false;
	}

	private void clear(boolean stopMovement) {
		if (stopMovement) {
			stopNoclip();
		}
		openings.clear();
		blocked = List.of();
		closedSides = 0;
		state = State.OPEN;
		trapOrigin = null;
		threatTicks = 0;
		actionWait = 0;
		acted = false;
	}

	private static boolean inShell(BlockPos feet, BlockPos changed) {
		if (changed.equals(feet) || changed.equals(feet.above()) || changed.equals(feet.above(2))) {
			return true;
		}
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (changed.equals(feet.relative(direction)) || changed.equals(feet.above().relative(direction))) {
				return true;
			}
		}
		return false;
	}

	private static int horizontalDistance(BlockPos a, BlockPos b) {
		return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getZ() - b.getZ()));
	}

	public void collect(List<WorldEsp.Box> out) {
		if (!isEnabled() || state == State.OPEN) {
			return;
		}
		for (BlockPos pos : blocked) {
			out.add(new WorldEsp.Box(new AABB(pos).inflate(0.003), withAlpha(blockedColor.get(), 225),
					withAlpha(blockedColor.get(), 45), 0.02f));
		}
		for (BlockPos pos : openings.values()) {
			out.add(new WorldEsp.Box(new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1),
					withAlpha(openingColor.get(), 240), withAlpha(openingColor.get(), 55), 0.025f));
		}
	}

	public void lines(List<WorldEsp.Line> out, net.minecraft.client.renderer.state.level.CameraRenderState camera, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || state != State.LAST_OPENING || mc.player == null || openings.isEmpty()) {
			return;
		}
		Vec3 from = mc.player.position().add(0, 0.1, 0);
		Vec3 to = Vec3.atBottomCenterOf(openings.values().iterator().next()).add(0, 0.1, 0);
		out.add(new WorldEsp.Line(from.x, from.y, from.z, to.x, to.y, to.z, withAlpha(openingColor.get(), 245), 2.5f));
	}

	private static int withAlpha(int color, int alpha) {
		return alpha << 24 | color & 0xFFFFFF;
	}
}
