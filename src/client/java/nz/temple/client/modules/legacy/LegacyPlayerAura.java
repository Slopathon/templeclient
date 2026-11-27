package nz.temple.client.modules.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import nz.temple.client.modules.cosmetic.Look;
import nz.temple.client.modules.cosmetic.Previewable;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Meshes;
import nz.temple.client.render.Theme;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;

public final class LegacyPlayerAura extends Module implements Previewable, nz.temple.client.render.FxSource {
	private final BoolSetting wingsOn;
	private final nz.temple.setting.ModeSetting wingsAtRest;
	private final BoolSetting haloOn;
	private final BoolSetting gyroscopeOn;
	private final BoolSetting crystalsOn;
	private final BoolSetting crownOn;
	private final BoolSetting bladesOn;
	private final BoolSetting shieldOn;
	private final BoolSetting hornsOn;
	private final ModeSetting hornStyle;
	private final ModeSetting wingStyle;
	private final BoolSetting hatOn;
	private final BoolSetting earsOn;
	private final ModeSetting earStyle;
	private final ModeSetting crownStyle;
	private final ModeSetting bladeStyle;
	private final NumberSetting bladeCount;
	private final BoolSetting runesOn;
	private final BoolSetting spiritsOn;
	private final BoolSetting ribbonsOn;
	private final java.util.Map<java.util.UUID, double[]> wingState = new java.util.HashMap<>();
	private long lastNanos = System.nanoTime();
	private final ModeSetting who;
	private final NumberSetting size;
	private final NumberSetting speed;
	private final NumberSetting feathers;
	private final NumberSetting rings;
	private final BoolSetting firstPerson;
	private final Look look;
	private double frameDt;
	public LegacyPlayerAura() {
		super("Player Aura", Category.COSMETICS, "Wings, halos, horns, ears, crowns, rune circles and other 3D accessories worn by players. "
				+ "Any combination can be worn.");
		wingsOn = add(new BoolSetting("Wings", true).describe("Feathered wings that flap as you move."));
		wingsAtRest = add(new nz.temple.setting.ModeSetting("Wings at rest", java.util.List.of("Spread", "Folded"), "Spread")
				.describe("Standing still, Spread keeps the wings out with a slow beat. Folded lays them against your back."));
		haloOn = add(new BoolSetting("Halo", false).describe("A ring above the head."));
		gyroscopeOn = add(new BoolSetting("Gyroscope", false).describe("Nested rings rotating on different axes around the body."));
		crystalsOn = add(new BoolSetting("Crystals", false).describe("Crystals orbiting the head."));
		crownOn = add(new BoolSetting("Crown", false).describe("A crown with gems and pointed tips. The Royal style adds crossed arches "
				+ "over velvet with an orb on top."));
		bladesOn = add(new BoolSetting("Orbiting blades", false).describe("Swords circling the body, leaving a light trail."));
		shieldOn = add(new BoolSetting("Shield shell", false).describe("A translucent segmented dome around the player."));
		hornsOn = add(new BoolSetting("Horns", false).describe("Horns that follow your head."));
		earsOn = add(new BoolSetting("Ears", false).describe("Animal ears that fold back as you move and twitch occasionally."));
		runesOn = add(new BoolSetting("Rune circle", false).describe("A magic circle at your feet with rings, runes and rotating triangles."));
		spiritsOn = add(new BoolSetting("Spirits", false).describe("Three wisps looping around you with light trails."));
		ribbonsOn = add(new BoolSetting("Ribbons", false).describe("Two ribbons spiralling up around the body."));
		hatOn = add(new BoolSetting("China hat", false).describe("A wide cone hat that tilts with your head."));
		group("Styles");
		wingStyle = add(new ModeSetting("Wing style", List.of("Feathered", "Bat", "Butterfly"), "Feathered")
				.describe("Feathered has layered feathers. Bat has a membrane stretched between finger bones. Butterfly has veined, "
						+ "patterned wings that flex as they beat.")
				.visibleWhen(wingsOn::get));
		hornStyle = add(new ModeSetting("Horn style", List.of("Devil", "Ram", "Dragon"), "Devil")
				.describe("Devil horns are short with forward hooks. Ram horns curl around the ears. Dragon horns are long and swept back.")
				.visibleWhen(hornsOn::get));
		earStyle = add(new ModeSetting("Ear style", List.of("Cat", "Fox", "Bunny"), "Cat")
				.describe("Cat ears are small and pointed. Fox ears are tall and wide. Bunny ears are long and flop back when you run.")
				.visibleWhen(earsOn::get));
		crownStyle = add(new ModeSetting("Crown style", List.of("Royal", "Spiked", "Circlet"), "Royal")
				.describe("Royal has a gemmed band, points, velvet and crossed arches with an orb. Spiked has tall points with pearl tips. "
						+ "Circlet is a slim band of gems with a drop at the forehead.")
				.visibleWhen(crownOn::get));
		bladeStyle = add(new ModeSetting("Blade style", List.of("Sword", "Energy"), "Sword")
				.describe("Sword has a guard, wrapped grip and pommel gem. Energy uses the same shape with glowing edges.")
				.visibleWhen(bladesOn::get));
		bladeCount = add(new NumberSetting("Blades", 3, 1, 6, 1, "").describe("Number of swords.").visibleWhen(bladesOn::get));
		group();
		who = add(new ModeSetting("Who", List.of("Self", "Players", "Both"), "Self").describe("Who wears the accessories."));
		group();
		size = add(new NumberSetting("Size", 1, 0.3, 3, 0.05, "x").describe("Overall scale."));
		speed = add(new NumberSetting("Speed", 1, 0.2, 3, 0.1, "x").describe("Animation speed."));
		feathers = add(new NumberSetting("Feathers", 9, 4, 16, 1, "").describe("Flight feathers per wing.")
				.visibleWhen(() -> wingsOn.get() && wingStyle.is("Feathered")));
		rings = add(new NumberSetting("Rings", 3, 1, 5, 1, "").describe("Number of rings.")
				.visibleWhen(gyroscopeOn::get));
		firstPerson = add(new BoolSetting("Show in first person", true)
				.describe("Shows your own accessories in first person. Wings are behind you, so they show when you look down or turn."));
		group();
		look = new Look(s -> add(s), "Gold");
	}
	@Override
	public void preview(Vec3 at) {
	}
	public void emit(Fx fx, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null || mc.player == null) {
			return;
		}
		long now = System.nanoTime();
		frameDt = Math.min(0.1, (now - lastNanos) / 1e9);
		lastNanos = now;
		boolean seesSelf = !mc.options.getCameraType().isFirstPerson() || nz.temple.client.util.DetachedCamera.active();
		for (Player player : mc.level.players()) {
			boolean self = player == mc.player;
			if (self ? who.is("Players") : who.is("Self")) {
				continue;
			}
			if (!player.isAlive() || player.isInvisible() || player.distanceToSqr(mc.player) > 96 * 96) {
				continue;
			}
			if (self && !seesSelf && !firstPerson.get()) {
				continue;
			}
			draw(fx, player, partial);
		}
		wingState.keySet().removeIf(id -> mc.level.getPlayerByUUID(id) == null);
		earState.keySet().removeIf(id -> mc.level.getPlayerByUUID(id) == null);
	}
	private void draw(Fx fx, Player player, float partial) {
		fx.mesh().transform(nz.temple.client.modules.cosmetic.Emotes.auraTransform(player, partial));
		try {
			drawWorn(fx, player, partial);
		} finally {
			fx.mesh().transform(null);
		}
	}
	private java.util.function.Predicate<String> pieceInFirstPerson = name -> true;
	private java.util.function.DoubleSupplier hatScale = () -> 1;

	private void drawWorn(Fx fx, Player player, float partial) {
		Vec3 p = player.getPosition(partial);
		double t = Theme.time() * speed.get();
		double s = size.get();
		Minecraft mc = Minecraft.getInstance();
		boolean own = player == mc.player && mc.options.getCameraType().isFirstPerson() && !nz.temple.client.util.DetachedCamera.active();
		if (shows(wingsOn, own)) {
			wings(fx, player, p, t, s, partial);
		}
		if (shows(haloOn, own)) {
			halo(fx, player, p, t, s, partial);
		}
		if (shows(gyroscopeOn, own)) {
			gyroscope(fx, player, p, t, s);
		}
		if (shows(crystalsOn, own)) {
			crystals(fx, player, p, t, s);
		}
		if (shows(crownOn, own) && !insideHead(player)) {
			crown(fx, player, p, t, s, partial);
		}
		if (shows(bladesOn, own)) {
			blades(fx, player, p, t, s);
		}
		if (shows(shieldOn, own)) {
			shield(fx, player, p, t, s);
		}
		if (shows(hornsOn, own)) {
			horns(fx, player, p, t, s, partial);
		}
		if (shows(earsOn, own)) {
			ears(fx, player, p, t, s, partial);
		}
		if (shows(runesOn, own)) {
			runes(fx, player, p, t, s);
		}
		if (shows(spiritsOn, own)) {
			spirits(fx, player, p, t, s);
		}
		if (shows(ribbonsOn, own)) {
			ribbons(fx, player, p, t, s);
		}
		if (shows(hatOn, own) && !insideHead(player)) {
			chinaHat(fx, player, p, t, s * hatScale.getAsDouble(), partial);
		}
	}
	public void followPieces(java.util.function.Predicate<String> inFirstPerson, java.util.function.DoubleSupplier hatSize) {
		pieceInFirstPerson = inFirstPerson;
		hatScale = hatSize;
	}
	private boolean shows(BoolSetting piece, boolean own) {
		return piece.get() && !(own && !pieceInFirstPerson.test(piece.name()));
	}
	private static boolean insideHead(Player player) {
		Minecraft mc = Minecraft.getInstance();
		return player == mc.player && mc.options.getCameraType().isFirstPerson() && !nz.temple.client.util.DetachedCamera.active();
	}

	private void chinaHat(Fx fx, Player player, Vec3 p, double t, double s, float partial) {
		Meshes mesh = fx.mesh();
		Quaternionf head = headRotation(player, partial);
		double radius = 0.62 * s;
		double height = 0.3 * s;
		double[] base = headPoint(headPivot(player, p), head, 0, hatSeat(player, radius, height), 0);
		int segments = 48;
		look.apply(mesh, 0.1f);
		double slope = Math.atan2(radius, height);
		double ny = Math.sin(slope);
		double nr = Math.cos(slope);
		Vector3f apex = head.transform(new Vector3f(0, (float) height, 0));
		double spin = t * 0.15;
		for (int i = 0; i < segments; i++) {
			double a0 = Math.PI * 2 * i / segments;
			double a1 = Math.PI * 2 * (i + 1) / segments;
			Vector3f r0 = head.transform(new Vector3f((float) (Math.cos(a0) * radius), 0, (float) (Math.sin(a0) * radius)));
			Vector3f r1 = head.transform(new Vector3f((float) (Math.cos(a1) * radius), 0, (float) (Math.sin(a1) * radius)));
			Vector3f n0 = head.transform(new Vector3f((float) (Math.cos(a0) * nr), (float) ny, (float) (Math.sin(a0) * nr)));
			Vector3f n1 = head.transform(new Vector3f((float) (Math.cos(a1) * nr), (float) ny, (float) (Math.sin(a1) * nr)));
			Vector3f nm = new Vector3f(n0).add(n1).normalize();
			int c0 = look.color(i / (double) segments + spin, 1);
			int c1 = look.color((i + 1) / (double) segments + spin, 1);
			int top = look.color(0.5 + spin, 1);
			mesh.quad(new double[] {base[0] + r0.x, base[1] + r0.y, base[2] + r0.z, n0.x, n0.y, n0.z},
					new double[] {base[0] + r1.x, base[1] + r1.y, base[2] + r1.z, n1.x, n1.y, n1.z},
					new double[] {base[0] + apex.x, base[1] + apex.y, base[2] + apex.z, nm.x, nm.y, nm.z},
					new double[] {base[0] + apex.x, base[1] + apex.y, base[2] + apex.z, nm.x, nm.y, nm.z}, c0, c1, top, top);
		}
		mesh.torus(base[0], base[1], base[2], head, radius, 0.018 * s, 0, segments, (u, v) -> look.color(u + spin + 0.25, 1));
		if (look.bloom() > 0.2f) {
			fx.additive(true);
			for (int i = 0; i < 24; i++) {
				double a = Math.PI * 2 * i / 24 + t * 0.8;
				Vector3f off = head.transform(new Vector3f((float) (Math.cos(a) * radius), 0, (float) (Math.sin(a) * radius)));
				fx.glow(base[0] + off.x, base[1] + off.y, base[2] + off.z, 0.1 * s, look.color(i / 24.0, 0.22 * look.bloom()));
			}
			fx.additive(false);
		}
	}
	private static double[] headPivot(Player player, Vec3 p) {
		double px = player.isCrouching() ? 19.8 : 24;
		return new double[] {p.x, p.y + px / 16 * 0.9375 * player.getScale(), p.z};
	}
	private static double hatSeat(Player player, double radius, double height) {
		boolean helmet = !player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty();
		double scale = 0.9375 * player.getScale() / 16;
		double half = (helmet ? 5 : 4.5) * scale;
		double top = (helmet ? 9 : 8.5) * scale;
		double corner = half * Math.sqrt(2);
		double clearance = 0.006;
		if (corner >= radius) {
			return top + clearance;
		}
		return top - height * (1 - corner / radius) + clearance;
	}
	private void wings(Fx fx, Player player, Vec3 p, double t, double s, float partial) {
		Meshes mesh = fx.mesh();
		Minecraft mc = Minecraft.getInstance();
		float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		Vector3f forward = new Vector3f(-Mth.sin(bodyYaw), 0, Mth.cos(bodyYaw));
		Vector3f back = new Vector3f(forward).negate();
		Vector3f right = new Vector3f(-Mth.cos(bodyYaw), 0, -Mth.sin(bodyYaw));
		Vector3f up = new Vector3f(0, 1, 0);
		double crouch = player.isCrouching() ? -0.25 : 0;
		double moving = Math.min(1, player.getDeltaMovement().horizontalDistance() * 6);
		boolean airborne = !player.onGround();

		boolean spread = wingsAtRest.is("Spread");
		double targetRate = (airborne ? 5.5 : spread ? 1.6 + moving * 1.6 : 1.4 + moving * 2.2) * speed.get();
		double targetDepth = airborne ? 0.42 : spread ? 0.12 + moving * 0.06 : 0.07 + moving * 0.12;
		double targetOpen = airborne ? 1 : spread ? 0.9 : 0.3 + moving * 0.3;
		double[] st = wingState.computeIfAbsent(player.getUUID(), k -> new double[] {targetRate, targetDepth, 0, targetOpen});
		double ease = 1 - Math.exp(-3.0 * frameDt);
		st[0] += (targetRate - st[0]) * ease;
		st[1] += (targetDepth - st[1]) * ease;
		st[2] = (st[2] + st[0] * frameDt) % (Math.PI * 2000);
		st[3] += (targetOpen - st[3]) * (1 - Math.exp(-2.2 * frameDt));
		double phase = st[2];
		double amp = st[1];
		double open = st[3];
		int primaries = (int) Math.round(feathers.get());
		double height = player.getBbHeight();
		boolean detailed = player == mc.player || player.distanceToSqr(mc.player) < 20 * 20;
		for (int side = -1; side <= 1; side += 2) {
			Vector3f out = new Vector3f(right).mul(side);
			double[] shoulder = {
					p.x + back.x * 0.17 + out.x * 0.1 * s,
					p.y + height * 0.74 + crouch,
					p.z + back.z * 0.17 + out.z * 0.1 * s};
			double b0 = Math.sin(phase) * amp;
			double b1 = Math.sin(phase - 0.45) * amp;
			double b2 = Math.sin(phase - 0.9) * amp;
			Vector3f upper = mix(new Vector3f(out).mul(0.55f).add(new Vector3f(up).mul(0.55f)).add(new Vector3f(back).mul(0.6f)),
					new Vector3f(out).mul(0.9f).add(new Vector3f(up).mul(0.45f)).add(new Vector3f(back).mul(0.3f)), open)
					.add(new Vector3f(up).mul((float) (b0 * 1.3))).normalize();
			Vector3f fore = mix(new Vector3f(out).mul(0.3f).add(new Vector3f(up).mul(-0.3f)).add(new Vector3f(back).mul(0.95f)),
					new Vector3f(out).mul(1f).add(new Vector3f(up).mul(0.08f)).add(new Vector3f(back).mul(0.18f)), open)
					.add(new Vector3f(up).mul((float) (b1 * 1.4))).normalize();
			Vector3f hand = mix(new Vector3f(out).mul(0.12f).add(new Vector3f(up).mul(-0.95f)).add(new Vector3f(back).mul(0.4f)),
					new Vector3f(out).mul(1f).add(new Vector3f(up).mul(-0.1f)).add(new Vector3f(back).mul(0.08f)), open)
					.add(new Vector3f(up).mul((float) (b2 * 1.6))).normalize();
			double[][] joints = new double[4][];
			joints[0] = shoulder;
			joints[1] = along(joints[0], upper, 0.34 * s);
			joints[2] = along(joints[1], fore, 0.44 * s);
			joints[3] = along(joints[2], hand, 0.36 * s);
			Vector3f[] bones = {upper, fore, hand};
			Vector3f hang = new Vector3f(up).mul(-0.85f).add(new Vector3f(back).mul(0.35f)).normalize();
			Vector3f[] trail = new Vector3f[3];
			Vector3f[] normal = new Vector3f[3];
			for (int i = 0; i < 3; i++) {
				Vector3f d = bones[i];
				trail[i] = new Vector3f(hang).sub(new Vector3f(d).mul(hang.dot(d))).normalize();
				normal[i] = new Vector3f(d).cross(trail[i]).normalize();
				if (normal[i].dot(back) < 0) {
					normal[i].negate();
				}
			}
			look.apply(mesh, 0.06f);
			if (!wingStyle.is("Feathered")) {
				switch (wingStyle.get()) {
					case "Bat" -> batWing(mesh, joints, bones, trail, back, up, out, p, height, crouch, open, s);
					default -> butterflyWing(mesh, p, back, up, out, height, crouch, phase, amp, open, airborne, s, detailed);
				}
				continue;
			}
			double[] bonePath = new double[12];
			double[] boneR = {0.034 * s, 0.03 * s, 0.022 * s, 0.01 * s};
			int[] boneC = new int[4];
			for (int i = 0; i < 4; i++) {
				System.arraycopy(joints[i], 0, bonePath, i * 3, 3);
				boneC[i] = look.color(0.05 + i * 0.05, 1);
			}
			mesh.tube(bonePath, 4, boneR, boneC, 7);
			for (int i = 0; i < primaries; i++) {
				double f = primaries == 1 ? 0 : (double) i / (primaries - 1);
				double c = 2.02 + f * 0.93;
				float reach = (float) (0.2 + f * (0.55 + 0.6 * open));
				Vector3f dir = new Vector3f(trail[2]).mul(1 - reach * 0.7f).add(new Vector3f(hand).mul(reach)).normalize();
				double len = s * (0.62 + 0.42 * Math.sin(Math.min(1, f * 1.15) * Math.PI * 0.62));
				feather(mesh, joints, bones, normal, c, dir, len, 0.105 * s, 0.0005 * s * i, 0.5 + f * 0.1, trail, 0.08 * s);
			}
			int secondaries = Math.max(6, (int) Math.round(primaries * 1.2));
			for (int i = 0; i < secondaries; i++) {
				double f = (double) i / (secondaries - 1);
				double c = 1.02 + f * 0.98;
				Vector3f dir = new Vector3f(trail[1]).add(new Vector3f(fore).mul(-0.1f + (float) f * 0.12f)).normalize();
				double len = s * (0.56 + 0.07 * f);
				feather(mesh, joints, bones, normal, c, dir, len, 0.125 * s, (0.004 + 0.0005 * i) * s, 0.42, trail, 0.05 * s);
			}
			for (int i = 0; i < 3; i++) {
				double c = 0.5 + i * 0.22;
				Vector3f dir = new Vector3f(trail[0]).add(new Vector3f(upper).mul(-0.35f)).normalize();
				feather(mesh, joints, bones, normal, c, dir, s * (0.46 - i * 0.03), 0.14 * s, (0.008 + 0.0006 * i) * s, 0.36, trail,
						0.03 * s);
			}
			coverts(mesh, joints, bones, normal, trail, s, secondaries + primaries, 0.9, 2.9, 0.34, 0.1, 0.015, 0.26);
			if (detailed) {
				coverts(mesh, joints, bones, normal, trail, s, secondaries + primaries, 0.45, 2.6, 0.21, 0.085, 0.024, 0.16);
				coverts(mesh, joints, bones, normal, trail, s, secondaries + primaries + 4, 0.12, 2.35, 0.12, 0.07, 0.032, 0.06);
			}
			if (look.bloom() > 0.2f) {
				fx.additive(true);
				fx.glow(joints[3][0], joints[3][1], joints[3][2], 0.45 * s, look.color(0.6, 0.3 * look.bloom()));
				fx.glow(joints[2][0], joints[2][1] - 0.3 * s, joints[2][2], 0.9 * s, look.color(0.4, 0.18 * look.bloom()));
				fx.additive(false);
			}
		}
	}
	private void batWing(Meshes mesh, double[][] joints, Vector3f[] bones, Vector3f[] trail, Vector3f back, Vector3f up, Vector3f out,
			Vec3 p, double height, double crouch, double open, double s) {
		int bone = look.color(0.05, 1);
		int skin = Fx.withAlpha(Fx.lerp(look.color(0.25), 0xFF16121C, 0.35), 225 * look.alpha());
		double[] armPath = new double[9];
		for (int i = 0; i < 3; i++) {
			System.arraycopy(joints[i], 0, armPath, i * 3, 3);
		}
		mesh.tube(armPath, 3, new double[] {0.04 * s, 0.034 * s, 0.026 * s}, new int[] {bone, bone, bone}, 12);
		double[] wrist = joints[2];
		Vector3f hand = bones[2];
		double spread = 0.2 + 0.8 * open;
		double[] lengths = {0.66, 0.74, 0.64, 0.5};
		Vector3f[] dirs = new Vector3f[5];
		double[] lens = new double[5];
		for (int f = 0; f < 4; f++) {
			float k = (float) (f / 3.0 * spread * 0.95);
			dirs[f] = new Vector3f(hand).mul(1 - k).add(new Vector3f(trail[2]).mul(k)).normalize();
			lens[f] = lengths[f] * s;
			double[] tip = along(wrist, dirs[f], lens[f]);
			double[] mid = along(wrist, dirs[f], lens[f] * 0.5);
			mid[0] += back.x * 0.03 * s;
			mid[2] += back.z * 0.03 * s;
			mesh.tube(new double[] {wrist[0], wrist[1], wrist[2], mid[0], mid[1], mid[2], tip[0], tip[1], tip[2]}, 3,
					new double[] {0.017 * s, 0.012 * s, 0.006 * s}, new int[] {bone, bone, bone}, 12);
		}
		for (int f = 0; f < 3; f++) {
			membrane(mesh, wrist, dirs[f], lens[f], dirs[f + 1], lens[f + 1], 0.17, back, skin);
		}
		Vector3f toWrist = new Vector3f((float) (wrist[0] - joints[0][0]), (float) (wrist[1] - joints[0][1]), (float) (wrist[2] - joints[0][2]));
		double wristDist = toWrist.length();
		membrane(mesh, joints[0], new Vector3f(bones[0]), 0.34 * s, toWrist.normalize(), wristDist, 0.1, back, skin);
		mesh.material(Meshes.Material.METAL);
		mesh.emission(0.1f);
		Vector3f clawDir = new Vector3f(hand).add(new Vector3f(up).mul(0.8f)).normalize();
		double[] claw = along(wrist, clawDir, 0.035 * s);
		Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), clawDir);
		mesh.ellipsoid(claw[0], claw[1], claw[2], 0.01 * s, 0.03 * s, 0.01 * s, q, 0xFF1A1716, 10);
		look.apply(mesh, 0.06f);
	}
	private static void membrane(Meshes mesh, double[] root, Vector3f da, double la, Vector3f db, double lb, double sag, Vector3f back,
			int colour) {
		int nu = 6, nv = 5;
		double[][] grid = new double[(nu + 1) * (nv + 1)][];
		for (int j = 0; j <= nv; j++) {
			double v = (double) j / nv;
			for (int i = 0; i <= nu; i++) {
				double u = (double) i / nu;
				double ax = da.x * la * u, ay = da.y * la * u, az = da.z * la * u;
				double bx = db.x * lb * u, by = db.y * lb * u, bz = db.z * lb * u;
				double x = ax + (bx - ax) * v, y = ay + (by - ay) * v, z = az + (bz - az) * v;
				double pull = Math.sin(Math.PI * v) * u * u * sag;
				double billow = Math.sin(Math.PI * v) * Math.sin(Math.PI * u) * 0.04 * Math.max(la, lb);
				grid[j * (nu + 1) + i] = new double[] {root[0] + x * (1 - pull) + back.x * billow, root[1] + y * (1 - pull),
						root[2] + z * (1 - pull) + back.z * billow};
			}
		}
		for (int j = 0; j < nv; j++) {
			for (int i = 0; i < nu; i++) {
				double[] a = grid[j * (nu + 1) + i];
				double[] b = grid[j * (nu + 1) + i + 1];
				double[] c = grid[(j + 1) * (nu + 1) + i + 1];
				double[] d = grid[(j + 1) * (nu + 1) + i];
				quadFace(mesh, a, b, c, d, back, colour);
			}
		}
	}
	private static void quadFace(Meshes mesh, double[] a, double[] b, double[] c, double[] d, Vector3f toward, int colour) {
		double ux = c[0] - a[0], uy = c[1] - a[1], uz = c[2] - a[2];
		double vx = d[0] - b[0], vy = d[1] - b[1], vz = d[2] - b[2];
		double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
		double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len < 1e-12) {
			return;
		}
		nx /= len;
		ny /= len;
		nz /= len;
		if (nx * toward.x + ny * toward.y + nz * toward.z < 0) {
			nx = -nx;
			ny = -ny;
			nz = -nz;
		}
		mesh.quad(new double[] {a[0], a[1], a[2], nx, ny, nz}, new double[] {b[0], b[1], b[2], nx, ny, nz},
				new double[] {c[0], c[1], c[2], nx, ny, nz}, new double[] {d[0], d[1], d[2], nx, ny, nz}, colour, colour, colour, colour);
	}
	private static final double[][] FOREWING = {{0, 0}, {0.06, 0.12}, {0.18, 0.3}, {0.34, 0.46}, {0.52, 0.56}, {0.66, 0.58},
			{0.74, 0.5}, {0.72, 0.36}, {0.64, 0.2}, {0.52, 0.06}, {0.36, -0.01}, {0.16, -0.03}};
	private static final double[][] HINDWING = {{0, 0}, {0.16, -0.02}, {0.34, -0.08}, {0.48, -0.18}, {0.54, -0.32}, {0.48, -0.46},
			{0.38, -0.54}, {0.33, -0.7}, {0.28, -0.72}, {0.25, -0.56}, {0.14, -0.48}, {0.05, -0.28}};
	private void butterflyWing(Meshes mesh, Vec3 p, Vector3f back, Vector3f up, Vector3f out, double height, double crouch, double phase,
			double amp, double open, boolean airborne, double s, boolean detailed) {
		double[] root = {p.x + back.x * 0.17, p.y + height * 0.66 + crouch, p.z + back.z * 0.17};
		double beat = Math.sin(phase);
		double spread = airborne ? 0.6 + 0.4 * beat : 0.68 + 0.1 * open + beat * Math.min(0.25, amp * 3);
		double flex = -0.35 * Math.cos(phase) * (airborne ? 1 : 0.4);
		double size = s * 1.45;
		int rings = detailed ? 14 : 7, segs = detailed ? 56 : 28;
		wingPanel(mesh, root, back, up, out, FOREWING, spread, flex, size, false, rings, segs);
		wingPanel(mesh, root, back, up, out, HINDWING, spread * 0.92, flex * 0.8, size, true, rings, segs);
	}

	private static double[] wingPoint(double[] root, Vector3f back, Vector3f up, Vector3f out, double x, double y, double spread, double flex,
			double size) {
		double a = Math.max(0.03, Math.min(1.05, spread + flex * x)) * Math.PI / 2;
		double cx = Math.cos(a), sx = Math.sin(a);

		double cup = -0.05 * x * x;
		double px = (back.x * cx + out.x * sx) * x + (-back.x * sx + out.x * cx) * cup;
		double pz = (back.z * cx + out.z * sx) * x + (-back.z * sx + out.z * cx) * cup;
		return new double[] {root[0] + px * size, root[1] + (up.y * y + 0.06 * x) * size, root[2] + pz * size};
	}
	private void wingPanel(Meshes mesh, double[] root, Vector3f back, Vector3f up, Vector3f out, double[][] outline, double spread, double flex,
			double size, boolean hind, int rings, int segs) {
		int n = outline.length;
		double[][] edge = new double[segs + 1][];
		for (int k = 0; k <= segs; k++) {
			double f = (double) k / segs * n;
			int i = Math.min(n - 1, (int) f);
			double t = f - i;
			double[] p0 = outline[(i - 1 + n) % n], p1 = outline[i % n], p2 = outline[(i + 1) % n], p3 = outline[(i + 2) % n];
			edge[k] = new double[] {catmull(p0[0], p1[0], p2[0], p3[0], t), catmull(p0[1], p1[1], p2[1], p3[1], t)};
		}
		double[][][] pts = new double[rings + 1][segs + 1][];
		int[][] cols = new int[rings + 1][segs + 1];
		for (int r = 0; r <= rings; r++) {
			double k = (double) r / rings;
			for (int i = 0; i <= segs; i++) {
				double x = edge[i][0] * k, y = edge[i][1] * k;
				pts[r][i] = wingPoint(root, back, up, out, x, y, spread, flex, size);
				cols[r][i] = wingColour(x, y, k, i, segs, hind);
			}
		}
		Vector3f n0 = new Vector3f();
		for (int r = 0; r < rings; r++) {
			for (int i = 0; i < segs; i++) {
				double[] a = pts[r][i], b = pts[r][i + 1], c = pts[r + 1][i + 1], d = pts[r + 1][i];
				double ux = c[0] - a[0], uy = c[1] - a[1], uz = c[2] - a[2];
				double vx = d[0] - b[0], vy = d[1] - b[1], vz = d[2] - b[2];
				n0.set((float) (uy * vz - uz * vy), (float) (uz * vx - ux * vz), (float) (ux * vy - uy * vx));
				if (n0.lengthSquared() < 1e-14) {
					continue;
				}
				n0.normalize();
				if (n0.dot(back) < 0) {
					n0.negate();
				}
				quadColoured(mesh, a, b, c, d, n0, cols[r][i], cols[r][i + 1], cols[r + 1][i + 1], cols[r + 1][i]);
			}
		}
		int spots = hind ? 9 : 11;
		for (int j = 0; j < spots; j++) {
			for (int row = 0; row < 2; row++) {
				double f = (j + 0.5 + row * 0.5) / (spots + 1);
				int i = (int) Math.round(f * segs);
				double k = row == 0 ? 0.9 : 0.955;
				spot(mesh, root, back, up, out, edge[i][0] * k, edge[i][1] * k, row == 0 ? 0.014 : 0.01, spread, flex, size, 0xFFF4F1E8);
			}
		}
		if (!hind) {
			double[][] apex = {{0.6, 0.47}, {0.64, 0.42}, {0.57, 0.4}, {0.66, 0.34}, {0.52, 0.47}};
			for (double[] a : apex) {
				spot(mesh, root, back, up, out, a[0], a[1], 0.02, spread, flex, size, Fx.lerp(0xFFFFF4D6, look.color(0.2), 0.25));
			}
		}
	}
	private void spot(Meshes mesh, double[] root, Vector3f back, Vector3f up, Vector3f out, double x, double y, double r, double spread,
			double flex, double size, int colour) {
		int c = Fx.withAlpha(colour, 245 * look.alpha());
		double[] centre = wingPoint(root, back, up, out, x, y, spread, flex, size);
		double a = Math.max(0.03, Math.min(1.05, spread + flex * x)) * Math.PI / 2;
		Vector3f nrm = new Vector3f(back).mul((float) -Math.sin(a)).add(new Vector3f(out).mul((float) Math.cos(a)));
		if (nrm.dot(back) < 0) {
			nrm.negate();
		}
		for (int side = -1; side <= 1; side += 2) {
			double off = 0.0025 * side;
			double[] cc = {centre[0] + nrm.x * off, centre[1] + nrm.y * off, centre[2] + nrm.z * off};
			Vector3f nn = new Vector3f(nrm).mul(side);
			for (int k = 0; k < 8; k++) {
				double t0 = Math.PI * 2 * k / 8, t1 = Math.PI * 2 * (k + 1) / 8;
				double[] p0 = wingPoint(root, back, up, out, x + Math.cos(t0) * r, y + Math.sin(t0) * r, spread, flex, size);
				double[] p1 = wingPoint(root, back, up, out, x + Math.cos(t1) * r, y + Math.sin(t1) * r, spread, flex, size);
				for (int q = 0; q < 3; q++) {
					p0[q] += nrm.get(q) * off;
					p1[q] += nrm.get(q) * off;
				}
				quadColoured(mesh, cc, cc, p1, p0, nn, c, c, c, c);
			}
		}
	}
	private int wingColour(double x, double y, double k, int i, int segs, boolean hind) {
		float a = 240 * look.alpha();
		int black = 0xFF120F12;
		if (k > 0.86) {
			return Fx.withAlpha(black, a);
		}
		if (!hind && Math.hypot(x - 0.66, y - 0.46) < 0.13) {
			return Fx.withAlpha(black, a);
		}
		int veinEvery = 7;
		if (i % veinEvery == 0 && k > 0.12) {
			return Fx.withAlpha(black, a);
		}
		double cx = hind ? 0.18 : 0.24, cy = hind ? -0.16 : 0.18;
		double ell = Math.hypot((x - cx) / (hind ? 0.12 : 0.16), (y - cy) / (hind ? 0.1 : 0.08));
		if (Math.abs(ell - 1) < 0.12) {
			return Fx.withAlpha(black, a);
		}
		int base = vivid(look.color((hind ? 0.25 : 0.0) + k * 0.35));
		base = Fx.lerp(base, 0xFFFFFFFF, 0.08 * k);
		if (k < 0.2) {
			base = Fx.lerp(base, 0xFF2A1E18, 0.65 * (1 - k / 0.2));
		}
		if (k > 0.78) {
			base = Fx.lerp(base, black, (k - 0.78) / 0.08 * 0.5);
		}
		return Fx.withAlpha(base, a);
	}
	private static int vivid(int argb) {
		int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, bl = argb & 0xFF;
		int max = Math.max(r, Math.max(g, bl)), min = Math.min(r, Math.min(g, bl));
		if (max - min < 8) {
			return argb;
		}
		double k = 255.0 / max;
		double span = max - min;
		r = (int) Math.min(255, (r - min) / span * 255 * 0.82 + r * k * 0.18);
		g = (int) Math.min(255, (g - min) / span * 255 * 0.82 + g * k * 0.18);
		bl = (int) Math.min(255, (bl - min) / span * 255 * 0.82 + bl * k * 0.18);
		return (argb & 0xFF000000) | r << 16 | g << 8 | bl;
	}
	private static void quadColoured(Meshes mesh, double[] a, double[] b, double[] c, double[] d, Vector3f n, int ca, int cb, int cc,
			int cd) {
		mesh.quad(new double[] {a[0], a[1], a[2], n.x, n.y, n.z}, new double[] {b[0], b[1], b[2], n.x, n.y, n.z},
				new double[] {c[0], c[1], c[2], n.x, n.y, n.z}, new double[] {d[0], d[1], d[2], n.x, n.y, n.z}, ca, cb, cc, cd);
	}
	private static double catmull(double p0, double p1, double p2, double p3, double t) {
		double t2 = t * t, t3 = t2 * t;
		return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
	}
	private static Vector3f mix(Vector3f a, Vector3f b, double t) {
		return new Vector3f(a).lerp(b, (float) Math.max(0, Math.min(1, t)));
	}
	private static double[] along(double[] from, Vector3f dir, double length) {
		return new double[] {from[0] + dir.x * length, from[1] + dir.y * length, from[2] + dir.z * length};
	}
	private static double[] chain(double[][] joints, double c) {
		int i = Math.max(0, Math.min(2, (int) Math.floor(c)));
		double k = Math.max(0, Math.min(1, c - i));
		return new double[] {
				joints[i][0] + (joints[i + 1][0] - joints[i][0]) * k,
				joints[i][1] + (joints[i + 1][1] - joints[i][1]) * k,
				joints[i][2] + (joints[i + 1][2] - joints[i][2]) * k};
	}
	private void coverts(Meshes mesh, double[][] joints, Vector3f[] bones, Vector3f[] normal, Vector3f[] trail, double s,
			int count, double from, double to, double length, double width, double lift, double tone) {
		for (int i = 0; i < count; i++) {
			double f = (double) i / (count - 1);
			double c = from + f * (to - from);
			int seg = Math.max(0, Math.min(2, (int) Math.floor(c)));
			Vector3f dir = new Vector3f(trail[seg]).add(new Vector3f(bones[seg]).mul(0.28f)).normalize();
			double len = s * length * (seg == 2 ? 1.1 : 1) * (0.92 + 0.08 * Math.sin(i * 2.3));
			feather(mesh, joints, bones, normal, c, dir, len, width * s, (lift + 0.0004 * i) * s, tone + f * 0.12, trail, 0.02 * s);
		}
	}

	private void feather(Meshes mesh, double[][] joints, Vector3f[] bones, Vector3f[] normal, double c, Vector3f dir, double length,
			double width, double lift, double tone, Vector3f[] trail, double bend) {
		int seg = Math.max(0, Math.min(2, (int) Math.floor(c)));
		Vector3f n = normal[seg];
		double[] root = chain(joints, c);
		root[0] += n.x * lift;
		root[1] += n.y * lift;
		root[2] += n.z * lift;
		float wob = (float) (Math.sin(c * 37.1 + tone * 11) * 0.04);
		Vector3f d = new Vector3f(dir).add(new Vector3f(bones[seg]).mul(wob)).normalize();
		Quaternionf q = frame(d, n);
		Vector3f z = q.transform(new Vector3f(0, 0, 1));
		double w = z.dot(trail[seg]) < 0 ? -width : width;
		int rootColour = look.color(tone * 0.6, 1);
		int tipColour = look.color(tone * 0.6 + 0.25, 0.97);
		if (!look.light() && !look.glass()) {
			tipColour = Fx.lerp(tipColour, tipColour & 0xFF000000, 0.14);
		}
		int shaft = Fx.lerp(rootColour, 0xFFFFFFFF, 0.35);
		mesh.feather(root[0], root[1], root[2], q, length, w, 0.09, bend, rootColour, tipColour, shaft);
	}
	private static double[] point(double[] path, double u) {
		double f = u * 5;
		int i = Math.min(4, (int) Math.floor(f));
		double k = f - i;
		return new double[] {
				path[i * 3] + (path[(i + 1) * 3] - path[i * 3]) * k,
				path[i * 3 + 1] + (path[(i + 1) * 3 + 1] - path[i * 3 + 1]) * k,
				path[i * 3 + 2] + (path[(i + 1) * 3 + 2] - path[i * 3 + 2]) * k};
	}
	private static Quaternionf frame(Vector3f along, Vector3f normal) {
		Vector3f x = new Vector3f(along).normalize();
		Vector3f z = new Vector3f(x).cross(normal).normalize();
		Vector3f y = new Vector3f(z).cross(x).normalize();
		return new Quaternionf().setFromNormalized(new Matrix3f(x, y, z));
	}
	private static double[] neck(Player player, Vec3 p) {
		return new double[] {p.x, p.y + (player.isCrouching() ? 1.22 : 1.5), p.z};
	}
	private static Quaternionf headRotation(Player player, float partial) {
		float yaw = Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot) * Mth.DEG_TO_RAD;
		float pitch = player.getViewXRot(partial) * Mth.DEG_TO_RAD;
		return new Quaternionf().rotationY(-yaw).rotateX(pitch);
	}
	private static double[] headPoint(double[] neck, Quaternionf q, double x, double y, double z) {
		Vector3f v = q.transform(new Vector3f((float) x, (float) y, (float) z));
		return new double[] {neck[0] + v.x, neck[1] + v.y, neck[2] + v.z};
	}
	private void horns(Fx fx, Player player, Vec3 p, double t, double s, float partial) {
		Meshes mesh = fx.mesh();
		double[] neck = neck(player, p);
		Quaternionf q = headRotation(player, partial);
		String style = hornStyle.get();
		int count = 12;
		look.apply(mesh, 0.12f);
		for (int side = -1; side <= 1; side += 2) {
			double[] path = new double[count * 3];
			double[] radius = new double[count];
			int[] colours = new int[count];
			double[] base = null;
			for (int i = 0; i < count; i++) {
				double f = (double) i / (count - 1);
				double x;
				double y;
				double z;
				double r;
				switch (style) {
					case "Ram" -> {
						double a = Math.PI / 2 + f * Math.PI * 1.65;
						double rr = 0.15 * (1 - 0.45 * f);
						x = side * (0.2 + 0.1 * f);
						y = 0.34 + rr * Math.sin(a);
						z = -0.02 + rr * Math.cos(a);
						r = 0.07 * (1 - f) + 0.014;
					}
					case "Dragon" -> {
						x = side * (0.14 + 0.1 * f);
						y = 0.45 + 0.26 * f - 0.1 * f * f;
						z = 0.02 - 0.58 * f;
						r = 0.05 * (1 - f) + 0.004;
					}
					default -> {
						x = side * (0.16 + 0.12 * f);
						y = 0.47 + 0.4 * f - 0.05 * f * f * f;
						z = 0.05 - 0.1 * f + 0.2 * f * f * f;
						r = 0.068 * (1 - f) + 0.006;
					}
				}
				if (base == null) {
					base = new double[] {x, y, z};
				}
				double[] w = headPoint(neck, q, base[0] + (x - base[0]) * s, base[1] + (y - base[1]) * s, base[2] + (z - base[2]) * s);
				System.arraycopy(w, 0, path, i * 3, 3);
				radius[i] = r * s;
				colours[i] = look.color(0.05 + f * 0.45, 1);
			}
			mesh.tube(path, count, radius, colours, 9);
			if (look.bloom() > 0.2f) {
				fx.additive(true);
				int last = (count - 1) * 3;
				fx.glow(path[last], path[last + 1], path[last + 2], 0.2 * s, look.color(0.5, 0.35 * look.bloom()));
				fx.additive(false);
			}
		}
	}
	private final java.util.Map<java.util.UUID, double[]> earState = new java.util.HashMap<>();
	private void ears(Fx fx, Player player, Vec3 p, double t, double s, float partial) {
		Meshes mesh = fx.mesh();
		double[] neck = neck(player, p);
		Quaternionf head = headRotation(player, partial);
		String style = earStyle.get();
		double moving = Math.min(1, Math.hypot(player.getX() - player.xo, player.getZ() - player.zo) * 6);
		double[] st = earState.computeIfAbsent(player.getUUID(), k -> new double[] {0});
		st[0] += (moving - st[0]) * (1 - Math.exp(-4 * frameDt));
		double twitch = Math.pow(Math.max(0, Math.sin(t * 1.3 + player.getId())), 40);
		look.apply(mesh, 0.06f);
		for (int side = -1; side <= 1; side += 2) {
			double tilt = side * (style.equals("Bunny") ? 0.12 : 0.28);
			double back = st[0] * (style.equals("Bunny") ? 0.7 : 0.25) + (side > 0 ? twitch * 0.35 : 0);
			Quaternionf q = new Quaternionf(head).rotateX((float) -back).rotateZ((float) -tilt);
			double[] root = headPoint(neck, head, side * (style.equals("Bunny") ? 0.1 : 0.16), 0.49, style.equals("Bunny") ? -0.02 : 0);
			int outer = look.color(0.1, 1);
			int inner = Fx.withAlpha(Fx.lerp(look.color(0.45, 1), 0xFFFFC4D6, 0.45), Fx.alpha(outer));
			switch (style) {
				case "Bunny" -> {
					double h = 0.42 * s;
					Vector3f up = q.transform(new Vector3f(0, 1, 0));
					mesh.ellipsoid(root[0] + up.x * h / 2, root[1] + up.y * h / 2, root[2] + up.z * h / 2, 0.065 * s, h / 2, 0.028 * s, q, outer, 7);
					Vector3f fwd = q.transform(new Vector3f(0, 0, 1));
					mesh.ellipsoid(root[0] + up.x * h / 2 + fwd.x * 0.012 * s, root[1] + up.y * h / 2 + fwd.y * 0.012 * s,
							root[2] + up.z * h / 2 + fwd.z * 0.012 * s, 0.04 * s, h * 0.42, 0.02 * s, q, inner, 6);
				}
				case "Fox" -> pyramid(mesh, root, q, 0.2 * s, 0.26 * s, 0.07 * s, outer, inner);
				default -> pyramid(mesh, root, q, 0.15 * s, 0.17 * s, 0.06 * s, outer, inner);
			}
		}
	}
	private static void pyramid(Meshes mesh, double[] root, Quaternionf q, double width, double height, double depth, int outer, int inner) {
		double[][] local = {{-width / 2, 0, depth / 2}, {width / 2, 0, depth / 2}, {width / 2, 0, -depth / 2}, {-width / 2, 0, -depth / 2}};
		double[][] base = new double[4][];
		for (int i = 0; i < 4; i++) {
			Vector3f v = q.transform(new Vector3f((float) local[i][0], (float) local[i][1], (float) local[i][2]));
			base[i] = new double[] {root[0] + v.x, root[1] + v.y, root[2] + v.z};
		}
		Vector3f tipV = q.transform(new Vector3f(0, (float) height, (float) (-depth * 0.2)));
		double[] tip = {root[0] + tipV.x, root[1] + tipV.y, root[2] + tipV.z};
		for (int i = 0; i < 4; i++) {
			double[] a = base[i];
			double[] b = base[(i + 1) % 4];
			double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
			double vx = tip[0] - a[0], vy = tip[1] - a[1], vz = tip[2] - a[2];
			double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
			double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
			if (len < 1e-12) {
				continue;
			}
			double[] na = {a[0], a[1], a[2], nx / len, ny / len, nz / len};
			double[] nb = {b[0], b[1], b[2], nx / len, ny / len, nz / len};
			double[] nt = {tip[0], tip[1], tip[2], nx / len, ny / len, nz / len};
			int c = i == 0 ? inner : outer;
			mesh.quad(na, nb, nt, nt, c, c, c, c);
		}
	}

	private void runes(Fx fx, Player player, Vec3 p, double t, double s) {
		double y = p.y + 0.03;
		double big = 1.05 * s;
		double turn = t * 0.35;
		fx.additive(true);
		fx.ring(p.x, y, p.z, 0, big, look.color(0, 0.12), look.color(0.3, 0), 48);
		fx.ring(p.x, y, p.z, big - 0.02 * s, big + 0.02 * s, look.color(0, 0.9), look.color(0.1, 0.9), 64);
		fx.ring(p.x, y, p.z, big - 0.16 * s, big - 0.135 * s, look.color(0.3, 0.8), look.color(0.3, 0.8), 64);
		fx.ring(p.x, y, p.z, big * 0.42, big * 0.42 + 0.02 * s, look.color(0.5, 0.75), look.color(0.5, 0.75), 48);
		double runeR = big - 0.078 * s;
		double cell = 0.05 * s;
		for (int i = 0; i < 16; i++) {
			double a = turn + Math.PI * 2 * i / 16;
			double cx = p.x + Math.cos(a) * runeR;
			double cz = p.z + Math.sin(a) * runeR;
			double tx = -Math.sin(a);
			double tz = Math.cos(a);
			double rx = Math.cos(a);
			double rz = Math.sin(a);
			int colour = look.color(i / 16.0, 0.95);
			for (int k = 0; k < 3; k++) {
				int h = (int) Math.abs((long) (Math.sin((i + 1) * 12.9898 + k * 78.233) * 43758.5453));
				int from = h % 9;
				int to = (h / 9) % 9;
				if (from == to) {
					to = (to + 4) % 9;
				}
				double ax = (from % 3 - 1) * cell;
				double ay = (from / 3 - 1) * cell;
				double bx = (to % 3 - 1) * cell;
				double by = (to / 3 - 1) * cell;
				flatLine(fx, cx + tx * ax + rx * ay, cz + tz * ax + rz * ay, cx + tx * bx + rx * by, cz + tz * bx + rz * by, y,
						0.012 * s, colour);
			}
		}
		double triR = big - 0.17 * s;
		for (int tri = 0; tri < 2; tri++) {
			double base = (tri == 0 ? -turn * 1.4 : turn * 0.9) + tri * Math.PI / 3;
			for (int k = 0; k < 3; k++) {
				double a0 = base + Math.PI * 2 * k / 3;
				double a1 = base + Math.PI * 2 * (k + 1) / 3;
				flatLine(fx, p.x + Math.cos(a0) * triR, p.z + Math.sin(a0) * triR, p.x + Math.cos(a1) * triR, p.z + Math.sin(a1) * triR, y,
						0.02 * s, look.color(0.25 + tri * 0.3, 0.8));
			}
		}
		for (int i = 0; i < 6; i++) {
			double a = -turn * 2 + Math.PI * 2 * i / 6;
			fx.glow(p.x + Math.cos(a) * big * 0.42, y + 0.02, p.z + Math.sin(a) * big * 0.42, 0.09 * s, look.color(0.5 + i / 12.0, 0.8));
		}
		fx.additive(false);
	}
	private static void flatLine(Fx fx, double ax, double az, double bx, double bz, double y, double width, int argb) {
		double dx = bx - ax;
		double dz = bz - az;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1e-6) {
			return;
		}
		double nx = -dz / len * width / 2;
		double nz = dx / len * width / 2;
		fx.quad(ax - nx, y, az - nz, argb, bx - nx, y, bz - nz, argb, bx + nx, y, bz + nz, argb, ax + nx, y, az + nz, argb);
	}
	private void spirits(Fx fx, Player player, Vec3 p, double t, double s) {
		double cy = p.y + player.getBbHeight() * 0.55;
		fx.additive(true);
		for (int i = 0; i < 3; i++) {
			int colour = look.color(i / 3.0, 1);
			for (int j = 14; j >= 0; j--) {
				double[] at = spiritAt(p.x, cy, p.z, t - j * 0.03, i, s);
				double fade = 1 - j / 15.0;
				fx.glow(at[0], at[1], at[2], (0.05 + 0.1 * fade) * s, Fx.withAlpha(colour, (float) (255 * 0.45 * fade * fade * look.alpha())));
			}
			double[] at = spiritAt(p.x, cy, p.z, t, i, s);
			fx.orb(at[0], at[1], at[2], 0.2 * s, look.color(i / 3.0, 0.9));
		}
		fx.additive(false);
	}
	private static double[] spiritAt(double x, double y, double z, double t, int i, double s) {
		double a = t * 1.3 + i * Math.PI * 2 / 3;
		double r = (0.7 + 0.12 * Math.sin(t * 0.7 + i)) * s;
		double tilt = i * 1.1;
		double lx = Math.cos(a) * r;
		double lz = Math.sin(a) * r;
		double ly = Math.sin(a * 2) * 0.28 * s + Math.sin(t * 0.9 + i * 2) * 0.12 * s;
		return new double[] {x + lx * Math.cos(tilt) - lz * Math.sin(tilt), y + ly, z + lx * Math.sin(tilt) + lz * Math.cos(tilt)};
	}

	private void ribbons(Fx fx, Player player, Vec3 p, double t, double s) {
		Meshes mesh = fx.mesh();
		double height = player.getBbHeight() + 0.3;
		look.apply(mesh, 0.15f);
		for (int i = 0; i < 2; i++) {
			int index = i;
			Meshes.Surface ribbon = (u, v, o) -> {
				double a = v * Math.PI * 2.3 + t * 1.1 + index * Math.PI;
				double r = (0.55 + 0.07 * Math.sin(v * 7 + t * 2 + index)) * s;
				double y = p.y + 0.05 + v * height * s + Math.sin(v * 5 - t * 2.4) * 0.06;
				double half = 0.07 * s * Math.sin(Math.PI * Math.min(1, Math.max(0, v))) + 0.004;
				double w = (u - 0.5) * 2 * half;
				double tx = -Math.sin(a);
				double tz = Math.cos(a);
				o[0] = p.x + Math.cos(a) * r + tx * w * 0.35;
				o[1] = y + w;
				o[2] = p.z + Math.sin(a) * r + tz * w * 0.35;
				o[3] = Math.cos(a);
				o[4] = 0;
				o[5] = Math.sin(a);
			};
			mesh.surface(ribbon, 2, 48, 0, (u, v) -> look.color(v * 0.8 + index * 0.5, Math.min(1, Math.min(v, 1 - v) * 6)));
		}
	}

	private void halo(Fx fx, Player player, Vec3 p, double t, double s, float partial) {
		Meshes mesh = fx.mesh();
		double bob = Math.sin(t * 1.4) * 0.03 * s;
		Quaternionf head = headRotation(player, partial);
		double[] at = headPoint(neck(player, p), head, 0, 0.5 + 0.22 * s + bob, -0.05);
		double x0 = at[0];
		double y = at[1];
		double z0 = at[2];
		Quaternionf tilt = new Quaternionf(head).rotateX(-0.28f);
		double r = 0.3 * s;
		look.apply(mesh, 0.35f);
		mesh.torus(x0, y, z0, tilt, r, 0.038 * s, 0, 40, (u, v) -> look.color(u, 1));
		mesh.torus(x0, y, z0, tilt, r * 0.78, 0.012 * s, 0, 32, (u, v) -> look.color(u + 0.5, 0.8));
		fx.additive(true);
		for (int i = 0; i < 16; i++) {
			double a = Math.PI * 2 * i / 16 + t * 0.6;
			Vector3f off = tilt.transform(new Vector3f((float) (Math.cos(a) * r), 0, (float) (Math.sin(a) * r)));
			fx.glow(x0 + off.x, y + off.y, z0 + off.z, 0.12 * s, look.color(i / 16.0, 0.25 * look.bloom()));
		}
		fx.glow(x0, y, z0, 0.55 * s, look.color(0, 0.12 * look.bloom()));
		fx.additive(false);
	}
	private void gyroscope(Fx fx, Player player, Vec3 p, double t, double s) {
		Meshes mesh = fx.mesh();
		int count = (int) Math.round(rings.get());
		double cy = p.y + player.getBbHeight() * 0.55;
		look.apply(mesh, 0.2f);
		for (int i = 0; i < count; i++) {
			double r = (0.75 + i * 0.16) * s;
			Quaternionf q = new Quaternionf()
					.rotationY((float) (t * (0.7 + i * 0.35)))
					.rotateX((float) (0.6 + i * 0.9 + Math.sin(t * 0.5 + i) * 0.4))
					.rotateZ((float) (t * 0.3 * (i % 2 == 0 ? 1 : -1)));
			int index = i;
			mesh.torus(p.x, cy, p.z, q, r, 0.028 * s, 0, 48, (u, v) -> look.color(u * 0.5 + index * 0.2, 1));
			double a = t * (1.6 + i * 0.4);
			Vector3f bead = q.transform(new Vector3f((float) (Math.cos(a) * r), 0, (float) (Math.sin(a) * r)));
			mesh.sphere(p.x + bead.x, cy + bead.y, p.z + bead.z, 0.06 * s, look.color(0.5 + i * 0.2, 1), 6);
			fx.additive(true);
			fx.glow(p.x + bead.x, cy + bead.y, p.z + bead.z, 0.25 * s, look.color(0.5 + i * 0.2, 0.35 * look.bloom()));
			fx.additive(false);
		}
	}
	private void crystals(Fx fx, Player player, Vec3 p, double t, double s) {
		Meshes mesh = fx.mesh();
		int count = 5;
		double r = 0.62 * s;
		look.apply(mesh, 0.25f);
		for (int i = 0; i < count; i++) {
			double a = t * 0.9 + Math.PI * 2 * i / count;
			double y = p.y + player.getBbHeight() + 0.15 * s + Math.sin(t * 1.7 + i * 1.3) * 0.08 * s;
			double x = p.x + Math.cos(a) * r;
			double z = p.z + Math.sin(a) * r;
			Quaternionf spin = new Quaternionf().rotationY((float) (t * 2 + i)).rotateZ(0.25f);
			mesh.crystal(x, y, z, spin, 0.26 * s, 0.07 * s, look.color(i / (double) count, 1), 6);
			fx.additive(true);
			fx.glow(x, y, z, 0.3 * s, look.color(i / (double) count, 0.3 * look.bloom()));
			fx.additive(false);
		}
	}
	private static final int RUBY = 0xFFD8203A;
	private static final int SAPPHIRE = 0xFF2C62F0;
	private static final int EMERALD = 0xFF19B866;
	private static final int PEARL = 0xFFF3EEE2;
	private record Loop(double a, double rc) {
		double length() {
			return 8 * (a - rc) + 2 * Math.PI * rc;
		}
		double[] at(double u) {
			double c = a - rc;
			double arc = Math.PI / 2 * rc;
			double d = (u - Math.floor(u)) * length();
			if (d < c) {
				return new double[] {d, a, 0, 1};
			}
			d -= c;
			if (d < arc) {
				return corner(c, c, Math.PI / 2 - d / rc);
			}
			d -= arc;
			if (d < 2 * c) {
				return new double[] {a, c - d, 1, 0};
			}
			d -= 2 * c;
			if (d < arc) {
				return corner(c, -c, -d / rc);
			}
			d -= arc;
			if (d < 2 * c) {
				return new double[] {c - d, -a, 0, -1};
			}
			d -= 2 * c;
			if (d < arc) {
				return corner(-c, -c, -Math.PI / 2 - d / rc);
			}
			d -= arc;
			if (d < 2 * c) {
				return new double[] {-a, -c + d, -1, 0};
			}
			d -= 2 * c;
			if (d < arc) {
				return corner(-c, c, Math.PI - d / rc);
			}
			d -= arc;
			return new double[] {-c + d, a, 0, 1};
		}
		private double[] corner(double cx, double cz, double angle) {
			return new double[] {cx + rc * Math.cos(angle), cz + rc * Math.sin(angle), Math.cos(angle), Math.sin(angle)};
		}
	}
	private static void worn(double[] o, double[] origin, Quaternionf q, double x, double y, double z, double nx, double ny, double nz) {
		Vector3f v = q.transform(new Vector3f((float) x, (float) y, (float) z));
		Vector3f n = q.transform(new Vector3f((float) nx, (float) ny, (float) nz));
		o[0] = origin[0] + v.x;
		o[1] = origin[1] + v.y;
		o[2] = origin[2] + v.z;
		o[3] = n.x;
		o[4] = n.y;
		o[5] = n.z;
	}
	private static double[] worn(double[] origin, Quaternionf q, double x, double y, double z) {
		double[] o = new double[6];
		worn(o, origin, q, x, y, z, 0, 1, 0);
		return o;
	}
	private void crown(Fx fx, Player player, Vec3 p, double t, double s, float partial) {
		Meshes mesh = fx.mesh();
		Quaternionf q = headRotation(player, partial);
		double[] pivot = headPivot(player, p);
		boolean helmet = !player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty();
		double px = 0.9375 * player.getScale() / 16;
		double top = (helmet ? 9 : 8.5) * px;
		String style = crownStyle.get();
		boolean royal = style.equals("Royal");
		boolean circlet = style.equals("Circlet");
		Loop loop = new Loop((helmet ? 5 : 4.5) * px + 0.016, 0.05);
		double y0 = top - (circlet ? 0.03 : 0.065);
		double y1 = top + (circlet ? 0.012 : 0.05);
		double th = circlet ? 0.012 : 0.017;
		double flow = t * 0.03;
		int segments = 72;
		if (royal) {
			double hv = 0.08 * s;
			mesh.material(Meshes.Material.MATTE);
			mesh.emission(0.03f);
			mesh.bloom(false, 0);
			mesh.surface((u, v, o) -> {
				double[] l = loop.at(u);
				double phi = v * Math.PI / 2;
				double k = Math.cos(phi);
				double inset = th + 0.002;
				worn(o, pivot, q, (l[0] - l[2] * inset) * k, y1 - 0.025 + hv * Math.sin(phi), (l[1] - l[3] * inset) * k,
						l[2] * k, Math.sin(phi) + 0.2, l[3] * k);
			}, 64, 8, 0, (u, v) -> Fx.withAlpha(Fx.lerp(0xFF8E1428, 0xFF4A0712, 0.35 + 0.35 * Math.cos(u * Math.PI * 8) * (1 - v)),
					255 * look.alpha()));
		}
		look.apply(mesh, 0.12f);
		mesh.surface((u, v, o) -> {
			double[] l = loop.at(u);
			double bulge = Math.sin(v * Math.PI) * 0.004;
			worn(o, pivot, q, l[0] + l[2] * bulge, y0 + v * (y1 - y0), l[1] + l[3] * bulge, l[2], (0.5 - v) * 0.6, l[3]);
		}, segments, 4, 0, (u, v) -> look.color(u * 0.6 + flow, 1));
		mesh.surface((u, v, o) -> {
			double[] l = loop.at(u);
			worn(o, pivot, q, l[0] - l[2] * th, y0 + v * (y1 - y0), l[1] - l[3] * th, -l[2], 0, -l[3]);
		}, segments, 1, 0, (u, v) -> Fx.lerp(look.color(u * 0.6 + flow, 1), 0xFF2A1A08, 0.35));
		for (int lip = 0; lip < 2; lip++) {
			double y = lip == 0 ? y1 : y0;
			double r = lip == 0 ? th * 0.75 : th * 0.6;
			double[] path = new double[(segments + 1) * 3];
			double[] radius = new double[segments + 1];
			int[] colours = new int[segments + 1];
			for (int i = 0; i <= segments; i++) {
				double u = (double) i / segments;
				double[] l = loop.at(u);
				double[] w = worn(pivot, q, l[0] - l[2] * th * 0.5, y, l[1] - l[3] * th * 0.5);
				System.arraycopy(w, 0, path, i * 3, 3);
				radius[i] = r;
				colours[i] = Fx.lerp(look.color(u * 0.6 + flow, 1), 0xFFFFFFFF, 0.12);
			}
			mesh.tube(path, segments + 1, radius, colours, 10);
		}
		int crests = circlet ? 0 : 8;
		double spacing = loop.length() / 8;
		for (int i = 0; i < crests; i++) {
			boolean tall = i % 2 == 0;
			double h = (royal ? (tall ? 0.115 : 0.075) : (tall ? 0.2 : 0.13)) * s;
			double bw = spacing * (royal ? 0.36 : 0.42);
			look.apply(mesh, 0.12f);
			crest(mesh, pivot, q, loop, i / 8.0, y1, h, bw, th, flow);
			double[] l = loop.at(i / 8.0);
			double lean = 0.022 * h / 0.115;
			double[] tip = worn(pivot, q, l[0] + l[2] * (lean - th * 0.5), y1 + h + 0.01 * s, l[1] + l[3] * (lean - th * 0.5));
			if (!royal && tall) {
				gem(mesh, tip, q, 0, 1, 0, 0.05 * s, 0.018 * s, i % 4 == 0 ? RUBY : SAPPHIRE);
			} else {
				pearl(mesh, tip, 0.014 * s);
			}
		}
		int gems = circlet ? 12 : 8;
		for (int i = 0; i < gems; i++) {
			if (circlet && i == 0) {
				continue;
			}
			double u = (i + (circlet ? 0 : 0.5)) / gems;
			double[] l = loop.at(u);
			double size = (circlet ? 0.6 : 1) * s;
			double[] at = worn(pivot, q, l[0] + l[2] * 0.004, (y0 + y1) / 2, l[1] + l[3] * 0.004);
			Quaternionf face = new Quaternionf(q).mul(new Quaternionf().rotationTo(0, 1, 0, (float) l[2], 0, (float) l[3]));
			look.apply(mesh, 0.12f);
			mesh.torus(at[0], at[1], at[2], face, 0.019 * size, 0.0045 * size, 0, 16, (a, b) -> look.color(a + flow, 1));
			gem(mesh, at, q, l[2], 0, l[3], 0.03 * size, 0.017 * size, switch (i % 3) {
				case 0 -> RUBY;
				case 1 -> SAPPHIRE;
				default -> EMERALD;
			});
		}
		double[] brow = loop.at(0);
		if (!circlet) {
			double[] at = worn(pivot, q, brow[0], (y0 + y1) / 2, brow[1] + 0.006);
			Quaternionf face = new Quaternionf(q).mul(new Quaternionf().rotationTo(0, 1, 0, 0, 0, 1));
			look.apply(mesh, 0.12f);
			mesh.torus(at[0], at[1], at[2], face, 0.028 * s, 0.006 * s, 0, 18, (a, b) -> look.color(a + flow, 1));
			gem(mesh, at, q, 0, 0, 1, 0.045 * s, 0.026 * s, RUBY);
		} else {
			look.apply(mesh, 0.12f);
			double[] hook = worn(pivot, q, brow[0], y0 - 0.002, brow[1] + 0.006);
			mesh.sphere(hook[0], hook[1], hook[2], 0.009 * s, look.color(flow, 1), 8);
			double[] at = worn(pivot, q, brow[0], y0 - 0.03 * s, brow[1] + 0.006);
			gem(mesh, at, q, 0, -1, 0, 0.075 * s, 0.024 * s, SAPPHIRE);
		}
		if (royal) {
			royalArches(mesh, pivot, q, loop, y1, th, s, flow);
		}
		if (look.bloom() > 0.2f) {
			double[] glowAt = worn(pivot, q, 0, y1 + 0.06 * s, 0);
			fx.additive(true);
			fx.glow(glowAt[0], glowAt[1], glowAt[2], 0.45 * s, look.color(flow, 0.12 * look.bloom()));
			fx.additive(false);
		}
	}
	private void crest(Meshes mesh, double[] pivot, Quaternionf q, Loop loop, double u, double y1, double h, double bw, double th, double flow) {
		double[] l = loop.at(u);
		double tx = -l[3];
		double tz = l[2];
		double bx = l[0] - l[2] * th * 0.5;
		double bz = l[1] - l[3] * th * 0.5;
		double lean = 0.022 * h / 0.115;
		for (int side = -1; side <= 1; side += 2) {
			int sd = side;
			mesh.surface((a, v, o) -> {
				double hw = bw * Math.pow(1 - v, 1.6);
				double w = (a * 2 - 1) * hw;
				double off = sd * th * 0.5 * (1 - v * 0.6) + lean * v;
				worn(o, pivot, q, bx + tx * w + l[2] * off, y1 + v * h, bz + tz * w + l[3] * off, l[2] * sd, 0.25 * sd, l[3] * sd);
			}, 4, 6, 0, (a, v) -> Fx.lerp(look.color(u * 0.6 + flow, 1), 0xFFFFFFFF, 0.1 * v));
			mesh.surface((a, v, o) -> {
				double hw = bw * Math.pow(1 - v, 1.6);
				double off = (a * 2 - 1) * th * 0.5 * (1 - v * 0.6) + lean * v;
				worn(o, pivot, q, bx + tx * sd * hw + l[2] * off, y1 + v * h, bz + tz * sd * hw + l[3] * off,
						tx * sd * h, bw * 1.6 * Math.pow(1 - v, 0.6), tz * sd * h);
			}, 1, 6, 0, (a, v) -> Fx.lerp(look.color(u * 0.6 + flow, 1), 0xFFFFFFFF, 0.25));
		}
	}

	private void royalArches(Meshes mesh, double[] pivot, Quaternionf q, Loop loop, double y1, double th, double s, double flow) {
		double ha = 0.15 * s;
		double reach = loop.a() - th * 0.5;
		int n = 25;
		for (int arch = 0; arch < 2; arch++) {
			boolean frontBack = arch == 0;
			double[] path = new double[n * 3];
			double[] radius = new double[n];
			int[] colours = new int[n];
			for (int i = 0; i < n; i++) {
				double phi = Math.PI * i / (n - 1);
				double along = reach * Math.cos(phi);
				double y = y1 + ha * (Math.sin(phi) - 0.12 * Math.pow(Math.sin(phi), 8));
				double[] w = frontBack ? worn(pivot, q, 0, y, along) : worn(pivot, q, along, y, 0);
				System.arraycopy(w, 0, path, i * 3, 3);
				radius[i] = 0.012 * s * (1 - 0.25 * Math.sin(phi));
				colours[i] = look.color(i / (double) n * 0.5 + flow + arch * 0.25, 1);
			}
			look.apply(mesh, 0.12f);
			mesh.tube(path, n, radius, colours, 10);
			for (int k = 1; k <= 5; k++) {
				double phi = Math.PI * k / 6;
				double along = reach * Math.cos(phi);
				double y = y1 + ha * (Math.sin(phi) - 0.12 * Math.pow(Math.sin(phi), 8)) + 0.012 * s * Math.sin(phi);
				double out = 0.012 * s * Math.cos(phi);
				double[] w = frontBack ? worn(pivot, q, 0, y, along + out) : worn(pivot, q, along + out, y, 0);
				pearl(mesh, w, 0.0095 * s);
			}
		}
		look.apply(mesh, 0.15f);
		double orbY = y1 + ha * 0.88 + 0.03 * s;
		double[] orb = worn(pivot, q, 0, orbY, 0);
		mesh.sphere(orb[0], orb[1], orb[2], 0.03 * s, look.color(flow + 0.3, 1), 14);
		mesh.torus(orb[0], orb[1], orb[2], q, 0.031 * s, 0.004 * s, 0, 20, (a, b) -> look.color(a + flow, 1));
		double[] stem = worn(pivot, q, 0, orbY + 0.058 * s, 0);
		mesh.box(stem[0], stem[1], stem[2], q, 0.006 * s, 0.03 * s, 0.006 * s, look.color(flow + 0.4, 1));
		double[] bar = worn(pivot, q, 0, orbY + 0.064 * s, 0);
		mesh.box(bar[0], bar[1], bar[2], q, 0.02 * s, 0.006 * s, 0.006 * s, look.color(flow + 0.4, 1));
	}
	private void gem(Meshes mesh, double[] at, Quaternionf q, double nx, double ny, double nz, double length, double width, int colour) {
		Quaternionf facing = new Quaternionf(q).mul(new Quaternionf().rotationTo(0, 1, 0, (float) nx, (float) ny, (float) nz));
		mesh.bloom(look.neon(), 0.6f);
		mesh.material(Meshes.Material.METAL);
		mesh.emission(look.light() ? 0.55f : 0.28f);
		mesh.crystal(at[0], at[1], at[2], facing, length, width, Fx.withAlpha(colour, 255 * look.alpha()), 8);
	}
	private void pearl(Meshes mesh, double[] at, double radius) {
		mesh.bloom(look.neon(), 0.4f);
		mesh.material(Meshes.Material.METAL);
		mesh.emission(look.light() ? 0.5f : 0.18f);
		mesh.sphere(at[0], at[1], at[2], radius, Fx.withAlpha(PEARL, 255 * look.alpha()), 10);
	}
	private static double[] swordOrbit(double t, int i, int count, double s) {
		double a = t * 1.1 + Math.PI * 2 * i / count;
		double r = 1.0 * s;
		return new double[] {Math.cos(a) * r, Math.sin(t * 1.3 + i * 2.1) * 0.2 * s, Math.sin(a) * r};
	}
	private static Vector3f swordHeading(double t, int i, int count, double s) {
		double[] now = swordOrbit(t, i, count, s);
		double[] next = swordOrbit(t + 0.01, i, count, s);
		return new Vector3f((float) (next[0] - now[0]), (float) (next[1] - now[1]), (float) (next[2] - now[2])).normalize();
	}
	private void blades(Fx fx, Player player, Vec3 p, double t, double s) {
		Meshes mesh = fx.mesh();
		int count = (int) Math.round(bladeCount.get());
		boolean energy = bladeStyle.is("Energy");
		double cy = p.y + player.getBbHeight() * 0.55;
		double k = s * 1.45;
		double length = 0.5 * k;
		for (int i = 0; i < count; i++) {
			double[] now = swordOrbit(t, i, count, s);
			Vector3f along = swordHeading(t, i, count, s);
			Vector3f out = new Vector3f((float) now[0], 0, (float) now[2]).normalize();
			Quaternionf q = frame(along, out);

			double[] guard = {p.x + now[0] - along.x * length * 0.45, cy + now[1] - along.y * length * 0.45, p.z + now[2] - along.z * length * 0.45};
			sword(mesh, guard, q, length, k, i, count, energy);
			swordTrail(fx, p, cy, t, i, count, s, length, energy);
		}
	}

	private void sword(Meshes mesh, double[] guard, Quaternionf q, double length, double s, int index, int count, boolean energy) {
		double halfWidth = 0.034 * s;
		double ridge = 0.009 * s;
		double hue = index / (double) count;
		java.util.function.DoubleUnaryOperator width = f -> f < 0.78 ? halfWidth * (1 - 0.14 * f)
				: halfWidth * 0.89 * Math.pow(Math.max(0, 1 - (f - 0.78) / 0.22), 0.85);
		java.util.function.DoubleUnaryOperator thick = f -> f < 0.78 ? ridge : ridge * Math.max(0.15, 1 - (f - 0.78) / 0.22);
		if (energy) {
			mesh.material(Meshes.Material.NEON);
			mesh.emission(0.5f);
			mesh.bloom(true, look.alpha() * 1.4f);
			Look.markGlow();
		} else {
			look.apply(mesh, 0.08f);
		}

		for (int fy = -1; fy <= 1; fy += 2) {
			for (int fz = -1; fz <= 1; fz += 2) {
				int sy = fy;
				int sz = fz;
				mesh.surface((u, v, o) -> {
					double w = width.applyAsDouble(u);
					double tk = thick.applyAsDouble(u);
					worn(o, guard, q, u * length, sy * tk * (1 - v), sz * v * w, 0, sy * w, sz * tk);
				}, 12, 1, 0, (u, v) -> {
					int base = energy ? Fx.lerp(look.color(hue + u * 0.3, 1), 0xFFFFFFFF, 0.25) : look.color(hue + 0.1 + u * 0.3, 1);
					return Fx.lerp(base, Fx.withAlpha(0xFFFFFFFF, Fx.alpha(base)), (energy ? 0.75 : 0.3) * v * v);
				});
			}
		}
		for (int fy = -1; fy <= 1; fy += 2) {
			int sy = fy;
			mesh.surface((u, v, o) -> {
				double f = 0.04 + u * 0.62;
				double w = width.applyAsDouble(f) * 0.22;
				double z0 = (v * 2 - 1) * w;
				double tk = thick.applyAsDouble(f) * (1 - Math.abs(z0) / width.applyAsDouble(f)) + 0.0006 * s;
				worn(o, guard, q, f * length, sy * tk, z0, 0, sy, 0);
			}, 8, 1, 0, (u, v) -> {
				int c = look.color(hue + 0.1 + u * 0.2, 1);
				return energy ? Fx.lerp(c, Fx.withAlpha(0xFFFFFFFF, Fx.alpha(c)), 0.5) : Fx.lerp(c, Fx.withAlpha(0xFF000000, Fx.alpha(c)), 0.38);
			});
		}
		look.apply(mesh, 0.1f);
		int fitting = look.color(hue + 0.5, 1);
		double[] at = new double[6];
		worn(at, guard, q, 0, 0, 0, 0, 1, 0);
		mesh.box(at[0], at[1], at[2], q, 0.011 * s, 0.015 * s, 0.085 * s, fitting);
		for (int side = -1; side <= 1; side += 2) {
			worn(at, guard, q, -0.006 * s, 0, side * 0.09 * s, 0, 1, 0);
			mesh.sphere(at[0], at[1], at[2], 0.016 * s, fitting, 10);
		}
		for (int side = -1; side <= 1; side += 2) {
			worn(at, guard, q, 0.002 * s, side * 0.015 * s, 0, 0, 1, 0);
			mesh.bloom(look.neon() || energy, 0.6f);
			mesh.material(Meshes.Material.METAL);
			mesh.emission(0.35f);
			mesh.sphere(at[0], at[1], at[2], 0.01 * s, Fx.withAlpha(index % 2 == 0 ? RUBY : SAPPHIRE, 255 * look.alpha()), 8);
		}
		int n = 10;
		double[] path = new double[n * 3];
		double[] radius = new double[n];
		int[] colours = new int[n];
		for (int i = 0; i < n; i++) {
			worn(at, guard, q, -0.008 * s - i * 0.012 * s, 0, 0, 0, 1, 0);
			System.arraycopy(at, 0, path, i * 3, 3);
			radius[i] = 0.0125 * s * (1 + 0.08 * Math.sin(i * Math.PI / (n - 1)));
			colours[i] = Fx.withAlpha(i % 2 == 0 ? 0xFF3B2A22 : 0xFF241812, 255 * look.alpha());
		}
		mesh.material(Meshes.Material.MATTE);
		mesh.emission(0.02f);
		mesh.bloom(false, 0);
		mesh.tube(path, n, radius, colours, 10);
		look.apply(mesh, 0.1f);
		worn(at, guard, q, -0.13 * s, 0, 0, 0, 1, 0);
		mesh.sphere(at[0], at[1], at[2], 0.021 * s, fitting, 12);
	}
	private void swordTrail(Fx fx, Vec3 p, double cy, double t, int i, int count, double s, double length, boolean energy) {
		int steps = 12;
		double[][] inner = new double[steps][];
		double[][] outer = new double[steps][];
		for (int k = 0; k < steps; k++) {
			double tk = t - k * 0.028 * speed.get();
			double[] now = swordOrbit(tk, i, count, s);
			Vector3f along = swordHeading(tk, i, count, s);
			double gx = p.x + now[0] - along.x * length * 0.45;
			double gy = cy + now[1] - along.y * length * 0.45;
			double gz = p.z + now[2] - along.z * length * 0.45;
			inner[k] = new double[] {gx + along.x * length * 0.35, gy + along.y * length * 0.35, gz + along.z * length * 0.35};
			outer[k] = new double[] {gx + along.x * length, gy + along.y * length, gz + along.z * length};
		}
		fx.additive(true);
		double strength = (energy ? 0.55 : 0.28) * Math.max(0.6, look.bloom());
		double hue = i / (double) count + 0.2;
		for (int k = 0; k < steps - 1; k++) {
			double f0 = 1 - k / (double) (steps - 1);
			double f1 = 1 - (k + 1) / (double) (steps - 1);
			int c0 = look.color(hue, strength * f0 * f0);
			int c1 = look.color(hue, strength * f1 * f1);
			int e0 = Fx.withAlpha(c0, Fx.alpha(c0) * 0.15f);
			int e1 = Fx.withAlpha(c1, Fx.alpha(c1) * 0.15f);
			fx.quad(inner[k][0], inner[k][1], inner[k][2], e0, outer[k][0], outer[k][1], outer[k][2], c0,
					outer[k + 1][0], outer[k + 1][1], outer[k + 1][2], c1, inner[k + 1][0], inner[k + 1][1], inner[k + 1][2], e1);
		}
		if (look.bloom() > 0.2f || energy) {
			fx.glow(outer[0][0], outer[0][1], outer[0][2], 0.16 * s, look.color(hue, 0.3 * Math.max(look.bloom(), energy ? 1 : 0)));
		}
		fx.additive(false);
	}
	private void shield(Fx fx, Player player, Vec3 p, double t, double s) {
		double radius = Math.max(0.65, player.getBbWidth() * 0.85) * s;
		double height = (player.getBbHeight() + 0.25) * s;
		int bottom = look.color(t * 0.03, 0.16);
		int top = look.color(t * 0.03 + 0.45, 0.04);
		fx.dome(p.x, p.y + 0.03, p.z, radius, height, 6, 28, bottom, top);
		fx.ring(p.x, p.y + 0.035, p.z, radius - 0.025 * s, radius + 0.025 * s,
				look.color(0, 0.55), look.color(0.4, 0.08), 40);
	}
}
