package nz.temple.smoke;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

public final class StartupSmoke implements ClientModInitializer {
	private int ticks;
	private int waited;
	@Override public void onInitializeClient() {
		try {
			for (String name : new String[] {"net.minecraft.client.Camera", "net.minecraft.client.MouseHandler",
					"net.minecraft.client.player.KeyboardInput", "net.minecraft.client.multiplayer.MultiPlayerGameMode",
					"net.minecraft.client.renderer.SkyRenderer", "net.minecraft.client.multiplayer.ClientPacketListener"}) {
				Class.forName(name);
			}
		} catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
		String dump = System.getProperty("temple.dump", "");
		if (!dump.isBlank()) {
			ClientTickEvents.END_CLIENT_TICK.register(mc -> {
				if ((mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen || mc.gui.screen() instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) && mc.gui.overlay() == null && ++ticks == 20) {
					try {
						ModuleDump.write(java.nio.file.Path.of(dump));
					} catch (Exception e) {
						throw new IllegalStateException(e);
					}
					mc.stop();
				}
			});
			return;
		}
		if (Trailer.ON) {
			Trailer.install();
			return;
		}
		if (System.getProperty("temple.showcase") != null) {
			Showcase showcase = new Showcase();
			ClientTickEvents.END_CLIENT_TICK.register(showcase::tick);
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!(mc.gui.screen() instanceof net.minecraft.client.gui.screens.TitleScreen) || mc.gui.overlay() != null) {
				if (++waited > 2400) throw new IllegalStateException("Title screen never appeared");
				return;
			}
			if (++ticks != 40) return;
			try {
				Class<?> client = Class.forName("nz.temple.client.TempleNzClient");
				Object modules = client.getMethod("modules").invoke(null);
				if (modules == null) throw new IllegalStateException("Module registry missing");
				if (!(Boolean) client.getMethod("glassAvailable").invoke(null)) {
					System.out.println("TEMPLE_SMOKE_FAIL: glass renderer disabled itself");
					Runtime.getRuntime().halt(3);
				}
				if (!(Boolean) client.getMethod("titleSceneAvailable").invoke(null)) {
					System.out.println("TEMPLE_SMOKE_FAIL: title landscape disabled itself");
					Runtime.getRuntime().halt(5);
				}
				if (mc.gui.screen().getClass() == net.minecraft.client.gui.screens.TitleScreen.class) {
					System.out.println("TEMPLE_SMOKE_FAIL: vanilla title screen was not replaced");
					Runtime.getRuntime().halt(6);
				}
				int moduleCount = (Integer) client.getMethod("moduleCount").invoke(null);
				Object problems = client.getMethod("shaderProblems").invoke(null);
				if (!((java.util.List<?>) problems).isEmpty()) {
					System.out.println("TEMPLE_SMOKE_FAIL: shaders did not compile: " + problems);
					Runtime.getRuntime().halt(4);
				}
				System.out.println("TEMPLE_SMOKE_PASS: startup, camera/input/interaction/sky/knockback mixins, glass, title landscape, world shaders, " + moduleCount + " modules");
			} catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
			mc.stop();
		});
	}
}
