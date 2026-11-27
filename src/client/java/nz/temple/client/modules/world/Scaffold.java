package nz.temple.client.modules.world;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.AimSmoother;
import nz.temple.client.util.Place;
import nz.temple.client.util.Rotations;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class Scaffold extends Module {
	private static final int ROTATION_PRIORITY = 150;
	private static volatile Scaffold instance;
	private final BoolSetting legit;
	private final ModeSetting mode;
	private final NumberSetting turnSpeed;
	private final BoolSetting safety;
	private final BoolSetting tower;
	private final BoolSetting sprint;
	private final NumberSetting extend;
	private final BoolSetting switchBack;

	private int layer = Integer.MIN_VALUE;
	private Vec3 bridgeDir;
	private float aimYaw;
	private float aimPitch;
	private boolean aiming;
	private boolean lastAimOnFace;
	private boolean active;
	private boolean eagle;
	private float userYaw;
	private float userPitch;
	private boolean turned;
	private int returning;
	private int idle;
	private int previousSlot = -1;
	public Scaffold() {
		super("Scaffold", Category.WORLD, "Places blocks under you as you walk, using god bridge, speed bridge or a blatant mode.");
		mode = add(new ModeSetting("Mode", List.of("God bridge", "Speed bridge", "Blatant"), "God bridge")
				.describe("God bridge shows the server you walking backwards off the edge while looking back at the block, placing as soon "
						+ "as it comes into view, without sneaking. Speed bridge does the same but sneaks briefly at each edge until the "
						+ "block is placed, which is the safest. Blatant places under and ahead of you with instant rotations while "
						+ "sprinting, with fast towering."));
		legit = add(new BoolSetting("Legit", false)
				.describe("Turns your real view to bridge instead of sending a different rotation to the server, and only places where your "
						+ "crosshair is. Blatant uses God bridge in this mode."));
		turnSpeed = add(new NumberSetting("Turn speed", 70, 20, 180, 5, "°")
				.describe("View turn speed in degrees per tick. Lower looks smoother; higher keeps up better when you change direction."));
		safety = add(new BoolSetting("Safety sneak", true)
				.describe("In God bridge, sneaks briefly if a block is late (lag or a sharp turn) instead of walking off the edge.")
				.visibleWhen(() -> mode.is("God bridge")));
		sprint = add(new BoolSetting("Sprint", true).describe("Keeps sprinting while bridging. Blatant only, since the other modes walk "
				+ "backwards.").visibleWhen(() -> mode.is("Blatant") && !legit.get()));
		extend = add(new NumberSetting("Extend", 0, 0, 4, 1, " b").describe("Also places this far ahead of you.")
				.visibleWhen(() -> mode.is("Blatant") && !legit.get()));
		tower = add(new BoolSetting("Tower", true)
				.describe("Hold jump while standing still to pillar up, placing a block under you at the top of each jump."));
		switchBack = add(new BoolSetting("Switch back", true)
				.describe("Switches back to your previous item when you stop bridging."));
		on(TickEvent.class, e -> tick());
		instance = this;
	}
	private boolean blatant() {
		return mode.is("Blatant") && !legit.get();
	}
	private boolean silent() {
		return !legit.get();
	}
	@Override
	protected void onDisable() {
		stop(Minecraft.getInstance().player);
		active = false;
		eagle = false;
		aiming = false;
		layer = Integer.MIN_VALUE;
	}

	public static boolean blocksSprint(LocalPlayer player) {
		Scaffold s = instance;
		return s != null && s.isEnabled() && s.active && !(s.blatant() && s.sprint.get()) && player == Minecraft.getInstance().player;
	}
	public static Vec2 input(LocalPlayer player, ClientInput input) {
		Scaffold s = instance;
		if (s == null || !s.isEnabled() || !s.active || player != Minecraft.getInstance().player) {
			return null;
		}
		Input raw = input.keyPresses;
		boolean noSprint = !(s.blatant() && s.sprint.get());
		if (noSprint && player.isSprinting()) {
			player.setSprinting(false);
		}
		if (s.blatant()) {
			if (noSprint) {
				input.keyPresses = new Input(raw.forward(), raw.backward(), raw.left(), raw.right(), raw.jump(), raw.shift(), false);
			}
			return null;
		}
		Vec3 wish = s.rawWish(raw, s.legit.get() && s.turned ? s.userYaw : player.getYRot());
		if (s.bridgeDir != null && wish.lengthSqr() > 1e-6 && wish.dot(s.bridgeDir) > Math.cos(Math.toRadians(30))) {
			wish = s.bridgeDir;
		}
		float ref = s.silent() && s.aiming ? s.aimYaw : player.getYRot();
		float r = ref * Mth.DEG_TO_RAD;
		double forward = wish.x * -Mth.sin(r) + wish.z * Mth.cos(r);
		double left = wish.x * Mth.cos(r) + wish.z * Mth.sin(r);
		boolean f = forward > 0.38;
		boolean b = forward < -0.38;
		boolean l = left > 0.38;
		boolean rt = left < -0.38;
		input.keyPresses = new Input(f, b, l, rt, raw.jump(), raw.shift() || s.eagle, false);
		float fi = (f ? 1 : 0) - (b ? 1 : 0);
		float li = (l ? 1 : 0) - (rt ? 1 : 0);
		Vec2 keys = new Vec2(li, fi).normalized();
		double wx = keys.y * -Mth.sin(r) + keys.x * Mth.cos(r);
		double wz = keys.y * Mth.cos(r) + keys.x * Mth.sin(r);
		float c = player.getYRot() * Mth.DEG_TO_RAD;
		float mf = (float) (wx * -Mth.sin(c) + wz * Mth.cos(c));
		float ml = (float) (wx * Mth.cos(c) + wz * Mth.sin(c));
		return new Vec2(ml, mf);
	}
	private Vec3 rawWish(Input k, float yaw) {
		double forward = (k.forward() ? 1 : 0) - (k.backward() ? 1 : 0);
		double strafe = (k.left() ? 1 : 0) - (k.right() ? 1 : 0);
		if (forward == 0 && strafe == 0) {
			return Vec3.ZERO;
		}
		float r = yaw * Mth.DEG_TO_RAD;
		Vec3 dir = new Vec3(forward * -Mth.sin(r) + strafe * Mth.cos(r), 0, forward * Mth.cos(r) + strafe * Mth.sin(r));
		return dir.normalize();
	}
	private static Input heldKeys(Minecraft mc) {
		return new Input(mc.options.keyUp.isDown(), mc.options.keyDown.isDown(), mc.options.keyLeft.isDown(), mc.options.keyRight.isDown(),
				mc.options.keyJump.isDown(), mc.options.keyShift.isDown(), mc.options.keySprint.isDown());
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.gameMode == null) {
			return;
		}
		if (mc.gui.screen() != null || player.getAbilities().flying || Place.blockHotbarSlot(player) < 0) {
			stop(player);
			return;
		}
		if (blatant()) {
			blatantTick(mc, player);
		} else {
			bridgeTick(mc, player);
		}
	}
	private void stop(LocalPlayer player) {
		active = false;
		eagle = false;
		if (player == null) {
			aiming = false;
			turned = false;
			return;
		}
		if (aiming) {
			float remaining = Rotations.angle(aimYaw, aimPitch, player.getYRot(), player.getXRot());
			if (remaining > turnSpeed.getFloat()) {
				turnToward(player.getYRot(), player.getXRot(), false);
			} else {
				aiming = false;
			}
		}
		if (turned) {
			turned = false;
			returning = 16;
		}
		if (returning > 0) {
			returning--;
			Vec3 look = player.getEyePosition().add(Vec3.directionFromRotation(userPitch, userYaw).scale(6));
			AimSmoother.look(player, look, turnSpeed.getFloat(), 0.3f);
			if (AimSmoother.offBy(player, look) < 1.5f) {
				returning = 0;
			}
		}
		if (switchBack.get() && previousSlot >= 0 && !aiming) {
			player.getInventory().setSelectedSlot(previousSlot);
			previousSlot = -1;
		}
	}
	private void bridgeTick(Minecraft mc, LocalPlayer player) {
		boolean silent = silent();
		Input held = heldKeys(mc);
		if (player.onGround()) {
			layer = Mth.floor(player.getY() + 1e-3) - 1;
		} else if (layer == Integer.MIN_VALUE) {
			layer = Mth.floor(player.getY() + 1e-3) - 1;
		}
		Vec3 wish = rawWish(held, legit.get() && turned ? userYaw : player.getYRot());
		boolean towering = tower.get() && held.jump() && wish.lengthSqr() < 1e-6;
		place(mc, player, silent, towering);

		Vec3 move = new Vec3(player.getX() - player.xo, 0, player.getZ() - player.zo);
		if (wish.lengthSqr() > 1e-6) {
			bridgeDir = snap(wish);
		} else if (move.lengthSqr() > 0.004) {
			bridgeDir = snap(move.normalize());
		}
		List<BlockPos> needed = towering ? towerCells(player) : neededCells(mc, player, move);
		if (wish.lengthSqr() < 1e-6 && needed.isEmpty() && !towering) {
			if (++idle > 8) {
				stop(player);
			} else if (aiming && silent) {
				Rotations.request(aimYaw, aimPitch, ROTATION_PRIORITY);
			}
			return;
		}
		idle = 0;
		if (!active) {
			active = true;
			if (legit.get() && !turned) {
				userYaw = player.getYRot();
				userPitch = player.getXRot();
				turned = true;
			}
			if (!aiming) {
				aimYaw = Rotations.serverYaw(player);
				aimPitch = Rotations.serverPitch(player);
			}
		}
		returning = 0;
		float[] want = towering ? new float[] {aiming ? aimYaw : player.getYRot(), 89.5f} : aimAt(mc, player, needed, move);
		boolean onFace = towering || lastAimOnFace;
		float fromYaw = silent ? (aiming ? aimYaw : player.getYRot()) : player.getYRot();
		float fromPitch = silent ? (aiming ? aimPitch : player.getXRot()) : player.getXRot();
		boolean inReach = want != null && Rotations.angle(fromYaw, fromPitch, want[0], want[1]) <= (silent ? turnSpeed.getFloat() : 2f);
		Vec3 ahead = player.position().add(move.scale(2));
		boolean overVoid = !supported(mc, player, player.position().add(move.scale(1.3)));
		boolean edge = Place.replaceable(mc.level, BlockPos.containing(ahead.x, layer, ahead.z));
		eagle = player.onGround() && !towering
				&& (mode.is("Speed bridge") ? edge : safety.get() && overVoid && !(onFace && inReach));
		if (want == null) {
			return;
		}
		if (silent) {
			turnToward(want[0], want[1], onFace);
		} else {
			Vec3 at = player.getEyePosition().add(Vec3.directionFromRotation(want[1], want[0]).scale(4));
			AimSmoother.look(player, at, turnSpeed.getFloat(), 0.25f);
		}
	}
	private void turnToward(float yaw, float pitch, boolean now) {
		float[] target = {aimYaw + Mth.wrapDegrees(yaw - aimYaw), Mth.clamp(pitch, -90, 90)};
		float remaining = Rotations.angle(aimYaw, aimPitch, target[0], target[1]);
		float step = Math.min(turnSpeed.getFloat(), now ? remaining : Math.max(3f, remaining * 0.7f + 2f));
		float[] next = Rotations.step(aimYaw, aimPitch, target[0], target[1], step);
		ThreadLocalRandom r = ThreadLocalRandom.current();
		float gcd = mouseStep();
		float jitter = remaining > 1 && !now ? 1 : 0.25f;
		float dy = next[0] - aimYaw + (float) r.nextGaussian() * 0.12f * jitter;
		float dp = next[1] - aimPitch + (float) r.nextGaussian() * 0.08f * jitter;
		dy = Math.round(dy / gcd) * gcd;
		dp = Math.round(dp / gcd) * gcd;
		aimYaw += dy;
		aimPitch = Mth.clamp(aimPitch + dp, -90, 90);
		aiming = true;
		Rotations.request(aimYaw, aimPitch, ROTATION_PRIORITY);
	}
	private static float mouseStep() {
		double sens = Minecraft.getInstance().options.sensitivity().get();
		double f = sens * 0.6 + 0.2;
		return (float) Math.max(1e-4, f * f * f * 8 * 0.15);
	}
	private static Vec3 snap(Vec3 dir) {
		double a = Math.atan2(dir.z, dir.x);
		double s = Math.round(a / (Math.PI / 4)) * (Math.PI / 4);
		return new Vec3(Math.cos(s), 0, Math.sin(s));
	}
	private boolean supported(Minecraft mc, LocalPlayer player, Vec3 at) {
		double half = player.getBbWidth() / 2 - 0.02;
		for (int x = Mth.floor(at.x - half); x <= Mth.floor(at.x + half); x++) {
			for (int z = Mth.floor(at.z - half); z <= Mth.floor(at.z + half); z++) {
				if (Place.solid(mc.level, new BlockPos(x, layer, z))) {
					return true;
				}
			}
		}
		return false;
	}
	private List<BlockPos> neededCells(Minecraft mc, LocalPlayer player, Vec3 move) {
		List<BlockPos> out = new ArrayList<>();
		double half = player.getBbWidth() / 2;
		for (int step = 0; step <= 3; step++) {
			Vec3 at = player.position().add(move.scale(step));
			for (int x = Mth.floor(at.x - half); x <= Mth.floor(at.x + half); x++) {
				for (int z = Mth.floor(at.z - half); z <= Mth.floor(at.z + half); z++) {
					BlockPos cell = new BlockPos(x, layer, z);
					if (!out.contains(cell) && Place.replaceable(mc.level, cell) && hasSupport(mc, cell)) {
						out.add(cell);
					}
				}
			}
		}
		Vec3 p = player.position().add(move);
		out.sort((a, b) -> Double.compare(flatDist(a, p), flatDist(b, p)));
		return out;
	}
	private List<BlockPos> towerCells(LocalPlayer player) {
		BlockPos cell = BlockPos.containing(player.getX(), Math.floor(player.getY() + 1e-3) - 1, player.getZ());
		Minecraft mc = Minecraft.getInstance();
		if (!player.onGround() && Place.replaceable(mc.level, cell) && player.getY() >= cell.getY() + 1 && hasSupport(mc, cell)) {
			return List.of(cell);
		}
		return List.of();
	}
	private static double flatDist(BlockPos cell, Vec3 p) {
		double dx = cell.getX() + 0.5 - p.x;
		double dz = cell.getZ() + 0.5 - p.z;
		return dx * dx + dz * dz;
	}
	private static boolean hasSupport(Minecraft mc, BlockPos cell) {
		for (Direction d : Direction.values()) {
			if (d != Direction.UP && Place.solid(mc.level, cell.relative(d))) {
				return true;
			}
		}
		return false;
	}
	private float[] aimAt(Minecraft mc, LocalPlayer player, List<BlockPos> needed, Vec3 move) {
		lastAimOnFace = false;
		Vec3 eyeNext = player.getEyePosition().add(move);
		float fromYaw = aiming ? aimYaw : player.getYRot();
		float fromPitch = aiming ? aimPitch : player.getXRot();
		float[] best = null;
		float bestCost = Float.MAX_VALUE;
		for (BlockPos cell : needed) {
			for (Direction face : Direction.values()) {
				if (face == Direction.DOWN) {
					continue;
				}
				BlockPos against = cell.relative(face.getOpposite());
				if (!Place.solid(mc.level, against)) {
					continue;
				}
				Vec3 point = facePoint(against, face, eyeNext);
				if (point == null) {
					continue;
				}
				float[] r = Rotations.toward(eyeNext, point, fromYaw);
				float cost = Rotations.angle(fromYaw, fromPitch, r[0], r[1]) + (float) flatDist(cell, eyeNext) * 20;
				if (cost < bestCost) {
					bestCost = cost;
					best = r;
				}
			}
			if (best != null) {
				lastAimOnFace = true;
				return best;
			}
		}
		if (bridgeDir == null) {
			return null;
		}
		BlockPos under = BlockPos.containing(player.getX(), layer, player.getZ());
		Direction face = Direction.getApproximateNearest(bridgeDir.x, 0, bridgeDir.z);
		if (!Place.solid(mc.level, under)) {
			under = under.relative(face.getOpposite());
		}
		Vec3 faceCentre = Vec3.atCenterOf(under).add(face.getStepX() * 0.5, 0, face.getStepZ() * 0.5);
		double lateralX = face.getAxis() == Direction.Axis.X ? faceCentre.x : Mth.clamp(player.getX(), under.getX() + 0.15, under.getX() + 0.85);
		double lateralZ = face.getAxis() == Direction.Axis.Z ? faceCentre.z : Mth.clamp(player.getZ(), under.getZ() + 0.15, under.getZ() + 0.85);
		Vec3 eye = new Vec3(lateralX + face.getStepX() * 0.14, player.getEyeY(), lateralZ + face.getStepZ() * 0.14);
		Vec3 point = new Vec3(lateralX, under.getY() + 0.72, lateralZ);
		return Rotations.toward(eye, point, fromYaw);
	}
	private static Vec3 facePoint(BlockPos block, Direction face, Vec3 eye) {
		Vec3 centre = Vec3.atCenterOf(block).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
		double outside = (eye.x - centre.x) * face.getStepX() + (eye.y - centre.y) * face.getStepY() + (eye.z - centre.z) * face.getStepZ();
		if (outside < 0.02) {
			return null;
		}
		double x = face.getAxis() == Direction.Axis.X ? centre.x : Mth.clamp(eye.x, block.getX() + 0.12, block.getX() + 0.88);
		double z = face.getAxis() == Direction.Axis.Z ? centre.z : Mth.clamp(eye.z, block.getZ() + 0.12, block.getZ() + 0.88);
		double y = face.getAxis() == Direction.Axis.Y ? centre.y : block.getY() + 0.72;
		return new Vec3(x, y, z);
	}
	private void place(Minecraft mc, LocalPlayer player, boolean silent, boolean towering) {
		float yaw = silent ? Rotations.serverYaw(player) : player.getYRot();
		float pitch = silent ? Rotations.serverPitch(player) : player.getXRot();
		if (silent && !aiming) {
			return;
		}
		double reach = player.blockInteractionRange();
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(Vec3.directionFromRotation(pitch, yaw).scale(reach));
		BlockHitResult hit = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK || Place.replaceable(mc.level, hit.getBlockPos())) {
			return;
		}
		BlockPos at = hit.getBlockPos().relative(hit.getDirection());
		if (!Place.replaceable(mc.level, at) || at.getY() != layer && !towering || at.getY() + 1 > player.getY() + 1e-3
				|| flatDist(at, player.position()) > 2.6 || new AABB(at).intersects(player.getBoundingBox())) {
			return;
		}
		int slot = Place.blockHotbarSlot(player);
		if (slot < 0) {
			return;
		}
		int current = player.getInventory().getSelectedSlot();
		if (current != slot) {
			if (previousSlot < 0) {
				previousSlot = current;
			}
			player.getInventory().setSelectedSlot(slot);
		}
		if (mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction()) {
			nz.temple.client.compat.Players.swing(player, InteractionHand.MAIN_HAND);
			if (towering) {
				layer = at.getY();
			}
		}
	}
	private void blatantTick(Minecraft mc, LocalPlayer player) {
		Input held = heldKeys(mc);
		Vec3 wish = rawWish(held, player.getYRot());
		boolean towering = tower.get() && held.jump() && wish.lengthSqr() < 1e-6;
		List<BlockPos> targets = new ArrayList<>();
		targets.add(player.blockPosition().below());
		Vec3 move = new Vec3(player.getX() - player.xo, 0, player.getZ() - player.zo);
		for (int step = 1; step <= 2 && !towering; step++) {
			BlockPos ahead = BlockPos.containing(player.getX() + move.x * step, player.getY() - 1, player.getZ() + move.z * step);
			if (!targets.contains(ahead)) {
				targets.add(ahead);
			}
		}
		if (extend.get() >= 1 && wish.lengthSqr() > 1e-6) {
			for (int i = 1; i <= (int) Math.round(extend.get()); i++) {
				targets.add(BlockPos.containing(player.getX() + wish.x * i, player.getY() - 1, player.getZ() + wish.z * i));
			}
		}
		boolean any = wish.lengthSqr() > 1e-6 || towering;
		for (BlockPos target : targets) {
			if (!Place.replaceable(mc.level, target)) {
				continue;
			}
			any = true;
			Vec3 point = blatantPoint(mc, target);
			if (point != null) {
				float[] r = Rotations.toward(player.getEyePosition(), point, aiming ? aimYaw : player.getYRot());
				aimYaw = r[0];
				aimPitch = r[1];
				aiming = true;
				Rotations.request(aimYaw, aimPitch, ROTATION_PRIORITY);
			}
			if (Place.placeWith(player, target, Place::buildable, true, false)) {
				if (towering && target.equals(player.blockPosition().below())) {
					player.setDeltaMovement(player.getDeltaMovement().x, 0.42, player.getDeltaMovement().z);
				}
				break;
			}
		}
		if (any) {
			active = true;
			idle = 0;
		} else if (++idle > 8) {
			stop(player);
		} else if (aiming) {
			Rotations.request(aimYaw, aimPitch, ROTATION_PRIORITY);
		}
	}
	private static Vec3 blatantPoint(Minecraft mc, BlockPos target) {
		for (Direction face : Direction.values()) {
			BlockPos against = target.relative(face.getOpposite());
			if (Place.solid(mc.level, against)) {
				return Vec3.atCenterOf(against).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
			}
		}
		return null;
	}
}
