package nz.temple.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Perf {
	public static final boolean ON = Boolean.getBoolean("temple.perf");
	public static final class Section {
		private final String name;
		private long pending;
		private long total;
		private Section(String name) {
			this.name = name;
		}
		public long begin() {
			return ON ? System.nanoTime() : 0;
		}
		public void end(long began) {
			if (ON) {
				pending += System.nanoTime() - began;
			}
		}
	}
	private static final Map<String, Section> SECTIONS = new LinkedHashMap<>();
	private static int frames;

	private Perf() {
	}

	public static synchronized Section section(String name) {
		return SECTIONS.computeIfAbsent(name, Section::new);
	}
	public static synchronized void frame() {
		if (!ON) {
			return;
		}
		frames++;
		for (Section s : SECTIONS.values()) {
			s.total += s.pending;
			s.pending = 0;
		}
	}
	public static synchronized Map<String, Double> take() {
		Map<String, Double> out = new LinkedHashMap<>();
		for (Section s : SECTIONS.values()) {
			out.put(s.name, frames == 0 ? 0 : s.total / 1e6 / frames);
			s.total = 0;
		}
		frames = 0;
		return out;
	}
	public static String rise(Map<String, Double> before, Map<String, Double> after, double threshold) {
		List<Map.Entry<String, Double>> rows = new ArrayList<>();
		for (Map.Entry<String, Double> e : after.entrySet()) {
			double delta = e.getValue() - before.getOrDefault(e.getKey(), 0.0);
			if (delta >= threshold) {
				rows.add(Map.entry(e.getKey(), delta));
			}
		}
		rows.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
		StringBuilder out = new StringBuilder();
		for (Map.Entry<String, Double> row : rows) {
			out.append(String.format(Locale.ROOT, " %s +%.3f", row.getKey(), row.getValue()));
		}
		return out.toString();
	}
}
