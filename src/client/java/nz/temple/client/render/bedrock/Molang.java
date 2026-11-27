package nz.temple.client.render.bedrock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Molang {
	@FunctionalInterface
	public interface Expr {
		float eval(float animTime);
	}
	public static float lifeTime;
	public static float moveSpeed;

	public static float headPitch;
	public static float headYaw;
	private final String src;
	private int pos;
	private Molang(String src) {
		this.src = src.toLowerCase(Locale.ROOT);
	}

	public static Expr compile(String src, float fallback) {
		try {
			Molang p = new Molang(src.replace("return", "").replace(";", ""));
			Expr e = p.ternary();
			p.skip();
			if (p.pos != p.src.length()) {
				return t -> fallback;
			}
			return e;
		} catch (RuntimeException ex) {
			return t -> fallback;
		}
	}
	private void skip() {
		while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
			pos++;
		}
	}
	private boolean eat(String s) {
		skip();
		if (src.startsWith(s, pos)) {
			pos += s.length();
			return true;
		}
		return false;
	}
	private Expr ternary() {
		Expr c = logic();
		if (eat("??")) {
			ternary();
			return c;
		}
		if (eat("?")) {
			Expr a = ternary();
			Expr b = eat(":") ? ternary() : t -> 0;
			return t -> c.eval(t) != 0 ? a.eval(t) : b.eval(t);
		}
		return c;
	}
	private Expr logic() {
		Expr l = compare();
		while (true) {
			if (eat("&&")) {
				Expr a = l, b = compare();
				l = t -> a.eval(t) != 0 && b.eval(t) != 0 ? 1 : 0;
			} else if (eat("||")) {
				Expr a = l, b = compare();
				l = t -> a.eval(t) != 0 || b.eval(t) != 0 ? 1 : 0;
			} else {
				return l;
			}
		}
	}
	private Expr compare() {
		Expr l = sum();
		while (true) {
			Expr a = l;
			if (eat("<=")) {
				Expr b = sum();
				l = t -> a.eval(t) <= b.eval(t) ? 1 : 0;
			} else if (eat(">=")) {
				Expr b = sum();
				l = t -> a.eval(t) >= b.eval(t) ? 1 : 0;
			} else if (eat("==")) {
				Expr b = sum();
				l = t -> a.eval(t) == b.eval(t) ? 1 : 0;
			} else if (eat("!=")) {
				Expr b = sum();
				l = t -> a.eval(t) != b.eval(t) ? 1 : 0;
			} else if (eat("<")) {
				Expr b = sum();
				l = t -> a.eval(t) < b.eval(t) ? 1 : 0;
			} else if (eat(">")) {
				Expr b = sum();
				l = t -> a.eval(t) > b.eval(t) ? 1 : 0;
			} else {
				return l;
			}
		}
	}
	private Expr sum() {
		Expr l = product();
		while (true) {
			Expr a = l;
			if (eat("+")) {
				Expr b = product();
				l = t -> a.eval(t) + b.eval(t);
			} else if (eat("-")) {
				Expr b = product();
				l = t -> a.eval(t) - b.eval(t);
			} else {
				return l;
			}
		}
	}
	private Expr product() {
		Expr l = unary();
		while (true) {
			Expr a = l;
			if (eat("*")) {
				Expr b = unary();
				l = t -> a.eval(t) * b.eval(t);
			} else if (eat("/")) {
				Expr b = unary();
				l = t -> {
					float d = b.eval(t);
					return d == 0 ? 0 : a.eval(t) / d;
				};
			} else {
				return l;
			}
		}
	}
	private Expr unary() {
		if (eat("-")) {
			Expr e = unary();
			return t -> -e.eval(t);
		}
		if (eat("!")) {
			Expr e = unary();
			return t -> e.eval(t) == 0 ? 1 : 0;
		}
		return atom();
	}
	private Expr atom() {
		skip();
		if (eat("(")) {
			Expr e = ternary();
			eat(")");
			return e;
		}
		int start = pos;
		if (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
			while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
				pos++;
			}
			float v = Float.parseFloat(src.substring(start, pos));
			if (pos < src.length() && src.charAt(pos) == 'f') {
				pos++;
			}
			return t -> v;
		}
		while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_' || src.charAt(pos) == '.')) {
			pos++;
		}
		String name = src.substring(start, pos);
		if (name.isEmpty()) {
			throw new IllegalArgumentException("unexpected " + src.substring(pos));
		}
		List<Expr> args = new ArrayList<>();
		if (eat("(")) {
			if (!eat(")")) {
				do {
					args.add(ternary());
				} while (eat(","));
				eat(")");
			}
		}
		return call(name.replaceFirst("^q\\.", "query.").replaceFirst("^v\\.", "variable."), args);
	}
	private static Expr arg(List<Expr> args, int i) {
		return i < args.size() ? args.get(i) : t -> 0;
	}
	private static Expr call(String name, List<Expr> args) {
		Expr a = arg(args, 0), b = arg(args, 1), c = arg(args, 2);
		return switch (name) {
			case "math.sin" -> t -> (float) Math.sin(Math.toRadians(a.eval(t)));
			case "math.cos" -> t -> (float) Math.cos(Math.toRadians(a.eval(t)));
			case "math.abs" -> t -> Math.abs(a.eval(t));
			case "math.min" -> t -> Math.min(a.eval(t), b.eval(t));
			case "math.max" -> t -> Math.max(a.eval(t), b.eval(t));
			case "math.clamp" -> t -> Math.max(b.eval(t), Math.min(c.eval(t), a.eval(t)));
			case "math.lerp" -> t -> a.eval(t) + (b.eval(t) - a.eval(t)) * c.eval(t);
			case "math.pow" -> t -> (float) Math.pow(a.eval(t), b.eval(t));
			case "math.sqrt" -> t -> (float) Math.sqrt(Math.max(0, a.eval(t)));
			case "math.floor" -> t -> (float) Math.floor(a.eval(t));
			case "math.ceil" -> t -> (float) Math.ceil(a.eval(t));
			case "math.round" -> t -> Math.round(a.eval(t));
			case "math.trunc" -> t -> (int) a.eval(t);
			case "math.mod" -> t -> {
				float d = b.eval(t);
				return d == 0 ? 0 : a.eval(t) % d;
			};
			case "math.exp" -> t -> (float) Math.exp(a.eval(t));
			case "math.pi" -> t -> (float) Math.PI;
			case "math.atan" -> t -> (float) Math.toDegrees(Math.atan(a.eval(t)));
			case "math.atan2" -> t -> (float) Math.toDegrees(Math.atan2(a.eval(t), b.eval(t)));
			case "math.random" -> t -> a.eval(t) + (float) Math.random() * (b.eval(t) - a.eval(t));
			case "query.anim_time", "query.time_stamp" -> t -> t;
			case "query.life_time" -> t -> lifeTime;
			case "query.modified_move_speed", "query.walk_speed" -> t -> moveSpeed;
			case "query.ground_speed" -> t -> moveSpeed * 5.6f;
			case "query.head_x_rotation" -> t -> headPitch;
			case "query.head_y_rotation" -> t -> headYaw;
			default -> t -> 0;
		};
	}
}
