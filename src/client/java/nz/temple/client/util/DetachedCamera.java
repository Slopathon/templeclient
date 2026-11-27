package nz.temple.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import nz.temple.module.Module;

public final class DetachedCamera {
	private static Module owner;
	private static LocalPlayer player;
	private static boolean free;
	private static Vec3 position = Vec3.ZERO;
	private static Vec3 previous = Vec3.ZERO;
	private static float yaw, pitch;
	private static boolean cameraNoclip;
	private static float distance = 4;

	private DetachedCamera() { }

	public static void begin(Module module, boolean freecam) {
		if (owner != null && owner != module) owner.setEnabled(false);
		player = Minecraft.getInstance().player;
		if (player == null) { module.setEnabled(false); return; }
		owner = module;
		free = freecam;
		position = previous = player.getEyePosition();
		yaw = player.getYRot();
		pitch = player.getXRot();
		viewYaw = yaw;
		viewPitch = pitch;
		following = false;
	}

	public static void end(Module module) {
		if (owner == module) { owner = null; player = null; }
	}

	/**
	 * Whether a camera other than the player's own view is in use: Freelook or Freecam, or the Cinematic Camera's
	 * director. Effects use this to know the player can see their own body.
	 */
	public static boolean active() {
		return ownerActive() || directed();
	}

	/** Whether the Cinematic Camera is placing the camera (only when Freelook and Freecam are off). */
	public static boolean directed() {
		return !ownerActive() && nz.temple.client.modules.render.CinematicCamera.active();
	}

	/** Whether Freelook or Freecam owns the camera. */
	public static boolean ownerActive() {
		Minecraft mc = Minecraft.getInstance();
		return owner != null && owner.isEnabled() && player != null && mc.player == player
				&& mc.level == player.level() && player.isAlive();
	}

	public static void configure(boolean noclip, float behind) {
		cameraNoclip = noclip;
		distance = behind;
	}

	public static boolean cameraNoclip() { return cameraNoclip; }
	private static boolean hideHand = true;
	private static boolean staySneaking;
	public static void hideHand(boolean hide) { hideHand = hide; }
	public static boolean handHidden() { return directed() || ownerActive() && hideHand; }
	public static void staySneaking(boolean stay) { staySneaking = stay; }
	public static boolean bodySneaking() { return freecam() && staySneaking; }
	private static boolean allowMining = true;
	public static void allowMining(boolean allow) { allowMining = allow; }
	public static boolean blocksMining() { return freecam() && !allowMining; }
	public static float distance() { return distance; }
	public static boolean freecam() { return ownerActive() && free; }
	public static float lookYaw() { return yaw; }
	public static float yaw() { smooth(); return cinematic ? viewYaw : yaw; }
	public static float pitch() { smooth(); return cinematic ? viewPitch : pitch; }
	private static boolean cinematic;
	private static float smoothness = 6;
	private static float viewYaw, viewPitch;
	private static long lastSmooth;
	public static void cinematic(boolean on, float amount) {
		if (on && !cinematic) {
			viewYaw = yaw;
			viewPitch = pitch;
		}
		cinematic = on;
		smoothness = amount;
	}
	public static boolean cinematicOn() {
		return cinematic && ownerActive() && !free;
	}
	private static void smooth() {
		long now = System.nanoTime();
		float dt = lastSmooth == 0 ? 0 : Math.min(0.1f, (now - lastSmooth) / 1e9f);
		lastSmooth = now;
		if (!cinematic || dt <= 0) {
			viewYaw = yaw;
			viewPitch = pitch;
			return;
		}
		float rate = 16f / Math.max(1, smoothness);
		float k = 1 - (float) Math.exp(-rate * dt);
		viewYaw += Mth.wrapDegrees(yaw - viewYaw) * k;
		viewPitch += (pitch - viewPitch) * k;
	}
	public static Vec3 position(float delta) { return previous.lerp(position, delta); }
	private static boolean robloxStyle;
	private static boolean following;
	public static void robloxStyle(boolean on) { robloxStyle = on; }

	public static boolean followsPlayer() {
		Minecraft mc = Minecraft.getInstance();
		boolean held = robloxStyle && ownerActive() && !free && mc.gui.screen() == null
				&& nz.temple.client.compat.Input.isMouseDown(mc.getWindow(),
						com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
		if (held && !following) {
			player.setYRot(yaw);
			player.setXRot(pitch);
			player.yRotO = yaw;
			player.xRotO = pitch;
		}
		following = held;
		return held;
	}
	public static void followPlayer() {
		if (player != null) {
			yaw = Mth.wrapDegrees(player.getYRot());
			pitch = Mth.clamp(player.getXRot(), -90, 90);
		}
	}
	public static void turn(double dx, double dy) {
		yaw = Mth.wrapDegrees(yaw + (float) dx * 0.15f);
		pitch = Mth.clamp(pitch + (float) dy * 0.15f, -90, 90);
	}
	public static void tick(Module module, double speed) {
		if (!ownerActive()) { module.setEnabled(false); return; }
		previous = position;
		Minecraft mc = Minecraft.getInstance();
		if (!free || mc.gui.screen() != null || mc.isPaused()) return;
		double forward = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
		double side = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
		double vertical = (mc.options.keyJump.isDown() ? 1 : 0) - (mc.options.keyShift.isDown() ? 1 : 0);
		Vec3 ahead = Vec3.directionFromRotation(pitch, yaw);
		Vec3 left = Vec3.directionFromRotation(0, yaw - 90);
		Vec3 motion = ahead.scale(forward).add(left.scale(side)).add(0, vertical, 0);
		if (motion.lengthSqr() > 1) motion = motion.normalize();
		position = position.add(motion.scale(speed * (mc.options.keySprint.isDown() ? 3 : 1)));
	}
}
