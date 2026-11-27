package nz.temple.client.util;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

public final class Motion {
	private Motion() {
	}

	public static Input keys(LocalPlayer player) {
		return player.input.keyPresses;
	}

	public static boolean movingHorizontally(LocalPlayer player) {
		Input k = keys(player);
		return k.forward() || k.backward() || k.left() || k.right();
	}

	public static Vec3 wishDirection(LocalPlayer player) {
		Input k = keys(player);
		double forward = (k.forward() ? 1 : 0) - (k.backward() ? 1 : 0);
		double strafe = (k.right() ? 1 : 0) - (k.left() ? 1 : 0);
		if (forward == 0 && strafe == 0) {
			return Vec3.ZERO;
		}
		float yaw = (float) Math.toRadians(player.getYRot());
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		double x = -forward * sin - strafe * cos;
		double z = forward * cos - strafe * sin;
		Vec3 dir = new Vec3(x, 0, z);
		return dir.lengthSqr() > 1e-6 ? dir.normalize() : Vec3.ZERO;
	}
}
