package nz.temple.client.cosmetic.aura;

import nz.temple.client.cosmetic.PieceLook;
import nz.temple.client.render.Meshes;
import nz.temple.cosmetic.Model;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.Setting;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public abstract class AuraPiece {
	public interface Host {
		<S extends Setting<?>> S add(S setting);
		void group(String title);
	}

	protected final BoolSetting on;
	protected final BoolSetting realism;
	protected final BoolSetting firstPerson;
	private final String name;
	private final Map<UUID, Object> state = new HashMap<>();
	protected AuraPiece(Host host, String name, boolean defaultOn, String description, boolean original) {
		this.name = name;
		host.group(name);
		on = host.add(new BoolSetting(name, defaultOn).describe(description));
		if (original) {
			realism = host.add(new BoolSetting("Realism", false)
					.describe("Uses the rebuilt " + name.toLowerCase() + ": sculpted, lit by the world, with its own finish, colour and glow. "
							+ "Off uses the original.")
					.visibleWhen(() -> on.get() && hasOriginal()));
			realism.keyAs(name + " realism");
		} else {
			realism = null;
		}
		firstPerson = host.add(new BoolSetting("In first person", true)
				.describe("Shows your own " + name.toLowerCase() + " in first person (with the module's Show in first person on). "
						+ "Off hides it only from your own view; others and F5 still see it.")
				.visibleWhen(on::get));
		firstPerson.keyAs(name + " first person");
	}
	public final boolean inFirstPerson() {
		return firstPerson.get();
	}
	public final boolean on() {
		return on.get();
	}
	public final String name() {
		return name;
	}
	public final BoolSetting realismToggle() {
		return realism;
	}

	protected boolean hasOriginal() {
		return realism != null;
	}

	public final boolean usesOriginal() {
		return on.get() && realism != null && hasOriginal() && !realism.get();
	}

	protected final boolean showsNew() {
		return on.get() && !usesOriginal();
	}

	public abstract void draw(AuraContext c);
	@SuppressWarnings("unchecked")
	protected final <T> T state(UUID id, Supplier<T> make) {
		return (T) state.computeIfAbsent(id, k -> make.get());
	}

	public void forget(Set<UUID> present) {
		state.keySet().retainAll(present);
	}

	public static double travel(double along, double time) {
		double wave = 0.5 + 0.5 * Math.sin((along * 1.4 - time * 0.55) * Math.PI * 2);
		return 0.45 + 0.55 * wave * wave * wave;
	}

	public static double sweep(float x, float z, double time, double speed) {
		double p = time * speed;
		return sweepToward(x, z, Math.cos(p), Math.sin(p));
	}
	public static double sweepToward(float x, float z, double cosPhase, double sinPhase) {

		double len = Math.sqrt(x * x + z * z);
		if (len < 1e-6) {
			return 0;
		}
		double c = (x * cosPhase + z * sinPhase) / len;
		if (c <= 0) {
			return 0;
		}
		double c2 = c * c;
		double c4 = c2 * c2;
		return c4 * c4 * c4;
	}
	private static final Map<Model, float[]> TIPS = new java.util.IdentityHashMap<>();
	private static final Map<Model, Map<String, int[]>> PART_VERTICES = new java.util.IdentityHashMap<>();
	protected static float[] tip(Model m) {
		return TIPS.computeIfAbsent(m, k -> {
			int best = 0;
			for (int v = 1; v < k.vertexCount(); v++) {
				if (k.ramp(v) > k.ramp(best)) {
					best = v;
				}
			}
			return new float[] {k.x(best), k.y(best), k.z(best)};
		});
	}
	protected static int[] partVertices(Model m, String name) {
		return PART_VERTICES.computeIfAbsent(m, k -> new HashMap<>()).computeIfAbsent(name, n -> {
			java.util.LinkedHashSet<Integer> set = new java.util.LinkedHashSet<>();
			for (Model.Part part : m.parts()) {
				if (part.name().equals(n)) {
					for (int i = part.firstIndex(); i < part.firstIndex() + part.indexCount(); i++) {
						set.add(m.indices()[i]);
					}
				}
			}
			return set.stream().mapToInt(Integer::intValue).toArray();
		});
	}
	protected static double spring(double[] s, int i, double target, double stiffness, double damping, double dt) {
		s[i + 1] += ((target - s[i]) * stiffness - s[i + 1] * damping) * dt;
		s[i] += s[i + 1] * dt;
		return s[i];
	}
	protected static Meshes.ModelPainter painter(PieceLook look, int pattern, double patternScale) {
		double time = nz.temple.client.render.Theme.time();
		double glow = look.glow();
		return new Meshes.ModelPainter() {
			@Override
			public boolean part(Meshes m, Model.Part part) {
				look.apply(m);
				m.pattern(pattern, patternScale);
				m.accent(0, look.glowColour(0));
				return true;
			}
			@Override
			public int colour(Model.Part part, double ramp) {
				return look.colour(ramp);
			}
			@Override
			public double glow(Model.Part part, double mask) {
				return mask * glow;
			}
			@Override
			public double glowAt(Model.Part part, double mask, double ramp, float x, float y, float z) {
				return mask * glow == 0 ? 0 : mask * glow * travel(ramp, time);
			}
		};
	}
}
