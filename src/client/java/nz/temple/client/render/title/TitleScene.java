package nz.temple.client.render.title;

import nz.temple.client.compat.Gpu;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import nz.temple.TempleNz;
import nz.temple.client.render.Theme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The title screen's world: "Temple.nz" in solid letters standing on a Minecraft landscape at golden hour. Drawn with
 * its own shaders into a texture twice the size of the window (so edges are smooth), then resolved into the frame
 * underneath the menu before the menu's glass is drawn, so the glass bends the landscape behind it.
 *
 * <p>The letters are alive: every few seconds one hops, the one under your pointer hops, a click sends one leaping
 * with a spin, and they land with a squash and a puff of dust. The sky answers the pointer with a soft glow and
 * brighter stars, and a click in the sky sends a shooting star across it.
 */
public final class TitleScene {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/title");
	private static final TitleScene INSTANCE = new TitleScene();
	private static final String WORD = "Temple.nz";
	// The scene's own depth runs the usual way (0 near, 1 far) on every version. Temple's 1.21.11 layer takes depth values
	// and tests as 26.x's reversed ones and mirrors them, so there they are given mirrored, to come out as written.
	//? if >=26 {
	private static final double CLEAR_FAR = 1.0;
	private static final CompareOp NEARER = CompareOp.LESS_THAN_OR_EQUAL;
	//?} else {
	/*private static final double CLEAR_FAR = 0.0;
	private static final CompareOp NEARER = CompareOp.GREATER_THAN_OR_EQUAL;
	*///?}
	private static final float CAP = 3.1f;
	private static final float DEPTH = 0.85f;
	/** The space between one letter's outline and the next. */
	private static final float LETTER_GAP = 0.36f;
	private static final int CONFIG_BYTES = 1152;
	private static final float GROUND = TitleTerrain.STAGE;

	public static TitleScene get() {
		return INSTANCE;
	}

	private static final class Letter {
		final LetterMesh.Glyph glyph;
		/** The middle of the letter's place in the word. */
		final float x;
		/** 0 at rest, 1 thrown or hopping, 2 dropping in, 3 held by the pointer. */
		int mode;
		double start = -100;
		double delay;
		float power;
		boolean spin;
		double lastHover = -100;
		/** How high its lowest point is off the ground, for its shadow. */
		float height;
		final Matrix4f matrix = new Matrix4f();
		/** How wide the letter is along the ground as it rests. */
		final float footprint;
		/** Where along the ground the letter is right now (its middle), for its shadow and dust. */
		float centre;
		final LetterBody body;
		/** The angle the body comes to rest at, and where its centre of mass sits when it is home. */
		final float restAngle;
		final float home;
		boolean kicked;
		/** When the current throw left the ground and how long it is in the air, for the twirl. */
		double thrownAt;
		float flight = 1;
		/** When it last came to rest, so a letter left away from home hops back after a while. */
		double restedAt;
		/** A squash on landing: when, and how hard. */
		double squashAt = -100;
		float squash;

		Letter(LetterMesh.Glyph glyph, float x, Rest rest) {
			this.glyph = glyph;
			this.x = x;
			this.centre = x;
			this.body = rest.body;
			this.footprint = rest.footprint;
			this.restAngle = rest.angle;
			this.home = x + rest.home;
		}
	}

	/** How a letter settles: its body, the angle it comes to rest at, its length along the ground, and home (from its middle). */
	private record Rest(LetterBody body, float angle, float footprint, float home) {
	}

	private static final class Dust {
		float x, y, z, vx, vy, vz, size, age, life;
		int rgb;
	}

	private final List<Letter> letters = new ArrayList<>();
	private final LetterWorld world = new LetterWorld();
	private final List<Dust> dust = new ArrayList<>();
	private final float[][] shooting = new float[4][];
	private boolean failed;
	private boolean wanted;
	private float blur;
	private float dim;
	private float focus = 0.62f;
	private float blurShown;
	private float focusShown = 0.62f;
	private double time;
	private long lastNanos = System.nanoTime();
	private double nextHop = 2;
	private double nextShooting = 5;
	private float pointerX = 0.5f;
	private float pointerY = 0.5f;
	private float pointerGlow;
	private float parallaxX;
	private float parallaxY;
	private double clickAt = -100;
	private float clickX;
	private float clickY;
	private boolean hasPointer;
	private int hovered = -1;
	private float stageHalfWidth = 10;
	private float wordWidth = 20;

	/** A press on a letter that has not yet become a drag or a click. */
	private int pressedLetter = -1;
	private double pressedAt;
	private double pressX;
	private double pressY;
	private boolean dragging;

	private RenderPipeline scenePipeline;
	private RenderPipeline skyPipeline;
	private RenderPipeline resolvePipeline;
	private GpuBuffer terrain;
	private int terrainIndices;
	private GpuBuffer letterBuffer;
	private int letterIndices;
	private GpuTextureView builtFor;
	private StagedVertexBuffer dynamic;
	private GpuBuffer config;
	private GpuBuffer resolveConfig;
	private final ByteBuffer configBytes = ByteBuffer.allocateDirect(CONFIG_BYTES).order(ByteOrder.nativeOrder());
	private final ByteBuffer resolveBytes = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
	private GpuTexture color;
	private GpuTextureView colorView;
	private GpuTexture depth;
	private GpuTextureView depthView;
	private int targetW;
	private int targetH;
	private final Matrix4f viewProj = new Matrix4f();
	private final Matrix4f invViewProj = new Matrix4f();
	private final Vector3f camera = new Vector3f();
	private int screenW = 1;
	private int screenH = 1;
	private boolean quality = true;

