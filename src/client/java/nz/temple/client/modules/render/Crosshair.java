package nz.temple.client.modules.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import nz.temple.TempleNz;
import nz.temple.client.render.UserTexture;
import nz.temple.client.util.FilePicker;
import nz.temple.client.util.HitConfirm;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.FileSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

public final class Crosshair extends Module {
	private static final int WHITE = 0xFFFFFFFF;
	private static final List<String> SHAPES = List.of("Cross", "Cross + dot", "Dot", "Circle", "Circle + dot", "T", "Chevron");
	private static Crosshair instance;
	private final ModeSetting style;
	private final FileSetting image;
	private final NumberSetting size;
	private final NumberSetting length;
	private final NumberSetting thickness;
	private final NumberSetting gap;
	private final NumberSetting dotSize;
	private final BoolSetting outline;
	private final NumberSetting outlineWidth;
	private final ModeSetting dynamic;
	private final NumberSetting spread;
	private final BoolSetting chargeRing;
	private final ColorSetting color;
	private final ModeSetting reaction;
	private final ColorSetting flashColor;
	private final NumberSetting reactionTime;
	private final ModeSetting spin;
	private final NumberSetting spinSpeed;
	private final UserTexture picture;
	private long hitAt;
	private long lastFrame;
	private double angle;
	private double turnRate;
	private double turnTarget;
	private double open;
	private final Identifier shapeId = Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "crosshair/shape");
	private DynamicTexture texture;
	private BufferedImage canvas;
	private int texSize;
	private String painted = "";
	public Crosshair() {
		super("Crosshair", Category.COSMETICS, "Replaces the crosshair with a built-in shape or your own image, in any colour. It can widen "
				+ "while you move, show your attack charge, spin, and react to hits.");
		style = add(new ModeSetting("Style", List.of("Default", "Cross", "Cross + dot", "Dot", "Circle", "Circle + dot", "T", "Chevron",
				"Custom image"), "Default")
				.describe("Default is Minecraft's crosshair. Custom image uses a picture you choose (a PNG with a transparent background "
						+ "works best). The rest are built-in shapes you can size below."));
		image = add(new FileSetting("Image", List.of("png", "jpg", "jpeg", "bmp"))
				.describe("The crosshair image. It reloads automatically when the file changes.")
				.visibleWhen(() -> style.is("Custom image")));
		size = add(new NumberSetting("Size", 15, 5, 64, 1, " px")
				.describe("Image size in GUI pixels. Minecraft's crosshair is 15.")
				.visibleWhen(() -> style.is("Custom image")));
		length = add(new NumberSetting("Length", 4, 1, 14, 0.5, " px")
				.describe("Length of each line, or the circle's size.")
				.visibleWhen(() -> shape() && !style.is("Dot")));
		thickness = add(new NumberSetting("Thickness", 1, 0.5, 4, 0.25, " px").describe("Line thickness.")
				.visibleWhen(() -> shape() && !style.is("Dot")));
		gap = add(new NumberSetting("Gap", 2, 0, 12, 0.5, " px").describe("Space between the centre and the lines.")
				.visibleWhen(() -> shape() && !style.is("Dot")));
		dotSize = add(new NumberSetting("Dot size", 1, 0.5, 5, 0.25, " px").describe("Size of the centre dot.")
				.visibleWhen(() -> style.is("Dot") || style.get().endsWith("+ dot")));
		outline = add(new BoolSetting("Outline", true).describe("Adds a thin dark outline so the crosshair stays visible on bright "
				+ "backgrounds.")
				.visibleWhen(this::shape));
		outlineWidth = add(new NumberSetting("Outline width", 0.5, 0.25, 2, 0.25, " px").describe("Outline thickness.")
				.visibleWhen(() -> shape() && outline.get()));
		dynamic = add(new ModeSetting("Dynamic gap", List.of("Off", "Movement", "Attack charge"), "Off")
				.describe("Movement widens the gap while you move or are in the air. Attack charge widens it while your weapon recharges.")
				.visibleWhen(() -> shape() && !style.is("Dot")));
		spread = add(new NumberSetting("Spread", 4, 1, 12, 0.5, " px").describe("How far the gap widens at most.")
				.visibleWhen(() -> shape() && !style.is("Dot") && !dynamic.is("Off")));
		chargeRing = add(new BoolSetting("Charge ring", false)
				.describe("A ring around the crosshair that fills as your attack recharges.").visibleWhen(this::shape));
		color = add(new ColorSetting("Colour", WHITE)
				.describe("Crosshair colour. With Default, white keeps Minecraft's inverted look, which shows on any background."));
		reaction = add(new ModeSetting("On hit", List.of("Off", "Pulse", "Flash"), "Off")
				.describe("Pulse briefly enlarges it when a hit lands. Flash switches to the flash colour and fades back."));
		flashColor = add(new ColorSetting("Flash colour", 0xFFFF4A4A)
				.describe("Colour used by Flash.")
				.visibleWhen(() -> reaction.is("Flash")));
		reactionTime = add(new NumberSetting("Reaction time", 260, 80, 800, 20, " ms")
				.describe("How long the pulse or flash lasts.")
				.visibleWhen(() -> !reaction.is("Off")));
		spin = add(new ModeSetting("Spin", List.of("Off", "Always", "On hit"), "Off")
				.describe("Always turns it continuously. On hit turns it a quarter turn each time a hit lands."));
		spinSpeed = add(new NumberSetting("Spin speed", 180, -720, 720, 15, "°/s")
				.describe("Rotation speed in degrees per second. Negative values turn it the other way.")
				.visibleWhen(() -> spin.is("Always")));
		picture = new UserTexture("crosshair", () -> style.is("Custom image") ? FilePicker.path(image) : null);
		HitConfirm.listen(new HitConfirm.Listener() {
			@Override
			public void hit(LivingEntity target, Vec3 offset) {
				hitAt = System.nanoTime();
				if (spin.is("On hit")) {
					turnTarget += 90;
				}
			}
			@Override
			public void kill(Entity target, int entityId, Vec3 offset) {
			}
		});
		instance = this;
	}
	public static Crosshair active() {
		return instance != null && instance.isEnabled() ? instance : null;
	}
	private boolean shape() {
		return SHAPES.contains(style.get());
	}

	public void draw(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h) {
		long now = System.nanoTime();
		double dt = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1e9);

		float t = (now - hitAt) / 1e6f / reactionTime.getFloat();
		float react = reaction.is("Off") || t >= 1 ? 0 : (1 - t) * (1 - t);
		int argb = color.get();
		if (reaction.is("Flash") && react > 0) {
			argb = mix(argb, flashColor.get(), react);
		}
		float scale = reaction.is("Pulse") && react > 0 ? 1 + 0.6f * (float) Math.sin(Math.min(1, t * 6) * Math.PI / 2) * react : 1;
		float turn = spinAngle(now, dt);
		if (shape()) {
			drawShape(g, x + w / 2f, y + h / 2f, argb, scale, turn, dt);
			return;
		}
		boolean custom = style.is("Custom image") && picture.ready();
		int dw = custom ? (int) Math.round(size.get()) : w;
		int dh = custom ? Math.max(1, Math.round(dw * picture.height() / (float) Math.max(1, picture.width()))) : h;
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x + w / 2f, y + h / 2f);
		if (turn != 0) {
			pose.rotate((float) Math.toRadians(turn));
		}
		pose.scale(scale, scale);
		pose.translate(-w / 2f, -h / 2f);
		if (custom) {
			int iw = picture.width(), ih = picture.height();
			g.blit(RenderPipelines.GUI_TEXTURED, picture.id(), (w - dw) / 2, (h - dh) / 2, 0, 0, dw, dh, iw, ih, iw, ih, argb);
		} else if (argb == WHITE) {
			g.blitSprite(pipeline, sprite, 0, 0, w, h);
		} else {
			g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, 0, 0, w, h, argb);
		}
		pose.popMatrix();
	}
	private void drawShape(GuiGraphicsExtractor g, float cx, float cy, int argb, float scale, float turn, double dt) {
		Minecraft mc = Minecraft.getInstance();
		float gs = (float) mc.getWindow().getGuiScale();
		double want = 0;
		float charge = mc.player == null ? 1 : mc.player.getAttackStrengthScale(0);
		if (mc.player != null) {
			if (dynamic.is("Movement")) {
				Vec3 v = mc.player.getDeltaMovement();
				want = Math.min(1, v.horizontalDistance() * 20 / 5.6) * 0.8 + (mc.player.onGround() ? 0 : 0.5);
			} else if (dynamic.is("Attack charge")) {
				want = 1 - charge;
			}
		}
		want = Math.min(1, want);
		open += (want - open) * (1 - Math.exp(-dt * (want > open ? 18 : 9)));
		double px = gs;
		int t = Math.max(1, (int) Math.round(thickness.get() * px));
		int o = outline.get() ? Math.max(1, (int) Math.round(outlineWidth.get() * px)) : 0;
		double gp = (gap.get() + (dynamic.is("Off") ? 0 : open * spread.get())) * px;
		double len = length.get() * px;
		double dot = Math.max(1, dotSize.get() * px);
		boolean ring = chargeRing.get() && charge < 1;
		double ringR = gp + len + 3 * px;
		int reach = (int) Math.ceil(Math.max(gp + len, ring ? ringR + t : 0) + dot + o + 3);
		int size = reach * 2 + 2;
		double c = reach + (t % 2 == 1 ? 0.5 : 0);
		String key = style.get() + "|" + argb + "|" + t + "|" + o + "|" + Math.round(gp * 4) + "|" + Math.round(len * 4) + "|" + Math.round(dot * 4)
				+ "|" + (ring ? Math.round(charge * 90) : -1) + "|" + size;
		ensure(size);
		if (!key.equals(painted)) {
			painted = key;
			paint(c, argb, t, o, gp, len, dot, ring ? charge : -1, ringR);
		}
		double scx = Math.floor(cx * gs);
		double scy = Math.floor(cy * gs);
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate((float) (scx / gs), (float) (scy / gs));
		if (turn != 0) {
			pose.rotate((float) Math.toRadians(turn));
		}
		pose.scale(scale / gs, scale / gs);
		g.blit(shapeId, -reach, -reach, -reach + texSize, -reach + texSize, 0, 1, 0, 1);
		pose.popMatrix();
	}
	private void ensure(int size) {
		if (texture != null && size <= texSize) {
			return;
		}
		texSize = Math.max(size, 32);
		canvas = new BufferedImage(texSize, texSize, BufferedImage.TYPE_INT_ARGB);
		var manager = Minecraft.getInstance().getTextureManager();
		if (texture != null) {
			manager.release(shapeId);
		}
		texture = new DynamicTexture(() -> "Temple crosshair", texSize, texSize, true);
		manager.register(shapeId, texture);
		painted = "";
	}
	private void paint(double c, int argb, int t, int o, double gp, double len, double dot, float charge, double ringR) {
		Graphics2D g2 = canvas.createGraphics();
		g2.setComposite(AlphaComposite.Clear);
		g2.fillRect(0, 0, texSize, texSize);
		g2.setComposite(AlphaComposite.SrcOver);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		Color fill = new Color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255);
		Color dark = new Color(0, 0, 0, Math.min(255, (int) (((argb >>> 24) & 255) * 0.85)));
		for (int pass = o > 0 ? 0 : 1; pass < 2; pass++) {
			double grow = pass == 0 ? o : 0;
			g2.setColor(pass == 0 ? dark : fill);
			String s = style.get();
			double half = t / 2.0;
			if (s.startsWith("Cross") || s.equals("T")) {
				g2.fill(new Rectangle2D.Double(c + gp - grow, c - half - grow, len + grow * 2, t + grow * 2));
				g2.fill(new Rectangle2D.Double(c - gp - len - grow, c - half - grow, len + grow * 2, t + grow * 2));
				g2.fill(new Rectangle2D.Double(c - half - grow, c + gp - grow, t + grow * 2, len + grow * 2));
				if (!s.equals("T")) {
					g2.fill(new Rectangle2D.Double(c - half - grow, c - gp - len - grow, t + grow * 2, len + grow * 2));
				}
			} else if (s.startsWith("Circle")) {
				double r = Math.max(t, gp + len / 2);
				g2.setStroke(new BasicStroke((float) (t + grow * 2)));
				g2.draw(new Ellipse2D.Double(c - r, c - r, r * 2, r * 2));
			} else if (s.equals("Chevron")) {
				double arm = len + gp * 0.5;
				Path2D v = new Path2D.Double();
				v.moveTo(c - arm, c + gp + arm * 0.8);
				v.lineTo(c, c + gp);
				v.lineTo(c + arm, c + gp + arm * 0.8);
				g2.setStroke(new BasicStroke((float) (t + grow * 2), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2.draw(v);
			}
			if (s.equals("Dot") || s.endsWith("+ dot")) {
				double r = dot / 2 + grow;
				g2.fill(new Ellipse2D.Double(c - r, c - r, r * 2, r * 2));
			}
			if (charge >= 0) {
				g2.setStroke(new BasicStroke((float) (Math.max(1, t * 0.75) + grow * 2), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
				if (pass == 1) {
					g2.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), fill.getAlpha() / 4));
					g2.draw(new Ellipse2D.Double(c - ringR, c - ringR, ringR * 2, ringR * 2));
					g2.setColor(fill);
				}
				g2.draw(new Arc2D.Double(c - ringR, c - ringR, ringR * 2, ringR * 2, 90, -360 * charge, Arc2D.OPEN));
			}
		}
		g2.dispose();
		NativeImage target = texture.getPixels();
		int[] row = new int[texSize];
		for (int y = 0; y < texSize; y++) {
			canvas.getRGB(0, y, texSize, 1, row, 0, texSize);
			for (int x = 0; x < texSize; x++) {
				target.setPixel(x, y, row[x]);
			}
		}
		texture.upload();
	}
	private float spinAngle(long now, double dt) {
		lastFrame = now;
		if (spin.is("Always")) {
			turnRate += (spinSpeed.get() - turnRate) * (1 - Math.exp(-dt * 6));
			angle = (angle + turnRate * dt) % 360;
			turnTarget = angle;
		} else if (spin.is("On hit")) {
			turnRate = 0;
			angle += (turnTarget - angle) * (1 - Math.exp(-dt * 14));
			if (Math.abs(turnTarget - angle) < 0.05) {
				angle = turnTarget = turnTarget % 360;
			}
		} else {
			turnRate = 0;
			double back = Math.IEEEremainder(angle, 90);
			angle -= back * (1 - Math.exp(-dt * 10));
			if (Math.abs(back) < 0.05) {
				angle = 0;
			}
			turnTarget = angle;
		}
		return (float) (Math.abs(angle) < 1e-3 ? 0 : angle);
	}
	private static int mix(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int out = 0;
		for (int shift = 0; shift < 32; shift += 8) {
			int ca = (a >>> shift) & 0xFF;
			int cb = (b >>> shift) & 0xFF;
			out |= Math.round(ca + (cb - ca) * t) << shift;
		}
		return out;
	}
}
