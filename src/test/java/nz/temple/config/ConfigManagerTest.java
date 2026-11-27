package nz.temple.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import nz.temple.event.EventBus;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.module.ModuleManager;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {
	static final class Aura extends Module {
		final NumberSetting range = add(new NumberSetting("Range", 4.5, 3, 6, 0.1, " m"));
		final ModeSetting targets = add(new ModeSetting("Targets", List.of("Players", "Mobs"), "Players"));

		Aura() {
			super("Kill Aura", Category.COMBAT, "");
		}
	}

	private record Setup(ModuleManager modules, Aura aura, ConfigManager config, AtomicLong clock) {
	}

	private static Setup setup(Path dir) {
		ModuleManager modules = new ModuleManager(new EventBus());
		Aura aura = modules.register(new Aura());
		AtomicLong clock = new AtomicLong(1_000);
		return new Setup(modules, aura, new ConfigManager(dir, modules, clock::get), clock);
	}

	@Test
	void roundTripsModulesSettingsBindsAndExtras(@TempDir Path dir) {
		Setup a = setup(dir);
		a.aura.setEnabled(true);
		a.aura.range.set(5.2);
		a.aura.targets.set("Mobs");
		a.aura.keybind().set(82);
		a.aura.disableOnKeyRelease().set(true);
		a.aura.visible().set(false);
		a.config.putExtra("menu.x", 120);
		a.config.save();

		Setup b = setup(dir);
		assertEquals(ConfigManager.LoadResult.LOADED, b.config.load());
		assertTrue(b.aura.isEnabled());
		assertEquals(5.2, b.aura.range.get(), 1e-9);
		assertEquals("Mobs", b.aura.targets.get());
		assertEquals(82, b.aura.keybind().get());
		assertTrue(b.aura.disableOnKeyRelease().get());
		assertFalse(b.aura.visible().get());
		assertEquals(120, b.config.getExtra("menu.x", 0));
	}

	@Test
	void savesOnlyAfterDebounceDelay(@TempDir Path dir) {
		Setup s = setup(dir);
		s.aura.range.set(5.0);
		s.clock.addAndGet(499);
		s.config.tick();
		assertFalse(Files.exists(s.config.file()));
		s.clock.addAndGet(1);
		s.config.tick();
		assertTrue(Files.exists(s.config.file()));
	}

	@Test
	void loadingDoesNotScheduleASave(@TempDir Path dir) {
		Setup a = setup(dir);
		a.aura.range.set(5.0);
		a.config.save();
		Setup b = setup(dir);
		b.config.load();
		assertFalse(b.config.isDirty());
	}

	@Test
	void corruptFileIsBackedUpAndDefaultsKept(@TempDir Path dir) throws Exception {
		Setup s = setup(dir);
		Files.createDirectories(dir);
		Files.writeString(s.config.file(), "{ this is not json");
		assertEquals(ConfigManager.LoadResult.CORRUPT, s.config.load());
		assertEquals(4.5, s.aura.range.get());
		assertFalse(Files.exists(s.config.file()));
		try (var files = Files.list(dir)) {
			assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("config.broken-")));
		}
	}

	@Test
	void unknownEntriesIgnoredAndOutOfRangeClamped(@TempDir Path dir) throws Exception {
		Setup s = setup(dir);
		Files.createDirectories(dir);
		Files.writeString(s.config.file(), """
				{"version":1,"modules":{
				  "Kill Aura":{"enabled":false,"bind":-1,"visible":true,"settings":{"Range":99,"Nope":1}},
				  "Ghost Module":{"enabled":true}
				},"extras":{}}
				""");
		assertEquals(ConfigManager.LoadResult.LOADED, s.config.load());
		assertEquals(6.0, s.aura.range.get());
	}

	static final class World extends Module {
		final nz.temple.setting.BoolSetting water = add(new nz.temple.setting.BoolSetting("Water", true));
		final NumberSetting waves = keyed(new NumberSetting("Waves", 60, 0, 100, 5, "%"), "Water: Waves");
		final nz.temple.setting.BoolSetting wet = add(new nz.temple.setting.BoolSetting("Wet ground", false));
		final NumberSetting shine = keyed(new NumberSetting("Shine", 70, 0, 100, 5, "%"), "Wet ground: Shine");
		final nz.temple.setting.BoolSetting foliage = add(new nz.temple.setting.BoolSetting("Foliage", true));
		final nz.temple.setting.BoolSetting grass = add(new nz.temple.setting.BoolSetting("Grass", true));
		final nz.temple.setting.BoolSetting lighting = add(new nz.temple.setting.BoolSetting("Lighting", true));

		World() {
			super("Realistic World", Category.COSMETICS, "");
		}

		private NumberSetting keyed(NumberSetting s, String key) {
			s.keyAs(key);
			return add(s);
		}
	}

	@Test
	void theThreeWorldModulesCarryOverIntoRealisticWorld(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("config.json"), """
				{"version":2,"modules":{
				"Realistic Water":{"enabled":false,"settings":{"Waves":35}},
				"Wet Ground":{"enabled":true,"settings":{"Shine":90}}}}""");
		ModuleManager modules = new ModuleManager(new EventBus());
		World world = modules.register(new World());
		assertEquals(ConfigManager.LoadResult.LOADED, new ConfigManager(dir, modules, () -> 0L).load());
		assertTrue(world.isEnabled());
		assertFalse(world.water.get());
		assertEquals(35, world.waves.get(), 1e-9);
		assertTrue(world.wet.get());
		assertEquals(90, world.shine.get(), 1e-9);
		assertTrue(world.foliage.get());
		assertFalse(world.grass.get());
		assertFalse(world.lighting.get());
	}

	@Test
	void missingFileReportsMissing(@TempDir Path dir) {
		assertEquals(ConfigManager.LoadResult.MISSING, setup(dir).config.load());
	}

	@Test
	void shareCodeCarriesOnlyChangesAndReplacesTheReceiversSetup(@TempDir Path dir, @TempDir Path other) {
		Setup sender = setup(dir);
		sender.aura.setEnabled(true);
		sender.aura.range.set(5.2);
		sender.aura.keybind().set(82);
		sender.aura.disableOnKeyRelease().set(true);
		String code = sender.config.exportCode();
		assertTrue(code.startsWith(ConfigManager.CODE_PREFIX));

		Setup receiver = setup(other);
		receiver.aura.targets.set("Mobs");
		receiver.aura.keybind().set(70);
		ConfigManager.ImportResult result = receiver.config.importCode("  " + code + "\n");
		assertTrue(result.ok(), result.error());
		assertTrue(receiver.aura.isEnabled());
		assertEquals(5.2, receiver.aura.range.get(), 1e-9);
		assertEquals("Players", receiver.aura.targets.get(), "settings the sender left default are reset");
		assertEquals(70, receiver.aura.keybind().get(), "keybinds are never shared");
		assertFalse(receiver.aura.disableOnKeyRelease().get(), "keybind behavior is never shared");
		assertTrue(receiver.config.isDirty());
	}

	@Test
	void rejectsForeignOrDamagedCodes(@TempDir Path dir) {
		Setup s = setup(dir);
		s.aura.range.set(5.5);
		assertFalse(s.config.importCode("hello").ok());
		String code = s.config.exportCode();
		assertFalse(s.config.importCode(code.substring(0, code.length() / 2)).ok());
		assertEquals(5.5, s.aura.range.get(), 1e-9, "a failed import changes nothing");
	}

	@Test
	void codesAreShortAndUseOnlyLettersAndDigits(@TempDir Path dir) {
		Setup s = setup(dir);
		s.aura.setEnabled(true);
		s.aura.range.set(5.2);
		s.aura.targets.set("Mobs");
		String code = s.config.exportCode();
		assertTrue(code.matches("[A-Za-z0-9]+"), code);
		assertTrue(code.length() < 40, "too long: " + code.length() + " " + code);
	}

	@Test
	void codesSurviveChatMangling(@TempDir Path dir, @TempDir Path other) {
		Setup sender = setup(dir);
		sender.aura.setEnabled(true);
		sender.aura.range.set(3.7);
		String code = sender.config.exportCode();
		String mangled = "```\n" + code.substring(0, 9) + "​\n " + code.substring(9) + " \n```";

		Setup receiver = setup(other);
		ConfigManager.ImportResult result = receiver.config.importCode(mangled);
		assertTrue(result.ok(), result.error());
		assertTrue(receiver.aura.isEnabled());
		assertEquals(3.7, receiver.aura.range.get(), 1e-9);
	}

	@Test
	void aSingleChangedCharacterIsCaught(@TempDir Path dir) {
		Setup s = setup(dir);
		s.aura.range.set(5.5);
		String code = s.config.exportCode();
		int i = code.length() - 3;
		char swapped = code.charAt(i) == 'a' ? 'b' : 'a';
		ConfigManager.ImportResult result = s.config.importCode(code.substring(0, i) + swapped + code.substring(i + 1));
		assertFalse(result.ok());
		assertEquals(5.5, s.aura.range.get(), 1e-9);
	}

	@Test
	void olderCodesStillLoad(@TempDir Path dir) {
		Setup s = setup(dir);
		String legacy = legacyCode("{\"v\":1,\"m\":{\"Kill Aura\":{\"e\":true,\"s\":{\"Range\":5.2}}}}");
		ConfigManager.ImportResult result = s.config.importCode(legacy);
		assertTrue(result.ok(), result.error());
		assertTrue(s.aura.isEnabled());
		assertEquals(5.2, s.aura.range.get(), 1e-9);
	}

	private static String legacyCode(String json) {
		java.util.zip.Deflater d = new java.util.zip.Deflater(java.util.zip.Deflater.BEST_COMPRESSION, true);
		d.setInput(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		d.finish();
		byte[] buf = new byte[1024];
		int n = d.deflate(buf);
		d.end();
		return "TEMPLE1-" + java.util.Base64.getUrlEncoder().withoutPadding()
				.encodeToString(java.util.Arrays.copyOf(buf, n));
	}
}
