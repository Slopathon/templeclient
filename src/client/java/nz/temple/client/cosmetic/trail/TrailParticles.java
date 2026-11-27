package nz.temple.client.cosmetic.trail;

import net.minecraft.world.phys.Vec3;
import nz.temple.client.cosmetic.GlowLook;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Meshes;
import nz.temple.cosmetic.Colours;
import nz.temple.cosmetic.Finishes;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class TrailParticles {
	public enum Kind {
		FLAME, EMBER, FIRE_SMOKE, MIST, ICE_DUST, MOTE, HAZE, SMOKE
	}
	static final class P {
		Kind kind;
		double x, y, z, vx, vy, vz;
		double age, life, size, seed, ground;
		boolean bounced;
	}
	static final class Crystal {
		double x, y, z, born, life, size, seed;
	}
	private final List<P> particles = new ArrayList<>();
	private final List<Crystal> crystals = new ArrayList<>();
	private final int cap;

	public TrailParticles(int cap) {
		this.cap = cap;
	}

	public int size() {
		return particles.size();
	}

	public void clear() {
		particles.clear();
		crystals.clear();
	}
	public void add(Kind kind, double x, double y, double z, double vx, double vy, double vz, double life, double size, double ground) {
		if (particles.size() >= cap) {
			return;
		}
		P p = new P();
		p.kind = kind;
		p.x = x;
		p.y = y;
		p.z = z;
		p.vx = vx;
		p.vy = vy;
		p.vz = vz;
		p.life = life;
		p.size = size;
		p.seed = Math.random() * 1000;
		p.ground = ground;
		particles.add(p);
	}
	public void crystal(double x, double y, double z, double life, double size, double now) {
		if (crystals.size() >= 90) {
			return;
		}
		Crystal c = new Crystal();
		c.x = x;
		c.y = y;
		c.z = z;
		c.born = now;
		c.life = life;
		c.size = size;
		c.seed = Math.random() * 1000;
		crystals.add(c);
	}
	public void step(double dt, double t) {
		for (Iterator<P> it = particles.iterator(); it.hasNext(); ) {
			P p = it.next();
			p.age += dt;
			if (p.age >= p.life) {
				it.remove();
				continue;
			}
			double f = p.age / p.life;
			double k = switch (p.kind) {
				case EMBER -> 0.4;
				case FLAME -> 1.6;
				case MOTE, ICE_DUST -> 0.9;
				default -> 1.2;
			};
			p.vx += Math.sin(p.y * 2.1 + t * 0.9 + p.z * 1.3 + p.seed) * k * dt;
			p.vy += Math.sin(p.z * 1.9 + t * 1.1 + p.x * 0.7 + p.seed * 0.5) * k * 0.5 * dt;
			p.vz += Math.sin(p.x * 1.7 + t * 0.8 + p.y * 1.1 + p.seed * 0.3) * k * dt;
			switch (p.kind) {
				case FLAME -> p.vy += 2.2 * dt;
				case FIRE_SMOKE, SMOKE -> p.vy += 0.5 * dt;
				case EMBER -> p.vy -= 6 * dt;
				case MIST -> p.vy -= 0.3 * dt;
				case MOTE -> p.vy += 0.15 * dt;
				default -> {
				}
			}
			double drag = switch (p.kind) {
				case EMBER -> 0.6;
				case MIST, SMOKE, FIRE_SMOKE, HAZE -> 2.2;
				default -> 1.4;
			};
			double d = Math.exp(-drag * dt);
			p.vx *= d;
			p.vy *= d;
			p.vz *= d;
			p.x += p.vx * dt;
			p.y += p.vy * dt;
			p.z += p.vz * dt;
			if (p.kind == Kind.EMBER && p.y < p.ground) {
				if (p.bounced) {
					p.y = p.ground;
					p.vx *= 0.5;
					p.vz *= 0.5;
					p.vy = 0;
				} else {
					p.y = p.ground;
					p.vy = -p.vy * 0.35;
					p.bounced = true;
				}
			}
			if (p.kind == Kind.MIST && p.y < p.ground + 0.02) {
				p.y = p.ground + 0.02;
				p.vy = 0;
			}
			if (f > 0.55 && p.kind == Kind.FLAME && Math.random() < dt * 3) {
				p.kind = Kind.FIRE_SMOKE;
				p.life = p.age + 0.6 + Math.random() * 0.5;
				p.size *= 1.4;
			}
		}
		crystals.removeIf(c -> t - c.born > c.life);
	}
	public void draw(Fx fx, Meshes mesh, GlowLook look, double t, Vec3 lightAt) {
		fx.additive(false);
		for (P p : particles) {
			double f = p.age / p.life;
			switch (p.kind) {
				case SMOKE -> {
					double swell = p.size * (0.6 + 1.4 * Math.sqrt(f));
					double a = Math.sin(Math.PI * Math.min(1, f * 3)) * Math.pow(1 - f, 1.2) * 0.85;
					int col = Colours.withAlpha(Colours.lerp(0xFF3A3434, look.colour(0.2), 0.2), a * look.alpha());
					fx.smoke(p.x, p.y, p.z, swell, col, p.seed, t * 0.3 + p.seed);
				}
				case FIRE_SMOKE -> {
					double swell = p.size * (0.8 + 1.2 * f);
					double a = Math.pow(1 - f, 1.5) * 0.45 * look.alpha();
					fx.smoke(p.x, p.y, p.z, swell, Colours.withAlpha(0xFF2C2420, a), p.seed, t * 0.4 + p.seed);
				}
				case MIST -> {
					double swell = p.size * (0.7 + 0.8 * f);
					double a = Math.sin(Math.PI * f) * 0.35 * look.alpha();
					fx.smoke(p.x, p.y, p.z, swell, Colours.withAlpha(Colours.lerp(0xFFE6F2FF, look.colour(0), 0.25), a), p.seed, t * 0.2 + p.seed);
				}
				default -> {
				}
			}
		}
		fx.additive(true);
		for (P p : particles) {
			double f = p.age / p.life;
			switch (p.kind) {
				case FLAME -> {
					int c = f < 0.15 ? Colours.lerp(0xFFFFF4D0, warm(look), f / 0.15) : Colours.lerp(warm(look), Colours.lerp(warm(look), 0xFF6A1000, 0.6),
							(f - 0.15) / 0.85);
					double r = p.size * (1 - 0.5 * f);
					double a = Math.min(1, (1 - f) * 1.3) * look.alpha();
					double vy = Math.max(0.4, p.vy);
					fx.streak(p.x, p.y + r * 0.8, p.z, p.vx * 0.3, vy, p.vz * 0.3, r * 3.2, r * 1.1, Colours.withAlpha(c, a));
					fx.light(p.x, p.y, p.z, r * 0.7, Colours.withAlpha(Colours.lerp(c, 0xFFFFF4D0, 0.4), a * 0.8), 0.6);
				}
				case EMBER -> {
					double a = (1 - f) * look.alpha();
					fx.light(p.x, p.y, p.z, p.size, Colours.withAlpha(Colours.lerp(0xFFFFE6A0, warm(look), f), a), 0.9);
				}
				case MOTE -> {
					double twinkle = 0.5 + 0.5 * Math.sin(t * 7 + p.seed * 13);
					double a = Math.sin(Math.PI * f) * (0.4 + 0.6 * twinkle) * look.alpha();
					fx.sparkle(p.x, p.y, p.z, p.size * (0.7 + 0.6 * twinkle), Colours.withAlpha(Colours.lerp(look.colour(p.seed % 1), 0xFFFFFFFF, 0.4), a),
							t * 0.5 + p.seed);
				}
				case HAZE -> fx.glow(p.x, p.y, p.z, p.size * (0.8 + 0.6 * f), look.colour(p.seed % 1, Math.sin(Math.PI * f) * 0.12));
				case SMOKE -> {
					if (f < 0.4) {
						fx.glow(p.x, p.y, p.z, p.size * 0.6, look.colour(p.seed % 1, (0.4 - f) * 0.6));
					}
				}
				case ICE_DUST -> {
					double twinkle = Math.max(0, Math.sin(t * 9 + p.seed * 17));
					double a = Math.sin(Math.PI * f) * twinkle * look.alpha();
					fx.light(p.x, p.y, p.z, p.size, Colours.withAlpha(Colours.lerp(0xFFEAF6FF, look.colour(0), 0.3), a), 0.9);
				}
				default -> {
				}
			}
		}
		fx.additive(false);
		if (!crystals.isEmpty()) {
			mesh.lightFrom(net.minecraft.core.BlockPos.containing(lightAt));
			for (Crystal c : crystals) {
				double age = t - c.born;
				double grow = Math.min(1, age / 0.25);
				double melt = Math.max(0, (age - c.life * 0.65) / (c.life * 0.35));
				double s = c.size * grow * (1 - melt);
				if (s <= 0.003) {
					continue;
				}
				mesh.finish(Finishes.CRYSTAL);
				mesh.pattern(Meshes.PATTERN_FRACTURE, 4);
				mesh.accent(0.25 * (1 - melt), look.colour(0));
				int col = Colours.withAlpha(Colours.lerp(0xFFCFE8FF, look.colour(0), 0.12), (1 - melt * 0.6) * look.alpha());
				for (int k = 0; k < 4; k++) {
					double a = c.seed + k * 1.7;
					double tilt = 0.15 + 0.35 * Math.abs(Math.sin(c.seed * 3 + k));
					Quaternionf q = new Quaternionf().rotationY((float) a).rotateZ((float) tilt);
					double len = s * (1.7 - k * 0.3);
					mesh.crystal(c.x + Math.cos(a) * s * 0.25, c.y + len * 0.45, c.z + Math.sin(a) * s * 0.25, q, len, s * 0.13, col, 6);
				}
			}
			mesh.lightFrom(null);
		}
	}
	private static int warm(GlowLook look) {
		return Colours.lerp(look.colour(0.2), 0xFFFF7A10, 0.3);
	}
}
