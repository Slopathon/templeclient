package nz.temple.command;

import org.junit.jupiter.api.Test;
import nz.temple.event.EventBus;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.module.ModuleManager;
import nz.temple.setting.NumberSetting;
import nz.temple.social.FriendManager;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandManagerTest {
	static final class Aura extends Module {
		final NumberSetting range = add(new NumberSetting("Range", 4.5, 3, 6, 0.1, " m"));

		Aura() {
			super("Kill Aura", Category.COMBAT, "");
		}
	}

	private record Setup(ModuleManager modules, Aura aura, FriendManager friends, List<String> out, CommandManager cmd) {
	}

	private static Setup setup() {
		ModuleManager modules = new ModuleManager(new EventBus());
		Aura aura = modules.register(new Aura());
		FriendManager friends = new FriendManager();
		List<String> out = new ArrayList<>();
		return new Setup(modules, aura, friends, out, new CommandManager(modules, friends, out::add));
	}

	@Test
	void nonCommandsPassThrough() {
		assertFalse(setup().cmd.handle("hello world"));
	}

	@Test
	void toggleBySpacelessName() {
		Setup s = setup();
		assertTrue(s.cmd.handle(".toggle killaura"));
		assertTrue(s.aura.isEnabled());
		assertTrue(s.out.get(0).contains("enabled"));
	}

	@Test
	void moduleNameShortcutToggles() {
		Setup s = setup();
		s.cmd.handle(".killaura");
		assertTrue(s.aura.isEnabled());
	}

	@Test
	void setChangesSettingValue() {
		Setup s = setup();
		assertTrue(s.cmd.handle(".set killaura range 5.2"));
		assertEquals(5.2, s.aura.range.get(), 1e-9);
	}

	@Test
	void friendAddAndList() {
		Setup s = setup();
		s.cmd.handle(".friend add Notch");
		s.cmd.handle(".friend list");
		assertTrue(s.friends.isFriend("notch"));
		assertTrue(s.out.get(s.out.size() - 1).toLowerCase().contains("notch"));
	}

	@Test
	void unknownModuleReports() {
		Setup s = setup();
		s.cmd.handle(".toggle nope");
		assertTrue(s.out.get(0).toLowerCase().contains("no module"));
	}

	@Test
	void customPrefix() {
		Setup s = setup();
		s.cmd.setPrefix("!");
		assertFalse(s.cmd.handle(".killaura"));
		assertTrue(s.cmd.handle("!killaura"));
		assertTrue(s.aura.isEnabled());
	}
}
