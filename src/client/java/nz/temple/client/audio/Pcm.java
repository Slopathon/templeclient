package nz.temple.client.audio;

public record Pcm(float[] samples, int rate) {
	public double seconds() {
		return samples.length / (double) rate;
	}

	public float[] peaks(int buckets) {
		float[] out = new float[Math.max(1, buckets)];
		int n = samples.length;
		for (int b = 0; b < out.length; b++) {
			int from = (int) ((long) n * b / out.length);
			int to = Math.max(from + 1, (int) ((long) n * (b + 1) / out.length));
			float peak = 0;
			for (int i = from; i < to && i < n; i++) {
				peak = Math.max(peak, Math.abs(samples[i]));
			}
			out[b] = peak;
		}
		return out;
	}
}