	private TitleScene() {
	}

	// ---- what screens call ------------------------------------------------------------------------------------------

	/**
	 * Asks for the scene behind this frame. {@code focus} is where across the screen the letters should sit (0.5 is
	 * the middle), {@code blur} (0..1) softens it behind a panel and {@code dim} darkens it behind other menus.
	 */
	public void request(float focus, float blur, float dim, boolean highQuality) {
		wanted = true;
		this.focus = focus;
		this.blur = blur;
		this.dim = dim;
		this.quality = highQuality;
	}

	/** The pointer, in GUI coordinates, for the sky's glow, the parallax and hovering letters. */
	public void pointer(double x, double y, int guiW, int guiH) {
		// Tabbed out (or minimised, when Windows reports the cursor at -32000), the pointer means nothing: the camera
		// used to lean after it, swing right round the letters and race back, so the whole sky spun.
		if (!Minecraft.getInstance().isWindowActive() || x < 0 || y < 0 || x > guiW || y > guiH) {
			hasPointer = false;
			return;
		}
		pointerX = (float) Math.max(0, Math.min(1, x / Math.max(1, guiW)));
		pointerY = (float) Math.max(0, Math.min(1, y / Math.max(1, guiH)));
		hasPointer = true;
	}

	/** Letters drop in from the sky, one after another. */
	public void intro() {
		world.release();
		for (int i = 0; i < letters.size(); i++) {
			Letter l = letters.get(i);
			l.mode = 2;
			l.start = time;
			l.delay = 0.25 + i * 0.085;
			l.kicked = false;
		}
		introPending = letters.isEmpty();
	}

	private boolean introPending = true;

	/** A click at (x, y) GUI: a letter leaps (its index is returned), or a shooting star crosses the sky (-1 is returned). */
	public int click(double x, double y, int guiW, int guiH) {
		int hit = letterAt(x, y, guiW, guiH);
		if (hit >= 0) {
			jump(letters.get(hit), 1.35f, true);
			return hit;
		}
		sky(x, y, guiW, guiH);
		return -1;
	}

	private void sky(double x, double y, int guiW, int guiH) {
		float u = (float) (x / guiW);
		float v = (float) (y / guiH);
		clickAt = time;
		clickX = u;
		clickY = 1 - v;
		launchShootingStar(u, 1 - v);
	}

	/**
	 * The mouse went down at (x, y) GUI. On a letter it is held: moving turns it into a drag (the letter hangs from
	 * where you took it and follows), letting go quickly is a click (it leaps). Elsewhere a star shoots. Returns the
	 * letter's index, or -1 for the sky.
	 */
	public int press(double x, double y, int guiW, int guiH) {
		int hit = letterAt(x, y, guiW, guiH);
		if (hit < 0) {
			sky(x, y, guiW, guiH);
			return -1;
		}
		pressedLetter = hit;
		pressedAt = time;
		pressX = x;
		pressY = y;
		dragging = false;
		return hit;
	}

	/** The mouse moved to (x, y) GUI with the button down. */
	public void drag(double x, double y, int guiW, int guiH) {
		if (pressedLetter < 0) {
			return;
		}
		float[] at = groundPlane(x, y, guiW, guiH);
		if (at == null) {
			return;
		}
		if (!dragging && Math.hypot(x - pressX, y - pressY) > 4) {
			float[] from = groundPlane(pressX, pressY, guiW, guiH);
			Letter l = letters.get(pressedLetter);
			if (from != null) {
				dragging = true;
				l.mode = 3;
				world.grab(l.body, from[0], from[1]);
			}
		}
		if (dragging) {
			world.target(at[0], Math.max(0.2f, at[1]));
		}
	}

	/** The mouse came up. True when it was a click on a letter (which then leaps), false for a drag or nothing. */
	public boolean release() {
		int was = pressedLetter;
		pressedLetter = -1;
		if (was < 0) {
			return false;
		}
		if (dragging) {
			dragging = false;
			Letter l = letters.get(was);
			world.release();
			// Thrown: it is in the air now, and hops home once it has come to rest for a while.
			l.mode = 1;
			l.kicked = true;
			l.spin = false;
			return false;
		}
		jump(letters.get(was), 1.35f, true);
		return true;
	}

	/** The letter the mouse went down on and has not let go of, or -1. */
	public int pressedLetter() {
		return pressedLetter;
	}

	/** True while a letter is held by the pointer. */
	public boolean holding() {
		return dragging;
	}

	/** Every letter leaps at once, with a spin (the showcase uses it to catch them mid-air). */
	public void jumpAll() {
		for (int i = 0; i < letters.size(); i++) {
			Letter l = letters.get(i);
			l.mode = 0;
			jump(l, 1.2f, true);
			l.delay = i * 0.04;
		}
	}

