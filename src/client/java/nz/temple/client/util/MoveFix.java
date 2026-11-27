package nz.temple.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

public final class MoveFix {
	private MoveFix() {
	}
	private static boolean active(Entity entity) {
		return entity == Minecraft.getInstance().player && Rotations.movementFixed();
	}
	public static float yaw(Entity entity, float yaw) {
		return active(entity) ? Rotations.requestedYaw() : yaw;
	}

	public static Input keys(Entity entity, Input keys, Vec2 move) {
		if (!active(entity) || move.lengthSquared() < 1e-6f) {
			return null;
		}

		double wish = Math.atan2(-move.x, move.y) + entity.getYRot() * Mth.DEG_TO_RAD;
		double sent = Rotations.requestedYaw() * Mth.DEG_TO_RAD;
		int bestForward = 0, bestLeft = 0;
		double bestDot = -2;
		for (int forward = -1; forward <= 1; forward++) {
			for (int left = -1; left <= 1; left++) {
				if (forward == 0 && left == 0) {
					continue;
				}
				double dot = Math.cos(Math.atan2(-left, forward) + sent - wish);
				if (dot > bestDot) {
					bestDot = dot;
					bestForward = forward;
					bestLeft = left;
				}
			}
		}
		return new Input(bestForward > 0, bestForward < 0, bestLeft > 0, bestLeft < 0, keys.jump(), keys.shift(), keys.sprint());
	}
	public static Vec2 vector(Input keys) {
		float left = (keys.left() ? 1 : 0) - (keys.right() ? 1 : 0);
		float forward = (keys.forward() ? 1 : 0) - (keys.backward() ? 1 : 0);
		return new Vec2(left, forward).normalized();
	}
}
