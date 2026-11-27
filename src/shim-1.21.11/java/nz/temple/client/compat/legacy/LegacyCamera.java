package nz.temple.client.compat.legacy;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

public final class LegacyCamera {
	static @Nullable Matrix4f projection;
	static @Nullable Matrix4f viewRotation;
	static @Nullable Frustum frustum;
	static float xRot;
	static float yRot;
	private LegacyCamera() {
	}
	public static @Nullable Matrix4f projectionMatrix(CameraRenderState state) {
		return projection;
	}

	public static @Nullable Matrix4f viewRotationMatrix(CameraRenderState state) {
		return viewRotation;
	}

	public static @Nullable Frustum cullFrustum(CameraRenderState state) {
		return frustum;
	}

	public static Matrix4f viewRotationProjection(Matrix4f dest) {
		if (projection == null || viewRotation == null) {
			return dest.identity();
		}
		return dest.set(projection).mul(viewRotation);
	}
	public static float xRot() {
		return xRot;
	}

	public static float yRot() {
		return yRot;
	}

	private static @Nullable Matrix4f plain;
	public static void capturePlain(Matrix4f projection) {
		plain = new Matrix4f(projection);
	}
	public static Matrix4f plainProjection(Matrix4f drawn) {
		return plain != null ? plain : drawn;
	}
	public static void capture(net.minecraft.client.Camera camera, Matrix4f viewRotationMatrix, Matrix4f projectionMatrix) {
		viewRotation = new Matrix4f(viewRotationMatrix);
		projection = new Matrix4f(projectionMatrix);
		xRot = camera.xRot();
		yRot = camera.yRot();
	}
	public static void captureFrustum(Frustum cull) {
		frustum = cull;
	}
}