	/** Development only (the showcase): each letter's angle (degrees), whether it sleeps, and its mode. */
	public String showcaseState() {
		StringBuilder sb = new StringBuilder();
		for (Letter l : letters) {
			sb.append(l.glyph.c()).append(String.format(" %.1f%s m%d t%b rest%.1f; ", Math.toDegrees(l.body.angle),
					l.body.asleep ? "z" : "", l.mode, l.body.touching, Math.toDegrees(l.restAngle)));
		}
		return sb.toString();
	}

	/** Development only (the showcase): picks a letter up by its middle and moves it to world (x, height). */
	public void showcaseDrag(int index, float x, float y) {
		if (index < 0 || index >= letters.size()) {
			return;
		}
		Letter l = letters.get(index);
		if (world.held() != l.body) {
			l.mode = 3;
			world.grab(l.body, l.body.x, l.body.y);
			dragging = true;
			pressedLetter = index;
		}
		world.target(x, y);
	}

	// ---- animation --------------------------------------------------------------------------------------------------

	private float frameDt;

	private void advance() {
		long now = System.nanoTime();
		double dt = Math.min(0.1, (now - lastNanos) / 1e9);
		lastNanos = now;
		time += dt;
		frameDt = (float) dt;
		blurShown += (blur - blurShown) * (float) Math.min(1, dt * 7);
		focusShown += (focus - focusShown) * (float) Math.min(1, dt * 5);
		pointerGlow += ((hasPointer ? 1 : 0) - pointerGlow) * (float) Math.min(1, dt * 3);
		// Without a pointer the lean eases back to the middle.
		float leanX = hasPointer ? pointerX - 0.5f : 0;
		float leanY = hasPointer ? pointerY - 0.5f : 0;
		parallaxX += (leanX - parallaxX) * (float) Math.min(1, dt * 2.5);
		parallaxY += (leanY - parallaxY) * (float) Math.min(1, dt * 2.5);
		if (time > nextHop && !letters.isEmpty()) {
			nextHop = time + ThreadLocalRandom.current().nextDouble(2.2, 4.5);
			Letter l = letters.get(ThreadLocalRandom.current().nextInt(letters.size()));
			if (l.mode == 0) {
				jump(l, ThreadLocalRandom.current().nextFloat(0.5f, 0.85f), false);
			}
		}
		if (time > nextShooting) {
			nextShooting = time + ThreadLocalRandom.current().nextDouble(6, 13);
			launchShootingStar(ThreadLocalRandom.current().nextFloat(0.2f, 0.95f), ThreadLocalRandom.current().nextFloat(0.72f, 0.95f));
		}
		for (Letter l : letters) {
			launch(l);
		}
		world.advance(frameDt);
		for (Letter l : letters) {
			settleAndPlace(l);
		}
		for (int i = dust.size() - 1; i >= 0; i--) {
			Dust d = dust.get(i);
			d.age += (float) dt;
			d.vy -= 9.5f * (float) dt;
			d.x += d.vx * dt;
			d.y += d.vy * dt;
			d.z += d.vz * dt;
			if (d.y < GROUND + d.size) {
				d.y = GROUND + d.size;
				d.vy *= -0.3f;
				d.vx *= 0.6f;
				d.vz *= 0.6f;
			}
			if (d.age > d.life) {
				dust.remove(i);
			}
		}
	}

	private void jump(Letter l, float power, boolean spin) {
		if (l.mode == 3 || l.mode == 1 && time - l.start < 0.3 || !l.body.grounded()) {
			return;
		}
		l.mode = 1;
		l.start = time;
		l.delay = 0;
		l.power = power;
		l.spin = spin;
		l.kicked = false;
	}

	/**
	 * Starts what a letter was asked to do: the drop in (from high above, landing flat on its resting edge when the
	 * others would), a hop or leap (thrown, aimed to come down on that edge again, and drifting home).
	 */
	private void launch(Letter l) {
		LetterBody b = l.body;
		double t = time - l.start - l.delay;
		if (l.mode == 2 && t < 0) {
			b.hold(l.home, b.restHeight(0) + 40, 0);
		} else if (l.mode == 2 && !l.kicked) {
			l.kicked = true;
			b.hold(l.home, b.restHeight(l.restAngle) + 14, 0);
			throwLetter(l, -17.7f);
		} else if (l.mode == 1 && !l.kicked && t >= 0) {
			l.kicked = true;
			throwLetter(l, (float) Math.sqrt(2 * LetterBody.GRAVITY * 1.2f * l.power));
		} else if (l.mode == 0 && time - l.restedAt > 2.5 && away(l)) {
			// Left somewhere else by a throw: after a moment it makes its own way home, turning upright as it goes.
			float distance = Math.abs(l.home - b.x);
			jump(l, Math.min(1.6f, 0.7f + distance * 0.08f), false);
		}
	}

	private static boolean away(Letter l) {
		return Math.abs(l.home - l.body.x) > 0.25f
				|| !l.body.round() && Math.abs(Math.IEEEremainder(l.restAngle - l.body.angle, Math.PI * 2)) > 0.15;
	}

