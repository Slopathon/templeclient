package nz.temple.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import nz.temple.client.util.BlockHider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Jump Circles' Earthquake style: a shock front runs out over the ground, and each block it reaches bursts up out of
 * the ground, tumbles, and drops back into its own place with a thud and a puff of dust.
 *
 * <p>Like Place Animation, the real blocks are left out of their chunk meshes while they move (see
 * {@link BlockHider}), and copies are drawn with vanilla's moving-block renderer, so they keep their real model, tint
 * and light. Every flip is a whole turn, so each block lands exactly as it was. Only what is drawn changes: the
 * blocks stay solid the whole time, so nothing about movement or the server is affected.
 *
 * <p>This is the engine only, kept in the client itself so the camera shake hook can reach it; Jump Circles holds the
 * settings and says when to start a quake.
 */
public final class GroundQuake {
	/** How long a copy keeps drawing at rest once the real blocks are back, covering the mesh rebuild. */
	private static final double HANDOFF = 0.35;
	/** The little bounces after a block lands. */
	private static final double SETTLE = 0.45;
	private static final double GRAVITY = 26;
	private static final int MAX_QUAKES = 6;
	/** Sodium builds chunk meshes itself, so the real blocks cannot be held back; the copies would only double them. */
	private static final boolean MESH_HOOK = !FabricLoader.getInstance().isModLoaded("sodium");

	/**
	 * How a quake looks: {@code radius} and {@code height} in blocks, {@code speed} of the shock front in blocks per
	 * second, {@code tumble} and {@code shake} from 0 to 1.
	 */
	public record Settings(double radius, double height, double speed, double tumble, double shake, boolean dust, boolean sound) {
	}

	private static final List<Quake> QUAKES = new ArrayList<>();
	/** Positions some running quake already moves, so two quakes never draw the same block twice. */
	private static final Set<Long> CLAIMED = new HashSet<>();
	private static ClientLevel level;
	private static boolean installed;

	private GroundQuake() {
	}

	/** One block thrown up by a quake, with whatever plant or snow sits on top of it. */
	private static final class Column {
		final BlockPos pos;
		final BlockState state;
		final BlockState top;
		final double delay;
		final double flight;
		final double peak;
		/** The turn: about a level axis, a whole number of turns (a flip) plus a wobble that is gone by landing. */
		final float axisX;
		final float axisZ;
		final double turns;
		final double wobble;
		final double driftX;
		final double driftZ;
		/**
		 * Where it takes its light from. Each face is lit from the cell beside it, so this is two cells up, where every
		 * neighbour is open air: one cell up, the underside would be lit from its own (still solid) home and turn black
		 * as the block flips. Under a low ceiling it falls back to the cell just above.
		 */
		BlockPos lightAt;
		final MovingBlockRenderState render = new MovingBlockRenderState();
		final MovingBlockRenderState renderTop = new MovingBlockRenderState();
		boolean launched;
		boolean landed;
		boolean dead;

		Column(BlockPos pos, BlockState state, BlockState top, double delay, double peak, float axisX, float axisZ, double turns,
				double wobble, double driftX, double driftZ) {
			this.pos = pos;
			this.state = state;
			this.top = top;
			this.delay = delay;
			this.peak = peak;
			this.flight = 2 * Math.sqrt(2 * peak / GRAVITY);
			this.axisX = axisX;
			this.axisZ = axisZ;
			this.turns = turns;
			this.wobble = wobble;
			this.driftX = driftX;
			this.driftZ = driftZ;
		}

		double end() {
			return delay + flight + SETTLE;
		}
	}

	private static final class Quake {
		final double x;
		final double y;
		final double z;
		final double strength;
		final Settings settings;
		final List<Column> columns;
		final List<BlockPos> hidden = new ArrayList<>();
		final long born = System.nanoTime();
		final double end;
		boolean released;

		Quake(double x, double y, double z, double strength, Settings settings, List<Column> columns) {
			this.settings = settings;
			this.x = x;
			this.y = y;
			this.z = z;
			this.strength = strength;
			this.columns = columns;
			double last = 0;
			for (Column c : columns) {
				last = Math.max(last, c.end());
			}
			this.end = last;
		}

