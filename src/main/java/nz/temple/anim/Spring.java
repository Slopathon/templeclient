package nz.temple.anim;

public final class Spring {
	private static final double STEP_HZ = 240;
	private static final double MAX_ELAPSED = 0.5;
	public double x;
	public double target;
	public double velocity;
	public double stiffness;
	public double damping;
	public Spring(double value, double stiffness, double damping) {
		this.x = value;
		this.target = value;
		this.stiffness = stiffness;
		this.damping = damping;
	}
	public void step(double dt) {
		double accel = -stiffness * (x - target) - damping * velocity;
		velocity += accel * dt;
		x += velocity * dt;
	}
	public void snap(double value) {
		x = value;
		target = value;
		velocity = 0;
	}
	public boolean atRest() {
		return Math.abs(velocity) < 0.01 && Math.abs(x - target) < 0.001;
	}
	public float xf() {
		return (float) x;
	}
	public static void advance(double elapsedSeconds, Iterable<Spring> springs) {
		double elapsed = Math.min(Math.max(elapsedSeconds, 0), MAX_ELAPSED);
		int steps = Math.max(1, (int) Math.round(elapsed * STEP_HZ));
		double dt = elapsed / steps;
		for (int i = 0; i < steps; i++) {
			for (Spring s : springs) {
				s.step(dt);
			}
		}
	}
	public static void aimPill(Spring left, Spring right, double newLeft, double newRight) {
		boolean movingRight = newLeft > left.target;
		left.stiffness = movingRight ? 120 : 300;
		right.stiffness = movingRight ? 300 : 120;
		left.damping = movingRight ? 17 : 26;
		right.damping = movingRight ? 26 : 17;
		left.target = newLeft;
		right.target = newRight;
	}
}
