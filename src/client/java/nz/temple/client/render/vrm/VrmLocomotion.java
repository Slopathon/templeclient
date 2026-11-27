package nz.temple.client.render.vrm;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class VrmLocomotion {
	private static final float DEG = (float) (Math.PI / 180);
	private static final float TAU = (float) (Math.PI * 2);
	private static final String[] SPINE = {"spine", "chest", "upperChest"};
	private static final float[] SPINE_SHARE = {0.4f, 0.35f, 0.25f};
	private final VrmRig rig;
	private final VrmModel m;
	private final Vector3f fwd;
	private final Vector3f right;
	private final Vector3f up = new Vector3f(0, 1, 0);
	private final int[] upper = new int[2];
	private final int[] lower = new int[2];
	private final int[] foot = new int[2];
	private final float[] l1 = new float[2];
	private final float[] l2 = new float[2];
	private final Vector3f[] restHip = new Vector3f[2];
	private final Vector3f[] restAnkle = new Vector3f[2];
	private final Vector3f[] restThighDir = new Vector3f[2];
	private final Vector3f[] restShinDir = new Vector3f[2];
	private final boolean legs;
	private final float legLength;
	private float phase;
	private float speed;
	private float sprint;
	private float sneak;
	private float move;
	private float dirX;
	private float dirY = 1;
	private float easedX;
	private float easedY = 1;
	private float dirSettled = 1;
	private float air;
	private float attackW;
	private float attackP = 1;
	private int attackSide;
	private float airTime;
	private float land;
	private float fallSpeed;
	private boolean grounded = true;
	private float fly;
	private float rootYaw = Float.NaN;
	private float lastRootYaw;
	private float yawRate;
	private boolean turning;
	private float plantedYaw;
	private float stepFrom;
	private float stepT = -1;
	private int stepLead;
	private float lean;
	private float roll;
	private float lastForward;
	private float accel;
	private float time;
	private final float[] groundOff = new float[2];
	VrmLocomotion(VrmRig rig, VrmModel m) {
		this.rig = rig;
		this.m = m;
		fwd = m.vrm1 ? new Vector3f(0, 0, 1) : new Vector3f(0, 0, -1);
		right = m.vrm1 ? new Vector3f(-1, 0, 0) : new Vector3f(1, 0, 0);
		var rest = m.restWorld();
		boolean ok = true;
		float len = 0;
		for (int i = 0; i < 2; i++) {
			String side = i == 0 ? "right" : "left";
			Integer u = m.human.get(side + "UpperLeg");
			Integer l = m.human.get(side + "LowerLeg");
			Integer f = m.human.get(side + "Foot");
			if (u == null || l == null || f == null) {
				ok = false;
				continue;
			}
			upper[i] = u;
			lower[i] = l;
			foot[i] = f;
			restHip[i] = rest[u].getTranslation(new Vector3f());
			Vector3f knee = rest[l].getTranslation(new Vector3f());
			restAnkle[i] = rest[f].getTranslation(new Vector3f());
			l1[i] = knee.distance(restHip[i]);
			l2[i] = restAnkle[i].distance(knee);
			if (l1[i] < 1e-3f || l2[i] < 1e-3f) {
				ok = false;
				continue;
			}
			restThighDir[i] = new Vector3f(knee).sub(restHip[i]).normalize();
			restShinDir[i] = new Vector3f(restAnkle[i]).sub(knee).normalize();
			len += (l1[i] + l2[i]) / 2;
		}
		legs = ok;
		legLength = ok ? len : 0.8f;
	}
	private static float clamp(float v, float lo, float hi) {
		return Math.max(lo, Math.min(hi, v));
	}
	private static float smooth(float e0, float e1, float v) {
		float t = clamp((v - e0) / (e1 - e0), 0, 1);
		return t * t * (3 - 2 * t);
	}
	private static float wrap(float deg) {
		deg %= 360;
		return deg >= 180 ? deg - 360 : deg < -180 ? deg + 360 : deg;
	}
	private static float approach(float cur, float target, float rate, float dt) {
		return cur + (target - cur) * (1 - (float) Math.exp(-rate * dt));
	}
	void apply(VrmRig.Drive d) {
		float dt = clamp(d.dt, 1e-4f, 0.1f);
		time += dt;
		float bpu = Math.max(0.05f, d.scale);
		float hs = (float) Math.sqrt(d.vx * d.vx + d.vz * d.vz);
		boolean lying = d.swimming || d.fallFlying || d.sleeping;
		boolean special = lying || d.passenger;
		if (Float.isNaN(rootYaw) || special) {
			rootYaw = d.bodyYaw;
			plantedYaw = rootYaw;
			stepT = -1;
			turning = false;
		} else {
			float diff = wrap(d.headYaw - rootYaw);
			if (hs > 0.4f || !d.grounded) {
				rootYaw += diff * (1 - (float) Math.exp(-10 * dt));
				turning = false;
			} else {
				if (Math.abs(diff) > 50) {
					turning = true;
				}
				if (turning) {
					rootYaw += clamp(diff * 7 * dt, -320 * dt, 320 * dt);
					turning = Math.abs(diff) > 3;
				}
				diff = wrap(d.headYaw - rootYaw);
				if (Math.abs(diff) > 75) {
					rootYaw = d.headYaw - Math.signum(diff) * 75;
				}
			}
		}
		rootYaw = wrap(rootYaw);
		yawRate = approach(yawRate, wrap(rootYaw - lastRootYaw) / dt, 8, dt);
		lastRootYaw = rootYaw;
		d.yawOut = wrap(d.bodyYaw - rootYaw);
		float yaw = rootYaw * DEG;
		float sin = (float) Math.sin(yaw);
		float cos = (float) Math.cos(yaw);
		float vf = -d.vx * sin + d.vz * cos;
		float vr = -d.vx * cos - d.vz * sin;
		accel = approach(accel, (vf - lastForward) / dt, 6, dt);
		lastForward = vf;
		speed = approach(speed, hs / bpu, 10, dt);
		if (hs > 0.05f) {
			easedX = approach(easedX, vr / hs, 6, dt);
			easedY = approach(easedY, vf / hs, 6, dt);
			float n = (float) Math.sqrt(easedX * easedX + easedY * easedY);
			if (n > 0.15f) {
				dirX = easedX / n;
				dirY = easedY / n;
			}
			dirSettled = clamp(n, 0, 1);
		}
		if (d.grounded && !grounded && airTime > 0.25f) {
			land = clamp(0.3f + fallSpeed / 12, 0.3f, 1);
		}
		grounded = d.grounded;
		airTime = grounded ? 0 : airTime + dt;
		if (!grounded) {
			fallSpeed = Math.max(0, -d.vy);
		}
		land *= (float) Math.exp(-5 * dt);
		air = approach(air, grounded || special ? 0 : 1, 10, dt);
		fly = approach(fly, d.flying && !grounded ? 1 : 0, 6, dt);
		sneak = approach(sneak, d.sneaking ? 1 : 0, 5, dt);
		sprint = approach(sprint, d.sneaking ? 0 : smooth(4.7f, 5.4f, hs), 3.5f, dt);
		move = approach(move, grounded ? smooth(0.08f, 0.9f, hs) : 0, 6, dt);
		boolean swinging = d.swing > 0 && !d.busy[d.swingSide];
		if (swinging) {
			attackP = d.swing;
			attackSide = d.swingSide;
		} else {
			attackP = Math.min(1, attackP + dt * 3);
		}
		attackW = approach(attackW, swinging ? 1 : 0, swinging ? 16 : 4, dt);
		rig.resetPose();
		if (d.swimming) {
			swimPose(d, hs);
		} else if (d.fallFlying) {
			glidePose();
		} else if (d.passenger) {
			sitPose(d);
		} else if (d.sleeping) {
			armsDown(80, 8, 8);
		} else {
			stand(d, hs, bpu, dt);
		}
		rig.relaxHands(d.holding);
	}
	private void stand(VrmRig.Drive d, float hs, float bpu, float dt) {
		float Lg = legLength;
		float sin = (float) Math.sin(rootYaw * DEG);
		float cos = (float) Math.cos(rootYaw * DEG);
		float back = Math.max(0, -easedY) * move;

		float walkC = 0.8f + 0.16f * hs;
		float runC = 1.45f + 0.08f * hs;
		float sneakC = 0.75f + 0.2f * hs;
		float c = lerp(lerp(walkC, runC, sprint), sneakC, sneak);
		float f = lerp(clamp(0.64f - 0.035f * hs, 0.5f, 0.64f), 0.36f, sprint);
		if (move > 0.02f) {
			phase = (phase + c * dt) % 1;
		}
		float hr = lerp(0.985f, lerp(0.95f - 0.006f * hs, 0.89f, sprint), move);
		hr = lerp(hr, 0.74f, sneak) - land * 0.12f;
		hr = lerp(hr, 0.97f, air);
		float reach = (float) Math.sqrt(Math.max(0, 0.98f * 0.98f - hr * hr)) * Lg;
		float travel = Math.min(speed * f / Math.max(0.3f, c), 1.8f * reach) * move * dirSettled;
		float lift = Lg * (0.09f + 0.07f * sprint - 0.03f * sneak) * move;
		float[] footYaw = new float[2];
		float[] stepLift = new float[2];
		if (move > 0.3f || air > 0.5f) {
			plantedYaw = rootYaw;
			stepT = -1;
		} else {
			float twist = wrap(rootYaw - plantedYaw);
			if (stepT < 0 && Math.abs(twist) > 22) {
				stepT = 0;
				stepFrom = plantedYaw;
				stepLead = twist > 0 ? 0 : 1;
			}
		}
		for (int i = 0; i < 2; i++) {
			if (stepT >= 0) {
				float start = i == stepLead ? 0 : 0.4f;
				float pr = clamp((stepT - start) / 0.6f, 0, 1);
				footYaw[i] = stepFrom + wrap(rootYaw - stepFrom) * pr * pr * (3 - 2 * pr);
				stepLift[i] = 0.07f * Lg * (float) Math.sin(Math.PI * pr);
			} else {
				footYaw[i] = plantedYaw;
			}
		}
		if (stepT >= 0) {
			stepT += dt / 0.42f;
			if (stepT >= 1) {
				stepT = -1;
				plantedYaw = rootYaw;
			}
		}
		float sideways = clamp(easedX, -1, 1) * (1 - 2 * smooth(-0.05f, -0.45f, easedY));
		float pelvisYaw = (sideways * 30 * (1 - 0.3f * sprint) + 5 * (float) Math.sin(TAU * phase)) * move;
		float travelYaw = pelvisYaw * 0.7f;
		Vector3f dirModel = new Vector3f(right).mul(dirX).fma(dirY, fwd);
		Vector3f[] target = new Vector3f[2];
		float[] pitch = new float[2];
		float rising = clamp(d.vy / 3, -1, 1);
		for (int i = 0; i < 2; i++) {
			float side = i == 0 ? 1 : -1;
			float q = (phase + (i == 0 ? 0.5f : 0)) % 1;
			Vector3f n = restAnkle[i] != null ? new Vector3f(restAnkle[i]) : new Vector3f(right).mul(side * 0.1f);
			float lateral = n.dot(right);
			n.fma(-lateral * (1 - lerp(1, lerp(0.8f, 0.6f, sprint), move)), right);
			float along;
			float upAmt = 0;
			float cross = 0;
			if (q < f) {
				along = travel * (0.5f - q / f);
			} else {
				float s = (q - f) / (1 - f);
				along = travel * (-0.5f + s * s * (3 - 2 * s));
				upAmt = lift * (float) Math.sin(Math.PI * s);
				pitch[i] = -18 * (float) Math.sin(Math.PI * s) * move;
				cross = 0.16f * Lg * clamp(-easedX * side, 0, 1) * (float) Math.sin(Math.PI * s) * move;
			}
			Vector3f p = new Vector3f(n).fma(along, dirModel).fma(upAmt + stepLift[i], up).fma(cross, fwd);
			boolean lead = q >= f;
			float tuck = lead ? 0.28f : 0.08f;
			float reachFwd = lead ? 0.12f : -0.06f;
			float hang = 0.05f + 0.03f * side;
			Vector3f a = new Vector3f(n)
					.fma(Lg * lerp(hang, tuck, Math.max(0, rising)), up)
					.fma(Lg * lerp(lead ? 0.05f : 0, reachFwd, Math.max(0, rising)), fwd);
			Vector3f fl = new Vector3f(n).fma(Lg * (0.06f + 0.04f * side), up)
					.fma(Lg * (-0.06f + 0.03f * (float) Math.sin(time * 1.3f + i * 2)), fwd);
			a.lerp(fl, fly);
			p.lerp(a, air);
			float yawOff = wrap(footYaw[i] - rootYaw) - travelYaw;
			p.rotateY(-yawOff * DEG);
			float probe = 0;
			if (d.ground != null && air < 0.5f) {
				float pf = p.dot(fwd) * bpu;
				float pr = p.dot(right) * bpu;
				double wx = d.x + (-sin) * pf + (-cos) * pr;
				double wz = d.z + cos * pf + (-sin) * pr;
				float h = d.ground.height(wx, d.y, wz);
				if (!Float.isNaN(h)) {
					probe = clamp(h, -0.5f, 0.55f) / bpu;
				}
			}
			groundOff[i] = approach(groundOff[i], probe, 18, dt);
			p.y += groundOff[i] * (1 - air);
			target[i] = p;
			footYaw[i] = yawOff;
		}
		float standing = restHip[0] != null ? (restHip[0].y + restHip[1].y) / 2 - (restAnkle[0].y + restAnkle[1].y) / 2 : Lg;
		float ankleY = restAnkle[0] != null ? (restAnkle[0].y + restAnkle[1].y) / 2 : 0;
		float hipRest = ankleY + standing;
		float bob = -0.02f * Lg * move * (1 - sprint) * (0.5f + 0.5f * (float) Math.cos(2 * TAU * phase))
				- 0.04f * Lg * move * sprint * (0.5f + 0.5f * (float) Math.cos(2 * TAU * (phase - f / 2)));
		float breathe = 0.004f * Lg * (float) Math.sin(time * 1.6f) * (1 - move);
		float hipY = ankleY + standing * hr / 0.985f + bob + breathe;
		float sway = -0.018f * Lg * (float) Math.sin(TAU * phase) * move * (1 - sprint) + 0.006f * Lg * (float) Math.sin(time * 0.6f) * (1 - move);
		float shift = -0.1f * Lg * sneak + 0.03f * Lg * sprint * move;
		if (legs) {
			for (int i = 0; i < 2; i++) {
				Vector3f h = new Vector3f(restHip[i]).fma(sway, right).fma(shift, fwd);
				float dx = target[i].x - h.x;
				float dz = target[i].z - h.z;
				float reachMax = 0.995f * (l1[i] + l2[i]);
				float top = target[i].y + (float) Math.sqrt(Math.max(0, reachMax * reachMax - dx * dx - dz * dz));
				hipY = Math.min(hipY, top - (restHip[i].y - hipRest));
			}
		}
		rig.hipsOffset.set(right).mul(sway).fma(shift, fwd).add(0, hipY - hipRest, 0);
		float leanTarget = 3 + 5 * move + 9 * sprint * move + 24 * sneak + clamp(accel * 1.2f, -6, 8) - 6 * back + land * 12;
		lean = approach(lean, leanTarget, 5, dt);
		roll = approach(roll, clamp(yawRate * hs * 0.006f, -12, 12), 6, dt);
		float walkTwist = -5 * (float) Math.sin(TAU * phase) * move;
		float strike = strike() * attackW;
		float swingTwist = (attackSide == 0 ? 1 : -1) * (6 + 14 * strike) * attackW;
		float bodyLean = lean + 7 * strike;
		rig.setBone("hips", new Quaternionf().rotationY(-pelvisYaw * DEG).rotateX(-(4 * sneak) * DEG));
		float breath = 1.2f * (float) Math.sin(time * 1.6f);
		for (int k = 0; k < SPINE.length; k++) {
			float share = SPINE_SHARE[k];
			float yawK = (pelvisYaw - walkTwist * 1.5f + swingTwist) * share;
			float pitchK = (bodyLean - 4 * sneak) * share + (k == 1 ? breath * (1 - move) : 0);
			rig.setBone(SPINE[k], new Quaternionf().rotationY(yawK * DEG).rotateX(-pitchK * DEG).rotateZ(-roll * share * DEG));
		}
		look(d, bodyLean - 4 * sneak);
		float swing = (16 + 26 * sprint) * move * (1 - 0.6f * sneak) * (0.6f + 0.4f * Math.min(1, hs / 4.3f));
		float armR = swing * (float) Math.cos(TAU * phase) + 5 * sprint * move + 12 * sneak;
		float elbow = 10 + 14 * move + 70 * sprint * move + 25 * sneak;
		float falling = air * Math.max(0, -clamp(d.vy / 4, -1, 0)) + 0.3f * air;
		float hang = 74 - 18 * Math.min(1, falling) * (1 - fly) - 8 * fly;
		setArm(0, armR, hang, elbow + 18 * sprint * move * Math.max(0, (float) Math.cos(TAU * phase)));
		setArm(1, -armR + 2 * (5 * sprint * move + 12 * sneak), hang, elbow + 18 * sprint * move * Math.max(0, -(float) Math.cos(TAU * phase)));
		swingArm(d, bodyLean - 4 * sneak);
		if (legs) {
			rig.worldPass();
			for (int i = 0; i < 2; i++) {
				solveLeg(i, target[i], footYaw[i] * -DEG, pitch[i] * DEG);
			}
		} else {
			for (int i = 0; i < 2; i++) {
				float q = (phase + (i == 0 ? 0.5f : 0)) % 1;
				float a = (float) Math.atan2(target[i].dot(fwd), Lg) / DEG;
				rig.setBone(i == 0 ? "rightUpperLeg" : "leftUpperLeg", new Quaternionf().rotationX(a * DEG));
				rig.setBone(i == 0 ? "rightLowerLeg" : "leftLowerLeg", new Quaternionf().rotationX(-(q >= f ? 40 : 5) * move * DEG));
			}
		}
	}
	private float strike() {
		float p = attackP;
		if (p < 0.4f) {
			float t = p / 0.4f;
			return t * t;
		}
		float t = (p - 0.4f) / 0.6f;
		return 1 - t * t * (3 - 2 * t);
	}
	private void swingArm(VrmRig.Drive d, float bodyPitch) {
		if (attackW < 0.01f) {
			return;
		}
		float strike = strike();
		float look = clamp(d.pitch - bodyPitch, -80, 85);
		float aim = clamp(85 - look * 0.75f, 5, 150);
		float swing = lerp(aim + 38, aim - 32, strike);
		float hang = lerp(62, 70, strike);
		float elbow = lerp(85, 14, strike);
		int i = attackSide;
		float side = i == 0 ? 1 : -1;
		String s = i == 0 ? "right" : "left";
		rig.mixBone(s + "UpperArm", new Quaternionf().rotationX(swing * DEG).rotateZ(-side * hang * DEG)
				.rotateY(-side * 20 * strike * DEG), attackW);
		rig.mixBone(s + "LowerArm", new Quaternionf().rotationY(side * elbow * DEG), attackW);
		rig.mixBone(s + "Hand", new Quaternionf().rotationZ(side * 25 * (1 - strike) * DEG), attackW);
	}
	private void look(VrmRig.Drive d, float bodyPitch) {
		float yaw = clamp(wrap(d.headYaw - rootYaw), -85, 85);
		float pitch = clamp(d.pitch - bodyPitch, -80, 80);
		rig.setBone("neck", new Quaternionf().rotationY(-yaw * 0.35f * DEG).rotateX(-pitch * 0.35f * DEG));
		rig.setBone("head", new Quaternionf().rotationY(-yaw * 0.65f * DEG).rotateX(-pitch * 0.65f * DEG));
	}
	private void setArm(int i, float swing, float hang, float elbow) {
		float side = i == 0 ? 1 : -1;
		String s = i == 0 ? "right" : "left";
		rig.setBone(s + "UpperArm", new Quaternionf().rotationX(swing * DEG).rotateZ(-side * hang * DEG));
		rig.setBone(s + "LowerArm", new Quaternionf().rotationY(side * elbow * DEG));
	}
	private void armsDown(float hang, float swing, float elbow) {
		setArm(0, swing, hang, elbow);
		setArm(1, swing, hang, elbow);
	}
	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private void swimPose(VrmRig.Drive d, float hs) {
		float moving = smooth(0.1f, 1.5f, hs);
		rig.hipsOffset.set(0);
		float kick = (8 + 10 * moving) * (float) Math.sin(time * (3 + 4 * moving));
		for (int i = 0; i < 2; i++) {
			String s = i == 0 ? "right" : "left";
			float k = i == 0 ? kick : -kick;
			rig.setBone(s + "UpperLeg", new Quaternionf().rotationX(k * DEG));
			rig.setBone(s + "LowerLeg", new Quaternionf().rotationX(-(10 + Math.max(0, k)) * DEG));
			rig.setBone(s + "Foot", new Quaternionf().rotationX(-30 * DEG));
			float stroke = 0.5f + 0.5f * (float) Math.sin(time * 2.2f + i * Math.PI);
			setArm(i, 160 - 70 * stroke * moving, 80, 15 + 20 * stroke * moving);
		}
		rig.setBone("head", new Quaternionf().rotationX((d.inWater ? 25 : 50) * DEG));
	}
	private void glidePose() {
		rig.hipsOffset.set(0);
		armsDown(80, -15, 5);
		rig.setBone("head", new Quaternionf().rotationX(50 * DEG));
		for (String s : new String[] {"right", "left"}) {
			rig.setBone(s + "Foot", new Quaternionf().rotationX(-35 * DEG));
		}
	}
	private void sitPose(VrmRig.Drive d) {
		rig.hipsOffset.set(0);
		for (int i = 0; i < 2; i++) {
			String s = i == 0 ? "right" : "left";
			float side = i == 0 ? 1 : -1;
			rig.setBone(s + "UpperLeg", new Quaternionf().rotationX(80 * DEG).rotateZ(side * 6 * DEG));
			rig.setBone(s + "LowerLeg", new Quaternionf().rotationX(-85 * DEG));
		}
		armsDown(70, 35, 30);
		swingArm(d, 0);
	}
	private static Vector3f orth(Vector3f a, Vector3f along, Vector3f fallback) {
		Vector3f o = new Vector3f(a).fma(-a.dot(along), along);
		if (o.lengthSquared() < 1e-8f) {
			o.set(fallback).fma(-fallback.dot(along), along);
		}
		return o.normalize();
	}

	private Quaternionf aim(Vector3f u0, Vector3f p0, Vector3f u1, Vector3f p1) {
		Vector3f b0 = orth(p0, u0, up);
		Vector3f b1 = orth(p1, u1, up);
		Matrix3f m0 = new Matrix3f(u0, b0, new Vector3f(u0).cross(b0));
		Matrix3f m1 = new Matrix3f(u1, b1, new Vector3f(u1).cross(b1));
		return m1.mul(m0.transpose()).getNormalizedRotation(new Quaternionf());
	}
	private void solveLeg(int i, Vector3f target, float yaw, float pitch) {
		Vector3f hip = rig.worldPos(upper[i]);
		Vector3f to = new Vector3f(target).sub(hip);
		float a = l1[i];
		float b = l2[i];
		float dist = to.length();
		Vector3f dir = dist > 1e-5f ? to.div(dist) : new Vector3f(0, -1, 0);
		dist = clamp(dist, Math.abs(a - b) + 1e-4f, (a + b) * 0.9995f);
		float cosA = clamp((a * a + dist * dist - b * b) / (2 * a * dist), -1, 1);
		float sinA = (float) Math.sqrt(1 - cosA * cosA);
		Vector3f pole = new Vector3f(fwd).rotateY(yaw);
		Vector3f perp = orth(pole, dir, up);
		Vector3f knee = new Vector3f(hip).fma(cosA * a, dir).fma(sinA * a, perp);
		Vector3f ankle = new Vector3f(hip).fma(dist, dir);
		Quaternionf thigh = aim(restThighDir[i], fwd, new Vector3f(knee).sub(hip).normalize(), pole).mul(m.restWorldR[upper[i]]);
		int parent = m.parent[upper[i]];
		Quaternionf parentRot = parent >= 0 ? rig.worldRot(parent) : new Quaternionf();
		rig.setLocal(upper[i], new Quaternionf(parentRot).invert().mul(thigh));
		Quaternionf shin = aim(restShinDir[i], fwd, new Vector3f(ankle).sub(knee).normalize(), pole).mul(m.restWorldR[lower[i]]);
		rig.setLocal(lower[i], new Quaternionf(thigh).invert().mul(shin));
		Vector3f toeAxis = new Vector3f(up).cross(new Vector3f(fwd).rotateY(yaw));
		Quaternionf footRot = new Quaternionf().rotateAxis(pitch, toeAxis).mul(new Quaternionf().rotationY(yaw)).mul(m.restWorldR[foot[i]]);
		rig.setLocal(foot[i], new Quaternionf(shin).invert().mul(footRot));
	}
}
