package nz.temple.setting;

import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {
	@Test
	void numberClampsAndSnapsToStep() {
		NumberSetting range = new NumberSetting("Range", 4.5, 3, 6, 0.1, " m");
		range.set(9.0);
		assertEquals(6.0, range.get());
		range.set(4.23);
		assertEquals(4.2, range.get(), 1e-9);
		range.set(-1.0);
		assertEquals(3.0, range.get());
	}

	@Test
	void numberFormatsWithStepDecimalsAndUnit() {
		assertEquals("4.5 m", new NumberSetting("Range", 4.5, 3, 6, 0.1, " m").display());
		assertEquals("100%", new NumberSetting("Cooldown", 100, 0, 100, 1, "%").display());
		assertEquals("6.0 hp", new NumberSetting("Min", 6, 0, 36, 0.5, " hp").display());
	}

	@Test
	void listenersFireOnlyOnRealChange() {
		BoolSetting rotate = new BoolSetting("Rotate", true);
		AtomicInteger calls = new AtomicInteger();
		rotate.addListener(calls::incrementAndGet);
		rotate.set(true);
		rotate.set(false);
		rotate.set(false);
		assertEquals(1, calls.get());
	}

	@Test
	void modeRejectsUnknownOption() {
		ModeSetting targets = new ModeSetting("Targets", List.of("Players", "Mobs", "All"), "Players");
		targets.set("Mobs");
		targets.set("Nope");
		assertEquals("Mobs", targets.get());
		assertEquals(1, targets.index());
	}

	@Test
	void colorRoundTripsThroughJson() {
		ColorSetting color = new ColorSetting("Color", 0xFF5AC8FA);
		ColorSetting copy = new ColorSetting("Color", 0xFFFFFFFF);
		copy.fromJson(color.toJson());
		assertEquals(0xFF5AC8FA, copy.get());
		assertEquals("#FF5AC8FA", color.toJson().getAsString());
	}

	@Test
	void fromJsonIgnoresWrongTypes() {
		NumberSetting n = new NumberSetting("Speed", 2, 0.1, 10, 0.1, "");
		n.fromJson(new JsonPrimitive("fast"));
		assertEquals(2.0, n.get());
		BoolSetting b = new BoolSetting("On", false);
		b.fromJson(new JsonPrimitive(5));
		assertFalse(b.get());
	}

	@Test
	void visibilityFollowsCondition() {
		ModeSetting mode = new ModeSetting("Mode", List.of("Packet", "Jump"), "Packet");
		NumberSetting height = new NumberSetting("Height", 0.4, 0.1, 1, 0.05, "").visibleWhen(() -> mode.is("Jump"));
		assertFalse(height.isVisible());
		mode.set("Jump");
		assertTrue(height.isVisible());
		assertTrue(mode.isVisible());
	}

	@Test
	void keybindNoneIsMinusOne() {
		KeybindSetting bind = new KeybindSetting("Keybind", KeybindSetting.NONE);
		assertFalse(bind.isBound());
		bind.set(82);
		assertTrue(bind.isBound());
		bind.set(-50);
		assertEquals(KeybindSetting.NONE, bind.get());
	}

	@Test
	void openModeKeepsAChoiceNotYetListed() {
		ModeSetting model = ModeSetting.open("Model", List.of("A"), "A");
		model.fromJson(new JsonPrimitive("Downloaded later"));
		assertEquals("Downloaded later", model.get());
		model.setOptions(List.of("A", "Downloaded later"));
		assertEquals(1, model.index());
		model.set("");
		assertEquals("Downloaded later", model.get());
		ModeSetting fixed = new ModeSetting("Fixed", List.of("A", "B"), "A");
		fixed.set("C");
		assertEquals("A", fixed.get());
		assertThrows(IllegalStateException.class, () -> fixed.setOptions(List.of("C")));
	}
}
