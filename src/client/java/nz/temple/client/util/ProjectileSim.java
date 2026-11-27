package nz.temple.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ProjectileSim {
	private ProjectileSim() {
	}

	public record Physics(double gravity, double drag, double waterDrag) {
		public static final Physics ARROW = new Physics(0.05, 0.99, 0.6);
		public static final Physics TRIDENT = new Physics(0.05, 0.99, 0.99);
		public static final Physics THROWN = new Physics(0.03, 0.99, 0.8);
		public static final Physics POTION = new Physics(0.05, 0.99, 0.8);
	}

	public record Flight(List<Vec3> points, Vec3 end, Entity hitEntity, BlockPos hitBlock, int ticks) {
	}

	public record LaunchSolution(Vec3 direction, Flight flight, double error) {
	}

	public static Physics physicsFor(String id) {
		if (id.contains("arrow")) {
			return Physics.ARROW;
		}
		if (id.contains("trident")) {
			return Physics.TRIDENT;
		}
		if (id.contains("potion") || id.contains("experience_bottle")) {
			return Physics.POTION;
		}
		if (id.contains("pearl") || id.contains("snowball") || id.contains("egg")) {
			return Physics.THROWN;
		}
		return null;
	}

	public static Flight predict(Entity entity, boolean stopAtEntities) {
		if (!(entity instanceof Projectile projectile)) {
			return null;
		}
		Physics physics = physicsFor(EntityType.getKey(entity.getType()).getPath());
		if (physics == null) {
			return null;
		}
		return simulate(entity.position(), entity.getDeltaMovement(), physics, projectile.getOwner(), entity, 200, stopAtEntities);
	}

	public static Flight simulate(Vec3 pos, Vec3 vel, Physics physics, Entity owner, Entity self, int maxTicks, boolean stopAtEntities) {
		Minecraft mc = Minecraft.getInstance();
		List<Vec3> points = new ArrayList<>();
		points.add(pos);
		for (int tick = 1; tick <= maxTicks; tick++) {
			boolean water = !mc.level.getFluidState(BlockPos.containing(pos)).isEmpty();
			vel = vel.subtract(0, physics.gravity(), 0).scale(water ? physics.waterDrag() : physics.drag());
			Vec3 next = pos.add(vel);
			BlockHitResult block = mc.level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
					owner != null ? owner : mc.player));
			Vec3 reach = block.getType() == HitResult.Type.MISS ? next : block.getLocation();
			if (stopAtEntities) {
				Entity hit = firstEntity(mc, pos, reach, owner, self, tick);
				if (hit != null) {
					Vec3 end = hit.getBoundingBox().inflate(0.3).clip(pos, reach).orElse(reach);
					points.add(end);
					return new Flight(points, end, hit, null, tick);
				}
			}
			if (block.getType() != HitResult.Type.MISS) {
				points.add(block.getLocation());
				return new Flight(points, block.getLocation(), null, block.getBlockPos(), tick);
			}
			pos = next;
			points.add(pos);
			if (pos.y < mc.level.getMinY() - 16) {
				break;
			}
		}
		return new Flight(points, pos, null, null, maxTicks);
	}

	public static LaunchSolution quickestLaunch(Vec3 start, Vec3 inherited, double speed, Physics physics, Entity owner,
			Vec3 target, BlockPos targetBlock, int maxTicks, double tolerance) {
		LaunchSolution best = null;
		double drag = physics.drag();
		for (int ticks = 2; ticks <= maxTicks; ticks++) {
			if (best != null && ticks > best.flight().ticks() + 2) {
				break;
			}
			double dragPower = Math.pow(drag, ticks);
			double sum = drag * (1 - dragPower) / (1 - drag);
			Vec3 delta = target.subtract(start);
			double vx = delta.x / sum;
			double vz = delta.z / sum;
			double vy = (delta.y + drag * physics.gravity() * (ticks - sum) / (1 - drag)) / sum;
			Vec3 launch = new Vec3(vx, vy, vz).subtract(inherited);
			if (launch.lengthSqr() < 1e-6 || Math.abs(launch.length() - speed) > 0.22) {
				continue;
			}
			Vec3 seed = launch.normalize();
			float[] angle = Targets.rotations(Vec3.ZERO, seed);
			for (int yawOffset = -2; yawOffset <= 2; yawOffset += 2) {
				for (int pitchOffset = -2; pitchOffset <= 2; pitchOffset += 2) {
					Vec3 direction = Vec3.directionFromRotation(angle[1] + pitchOffset, angle[0] + yawOffset);
					Flight flight = simulate(start, direction.scale(speed).add(inherited), physics, owner, null,
							Math.min(maxTicks, ticks + 4), true);
					if (flight.hitBlock() == null || !flight.hitBlock().equals(targetBlock)) {
						continue;
					}
					double error = flight.end().distanceTo(target);
					if (error <= tolerance && (best == null || flight.ticks() < best.flight().ticks()
							|| flight.ticks() == best.flight().ticks() && error < best.error())) {
						best = new LaunchSolution(direction, flight, error);
					}
				}
			}
		}
		return best;
	}

	private static Entity firstEntity(Minecraft mc, Vec3 from, Vec3 to, Entity owner, Entity self, int tick) {
		AABB sweep = new AABB(from, to).inflate(0.5);
		Entity best = null;
		double bestSq = Double.MAX_VALUE;
		for (Entity e : mc.level.getEntities(self, sweep, e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator())) {
			if (e == owner && tick < 5) {
				continue;
			}
			Optional<Vec3> hit = e.getBoundingBox().inflate(0.3).clip(from, to);
			if (hit.isPresent()) {
				double d = from.distanceToSqr(hit.get());
				if (d < bestSq) {
					bestSq = d;
					best = e;
				}
			}
		}
		return best;
	}
}
