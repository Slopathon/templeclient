package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.hud.HudTheme;
import nz.temple.client.render.Fx;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;

/** A glowing cone above the head (after x.monolith's China Hat): coloured in a wave that turns around it, with a soft halo. */
public final class ChinaHat extends Module {
	private static final int SEGMENTS = 48;
	private final ModeSetting colors;
	private final ColorSetting color;
	private final NumberSetting opacity;
	private final BoolSetting glow;
	private final NumberSetting radius;
	private final NumberSetting height;
	private final NumberSetting speed;
	private final BoolSetting self;
	private final BoolSetting others;

	public ChinaHat() {
		super("China Hat", Category.RENDER, "A glowing cone above your head, in your theme's colours.");
		colors = add(new ModeSetting("Colors", List.of("Theme", "Astolfo", "Rainbow", "Custom"), "Theme")
				.describe("Theme follows the HUD theme."));
		color = add(new ColorSetting("Color", 0xFF9B6BFF).fixed().visibleWhen(() -> colors.is("Custom")));
		opacity = add(new NumberSetting("Opacity", 70, 10, 100, 5, "%"));
		glow = add(new BoolSetting("Glow", true).describe("A soft halo around the brim."));
		radius = add(new NumberSetting("Radius", 0.62, 0.3, 1.2, 0.02, " m"));
		height = add(new NumberSetting("Height", 0.28, 0.1, 0.7, 0.02, " m"));
		speed = add(new NumberSetting("Speed", 1, 0, 4, 0.1, "x"));
		self = add(new BoolSetting("Self", true).describe("On you, in third person."));
		others = add(new BoolSetting("Others", false).describe("On other players."));
	}

	public void emit(Fx fx, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null || mc.player == null) {
			return;
		}
		for (Player p : mc.level.players()) {
			if (wants(mc, p)) {
				draw(fx, p, partial);
			}
		}
	}

	private boolean wants(Minecraft mc, Player p) {
		if (p.isSpectator() || p.isSleeping() || p.isVisuallyCrawling() || p.isFallFlying() || p.isSwimming() || !p.isAlive()
				|| p.isInvisible()) {
			return false;
		}
		if (p == mc.player) {
			return self.get() && !mc.options.getCameraType().isFirstPerson();
		}
		return others.get();
	}

	private int shade(double angle, double time) {
		float wave = (float) ((Math.sin(angle + time) + 1) * 0.5);
		return switch (colors.get()) {
			case "Astolfo" -> HudTheme.mix(0xFFFF60A5, 0xFF60A5FA, wave);
			case "Rainbow" -> 0xFF000000 | (java.awt.Color.HSBtoRGB((float) (((angle + time) / (Math.PI * 2)) % 1 + 1) % 1, 0.65f, 1) & 0xFFFFFF);
			case "Custom" -> HudTheme.mix(color.get() | 0xFF000000, 0xFFFFFFFF, wave * 0.35f);
			default -> HudTheme.mix(HudTheme.accent(), HudTheme.light(), wave);
		};
	}

	private void draw(Fx fx, Player p, float partial) {
		Vec3 eye = p.getEyePosition(partial);
		double yaw = Math.toRadians(Mth.rotLerp(partial, p.yHeadRotO, p.yHeadRot));
		double pitch = Math.toRadians(Mth.rotLerp(partial, p.xRotO, p.getXRot()) * 0.5f);
		Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 up = new Vec3(0, Math.cos(pitch), 0).add(forward.scale(Math.sin(pitch)));
		double time = System.nanoTime() / 1e9 * speed.get() * 1.5;
		double sway = Math.sin(time * 1.3 + p.getId()) * 0.015;
		Vec3 base = eye.add(0, 0.3 + sway, 0).add(forward.scale(p.isCrouching() ? 0.08 : 0));
		double r = radius.get();
		Vec3 apex = base.add(up.scale(height.get()));
		float alpha = opacity.getFloat() / 100f * 255f;
		Vec3[] rim = new Vec3[SEGMENTS];
		int[] tint = new int[SEGMENTS];
		for (int i = 0; i < SEGMENTS; i++) {
			double angle = Math.PI * 2 * i / SEGMENTS;
			rim[i] = base.add(right.scale(r * Math.cos(angle))).add(forward.scale(r * Math.sin(angle)));
			tint[i] = shade(angle, time);
		}
		for (int i = 0; i < SEGMENTS; i++) {
			int n = (i + 1) % SEGMENTS;
			int top = Fx.withAlpha(HudTheme.mix(tint[i], 0xFF000000, 0.25f), alpha);
			fx.quad(apex.x, apex.y, apex.z, top, rim[i].x, rim[i].y, rim[i].z, Fx.withAlpha(tint[i], alpha),
					rim[n].x, rim[n].y, rim[n].z, Fx.withAlpha(tint[n], alpha), apex.x, apex.y, apex.z, top);
			fx.quad(apex.x, apex.y, apex.z, top, rim[n].x, rim[n].y, rim[n].z, Fx.withAlpha(tint[n], alpha),
					rim[i].x, rim[i].y, rim[i].z, Fx.withAlpha(tint[i], alpha), apex.x, apex.y, apex.z, top);
		}
		if (!glow.get()) {
			return;
		}
		fx.additive(true);
		double outer = r * 1.28;
		Vec3[] halo = new Vec3[SEGMENTS];
		for (int i = 0; i < SEGMENTS; i++) {
			double angle = Math.PI * 2 * i / SEGMENTS;
			halo[i] = base.add(right.scale(outer * Math.cos(angle))).add(forward.scale(outer * Math.sin(angle))).add(up.scale(-0.025));
		}
		for (int i = 0; i < SEGMENTS; i++) {
			int n = (i + 1) % SEGMENTS;
			int inner = Fx.withAlpha(tint[i], 0.34f * alpha);
			int innerNext = Fx.withAlpha(tint[n], 0.34f * alpha);
			fx.quad(rim[i].x, rim[i].y, rim[i].z, inner, halo[i].x, halo[i].y, halo[i].z, tint[i] & 0xFFFFFF,
					halo[n].x, halo[n].y, halo[n].z, tint[n] & 0xFFFFFF, rim[n].x, rim[n].y, rim[n].z, innerNext);
		}
		fx.additive(false);
	}
}
