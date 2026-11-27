package nz.temple.client.modules.render;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import nz.temple.client.modules.client.HudModule;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;

public final class Vitals extends Module implements nz.temple.client.render.HudSource {
	private final NumberSetting threshold;
	private final ModeSetting style;
	private final ColorSetting color;
	private final NumberSetting strength;
	private final BoolSetting heartbeat;
	private final BoolSetting burning;
	private final BoolSetting drowning;
	private double phase;
	private long last;
	private boolean beatPlayed;

	public Vitals() {
		super("Vitals", Category.COSMETICS, "Darkens the edges of the screen with a pulse when your health is low.");
		threshold = add(new NumberSetting("Threshold", 8, 1, 20, 1, " hp")
				.describe("Health at which the warning starts. It gets stronger as your health drops."));
		style = add(new ModeSetting("Style", List.of("Pulse", "Steady", "Corners"), "Pulse")
				.describe("Pulse beats like a heartbeat. Steady shows a constant vignette. Corners only darkens the corners."));
		color = add(new ColorSetting("Color", 0xFFFF3B30).describe("Warning colour."));
		strength = add(new NumberSetting("Strength", 70, 10, 100, 5, "%").describe("How far in from the edges the effect reaches."));
		group();
		heartbeat = add(new BoolSetting("Heartbeat", false)
				.describe("Plays a heartbeat sound with each pulse.")
				.visibleWhen(() -> style.is("Pulse")));
		burning = add(new BoolSetting("Warn while burning", true).describe("Also warns while you are on fire."));
		drowning = add(new BoolSetting("Warn while drowning", true).describe("Also warns while you are running out of air."));
	}
	private float severity(LocalPlayer player) {
		float health = player.getHealth() + player.getAbsorptionAmount();
		float limit = threshold.getFloat();
		float danger = health >= limit ? 0 : 1 - health / limit;
		if (burning.get() && player.isOnFire()) {
			danger = Math.max(danger, 0.45f);
		}
		if (drowning.get() && player.getAirSupply() < 80) {
			danger = Math.max(danger, 0.35f + 0.4f * (1 - Math.max(0, player.getAirSupply()) / 80f));
		}
		return Math.max(0, Math.min(1, danger));
	}
	public void render(GuiGraphicsExtractor g, DeltaTracker delta, nz.temple.client.render.Projection projection) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (!isEnabled() || player == null || player.isSpectator() || mc.gui.screen() != null) {
			return;
		}
		float danger = severity(player);
		if (danger <= 0.01f) {
			phase = 0;
			return;
		}
		long now = System.nanoTime();
		float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		float beat = 1;
		if (style.is("Pulse")) {
			phase += dt * (1.1 + danger * 2.2);
			double cycle = phase % 1;
			beat = (float) (Math.exp(-14 * cycle) + 0.6 * Math.exp(-14 * Math.abs(cycle - 0.22)));
			beat = 0.35f + 0.9f * Math.min(1, beat);
			if (heartbeat.get()) {
				if (cycle < 0.06 && !beatPlayed) {
					mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
							SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.7f + 0.5f * danger, 0.18f + 0.22f * danger));
					beatPlayed = true;
				} else if (cycle > 0.3) {
					beatPlayed = false;
				}
			}
		}
		int gw = g.guiWidth();
		int gh = g.guiHeight();
		float power = danger * strength.getFloat() / 100f * beat;
		int rgb = color.get() & 0xFFFFFF;
		if (style.is("Corners")) {
			int span = (int) (Math.min(gw, gh) * (0.14f + 0.16f * power));
			int a = alpha(power * 0.85f);
			hgrad(g, 0, 0, span, gh, a << 24 | rgb, rgb);
			hgrad(g, gw - span, 0, gw, gh, rgb, a << 24 | rgb);
			return;
		}
		int vertical = (int) (gh * (0.1f + 0.28f * power));
		int horizontal = (int) (gw * (0.06f + 0.2f * power));
		int edge = alpha(power * 0.9f) << 24 | rgb;
		int clear = rgb;
		g.fillGradient(0, 0, gw, vertical, edge, clear);
		g.fillGradient(0, gh - vertical, gw, gh, clear, edge);
		hgrad(g, 0, 0, horizontal, gh, edge, clear);
		hgrad(g, gw - horizontal, 0, gw, gh, clear, edge);
		if (power > 0.55f) {
			g.fill(0, gh - 1, gw, gh, alpha((power - 0.55f) * 1.2f) << 24 | HudModule.GOLD & 0xFFFFFF);
		}
	}
	static void hgrad(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int left, int right) {
		int steps = Math.max(1, Math.min(48, (x1 - x0) / 3));
		for (int i = 0; i < steps; i++) {
			int sx0 = x0 + (x1 - x0) * i / steps;
			int sx1 = x0 + (x1 - x0) * (i + 1) / steps;
			if (sx1 > sx0) {
				g.fill(sx0, y0, sx1, y1, blend(left, right, (i + 0.5f) / steps));
			}
		}
	}
	private static int blend(int from, int to, float t) {
		int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
		int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
		int gg = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
		int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
		return a << 24 | r << 16 | gg << 8 | b;
	}
	private static int alpha(float v) {
		return Math.max(0, Math.min(255, Math.round(v * 255)));
	}
}
