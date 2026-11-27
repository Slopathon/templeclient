package nz.temple.music;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Lyrics {
	public record Word(String text, double start, double end) {
	}
	public record Line(String text, double start, double end, List<Word> words) {
		public boolean gap() {
			return text.isEmpty();
		}
		public int wordAt(double time) {
			int at = -1;
			for (int i = 0; i < words.size(); i++) {
				if (words.get(i).start() <= time) {
					at = i;
				}
			}
			return at;
		}
	}
	private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
	private static final Pattern WORD_TIME = Pattern.compile("<(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?>");
	private static final double HOLD = 2.5;
	private final List<Line> lines;
	private Lyrics(List<Line> lines) {
		this.lines = lines;
	}

	public List<Line> lines() {
		return lines;
	}

	public boolean isEmpty() {
		return lines.isEmpty();
	}

	public Line lineAt(double time) {
		int i = indexAt(time);
		return i < 0 ? null : lines.get(i);
	}
	public int indexAt(double time) {
		int lo = 0;
		int hi = lines.size() - 1;
		int found = -1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (lines.get(mid).start() <= time) {
				found = mid;
				lo = mid + 1;
			} else {
				hi = mid - 1;
			}
		}
		if (found < 0) {
			return -1;
		}
		Line line = lines.get(found);
		return time < line.end() && !line.gap() ? found : -1;
	}
	public static Lyrics parse(String lrc, double songLength) {
		record Stamp(double at, String text) {
		}
		List<Stamp> stamps = new ArrayList<>();
		if (lrc != null) {
			for (String raw : lrc.split("\r?\n")) {
				Matcher m = TIME.matcher(raw);
				List<Double> times = new ArrayList<>();
				int end = 0;
				while (m.find() && m.start() == end) {
					times.add(seconds(m.group(1), m.group(2), m.group(3)));
					end = m.end();
				}
				if (times.isEmpty()) {
					continue;
				}
				String text = raw.substring(end).trim();
				for (double t : times) {
					stamps.add(new Stamp(t, text));
				}
			}
		}
		stamps.sort((a, b) -> Double.compare(a.at(), b.at()));
		List<Line> lines = new ArrayList<>();
		for (int i = 0; i < stamps.size(); i++) {
			Stamp s = stamps.get(i);
			double next = i + 1 < stamps.size() ? stamps.get(i + 1).at() : songLength > s.at() ? songLength : s.at() + 6;
			lines.add(line(s.text(), s.at(), next));
		}
		return new Lyrics(List.copyOf(lines));
	}
	static Line line(String raw, double start, double next) {
		List<Word> words = new ArrayList<>();
		Matcher m = WORD_TIME.matcher(raw);
		if (m.find()) {
			List<double[]> marks = new ArrayList<>();
			List<String> texts = new ArrayList<>();
			m.reset();
			int last = -1;
			double lastTime = start;
			while (m.find()) {
				if (last >= 0) {
					texts.add(raw.substring(last, m.start()));
					marks.add(new double[] {lastTime});
				}
				lastTime = seconds(m.group(1), m.group(2), m.group(3));
				last = m.end();
			}
			if (last >= 0) {
				texts.add(raw.substring(last));
				marks.add(new double[] {lastTime});
			}
			StringBuilder plain = new StringBuilder();
			for (int i = 0; i < texts.size(); i++) {
				String w = texts.get(i).trim();
				if (w.isEmpty()) {
					continue;
				}
				double s = marks.get(i)[0];
				double e = i + 1 < marks.size() ? marks.get(i + 1)[0] : Math.min(next, s + 1);
				for (String piece : w.split("\\s+")) {
					words.add(new Word(piece, s, e));
				}
				plain.append(plain.isEmpty() ? "" : " ").append(w);
			}
			double end = words.isEmpty() ? next : Math.min(next, words.get(words.size() - 1).end() + HOLD);
			return new Line(plain.toString(), start, end, List.copyOf(words));
		}
		String text = raw.trim();
		if (text.isEmpty()) {
			return new Line("", start, next, List.of());
		}
		String[] pieces = text.split("\\s+");
		double span = Math.max(0.25, next - start);
		double singing = Math.min(span * 0.88, 0.42 * pieces.length + 0.9);
		double total = 0;
		for (String p : pieces) {
			total += weight(p);
		}
		double t = start;
		for (String p : pieces) {
			double d = singing * weight(p) / total;
			words.add(new Word(p, t, t + d));
			t += d;
		}
		double end = Math.min(next, t + HOLD);
		return new Line(text, start, end, List.copyOf(words));
	}
	private static double weight(String word) {
		int letters = 0;
		for (int i = 0; i < word.length(); i++) {
			if (Character.isLetterOrDigit(word.charAt(i))) {
				letters++;
			}
		}
		return 1.6 + letters;
	}
	private static double seconds(String min, String sec, String frac) {
		double f = 0;
		if (frac != null) {
			f = Integer.parseInt(frac) / Math.pow(10, frac.length());
		}
		return Integer.parseInt(min) * 60 + Integer.parseInt(sec) + f;
	}
	public static String cleanTitle(String title) {
		String t = title == null ? "" : title;
		t = t.replaceAll("(?i)\\s*[(\\[][^)\\]]*(feat\\.?|ft\\.?|with |official|video|audio|lyrics?|visuali[sz]er|remaster|live|version|edit|mix|created by|prod\\.?)[^)\\]]*[)\\]]", "");
		t = t.replaceAll("(?i)\\s+-\\s+.*(remaster|version|edit|live|mono|stereo|mix|single).*$", "");
		t = t.replaceAll("(?i)\\s+(feat\\.?|ft\\.?)\\s+.*$", "");
		return t.trim();
	}
	public static String cleanArtist(String artist) {
		String a = artist == null ? "" : artist;
		a = a.replaceAll("(?i)\\s+-\\s+topic$", "").replaceAll("(?i)VEVO$", "");
		for (String sep : new String[] {", ", " & ", " x ", " feat. ", " ft. ", " / "}) {
			int i = a.toLowerCase(Locale.ROOT).indexOf(sep.toLowerCase(Locale.ROOT));
			if (i > 0) {
				a = a.substring(0, i);
			}
		}
		return a.trim();
	}
}
