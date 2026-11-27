package nz.temple.module;

import org.junit.jupiter.api.Test;
import nz.temple.event.EventBus;
import nz.temple.event.TickEvent;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModuleManagerTest {
	static class Named extends Module {
		Named(String name, Category category) {
			super(name, category, "");
		}
	}

	static final class Crashy extends Module {
		Crashy() {
			super("Crashy", Category.WORLD, "");
			on(TickEvent.class, e -> {
				throw new IllegalStateException("boom");
			});
		}
	}

	private static ModuleManager withAuras() {
		ModuleManager m = new ModuleManager(new EventBus());
		m.register(new Named("Crystal Aura", Category.COMBAT));
		m.register(new Named("Kill Aura", Category.COMBAT));
		m.register(new Named("Anchor Aura", Category.COMBAT));
		m.register(new Named("Speed", Category.MOVEMENT));
		return m;
	}

	@Test
	void searchRanksByMatchPositionThenName() {
		List<String> names = withAuras().search("aura").stream().map(Module::name).toList();
		assertEquals(List.of("Kill Aura", "Anchor Aura", "Crystal Aura"), names);
	}

	@Test
	void searchIsCaseInsensitiveAndEmptyTermReturnsNothing() {
		ModuleManager m = withAuras();
		assertEquals(1, m.search("SPE").size());
		assertTrue(m.search("  ").isEmpty());
	}

	static final class WithSettings extends Module {
		final nz.temple.setting.BoolSetting walls = add(new nz.temple.setting.BoolSetting("Through walls", false));
		final nz.temple.setting.BoolSetting hidden = add(new nz.temple.setting.BoolSetting("Wall hidden", false));

		WithSettings(String name) {
			super(name, Category.COMBAT, "");
			hidden.visibleWhen(() -> false);
		}
	}

	@Test
	void settingSearchSkipsNameMatchesAndHiddenSettings() {
		ModuleManager m = new ModuleManager(new EventBus());
		WithSettings trigger = m.register(new WithSettings("Trigger Bot"));
		m.register(new WithSettings("Wall Hack"));
		List<ModuleManager.SettingMatch> hits = m.searchSettings("WALL");
		assertEquals(1, hits.size());
		assertSame(trigger, hits.get(0).module());
		assertSame(trigger.walls, hits.get(0).setting());
		assertTrue(m.searchSettings(" ").isEmpty());
	}

	@Test
	void categoryKeepsRegistrationOrder() {
		List<String> names = withAuras().inCategory(Category.COMBAT).stream().map(Module::name).toList();
		assertEquals(List.of("Crystal Aura", "Kill Aura", "Anchor Aura"), names);
	}

	@Test
	void duplicateNamesAreRejected() {
		ModuleManager m = withAuras();
		assertThrows(IllegalArgumentException.class, () -> m.register(new Named("kill aura", Category.COMBAT)));
	}

	@Test
	void crashingModuleIsDisabledAndReported() {
		EventBus bus = new EventBus();
		ModuleManager m = new ModuleManager(bus);
		Crashy crashy = m.register(new Crashy());
		List<String> errors = new ArrayList<>();
		m.addErrorListener((module, error) -> errors.add(module.name() + ": " + error.getMessage()));
		crashy.setEnabled(true);
		bus.post(TickEvent.INSTANCE);
		assertFalse(crashy.isEnabled());
		assertEquals(List.of("Crashy: boom"), errors);
	}

	@Test
	void toggleListenersSeeEveryModule() {
		ModuleManager m = withAuras();
		List<String> toggled = new ArrayList<>();
		m.addToggleListener(mod -> toggled.add(mod.name()));
		m.byName("speed").toggle();
		assertEquals(List.of("Speed"), toggled);
		assertSame(m.byName("Crystal Aura"), m.get(Named.class));
	}

	@Test
	void typeLookupSeesModulesRegisteredAfterAMiss() {
		ModuleManager m = new ModuleManager(new EventBus());
		assertNull(m.get(Crashy.class));
		Crashy crashy = m.register(new Crashy());
		assertSame(crashy, m.get(Crashy.class));
		assertSame(crashy, m.get(Crashy.class));
		assertSame(crashy, m.get(Module.class));
	}

	@Test
	void moduleListShowsEnabledVisibleNonClientModulesLongestFirst() {
		ModuleManager m = withAuras();
		m.byName("Speed").setEnabled(true);
		m.byName("Crystal Aura").setEnabled(true);
		m.byName("Kill Aura").setEnabled(true);
		m.byName("Kill Aura").visible().set(false);
		List<String> names = m.moduleList().stream().map(Module::name).toList();
		assertEquals(List.of("Crystal Aura", "Speed"), names);
	}
}
