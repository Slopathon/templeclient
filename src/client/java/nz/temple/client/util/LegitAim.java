package nz.temple.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class LegitAim {
	private static final double[][] FACE_POINTS = {{0, 0}, {0.3, 0.3}, {-0.3, 0.3}, {0.3, -0.3}, {-0.3, -0.3}};
	private LegitAim() {
	}

	public static Vec3 visiblePoint(LocalPlayer player, BlockPos pos, Direction face, double reach) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 eye = player.getEyePosition();
		Vec3 center = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
		Direction.Axis[] inFace = switch (face.getAxis()) {
			case X -> new Direction.Axis[] {Direction.Axis.Y, Direction.Axis.Z};
			case Y -> new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z};
			case Z -> new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Y};
		};
		for (double[] o : FACE_POINTS) {
			Vec3 point = center.add(along(inFace[0], o[0])).add(along(inFace[1], o[1]));
			if (eye.distanceTo(point) > reach - 0.05) {
				continue;
			}
			BlockHitResult hit = mc.level.clip(new ClipContext(eye, point.add(point.subtract(eye).normalize().scale(0.05)),
					ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
			if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos) && hit.getDirection() == face) {
				return point;
			}
		}
		return null;
	}
	public static Vec3 visiblePoint(LocalPlayer player, BlockPos pos, double reach) {
		Vec3 best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Direction face : Direction.values()) {
			Vec3 point = visiblePoint(player, pos, face, reach);
			if (point != null && point.distanceToSqr(player.getEyePosition()) < bestDistance) {
				best = point;
				bestDistance = point.distanceToSqr(player.getEyePosition());
			}
		}
		return best;
	}
	public static BlockHitResult blockUnderCrosshair(BlockPos pos, Direction face) {
		HitResult hit = Minecraft.getInstance().hitResult;
		if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK && block.getBlockPos().equals(pos)
				&& (face == null || block.getDirection() == face)) {
			return block;
		}
		return null;
	}
	public static boolean onEntity(Entity entity) {
		return Minecraft.getInstance().hitResult instanceof EntityHitResult hit && hit.getEntity() == entity;
	}
	public static boolean select(LocalPlayer player, int slot) {
		if (slot < 0 || player.getInventory().getSelectedSlot() == slot) {
			return false;
		}
		player.getInventory().setSelectedSlot(slot);
		return true;
	}
	public enum Step {
		DONE,
		WORKING,
		CANNOT
	}
	public static final float SPEED = 32f;

	public static boolean look(LocalPlayer player, Vec3 at, float speed, float tolerance) {
		AimSmoother.look(player, at, speed, 0.4f);
		return AimSmoother.offBy(player, at) <= tolerance;
	}
	public static Step mine(LocalPlayer player, BlockPos pos, float speed) {
		Minecraft mc = Minecraft.getInstance();
		var state = mc.level.getBlockState(pos);
		if (state.isAir() || !state.getFluidState().isEmpty() && state.getCollisionShape(mc.level, pos).isEmpty()) {
			return Step.DONE;
		}
		if (state.getDestroySpeed(mc.level, pos) < 0) {
			return Step.CANNOT;
		}
		Vec3 point = visiblePoint(player, pos, player.blockInteractionRange());
		if (point == null) {
			return Step.CANNOT;
		}
		int tool = bestTool(player, state);
		if (tool >= 0 && select(player, tool)) {
			return Step.WORKING;
		}
		AimSmoother.look(player, point, speed, 0.4f);
		BlockHitResult hit = blockUnderCrosshair(pos, null);
		if (hit != null) {
			Mining.dig(mc, pos, hit.getDirection());
			nz.temple.client.compat.Players.swing(player, net.minecraft.world.InteractionHand.MAIN_HAND);
		}
		return Step.WORKING;
	}
	public static int bestTool(LocalPlayer player, net.minecraft.world.level.block.state.BlockState state) {
		int best = -1;
		float bestSpeed = 1.0f;
		for (int i = 0; i < 9; i++) {
			float s = player.getInventory().getItem(i).getDestroySpeed(state);
			if (s > bestSpeed + 0.01f) {
				bestSpeed = s;
				best = i;
			}
		}
		return best;
	}
	public static Step place(LocalPlayer player, BlockPos target, java.util.function.Predicate<net.minecraft.world.item.ItemStack> item,
			float speed) {
		Minecraft mc = Minecraft.getInstance();
		if (!Place.replaceable(mc.level, target)) {
			return Step.DONE;
		}
		double reach = player.blockInteractionRange();
		for (Direction d : Direction.values()) {
			BlockPos neighbour = target.relative(d);
			Direction face = d.getOpposite();
			if (!Place.solid(mc.level, neighbour)) {
				continue;
			}
			Vec3 point = visiblePoint(player, neighbour, face, reach);
			if (point == null) {
				continue;
			}
			int slot = Place.hotbarSlot(player, item);
			if (slot < 0) {
				return Step.CANNOT;
			}
			if (select(player, slot)) {
				return Step.WORKING;
			}
			AimSmoother.look(player, point, speed, 0.4f);
			BlockHitResult hit = blockUnderCrosshair(neighbour, face);
			if (hit != null && mc.gameMode.useItemOn(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit).consumesAction()) {
				nz.temple.client.compat.Players.swing(player, net.minecraft.world.InteractionHand.MAIN_HAND);
				return Step.DONE;
			}
			return Step.WORKING;
		}
		return Step.CANNOT;
	}
	public static Step useToward(LocalPlayer player, int slot, Vec3 at, float speed, float tolerance) {
		Minecraft mc = Minecraft.getInstance();
		if (slot < 0) {
			return Step.CANNOT;
		}
		if (select(player, slot)) {
			return Step.WORKING;
		}
		if (at != null && !look(player, at, speed, tolerance)) {
			return Step.WORKING;
		}
		if (mc.gameMode.useItem(player, net.minecraft.world.InteractionHand.MAIN_HAND).consumesAction()) {
			nz.temple.client.compat.Players.swing(player, net.minecraft.world.InteractionHand.MAIN_HAND);
			return Step.DONE;
		}
		return Step.CANNOT;
	}
	public static Step hit(LocalPlayer player, Entity target, float speed, float charge) {
		Minecraft mc = Minecraft.getInstance();
		if (target == null || !target.isAlive()) {
			return Step.CANNOT;
		}
		double reach = player.entityInteractionRange() + 1.5;
		if (target.getBoundingBox().distanceToSqr(player.getEyePosition()) > reach * reach || !player.hasLineOfSight(target)) {
			return Step.CANNOT;
		}
		if (target instanceof net.minecraft.world.entity.LivingEntity living) {
			AimSmoother.aim(player, living, t -> nearestPoint(player, t), speed, 0.45f);
		} else {
			AimSmoother.look(player, target.getBoundingBox().getCenter(), speed, 0.45f);
		}
		if (onEntity(target) && player.getAttackStrengthScale(0.5f) >= charge) {
			mc.gameMode.attack(player, target);
			nz.temple.client.compat.Players.swing(player, net.minecraft.world.InteractionHand.MAIN_HAND);
			return Step.DONE;
		}
		return Step.WORKING;
	}
	public static Vec3 nearestPoint(LocalPlayer player, Entity target) {
		Vec3 eye = player.getEyePosition();
		var box = target.getBoundingBox().deflate(0.1);
		return new Vec3(net.minecraft.util.Mth.clamp(eye.x, box.minX, box.maxX), net.minecraft.util.Mth.clamp(eye.y, box.minY, box.maxY),
				net.minecraft.util.Mth.clamp(eye.z, box.minZ, box.maxZ));
	}
	public static Step pillar(LocalPlayer player, float speed) {
		Minecraft mc = Minecraft.getInstance();
		java.util.function.Predicate<net.minecraft.world.item.ItemStack> blocks = Place::buildable;
		if (Place.hotbarSlot(player, blocks) < 0) {
			return Step.CANNOT;
		}
		BlockPos feet = BlockPos.containing(player.getX(), player.getY() + 0.1, player.getZ());
		AimSmoother.look(player, Vec3.atCenterOf(feet.below()).add(0, 0.5, 0), speed, 0.4f);
		if (player.onGround()) {
			if (player.getXRot() > 70) {
				Keys.press(mc.options.keyJump, 1);
			}
			return Step.WORKING;
		}
		BlockPos under = BlockPos.containing(player.getX(), player.getY() - 0.5, player.getZ());
		if (player.getY() - under.getY() >= 1.0 && Place.replaceable(mc.level, under)) {
			return place(player, under, blocks, speed * 2) == Step.DONE ? Step.DONE : Step.WORKING;
		}
		return Step.WORKING;
	}
	public static void walk(LocalPlayer player, Vec3 goal, boolean sprint) {
		Vec3 to = goal.subtract(player.position()).multiply(1, 0, 1);
		if (to.lengthSqr() < 0.04) {
			Steering.stop();
			return;
		}
		Steering.steer(player, to.normalize(), sprint);
		if (player.onGround() && player.horizontalCollision) {
			Steering.requestJump();
		}
	}
	private static Vec3 along(Direction.Axis axis, double amount) {
		return switch (axis) {
			case X -> new Vec3(amount, 0, 0);
			case Y -> new Vec3(0, amount, 0);
			case Z -> new Vec3(0, 0, amount);
		};
	}
}
