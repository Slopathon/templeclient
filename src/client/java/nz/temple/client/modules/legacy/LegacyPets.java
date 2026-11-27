package nz.temple.client.modules.legacy;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import nz.temple.client.hud.Notifications;
import nz.temple.client.modules.cosmetic.Look;
import nz.temple.client.modules.cosmetic.Previewable;
import nz.temple.client.render.Draw2D;
import nz.temple.client.render.Fonts;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Meshes;
import nz.temple.client.render.Projection;
import nz.temple.client.render.Theme;
import nz.temple.client.render.UiScale;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class LegacyPets extends Module
		implements Previewable, nz.temple.client.render.FxSource, nz.temple.client.cosmetic.PetsAccess {
	private final ModeSetting kind;
	private final ModeSetting colors;
	private final ColorSetting custom;
	private final NumberSetting size;
	private final ModeSetting spot;
	private final BoolSetting sparks;
	private final BoolSetting firstPerson;
	private final BoolSetting bubbles;
	private Vec3 pos;
	private Vec3 vel = Vec3.ZERO;
	private float yaw;
	private float pitch;
	private long lastNanos = System.nanoTime();
	private double time;
	private double nextBlink = 3;
	private double happy;
	private double twirl = 1;
	private long lastHit;
	private final List<double[]> trail = new ArrayList<>();
	private double wingPhase;
	private double wingRate = 6.5;
	private double glide;
	private double frameDt;
	private Vec3 bubbleAnchor;
	private long anchorSeen;
	private long bubbleVisibleAt;
	private record Say(String title, String detail, int accent, long life) {
	}
	private final Deque<Say> sayQueue = new ArrayDeque<>();
	private Say saying;
	private long sayStart;

	public LegacyPets() {
		super("Pets", Category.COSMETICS, "A pet that follows you and can show your notifications. Only visible to you.");
		kind = add(new ModeSetting("Pet", List.of("Dragon", "Jellyfish", "Rune Orb", "Bee", "Ghost"), "Dragon")
				.describe("Which pet floats near you."));
		colors = add(new ModeSetting("Color", List.of("Natural", "Theme", "Custom"), "Natural")
				.describe("Glow colour. Natural gives each pet its own colour, Theme uses the HUD accent, Custom uses one colour."));
		custom = add(new ColorSetting("Custom color", 0xFF7FD8FF).describe("Glow colour.").visibleWhen(() -> colors.is("Custom")));
		size = add(new NumberSetting("Size", 1, 0.5, 2.5, 0.05, "x").describe("Pet size."));
		spot = add(new ModeSetting("Position", List.of("Shoulder", "Orbit", "Behind"), "Shoulder")
				.describe("Where the pet stays: above your right shoulder, circling you, or behind you."));
		sparks = add(new BoolSetting("Sparkle trail", true).describe("Leaves a trail of sparks as it moves."));
		firstPerson = add(new BoolSetting("In first person", true)
				.describe("Also show the pet in first person."));
		bubbles = add(new BoolSetting("Speech bubbles", true)
				.describe("Shows client notifications in a speech bubble above your pet instead of the corner banner, while the pet is on "
						+ "screen. Chat messages and sounds are unchanged."));
	}
	@Override
	public void preview(Vec3 at) {
		happy = 1.5;
	}
	@Override
	protected void onEnable() {
		pos = null;
		trail.clear();
	}
	@Override
	protected void onDisable() {
		sayQueue.clear();
		saying = null;
		bubbleAnchor = null;
	}
	private int glow() {
		return switch (colors.get()) {
			case "Custom" -> custom.get();
			case "Natural" -> switch (kind.get()) {
				case "Jellyfish" -> 0xFF7FE3FF;
				case "Rune Orb" -> 0xFFB48CFF;
				case "Bee" -> 0xFFFFC83D;
				case "Ghost" -> 0xFFBFD8FF;
				default -> 0xFFFF7A3D;
			};
			default -> Theme.primary();
		};
	}
	public void emit(Fx fx, float partial) {
		Minecraft mc = Minecraft.getInstance();
		long now = System.nanoTime();
		double dt = Math.min((now - lastNanos) / 1e9, 0.1);
		lastNanos = now;
		if (!isEnabled() || mc.player == null || mc.level == null) {
			pos = null;
			bubbleAnchor = null;
			return;
		}
		time += dt;
		frameDt = dt;
		happy = Math.max(0, happy - dt);
		long hit = nz.temple.client.util.CombatTracker.lastAttackMillis();
		if (hit != lastHit) {
			if (lastHit != 0 && System.currentTimeMillis() - hit < 500) {
				happy = Math.max(happy, 1.1);
				twirl = 0;
			}
			lastHit = hit;
		}
		twirl = Math.min(1, twirl + dt / 0.65);
		double s = size.get();
		boolean firstPersonView = mc.options.getCameraType().isFirstPerson();
		Vec3 eye = mc.player.getPosition(partial).add(0, mc.player.getEyeHeight(), 0);
		float bodyYaw = heading(mc, partial, dt);
		Vec3 forward = new Vec3(-Math.sin(bodyYaw), 0, Math.cos(bodyYaw));
		Vec3 right = new Vec3(-forward.z, 0, forward.x);
		Vec3 target = switch (spot.get()) {
			case "Orbit" -> {
				double clock = nz.temple.client.render.Theme.time();
				double a = clock * 0.9;
				yield eye.add(Math.cos(a) * 1.3 * s, -0.15 + Math.sin(clock * 1.7) * 0.15, Math.sin(a) * 1.3 * s);
			}
			case "Behind" -> eye.add(forward.scale(-1.4 * s)).add(right.scale(0.35)).add(0, -0.95, 0);
			default -> eye.add(right.scale(0.72 * s)).add(forward.scale(-0.35)).add(0, 0.28 * s, 0);
		};
		if (pos == null || pos.distanceToSqr(target) > 400) {
			pos = target;
			vel = Vec3.ZERO;
		}
		Vec3 pull = target.subtract(pos).scale(26);
		vel = vel.add(pull.scale(dt)).scale(Math.pow(0.004, dt));
		pos = pos.add(vel.scale(dt));
		double speed = vel.length();
		float wantYaw = speed > 0.6 ? (float) Math.atan2(-vel.x, vel.z) : (float) Math.atan2(-forward.x, forward.z);
		yaw += wrap(wantYaw - yaw) * (float) Math.min(1, dt * 6);
		pitch += ((float) Math.max(-0.6, Math.min(0.6, -vel.y * 0.35)) - pitch) * (float) Math.min(1, dt * 5);
		double bob = Math.sin(time * 2.1) * 0.05 * s;
		double spinAngle = twirl < 1 ? (1 - Math.pow(1 - twirl, 3)) * Math.PI * 2 : 0;
		double hopUp = twirl < 1 ? Math.sin(twirl * Math.PI) * 0.12 * s : 0;
		Vec3 at = pos.add(0, bob + hopUp, 0);
		bubbleAnchor = at.add(0, 0.32 * s, 0);
		anchorSeen = System.currentTimeMillis();
		if (firstPersonView && !firstPerson.get()) {
			return;
		}
		Quaternionf facing = new Quaternionf().rotationY((float) (-yaw + spinAngle)).rotateX(pitch);
		int glow = glow();
		shadow(fx, mc, at, s);
		if (sparks.get()) {
			trail(fx, at, glow, speed, s);
		}
		fx.throughWalls(false);
		switch (kind.get()) {
			case "Jellyfish" -> jellyfish(fx, at, facing, glow, s);
			case "Rune Orb" -> orb(fx, at, facing, glow, s);
			case "Bee" -> bee(fx, at, facing, glow, s, speed);
			case "Ghost" -> ghost(fx, at, facing, glow, s, speed);
			default -> dragon(fx, at, facing, glow, s, speed);
		}
	}
	private float anchorYaw = Float.NaN;

	private float heading(Minecraft mc, float partial, double dt) {
		float view = (float) Math.toRadians(mc.player.getViewYRot(partial));
		if (Float.isNaN(anchorYaw)) {
			anchorYaw = view;
		}
		Vec3 move = mc.player.getDeltaMovement();
		double moving = move.horizontalDistance() * 20;
		if (moving > 1.0) {
			float moveYaw = (float) Math.atan2(-move.x, move.z);
			anchorYaw += wrap(moveYaw - anchorYaw) * (float) Math.min(1, dt * 3);
		} else if (pos != null && (mc.options.keyUse.isDown() || mc.options.keyAttack.isDown())) {
			Vec3 eye = mc.player.getEyePosition(partial);
			Vec3 toPet = pos.subtract(eye);
			double dist = toPet.length();
			Vec3 look = mc.player.getViewVector(partial);
			if (dist < 6 && dist > 1e-3 && toPet.scale(1 / dist).dot(look) > Math.cos(Math.toRadians(28))) {
				anchorYaw += wrap(view - anchorYaw) * (float) Math.min(1, dt * 6);
			}
		}
		anchorYaw = wrap(anchorYaw);
		return anchorYaw;
	}
	private void shadow(Fx fx, Minecraft mc, Vec3 at, double s) {
		double reach = 4.5;
		var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(at, at.add(0, -reach, 0),
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY, mc.player));
		if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
			return;
		}
		double height = at.y - hit.getLocation().y;
		double fade = 1 - height / reach;
		if (fade <= 0) {
			return;
		}
		double r = (0.16 + height * 0.05) * s;
		fx.throughWalls(false);
		fx.additive(false);
		double y = hit.getLocation().y + 0.012;
		fx.ring(at.x, y, at.z, 0, r * 0.55, Fx.withAlpha(0xFF000000, (float) (110 * fade * fade)),
				Fx.withAlpha(0xFF000000, (float) (65 * fade * fade)), 24);
		fx.ring(at.x, y, at.z, r * 0.55, r, Fx.withAlpha(0xFF000000, (float) (65 * fade * fade)), 0x00000000, 24);
	}
	private static float wrap(float a) {
		while (a > Math.PI) {
			a -= (float) (Math.PI * 2);
		}
		while (a < -Math.PI) {
			a += (float) (Math.PI * 2);
		}
		return a;
	}
	private static double[] at(Vec3 c, Quaternionf q, double x, double y, double z) {
		Vector3f v = q.transform(new Vector3f((float) x, (float) y, (float) z));
		return new double[] {c.x + v.x, c.y + v.y, c.z + v.z};
	}
	private boolean blinking() {
		if (time > nextBlink) {
			if (time > nextBlink + 0.14) {
				nextBlink = time + 2.5 + ThreadLocalRandom.current().nextDouble(3.5);
			}
			return true;
		}
		return false;
	}
	private void eyes(Meshes mesh, Vec3 c, Quaternionf q, double x, double y, double z, double r, int iris) {
		boolean shut = blinking();
		for (int side = -1; side <= 1; side += 2) {
			double[] e = at(c, q, side * x, y, z);
			mesh.material(Meshes.Material.METAL);
			mesh.emission(0.15f);
			mesh.ellipsoid(e[0], e[1], e[2], r, shut ? r * 0.12 : r * (happy > 0 ? 0.55 : 1), r * 0.7, q, 0xFF14121A, 8);
			if (!shut) {
				double[] shine = at(c, q, side * x + r * 0.35, y + r * 0.4, z + r * 0.55);
				mesh.material(Meshes.Material.MATTE);
				mesh.emission(1f);
				mesh.sphere(shine[0], shine[1], shine[2], r * 0.28, 0xFFFFFFFF, 6);
				double[] glint = at(c, q, side * x - r * 0.25, y - r * 0.3, z + r * 0.6);
				mesh.sphere(glint[0], glint[1], glint[2], r * 0.14, iris, 5);
			}
		}
	}
	private void jellyfish(Fx fx, Vec3 c, Quaternionf q, int glow, double s) {
		Meshes mesh = fx.mesh();
		double pulse = Math.pow(Math.max(0, Math.sin(time * 2.4)), 3);
		double w = (0.2 + pulse * 0.03) * s;
		double h = (0.15 - pulse * 0.03) * s;
		Quaternionf up = new Quaternionf().rotationY((float) (time * 0.3));

		mesh.material(Meshes.Material.GLASS);
		mesh.emission(0.25f);
		mesh.ellipsoid(c.x, c.y, c.z, w, h, w, up, Fx.withAlpha(Fx.lerp(glow, 0xFFFFFFFF, 0.5), 150), 16);
		mesh.material(Meshes.Material.MATTE);
		mesh.emission(1f);
		mesh.ellipsoid(c.x, c.y - 0.02 * s, c.z, w * 0.45, h * 0.5, w * 0.45, up, glow, 10);
		mesh.torus(c.x, c.y - h * 0.55, c.z, up, w * 0.9, 0.018 * s, 0, 32,
				(u, v) -> Fx.lerp(glow, 0xFFFFFFFF, 0.5 + 0.5 * Math.sin(u * Math.PI * 12 + time * 4)));
		eyes(mesh, c, q, 0.07 * s, 0.01 * s, w * 0.92, 0.02 * s, glow);
		int tentacles = 8;
		int n = 7;
		for (int k = 0; k < tentacles; k++) {
			double a = Math.PI * 2 * k / tentacles + time * 0.3;
			double[] path = new double[n * 3];
			double[] radius = new double[n];
			int[] colours = new int[n];
			for (int i = 0; i < n; i++) {
				double u = (double) i / (n - 1);
				double sway = Math.sin(time * 2.2 - u * 4 + k) * 0.06 * u * s;
				double rr = w * 0.75 * (1 - u * 0.35);
				path[i * 3] = c.x + Math.cos(a) * rr + sway;
				path[i * 3 + 1] = c.y - h * 0.5 - u * (0.38 + 0.08 * Math.sin(k)) * s;
				path[i * 3 + 2] = c.z + Math.sin(a) * rr + Math.cos(time * 1.9 - u * 3.5 + k) * 0.05 * u * s;
				radius[i] = 0.012 * s * (1 - u * 0.7);
				colours[i] = Fx.withAlpha(Fx.lerp(glow, 0xFFFFFFFF, 0.3 * (1 - u)), (int) (230 * (1 - u * 0.6)));
			}
			mesh.emission(0.8f);
			mesh.tube(path, n, radius, colours, 5);
		}
		fx.bloom(true);
		fx.glow(c.x, c.y, c.z, (0.45 + pulse * 0.15) * s, Fx.withAlpha(glow, 110));
		fx.bloom(false);
	}
	private void dragon(Fx fx, Vec3 c, Quaternionf q, int glow, double s, double speed) {
		Meshes mesh = fx.mesh();
		boolean natural = colors.is("Natural");
		int scale = natural ? 0xFF2E8C8F : Fx.lerp(0xFF2A2440, glow, 0.45);
		int scaleDark = Fx.lerp(scale, 0xFF081018, 0.5);
		int belly = 0xFFF2CE7A;
		int horn = 0xFFF3E6CC;
		double wantGlide = Math.max(0, Math.min(1, (speed - 2.5) / 3));
		glide += (wantGlide - glide) * Math.min(1, frameDt * 3);
		double wantRate = 6.5 + Math.min(speed, 2.5) * 0.6 - glide * 3;
		wingRate += (wantRate - wingRate) * Math.min(1, frameDt * 2);
		wingPhase = (wingPhase + frameDt * wingRate) % (Math.PI * 2);
		double beat = Math.sin(wingPhase) * (1 - glide * 0.75) + glide * 0.15;
		Vec3 body = c.add(0, -beat * 0.015 * s, 0);
		mesh.material(Meshes.Material.METAL);
		mesh.emission(0.04f);
		mesh.ellipsoid(body.x, body.y, body.z, 0.085 * s, 0.085 * s, 0.13 * s, q, scale, 16);
		double[] hips = at(body, q, 0, -0.01 * s, -0.1 * s);
		mesh.ellipsoid(hips[0], hips[1], hips[2], 0.07 * s, 0.065 * s, 0.08 * s, q, scale, 12);
		mesh.material(Meshes.Material.MATTE);
		for (int i = 0; i < 5; i++) {
			double[] plate = at(body, q, 0, (-0.06 + i * 0.004) * s, (0.08 - i * 0.042) * s);
			mesh.ellipsoid(plate[0], plate[1], plate[2], 0.05 * s, 0.022 * s, 0.028 * s, q, Fx.lerp(belly, 0xFFFFFFFF, 0.1 * (i % 2)), 8);
		}
		double nod = Math.sin(time * 1.3) * 0.1;
		double[] n0 = at(body, q, 0, 0.04 * s, 0.1 * s);
		double[] n1 = at(body, q, 0, 0.1 * s, 0.14 * s);
		double[] n2 = at(body, q, 0, 0.15 * s, 0.17 * s);
		mesh.material(Meshes.Material.METAL);
		mesh.tube(new double[] {n0[0], n0[1], n0[2], n1[0], n1[1], n1[2], n2[0], n2[1], n2[2]}, 3,
				new double[] {0.055 * s, 0.042 * s, 0.038 * s}, new int[] {scale, scale, scale}, 10);
		Vec3 head = new Vec3(n2[0], n2[1] + 0.02 * s, n2[2]);
		Quaternionf hq = new Quaternionf(q).rotateX((float) nod).rotateZ((float) (Math.sin(time * 0.8) * 0.1));
		double[] headAt = at(head, hq, 0, 0, 0.03 * s);
		head = new Vec3(headAt[0], headAt[1], headAt[2]);
		mesh.ellipsoid(head.x, head.y, head.z, 0.07 * s, 0.062 * s, 0.075 * s, hq, scale, 16);
		double[] snout = at(head, hq, 0, -0.015 * s, 0.08 * s);
		mesh.ellipsoid(snout[0], snout[1], snout[2], 0.045 * s, 0.035 * s, 0.06 * s, hq, scale, 12);
		mesh.material(Meshes.Material.MATTE);
		double[] jaw = at(head, hq, 0, -0.04 * s, 0.07 * s);
		mesh.ellipsoid(jaw[0], jaw[1], jaw[2], 0.036 * s, 0.016 * s, 0.05 * s, hq, Fx.lerp(scale, belly, 0.25), 10);
		for (int side = -1; side <= 1; side += 2) {
			double[] nostril = at(head, hq, side * 0.018 * s, -0.003 * s, 0.135 * s);
			mesh.material(Meshes.Material.METAL);
			mesh.sphere(nostril[0], nostril[1], nostril[2], 0.006 * s, 0xFF0A0A10, 6);
			int hn = 5;
			double[] hp = new double[hn * 3];
			double[] hr = new double[hn];
			int[] hc = new int[hn];
			for (int i = 0; i < hn; i++) {
				double u = (double) i / (hn - 1);
				double[] p = at(head, hq, side * (0.035 + u * 0.03) * s, (0.045 + u * 0.05 - u * u * 0.03) * s, (-0.01 - u * 0.1) * s);
				hp[i * 3] = p[0];
				hp[i * 3 + 1] = p[1];
				hp[i * 3 + 2] = p[2];
				hr[i] = (0.017 * (1 - u) + 0.002) * s;
				hc[i] = Fx.lerp(horn, 0xFF5A4A3A, u * u);
			}
			mesh.material(Meshes.Material.METAL);
			mesh.emission(0.05f);
			mesh.tube(hp, hn, hr, hc, 7);
			double[] frill = at(head, hq, side * 0.065 * s, -0.005 * s, -0.02 * s);
			Quaternionf fq = new Quaternionf(hq).rotateZ((float) (-side * 1.25)).rotateX(-0.5f);
			mesh.material(Meshes.Material.GLASS);
			mesh.emission(0.35f);
			mesh.crystal(frill[0], frill[1], frill[2], fq, 0.06 * s, 0.028 * s, Fx.withAlpha(Fx.lerp(scale, glow, 0.5), 210), 3);
			double[] eye = at(head, hq, side * 0.042 * s, 0.015 * s, 0.045 * s);
			Quaternionf eq = new Quaternionf(hq).rotateY((float) (side * 0.5));
			mesh.material(Meshes.Material.MATTE);
			boolean shut = blinking();
			mesh.emission(shut ? 0.02f : 0.9f);
			mesh.ellipsoid(eye[0], eye[1], eye[2], 0.018 * s, shut ? 0.003 * s : 0.014 * s, 0.009 * s, eq, shut ? scaleDark : 0xFFFFC43A, 8);
			if (!shut) {
				double[] pupil = at(new Vec3(eye[0], eye[1], eye[2]), eq, 0, 0, 0.006 * s);
				mesh.emission(0f);
				mesh.ellipsoid(pupil[0], pupil[1], pupil[2], 0.004 * s, 0.012 * s, 0.004 * s, eq, 0xFF140A04, 6);
			}
		}
		mesh.material(Meshes.Material.METAL);
		for (int i = 0; i < 6; i++) {
			double u = i / 5.0;
			double[] spike = at(body, q, 0, (0.08 - u * 0.03) * s, (0.08 - u * 0.2) * s);
			Quaternionf sq = new Quaternionf(q).rotateX((float) (-0.55 - u * 0.3));
			mesh.emission(0.3f);
			mesh.crystal(spike[0], spike[1], spike[2], sq, (0.045 - u * 0.015) * s, 0.02 * s, Fx.lerp(scaleDark, glow, 0.3), 4);
		}
		mesh.emission(0.04f);
		for (int side = -1; side <= 1; side += 2) {
			double[] arm = at(body, q, side * 0.06 * s, -0.06 * s, 0.07 * s);
			mesh.ellipsoid(arm[0], arm[1], arm[2], 0.02 * s, 0.032 * s, 0.022 * s, q, scaleDark, 8);
			double[] leg = at(body, q, side * 0.055 * s, -0.07 * s, -0.08 * s);
			mesh.ellipsoid(leg[0], leg[1], leg[2], 0.028 * s, 0.036 * s, 0.035 * s, q, scaleDark, 8);
			for (int k = -1; k <= 1; k++) {
				double[] claw = at(new Vec3(leg[0], leg[1], leg[2]), q, k * 0.011 * s, -0.032 * s, 0.022 * s);
				mesh.crystal(claw[0], claw[1], claw[2], new Quaternionf(q).rotateX(1.9f), 0.018 * s, 0.006 * s, horn, 3);
			}
		}
		dragonWings(mesh, body, q, s, beat, scale, scaleDark, glow);
		int n = 9;
		double[] path = new double[n * 3];
		double[] radius = new double[n];
		int[] colours = new int[n];
		for (int i = 0; i < n; i++) {
			double u = (double) i / (n - 1);
			double[] p = at(new Vec3(hips[0], hips[1], hips[2]), q, Math.sin(time * 2.4 - u * 3) * 0.08 * u * s, (-u * 0.07 + u * u * 0.1) * s, (-0.05 - u * 0.32) * s);
			path[i * 3] = p[0];
			path[i * 3 + 1] = p[1];
			path[i * 3 + 2] = p[2];
			radius[i] = 0.045 * s * (1 - u * 0.82);
			colours[i] = scale;
		}
		mesh.material(Meshes.Material.METAL);
		mesh.emission(0.04f);
		mesh.tube(path, n, radius, colours, 10);
		Quaternionf tip = Meshes.pointing(path[(n - 1) * 3] - path[(n - 2) * 3], path[(n - 1) * 3 + 1] - path[(n - 2) * 3 + 1],
				path[(n - 1) * 3 + 2] - path[(n - 2) * 3 + 2]);
		mesh.emission(0.8f);
		mesh.crystal(path[(n - 1) * 3], path[(n - 1) * 3 + 1], path[(n - 1) * 3 + 2], tip, 0.07 * s, 0.04 * s, glow, 4);
		fx.bloom(true);
		fx.glow(path[(n - 1) * 3], path[(n - 1) * 3 + 1], path[(n - 1) * 3 + 2], 0.09 * s, Fx.withAlpha(glow, 190));
		double puff = (time % 4.0) / 4.0;
		if (puff < 0.2) {
			double[] mouth = at(head, hq, 0, -0.02 * s, (0.15 + puff * 0.6) * s);
			fx.glow(mouth[0], mouth[1] + puff * 0.1, mouth[2], (0.05 + puff * 0.2) * s, Fx.withAlpha(0xFFFF8A3D, (int) (230 * (1 - puff * 5))));
		}
		fx.bloom(false);
	}
	private void dragonWings(Meshes mesh, Vec3 body, Quaternionf q, double s, double beat, int scale, int scaleDark, int glow) {
		int membrane = Fx.withAlpha(Fx.lerp(Fx.lerp(scale, 0xFF081018, 0.25), glow, 0.18), 232);
		int membraneEdge = Fx.withAlpha(Fx.lerp(scale, glow, 0.45), 240);
		for (int side = -1; side <= 1; side += 2) {
			double lift = 0.35 + beat * 0.7;
			double[] shoulder = at(body, q, side * 0.06 * s, 0.06 * s, 0.05 * s);
			Vec3 sh = new Vec3(shoulder[0], shoulder[1], shoulder[2]);
			double[] elbow = at(sh, q, side * 0.13 * s * Math.cos(lift), 0.13 * s * Math.sin(lift), -0.03 * s);
			double[] wrist = at(sh, q, side * 0.24 * s * Math.cos(lift * 0.8), 0.24 * s * Math.sin(lift * 0.8) + 0.02 * s, -0.02 * s);
			Vec3 wr = new Vec3(wrist[0], wrist[1], wrist[2]);
			mesh.material(Meshes.Material.METAL);
			mesh.emission(0.05f);
			mesh.tube(new double[] {shoulder[0], shoulder[1], shoulder[2], elbow[0], elbow[1], elbow[2], wrist[0], wrist[1], wrist[2]}, 3,
					new double[] {0.016 * s, 0.012 * s, 0.01 * s}, new int[] {scaleDark, scaleDark, scaleDark}, 7);
			double[][] tips = new double[3][];
			double[] angles = {0.35, -0.15, -0.75};
			double[] lengths = {0.2, 0.24, 0.2};
			for (int k = 0; k < 3; k++) {
				double a = angles[k] + beat * 0.25;
				tips[k] = at(wr, q, side * lengths[k] * s * Math.cos(a) * Math.cos(lift * 0.5), lengths[k] * s * Math.sin(lift * 0.5) * 0.6
						- 0.08 * s * k, lengths[k] * s * Math.sin(a) * 0.9);
				mesh.material(Meshes.Material.METAL);
				mesh.tube(new double[] {wrist[0], wrist[1], wrist[2], tips[k][0], tips[k][1], tips[k][2]}, 2,
						new double[] {0.008 * s, 0.003 * s}, new int[] {scaleDark, scaleDark}, 5);
			}
			double[] flank = at(body, q, side * 0.07 * s, 0.0, -0.08 * s);
			mesh.material(Meshes.Material.MATTE);
			mesh.emission(0.12f);
			panel(mesh, shoulder, wrist, tips[0], membrane, membraneEdge);
			panel(mesh, wrist, tips[0], tips[1], membrane, membraneEdge);
			panel(mesh, wrist, tips[1], tips[2], membrane, membraneEdge);
			panel(mesh, wrist, tips[2], flank, membrane, membraneEdge);
			panel(mesh, shoulder, wrist, flank, membrane, membrane);
		}
	}
	private static void panel(Meshes mesh, double[] a, double[] b, double[] c, int inner, int edge) {
		double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
		double vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
		double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
		double[] mid = {(b[0] + c[0]) / 2 * 0.82 + a[0] * 0.18, (b[1] + c[1]) / 2 * 0.82 + a[1] * 0.18, (b[2] + c[2]) / 2 * 0.82 + a[2] * 0.18};
		double[] pa = {a[0], a[1], a[2], nx, ny, nz};
		double[] pb = {b[0], b[1], b[2], nx, ny, nz};
		double[] pm = {mid[0], mid[1], mid[2], nx, ny, nz};
		double[] pc = {c[0], c[1], c[2], nx, ny, nz};
		mesh.quad(pa, pb, pm, pc, inner, edge, edge, edge);
	}

	public boolean say(String title, String detail, Notifications.Kind kind) {
		if (!isEnabled() || !bubbles.get() || bubbleAnchor == null || System.currentTimeMillis() - bubbleVisibleAt > 600) {
			return false;
		}
		int accent = switch (kind) {
			case ENABLED -> 0xFF34C759;
			case DISABLED -> 0xFF8E8E93;
			case ERROR -> 0xFFFF453A;
			case INFO -> Theme.color(0);
		};
		long life = switch (kind) {
			case ERROR -> 5000;
			case INFO -> 4000;
			default -> 1800;
		};
		sayQueue.removeIf(m -> m.title().equals(title));
		if (saying != null && saying.title().equals(title)) {
			saying = null;
		}
		sayQueue.add(new Say(title, detail, accent, life));
		while (sayQueue.size() > 4) {
			sayQueue.poll();
		}
		happy = Math.max(happy, 0.6);
		return true;
	}
	public void renderBubble(GuiGraphicsExtractor g, DeltaTracker delta, Projection projection) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || bubbleAnchor == null || !projection.ready() || mc.player == null
				|| System.currentTimeMillis() - anchorSeen > 500) {
			return;
		}
		int gw = g.guiWidth();
		int gh = g.guiHeight();
		float[] at = projection.toScreen(bubbleAnchor.x, bubbleAnchor.y, bubbleAnchor.z, gw, gh);
		if (at == null || at[0] < 0 || at[0] > gw || at[1] < 0 || at[1] > gh) {
			return;
		}
		bubbleVisibleAt = System.currentTimeMillis();
		long now = System.currentTimeMillis();
		if (saying != null && now - sayStart > saying.life() + 220) {
			saying = null;
		}
		if (saying == null && !sayQueue.isEmpty()) {
			saying = sayQueue.poll();
			sayStart = now;
		}
		if (saying == null) {
			return;
		}
		float age = (now - sayStart) / 1000f;
		float leave = Math.max(0, (now - sayStart - saying.life()) / 220f);
		float pop = age < 0.22f ? easeOutBack(age / 0.22f) : 1;
		float alpha = Math.max(0, Math.min(1, age / 0.12f)) * (1 - Math.min(1, leave));
		if (alpha <= 0.01f) {
			return;
		}
		var font = mc.font;
		float u = UiScale.factor();
		float scale = 0.5f * u;
		Component title = Fonts.text(saying.title(), Fonts.SEMIBOLD);
		Component detail = Fonts.text(saying.detail(), Fonts.MEDIUM);
		float titleW = font.width(title) * scale;
		float detailW = font.width(detail) * scale;
		float line = font.lineHeight * scale;
		float padX = 7 * u;
		float padY = 5 * u;
		float dot = 4 * u;
		float w = padX * 2 + dot + 5 * u + Math.max(titleW, detailW);
		float h = padY * 2 + line * 2 + 1.5f * u;
		float tail = 6 * u;
		float cx = at[0];
		float bottom = at[1] - 2 * u;
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(cx, bottom);
		pose.scale(pop, pop);
		pose.translate(-cx, -bottom);
		float x = Math.max(4, Math.min(gw - 4 - w, cx - w / 2));
		float y = bottom - tail - h;
		int a8 = Math.round(alpha * 255);
		int shadow = Math.round(alpha * 0x38) << 24;
		int fill = (Math.round(alpha * 0xF4) << 24) | 0xFFFFFF;
		float p = 1f / Math.max(1, (float) mc.getWindow().getGuiScale());
		Draw2D.pixelRound(g, x - p, y + p, x + w + p, y + h + 2 * p, 9, shadow);
		Draw2D.pixelRound(g, x, y, x + w, y + h, 8, fill);
		float tx = Math.max(x + 10 * u, Math.min(x + w - 10 * u, cx));
		int rows = Math.max(1, Math.round(tail / p));
		for (int i = 0; i < rows; i++) {
			float k = 1 - (float) i / rows;
			float half = 4.5f * u * k;
			float lean = (cx - tx) * (1 - k);
			Draw2D.pixelRect(g, tx - half + lean, y + h + i * p - p, tx + half + lean, y + h + (i + 1) * p, fill);
		}
		Draw2D.pixelRound(g, x + padX, y + padY + line / 2 - dot / 2, x + padX + dot, y + padY + line / 2 + dot / 2, 3,
				(a8 << 24) | (saying.accent() & 0xFFFFFF));
		float textX = x + padX + dot + 5 * u;
		pose.pushMatrix();
		pose.translate(textX, y + padY);
		pose.scale(scale, scale);
		g.text(font, title, 0, 0, (a8 << 24) | 0x1C1C1E, false);
		pose.popMatrix();
		pose.pushMatrix();
		pose.translate(textX, y + padY + line + 1.5f * u);
		pose.scale(scale, scale);
		g.text(font, detail, 0, 0, (a8 << 24) | 0x6C6C72, false);
		pose.popMatrix();
		pose.popMatrix();
	}
	private static float easeOutBack(float t) {
		float c1 = 1.70158f;
		float c3 = c1 + 1;
		return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
	}
	private void bee(Fx fx, Vec3 at, Quaternionf q, int glow, double s, double speed) {
		Meshes mesh = fx.mesh();
		Vec3 c = at.add(Math.sin(time * 13) * 0.012 * s, Math.sin(time * 17 + 1) * 0.01 * s, Math.cos(time * 11) * 0.008 * s);
		boolean natural = colors.is("Natural");
		int yellow = natural ? 0xFFF6C232 : Fx.lerp(0xFFF6C232, glow, 0.65);
		int black = 0xFF1E1912;
		int fuzz = Fx.lerp(yellow, 0xFFFFE9A8, 0.35);
		mesh.bloom(false, 0);
		mesh.material(Meshes.Material.MATTE);
		mesh.emission(0.12f);
		double ax = 0.1 * s, ay = 0.092 * s, az = 0.125 * s;
		double[] ac = at(c, q, 0, -0.012 * s, -0.1 * s);
		Vector3f tmp = new Vector3f();
		mesh.surface((u, v, o) -> {
			double theta = u * Math.PI * 2;
			double phi = v * Math.PI;
			double lx = Math.sin(phi) * Math.cos(theta), ly = Math.sin(phi) * Math.sin(theta), lz = -Math.cos(phi);
			q.transform(tmp.set((float) (lx * ax), (float) (ly * ay), (float) (lz * az)));
			o[0] = ac[0] + tmp.x;
			o[1] = ac[1] + tmp.y;
			o[2] = ac[2] + tmp.z;
			q.transform(tmp.set((float) (lx / ax), (float) (ly / ay), (float) (lz / az)));
			o[3] = tmp.x;
			o[4] = tmp.y;
			o[5] = tmp.z;
		}, 24, 18, 0, (u, v) -> v < 0.16 ? black : ((int) ((v - 0.16) * 6.2) % 2 == 0 ? yellow : black));
		double[] st = at(c, q, 0, -0.02 * s, -0.235 * s);
		Quaternionf back = new Quaternionf(q).rotateX((float) (Math.PI / 2));
		mesh.material(Meshes.Material.METAL);
		mesh.emission(0.1f);
		mesh.ellipsoid(st[0], st[1], st[2], 0.009 * s, 0.026 * s, 0.009 * s, back, black, 10);
		mesh.material(Meshes.Material.MATTE);
		mesh.emission(0.15f);
		double[] th = at(c, q, 0, 0.012 * s, 0.03 * s);
		mesh.sphere(th[0], th[1], th[2], 0.075 * s, fuzz, 14);
		double[] hd = at(c, q, 0, 0.02 * s, 0.115 * s);
		mesh.sphere(hd[0], hd[1], hd[2], 0.062 * s, black, 14);
		eyes(mesh, c, q, 0.03 * s, 0.032 * s, 0.165 * s, 0.019 * s, glow);
		for (int side = -1; side <= 1; side += 2) {
			double[] a0 = at(c, q, side * 0.022 * s, 0.07 * s, 0.135 * s);
			double[] a1 = at(c, q, side * 0.04 * s, 0.13 * s, 0.16 * s);
			double[] a2 = at(c, q, side * 0.05 * s, 0.15 * s, 0.205 * s);
			mesh.tube(new double[] {a0[0], a0[1], a0[2], a1[0], a1[1], a1[2], a2[0], a2[1], a2[2]}, 3,
					new double[] {0.006 * s, 0.005 * s, 0.005 * s}, new int[] {black, black, black}, 12);
			mesh.sphere(a2[0], a2[1], a2[2], 0.012 * s, black, 10);
		}
		for (int side = -1; side <= 1; side += 2) {
			for (int k = 0; k < 3; k++) {
				double z = (0.06 - k * 0.045) * s;
				double swing = Math.sin(time * 6 + k * 1.3 + side) * 0.012 * s;
				double[] l0 = at(c, q, side * 0.04 * s, -0.03 * s, z);
				double[] l1 = at(c, q, side * 0.07 * s, -0.075 * s, z + swing);
				double[] l2 = at(c, q, side * 0.06 * s, -0.11 * s, z + swing * 1.5);
				mesh.tube(new double[] {l0[0], l0[1], l0[2], l1[0], l1[1], l1[2], l2[0], l2[1], l2[2]}, 3,
						new double[] {0.006 * s, 0.005 * s, 0.004 * s}, new int[] {black, black, black}, 12);
			}
		}
		double beat = Math.sin(time * 48);
		double sweep = 0.35 + Math.min(0.4, speed * 0.08);
		mesh.material(Meshes.Material.GLASS);
		mesh.emission(0.3f);
		int wing = Fx.withAlpha(Fx.lerp(0xFFEAF7FF, glow, 0.15), 170);
		int wingTip = Fx.withAlpha(0xFFFFFFFF, 120);
		for (int side = -1; side <= 1; side += 2) {
			double[] root = at(c, q, side * 0.03 * s, 0.075 * s, 0.03 * s);
			for (int pair = 0; pair < 2; pair++) {
				float flap = (float) (0.55 + beat * 0.65 - pair * 0.15);
				Quaternionf w = new Quaternionf(q).rotateY((float) (side > 0 ? -(sweep + pair * 0.5) : Math.PI + sweep + pair * 0.5))
						.rotateZ(flap);
				double len = (pair == 0 ? 0.2 : 0.13) * s;
				mesh.blade(root[0], root[1], root[2], w, len, len * 0.55, 0.004 * s, -0.01 * s, wing, wingTip);
			}
		}
		fx.bloom(true);
		fx.glow(c.x, c.y, c.z, 0.35 * s, Fx.withAlpha(glow, 60));
		fx.bloom(false);
	}
	private void ghost(Fx fx, Vec3 c, Quaternionf q, int glow, double s, double speed) {
		Meshes mesh = fx.mesh();
		double h = 0.38 * s;
		double r = 0.135 * s;
		double trail = Math.min(0.12, speed * 0.03) * s;
		int body = Fx.withAlpha(colors.is("Natural") ? 0xFFF3F6FF : Fx.lerp(0xFFF3F6FF, glow, 0.35), 225);
		mesh.bloom(false, 0);
		mesh.material(Meshes.Material.MATTE);
		mesh.emission(0.45f);
		Vector3f tmp = new Vector3f();
		double e = 1e-3;
		mesh.surface((u, v, o) -> {
			double[] p = ghostPoint(u, v, h, r, trail);
			double[] pu = ghostPoint(u + e, v, h, r, trail);
			double[] pv = ghostPoint(u, Math.min(1, v + e), h, r, trail);
			double[] pv0 = ghostPoint(u, Math.max(0, v - e), h, r, trail);
			double ax = pu[0] - p[0], ay = pu[1] - p[1], az = pu[2] - p[2];
			double bx = pv[0] - pv0[0], by = pv[1] - pv0[1], bz = pv[2] - pv0[2];
			double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
			if (nx * p[0] + nz * p[2] + ny * Math.max(0, p[1]) < 0) {
				nx = -nx;
				ny = -ny;
				nz = -nz;
			}
			if (v < 0.02) {
				nx = 0;
				ny = 1;
				nz = 0;
			}
			q.transform(tmp.set((float) p[0], (float) p[1], (float) p[2]));
			o[0] = c.x + tmp.x;
			o[1] = c.y + tmp.y;
			o[2] = c.z + tmp.z;
			q.transform(tmp.set((float) nx, (float) ny, (float) nz));
			o[3] = tmp.x;
			o[4] = tmp.y;
			o[5] = tmp.z;
		}, 28, 18, body, null);
		for (int side = -1; side <= 1; side += 2) {
			double wave = Math.sin(time * 3.2 + side) * 0.25 + (happy > 0 ? Math.sin(time * 14) * 0.4 : 0);
			Quaternionf arm = new Quaternionf(q).rotateZ((float) (side * (0.9 + wave)));
			double[] a = at(c, q, side * r * 1.05, -0.02 * s, 0.01 * s);
			mesh.ellipsoid(a[0], a[1], a[2], 0.028 * s, 0.05 * s, 0.028 * s, arm, body, 12);
		}
		boolean shut = blinking();
		mesh.material(Meshes.Material.METAL);
		mesh.emission(0.1f);
		for (int side = -1; side <= 1; side += 2) {
			double[] eye = at(c, q, side * 0.045 * s, 0.07 * s, r * 0.96);
			mesh.ellipsoid(eye[0], eye[1], eye[2], 0.018 * s, shut ? 0.003 * s : 0.03 * s, 0.01 * s, q, 0xFF1A1820, 10);
		}
		double open = happy > 0 ? 0.022 : 0.01;
		double[] mouth = at(c, q, 0, 0.015 * s, r * 0.98);
		mesh.ellipsoid(mouth[0], mouth[1], mouth[2], 0.014 * s, open * s, 0.008 * s, q, 0xFF1A1820, 10);
		fx.bloom(true);
		fx.glow(c.x, c.y, c.z, 0.5 * s, Fx.withAlpha(glow, 70));
		fx.bloom(false);
	}
	private double[] ghostPoint(double u, double v, double h, double r, double trail) {
		double theta = u * Math.PI * 2;
		double dome = 0.42;
		double radius = v < dome ? r * Math.sqrt(Math.max(0, 1 - Math.pow(1 - v / dome, 2))) : r * (1 + (v - dome) * 0.45);
		double y = h * 0.45 - v * h;
		double low = Math.max(0, (v - 0.55) / 0.45);
		y += Math.sin(theta * 6 + time * 5) * 0.022 * h / 0.38 * low * low * 2.5;
		double sway = Math.sin(time * 2 + v * 2.5) * 0.02 * low;
		return new double[] {Math.cos(theta) * radius + sway, y, Math.sin(theta) * radius - trail * low * low};
	}
	private void orb(Fx fx, Vec3 c, Quaternionf q, int glow, double s) {
		Meshes mesh = fx.mesh();
		double beat = Math.pow(Math.max(0, Math.sin(time * 2.6)), 6);
		int gold = 0xFFE7B54A;
		Quaternionf spin = new Quaternionf().rotationY((float) (time * 0.9));
		mesh.material(Meshes.Material.NEON);
		mesh.emission(0.7f + (float) beat * 0.3f);
		mesh.bloom(true, 0.9f);
		Look.markGlow();
		int heart = Fx.lerp(glow, 0xFFFFFFFF, 0.3 + 0.3 * beat);
		double grow = 1 + beat * 0.12;
		mesh.crystal(c.x, c.y, c.z, spin, 0.13 * s * grow, 0.042 * s * grow, heart, 6);
		for (int k = 0; k < 2; k++) {
			Quaternionf side = new Quaternionf(spin).rotateY(k * (float) Math.PI).rotateZ(0.55f);
			Vector3f off = side.transform(new Vector3f(0, 0.035f * (float) s, 0));
			mesh.crystal(c.x + off.x, c.y + off.y - 0.012 * s, c.z + off.z, side, 0.08 * s * grow, 0.028 * s, Fx.lerp(heart, glow, 0.4), 5);
		}
		mesh.bloom(false, 0);

		mesh.material(Meshes.Material.GLASS);
		mesh.emission(0.12f);
		mesh.sphere(c.x, c.y, c.z, 0.125 * s, Fx.withAlpha(Fx.lerp(glow, 0xFFFFFFFF, 0.7), 110), 18);
		eyes(mesh, c, q, 0.042 * s, 0.03 * s, 0.112 * s, 0.017 * s, glow);
		for (int i = 0; i < 2; i++) {
			Quaternionf ring = new Quaternionf().rotationY((float) (time * (0.5 + i * 0.3) + i * 1.7))
					.rotateX((float) (0.5 + i * 1.1 + Math.sin(time * 0.4 + i) * 0.25));
			double r = (0.165 + i * 0.03) * s;
			mesh.material(Meshes.Material.METAL);
			mesh.emission(0.12f);
			mesh.torus(c.x, c.y, c.z, ring, r, 0.006 * s, 0, 48, (u, v) -> Fx.lerp(gold, 0xFFFFF2C8, 0.25 * Math.max(0, Math.sin(u * Math.PI * 2 * 3))));
			for (int k = 0; k < 6; k++) {
				double a = Math.PI * 2 * k / 6;
				Vector3f stud = ring.transform(new Vector3f((float) (Math.cos(a) * r), 0, (float) (Math.sin(a) * r)));
				mesh.emission(0.5f);
				mesh.sphere(c.x + stud.x, c.y + stud.y, c.z + stud.z, 0.009 * s, Fx.lerp(gold, glow, 0.5), 8);
			}
		}
		Quaternionf plane = new Quaternionf().rotationX(0.35f).rotateZ((float) (Math.sin(time * 0.3) * 0.2));
		for (int k = 0; k < 4; k++) {
			double a = -time * 0.7 + Math.PI * 2 * k / 4;
			double r = 0.27 * s;
			Vector3f pos = plane.transform(new Vector3f((float) (Math.cos(a) * r), (float) (Math.sin(time * 1.6 + k) * 0.015 * s),
					(float) (Math.sin(a) * r)));
			Vector3f out = new Vector3f(pos).normalize();
			Quaternionf face = new Quaternionf().rotationY((float) Math.atan2(out.x, out.z));
			double x = c.x + pos.x;
			double y = c.y + pos.y;
			double z = c.z + pos.z;
			mesh.material(Meshes.Material.MATTE);
			mesh.emission(0.03f);
			mesh.box(x, y, z, face, 0.026 * s, 0.036 * s, 0.007 * s, 0xFF2B2735);
			mesh.material(Meshes.Material.NEON);
			mesh.emission(0.8f);
			mesh.bloom(true, 0.7f);
			int lit = Fx.lerp(glow, 0xFFFFFFFF, 0.35);
			Vector3f front = face.transform(new Vector3f(0, 0, 0.0075f * (float) s));
			mesh.box(x + front.x, y + front.y, z + front.z, face, 0.003 * s, 0.026 * s, 0.0015 * s, lit);
			for (int b = 0; b < 2; b++) {
				int pattern = (k * 7 + b * 3) % 4;
				double by = (b == 0 ? 0.012 : -0.008) * s;
				double tiltZ = (pattern % 2 == 0 ? 0.7 : -0.7);
				double bx = (pattern < 2 ? 0.009 : -0.009) * s;
				Quaternionf branch = new Quaternionf(face).rotateZ((float) tiltZ);
				Vector3f off = face.transform(new Vector3f((float) bx, (float) by, 0.0075f * (float) s));
				mesh.box(x + off.x, y + off.y, z + off.z, branch, 0.0025 * s, 0.012 * s, 0.0015 * s, lit);
			}
			mesh.bloom(false, 0);
		}
		fx.bloom(true);
		for (int k = 0; k < 5; k++) {
			double a = time * 1.8 + k * Math.PI * 2 / 5;
			double r = 0.34 * s;
			fx.glow(c.x + Math.cos(a) * r, c.y + Math.sin(a * 2) * 0.05 * s, c.z + Math.sin(a) * r, 0.035 * s, Fx.withAlpha(glow, 170));
		}
		fx.glow(c.x, c.y, c.z, (0.4 + beat * 0.12) * s, Fx.withAlpha(glow, 70));
		fx.bloom(false);
	}
	private void trail(Fx fx, Vec3 at, int glow, double speed, double s) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		if (r.nextDouble() < 0.25 + Math.min(0.6, speed * 0.3)) {
			trail.add(new double[] {at.x + r.nextGaussian() * 0.06 * s, at.y + r.nextGaussian() * 0.06 * s,
					at.z + r.nextGaussian() * 0.06 * s, time, r.nextDouble(0.8, 1.6)});
		}
		trail.removeIf(p -> time - p[3] > p[4]);
		fx.additive(true);
		for (double[] p : trail) {
			double u = (time - p[3]) / p[4];
			float twinkle = (float) (0.5 + 0.5 * Math.sin(time * 12 + p[4] * 40));
			fx.star(p[0], p[1] - u * 0.15, p[2], 0.035 * s * (1 - u), Fx.withAlpha(Fx.lerp(glow, 0xFFFFFFFF, 0.5), (int) (220 * (1 - u) * twinkle)),
					p[4] * 10);
		}
		fx.additive(false);
	}
}
