package nz.temple.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class PreviewSpot {
	private PreviewSpot() {
	}

	public static Vec3 inFront(Minecraft mc, double distance) {
		var camera = mc.gameRenderer.mainCamera();
		Vec3 eye = camera.position();
		Vec3 look = Vec3.directionFromRotation(0, camera.yRot()).scale(distance);
		Vec3 ahead = eye.add(look);
		BlockHitResult hit = mc.level.clip(new ClipContext(ahead.add(0, 1, 0), ahead.add(0, -6, 0),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, mc.player));
		return hit.getType() == HitResult.Type.MISS ? ahead : hit.getLocation();
	}
}