		double age() {
			return (System.nanoTime() - born) / 1e9;
		}
	}

	/** Hooks the drawing in, once. */
	public static void install() {
		if (!installed) {
			installed = true;
			LevelRenderEvents.COLLECT_SUBMITS.register(GroundQuake::submit);
		}
	}

	/** Whether any quake is still running or handing its blocks back. */
	public static boolean active() {
		return !QUAKES.isEmpty();
	}

	/** Sets off a quake centred on {@code x, z}, on the ground at {@code y}; {@code strength} 1 is an ordinary landing. */
	public static void start(double x, double y, double z, double strength, Settings settings) {
		Minecraft mc = Minecraft.getInstance();
		if (!MESH_HOOK || mc.level == null || QUAKES.size() >= MAX_QUAKES) {
			return;
		}
		install();
		if (level != mc.level) {
			reset();
			level = mc.level;
		}
		ThreadLocalRandom r = ThreadLocalRandom.current();
		double reach = settings.radius() * Math.sqrt(strength);
		int span = (int) Math.ceil(reach);
		int baseY = (int) Math.floor(y - 0.01);
		int cx = (int) Math.floor(x);
		int cz = (int) Math.floor(z);
		double wave = settings.speed();
		double lift = settings.height() * strength;
		double spin = settings.tumble();
		List<Column> columns = new ArrayList<>();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
		for (int dx = -span; dx <= span; dx++) {
			for (int dz = -span; dz <= span; dz++) {
				double ox = cx + dx + 0.5 - x;
				double oz = cz + dz + 0.5 - z;
				double dist = Math.sqrt(ox * ox + oz * oz);
				// The ground under the feet stays put, so whoever set it off is not standing in a hole.
				if (dist > reach || dist < 1.1) {
					continue;
				}
				for (int dy = 1; dy >= -2; dy--) {
					at.set(cx + dx, baseY + dy, cz + dz);
					BlockState state = mc.level.getBlockState(at);
					if (state.getCollisionShape(mc.level, at).isEmpty()) {
						continue;
					}
					above.set(at.getX(), at.getY() + 1, at.getZ());
					BlockState topState = mc.level.getBlockState(above);
					if (!topState.getCollisionShape(mc.level, above).isEmpty() || !movable(state) || CLAIMED.contains(at.asLong())) {
						break;
					}
					BlockState top = topState.isAir() || !movable(topState) ? null : topState;
					// Strongest close in, dying away toward the edge.
					double edge = dist / reach;
					double falloff = Math.pow(1 - edge * edge, 1.3);
					double peak = lift * falloff * r.nextDouble(0.6, 1.15);
					if (peak < 0.06) {
						break;
					}
					// Roll outward, away from the centre, with a little randomness.
					float ax = (float) (-oz / dist + r.nextGaussian() * 0.35);
					float az = (float) (ox / dist + r.nextGaussian() * 0.35);
					float len = (float) Math.sqrt(ax * ax + az * az);
					ax /= len;
					az /= len;
					double turns = 0;
					if (peak > 0.7 && r.nextDouble() < spin * spin * 1.2) {
						turns = peak > 1.8 && r.nextDouble() < 0.35 ? 2 : 1;
						turns *= r.nextBoolean() ? 1 : -1;
					}
					double wobble = spin * Math.min(1, peak) * r.nextDouble(0.25, 0.65) * (r.nextBoolean() ? 1 : -1);
					double drift = Math.min(0.35, peak * 0.18) * r.nextDouble(0.4, 1);
					double delay = dist / wave + r.nextDouble(0, 0.05);
					columns.add(new Column(at.immutable(), state, top, delay, peak, ax, az, turns, wobble, ox / dist * drift,
							oz / dist * drift));
					break;
				}
			}
		}
		if (columns.isEmpty()) {
			return;
		}
		Quake quake = new Quake(x, y, z, strength, settings, columns);
		for (Column c : columns) {
			BlockPos two = c.pos.above(2);
			c.lightAt = mc.level.getBlockState(two).canOcclude() ? c.pos.above() : two;
			CLAIMED.add(c.pos.asLong());
			quake.hidden.add(c.pos);
			if (c.top != null) {
				quake.hidden.add(c.pos.above());
			}
		}
		BlockHider.hideAll(quake.hidden);
		QUAKES.add(quake);
	}

