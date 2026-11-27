package nz.temple.client.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Matrix4fc;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.Map;

public final class Anchor {
	public static final int RIGHT_ARM = 0, LEFT_ARM = 1, RIGHT_LEG = 2, LEFT_LEG = 3;
	private static final Map<Integer, double[]> CAPTURED = new HashMap<>();
	private static final int PARTS = 6;
	private static long frame;
	private final Matrix4d head;
	private final Matrix4d body;
	private final Matrix4d[] limbs;
	private final double px;
	private final boolean helmet;
	private final boolean captured;
	private Anchor(Matrix4d head, Matrix4d body, Matrix4d[] limbs, double px, boolean helmet, boolean captured) {
		this.head = head;
		this.body = body;
		this.limbs = limbs;
		this.px = px;
		this.helmet = helmet;
		this.captured = captured;
	}
	static void capture(int entityId, Matrix4fc[] poses, Vec3 camera) {
		double[] m = new double[PARTS * 16 + 1];
		for (int i = 0; i < PARTS; i++) {
			toSpace(poses[i], camera).get(m, i * 16);
		}
		m[PARTS * 16] = frame;
		CAPTURED.put(entityId, m);
	}

	public static void nextFrame() {
		frame++;
		if (CAPTURED.size() > 256) {
			CAPTURED.values().removeIf(m -> m[PARTS * 16] < frame - 2);
		}
	}
	private static Matrix4d toSpace(Matrix4fc pose, Vec3 camera) {
		return new Matrix4d().translation(camera.x, camera.y, camera.z).mul(new Matrix4d().set(pose)).scale(1, -1, -1);
	}
	public static Anchor of(Player player, float partial) {
		boolean helmet = !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
		double px = 0.9375 * player.getScale() / 16;
		double[] m = CAPTURED.get(player.getId());
		if (m != null && m[PARTS * 16] == frame) {
			Matrix4d h = new Matrix4d().set(m, 0);
			Matrix4d b = new Matrix4d().set(m, 16);
			Matrix4d[] limbs = new Matrix4d[4];
			for (int i = 0; i < 4; i++) {
				limbs[i] = new Matrix4d().set(m, (2 + i) * 16);
			}
			return new Anchor(h, b, limbs, px, helmet, true);
		}
		return fallback(player, partial, px, helmet);
	}
	private static Anchor fallback(Player player, float partial, double px, boolean helmet) {
		Vec3 p = player.getPosition(partial);
		float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
		float headYaw = Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot);
		float pitch = player.getViewXRot(partial);
		double neckY = (player.isCrouching() ? 19.8 : 24) * px;
		double scale = px * 16;
		Matrix4d b = new Matrix4d().translation(p.x, p.y + neckY, p.z).rotateY(-bodyYaw * Mth.DEG_TO_RAD);
		if (player.isCrouching()) {
			b.rotateX(0.5);
		}
		b.scale(scale);
		Matrix4d h = new Matrix4d().translation(p.x, p.y + neckY, p.z).rotateY(-headYaw * Mth.DEG_TO_RAD).rotateX(pitch * Mth.DEG_TO_RAD)
				.scale(scale);
		double swing = Math.sin(player.walkAnimation.position(partial) * 0.6662) * 1.4 * player.walkAnimation.speed(partial);
		Matrix4d[] limbs = {
				new Matrix4d(b).translate(-5 / 16.0, -2 / 16.0, 0).rotateX(swing * 0.7),
				new Matrix4d(b).translate(5 / 16.0, -2 / 16.0, 0).rotateX(-swing * 0.7),
				new Matrix4d(b).translate(-1.9 / 16.0, -12 / 16.0, 0).rotateX(-swing),
				new Matrix4d(b).translate(1.9 / 16.0, -12 / 16.0, 0).rotateX(swing)};
		return new Anchor(h, b, limbs, px, helmet, false);
	}
	public Matrix4d head() {
		return new Matrix4d(head);
	}
	public Matrix4d body() {
		return new Matrix4d(body);
	}
	public Matrix4d limb(int which) {
		return new Matrix4d(limbs[which]);
	}
	public Vector3d limbEnd(int which) {
		return limbs[which].transformPosition(new Vector3d(0, which < 2 ? -10 / 16.0 : -12 / 16.0, 0));
	}
	public double px() {
		return px;
	}
	public boolean captured() {
		return captured;
	}
	public double headHalf() {
		return (helmet ? 5 : 4.5) / 16.0;
	}
	public double headTop() {
		return (helmet ? 9 : 8.5) / 16.0;
	}
	public boolean helmet() {
		return helmet;
	}

	public Vector3d headPoint(double x, double y, double z) {
		return head.transformPosition(new Vector3d(x, y, z));
	}

	public Vector3d bodyPoint(double x, double y, double z) {
		return body.transformPosition(new Vector3d(x, y, z));
	}

	public Vector3d headDir(double x, double y, double z) {
		return head.transformDirection(new Vector3d(x, y, z)).normalize();
	}
	public Vector3d bodyDir(double x, double y, double z) {
		return body.transformDirection(new Vector3d(x, y, z)).normalize();
	}
	public static boolean cameraInHead(Player player) {
		Minecraft mc = Minecraft.getInstance();
		return player == mc.player && mc.options.getCameraType().isFirstPerson() && !nz.temple.client.util.DetachedCamera.active();
	}
}
