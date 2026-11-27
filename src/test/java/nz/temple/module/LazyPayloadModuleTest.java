package nz.temple.module;

import com.google.gson.JsonObject;
import nz.temple.event.EventBus;
import nz.temple.event.TickEvent;
import nz.temple.setting.BoolSetting;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LazyPayloadModuleTest {
	private static final class Implementation extends Module {
		private final AtomicInteger ticks;
		final BoolSetting option = add(new BoolSetting("Option", false));

		Implementation(AtomicInteger ticks) {
			super("Lazy test", Category.CLIENT, "Lazy test module");
			this.ticks = ticks;
			on(TickEvent.class, event -> ticks.incrementAndGet());
		}
	}

	@Test
	void constructsAndSubscribesImplementationOnlyWhenEnabled() {
		AtomicInteger constructed = new AtomicInteger();
		AtomicInteger ticks = new AtomicInteger();
		LazyPayloadModule module = new LazyPayloadModule(
				new PayloadModuleDescriptor("Lazy test", Category.CLIENT, "Lazy test module"),
				() -> {
					constructed.incrementAndGet();
					return new Implementation(ticks);
				});
		EventBus bus = new EventBus();
		module.attach(bus);

		bus.post(TickEvent.INSTANCE);
		assertEquals(0, constructed.get());
		assertEquals(0, ticks.get());

		module.setEnabled(true);
		assertEquals(1, constructed.get());
		bus.post(TickEvent.INSTANCE);
		assertEquals(1, ticks.get());

		module.setEnabled(false);
		bus.post(TickEvent.INSTANCE);
		assertEquals(1, ticks.get());
	}

	@Test
	void loadsForItsSettingsWithoutEnabling() {
		AtomicInteger ticks = new AtomicInteger();
		LazyPayloadModule module = new LazyPayloadModule(
				new PayloadModuleDescriptor("Lazy test", Category.CLIENT, "Lazy test module"),
				() -> new Implementation(ticks));
		EventBus bus = new EventBus();
		module.attach(bus);
		assertTrue(module.groups().isEmpty());

		assertTrue(module.load());
		assertFalse(module.isEnabled());
		assertEquals(1, module.groups().size());
		assertNotNull(module.groupTitle(0));
		bus.post(TickEvent.INSTANCE);
		assertEquals(0, ticks.get());
	}

	@Test
	void staysOffWhenTheImplementationIsUnavailable() {
		AtomicInteger failures = new AtomicInteger();
		LazyPayloadModule.onLoadFailure((failed, error) -> failures.incrementAndGet());
		try {
			LazyPayloadModule module = new LazyPayloadModule(
					new PayloadModuleDescriptor("Lazy test", Category.CLIENT, "Lazy test module"),
					() -> {
						throw new IllegalStateException("payload artifact unavailable");
					});
			module.attach(new EventBus());

			module.setEnabled(true);
			assertFalse(module.isEnabled());
			assertFalse(module.load());
			assertEquals(2, failures.get());
		} finally {
			LazyPayloadModule.onLoadFailure((failed, error) -> {
			});
		}
	}

	@Test
	void asyncEnableFetchesOffThreadThenConstructsAndEnables() {
		List<String> order = new ArrayList<>();
		AtomicInteger ticks = new AtomicInteger();
		Executor direct = Runnable::run;
		LazyPayloadModule.asyncLoader(direct, direct);
		try {
			LazyPayloadModule module = new LazyPayloadModule(
					new PayloadModuleDescriptor("Lazy test", Category.CLIENT, "Lazy test module"),
					() -> {
						order.add("construct");
						return new Implementation(ticks);
					},
					() -> order.add("prefetch"));
			EventBus bus = new EventBus();
			module.attach(bus);

			module.setEnabled(true);
			assertEquals(List.of("prefetch", "construct"), order);
			assertTrue(module.isEnabled());
			bus.post(TickEvent.INSTANCE);
			assertEquals(1, ticks.get());
		} finally {
			LazyPayloadModule.asyncLoader(null, null);
		}
	}

	@Test
	void asyncEnableThatFailsToFetchStaysOffAndNeverConstructs() {
		AtomicInteger constructed = new AtomicInteger();
		AtomicInteger failures = new AtomicInteger();
		Executor direct = Runnable::run;
		LazyPayloadModule.asyncLoader(direct, direct);
		LazyPayloadModule.onLoadFailure((failed, error) -> failures.incrementAndGet());
		try {
			LazyPayloadModule module = new LazyPayloadModule(
					new PayloadModuleDescriptor("Lazy test", Category.CLIENT, "Lazy test module"),
					() -> {
						constructed.incrementAndGet();
						return new Implementation(new AtomicInteger());
					},
					() -> {
						throw new IllegalStateException("payload artifact unavailable");
					});
			module.attach(new EventBus());

			module.setEnabled(true);
			assertFalse(module.isEnabled());
			assertEquals(0, constructed.get());
			assertEquals(1, failures.get());
		} finally {
			LazyPayloadModule.onLoadFailure((failed, error) -> {
			});
			LazyPayloadModule.asyncLoader(null, null);
		}
	}

	@Test
	void cancellingDuringAsyncLoadLeavesItOff() {
		AtomicInteger constructed = new AtomicInteger();
		ArrayDeque<Runnable> background = new ArrayDeque<>();
		ArrayDeque<Runnable> foreground = new ArrayDeque<>();
		LazyPayloadModule.asyncLoader(background::add, foreground::add);
		try {
			LazyPayloadModule module = new LazyPayloadModule(
					new PayloadModuleDescriptor("Lazy test", Category.CLIENT, "Lazy test module"),
					() -> {
						constructed.incrementAndGet();
						return new Implementation(new AtomicInteger());
					},
					() -> {
					});
			module.attach(new EventBus());

			module.setEnabled(true);
			module.setEnabled(false);
			background.poll().run();
			foreground.poll().run();

			assertFalse(module.isEnabled());
			assertEquals(0, constructed.get());
		} finally {
			LazyPayloadModule.asyncLoader(null, null);
		}
	}

	@Test
	void retainsDisabledSettingsUntilTheImplementationIsNeeded() {
		AtomicInteger ticks = new AtomicInteger();
		LazyPayloadModule module = new LazyPayloadModule(
				new PayloadModuleDescriptor("Lazy test", Category.CLIENT, "Lazy test module"),
				() -> new Implementation(ticks));
		JsonObject settings = new JsonObject();
		settings.addProperty("Option", true);
		module.restoreSettings(settings);

		assertEquals(true, module.savedSettings().get("Option").getAsBoolean());
		assertNull(module.implementationIfLoaded());

		module.setEnabled(true);
		Implementation implementation = (Implementation) module.implementationIfLoaded();
		assertNotNull(implementation);
		assertTrue(implementation.option.get());
	}
}