	private static boolean movable(BlockState state) {
		return state.getRenderShape() == RenderShape.MODEL && state.getFluidState().isEmpty() && !state.hasBlockEntity();
	}

	/** Puts a quake's blocks back into their chunk meshes. */
	private static void release(Quake q) {
		if (q.released) {
			return;
		}
		q.released = true;
		BlockHider.showAll(q.hidden);
		for (Column c : q.columns) {
			CLAIMED.remove(c.pos.asLong());
		}
	}

	/** Ends every quake at once and gives all their blocks back (the style was switched off, or the world changed). */
	public static void reset() {
		for (Quake q : QUAKES) {
			release(q);
		}
		QUAKES.clear();
		CLAIMED.clear();
	}

	// ---- drawing ------------------------------------------------------------------------------------------------

	private static void submit(LevelRenderContext ctx) {
		if (QUAKES.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || level != mc.level) {
			reset();
			return;
		}
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		PoseStack pose = ctx.poseStack();
		for (int i = QUAKES.size() - 1; i >= 0; i--) {
			Quake q = QUAKES.get(i);
			double age = q.age();
			if (age >= q.end) {
				release(q);
			}
			if (age >= q.end + HANDOFF) {
				QUAKES.remove(i);
				continue;
			}
			for (Column c : q.columns) {
				if (c.dead) {
					continue;
				}
				// The block was broken or changed under the effect: stop drawing the old one and give the new one back.
				if (mc.level.getBlockState(c.pos) != c.state) {
					c.dead = true;
					BlockHider.show(c.pos);
					if (c.top != null) {
						BlockHider.show(c.pos.above());
					}
					q.hidden.remove(c.pos);
					q.hidden.remove(c.pos.above());
					continue;
				}
				draw(ctx, pose, cam, q, c, age - c.delay);
			}
		}
	}

	private static void draw(LevelRenderContext ctx, PoseStack pose, Vec3 cam, Quake q, Column c, double s) {
		Minecraft mc = Minecraft.getInstance();
		double lift = 0;
		double angle = 0;
		double tilt = 0;
		double drift = 0;
		if (s > 0 && s < c.flight) {
			if (!c.launched) {
				c.launched = true;
				puff(mc, q, c, 4, 0.18);
			}
			double t = s / c.flight;
			lift = c.peak * 4 * t * (1 - t);
			// A whole number of turns, eased so it starts and stops slowly, plus a wobble that is gone by landing.
			double eased = t * t * (3 - 2 * t);
			angle = c.turns * Math.PI * 2 * eased + c.wobble * Math.sin(Math.PI * t);
			drift = Math.sin(Math.PI * t);
		} else if (s >= c.flight && s < c.flight + SETTLE) {
			if (!c.landed) {
				c.landed = true;
				land(mc, q, c);
			}
			double k = s - c.flight;
			lift = c.peak * 0.09 * Math.abs(Math.sin(k * Math.PI / 0.17)) * Math.exp(-k * 8);
			tilt = c.wobble * 0.25 * Math.sin(k * 26) * Math.exp(-k * 9);
		}
		pose.pushPose();
		pose.translate(c.pos.getX() - cam.x + 0.5 + c.driftX * drift, c.pos.getY() - cam.y + 0.5 + lift,
				c.pos.getZ() - cam.z + 0.5 + c.driftZ * drift);
		double total = angle + tilt;
		if (total != 0) {
			pose.mulPose(new Quaternionf().rotationAxis((float) total, c.axisX, 0, c.axisZ));
		}
		pose.translate(-0.5f, -0.5f, -0.5f);
		submitBlock(ctx, pose, c.render, c.pos, c.state, c.lightAt);
		if (c.top != null) {
			pose.translate(0, 1, 0);
			submitBlock(ctx, pose, c.renderTop, c.pos.above(), c.top, c.lightAt);
		}
		pose.popPose();
	}

