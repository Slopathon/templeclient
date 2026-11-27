package nz.temple.client.render.title;

import java.util.ArrayList;
import java.util.List;

final class LetterWorld {
	private static final float PAIR_RESTITUTION = 0.12f;
	private static final float PAIR_FRICTION = 0.5f;
	private static final float PULL = 26;
	private static final float MAX_PULL_SPEED = 30;
	final List<LetterBody> bodies = new ArrayList<>();
	private float accumulator;
	private LetterBody heldBody;
	private float grabX;
	private float grabY;
	private float targetX;
	private float targetY;
	void advance(float dt) {
		accumulator = Math.min(accumulator + dt, 0.1f);
		while (accumulator >= LetterBody.STEP) {
			step();
			accumulator -= LetterBody.STEP;
		}
	}
	private void step() {
		for (LetterBody b : bodies) {
			b.touching = false;
			b.accelerate();
		}
		if (heldBody != null) {
			pull(heldBody);
		}
		for (LetterBody b : bodies) {
			b.solveBounds();
		}
		for (int i = 0; i < bodies.size(); i++) {
			for (int j = i + 1; j < bodies.size(); j++) {
				collide(bodies.get(i), bodies.get(j));
			}
		}
		for (LetterBody b : bodies) {
			b.integrate();
		}
	}
	void grab(LetterBody b, float px, float py) {
		release();
		heldBody = b;
		float c = (float) Math.cos(-b.angle), s = (float) Math.sin(-b.angle);
		float dx = px - b.x, dy = py - b.y;
		grabX = c * dx - s * dy;
		grabY = s * dx + c * dy;
		targetX = px;
		targetY = py;
		b.held = true;
		b.wake();
	}
	void target(float px, float py) {
		targetX = px;
		targetY = py;
	}
	void release() {
		if (heldBody != null) {
			heldBody.held = false;
			heldBody.wake();
		}
		heldBody = null;
	}
	LetterBody held() {
		return heldBody;
	}

