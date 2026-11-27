package nz.temple.client.util;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public final class Steering {
	private static Vec3 wish = Vec3.ZERO;
	private static boolean sprint;
	private static boolean jump;
	private static int expires;
	private static LocalPlayer owner;
	private Steering() {
	}
	public static void steer(LocalPlayer player, Vec3 direction, boolean sprinting) {
		wish = direction.lengthSqr() > 1e-6 ? direction.normalize() : Vec3.ZERO;
		sprint = sprinting;
		owner = player;
		expires = player.tickCount + 1;
	}
	public static void requestJump() {
		jump = true;
	}
	public static void stop() {
		wish = Vec3.ZERO;
		expires = 0;
		jump = false;
		owner = null;
	}

	public static boolean active(LocalPlayer player) {
		return player == owner && player.isAlive() && player.tickCount <= expires && (wish.lengthSqr() > 1e-6 || jump);
	}
	public static Vec3 direction(LocalPlayer player) {
		return active(player) && wish.lengthSqr() > 1e-6 ? wish : null;
	}
	private static float[] local(LocalPlayer player) {
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		double forwardX = -Mth.sin(yaw);
		double forwardZ = Mth.cos(yaw);
		double leftX = Mth.cos(yaw);
		double leftZ = Mth.sin(yaw);
		return new float[] {
				(float) (wish.x * forwardX + wish.z * forwardZ),
				(float) (wish.x * leftX + wish.z * leftZ)
		};
	}
	public static Input keys(LocalPlayer player, Input current) {
		float[] l = local(player);
		boolean wantJump = jump;
		jump = false;
		return new Input(
				l[0] > 0.3f || current.forward(),
				l[0] < -0.3f || current.backward(),
				l[1] > 0.3f || current.left(),
				l[1] < -0.3f || current.right(),
				wantJump || current.jump(),
				current.shift(),
				sprint || current.sprint());
	}
	public static Vec2 move(LocalPlayer player) {
		if (wish.lengthSqr() < 1e-6) {
			return Vec2.ZERO;
		}
		float[] l = local(player);
		return new Vec2(l[1], l[0]).normalized();
	}
}