	/**
	 * Draws one block, lit as if it stood at {@code lightAt}: open air above its home (see {@link Column#lightAt}), so no
	 * face of a block thrown up and turned over takes its light from solid ground.
	 */
	private static void submitBlock(LevelRenderContext ctx, PoseStack pose, MovingBlockRenderState state, BlockPos seed,
			BlockState block, BlockPos lightAt) {
		Minecraft mc = Minecraft.getInstance();
		state.randomSeedPos = seed;
		state.blockPos = lightAt;
		state.blockState = block;
		state.biome = mc.level.getBiome(seed);
		//? if >=26 {
		state.cardinalLighting = mc.level.cardinalLighting();
		state.lightEngine = mc.level.getLightEngine();
		ctx.submitNodeCollector().submitMovingBlock(pose, state, 0);
		//?} else {
		/*state.level = mc.level;
		ctx.submitNodeCollector().submitMovingBlock(pose, state);
		*///?}
	}

	private static void land(Minecraft mc, Quake q, Column c) {
		puff(mc, q, c, 6, 0.1);
		// A thud from about one block in five: enough to sound like a rumble without a wall of noise.
		if (q.settings.sound() && (c.pos.getX() * 31 + c.pos.getZ() * 17 & 7) < 2) {
			var type = c.state.getSoundType();
			mc.level.playLocalSound(c.pos.getX() + 0.5, c.pos.getY() + 1, c.pos.getZ() + 0.5, type.getFallSound(), SoundSource.BLOCKS,
					type.getVolume() * (float) Math.min(0.9, 0.35 + c.peak * 0.3), type.getPitch() * 0.65f, false);
		}
	}

	private static void puff(Minecraft mc, Quake q, Column c, int count, double up) {
		if (!q.settings.dust()) {
			return;
		}
		ThreadLocalRandom r = ThreadLocalRandom.current();
		BlockParticleOption particle = new BlockParticleOption(ParticleTypes.BLOCK, c.state);
		for (int i = 0; i < count; i++) {
			mc.level.addParticle(particle, c.pos.getX() + r.nextDouble(), c.pos.getY() + 1.02, c.pos.getZ() + r.nextDouble(),
					r.nextGaussian() * 0.6, up + r.nextDouble() * 0.15, r.nextGaussian() * 0.6);
		}
	}

	// ---- camera shake -------------------------------------------------------------------------------------------

	/** Shakes the view while a quake runs near the camera. Called from the view bobbing ({@code GameRendererHandMixin}). */
	public static void shake(PoseStack pose) {
		Minecraft mc = Minecraft.getInstance();
		if (QUAKES.isEmpty() || mc.player == null) {
			return;
		}
		double amount = 0;
		for (Quake q : QUAKES) {
			if (q.settings.shake() <= 0) {
				continue;
			}
			double age = q.age();
			double dx = q.x - mc.player.getX();
			double dz = q.z - mc.player.getZ();
			double near = Math.max(0, 1 - Math.sqrt(dx * dx + dz * dz) / 24);
			// A sharp jolt that rumbles on and dies away.
			amount += q.strength * near * Math.min(1, age * 20) * Math.exp(-age * 2.6) * q.settings.shake();
		}
		if (amount < 0.002) {
			return;
		}
		amount = Math.min(1.5, amount);
		double t = System.nanoTime() / 1e9;
		float roll = (float) (amount * 0.035 * (Math.sin(t * 41) * 0.6 + Math.sin(t * 67 + 1.3) * 0.4));
		float pitch = (float) (amount * 0.025 * (Math.sin(t * 53 + 2.1) * 0.6 + Math.sin(t * 29 + 0.4) * 0.4));
		pose.mulPose(new Quaternionf().rotationZ(roll).rotateX(pitch));
	}
}
