package nz.temple.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class Projection {
	private final Matrix4f viewProjection = new Matrix4f();
	private final Vector4f scratch = new Vector4f();
	private Vec3 cameraPos = Vec3.ZERO;
	private boolean ready;
	public void register() {
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(ctx -> capture(ctx.levelState().cameraRenderState));
	}

	private void capture(CameraRenderState camera) {
		if (camera.projectionMatrix == null || camera.viewRotationMatrix == null || camera.pos == null) {
			ready = false;
			return;
		}
		viewProjection.set(nz.temple.client.render.LevelProjection.projection(camera)).mul(camera.viewRotationMatrix);
		cameraPos = camera.pos;
		ready = true;
	}
	public boolean ready() {
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.level == null) { ready = false; return false; }
		var camera = mc.gameRenderer.mainCamera();
		if (camera.isInitialized()) {
			if (!LevelProjection.viewProjection(viewProjection)) {
				camera.getViewRotationProjectionMatrix(viewProjection);
			}
			cameraPos = camera.position();
			ready = true;
		}
		return ready;
	}
	public float[] toScreen(double wx, double wy, double wz, int guiWidth, int guiHeight) {
		if (!ready) {
			return null;
		}
		scratch.set((float) (wx - cameraPos.x), (float) (wy - cameraPos.y), (float) (wz - cameraPos.z), 1f);
		viewProjection.transform(scratch);
		if (scratch.w <= 1e-4f) {
			return null;
		}
		float ndcX = scratch.x / scratch.w;
		float ndcY = scratch.y / scratch.w;
		return new float[] {
				(ndcX * 0.5f + 0.5f) * guiWidth,
				(1f - (ndcY * 0.5f + 0.5f)) * guiHeight
		};
	}
	public Vec3 cameraPos() {
		return cameraPos;
	}
	public float screenRadius(double wx, double wy, double wz, double worldRadius, int guiWidth, int guiHeight) {
		float[] center = toScreen(wx, wy, wz, guiWidth, guiHeight);
		if (center == null) {
			return -1;
		}
		double dx = wx - cameraPos.x;
		double dz = wz - cameraPos.z;
		double rx = -dz;
		double rz = dx;
		double len = Math.sqrt(rx * rx + rz * rz);
		if (len < 1e-6) {
			rx = 1;
			rz = 0;
			len = 1;
		}
		float[] edge = toScreen(wx + rx / len * worldRadius, wy, wz + rz / len * worldRadius, guiWidth, guiHeight);
		if (edge == null) {
			return -1;
		}
		return (float) Math.hypot(edge[0] - center[0], edge[1] - center[1]);
	}
}
