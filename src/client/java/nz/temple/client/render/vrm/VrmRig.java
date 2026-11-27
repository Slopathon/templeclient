package nz.temple.client.render.vrm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public final class VrmRig {
	public interface Ground {
		float height(double x, double y, double z);
	}
	public static final class Drive {
		public final float[][] parts = new float[6][3];
		public boolean attacking;
		public float swing;
		public int swingSide;
		public final boolean[] busy = new boolean[2];
		public final boolean[] holding = new boolean[2];
		public final Map<String, VrmAnimation> clips = new HashMap<>();
		public final Map<String, Float> weights = new HashMap<>();
		public float time;
		public float dt;
		public double x;
		public double y;
		public double z;
		public float vx;
		public float vy;
		public float vz;
		public float bodyYaw;
		public float headYaw;
		public float pitch;
		public boolean grounded = true;
		public boolean sneaking;
		public boolean flying;
		public boolean swimming;
		public boolean fallFlying;
		public boolean passenger;
		public boolean sleeping;
		public boolean inWater;
		public Ground ground;
		public float scale;
		public boolean anime;
		public boolean outline;
		public boolean physics;
		public JigglePaint jiggle;
		public boolean jiggleMotion = true;
		public float yawOut;
	}
	private static final String[] FINGERS = {"Index", "Middle", "Ring", "Little"};
	private static final String[] JOINTS = {"Proximal", "Intermediate", "Distal"};
	private static final float[] CURL = {22, 32, 22};
	private static final float[] GRIP = {78, 92, 55};
	private static final nz.temple.util.Perf.Section EMIT = nz.temple.util.Perf.section("avatar emit");
	private final VrmAvatar avatar;
	private final VrmModel m;
	private final Quaternionf[] local;
	private final Matrix4f[] world;
	private final Vector3f[] tail;
	private final Vector3f[] prevTail;
	private boolean springReady;
	private double ox;
	private double oy;
	private double oz;
	private final float[][] skinPos;
	private final float[][] skinNrm;
	private final Matrix4f[][] jointMats;
	private final VrmLocomotion loco;
	private float blinkTimer = 2;
	private float blink;
	private long lastSeen;
	public VrmRig(VrmAvatar avatar) {
		this.avatar = avatar;
		this.m = avatar.model;
		local = new Quaternionf[m.nodeCount];
		world = new Matrix4f[m.nodeCount];
		for (int i = 0; i < m.nodeCount; i++) {
			local[i] = new Quaternionf(m.restR[i]);
			world[i] = new Matrix4f();
		}
		tail = new Vector3f[m.springs.size()];
		prevTail = new Vector3f[m.springs.size()];
		for (int i = 0; i < tail.length; i++) {
			tail[i] = new Vector3f();
			prevTail[i] = new Vector3f();
		}
		skinPos = new float[m.sets.size()][];
		skinNrm = new float[m.sets.size()][];
		jointMats = new Matrix4f[m.sets.size()][];
		for (int s = 0; s < skinPos.length; s++) {
			VrmModel.VertexSet set = m.sets.get(s);
			skinPos[s] = new float[set.pos.length];
			skinNrm[s] = new float[set.pos.length];
			jointMats[s] = new Matrix4f[set.skinNodes.length];
			for (int j = 0; j < jointMats[s].length; j++) {
				jointMats[s][j] = new Matrix4f();
			}
		}
		loco = new VrmLocomotion(this, m);
	}
	public VrmAvatar avatar() {
		return avatar;
	}
	public long lastSeen() {
		return lastSeen;
	}

	public float eyeHeight() {
		return m.eyeHeight;
	}

	public float soleY() {
		return m.soleY;
	}

	private static Quaternionf part(float[] r) {
		return new Quaternionf().rotationZYX(r[2], -r[1], -r[0]);
	}

	private static final Quaternionf HALF = new Quaternionf().rotationY((float) Math.PI);
	private static Quaternionf flip(Quaternionf q) {
		return new Quaternionf(HALF).mul(q).mul(HALF);
	}

	private Quaternionf localFor(int n, Quaternionf q) {
		Quaternionf aligned = m.vrm1 ? flip(q) : new Quaternionf(q);
		int p = m.parent[n];
		if (p >= 0) {
			Quaternionf pw = m.restWorldR[p];
			aligned = new Quaternionf(pw).invert().mul(aligned).mul(pw);
		}
		return aligned.mul(m.restR[n]);
	}
	void setBone(String bone, Quaternionf q) {
		Integer n = m.human.get(bone);
		if (n != null) {
			local[n].set(localFor(n, q));
		}
	}

	void mixBone(String bone, Quaternionf q, float w) {
		Integer n = m.human.get(bone);
		if (n != null) {
			local[n].slerp(localFor(n, q), Math.max(0, Math.min(1, w)));
		}
	}

	void setLocal(int node, Quaternionf q) {
		local[node].set(q).normalize();
	}
	void resetPose() {
		for (int i = 0; i < m.nodeCount; i++) {
			local[i].set(m.restR[i]);
		}
		hipsOffset.set(0);
	}

	public boolean handFrame(int side, Vector3f palm, Vector3f along, Vector3f thumb) {
		String s = side == 0 ? "right" : "left";
		Integer hand = m.human.get(s + "Hand");
		Integer lower = m.human.get(s + "LowerArm");
		if (hand == null || lower == null) {
			return false;
		}
		Vector3f wrist = worldPos(hand);
		Integer finger = m.human.get(s + "MiddleProximal");
		Vector3f knuckle = finger != null ? worldPos(finger)
				: new Vector3f(wrist).sub(worldPos(lower)).mul(0.3f).add(wrist);
		along.set(knuckle).sub(wrist);
		float len = along.length();
		if (len < 1e-5f) {
			return false;
		}
		along.div(len);
		palm.set(wrist).fma(len * 0.6f, along);
		Integer t = m.human.get(s + "ThumbProximal");
		if (t == null) {
			t = m.human.get(s + "ThumbIntermediate");
		}
		if (t != null) {
			thumb.set(worldPos(t)).sub(wrist);
		} else {
			thumb.set(0, 0, m.vrm1 ? 1 : -1);
		}
		thumb.fma(-thumb.dot(along), along);
		if (thumb.lengthSquared() < 1e-10f) {
			return false;
		}
		thumb.normalize();
		return true;
	}
	Vector3f worldPos(int n) {
		return world[n].getTranslation(new Vector3f());
	}
	Quaternionf worldRot(int n) {
		return world[n].getUnnormalizedRotation(new Quaternionf()).normalize();
	}
	void relaxHands(boolean[] holding) {
		for (int side = 0; side < 2; side++) {
			String s = side == 0 ? "right" : "left";
			float sign = side == 0 ? -1 : 1;
			float[] bend = holding[side] ? GRIP : CURL;
			for (String f : FINGERS) {
				for (int j = 0; j < JOINTS.length; j++) {
					setBone(s + f + JOINTS[j], new Quaternionf().rotationZ(sign * (float) Math.toRadians(bend[j])));
				}
			}
			if (holding[side]) {
				setBone(s + "ThumbProximal", new Quaternionf().rotationY(-sign * (float) Math.toRadians(25)));
			}
			setBone(s + "ThumbIntermediate", new Quaternionf().rotationY(-sign * (float) Math.toRadians(holding[side] ? 45 : 20)));
		}
	}
	private void pose(Drive d) {
		loco.apply(d);
		Quaternionf unbody = part(d.parts[1]).invert();
		for (int side = 0; side < 2; side++) {
			if (d.busy[side]) {
				float lower = side == 0 ? -72 : 72;
				setBone(side == 0 ? "rightUpperArm" : "leftUpperArm",
						new Quaternionf(unbody).mul(part(d.parts[2 + side])).rotateZ((float) Math.toRadians(lower)));
				setBone(side == 0 ? "rightLowerArm" : "leftLowerArm", new Quaternionf().rotationY((float) Math.toRadians(side == 0 ? 12 : -12)));
			}
		}
		float clipTotal = 0;
		for (float w : d.weights.values()) {
			clipTotal += w;
		}
		if (clipTotal <= 0.001f) {
			return;
		}
		Quaternionf scratch = new Quaternionf();
		for (String bone : m.human.keySet()) {
			if (bone.equals("head") || bone.equals("neck")
					|| ((d.busy[0] || d.attacking && d.swingSide == 0) && bone.startsWith("right") && bone.contains("Arm"))
					|| ((d.busy[1] || d.attacking && d.swingSide == 1) && bone.startsWith("left") && bone.contains("Arm"))) {
				continue;
			}
			Quaternionf mixed = null;
			float used = 0;
			for (Map.Entry<String, VrmAnimation> c : d.clips.entrySet()) {
				float w = d.weights.getOrDefault(c.getKey(), 0f);
				VrmAnimation a = c.getValue();
				if (w <= 0.001f || a == null) {
					continue;
				}
				float t = a.length > 0 ? d.time % a.length : 0;
				if (a.rotation(bone, t, scratch)) {
					scratch.set(flip(scratch));
					mixed = mixed == null ? new Quaternionf(scratch) : mixed.slerp(scratch, w / (used + w));
					used += w;
				}
			}
			if (mixed != null) {
				mixBone(bone, mixed, used);
			}
		}
		Integer hips = m.human.get("hips");
		if (hips != null) {
			Vector3f off = new Vector3f();
			Vector3f clip = new Vector3f();
			for (Map.Entry<String, VrmAnimation> c : d.clips.entrySet()) {
				float w = d.weights.getOrDefault(c.getKey(), 0f);
				VrmAnimation a = c.getValue();
				if (w > 0.001f && a != null && a.hipsOffset(a.length > 0 ? d.time % a.length : 0, off)) {
					if (!m.vrm1) {
						off.x = -off.x;
						off.z = -off.z;
					}
					clip.fma(w, off);
				}
			}
			float w = Math.min(1, clipTotal);
			hipsOffset.mul(1 - w).fma(m.restT[hips].y, clip);
		}
	}
	final Vector3f hipsOffset = new Vector3f();
	void worldPass() {
		Integer hips = m.human.get("hips");
		Vector3f t = new Vector3f();
		Matrix4f own = new Matrix4f();
		for (int i : m.order) {
			t.set(m.restT[i]);
			if (hips != null && i == hips) {
				t.add(hipsOffset);
			}
			own.translationRotateScale(t, local[i], m.restS[i]);
			if (m.parent[i] >= 0) {
				world[i].set(world[m.parent[i]]).mul(own);
			} else {
				world[i].set(own);
			}
		}
	}
	private Matrix4f physicsFrame(Drive d) {
		Matrix4f frame = new Matrix4f().rotationY((float) Math.toRadians(180 - (d.bodyYaw - d.yawOut))).scale(d.scale);
		return m.vrm1 ? frame.rotateY((float) Math.PI) : frame;
	}
	private void springs(Drive d) {
		if (m.springs.isEmpty()) {
			return;
		}
		Matrix4f frame = physicsFrame(d);
		float mx = (float) (d.x - ox);
		float my = (float) (d.y - oy);
		float mz = (float) (d.z - oz);
		boolean jump = !springReady || mx * mx + my * my + mz * mz > 16;
		ox = d.x;
		oy = d.y;
		oz = d.z;
		float dt = Math.max(1e-4f, Math.min(d.dt, 1 / 30f));
		Vector3f[][] centres = new Vector3f[m.colliderGroups.size()][];
		float[][] radii = new float[m.colliderGroups.size()][];
		for (int g = 0; g < centres.length; g++) {
			var group = m.colliderGroups.get(g);
			centres[g] = new Vector3f[group.size()];
			radii[g] = new float[group.size()];
			for (int c = 0; c < group.size(); c++) {
				VrmModel.Collider col = group.get(c);
				centres[g][c] = new Matrix4f(frame).mul(world[col.node()]).transformPosition(new Vector3f(col.offset()));
				radii[g][c] = col.radius() * d.scale;
			}
		}
		Map<Integer, Integer> jointOf = new HashMap<>();
		for (int j = 0; j < m.springs.size(); j++) {
			jointOf.put(m.springs.get(j).node, j);
		}
		Vector3f t = new Vector3f();
		Matrix4f scratch = new Matrix4f();
		Integer hips = m.human.get("hips");
		for (int i : m.order) {
			int p = m.parent[i];
			t.set(m.restT[i]);
			if (hips != null && i == hips) {
				t.add(hipsOffset);
			}
			Integer ji = jointOf.get(i);
			if (ji != null && p >= 0) {
				VrmModel.SpringJoint sj = m.springs.get(ji);
				Matrix4f parentPhys = new Matrix4f(frame).mul(world[p]);
				Quaternionf parentRot = parentPhys.getUnnormalizedRotation(new Quaternionf()).normalize();
				world[i].set(world[p]).mul(scratch.translationRotateScale(t, local[i], m.restS[i]));
				Vector3f head = new Matrix4f(frame).mul(world[i]).getTranslation(new Vector3f());
				Quaternionf rest = new Quaternionf(parentRot).mul(local[i]);
				Vector3f axis = rest.transform(new Vector3f(sj.axis)).normalize();
				float length = new Matrix4f(frame).mul(world[i]).transformDirection(new Vector3f(sj.axis).mul(sj.length)).length();
				Vector3f cur = tail[ji];
				Vector3f prev = prevTail[ji];
				if (jump) {
					cur.set(head).fma(length, axis);
					prev.set(cur);
				} else {
					cur.sub(mx, my, mz);
					prev.sub(mx, my, mz);
				}
				Vector3f next = new Vector3f(cur).add(new Vector3f(cur).sub(prev).mul(1 - sj.drag))
						.fma(sj.stiffness * dt, axis)
						.fma(sj.gravityPower * dt, sj.gravityDir);
				next.sub(head);
				if (next.lengthSquared() < 1e-12f) {
					next.set(axis);
				}
				next.normalize(length).add(head);
				for (int g : sj.colliderGroups) {
					if (g < 0 || g >= centres.length) {
						continue;
					}
					for (int c = 0; c < centres[g].length; c++) {
						float r = radii[g][c] + sj.hitRadius * d.scale;
						Vector3f away = new Vector3f(next).sub(centres[g][c]);
						float dist = away.length();
						if (dist < r && dist > 1e-6f) {
							next.set(centres[g][c]).fma(r / dist, away);
							next.sub(head).normalize(length).add(head);
						}
					}
				}
				prev.set(cur);
				cur.set(next);
				Vector3f to = new Vector3f(next).sub(head).normalize();
				Quaternionf swing = new Quaternionf().rotationTo(axis, to);
				Quaternionf worldRot = swing.mul(rest);
				local[i].set(parentRot).invert().mul(worldRot).normalize();
			}
			if (p >= 0) {
				world[i].set(world[p]).mul(scratch.translationRotateScale(t, local[i], m.restS[i]));
			}
		}
		springReady = true;
	}
	private int jVersion = -1;
	private float[][] jPaint;
	private int[][] jSpringOfJoint;
	private int[] jNode = new int[0];
	private Vector3f[] jAnchor = new Vector3f[0];
	private Vector3f[] jPos = new Vector3f[0];
	private Vector3f[] jVel = new Vector3f[0];
	private Vector3f[] jPrevAnchor = new Vector3f[0];
	private Vector3f[] jDisp = new Vector3f[0];
	private boolean jiggleOn;
	private boolean jReady;
	private double jx;
	private double jy;
	private double jz;
	private void buildJiggle(JigglePaint paint) {
		jVersion = paint.version();
		jPaint = paint.weights;
		Matrix4f[] rest = m.restWorld();
		Map<Integer, Integer> springOfNode = new HashMap<>();
		java.util.List<Vector3f> sums = new java.util.ArrayList<>();
		java.util.List<float[]> counts = new java.util.ArrayList<>();
		jSpringOfJoint = new int[m.sets.size()][];
		Vector3f p = new Vector3f();
		for (int s = 0; s < m.sets.size(); s++) {
			VrmModel.VertexSet set = m.sets.get(s);
			float[] w = s < jPaint.length ? jPaint[s] : null;
			int[] of = new int[set.skinNodes.length];
			java.util.Arrays.fill(of, -1);
			jSpringOfJoint[s] = of;
			if (w == null) {
				continue;
			}
			int count = set.pos.length / 3;
			for (int v = 0; v < count && v < w.length; v++) {
				if (w[v] <= 0) {
					continue;
				}
				for (int k = 0; k < 4; k++) {
					float sw = set.weights[v * 4 + k];
					if (sw <= 0.05f) {
						continue;
					}
					int j = Math.min(set.joints[v * 4 + k], set.skinNodes.length - 1);
					int node = set.skinNodes[j];
					Integer sp = springOfNode.get(node);
					if (sp == null) {
						sp = sums.size();
						springOfNode.put(node, sp);
						sums.add(new Vector3f());
						counts.add(new float[1]);
					}
					of[j] = sp;
					new Matrix4f(rest[node]).mul(set.inverseBind[j]).transformPosition(set.pos[v * 3], set.pos[v * 3 + 1], set.pos[v * 3 + 2], p);
					sums.get(sp).fma(w[v] * sw, p);
					counts.get(sp)[0] += w[v] * sw;
				}
			}
		}
		for (int s = 0; s < m.sets.size(); s++) {
			VrmModel.VertexSet set = m.sets.get(s);
			for (int j = 0; j < set.skinNodes.length; j++) {
				Integer sp = springOfNode.get(set.skinNodes[j]);
				if (sp != null) {
					jSpringOfJoint[s][j] = sp;
				}
			}
		}
		int n = sums.size();
		jNode = new int[n];
		jAnchor = new Vector3f[n];
		jPos = new Vector3f[n];
		jVel = new Vector3f[n];
		jPrevAnchor = new Vector3f[n];
		jDisp = new Vector3f[n];
		for (Map.Entry<Integer, Integer> e : springOfNode.entrySet()) {
			int sp = e.getValue();
			jNode[sp] = e.getKey();
			Vector3f centre = sums.get(sp).div(Math.max(1e-6f, counts.get(sp)[0]));
			jAnchor[sp] = new Matrix4f(rest[e.getKey()]).invert().transformPosition(centre);
			jPos[sp] = new Vector3f();
			jVel[sp] = new Vector3f();
			jPrevAnchor[sp] = new Vector3f();
			jDisp[sp] = new Vector3f();
		}
		jReady = false;
	}
	private void jiggle(Drive d) {
		JigglePaint paint = d.jiggle;
		jiggleOn = paint != null && d.jiggleMotion && paint.hasJiggle() && paint.amount > 0;
		if (!jiggleOn) {
			jReady = false;
			return;
		}
		if (paint.version() != jVersion || jPaint != paint.weights) {
			buildJiggle(paint);
		}
		if (jNode.length == 0) {
			jiggleOn = false;
			return;
		}
		Matrix4f frame = physicsFrame(d);
		Matrix4f back = new Matrix4f(frame).invert();
		float mx = (float) (d.x - jx);
		float my = (float) (d.y - jy);
		float mz = (float) (d.z - jz);
		boolean reset = !jReady || mx * mx + my * my + mz * mz > 16;
		jx = d.x;
		jy = d.y;
		jz = d.z;
		float dt = Math.max(1e-4f, Math.min(d.dt, 0.05f));
		float stiff = paint.stiffness;
		float k = 60 + 1400 * stiff * stiff;
		float c = 2 * (float) Math.sqrt(k) * (0.08f + 0.9f * paint.damping);
		int steps = Math.max(1, (int) Math.ceil(dt / (1 / 120f)));
		float h = dt / steps;
		float limit = 0.15f;
		for (int i = 0; i < jNode.length; i++) {
			Vector3f anchor = new Matrix4f(frame).mul(world[jNode[i]]).transformPosition(new Vector3f(jAnchor[i]));
			Vector3f pos = jPos[i];
			Vector3f vel = jVel[i];
			if (reset) {
				pos.set(anchor);
				vel.set(0);
				jPrevAnchor[i].set(anchor);
			} else {
				pos.sub(mx * 0.3f, my * 0.3f, mz * 0.3f);
				jPrevAnchor[i].sub(mx * 0.3f, my * 0.3f, mz * 0.3f);
			}
			Vector3f anchorVel = new Vector3f(anchor).sub(jPrevAnchor[i]).div(dt);
			Vector3f from = new Vector3f(jPrevAnchor[i]);
			for (int st = 1; st <= steps; st++) {
				Vector3f a = new Vector3f(from).lerp(anchor, st / (float) steps);
				Vector3f acc = new Vector3f(a).sub(pos).mul(k).fma(-c, new Vector3f(vel).sub(anchorVel));
				vel.fma(h, acc);
				pos.fma(h, vel);
			}
			Vector3f off = new Vector3f(pos).sub(anchor);
			float len = off.length();
			if (len > limit * 2) {
				off.normalize(limit * 2);
				pos.set(anchor).add(off);
				len = limit * 2;
			}
			if (len > 1e-6f) {
				off.mul(limit * (float) Math.tanh(len / limit) / len);
			}
			jPrevAnchor[i].set(anchor);
			back.transformDirection(off, jDisp[i]).mul(paint.amount);
		}
		jReady = true;
	}
	public float seamGap(JigglePaint paint) {
		if (paint == null) {
			return 0;
		}
		float max = 0;
		for (int id = 0; id < paint.points; id++) {
			int s0 = paint.firstSet(id);
			int v0 = paint.firstVertex(id);
			if (paint.weights[s0][v0] <= 0 && paint.mass[s0][v0] <= 0) {
				continue;
			}
			float[] gap = {0};
			float x = skinPos[s0][v0 * 3], y = skinPos[s0][v0 * 3 + 1], z = skinPos[s0][v0 * 3 + 2];
			paint.forEachCopy(id, (s, v) -> {
				float dx = skinPos[s][v * 3] - x, dy = skinPos[s][v * 3 + 1] - y, dz = skinPos[s][v * 3 + 2] - z;
				gap[0] = Math.max(gap[0], (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
			});
			max = Math.max(max, gap[0]);
		}
		return max;
	}
	public float jiggleReach() {
		float max = 0;
		for (Vector3f v : jDisp) {
			if (v != null) {
				max = Math.max(max, v.length());
			}
		}
		return max;
	}
	private void skin() {
		Vector3f p = new Vector3f();
		for (int s = 0; s < m.sets.size(); s++) {
			VrmModel.VertexSet set = m.sets.get(s);
			Matrix4f[] joints = jointMats[s];
			for (int j = 0; j < joints.length; j++) {
				joints[j].set(world[set.skinNodes[j]]).mul(set.inverseBind[j]);
			}
			float[] pos = set.pos;
			float[] nrm = set.nrm;
			float[] outP = skinPos[s];
			float[] outN = skinNrm[s];
			float[] blinkDelta = blink > 0.01f ? set.blink : null;
			int count = pos.length / 3;
			for (int v = 0; v < count; v++) {
				float px = pos[v * 3], py = pos[v * 3 + 1], pz = pos[v * 3 + 2];
				if (blinkDelta != null) {
					px += blinkDelta[v * 3] * blink;
					py += blinkDelta[v * 3 + 1] * blink;
					pz += blinkDelta[v * 3 + 2] * blink;
				}
				float nx = nrm[v * 3], ny = nrm[v * 3 + 1], nz = nrm[v * 3 + 2];
				float ax = 0, ay = 0, az = 0, bx = 0, by = 0, bz = 0;
				for (int k = 0; k < 4; k++) {
					float w = set.weights[v * 4 + k];
					if (w <= 0) {
						continue;
					}
					Matrix4f j = joints[Math.min(set.joints[v * 4 + k], joints.length - 1)];
					ax += w * (j.m00() * px + j.m10() * py + j.m20() * pz + j.m30());
					ay += w * (j.m01() * px + j.m11() * py + j.m21() * pz + j.m31());
					az += w * (j.m02() * px + j.m12() * py + j.m22() * pz + j.m32());
					bx += w * (j.m00() * nx + j.m10() * ny + j.m20() * nz);
					by += w * (j.m01() * nx + j.m11() * ny + j.m21() * nz);
					bz += w * (j.m02() * nx + j.m12() * ny + j.m22() * nz);
				}
				outP[v * 3] = ax;
				outP[v * 3 + 1] = ay;
				outP[v * 3 + 2] = az;
				float len = (float) Math.sqrt(bx * bx + by * by + bz * bz);
				if (len > 1e-6f) {
					bx /= len;
					by /= len;
					bz /= len;
				}
				outN[v * 3] = bx;
				outN[v * 3 + 1] = by;
				outN[v * 3 + 2] = bz;
			}
		}
	}
	private void blink(float dt) {
		blinkTimer -= dt;
		if (blinkTimer <= 0) {
			float t = -blinkTimer;
			blink = t < 0.06f ? t / 0.06f : t < 0.16f ? 1 - (t - 0.06f) / 0.1f : 0;
			if (t >= 0.16f) {
				blinkTimer = 2 + (float) Math.random() * 4;
				blink = 0;
			}
		}
	}
	public void update(Drive d) {
		lastSeen = System.nanoTime();
		pose(d);
		worldPass();
		if (d.physics) {
			springs(d);
		} else {
			springReady = false;
		}
		blink(d.dt);
		jiggle(d);
		skin();
		shape(d.jiggle);
	}
	private float[] pointOffset = new float[0];
	private boolean[] pointMoved = new boolean[0];
	private void shape(JigglePaint paint) {
		if (paint == null || !(jiggleOn || paint.hasMass())) {
			return;
		}
		int n = paint.points;
		if (pointOffset.length != n * 3) {
			pointOffset = new float[n * 3];
			pointMoved = new boolean[n];
		}
		java.util.Arrays.fill(pointOffset, 0);
		java.util.Arrays.fill(pointMoved, false);
		boolean jig = jiggleOn && jSpringOfJoint != null;
		for (int id = 0; id < n; id++) {
			int s = paint.firstSet(id);
			int v = paint.firstVertex(id);
			float mass = paint.mass[s][v];
			float w = jig ? paint.weights[s][v] : 0;
			if (mass <= 0 && w <= 0) {
				continue;
			}
			pointMoved[id] = true;
			if (mass > 0) {
				pointNormal(paint, id, scratchNormal);
				float push = mass * JigglePaint.MAX_MASS;
				pointOffset[id * 3] += scratchNormal.x * push;
				pointOffset[id * 3 + 1] += scratchNormal.y * push;
				pointOffset[id * 3 + 2] += scratchNormal.z * push;
			}
			if (w > 0) {
				VrmModel.VertexSet set = m.sets.get(s);
				int[] springOf = jSpringOfJoint[s];
				for (int k = 0; k < 4; k++) {
					float sw = set.weights[v * 4 + k];
					int sp = sw > 0 ? springOf[Math.min(set.joints[v * 4 + k], springOf.length - 1)] : -1;
					if (sp >= 0) {
						Vector3f o = jDisp[sp];
						pointOffset[id * 3] += sw * w * o.x;
						pointOffset[id * 3 + 1] += sw * w * o.y;
						pointOffset[id * 3 + 2] += sw * w * o.z;
					}
				}
			}
		}
		for (int s = 0; s < skinPos.length; s++) {
			int[] ids = paint.point[s];
			float[] outP = skinPos[s];
			for (int v = 0; v < ids.length; v++) {
				int id = ids[v];
				if (pointMoved[id]) {
					outP[v * 3] += pointOffset[id * 3];
					outP[v * 3 + 1] += pointOffset[id * 3 + 1];
					outP[v * 3 + 2] += pointOffset[id * 3 + 2];
				}
			}
		}
	}
	private final Vector3f scratchNormal = new Vector3f();
	private void pointNormal(JigglePaint paint, int id, Vector3f out) {
		out.set(0);
		paint.forEachCopy(id, (s, v) -> out.add(skinNrm[s][v * 3], skinNrm[s][v * 3 + 1], skinNrm[s][v * 3 + 2]));
		if (out.lengthSquared() > 1e-12f) {
			out.normalize();
		}
	}
	public void submit(Drive d, PoseStack pose, SubmitNodeCollector collector, int light, int overlay) {
		boolean anime = d.anime;
		for (int mat : avatar.drawOrder) {
			VrmModel.Material material = m.materials.get(mat);
			int[] prims = avatar.primsOf[mat];
			int color = material.color();
			collector.submitCustomGeometry(pose, avatar.type(mat), (pp, vc) -> {
				long began = EMIT.begin();
				for (int pi : prims) {
					emit(pp, vc, m.prims.get(pi), color, light, overlay, anime, 0, false);
				}
				EMIT.end(began);
			});
		}
		if (d.outline) {
			for (int mat : avatar.drawOrder) {
				VrmModel.Material material = m.materials.get(mat);
				if (material.outline() <= 0 || material.mode() == 2) {
					continue;
				}
				int[] prims = avatar.primsOf[mat];
				float width = material.outline();
				collector.submitCustomGeometry(pose, avatar.outline(mat), (pp, vc) -> {
					long began = EMIT.begin();
					for (int pi : prims) {
						emit(pp, vc, m.prims.get(pi), 0xFF2A2026, light, overlay, true, width, true);
					}
					EMIT.end(began);
				});
			}
		}
	}
	private int[][][] armQuads;
	private boolean armPosed;
	public boolean submitArm(int side, PoseStack pose, SubmitNodeCollector collector, int light, int overlay, boolean anime,
			boolean outline, float scale, float wristX, float wristY) {
		String s = side == 0 ? "right" : "left";
		Integer upperArm = m.human.get(s + "UpperArm");
		Integer lowerArm = m.human.get(s + "LowerArm");
		Integer hand = m.human.get(s + "Hand");
		if (upperArm == null || lowerArm == null || hand == null) {
			return false;
		}
		if (!armPosed) {
			resetPose();
			relaxHands(new boolean[2]);
			worldPass();
			blink = 0;
			skin();
			armPosed = true;
		}
		if (armQuads == null) {
			armQuads = new int[][][] {armQuads(m.human.get("rightUpperArm")), armQuads(m.human.get("leftUpperArm"))};
		}
		Vector3f wrist = worldPos(hand);
		Vector3f along = new Vector3f(wrist).sub(worldPos(lowerArm));
		Vector3f palm = new Vector3f();
		Vector3f handAlong = new Vector3f();
		Vector3f thumb = new Vector3f();
		if (along.lengthSquared() < 1e-10f || !handFrame(side, palm, handAlong, thumb)) {
			return false;
		}
		along.normalize();
		thumb.fma(-thumb.dot(along), along);
		if (thumb.lengthSquared() < 1e-10f) {
			return false;
		}
		thumb.normalize();
		Vector3f third = new Vector3f(along).cross(thumb);
		Matrix3f avatarAxes = new Matrix3f().setColumn(0, along).setColumn(1, thumb).setColumn(2, third);
		Matrix3f partAxes = new Matrix3f().setColumn(0, new Vector3f(0, 1, 0)).setColumn(1, new Vector3f(0, 0, -1))
				.setColumn(2, new Vector3f(-1, 0, 0));
		Matrix3f turn = partAxes.mul(avatarAxes.transpose());
		Matrix4f place = new Matrix4f().translation(wristX, wristY, 0).scale(scale).mul(new Matrix4f().set(turn))
				.translate(-wrist.x, -wrist.y, -wrist.z);
		pose.pushPose();
		pose.last().pose().mul(place);
		pose.last().normal().mul(turn);
		int[][] keep = armQuads[side];
		for (int mat : avatar.drawOrder) {
			int[] prims = avatar.primsOf[mat];
			int color = m.materials.get(mat).color();
			collector.submitCustomGeometry(pose, avatar.type(mat), (pp, vc) -> {
				for (int pi : prims) {
					if (keep[pi].length > 0) {
						emitQuads(pp, vc, m.prims.get(pi).set(), keep[pi], color, light, overlay, anime, 0, false);
					}
				}
			});
		}
		if (outline) {
			for (int mat : avatar.drawOrder) {
				VrmModel.Material material = m.materials.get(mat);
				if (material.outline() <= 0 || material.mode() == 2) {
					continue;
				}
				int[] prims = avatar.primsOf[mat];
				float width = material.outline();
				collector.submitCustomGeometry(pose, avatar.outline(mat), (pp, vc) -> {
					for (int pi : prims) {
						if (keep[pi].length > 0) {
							emitQuads(pp, vc, m.prims.get(pi).set(), keep[pi], 0xFF2A2026, light, overlay, true, width, true);
						}
					}
				});
			}
		}
		pose.popPose();
		return true;
	}
	private int[][] armQuads(Integer root) {
		boolean[] under = new boolean[m.nodeCount];
		if (root != null) {
			for (int n = 0; n < m.nodeCount; n++) {
				for (int p = n; p >= 0; p = m.parent[p]) {
					if (p == root) {
						under[n] = true;
						break;
					}
				}
			}
		}
		boolean[][] keep = new boolean[m.sets.size()][];
		for (int si = 0; si < keep.length; si++) {
			VrmModel.VertexSet set = m.sets.get(si);
			int count = set.pos.length / 3;
			keep[si] = new boolean[count];
			for (int v = 0; v < count; v++) {
				float w = 0;
				for (int k = 0; k < 4; k++) {
					int j = Math.min(set.joints[v * 4 + k], set.skinNodes.length - 1);
					if (set.weights[v * 4 + k] > 0 && under[set.skinNodes[j]]) {
						w += set.weights[v * 4 + k];
					}
				}
				keep[si][v] = w >= 0.5f;
			}
		}
		int[][] out = new int[m.prims.size()][];
		for (int pi = 0; pi < out.length; pi++) {
			VrmModel.Prim prim = m.prims.get(pi);
			boolean[] k = keep[prim.set()];
			int[] q = prim.quads();
			int[] kept = new int[q.length];
			int o = 0;
			for (int i = 0; i + 3 < q.length; i += 4) {
				if (k[q[i]] && k[q[i + 1]] && k[q[i + 2]] && k[q[i + 3]]) {
					System.arraycopy(q, i, kept, o, 4);
					o += 4;
				}
			}
			out[pi] = java.util.Arrays.copyOf(kept, o);
		}
		return out;
	}
	private void emit(PoseStack.Pose pp, VertexConsumer vc, VrmModel.Prim prim, int color, int light, int overlay, boolean anime,
			float push, boolean flip) {
		emitQuads(pp, vc, prim.set(), prim.quads(), color, light, overlay, anime, push, flip);
	}
	private void emitQuads(PoseStack.Pose pp, VertexConsumer vc, int setIndex, int[] q, int color, int light, int overlay,
			boolean anime, float push, boolean flip) {
		float[] pos = skinPos[setIndex];
		float[] nrm = skinNrm[setIndex];
		float[] uv = m.sets.get(setIndex).uv;
		org.joml.Matrix4f mat = pp.pose();
		org.joml.Matrix3f nm = pp.normal();
		Vector3f up = anime ? nm.transform(new Vector3f(0, 1, 0)).normalize() : null;
		Vector3f p = new Vector3f();
		Vector3f n = new Vector3f();
		for (int k = 0; k + 3 < q.length; k += 4) {
			for (int c = 0; c < 4; c++) {
				int i = q[k + (flip ? (4 - c) & 3 : c)];
				float nx = nrm[i * 3], ny = nrm[i * 3 + 1], nz = nrm[i * 3 + 2];
				mat.transformPosition(pos[i * 3] + nx * push, pos[i * 3 + 1] + ny * push, pos[i * 3 + 2] + nz * push, p);
				if (up != null) {
					n.set(up);
				} else {
					nm.transform(nx, ny, nz, n).normalize();
				}
				vc.addVertex(p.x, p.y, p.z, color, uv[i * 2], uv[i * 2 + 1], overlay, light, n.x, n.y, n.z);
			}
		}
	}
}
