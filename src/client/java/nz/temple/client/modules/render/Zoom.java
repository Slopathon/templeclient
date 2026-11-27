package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.NumberSetting;

public final class Zoom extends Module {
	private static final double MAX = 100_000;
	private final NumberSetting start;
	private final BoolSetting wheel;
	private final NumberSetting wheelStep;
	private final BoolSetting smooth;
	private final BoolSetting slowMouse;
	private final BoolSetting cinematic;
	private final BoolSetting keepZoom;
	private static Zoom instance;
	private double target = 1;
	private double current = 1;
	private long lastNanos = System.nanoTime();
	private boolean previousSmooth;
	public Zoom() {
		super("Zoom", Category.RENDER, "Zooms your view. Hold or tap the key, then scroll to zoom further.");
		start = add(new NumberSetting("Zoom", 4, 1.5, 50, 0.5, "x").describe("Zoom level when you press the key."));
		wheel = add(new BoolSetting("Scroll to zoom", true).describe("The mouse wheel adjusts zoom while zoomed, with no limit."));
		wheelStep = add(new NumberSetting("Scroll speed", 25, 5, 100, 5, "%").describe("Zoom change per scroll notch.")
				.visibleWhen(wheel::get));
		smooth = add(new BoolSetting("Smooth", true).describe("Smoothly transitions between zoom levels."));
		slowMouse = add(new BoolSetting("Slower mouse", true)
				.describe("Lowers mouse sensitivity as you zoom in, so the view moves the same distance on screen at any zoom level."));
		cinematic = add(new BoolSetting("Cinematic camera", false).describe("Uses Minecraft's smoothed camera while zoomed."));
		keepZoom = add(new BoolSetting("Remember zoom", false)
				.describe("Starts from your last zoom level instead of the Zoom setting."));
		instance = this;
	}
	@Override
	protected void onEnable() {
		if (!keepZoom.get() || target <= 1.0001) {
			target = start.get();
		}
		lastNanos = System.nanoTime();
		Minecraft mc = Minecraft.getInstance();
		if (mc.options != null) {
			previousSmooth = mc.options.smoothCamera;
			if (cinematic.get()) {
				mc.options.smoothCamera = true;
			}
		}
	}
	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options != null && cinematic.get()) {
			mc.options.smoothCamera = previousSmooth;
		}
	}
	private double magnification() {
		long now = System.nanoTime();
		double dt = Math.min(0.1, (now - lastNanos) / 1e9);
		lastNanos = now;
		double want = isEnabled() ? target : 1;
		if (!smooth.get()) {
			current = want;
		} else {
			double a = Math.log(current);
			double b = Math.log(want);
			current = Math.exp(a + (b - a) * (1 - Math.exp(-14 * dt)));
			if (Math.abs(current / want - 1) < 0.002) {
				current = want;
			}
		}
		return current;
	}
	public static float fov(float fov) {
		Zoom z = instance;
		if (z == null || !z.isEnabled() && z.current <= 1.0001) {
			return fov;
		}
		return (float) (fov / z.magnification());
	}
	public static double mouseScale() {
		Zoom z = instance;
		if (z == null || !z.slowMouse.get() || z.current <= 1.0001) {
			return 1;
		}
		return 1 / z.current;
	}
	public static boolean scroll(double amount) {
		Minecraft mc = Minecraft.getInstance();
		Zoom z = instance;
		if (z == null || !z.isEnabled() || !z.wheel.get() || mc.gui.screen() != null || amount == 0) {
			return false;
		}
		double step = 1 + z.wheelStep.get() / 100;
		z.target = Math.max(1, Math.min(MAX, z.target * Math.pow(step, Math.signum(amount))));
		return true;
	}
}
