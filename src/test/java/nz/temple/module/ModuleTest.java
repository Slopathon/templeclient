package nz.temple.module;

import org.junit.jupiter.api.Test;
import nz.temple.event.EventBus;
import nz.temple.event.TickEvent;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.NumberSetting;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ModuleTest {
	static final class Sample extends Module {
		final AtomicInteger ticks = new AtomicInteger();
		final NumberSetting range = add(new NumberSetting("Range", 4.5, 3, 6, 0.1, " m"));
		final BoolSetting rotate;

		Sample() {
			super("Sample", Category.COMBAT, "Test module");
			group();
			rotate = add(new BoolSetting("Rotate", true));
			on(TickEvent.class, e -> ticks.incrementAndGet());
		}
	}

	static final class Always extends Module {
		Always() {
			super("Always", Category.CLIENT, "Always on", true);
		}
	}

	@Test
	void listensOnlyWhileEnabled() {
		EventBus bus = new EventBus();
		Sample m = new Sample();
		m.attach(bus);
		bus.post(TickEvent.INSTANCE);
		assertEquals(0, m.ticks.get());
		m.setEnabled(true);
		bus.post(TickEvent.INSTANCE);
		m.toggle();
		bus.post(TickEvent.INSTANCE);
		assertEquals(1, m.ticks.get());
		assertEquals(0, bus.listenerCount(TickEvent.class));
	}

	@Test
	void settingsAreGroupedInOrder() {
		Sample m = new Sample();
		assertEquals(2, m.groups().size());
		assertSame(m.range, m.groups().get(0).get(0));
		assertSame(m.rotate, m.groups().get(1).get(0));
		assertSame(m.rotate, m.setting("rotate"));
	}

	@Test
	void changeAndToggleListenersFire() {
		Sample m = new Sample();
		AtomicInteger changes = new AtomicInteger();
		AtomicInteger toggles = new AtomicInteger();
		m.addChangeListener(changes::incrementAndGet);
		m.addToggleListener(mod -> toggles.incrementAndGet());
		m.range.set(5.0);
		m.keybind().set(82);
		m.disableOnKeyRelease().set(true);
		m.setEnabled(true);
		assertEquals(4, changes.get());
		assertEquals(1, toggles.get());
	}

	@Test
	void alwaysOnModulesCannotBeDisabled() {
		Always m = new Always();
		m.setEnabled(false);
		assertTrue(m.isEnabled());
	}
}
