package nz.temple.client.render;

import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

public final class LevelProjection {
	private static final Matrix4f PROJECTION = new Matrix4f();
	private static final Matrix4f PLAIN = new Matrix4f();
	private static final Matrix4f VIEW_ROTATION = new Matrix4f();
	private static boolean captured;
	private LevelProjection() {
	}
	public static void capture(Matrix4f projection, Matrix4f plain, Matrix4f viewRotation) {
		PROJECTION.set(projection);
		PLAIN.set(plain);
		VIEW_ROTATION.set(viewRotation);
		captured = true;
	}
	public static @Nullable Matrix4f projection(CameraRenderState camera) {
		return captured ? PROJECTION : camera.projectionMatrix;
	}
	public static boolean viewProjection(Matrix4f dest) {
		if (!captured) {
			return false;
		}
		dest.set(PROJECTION).mul(VIEW_ROTATION);
		return true;
	}
	public static boolean cameraViewProjection(Matrix4f dest) {
		if (!captured) {
			return false;
		}
		dest.set(PLAIN).mul(VIEW_ROTATION);
		return true;
	}
	public static @Nullable Vector3f ahead(float distance) {
		if (!captured) {
			return null;
		}
		Matrix4f toWorld = new Matrix4f(PLAIN).invert().mul(PROJECTION).invert();
		Vector4f p = new Vector4f(0, 0, -distance, 1);
		toWorld.transform(p);
		new Matrix4f(VIEW_ROTATION).invert().transform(p);
		return new Vector3f(p.x / p.w, p.y / p.w, p.z / p.w);
	}
}
