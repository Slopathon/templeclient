package nz.temple.client.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Meshes;
import nz.temple.client.render.Theme;
import nz.temple.client.util.FilePicker;
import nz.temple.client.util.GifFrames;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.ActionSetting;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.FileSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Cape extends Module {
	public static final float ASPECT = 16f / 10f;
	private static final int MAX_COLUMNS = 40;
	private static final double HALF_THICK = 0.014;
	private static volatile Cape instance;
	private final ModeSetting source;
	private final ModeSetting style;
	private final ModeSetting hem;
	private final FileSetting image;
	private final NumberSetting gifSpeed;
	private final NumberSetting cropX;
	private final NumberSetting cropY;
	private final NumberSetting cropW;
	private final BoolSetting shimmer;
	private final ModeSetting clasp;
	private final NumberSetting flow;
	private final NumberSetting ripple;
	private final NumberSetting wind;
	private final NumberSetting length;
	private final NumberSetting width;
	private final ModeSetting who;
	private final BoolSetting hideWithElytra;
	private final BoolSetting hideVanilla;
	private final Look look;
	private final Map<UUID, CapeCloth> cloths = new HashMap<>();
	private volatile Set<Integer> wearing = Set.of();
	private long lastNanos = System.nanoTime();
	private double frameDt;

	private volatile GifFrames.Animation animation;
	private Path loadedPath;
	private long loadedStamp;
	private long lastCheck;
	private volatile boolean loading;
	private int[][] frameTexels;
	private int columns;
	private int rows;
	private String sampledFor = "";
	public Cape() {
		super("Cape", Category.COSMETICS, "A cloth cape that moves with you, in built-in styles or your own image. Animated GIFs are "
				+ "supported, and the cape editor lets you frame the image.");
		group("Look");
		source = add(new ModeSetting("Cape", List.of("Colours", "Image"), "Colours")
				.describe("Colours uses a built-in style. Image uses a picture you choose."));
		style = add(new ModeSetting("Style", List.of("Trimmed", "Royal", "Emblem", "Stripes", "Gradient", "Galaxy", "Flame", "Aurora", "Plain"),
				"Trimmed")
				.describe("Trimmed has a bright border. Royal is crimson with gold trim and an ermine collar. Emblem is dark with a diamond "
						+ "crest. Stripes has vertical bands. Gradient shades from top to bottom. Galaxy shows a moving nebula with stars. "
						+ "Flame shows fire rising from the hem. Aurora shows moving bands of light. Plain is one colour.")
				.visibleWhen(() -> source.is("Colours")));
		hem = add(new ModeSetting("Hem", List.of("Straight", "Rounded", "Pointed", "Swallowtail", "Tattered"), "Straight")
				.describe("Shape of the bottom edge. Swallowtail has two tails with a notch between them. Tattered is torn into uneven "
						+ "strips."));
		image = add(new FileSetting("Cape image", List.of("png", "jpg", "jpeg", "bmp", "gif"))
				.describe("The image on your cape. Pixel-art capes (such as 10x16 or 64x32 cape files) stay sharp, and animated GIFs play.")
				.visibleWhen(() -> source.is("Image")));
		add(new ActionSetting("Cape editor", "Open editor", () -> Minecraft.getInstance().execute(() -> {
			var mc = Minecraft.getInstance();
			mc.gui.setScreen(new nz.temple.client.gui.CapeEditorScreen(mc.gui.screen(), this));
		})).describe("Choose the part of the image the cape shows. Drag to move it; scroll or drag the corner to resize.")
				.visibleWhen(() -> source.is("Image")));
		gifSpeed = add(new NumberSetting("GIF speed", 100, 25, 400, 25, "%")
				.describe("Playback speed for animated GIFs.")
				.visibleWhen(() -> source.is("Image")));
		cropX = add(new NumberSetting("Frame X", 0, 0, 1, 0.0001, "").internal());
		cropY = add(new NumberSetting("Frame Y", 0, 0, 1, 0.0001, "").internal());
		cropW = add(new NumberSetting("Frame width", 1, 0.01, 1, 0.0001, "").internal());
		shimmer = add(new BoolSetting("Shimmer", false).describe("A band of light passes across the cape every few seconds."));
		clasp = add(new ModeSetting("Clasp", List.of("Studs", "Chain", "None"), "Studs")
				.describe("Studs puts a jewelled stud on each shoulder. Chain adds cords over the shoulders to brooches joined by a chain "
						+ "across the chest."));
		group("Motion");
		flow = add(new NumberSetting("Flow", 100, 0, 200, 5, "%")
				.describe("How much the cape lifts and trails as you move. 0 lets it hang straight."));
		ripple = add(new NumberSetting("Ripple", 100, 0, 250, 5, "%").describe("How much the cloth ripples."));
		wind = add(new NumberSetting("Wind", 40, 0, 200, 5, "%").describe("A light breeze that moves the cape while you stand still."));
		length = add(new NumberSetting("Length", 1, 0.6, 1.5, 0.05, "x").describe("How long the cape is."));
		width = add(new NumberSetting("Width", 1, 0.7, 1.4, 0.05, "x").describe("How wide the cape is."));
		who = add(new ModeSetting("Who", List.of("Self", "Players", "Both"), "Self")
				.describe("Who wears the cape. Other players always get the coloured version; your image is only shown on you."));
		hideWithElytra = add(new BoolSetting("Hide with elytra", true).describe("Removes the cape while you wear elytra, as Minecraft does."));
		hideVanilla = add(new BoolSetting("Hide Minecraft cape", true)
				.describe("Hides Minecraft capes (Minecon, Migrator and so on) on anyone wearing this cape, so they do not overlap."));
		group("Colours");
		look = new Look(s -> add(s), "Matte");
		instance = this;
	}
	public FileSetting imageSetting() {
		return image;
	}
	public double[] frame() {
		return new double[] {cropX.get(), cropY.get(), cropW.get()};
	}
	public void setFrame(double x, double y, double w) {
		cropX.set(x);
		cropY.set(y);
		cropW.set(w);
	}

	public void fitFrame(BufferedImage img) {
		double maxW = Math.min(1, img.getHeight() / (img.getWidth() * (double) ASPECT));
		double w = Math.max(2.0 / img.getWidth(), Math.min(maxW, cropW.get()));
		double h = w * img.getWidth() * ASPECT / img.getHeight();
		double x = Math.max(0, Math.min(1 - w, cropX.get()));
		double y = Math.max(0, Math.min(Math.max(0, 1 - h), cropY.get()));
		if (w != cropW.get() && cropW.get() > maxW) {
			x = (1 - w) / 2;
			y = 0;
		}
		if (w != cropW.get() || x != cropX.get() || y != cropY.get()) {
			setFrame(x, y, w);
		}
	}
	public BufferedImage picture() {
		pollPicture();
		GifFrames.Animation a = animation;
		return a == null ? null : a.first();
	}
	public static boolean hidesVanillaCape(int entityId) {
		Cape cape = instance;
		return cape != null && cape.isEnabled() && cape.hideVanilla.get() && cape.wearing.contains(entityId);
	}
	public void emit(Fx fx, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null || mc.player == null) {
			wearing = Set.of();
			return;
		}
		long now = System.nanoTime();
		frameDt = Math.min(0.1, (now - lastNanos) / 1e9);
		lastNanos = now;
		boolean seesSelf = !mc.options.getCameraType().isFirstPerson() || nz.temple.client.util.DetachedCamera.active();
		Set<Integer> worn = new HashSet<>();
		for (Player player : mc.level.players()) {
			boolean self = player == mc.player;
			if (nz.temple.client.util.Wearer.mine(player) ? who.is("Players") : who.is("Self")) {
				continue;
			}
			if (!player.isAlive() || player.isInvisible() || player.isSpectator() || player.distanceToSqr(mc.player) > 96 * 96) {
				continue;
			}
			ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
			if (hideWithElytra.get() && chest.has(DataComponents.GLIDER)) {
				continue;
			}
			worn.add(player.getId());
			CapeCloth cloth = cloths.computeIfAbsent(player.getUUID(), id -> new CapeCloth((id.getLeastSignificantBits() & 0xFFFF) / 997.0));
			boolean armour = !chest.isEmpty() && !chest.has(DataComponents.GLIDER);
			simulate(cloth, player, partial, armour);
			if (self && !seesSelf) {
				continue;
			}
			draw(fx, cloth, nz.temple.client.util.Wearer.mine(player) && source.is("Image"));
		}
		wearing = Set.copyOf(worn);
		cloths.keySet().removeIf(id -> mc.level.getPlayerByUUID(id) == null);
	}
	private record Pose(CapeCloth.Frame frame, double shoulderY, double back, double capeW) {
	}
	private final Map<CapeCloth, Pose> poses = new HashMap<>();
	private void simulate(CapeCloth cloth, Player player, float partial, boolean armour) {
		Vec3 p = player.getPosition(partial);
		float yaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		double[] back = {Mth.sin(yaw), 0, -Mth.cos(yaw)};
		double[] right = {-Mth.cos(yaw), 0, -Mth.sin(yaw)};
		double[] up = {0, 1, 0};
		double ox = p.x, oy = p.y, oz = p.z;
		org.joml.Matrix4d t = Emotes.auraTransform(player, partial);
		org.joml.Matrix4d lying = lying(player, partial);
		if (lying != null) {
			t = t == null ? lying : new org.joml.Matrix4d(t).mul(lying);
		}
		if (t != null) {
			org.joml.Vector3d o = t.transformPosition(new org.joml.Vector3d(ox, oy, oz));
			ox = o.x;
			oy = o.y;
			oz = o.z;
			back = dir(t, back);
			right = dir(t, right);
			up = dir(t, up);
		}
		CapeCloth.Frame frame = new CapeCloth.Frame(ox, oy, oz, right, up, back);
		double scale = player.getScale();
		boolean crouch = player.isCrouching();
		double shoulderY = (crouch ? 1.2 : 1.39) * scale;
		double backDepth = (0.125 + (armour ? 0.06 : 0)) * scale;
		double capeW = 0.58 * width.get() * scale;
		double capeL = 0.95 * length.get() * scale;
		double[] anchors = new double[CapeCloth.COLS * 3];
		for (int c = 0; c < CapeCloth.COLS; c++) {
			double lx = ((double) c / (CapeCloth.COLS - 1) - 0.5) * capeW * 0.94;
			double[] a = frame.at(lx, shoulderY, backDepth + 0.012);
			System.arraycopy(a, 0, anchors, c * 3, 3);
		}
		double pos = player.walkAnimation.position(partial);
		double speed = Math.min(1, player.walkAnimation.speed(partial));
		double legRight = Math.sin(Math.cos(pos * 0.6662) * 1.4 * speed);
		double legLeft = Math.sin(Math.cos(pos * 0.6662 + Math.PI) * 1.4 * speed);
		double floor = player.onGround() ? p.y + 0.03 : -1e9;
		CapeCloth.Body body = new CapeCloth.Body(shoulderY, (crouch ? 0.62 : 0.74) * scale, backDepth, crouch ? 0.55 : 0,
				crouch ? 0.22 * scale : 0, legLeft, legRight, floor);
		double k = flow.get() / 100;
		double clock = Theme.time();
		double w = wind.get() / 100 * 1.6;
		double windAngle = clock * 0.07 + Math.sin(clock * 0.31) * 0.6;
		double gusting = 0.65 + 0.35 * Math.sin(clock * 0.9) * Math.sin(clock * 0.37 + 1.3);
		double[] air = {Math.cos(windAngle) * w * gusting, 0, Math.sin(windAngle) * w * gusting};
		cloth.update(frameDt, frame, body, anchors, capeW, capeL, air, 0.9 * k, ripple.get() / 100 * (0.4 + w * 0.5));
		poses.put(cloth, new Pose(frame, shoulderY, backDepth, capeW));
	}
	private static org.joml.Matrix4d lying(Player player, float partial) {
		float swim = player.getSwimAmount(partial);
		if (swim <= 0 || player.isFallFlying()) {
			return null;
		}
		float pitch = Mth.lerp(partial, player.xRotO, player.getXRot());
		float tilt = Mth.lerp(swim, 0, player.isInWater() ? -90 - pitch : -90);
		Vec3 feet = player.getPosition(partial);
		float yaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
		double turn = Math.toRadians(180 - yaw);
		double scale = player.getScale();
		org.joml.Matrix4d m = new org.joml.Matrix4d().translate(feet.x, feet.y, feet.z).rotateY(turn).rotateX(Math.toRadians(tilt));
		if (player.isVisuallySwimming()) {
			m.translate(0, -scale, 0.3 * scale);
		}
		return m.rotateY(-turn).translate(-feet.x, -feet.y, -feet.z);
	}
	private static double[] dir(org.joml.Matrix4d t, double[] v) {
		org.joml.Vector3d d = t.transformDirection(new org.joml.Vector3d(v[0], v[1], v[2])).normalize();
		return new double[] {d.x, d.y, d.z};
	}
	private void draw(Fx fx, CapeCloth cloth, boolean usePicture) {
		Meshes mesh = fx.mesh();
		Pose pose = poses.get(cloth);
		double time = Theme.time();
		if (usePicture && samplePicture()) {
			mesh.material(Meshes.Material.MATTE);
			mesh.emission(0.08f);
			mesh.bloom(false, 0);
			GifFrames.Animation a = animation;
			int frame = a == null ? 0 : Math.min(frameTexels.length - 1, a.frameAt((long) (time * 10 * gifSpeed.get())));
			int[] texels = frameTexels[frame];
			drawPicture(mesh, cloth, texels, time);
		} else {
			look.apply(mesh, 0.05f);
			drawColours(fx, mesh, cloth, time);
		}
		if (pose != null) {
			drawClasp(mesh, pose);
		}
		poses.remove(cloth);
	}
	private boolean crisp(String s) {
		return s.equals("Trimmed") || s.equals("Royal") || s.equals("Emblem") || s.equals("Stripes");
	}
	private static double[] knots(int even, double... breaks) {
		List<Double> k = new ArrayList<>();
		for (int i = 0; i <= even; i++) {
			k.add((double) i / even);
		}
		for (double b : breaks) {
			k.add(b);
		}
		k.sort(Double::compare);
		List<Double> out = new ArrayList<>();
		for (double d : k) {
			if (out.isEmpty() || d - out.get(out.size() - 1) > 0.004) {
				out.add(d);
			} else if (d == 1.0 || d == 0.0) {
				out.set(out.size() - 1, d);
			}
		}
		double[] r = new double[out.size()];
		for (int i = 0; i < r.length; i++) {
			r[i] = out.get(i);
		}
		return r;
	}
	private String gridStyle;
	private double[] gridUs;
	private double[] gridVs;
	private double[][] gridOuter = new double[0][];
	private double[][] gridInner = new double[0][];
	private void drawColours(Fx fx, Meshes mesh, CapeCloth cloth, double time) {
		String s = style.get();
		if (!s.equals(gridStyle)) {
			gridStyle = s;
			gridUs = switch (s) {
				case "Trimmed", "Emblem" -> knots(14, 0.08, 0.92);
				case "Royal" -> knots(14, 0.06, 0.94);
				case "Stripes" -> knots(14, 1 / 7.0, 2 / 7.0, 3 / 7.0, 4 / 7.0, 5 / 7.0, 6 / 7.0);
				default -> knots(16);
			};
			gridVs = switch (s) {
				case "Trimmed", "Emblem" -> knots(24, 0.92);
				case "Royal" -> knots(24, 0.16, 0.95);
				default -> knots(26);
			};
		}
		double[] us = gridUs;
		double[] vs = gridVs;
		boolean crisp = crisp(s);
		int nu = us.length, nv = vs.length;
		if (gridOuter.length < nu * nv) {
			gridOuter = new double[nu * nv][6];
			gridInner = new double[nu * nv][6];
		}
		double[][] outer = gridOuter;
		double[][] inner = gridInner;
		for (int j = 0; j < nv; j++) {
			for (int i = 0; i < nu; i++) {
				cloth.samplePair(us[i], vs[j] * hem(us[i]), HALF_THICK, outer[j * nu + i], inner[j * nu + i]);
			}
		}
		float base = emissionFor(s);
		for (int j = 0; j < nv - 1; j++) {
			for (int i = 0; i < nu - 1; i++) {
				int a = j * nu + i;
				int b = a + 1;
				int c = a + nu + 1;
				int d = a + nu;
				int ca, cb, cc, cd;
				if (crisp) {
					int cell = colour(s, (us[i] + us[i + 1]) / 2, (vs[j] + vs[j + 1]) / 2, time);
					ca = cb = cc = cd = cell;
				} else {
					ca = colour(s, us[i], vs[j], time);
					cb = colour(s, us[i + 1], vs[j], time);
					cc = colour(s, us[i + 1], vs[j + 1], time);
					cd = colour(s, us[i], vs[j + 1], time);
				}
				mesh.emission(base + (crisp && trim(s, (us[i] + us[i + 1]) / 2, (vs[j] + vs[j + 1]) / 2) ? 0.1f : 0f));
				mesh.quad(outer[a], outer[b], outer[c], outer[d], glint(ca, us[i], vs[j], time), glint(cb, us[i + 1], vs[j], time),
						glint(cc, us[i + 1], vs[j + 1], time), glint(cd, us[i], vs[j + 1], time));
				mesh.emission(base * 0.4f);
				mesh.quad(inner[d], inner[c], inner[b], inner[a], lining(cd), lining(cc), lining(cb), lining(ca));
			}
		}
		int edge = darker(crisp ? colour(s, 0.02, 0.5, time) : colour(s, 0, 0.5, time), 0.25);
		mesh.emission(base * 0.5f);
		drawEdges(mesh, outer, inner, nu, nv, edge);
		switch (s) {
			case "Emblem" -> drawEmblem(mesh, cloth);
			case "Royal" -> drawErmine(mesh, cloth);
			case "Galaxy" -> drawStars(fx, cloth, time);
			case "Flame" -> drawEmbers(fx, cloth, time);
			default -> {
			}
		}
	}
	private float emissionFor(String s) {
		return switch (s) {
			case "Galaxy" -> 0.3f;
			case "Flame" -> 0.55f;
			case "Aurora" -> 0.45f;
			default -> 0.04f;
		};
	}
	private boolean trim(String s, double u, double v) {
		return switch (s) {
			case "Trimmed", "Emblem" -> u < 0.08 || u > 0.92 || v > 0.92;
			case "Royal" -> u < 0.06 || u > 0.94 || v > 0.95;
			default -> false;
		};
	}
	private int colour(String s, double u, double v, double time) {
		int trim = look.color(0.6);
		return switch (s) {
			case "Gradient" -> look.color(v * 0.9);
			case "Plain" -> look.color(0.15);
			case "Emblem" -> trim(s, u, v) ? trim : darker(look.color(0.1), 0.45);
			case "Royal" -> {
				if (v < 0.16) {
					yield 0xFFF4F1EA;
				}
				yield trim(s, u, v) ? Fx.lerp(trim, 0xFFFFE3A1, 0.25) : Fx.lerp(0xFF6A0F1E, look.color(0.05), 0.12 + v * 0.05);
			}
			case "Stripes" -> ((int) (u * 7) % 2 == 0) ? look.color(0.1) : darker(look.color(0.6), 0.35);
			case "Galaxy" -> {
				double n1 = noise(u * 3.1 + time * 0.05, v * 2.3 - time * 0.03);
				double n2 = noise(u * 5.7 - time * 0.04 + 9, v * 4.1 + 3);
				int deep = 0xFF04050C;
				int c1 = darker(look.color(0.0), 0.35);
				int c2 = darker(look.color(0.65), 0.3);
				int c = Fx.lerp(deep, c1, smooth(0.35, 0.85, n1) * 0.8);
				yield Fx.lerp(c, c2, smooth(0.45, 0.9, n2) * 0.55 * (1 - v * 0.4));
			}
			case "Flame" -> {
				double tongue = noise(u * 5.5, v * 2.6 - time * 2.4) * 0.6 + noise(u * 11, v * 5 - time * 3.7) * 0.4;
				double heat = Math.max(0, Math.min(1, (v - 0.3) / 0.7 + (tongue - 0.5) * 0.7));
				heat = Math.pow(heat, 1.25);
				int c = Fx.lerp(0xFF0C0605, darker(look.color(0.0), 0.35), smooth(0.0, 0.45, heat));
				c = Fx.lerp(c, look.color(0.5), smooth(0.35, 0.8, heat));
				yield Fx.lerp(c, 0xFFFFF4D6, smooth(0.78, 1.0, heat) * 0.75);
			}
			case "Aurora" -> {
				double curtain = 0;
				for (int k = 0; k < 3; k++) {
					double x = u * (2.2 + k * 1.3) + Math.sin(v * 3 + time * (0.4 + k * 0.17) + k * 2) * 0.35 + time * 0.08 * (k + 1);
					curtain += Math.pow(0.5 + 0.5 * Math.sin(x * Math.PI * 2), 6) * (0.6 - k * 0.15);
				}
				double fadeTop = smooth(0.0, 0.5, v);
				int glow = Fx.lerp(look.color(0.15), look.color(0.75), 0.5 + 0.5 * Math.sin(u * 4 + time * 0.3));
				yield Fx.lerp(0xFF060912, glow, Math.min(1, curtain * fadeTop * 1.4));
			}
			default -> trim(s, u, v) ? trim : look.color(0.1 + v * 0.12);
		};
	}
	private int glint(int argb, double u, double v, double time) {
		if (!shimmer.get()) {
			return argb;
		}
		double sweep = (time * 0.42) % 2.6 - 0.5;
		double d = (u * 0.7 + v) - sweep * 1.7;
		double band = Math.exp(-d * d / 0.012) + 0.35 * Math.exp(-(d + 0.18) * (d + 0.18) / 0.004);
		return Fx.withAlpha(Fx.lerp(argb, Fx.lerp(0xFFFFFFFF, look.color(0.5), 0.35), Math.min(0.75, band * 0.7)), Fx.alpha(argb));
	}
	private static int lining(int argb) {
		return darker(argb, 0.45);
	}
	private double hem(double u) {
		double x = Math.abs(u * 2 - 1);
		return switch (hem.get()) {
			case "Rounded" -> 1 - 0.1 * x * x;
			case "Pointed" -> 1 - 0.2 * x;
			case "Swallowtail" -> 1 - 0.22 * Math.max(0, 1 - x * 1.6);
			case "Tattered" -> {

				double f = u * 9;
				int i = (int) Math.floor(f);
				double a = hash(i), b = hash(i + 1);
				double k = f - i;
				double tear = k < 0.75 ? a : a + (b - a) * ((k - 0.75) / 0.25);
				yield 1 - 0.16 * tear;
			}
			default -> 1;
		};
	}
	private static void drawEdges(Meshes mesh, double[][] outer, double[][] inner, int nu, int nv, int colour) {
		for (int j = 0; j < nv - 1; j++) {
			edge(mesh, outer, inner, j * nu, (j + 1) * nu, j * nu + 1, colour);
			edge(mesh, outer, inner, (j + 1) * nu + nu - 1, j * nu + nu - 1, j * nu + nu - 2, colour);
		}
		for (int i = 0; i < nu - 1; i++) {
			edge(mesh, outer, inner, (nv - 1) * nu + i + 1, (nv - 1) * nu + i, (nv - 2) * nu + i, colour);
			edge(mesh, outer, inner, i, i + 1, nu + i, colour);
		}
	}
	private static void edge(Meshes mesh, double[][] outer, double[][] inner, int a, int b, int inward, int colour) {
		double[] pa = outer[a], pb = outer[b], pi = outer[inward];
		double nx = pa[0] - pi[0], ny = pa[1] - pi[1], nz = pa[2] - pi[2];
		double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len < 1e-9) {
			return;
		}
		nx /= len;
		ny /= len;
		nz /= len;
		double[] oa = {pa[0], pa[1], pa[2], nx, ny, nz};
		double[] ob = {pb[0], pb[1], pb[2], nx, ny, nz};
		double[] ib = {inner[b][0], inner[b][1], inner[b][2], nx, ny, nz};
		double[] ia = {inner[a][0], inner[a][1], inner[a][2], nx, ny, nz};
		mesh.quad(oa, ob, ib, ia, colour, colour, colour, colour);
	}
	@FunctionalInterface
	private interface Shape {
		double[] at(double a, double b);
	}
	private void decal(Meshes mesh, CapeCloth cloth, Shape shape, int na, int nb, int colour, float emission) {
		double[][] grid = new double[(na + 1) * (nb + 1)][];
		for (int j = 0; j <= nb; j++) {
			for (int i = 0; i <= na; i++) {
				double[] uv = shape.at((double) i / na, (double) j / nb);
				grid[j * (na + 1) + i] = cloth.sample(uv[0], uv[1] * hem(uv[0]), HALF_THICK + 0.0035);
			}
		}
		mesh.emission(emission);
		for (int j = 0; j < nb; j++) {
			for (int i = 0; i < na; i++) {
				int a = j * (na + 1) + i;
				mesh.quad(grid[a], grid[a + 1], grid[a + na + 2], grid[a + na + 1], colour, colour, colour, colour);
			}
		}
	}
	private void drawEmblem(Meshes mesh, CapeCloth cloth) {
		double aspect = width.get() / (length.get() * 1.64);
		int trim = look.color(0.6);
		double cu = 0.5, cv = 0.34;
		double[][] corners = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 0}};
		for (int side = 0; side < 4; side++) {
			double[] p0 = corners[side], p1 = corners[side + 1];
			Shape ring = (a, b) -> {
				double r = 0.075 + b * 0.035;
				double x = p0[0] + (p1[0] - p0[0]) * a, y = p0[1] + (p1[1] - p0[1]) * a;
				return new double[] {cu + x * r, cv + y * r * aspect};
			};
			decal(mesh, cloth, ring, 4, 1, trim, 0.15f);
			Shape heart = (a, b) -> {
				double r = b * 0.042;
				double x = p0[0] + (p1[0] - p0[0]) * a, y = p0[1] + (p1[1] - p0[1]) * a;
				return new double[] {cu + x * r, cv + y * r * aspect};
			};
			decal(mesh, cloth, heart, 2, 1, Fx.lerp(trim, 0xFFFFFFFF, 0.2), 0.2f);
		}
	}
	private void drawErmine(Meshes mesh, CapeCloth cloth) {
		double aspect = width.get() / (length.get() * 1.64);
		for (int row = 0; row < 2; row++) {
			for (int k = 0; k < 6; k++) {
				double cu = 0.12 + (k + (row == 1 ? 0.5 : 0)) * 0.15;
				double cv = 0.045 + row * 0.07;
				if (cu > 0.9) {
					continue;
				}
				Shape spot = (a, b) -> {
					double ang = a * Math.PI * 2;

					double r = b * 0.026 * (1 + 0.45 * Math.max(0, Math.sin(ang)));
					return new double[] {cu + Math.cos(ang) * r * 0.8, cv + Math.sin(ang) * r * aspect};
				};
				decal(mesh, cloth, spot, 8, 1, 0xFF15151A, 0.02f);
			}
		}
	}
	private void drawStars(Fx fx, CapeCloth cloth, double time) {
		for (int i = 0; i < 26; i++) {
			double u = 0.06 + hash(i * 3 + 1) * 0.88;
			double v = 0.04 + hash(i * 3 + 2) * 0.88;
			double tw = 0.5 + 0.5 * Math.sin(time * (1.5 + hash(i * 7) * 2.5) + i * 1.7);
			double[] p = cloth.sample(u, v * hem(u), HALF_THICK + 0.012);
			int c = Fx.lerp(0xFFFFFFFF, look.color(hash(i * 5)), 0.3);
			double r = 0.018 + hash(i * 11) * 0.025;
			fx.sparkle(p[0], p[1], p[2], r * (0.6 + tw * 0.6), Fx.withAlpha(c, (float) (255 * look.alpha() * (0.35 + tw * 0.65))), time * 0.6 + i);
		}
	}

	private void drawEmbers(Fx fx, CapeCloth cloth, double time) {
		for (int i = 0; i < 10; i++) {
			double phase = (time * (0.55 + hash(i) * 0.4) + hash(i * 13)) % 1;
			double u = 0.08 + hash(i * 17 + (int) (time * 0.55 + hash(i * 13))) * 0.84;
			double[] p = cloth.sample(u, (0.9 - phase * 0.5) * hem(u), HALF_THICK + 0.02 + phase * 0.08);
			double sway = Math.sin(time * 3 + i) * 0.04 * phase;
			int c = Fx.lerp(0xFFFFF0C0, look.color(0.4), phase);
			fx.glow(p[0] + sway, p[1] + phase * 0.35, p[2] + sway, 0.03 * (1 - phase * 0.6),
					Fx.withAlpha(c, (float) (255 * look.alpha() * (1 - phase))));
		}
	}

	private void drawClasp(Meshes mesh, Pose pose) {
		if (clasp.is("None")) {
			return;
		}
		CapeCloth.Frame f = pose.frame();
		int metal = Fx.lerp(look.color(0.55), 0xFFE9B949, 0.5);
		int gem = look.color(0.0);
		double y = pose.shoulderY();
		double half = pose.capeW() * 0.47;
		mesh.bloom(false, 0);
		for (int sgn = -1; sgn <= 1; sgn += 2) {
			if (clasp.is("Studs")) {
				double[] at = f.at(sgn * half, y - 0.01, pose.back() + 0.02);
				Quaternionf q = facing(f.back());
				mesh.material(Meshes.Material.METAL);
				mesh.emission(0.12f);
				mesh.disc(at[0], at[1], at[2], q, 0.038, 0.014, metal, metal, 18);
				mesh.material(Meshes.Material.GLASS);
				mesh.emission(0.35f);
				double[] g = f.at(sgn * half, y - 0.01, pose.back() + 0.032);
				mesh.sphere(g[0], g[1], g[2], 0.019, gem, 10);
			} else {
				double[] a = f.at(sgn * half, y, pose.back() + 0.01);
				double[] b = f.at(sgn * 0.21, y + 0.03, 0.03);
				double[] c = f.at(sgn * 0.14, y - 0.035, -0.135);
				double[] path = {a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2]};
				mesh.material(Meshes.Material.MATTE);
				mesh.emission(0.05f);
				int cord = darker(look.color(0.6), 0.2);
				mesh.tube(path, 3, new double[] {0.009, 0.009, 0.009}, new int[] {cord, cord, cord}, 12);
				double[] br = f.at(sgn * 0.13, y - 0.05, -0.145);
				mesh.material(Meshes.Material.METAL);
				mesh.emission(0.12f);
				mesh.disc(br[0], br[1], br[2], facing(neg(f.back())), 0.03, 0.012, metal, metal, 18);
				mesh.material(Meshes.Material.GLASS);
				mesh.emission(0.35f);
				double[] g = f.at(sgn * 0.13, y - 0.05, -0.154);
				mesh.sphere(g[0], g[1], g[2], 0.015, gem, 10);
			}
		}
		if (clasp.is("Chain")) {
			int links = 9;
			mesh.material(Meshes.Material.METAL);
			mesh.emission(0.1f);
			double[] l = f.at(-0.11, y - 0.05, -0.15);
			double[] r = f.at(0.11, y - 0.05, -0.15);
			double[] across = {r[0] - l[0], r[1] - l[1], r[2] - l[2]};
			double span = Math.sqrt(across[0] * across[0] + across[1] * across[1] + across[2] * across[2]);
			double[] d = {across[0] / span, across[1] / span, across[2] / span};
			double[] front = neg(f.back());
			for (int i = 0; i < links; i++) {
				double t = (i + 0.5) / links;
				double sag = 4 * t * (1 - t) * 0.06;
				double x = l[0] + across[0] * t - f.up()[0] * sag;
				double yy = l[1] + across[1] * t - f.up()[1] * sag;
				double z = l[2] + across[2] * t - f.up()[2] * sag;
				double[] n = i % 2 == 0 ? front : cross(d, front);
				mesh.torus(x, yy, z, facing(n), 0.0115, 0.0032, metal, 14, null);
			}
		}
	}
	private static Quaternionf facing(double[] n) {
		return new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f((float) n[0], (float) n[1], (float) n[2]).normalize());
	}
	private static double[] neg(double[] v) {
		return new double[] {-v[0], -v[1], -v[2]};
	}
	private static double[] cross(double[] a, double[] b) {
		return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}

	private void drawPicture(Meshes mesh, CapeCloth cloth, int[] texels, double time) {
		double[][] outer = new double[(columns + 1) * (rows + 1)][];
		double[][] inner = new double[(columns + 1) * (rows + 1)][];
		for (int r = 0; r <= rows; r++) {
			for (int c = 0; c <= columns; c++) {
				outer[r * (columns + 1) + c] = cloth.sample((double) c / columns, (double) r / rows, HALF_THICK);
				inner[r * (columns + 1) + c] = cloth.sample((double) c / columns, (double) r / rows, -HALF_THICK);
			}
		}
		boolean shaped = !hem.is("Straight");
		for (int r = 0; r < rows; r++) {
			for (int c = 0; c < columns; c++) {
				if (shaped && (r + 0.5) / rows > hem((c + 0.5) / columns)) {
					continue;
				}
				int i = r * (columns + 1) + c;
				int front = texels[r * columns + c];
				int back = darker(texels[r * columns + columns - 1 - c], 0.45);
				if ((front >>> 24) != 0) {
					double u0 = (double) c / columns, u1 = (c + 1.0) / columns, v0 = (double) r / rows, v1 = (r + 1.0) / rows;
					mesh.quad(outer[i], outer[i + 1], outer[i + columns + 2], outer[i + columns + 1], glint(front, u0, v0, time),
							glint(front, u1, v0, time), glint(front, u1, v1, time), glint(front, u0, v1, time));
				}
				if ((back >>> 24) != 0) {
					mesh.quad(inner[i + columns + 1], inner[i + columns + 2], inner[i + 1], inner[i], back, back, back, back);
				}
			}
		}
		if (!shaped) {
			int edge = darker(texels[(rows / 2) * columns], 0.3) | 0xFF000000;
			drawEdges(mesh, outer, inner, columns + 1, rows + 1, edge);
		}
	}
	private void pollPicture() {
		Path path = FilePicker.path(image);
		long now = System.currentTimeMillis();
		if (loading || now - lastCheck < 500 && java.util.Objects.equals(path, loadedPath)) {
			return;
		}
		lastCheck = now;
		if (path == null) {
			animation = null;
			loadedPath = null;
			return;
		}
		long stamp;
		try {
			stamp = Files.isRegularFile(path) ? Files.getLastModifiedTime(path).toMillis() : 0;
		} catch (Exception e) {
			return;
		}
		if (path.equals(loadedPath) && stamp == loadedStamp) {
			return;
		}
		loadedPath = path;
		loadedStamp = stamp;
		loading = true;
		Thread loader = new Thread(() -> {
			try {
				animation = GifFrames.read(path.toFile());
			} catch (Throwable e) {
				animation = null;
			} finally {
				sampledFor = "";
				loading = false;
			}
		}, "Temple cape image");
		loader.setDaemon(true);
		loader.start();
	}
	private boolean samplePicture() {
		pollPicture();
		GifFrames.Animation a = animation;
		if (a == null) {
			return false;
		}
		BufferedImage img = a.first();
		String key = System.identityHashCode(a) + ":" + cropX.get() + ":" + cropY.get() + ":" + cropW.get();
		if (key.equals(sampledFor)) {
			return frameTexels != null;
		}
		fitFrame(img);
		key = System.identityHashCode(a) + ":" + cropX.get() + ":" + cropY.get() + ":" + cropW.get();
		double fw = cropW.get() * img.getWidth();
		double fh = fw * ASPECT;
		double fx = cropX.get() * img.getWidth();
		double fy = cropY.get() * img.getHeight();
		int cols = Math.max(2, Math.min(MAX_COLUMNS, (int) Math.round(fw)));
		int rws = Math.max(2, (int) Math.round(cols * ASPECT));
		int[][] all = new int[a.frames().size()][];
		for (int f = 0; f < all.length; f++) {
			BufferedImage frame = a.frames().get(f);
			int[] texels = new int[cols * rws];
			for (int r = 0; r < rws; r++) {
				for (int c = 0; c < cols; c++) {
					int px = (int) Math.floor(fx + (c + 0.5) * fw / cols);
					int py = (int) Math.floor(fy + (r + 0.5) * fh / rws);
					texels[r * cols + c] = px >= 0 && py >= 0 && px < frame.getWidth() && py < frame.getHeight() ? frame.getRGB(px, py) : 0;
				}
			}
			all[f] = texels;
		}
		columns = cols;
		rows = rws;
		frameTexels = all;
		sampledFor = key;
		return true;
	}
	private static int darker(int argb, double amount) {
		return Fx.withAlpha(Fx.lerp(argb, 0xFF0B0B10, amount), Fx.alpha(argb));
	}
	private static double smooth(double a, double b, double x) {
		double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
		return t * t * (3 - 2 * t);
	}
	private static double hash(int n) {
		int h = n * 0x27d4eb2d;
		h ^= h >>> 15;
		h *= 0x165667b1;
		h ^= h >>> 13;
		return (h & 0xFFFFFF) / (double) 0xFFFFFF;
	}
	private static double noise(double x, double y) {
		int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
		double xf = x - xi, yf = y - yi;
		double u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf);
		double a = hash(xi * 157 + yi * 311), b = hash((xi + 1) * 157 + yi * 311);
		double c = hash(xi * 157 + (yi + 1) * 311), d = hash((xi + 1) * 157 + (yi + 1) * 311);
		return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
	}
	@Override
	protected void onDisable() {
		cloths.clear();
		wearing = Set.of();
	}

	public static Cape instance() {
		return instance;
	}
}
