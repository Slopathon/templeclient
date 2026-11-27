package nz.temple.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class BodySnapshot {
	public record Snap(Model<?> model, Object state, Matrix4f world, Matrix3f normal, Identifier texture, int light, long nanos) {
		public Vec3 origin() {
			org.joml.Vector3f o = world.transformPosition(new org.joml.Vector3f());
			return new Vec3(o.x, o.y, o.z);
		}
	}
	private record Ghost(Snap snap, Matrix4f motion, float scale, int argb, boolean lit) {
	}
	private static final Map<Integer, Snap> LATEST = new HashMap<>();
	private static List<Ghost> pending = new ArrayList<>();
	private static double camX;
	private static double camY;
	private static double camZ;
	private BodySnapshot() {
	}

	public static void capture(int entityId, Model<?> model, Object state, PoseStack.Pose pose, Identifier texture, int light, Vec3 cam) {
		if (entityId == 0) {
			return;
		}
		Matrix4f world = new Matrix4f().translation((float) cam.x, (float) cam.y, (float) cam.z).mul(pose.pose());
		LATEST.put(entityId, new Snap(model, state, world, new Matrix3f(pose.normal()), texture, light, System.nanoTime()));
	}

	public static Snap latest(int entityId) {
		Snap s = LATEST.get(entityId);
		return s != null && System.nanoTime() - s.nanos < 1_500_000_000L ? s : null;
	}
	public static void ghost(Snap snap, double dx, double dy, double dz, float scale, int argb) {
		ghost(snap, new Matrix4f().translation((float) dx, (float) dy, (float) dz), scale, argb);
	}
	public static void ghost(Snap snap, Matrix4f motion, float scale, int argb) {
		if (snap != null && (argb >>> 24) > 2 && pending.size() < 256) {
			pending.add(new Ghost(snap, new Matrix4f(motion), scale, argb, false));
		}
	}
	public static void body(Snap snap, double dx, double dy, double dz, int argb) {
		if (snap != null && (argb >>> 24) > 2 && pending.size() < 256) {
			pending.add(new Ghost(snap, new Matrix4f().translation((float) dx, (float) dy, (float) dz), 1, argb, true));
		}
	}
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void submitGhosts(SubmitNodeCollector collector, Vec3 cam) {
		camX = cam.x;
		camY = cam.y;
		camZ = cam.z;
		List<Ghost> now = pending;
		pending = new ArrayList<>();
		PoseStack stack = new PoseStack();
		for (Ghost g : now) {
			Snap s = g.snap;
			Matrix4f pose = new Matrix4f().translation((float) -camX, (float) -camY, (float) -camZ).mul(g.motion).mul(s.world);
			if (g.scale != 1) {
				pose.translate(0, 0.75f, 0).scale(g.scale).translate(0, -0.75f, 0);
			}
			stack.last().pose().set(pose);
			stack.last().normal().set(new Matrix3f(g.motion).mul(s.normal));
			Model model = s.model;
			try {
				nz.temple.client.compat.Submit.model(collector, model, s.state, stack, g.lit ? RenderTypes.entityTranslucent(s.texture) : RenderTypes.entityTranslucentEmissive(s.texture),
						g.lit ? s.light : 0xF000F0, OverlayTexture.NO_OVERLAY, g.argb, null, 0);
			} catch (RuntimeException e) {
			}
		}
		long cutoff = System.nanoTime() - 3_000_000_000L;
		for (Iterator<Snap> it = LATEST.values().iterator(); it.hasNext(); ) {
			if (it.next().nanos < cutoff) {
				it.remove();
			}
		}
	}
}
