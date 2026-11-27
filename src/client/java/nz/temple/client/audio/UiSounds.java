package nz.temple.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import nz.temple.TempleNz;
import nz.temple.client.modules.client.MenuModule;

import java.util.HashMap;
import java.util.Map;

public final class UiSounds {
	private static final long TICK_GAP_NANOS = 28_000_000L;
	private static final Map<String, SoundEvent> EVENTS = new HashMap<>();
	private static long lastTick;
	private static long lastTap;
	private static long lastHover;
	private UiSounds() {
	}
	public static void tap() {
		long now = System.nanoTime();
		if (now - lastTap < 40_000_000L) {
			return;
		}
		lastTap = now;
		play("ui.tap", 0.7f, 0.97f + (float) Math.random() * 0.06f);
	}
	public static void toggle(boolean on) {
		lastTap = System.nanoTime();
		play(on ? "ui.on" : "ui.off", 0.6f, 1f);
	}
	public static void slider(double fraction) {
		long now = System.nanoTime();
		if (now - lastTick < TICK_GAP_NANOS) {
			return;
		}
		lastTick = now;
		play("ui.tick", 0.45f, (float) (0.8 + 0.6 * Math.max(0, Math.min(1, fraction))));
	}
	public static void soft() {
		long now = System.nanoTime();
		if (now - lastTap < 40_000_000L) {
			return;
		}
		lastTap = now;
		play("ui.soft", 0.6f, 0.98f + (float) Math.random() * 0.04f);
	}
	public static void hover() {
		long now = System.nanoTime();
		if (now - lastHover < 25_000_000L) {
			return;
		}
		lastHover = now;
		play("ui.hover", 0.28f, 0.96f + (float) Math.random() * 0.08f);
	}
	public static void panel(boolean open) {
		lastTap = System.nanoTime();
		play(open ? "ui.open" : "ui.close", 0.45f, 1f);
	}
	public static void note(int index) {
		float[] scale = {1f, 9 / 8f, 5 / 4f, 3 / 2f, 5 / 3f};
		int i = Math.floorMod(index, scale.length);
		float octave = index >= scale.length ? 2 : 1;
		play("ui.pluck", 0.4f, scale[i] * octave * 0.6f);
	}
	private static void play(String name, float volume, float pitch) {
		float v = MenuModule.soundVolume();
		if (v <= 0) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.getSoundManager() == null) {
			return;
		}
		SoundEvent event = EVENTS.computeIfAbsent(name, n -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, n)));
		mc.getSoundManager().play(SimpleSoundInstance.forUI(event, Math.max(0.5f, Math.min(2f, pitch)), Math.min(1f, volume * v * 1.6f)));
	}
}
