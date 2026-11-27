package nz.temple.client.util;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class Rotations {
	private static float yaw;
	private static float pitch;
	private static boolean requested;
	private static int requestedPriority;
	private static float sentYaw;
	private static float sentPitch;
	private static boolean hasSent;
	private static boolean fixMovement;
	private Rotations() {
	}

	public static void request(float newYaw, float newPitch, int priority) {
		request(newYaw, newPitch, priority, false);
	}
	public static void request(float newYaw, float newPitch, int priority, boolean fixMovement) {
		if (requested && priority < requestedPriority) {
			return;
		}
		Rotations.fixMovement = fixMovement;
		yaw = newYaw;
		pitch = Mth.clamp(newPitch, -90f, 90f);
		requested = true;
		requestedPriority = priority;
	}
	public static boolean consume() {
		boolean r = requested;
		requested = false;
		fixMovement = false;
		return r;
	}
	public static boolean movementFixed() {
		return requested && fixMovement;
	}
	public static boolean pending() {
		return requested;
	}
	public static float requestedYaw() {
		return yaw;
	}
	public static float requestedPitch() {
		return pitch;
	}
	public static void sent(float y, float p) {
		sentYaw = y;
		sentPitch = p;
		hasSent = true;
	}

	public static float serverYaw(LocalPlayer player) {
		return hasSent ? sentYaw : player.getYRot();
	}
	public static float serverPitch(LocalPlayer player) {
		return hasSent ? sentPitch : player.getXRot();
	}
	public static float wholeTurns(float yaw) {
		if (!hasSent) {
			return 0;
		}
		return Math.round((sentYaw - yaw) / 360f) * 360f;
	}
	public static void reset() {
		requested = false;
		fixMovement = false;
		hasSent = false;
	}

	public static float[] toward(Vec3 from, Vec3 to, float nearYaw) {
		float[] r = Targets.rotations(from, to);
		r[0] = nearYaw + Mth.wrapDegrees(r[0] - nearYaw);
		return r;
	}

	public static float[] step(float fromYaw, float fromPitch, float toYaw, float toPitch, float maxStep) {
		float dy = Mth.clamp(Mth.wrapDegrees(toYaw - fromYaw), -maxStep, maxStep);
		float dp = Mth.clamp(toPitch - fromPitch, -maxStep, maxStep);
		return new float[] {fromYaw + dy, Mth.clamp(fromPitch + dp, -90f, 90f)};
	}

	public static float angle(float yawA, float pitchA, float yawB, float pitchB) {
		return Math.max(Math.abs(Mth.wrapDegrees(yawA - yawB)), Math.abs(pitchA - pitchB));
	}
}