	/** After the world has moved: dust and a squash on landings, coming to rest, and the letter's matrix. */
	private void settleAndPlace(Letter l) {
		LetterBody b = l.body;
		if (b.impact > 4) {
			float power = Math.max(0.35f, Math.min(1.3f, b.impact / 26));
			puff(l, power);
			if (time - l.squashAt > 0.25) {
				l.squashAt = time;
				l.squash = Math.min(0.22f, b.impact / 150);
			}
		}
		b.impact = 0;
		if (l.mode != 0 && l.mode != 3 && l.kicked && b.asleep) {
			l.mode = 0;
			l.restedAt = time;
		}
		float[] span = b.span();
		l.centre = (span[0] + span[1]) / 2;
		l.height = Math.max(0, b.y - b.restHeight(b.angle));
		// A click twirls it about the upright; that turn never changes what touches the ground.
		float twirl = 0;
		if (l.mode == 1 && l.spin && l.kicked) {
			double u = Math.min(1, (time - l.thrownAt) / l.flight);
			twirl = (float) ((u < 0.5 ? 2 * u * u : 1 - Math.pow(-2 * u + 2, 2) / 2) * Math.PI * 2);
		}
		// The landing squash, pressed down onto the ground and wobbling back.
		double since = time - l.squashAt;
		float sy = since < 0.8 ? 1 - (float) (Math.exp(-since * 8) * Math.cos(since * 24) * l.squash) : 1;
		float sxz = (float) (1 / Math.sqrt(Math.max(0.3f, sy)));
		l.matrix.identity()
				.translate(l.centre, GROUND, 0).scale(sxz, sy, sxz).translate(-l.centre, -GROUND, 0)
				.translate(b.x, GROUND + b.y, 0).rotateY(twirl).rotateZ(b.angle).translate(-b.comX, -b.comY, 0);
	}

	/**
	 * Throws a letter upward at {@code vy} from where it is, turning just enough to come down on its resting edge, and
	 * drifting back home on the way.
	 */
	private void throwLetter(Letter l, float vy) {
		LetterBody b = l.body;
		float g = LetterBody.GRAVITY;
		float drop = Math.max(0, b.y - b.restHeight(l.restAngle));
		float flight = (vy + (float) Math.sqrt(vy * vy + 2 * g * drop)) / g;
		float turn = (float) Math.IEEEremainder(l.restAngle - b.angle, Math.PI * 2);
		b.set(b.x, b.y, b.angle, (l.home - b.x) / flight, vy, turn / flight);
		l.thrownAt = time;
		l.flight = flight;
	}

