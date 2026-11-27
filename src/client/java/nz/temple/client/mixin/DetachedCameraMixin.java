package nz.temple.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.util.DetachedCamera;

@Mixin(Camera.class)
public abstract class DetachedCameraMixin {
	@Shadow private boolean detached;
	@Shadow protected abstract void setPosition(Vec3 pos);
	@Shadow protected abstract void setRotation(float yaw, float pitch);
	@Shadow protected abstract void move(float forward, float up, float left);
	@Shadow private float getMaxZoom(float distance) { throw new AssertionError(); }

	//? if >=26 {
	@Inject(method = "alignWithEntity", at = @At("HEAD"), cancellable = true)
	private void temple$camera(float delta, CallbackInfo ci) {
		if (temple$place(delta)) {
			ci.cancel();
		}
	}
	//?} else {
	/*// 1.21.11 places the camera in setup, along with the rest of its per-frame state: let that run, then move it.
	@Inject(method = "setup", at = @At("TAIL"))
	private void temple$camera(net.minecraft.world.level.Level level, net.minecraft.world.entity.Entity entity, boolean thirdPerson,
			boolean mirror, float delta, CallbackInfo ci) {
		temple$place(delta);
	}
	*///?}

	/** Puts the camera where the detached camera wants it; false (and nothing changed) when that is off. */
	private boolean temple$place(float delta) {
		nz.temple.client.modules.render.CinematicCamera.frame(delta);
		if (DetachedCamera.directed()) {
			var shot = nz.temple.client.modules.render.CinematicCamera.pose(delta);
			detached = true;
			setRotation(shot.yaw(), shot.pitch());
			setPosition(shot.pos());
			return true;
		}
		if (!DetachedCamera.ownerActive()) return false;
		detached = true;
		setRotation(DetachedCamera.yaw(), DetachedCamera.pitch());
		if (DetachedCamera.freecam()) {
			setPosition(DetachedCamera.position(delta));
		} else {
			setPosition(Minecraft.getInstance().player.getEyePosition(delta));
			float distance = DetachedCamera.distance();
			// Noclip skips the wall raycast, so the camera passes through blocks instead of being pulled in.
			move(-(DetachedCamera.cameraNoclip() ? distance : getMaxZoom(distance)), 0, 0);
		}
		return true;
	}

	/**
	 * Chunk sections are normally picked by walking the visibility graph outward from the section the camera is
	 * in. Once the freecam is underground or outside the world that walk reaches almost nothing, so terrain stops
	 * rendering. Turning smart culling off falls back to plain frustum culling, which draws everything in view.
	 * (1.21.11 reads smart culling straight from Minecraft: LegacyCameraCaptureMixin turns it off there.)
	 */
	//? if >=26 {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	//? if >=26.3 {
	/*private void temple$disableSmartCull(CameraRenderState state, net.minecraft.client.DeltaTracker delta, CallbackInfo ci) {
	*///?} else {
	private void temple$disableSmartCull(CameraRenderState state, float delta, CallbackInfo ci) {
	//?}
		if (DetachedCamera.freecam()) {
			state.smartCull = false;
		}
	}
	//?}
}
