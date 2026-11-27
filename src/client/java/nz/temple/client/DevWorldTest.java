package nz.temple.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import nz.temple.module.Category;
import nz.temple.module.Module;

import java.util.ArrayList;
import java.util.List;

final class DevWorldTest {
	private static final int PLAY_TICKS = 400;
	private int stage;
	private int waited;
	private boolean listening;
	private final List<String> errors = new ArrayList<>();
	private DevWorldTest() {
	}

	private static boolean wanted(Module module) {
		String only = System.getProperty("temple.worldTest.modules", "").trim();
		if (only.equalsIgnoreCase("keep")) {
			return false;
		}
		if (only.isEmpty()) {
			return module.category() == Category.RENDER || module.category() == Category.COSMETICS;
		}
		for (String name : only.split(",")) {
			if (module.name().equalsIgnoreCase(name.trim())) {
				return true;
			}
		}
		return false;
	}
	private static final boolean WALK = Boolean.getBoolean("temple.worldTest.walk");
	private static void grab(Minecraft mc, String name) {
		net.minecraft.client.Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1,
				message -> System.out.println("[temple] world test: saved " + name));
	}

	static void start() {
		ClientTickEvents.END_CLIENT_TICK.register(new DevWorldTest()::tick);
	}
	private void tick(Minecraft mc) {
		mc.options.pauseOnLostFocus = false;
		switch (stage) {
			case 0 -> {
				if (!listening) {
					listening = true;
					TempleNzClient.modules().addErrorListener((module, error) -> errors.add(module.name() + ": " + error));
				}
				if (mc.gui.overlay() != null || mc.gui.screen() == null || ++waited < 40) {
					return;
				}
				mc.options.onboardAccessibility = false;
				CreateWorldScreen.testWorld(mc, () -> { });
				stage = 1;
			}
			case 1 -> {
				if (mc.gui.screen() instanceof CreateWorldScreen screen) {
					for (var child : screen.children()) {
						if (child instanceof AbstractButton button && button.getMessage().getString().toLowerCase().contains("create")) {
							button.onPress(null);
							stage = 2;
							waited = 0;
							return;
						}
					}
				}
			}
			case 2 -> {
				if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.ConfirmScreen confirm) {
					for (var child : confirm.children()) {
						if (child instanceof AbstractButton button) {
							String label = button.getMessage().getString().toLowerCase();
							if (!label.contains("back") && !label.contains("cancel") && !label.equals("no")) {
								button.onPress(null);
								return;
							}
						}
					}
					return;
				}
				if (mc.level != null && mc.player != null && ++waited > 80) {
					for (Module module : TempleNzClient.modules().all()) {
						if (wanted(module) && !module.isEnabled()) {
							try {
								module.setEnabled(true);
							} catch (RuntimeException e) {
								errors.add(module.name() + " (enabling): " + e);
							}
						}
					}
					System.out.println("[temple] world test: in the world, render and cosmetic modules on");
					stage = 3;
					waited = 0;
				}
			}
			case 3 -> {
				++waited;
				if (waited == 60 && !System.getProperty("temple.worldTest.time", "").isEmpty()) {
					mc.player.connection.sendCommand("time set " + System.getProperty("temple.worldTest.time"));
				}
				if (waited == 50 && !System.getProperty("temple.worldTest.parts", "").isEmpty()) {
					List<String> parts = List.of(System.getProperty("temple.worldTest.parts").toLowerCase().split(","));
					for (Module module : TempleNzClient.modules().all()) {
						if (module.name().equals("Realistic World")) {
							for (var setting : module.allSettings()) {
								if (setting instanceof nz.temple.setting.BoolSetting toggle
										&& java.util.Set.of("water", "wet ground", "foliage", "grass", "lighting").contains(toggle.name().toLowerCase())) {
									toggle.set(parts.contains(toggle.name().toLowerCase()));
								}
							}
						}
					}
				}
				if (waited == 60) {
					for (String name : System.getProperty("temple.worldTest.off", "").split(",")) {
						for (Module module : TempleNzClient.modules().all()) {
							if (!name.isBlank() && module.name().equalsIgnoreCase(name.trim()) && module.isEnabled()) {
								module.setEnabled(false);
							}
						}
					}
				}
				mc.options.pauseOnLostFocus = false;
				if (mc.gui.screen() != null) {
					mc.gui.setScreen(null);
				}
				if (waited == 100) {
					mc.player.setXRot(Float.parseFloat(System.getProperty("temple.worldTest.pitch", "15")));
					mc.player.setYRot(0);
					if (WALK) {
						mc.player.connection.sendCommand("summon minecraft:pig ~4 ~ ~30 {NoAI:1b}");
					}
				}
				if (waited == 120 || waited == 121) {
					grab(mc, "world-test-" + waited + ".png");
				}
				if (WALK && waited == 125) {
					mc.options.bobView().set(true);
					mc.options.keyUp.setDown(true);
				}
				if (WALK && waited >= 140 && waited < 148) {
					grab(mc, "world-walk-" + waited + ".png");
				}
				if (WALK && waited == 150) {
					mc.options.keyUp.setDown(false);
				}
				if (waited >= PLAY_TICKS) {
					System.out.println("[temple] world test: " + (errors.isEmpty() ? "no module errors" : errors.size() + " module errors: " + errors));
					Runtime.getRuntime().halt(errors.isEmpty() ? 0 : 1);
				}
			}
			default -> {
			}
		}
	}
}