	private void puff(Letter l, float power) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		int count = (int) (10 + 10 * power);
		for (int i = 0; i < count && dust.size() < 220; i++) {
			Dust d = new Dust();
			double a = r.nextDouble(Math.PI * 2);
			float out = l.footprint * 0.5f + 0.2f;
			d.x = l.centre + (float) Math.cos(a) * out * r.nextFloat(0.4f, 1f);
			d.z = (float) Math.sin(a) * (DEPTH * 0.5f + 0.25f);
			d.y = GROUND + 0.05f;
			float speed = r.nextFloat(1.2f, 3.2f) * power;
			d.vx = (float) Math.cos(a) * speed;
			d.vz = (float) Math.sin(a) * speed;
			d.vy = r.nextFloat(1.5f, 3.5f) * power;
			d.size = r.nextFloat(0.035f, 0.08f);
			d.life = r.nextFloat(0.6f, 1.1f);
			d.rgb = r.nextBoolean() ? 0x7E5A3C : 0x7FAE4F;
			dust.add(d);
		}
	}

	private void launchShootingStar(float u, float v) {
		for (int i = 0; i < shooting.length; i++) {
			if (shooting[i] == null || time - shooting[i][3] > 1.4) {
				float angle = (float) (Math.PI + ThreadLocalRandom.current().nextDouble(0.25, 0.6) * (u > 0.5 ? 1 : -1));
				if (u <= 0.5) {
					angle = (float) (-ThreadLocalRandom.current().nextDouble(0.25, 0.6));
				}
				shooting[i] = new float[] {u, v, angle, (float) time};
				return;
			}
		}
	}

	// ---- picking ----------------------------------------------------------------------------------------------------

	/**
	 * Where the pointer at (x, y) GUI meets the letters' plane (z = 0), as {along the ground, height above it}, or null
	 * when the ray runs parallel to it.
	 */
	private float[] groundPlane(double x, double y, int guiW, int guiH) {
		float ndcX = (float) (x / guiW * 2 - 1);
		float ndcY = (float) (1 - y / guiH * 2);
		boolean zeroToOne = Gpu.depthZeroToOne();
		Vector4f near = new Vector4f(ndcX, ndcY, zeroToOne ? 0 : -1, 1);
		Vector4f far = new Vector4f(ndcX, ndcY, 1, 1);
		invViewProj.transform(near);
		invViewProj.transform(far);
		near.div(near.w);
		far.div(far.w);
		float dz = far.z - near.z;
		if (Math.abs(dz) < 1e-6f) {
			return null;
		}
		float t = -near.z / dz;
		if (t < 0) {
			return null;
		}
		return new float[] {near.x + (far.x - near.x) * t, near.y + (far.y - near.y) * t - GROUND};
	}

	/** The letter under the pointer: the one whose outline holds the point where the pointer meets their plane. */
	private int letterAt(double x, double y, int guiW, int guiH) {
		float[] at = groundPlane(x, y, guiW, guiH);
		if (at == null) {
			return -1;
		}
		int best = -1;
		float bestDistance = 0.25f;
		for (int i = 0; i < letters.size(); i++) {
			float d = outside(letters.get(i).body, at[0], at[1]);
			if (d < bestDistance) {
				bestDistance = d;
				best = i;
			}
		}
		return best;
	}

	/** How far (px, py) is outside the body's outline (0 or less when inside). */
	private static float outside(LetterBody b, float px, float py) {
		float worst = -Float.MAX_VALUE;
		int n = b.wx.length;
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			float ex = b.wx[j] - b.wx[i], ey = b.wy[j] - b.wy[i];
			float len = (float) Math.hypot(ex, ey);
			if (len < 1e-5f) {
				continue;
			}
			float d = ((px - b.wx[i]) * ey - (py - b.wy[i]) * ex) / len;
			worst = Math.max(worst, d);
		}
		return worst;
	}

	/** Hover: the letter under the pointer gives a little hop (not more than once a second). */
	private void hover() {
		if (!hasPointer || letters.isEmpty() || dragging || pressedLetter >= 0) {
			return;
		}
		int at = letterAt(pointerX * screenW, pointerY * screenH, screenW, screenH);
		if (at != hovered && at >= 0) {
			Letter l = letters.get(at);
			if (l.mode == 0 && time - l.lastHover > 1.0) {
				l.lastHover = time;
				jump(l, 0.55f, false);
			}
		}
		hovered = at;
	}

	// ---- drawing ----------------------------------------------------------------------------------------------------

	/** Called just before the GUI draws: renders the scene into the frame if a screen asked for it this frame. */
	public void render() {
		if (!wanted || failed) {
			wanted = false;
			return;
		}
		wanted = false;
		try {
			draw();
		} catch (RuntimeException e) {
			failed = true;
			LOGGER.error("Title scene failed; menus fall back to the flat backdrop", e);
		}
	}

	/** True while the scene can be drawn; false once it failed (menus then use the flat backdrop). */
	public boolean available() {
		return !failed;
	}

	private void draw() {
		Minecraft mc = Minecraft.getInstance();
		RenderTarget main = mc.gameRenderer.mainRenderTarget();
		GpuDevice device = RenderSystem.getDevice();
		TextureAtlas atlas = mc.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
		if (scenePipeline == null) {
			buildPipelines();
		}
		if (letters.isEmpty()) {
			buildLetters(device);
		}
		if (terrain == null || builtFor != atlas.getTextureView()) {
			buildTerrain(device, atlas);
		}
		if (introPending && !letters.isEmpty()) {
			introPending = false;
			intro();
		}
		screenW = mc.getWindow().getGuiScaledWidth();
		screenH = mc.getWindow().getGuiScaledHeight();
		advance();
		hover();
		int scale = quality && main.width * 2 <= 5120 ? 2 : 1;
		ensureTargets(device, main.width * scale, main.height * scale);
		camera(main.width, main.height);
		writeConfig();
		CommandEncoder encoder = device.createCommandEncoder();
		encoder.writeToBuffer(config.slice(), configBytes);

		// The sky fills the whole picture first.
		try (RenderPass pass = Gpu.openPass(encoder, () -> "Temple.nz title sky", colorView, Optional.empty())) {
			pass.setPipeline(nz.temple.client.compat.Gpu.pipeline(skyPipeline));
			pass.setUniform("SceneConfig", config);
			Gpu.drawFullscreen(pass);
		}
		// Then the land, the shadows and dust, and the letters, depth-tested against each other.
		buildDynamic();
		var quadIndices = Gpu.sequentialBuffer(PrimitiveTopology.QUADS);
		// Sized once for the largest draw, and after anything else that might ask for it, so the shared index buffer is
		// not regrown (and the one bound here freed) before the pass is done with it.
		GpuBuffer indices = quadIndices.getBuffer(Math.max(6, Math.max(terrainIndices, letterIndices)));
		StagedVertexBuffer.ExecuteInfo dyn = dynamicInfo;
		try (RenderPass pass = Gpu.openPass(encoder, () -> "Temple.nz title world", colorView, Optional.empty(), depthView, OptionalDouble.of(CLEAR_FAR))) {
			pass.setPipeline(nz.temple.client.compat.Gpu.pipeline(scenePipeline));
			pass.setUniform("SceneConfig", config);
			pass.bindTexture("Sampler0", atlas.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST, true));
			if (terrainIndices > 0) {
				Gpu.vertices(pass, terrain);
				pass.setIndexBuffer(indices, quadIndices.type());
				Gpu.drawIndexed(pass, terrainIndices, 0, 0);
			}
			if (dyn != null) {
				Gpu.vertices(pass, dyn.vertexBuffer());
				pass.setIndexBuffer(dyn.indexBuffer(), dyn.indexType());
				Gpu.drawIndexed(pass, dyn.indexCount(), dyn.firstIndex(), dyn.baseVertex());
			}
			if (letterIndices > 0) {
				Gpu.vertices(pass, letterBuffer);
				pass.setIndexBuffer(indices, quadIndices.type());
				Gpu.drawIndexed(pass, letterIndices, 0, 0);
			}
		}
		if (dynamic != null && dynamicDraw != null) {
			dynamic.endFrame();
			dynamicDraw = null;
			dynamicInfo = null;
		}
		// Finally into the frame, at its real size, softened and darkened as asked.
		Std140Builder.intoBuffer(resolveBytes.clear()).putVec4(blurShown * 9f, dim, 0.55f, 0);
		encoder.writeToBuffer(resolveConfig.slice(), resolveBytes.flip());
		try (RenderPass pass = Gpu.openPass(encoder, () -> "Temple.nz title resolve", main.getColorTextureView(), Optional.empty())) {
			pass.setPipeline(nz.temple.client.compat.Gpu.pipeline(resolvePipeline));
			pass.setUniform("ResolveConfig", resolveConfig);
			pass.bindTexture("SceneSampler", colorView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			Gpu.drawFullscreen(pass);
		}
	}

	private StagedVertexBuffer.Draw dynamicDraw;
	private StagedVertexBuffer.ExecuteInfo dynamicInfo;

	/** Contact shadows under the letters and the dust in the air, rebuilt every frame. */
	private void buildDynamic() {
		if (dynamic == null) {
			dynamic = new StagedVertexBuffer(() -> "Temple.nz title dynamic", RenderType.SMALL_BUFFER_SIZE);
		}
		dynamicDraw = dynamic.appendDraw(Gpu.vertexFormat(scenePipeline), PrimitiveTopology.QUADS, null);
		VertexConsumer vc = dynamic.getVertexBuilder(dynamicDraw);
		for (Letter l : letters) {
			if (l.glyph.quads().isEmpty()) {
				continue;
			}
			float h = Math.max(0, Math.min(20, l.height));
			float strength = 0.95f / (1 + h * 0.8f);
			float rx = l.footprint * 0.55f + 0.35f + h * 0.08f;
			float rz = DEPTH * 0.5f + 0.55f + h * 0.08f;
			float y = GROUND + 0.015f;
			int a = 251;
			int s = Math.round(strength * 255);
			float cx = l.centre;
			vc.addVertex(cx - rx, y, -rz).setUv(-1, -1).setColor(s, 0, 0, a).setNormal(0, 1, 0);
			vc.addVertex(cx + rx, y, -rz).setUv(1, -1).setColor(s, 0, 0, a).setNormal(0, 1, 0);
			vc.addVertex(cx + rx, y, rz).setUv(1, 1).setColor(s, 0, 0, a).setNormal(0, 1, 0);
			vc.addVertex(cx - rx, y, rz).setUv(-1, 1).setColor(s, 0, 0, a).setNormal(0, 1, 0);
		}
		for (Dust d : dust) {
			float fade = 1 - Math.max(0, d.age / d.life - 0.6f) / 0.4f;
			float s = d.size * fade;
			if (s <= 0.002f) {
				continue;
			}
			box(vc, d.x, d.y, d.z, s, d.rgb);
		}
		dynamic.upload();
		dynamicInfo = dynamic.getExecuteInfo(dynamicDraw);
	}

	private static void box(VertexConsumer vc, float x, float y, float z, float s, int rgb) {
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		float[][] n = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		for (float[] f : n) {
			float[] u = f[0] != 0 ? new float[] {0, 1, 0} : new float[] {1, 0, 0};
			float[] w = {f[1] * u[2] - f[2] * u[1], f[2] * u[0] - f[0] * u[2], f[0] * u[1] - f[1] * u[0]};
			float[][] sgn = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
			for (float[] c : sgn) {
				vc.addVertex(x + (f[0] + u[0] * c[0] + w[0] * c[1]) * s, y + (f[1] + u[1] * c[0] + w[1] * c[1]) * s,
						z + (f[2] + u[2] * c[0] + w[2] * c[1]) * s).setUv(0, 0).setColor(r, g, b, 250).setNormal(f[0], f[1], f[2]);
			}
		}
	}

	/**
	 * The camera: a little above and in front of the letters, drifting slowly side to side and leaning after your
	 * pointer. The lens is shifted so the letters sit where the screen asked for them.
	 */
	private void camera(int width, int height) {
		float wordCentre = 0;
		Vector3f target = new Vector3f(wordCentre, GROUND + CAP * 0.72f, 0);
		float sway = (float) Math.sin(time * 0.045) * 0.06f + parallaxX * 0.08f;
		float lift = -parallaxY * 0.7f;
		// Far enough back that the whole word fills a little over half the screen's width.
		float aspect = width / (float) Math.max(1, height);
		float halfWidth = (float) Math.tan(Math.toRadians(20)) * aspect;
		float dist = Math.max(16, wordWidth / (0.56f * 2 * halfWidth));
		camera.set(target.x + (float) Math.sin(sway) * dist, target.y + 1.1f + lift, target.z + (float) Math.cos(sway) * dist);
		Matrix4f view = new Matrix4f().lookAt(camera, target, new Vector3f(0, 1, 0));
		boolean zeroToOne = Gpu.depthZeroToOne();
		Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(40), width / (float) Math.max(1, height), 0.2f, 600f, zeroToOne);
		// Lens shift: the letters land at focusShown across the screen without turning the camera.
		float shift = (focusShown - 0.5f) * 2f;
		new Matrix4f().translation(shift, 0, 0).mul(projection, projection);
		projection.mul(view, viewProj);
		viewProj.invert(invViewProj);
	}

	private void writeConfig() {
		configBytes.clear();
		Std140Builder b = Std140Builder.intoBuffer(configBytes);
		b.putMat4f(viewProj);
		b.putMat4f(invViewProj);
		b.putVec4(camera.x, camera.y, camera.z, 1);
		// The low sun sits behind the scene to the left; the light on the letters comes from the front right.
		Vector3f sun = new Vector3f(-0.55f, 0.12f, -0.83f).normalize();
		b.putVec4(sun.x, sun.y, sun.z, 0);
		Vector3f key = new Vector3f(0.45f, 0.62f, 0.64f).normalize();
		b.putVec4(key.x, key.y, key.z, 0);
		b.putVec4(0.16f, 0.2f, 0.42f, 1);
		b.putVec4(1.05f, 0.62f, 0.38f, 1);
		b.putVec4(1.18f, 0.9f, 0.66f, 0.022f);
		b.putVec4(0.62f, 0.58f, 0.72f, 0.0052f);
		int accent = Theme.primary();
		b.putVec4(srgb((accent >> 16) & 0xFF), srgb((accent >> 8) & 0xFF), srgb(accent & 0xFF), 1.2f);
		b.putVec4((float) time, 1f, screenW, screenH);
		b.putVec4(pointerX, 1 - pointerY, pointerGlow, (float) (time - clickAt));
		b.putVec4(clickX, clickY, 0, 0);
		for (float[] s : shooting) {
			if (s == null) {
				b.putVec4(0, 0, 0, -1);
			} else {
				b.putVec4(s[0], s[1], s[2], (float) (time - s[3]));
			}
		}
		for (int i = 0; i < 12; i++) {
			b.putMat4f(i < letters.size() ? letters.get(i).matrix : new Matrix4f());
		}
		configBytes.flip();
	}

	private static float srgb(int c) {
		return (float) Math.pow(c / 255.0, 2.2) * 1.4f;
	}

	// ---- resources --------------------------------------------------------------------------------------------------

	private void buildPipelines() {
		BindGroupLayout sceneLayout = BindGroupLayout.builder().withSampler("Sampler0")
				.withUniform("SceneConfig", UniformType.UNIFORM_BUFFER).build();
		scenePipeline = RenderPipeline.builder()
				.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "pipeline/title_scene"))
				.withVertexShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/title_scene"))
				.withFragmentShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/title_scene"))
				.withBindGroupLayout(sceneLayout)
				.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL)
				.withPrimitiveTopology(PrimitiveTopology.QUADS)
				.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
				.withDepthStencilState(new DepthStencilState(NEARER, true))
				.withCull(false)
				.build();
		skyPipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
				.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "pipeline/title_sky"))
				.withVertexShader("core/screenquad")
				.withFragmentShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/title_sky"))
				.withBindGroupLayout(BindGroupLayout.builder().withUniform("SceneConfig", UniformType.UNIFORM_BUFFER).build())
				.withColorTargetState(ColorTargetState.DEFAULT)
				.withDepthStencilState(Optional.empty())
				.build();
		resolvePipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
				.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "pipeline/title_resolve"))
				.withVertexShader("core/screenquad")
				.withFragmentShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/title_resolve"))
				.withBindGroupLayout(BindGroupLayout.builder().withSampler("SceneSampler")
						.withUniform("ResolveConfig", UniformType.UNIFORM_BUFFER).build())
				.withColorTargetState(ColorTargetState.DEFAULT)
				.withDepthStencilState(Optional.empty())
				.build();
		GpuDevice device = RenderSystem.getDevice();
		int usage = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
		config = device.createBuffer(() -> "Temple.nz title config", usage, CONFIG_BYTES);
		resolveConfig = device.createBuffer(() -> "Temple.nz title resolve config", usage, 16);
	}

	/** Startup check for the smoke test: the three pipelines compile. Returns null when fine. */
	public String selfTest() {
		try {
			if (scenePipeline == null) {
				buildPipelines();
			}
			for (RenderPipeline p : new RenderPipeline[] {scenePipeline, skyPipeline, resolvePipeline}) {
				if (!nz.temple.client.compat.Gpu.precompile(p)) {
					return p.getLocation().toString();
				}
			}
			return null;
		} catch (RuntimeException e) {
			return "title scene: " + e;
		}
	}

	private void buildLetters(GpuDevice device) {
		List<LetterMesh.Glyph> glyphs = LetterMesh.build(WORD, "/assets/" + TempleNz.MOD_ID + "/font/inter_semibold.ttf", CAP, DEPTH);
		if (glyphs.isEmpty()) {
			throw new IllegalStateException("letter font missing");
		}
		// Every letter is a real body, set down where it would settle. Most stand up; the p would stand on its tail,
		// which sinks into the ground, so it tips over onto the edge where its tail and its bowl both touch the ground,
		// and takes up that length along the ground.
		Rest[] rests = new Rest[glyphs.size()];
		float[] slots = new float[glyphs.size()];
		float total = 0;
		for (int i = 0; i < glyphs.size(); i++) {
			LetterMesh.Glyph g = glyphs.get(i);
			rests[i] = settle(g);
			// Spaced by their outlines rather than the font's kerning: a T's outline fills the space under its arm,
			// which the font tucks the e into, and letters that touch at rest jostle for ever.
			slots[i] = rests[i].footprint();
			total += slots[i] + LETTER_GAP;
		}
		total -= LETTER_GAP;
		float x = -total / 2;
		int quads = 0;
		for (int i = 0; i < glyphs.size(); i++) {
			LetterMesh.Glyph g = glyphs.get(i);
			letters.add(new Letter(g, x + slots[i] / 2, rests[i]));
			x += slots[i] + LETTER_GAP;
			quads += g.quads().size();
		}
		stageHalfWidth = total / 2 + 2.5f;
		wordWidth = total;
		world.bodies.clear();
		for (Letter l : letters) {
			// The stage's flat ground ends a little inside its edge; thrown letters stay on it.
			l.body.wallMin = -stageHalfWidth + 0.6f;
			l.body.wallMax = stageHalfWidth - 0.6f;
			l.body.restAngle = l.restAngle;
			l.body.hold(l.home, l.body.restHeight(l.restAngle), l.restAngle);
			world.bodies.add(l.body);
		}
		try (ByteBufferBuilder bytes = new ByteBufferBuilder(Math.max(256, quads * 4 * DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL.getVertexSize()))) {
			BufferBuilder builder = Gpu.bufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
			for (int i = 0; i < letters.size(); i++) {
				Letter l = letters.get(i);
				for (LetterMesh.Quad q : l.glyph.quads()) {
					float[] v = q.v();
					for (int k = 0; k < 4; k++) {
						int o = k * 6;
						// The x is kept relative to the letter's own centre; its matrix places it.
						builder.addVertex(v[o] - 0, v[o + 1], v[o + 2]).setUv(q.side(), 0).setColor(255, 255, 255, i)
								.setNormal(v[o + 3], v[o + 4], v[o + 5]);
					}
				}
			}
			try (MeshData mesh = builder.build()) {
				if (mesh != null) {
					letterIndices = mesh.drawState().indexCount();
					letterBuffer = device.createBuffer(() -> "Temple.nz title letters", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
				}
			}
		}
	}

	/**
	 * Lets the letter topple from standing on its end and settle, to find where it comes to rest: the angle, its
	 * length along the ground, and where its centre of mass must be for that length to sit centred on its slot.
	 */
	private static Rest settle(LetterMesh.Glyph g) {
		LetterBody b = LetterBody.of(g);
		if (b == null) {
			// No shape to speak of (a space): a small invisible block.
			float w = Math.max(0.2f, g.width()) / 2;
			b = new LetterBody(List.of(new float[] {-w, 0}, new float[] {w, 0}, new float[] {w, 0.2f}, new float[] {-w, 0.2f}), 0, 0.1f, 0.05f, 0.1f);
		}
		float angle = 0;
		if (!b.standsUpright()) {
			b.set(0, b.restHeight(0) + 0.01f, 0, 0, -2, 0);
			b.settle(12);
			angle = (float) Math.IEEEremainder(b.angle, Math.PI * 2);
		}
		b.hold(0, b.restHeight(angle), angle);
		float[] span = b.span();
		return new Rest(b, angle, span[1] - span[0], -(span[0] + span[1]) / 2);
	}

	private void buildTerrain(GpuDevice device, TextureAtlas atlas) {
		if (terrain != null) {
			terrain.close();
			terrain = null;
		}
		builtFor = atlas.getTextureView();
		try (ByteBufferBuilder bytes = new ByteBufferBuilder(4 << 20)) {
			BufferBuilder builder = Gpu.bufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
			TitleTerrain.build(stageHalfWidth, name -> {
				TextureAtlasSprite sprite = atlas.getSprite(Identifier.withDefaultNamespace(name));
				return new float[] {sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1()};
			}, (xyz, uv, rgb, nx, ny, nz, kind) -> {
				for (int k = 0; k < 4; k++) {
					int c = rgb[k];
					// Plant tops sway; their bottoms stay rooted.
					int id = kind == TitleTerrain.KIND_SWAY && k < 2 ? TitleTerrain.KIND_CUTOUT : kind;
					builder.addVertex(xyz[k * 3], xyz[k * 3 + 1], xyz[k * 3 + 2]).setUv(uv[k * 2], uv[k * 2 + 1])
							.setColor((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, id).setNormal(nx, ny, nz);
				}
			});
			try (MeshData mesh = builder.build()) {
				if (mesh != null) {
					terrainIndices = mesh.drawState().indexCount();
					terrain = device.createBuffer(() -> "Temple.nz title terrain", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
				}
			}
		}
	}

	private void ensureTargets(GpuDevice device, int width, int height) {
		if (color != null && width == targetW && height == targetH) {
			return;
		}
		for (AutoCloseable c : new AutoCloseable[] {colorView, color, depthView, depth}) {
			if (c != null) {
				try {
					c.close();
				} catch (Exception ignored) {
				}
			}
		}
		color = Gpu.createTexture(() -> "Temple.nz title colour", GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
				GpuFormat.RGBA8_UNORM, width, height, 1, 1);
		colorView = device.createTextureView(color);
		depth = Gpu.createTexture(() -> "Temple.nz title depth", GpuTexture.USAGE_RENDER_ATTACHMENT, GpuFormat.D32_FLOAT, width, height, 1, 1);
		depthView = device.createTextureView(depth);
		targetW = width;
		targetH = height;
	}
}
