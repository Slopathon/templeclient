package nz.temple.setting;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SettingHistoryTest {
	private long now;

	@BeforeEach
	void setUp() {
		now = 1_000_000;
		SettingHistory.useClock(() -> now);
		SettingHistory.clear();
		SettingHistory.start();
	}

	@AfterEach
	void tearDown() {
		SettingHistory.stop();
		SettingHistory.clear();
		SettingHistory.useClock(System::currentTimeMillis);
	}

	@Test
	void undoAndRedoASingleChange() {
		BoolSetting b = new BoolSetting("Flag", false);
		b.set(true);
		assertEquals("Flag", SettingHistory.undo());
		assertFalse(b.get());
		assertEquals("Flag", SettingHistory.redo());
		assertTrue(b.get());
		assertNull(SettingHistory.redo());
	}

	@Test
	void sliderDragMergesIntoOneStep() {
		NumberSetting n = new NumberSetting("Size", 1, 0, 10, 0.1, "");
		for (int i = 2; i <= 8; i++) {
			n.set((double) i);
			now += 50;
		}
		SettingHistory.undo();
		assertEquals(1, n.get(), 1e-9);
		assertNull(SettingHistory.undo());
		SettingHistory.redo();
		assertEquals(8, n.get(), 1e-9);
	}

	@Test
	void separateChangesAreSeparateSteps() {
		NumberSetting n = new NumberSetting("Size", 1, 0, 10, 0.1, "");
		n.set(3.0);
		now += SettingHistory.MERGE_MILLIS + 1;
		n.set(5.0);
		SettingHistory.undo();
		assertEquals(3, n.get(), 1e-9);
		SettingHistory.undo();
		assertEquals(1, n.get(), 1e-9);
	}

	@Test
	void undoingDoesNotRecordItselfAndNewChangeClearsRedo() {
		ModeSetting m = new ModeSetting("Mode", java.util.List.of("A", "B", "C"), "A");
		m.set("B");
		SettingHistory.undo();
		assertEquals("A", m.get());
		m.set("C");
		assertNull(SettingHistory.redo());
		SettingHistory.undo();
		assertEquals("A", m.get());
	}

	@Test
	void notRecordedWhileStopped() {
		SettingHistory.stop();
		BoolSetting b = new BoolSetting("Flag", false);
		b.set(true);
		assertNull(SettingHistory.undo());
	}

	@Test
	void recordsExplicitSteps() {
		boolean[] on = {true};
		SettingHistory.record("mod", "Fly", () -> on[0] = false, () -> on[0] = true);
		assertEquals("Fly", SettingHistory.undo());
		assertFalse(on[0]);
		SettingHistory.redo();
		assertTrue(on[0]);
	}
}
