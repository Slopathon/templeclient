package nz.temple.client.modules.movement;

import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;

public final class Timer extends Module {
	private static Timer instance;
	private final ModeSetting mode;
	private final NumberSetting speed;
	private final NumberSetting onTicks;
	private final NumberSetting offTicks;
	private final NumberSetting offSpeed;
	private int phase;
	public Timer() {
		super("Timer", Category.MOVEMENT, "Changes how fast the game ticks on your side, which speeds up or slows down your movement.");
		instance = this;
		mode = add(new ModeSetting("Mode", List.of("Constant", "Pulse"), "Constant")
				.describe("Constant always runs at Speed. Pulse runs at Speed for a few ticks, then slower, so the average stays "
						+ "near normal."));
		speed = add(new NumberSetting("Speed", 1.5, 0.1, 10, 0.05, "x").describe("Tick speed. 1 is normal."));
		onTicks = add(new NumberSetting("Fast ticks", 6, 1, 40, 1, "").describe("Ticks at Speed in each pulse.")
				.visibleWhen(() -> mode.is("Pulse")));
		offTicks = add(new NumberSetting("Slow ticks", 10, 1, 80, 1, "").describe("Ticks at Slow speed after each burst.")
				.visibleWhen(() -> mode.is("Pulse")));
		offSpeed = add(new NumberSetting("Slow speed", 0.7, 0.1, 1, 0.05, "x").describe("Tick speed between bursts.")
				.visibleWhen(() -> mode.is("Pulse")));
		on(TickEvent.class, e -> {
			int cycle = (int) Math.round(onTicks.get() + offTicks.get());
			phase = (phase + 1) % Math.max(1, cycle);
		});
	}
	@Override
	protected void onEnable() {
		phase = 0;
	}
	public static float speed() {
		Timer t = instance;
		if (t == null || !t.isEnabled()) {
			return 1;
		}
		if (t.mode.is("Pulse") && t.phase >= t.onTicks.get()) {
			return t.offSpeed.getFloat();
		}
		return t.speed.getFloat();
	}
}
