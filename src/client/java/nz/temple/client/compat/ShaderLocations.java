package nz.temple.client.compat;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShaderLocations {
	private static final Pattern DECLARATION = Pattern.compile(
			"^(\\s*)(layout\\s*\\(([^)]*)\\)\\s*)?((?:(?:flat|smooth|noperspective|centroid|invariant)\\s+)*)(in|out)\\s+(\\w+)\\s+(\\w+)\\s*(\\[\\s*(\\d+)\\s*])?\\s*;(.*)$");
	private static final Pattern LOCATION = Pattern.compile("location\\s*=\\s*(\\d+)");
	public record Vertex(String source, Map<String, Integer> outputs) {
	}
	private ShaderLocations() {
	}
	public static Vertex vertex(String source) {
		return vertex(source, Map.of());
	}

	public static Vertex vertex(String source, Map<String, Integer> fixed) {
		Map<String, Integer> outputs = new HashMap<>();
		String rewritten = rewrite(source, null, fixed, outputs);
		return new Vertex(rewritten, outputs);
	}

	public static String fragment(String source, Map<String, Integer> varyings) {
		return rewrite(source, varyings, Map.of(), new HashMap<>());
	}
	public static Map<String, Integer> explicitInputs(String source) {
		Map<String, Integer> found = new HashMap<>();
		for (String line : source.replace(String.valueOf((char) 13), "").split("\n")) {
			Matcher m = DECLARATION.matcher(line);
			if (m.matches() && m.group(3) != null && m.group(5).equals("in")) {
				Matcher at = LOCATION.matcher(m.group(3));
				if (at.find()) {
					found.put(m.group(7), Integer.parseInt(at.group(1)));
				}
			}
		}
		return found;
	}
	private static String rewrite(String source, Map<String, Integer> varyings, Map<String, Integer> fixed,
			Map<String, Integer> outputs) {
		boolean fragment = varyings != null;
		source = source.replace(String.valueOf((char) 13), "");
		String[] lines = source.split("\n", -1);
		Map<String, java.util.BitSet> used = new HashMap<>();
		used.put("in", new java.util.BitSet());
		used.put("out", new java.util.BitSet());

		for (int loc : fixed.values()) {
			used.get("out").set(loc);
		}
		for (String line : lines) {
			Matcher m = DECLARATION.matcher(line);
			if (m.matches() && m.group(3) != null) {
				Matcher at = LOCATION.matcher(m.group(3));
				if (at.find()) {
					int loc = Integer.parseInt(at.group(1));
					used.get(m.group(5)).set(loc, loc + slots(m.group(6), m.group(9)));
					if (!fragment && m.group(5).equals("out")) {
						outputs.put(m.group(7), loc);
					}
				}
			}
		}
		StringBuilder out = new StringBuilder(source.length() + 256);
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			Matcher m = DECLARATION.matcher(line);
			if (m.matches() && (m.group(3) == null || !LOCATION.matcher(m.group(3)).find())) {
				String direction = m.group(5);
				String name = m.group(7);
				int size = slots(m.group(6), m.group(9));
				java.util.BitSet taken = used.get(direction);
				int loc;
				Integer paired = fragment && direction.equals("in") ? varyings.get(name)
						: !fragment && direction.equals("out") ? fixed.get(name) : null;
				if (paired != null) {
					loc = paired;
				} else {
					loc = free(taken, size);
				}
				taken.set(loc, loc + size);
				if (!fragment && direction.equals("out")) {
					outputs.put(name, loc);
				}
				String otherLayout = m.group(3) == null ? "" : ", " + m.group(3).trim();
				line = m.group(1) + "layout(location = " + loc + otherLayout + ") " + m.group(4) + direction + " " + m.group(6) + " "
						+ name + (m.group(8) == null ? "" : m.group(8)) + ";" + m.group(10);
			}
			out.append(line);
			if (i < lines.length - 1) {
				out.append('\n');
			}
		}
		return out.toString();
	}
	private static int free(java.util.BitSet taken, int size) {
		int loc = 0;
		while (true) {
			int next = taken.nextSetBit(loc);
			if (next < 0 || next >= loc + size) {
				return loc;
			}
			loc = next + 1;
		}
	}

	private static int slots(String type, String arrayLength) {
		int per = switch (type) {
			case "mat2", "dmat2" -> 2;
			case "mat3", "dmat3" -> 3;
			case "mat4", "dmat4" -> 4;
			default -> 1;
		};
		return per * (arrayLength == null ? 1 : Integer.parseInt(arrayLength));
	}
}
