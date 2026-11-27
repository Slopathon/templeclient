package nz.temple.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.function.Function;

public final class AimSmoother {
	private static LivingEntity target;
	private static Function<LivingEntity, Vec3> point;
	private static Vec3 spot;
	private static float maxPerTick;
	private static float smoothing;
	private static int expires;
	private static long last;
	private static Object lastKey;
	private static float lastWantYaw, lastWantPitch;
	private AimSmoother() {
	}
	public static void aim(LocalPlayer player, LivingEntity newTarget, Function<LivingEntity, Vec3> aimPoint, float maxDegreesPerTick, float smooth) {
		target = newTarget;
		point = aimPoint;
		spot = null;
		maxPerTick = maxDegreesPerTick;
		smoothing = Mth.clamp(smooth, 0, 1);
		expires = player.tickCount + 2;
	}
	public static void look(LocalPlayer player, Vec3 at, float maxDegreesPerTick, float smooth) {
		target = null;
		point = null;
		spot = at;
		maxPerTick = maxDegreesPerTick;
		smoothing = Mth.clamp(smooth, 0, 1);
		expires = player.tickCount + 2;
	}
	public static float offBy(LocalPlayer player, Vec3 at) {
		float[] want = Rotations.toward(player.getEyePosition(), at, player.getYRot());
		return Rotations.angle(player.getYRot(), player.getXRot(), want[0], want[1]);
	}
	public static void frame() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		long now = System.nanoTime();
		float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1e9f);
		last = now;
		boolean aimingEntity = target != null && point != null && !target.isRemoved();
		if (player == null || !aimingEntity && spot == null || player.tickCount > expires
				|| mc.gui.screen() != null || DetachedCamera.freecam()) {
			target = null;
			spot = null;
			lastKey = null;
			return;
		}
		if (dt <= 0) {
			return;
		}
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 aim = aimingEntity ? point.apply(target).add(target.getPosition(partial).subtract(target.xo, target.yo, target.zo)) : spot;
		float yaw = player.getYRot();
		float pitch = player.getXRot();
		float[] want = Rotations.toward(player.getEyePosition(partial), aim, yaw);
		float rate = 28 - 22 * smoothing;
		float k = 1 - (float) Math.exp(-rate * dt);
		float cap = maxPerTick * 20 * dt;
		Object key = aimingEntity ? target : spot;
		float followYaw = 0, followPitch = 0;
		if (key == lastKey || key != null && key.equals(lastKey)) {
			followYaw = Mth.wrapDegrees(want[0] - lastWantYaw);
			followPitch = want[1] - lastWantPitch;
		}
		lastKey = key;
		lastWantYaw = want[0];
		lastWantPitch = want[1];
		float[] follow = capTurn(followYaw, followPitch, cap);
		float[] rest = capTurn((want[0] - yaw - follow[0]) * k, (want[1] - pitch - follow[1]) * k, cap);
		float stepYaw = follow[0] + rest[0];
		float stepPitch = follow[1] + rest[1];
		float newPitch = Mth.clamp(pitch + stepPitch, -90f, 90f);
		stepPitch = newPitch - pitch;
		player.setYRot(yaw + stepYaw);
		player.setXRot(newPitch);
		player.yRotO += stepYaw;
		player.xRotO += stepPitch;
	}
	private static float[] capTurn(float yaw, float pitch, float cap) {
		float length = (float) Math.sqrt(yaw * yaw + pitch * pitch);
		if (length <= cap || length < 1e-6f) {
			return new float[] {yaw, pitch};
		}
		float f = cap / length;
		return new float[] {yaw * f, pitch * f};
	}
}
