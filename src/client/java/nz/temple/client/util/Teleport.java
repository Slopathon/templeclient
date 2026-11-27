package nz.temple.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class Teleport {
	public static final double MAX_PER_TICK = 19.5;
	private static final int FAR_PACKETS = 4;
	private static final double SPARE = 0.3;
	private static final double[] LIFTS = {1.25, 2.5, 4, 6};
	private static int lastHopTick = Integer.MIN_VALUE;
	private static LocalPlayer lastHopPlayer;
	private Teleport() {
	}
	public static boolean clear(LocalPlayer player, Vec3 from, Vec3 to) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return false;
		}
		AABB box = player.getBoundingBox().move(from.subtract(player.position())).deflate(0.02);
		Vec3 delta = to.subtract(from);
		int steps = Math.max(1, (int) Math.ceil(delta.length() / 0.4));
		for (int i = 1; i <= steps; i++) {
			Vec3 offset = delta.scale((double) i / steps);
			if (!mc.level.noCollision(player, box.move(offset))) {
				return false;
			}
		}
		return true;
	}

	public static boolean fits(LocalPlayer player, Vec3 pos) {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null && mc.level.noCollision(player, player.getBoundingBox().move(pos.subtract(player.position())).deflate(1e-4));
	}
	public static boolean leg(LocalPlayer player, Vec3 from, Vec3 to) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return false;
		}
		AABB box = player.getBoundingBox().move(from.subtract(player.position()));
		Vec3 delta = to.subtract(from);
		Vec3 moved = Entity.collideBoundingBox(player, delta, box, mc.level, List.of());
		return moved.distanceToSqr(delta) < 1e-6;
	}
	public static List<Vec3> route(LocalPlayer player, Vec3 from, Vec3 to) {
		if (!fits(player, to)) {
			return null;
		}
		if (leg(player, from, to)) {
			return List.of(to);
		}
		Vec3 across = new Vec3(to.x, from.y, to.z);
		if (fits(player, across) && leg(player, from, across) && leg(player, across, to)) {
			return List.of(across, to);
		}
		double top = Math.max(from.y, to.y);
		for (double lift : LIFTS) {
			Vec3 over = new Vec3(to.x, top + lift, to.z);
			if (fits(player, over) && leg(player, from, over) && leg(player, over, to)) {
				return List.of(over, to);
			}
		}
		return null;
	}
	private static double allowance(int k) {
		int n = k <= FAR_PACKETS ? k : 1;
		return Math.sqrt(100.0 * n) - SPARE;
	}
	private static int padding(Vec3 origin, List<Vec3> path, int before, int maxPad) {
		for (int pad = 0; pad <= maxPad; pad++) {
			boolean ok = true;
			for (int j = 0; j < path.size() && ok; j++) {
				ok = origin.distanceTo(path.get(j)) <= allowance(before + pad + j + 1);
			}
			if (ok) {
				return pad;
			}
		}
		return -1;
	}
	private record Plan(int pad, List<Vec3> out, List<Vec3> back) {
	}
	private static Plan plan(LocalPlayer player, Vec3 from, Vec3 to, boolean andBack, int extra) {
		List<Vec3> out = route(player, from, to);
		if (out == null) {
			return null;
		}
		List<Vec3> far = new java.util.ArrayList<>(out);
		for (int i = 0; i < extra; i++) {
			far.add(to);
		}
		int pad = padding(from, far, 0, FAR_PACKETS - far.size());
		if (pad < 0) {
			return null;
		}
		List<Vec3> back = List.of();
		if (andBack) {
			back = route(player, to, from);
			if (back == null || padding(from, back, pad + far.size(), 0) != 0) {
				return null;
			}
		}
		return new Plan(pad, out, back);
	}
	public static boolean reachable(LocalPlayer player, Vec3 from, Vec3 to, boolean andBack) {
		return reachable(player, from, to, andBack, 0);
	}

	public static boolean reachable(LocalPlayer player, Vec3 from, Vec3 to, boolean andBack, int extra) {
		return from.distanceTo(to) <= MAX_PER_TICK && plan(player, from, to, andBack, extra) != null;
	}
	public static boolean ready(LocalPlayer player) {
		return player != lastHopPlayer || player.tickCount - lastHopTick >= 2 || player.tickCount < lastHopTick;
	}
	public static boolean hop(LocalPlayer player, Vec3 from, Vec3 to, boolean follow) {
		return hop(player, from, to, follow, Float.NaN, 0);
	}
	public static boolean hop(LocalPlayer player, Vec3 from, Vec3 to, boolean follow, float yaw, float pitch) {
		return hop(player, from, to, follow, yaw, pitch, 0);
	}
	public static boolean hop(LocalPlayer player, Vec3 from, Vec3 to, boolean follow, float yaw, float pitch, int extra) {
		if (from.distanceTo(to) > MAX_PER_TICK || !ready(player)) {
			return false;
		}
		Plan plan = plan(player, from, to, !follow, extra);
		if (plan == null) {
			return false;
		}
		for (int i = 0; i < plan.pad(); i++) {
			player.connection.send(new ServerboundMovePlayerPacket.Pos(from, player.onGround(), false));
		}
		for (int i = 0; i < plan.out().size(); i++) {
			Vec3 at = plan.out().get(i);
			boolean last = i == plan.out().size() - 1;
			if (last && !Float.isNaN(yaw)) {
				player.connection.send(new ServerboundMovePlayerPacket.PosRot(at, yaw, pitch, true, false));
			} else {
				player.connection.send(new ServerboundMovePlayerPacket.Pos(at, true, false));
			}
		}
		lastHopTick = player.tickCount;
		lastHopPlayer = player;
		if (follow) {
			player.setPos(to);
			player.setDeltaMovement(Vec3.ZERO);
		} else {
			pendingBack = plan.back();
		}
		return true;
	}
	private static List<Vec3> pendingBack = List.of();
	public static Vec3 step(LocalPlayer player, Vec3 goal) {
		Vec3 from = player.position();
		Vec3 delta = goal.subtract(from);
		double length = delta.length();
		if (length < 0.05) {
			return from;
		}
		if (!ready(player)) {
			return from;
		}
		Vec3 to = length <= MAX_PER_TICK ? goal : from.add(delta.scale(MAX_PER_TICK / length));
		if (hop(player, from, to, true)) {
			return to;
		}
		for (double up = Math.min(MAX_PER_TICK, 12); up >= 2; up -= 2.5) {
			Vec3 lift = from.add(0, up, 0);
			if (fits(player, lift) && hop(player, from, lift, true)) {
				return lift;
			}
		}
		for (double part = 0.75; part > 0.1; part -= 0.2) {
			Vec3 shorter = from.add(to.subtract(from).scale(part));
			if (hop(player, from, shorter, true)) {
				return shorter;
			}
		}
		return null;
	}
	public static int ticksFor(double distance) {
		return (int) Math.ceil(distance / MAX_PER_TICK) * 2;
	}
	public static void back(LocalPlayer player) {
		List<Vec3> path = pendingBack;
		pendingBack = List.of();
		if (path.isEmpty()) {
			path = List.of(player.position());
		}
		for (int i = 0; i < path.size(); i++) {
			boolean last = i == path.size() - 1;
			player.connection.send(new ServerboundMovePlayerPacket.Pos(last ? player.position() : path.get(i),
					last ? player.onGround() : true, false));
		}
	}
	public static Vec3 spotNear(LocalPlayer player, Vec3 center, double radius, Vec3 prefer) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 best = null;
		double bestScore = Double.MAX_VALUE;
		for (int i = 0; i < 16; i++) {
			double angle = Math.PI * 2 * i / 16;
			for (double r = Math.max(0.6, radius * 0.5); r <= radius; r += 0.5) {
				for (int dy = -1; dy <= 1; dy++) {
					Vec3 spot = new Vec3(center.x + Math.cos(angle) * r, Math.floor(center.y) + dy, center.z + Math.sin(angle) * r);
					if (!fits(player, spot)) {
						continue;
					}
					double score = spot.distanceToSqr(prefer) + (grounded(mc, spot) ? 0 : 6) + Math.abs(dy) * 2;
					if (score < bestScore) {
						bestScore = score;
						best = spot;
					}
				}
			}
		}
		return best;
	}
	public static boolean grounded(Minecraft mc, Vec3 feet) {
		BlockPos below = BlockPos.containing(feet.x, feet.y - 0.5, feet.z);
		return !mc.level.getBlockState(below).getCollisionShape(mc.level, below).isEmpty();
	}
}