	private void pull(LetterBody b) {
		b.wake();
		float c = (float) Math.cos(b.angle), s = (float) Math.sin(b.angle);
		float rx = c * grabX - s * grabY;
		float ry = s * grabX + c * grabY;
		float px = b.x + rx, py = b.y + ry;
		float wantX = (targetX - px) * PULL;
		float wantY = (targetY - py) * PULL;
		float len = (float) Math.hypot(wantX, wantY);
		if (len > MAX_PULL_SPEED) {
			wantX *= MAX_PULL_SPEED / len;
			wantY *= MAX_PULL_SPEED / len;
		}
		float vpx = b.vx - b.spin * ry;
		float vpy = b.vy + b.spin * rx;
		float ex = wantX - vpx, ey = wantY - vpy;
		float inv = 1 / b.inertia;
		float k11 = 1 + ry * ry * inv, k12 = -rx * ry * inv, k22 = 1 + rx * rx * inv;
		float det = k11 * k22 - k12 * k12;
		if (Math.abs(det) < 1e-6f) {
			return;
		}
		float jx = 0.85f * (k22 * ex - k12 * ey) / det;
		float jy = 0.85f * (k11 * ey - k12 * ex) / det;
		b.vx += jx;
		b.vy += jy;
		b.spin += (rx * jy - ry * jx) * inv;
		b.spin *= 1 - 3f * LetterBody.STEP;
	}
	private final float[] contact = new float[4];
	private void collide(LetterBody a, LetterBody b) {
		if (a.asleep && b.asleep) {
			return;
		}
		if (!overlaps(a, b)) {
			return;
		}
		float best = -Float.MAX_VALUE;
		boolean fromA = true;
		float bnx = 0, bny = 0, cx = 0, cy = 0;
		for (int side = 0; side < 2; side++) {
			LetterBody ref = side == 0 ? a : b;
			LetterBody inc = side == 0 ? b : a;
			if (!axis(ref, inc)) {
				return;
			}
			if (contact[0] > best) {
				best = contact[0];
				fromA = side == 0;
				bnx = contact[1];
				bny = contact[2];
				cx = contactX;
				cy = contactY;
			}
		}
		float depth = -best;
		if (depth <= 0) {
			return;
		}
		if (!fromA) {
			bnx = -bnx;
			bny = -bny;
		}
		a.wake();
		b.wake();
		a.touching = true;
		b.touching = true;
		impulse(a, b, bnx, bny, cx, cy);
		float slop = 0.004f;
		float move = Math.max(0, depth - slop) * 0.6f;
		float ia = 1 / a.mass, ib = 1 / b.mass;
		float share = ia + ib;
		a.x -= bnx * move * ia / share;
		a.y -= bny * move * ia / share;
		b.x += bnx * move * ib / share;
		b.y += bny * move * ib / share;
		a.updateWorld();
		b.updateWorld();
	}
	private float contactX;
	private float contactY;
	private boolean axis(LetterBody ref, LetterBody inc) {
		int n = ref.wx.length;
		float best = -Float.MAX_VALUE;
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			float ex = ref.wx[j] - ref.wx[i], ey = ref.wy[j] - ref.wy[i];
			float len = (float) Math.hypot(ex, ey);
			if (len < 1e-5f) {
				continue;
			}
			float nx = ey / len, ny = -ex / len;
			float min = Float.MAX_VALUE;
			int deepest = 0;
			for (int k = 0; k < inc.wx.length; k++) {
				float d = (inc.wx[k] - ref.wx[i]) * nx + (inc.wy[k] - ref.wy[i]) * ny;
				if (d < min) {
					min = d;
					deepest = k;
				}
			}
			if (min > 0) {
				return false;
			}
			if (min > best) {
				best = min;
				contact[0] = min;
				contact[1] = nx;
				contact[2] = ny;
				contactX = inc.wx[deepest];
				contactY = inc.wy[deepest];
			}
		}
		return true;
	}
	private static boolean overlaps(LetterBody a, LetterBody b) {
		float dx = a.x - b.x, dy = a.y - b.y;
		float r = a.radius + b.radius;
		return dx * dx + dy * dy < r * r;
	}
	private static void impulse(LetterBody a, LetterBody b, float nx, float ny, float cx, float cy) {
		float ima = 1 / a.mass, imb = 1 / b.mass;
		float iia = ima / a.inertia, iib = imb / b.inertia;
		float rax = cx - a.x, ray = cy - a.y;
		float rbx = cx - b.x, rby = cy - b.y;
		float rvx = (b.vx - b.spin * rby) - (a.vx - a.spin * ray);
		float rvy = (b.vy + b.spin * rbx) - (a.vy + a.spin * rax);
		float vn = rvx * nx + rvy * ny;
		if (vn > 0) {
			return;
		}
		float ran = rax * ny - ray * nx, rbn = rbx * ny - rby * nx;
		float k = ima + imb + ran * ran * iia + rbn * rbn * iib;
		float j = -(1 + (vn < -2 ? PAIR_RESTITUTION : 0)) * vn / k;
		apply(a, b, nx * j, ny * j, rax, ray, rbx, rby);
		if (vn < -4) {
			a.impact = Math.max(a.impact, -vn * 0.6f);
			b.impact = Math.max(b.impact, -vn * 0.6f);
		}
		rvx = (b.vx - b.spin * rby) - (a.vx - a.spin * ray);
		rvy = (b.vy + b.spin * rbx) - (a.vy + a.spin * rax);
		float tx = -ny, ty = nx;
		float vt = rvx * tx + rvy * ty;
		float rat = rax * ty - ray * tx, rbt = rbx * ty - rby * tx;
		float kt = ima + imb + rat * rat * iia + rbt * rbt * iib;
		float jt = Math.max(-PAIR_FRICTION * j, Math.min(PAIR_FRICTION * j, -vt / kt));
		apply(a, b, tx * jt, ty * jt, rax, ray, rbx, rby);
	}
	private static void apply(LetterBody a, LetterBody b, float jx, float jy, float rax, float ray, float rbx, float rby) {
		float ima = 1 / a.mass, imb = 1 / b.mass;
		a.vx -= jx * ima;
		a.vy -= jy * ima;
		a.spin -= (rax * jy - ray * jx) * ima / a.inertia;
		b.vx += jx * imb;
		b.vy += jy * imb;
		b.spin += (rbx * jy - rby * jx) * imb / b.inertia;
	}
}
