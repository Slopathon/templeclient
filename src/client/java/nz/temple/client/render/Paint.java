package nz.temple.client.render;

public interface Paint {
	int color(double t);

	default int color(double t, double fade) {
		return Fx.withAlpha(color(t), (float) (255 * alpha() * Math.max(0, Math.min(1, fade))));
	}

	float alpha();

	void apply(Meshes mesh, float extraGlow);

	float bloom();
}
