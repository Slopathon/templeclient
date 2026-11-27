package nz.temple.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import nz.temple.smoke.mixin.TrailerMouseAccessor;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

final class TrailerShots {
	private TrailerShots() {
	}

	private static double cursorX = -1;
	private static double cursorY = -1;
	private static boolean cursorDown;

	static String cursorLine() {
		return String.format(Locale.ROOT, "%.5f,%.5f,%d", cursorX, cursorY, cursorDown ? 1 : 0);
	}

	static void cursor(Minecraft mc, double fx, double fy) {
		cursorX = fx;
		cursorY = fy;
		var window = mc.getWindow();
		double sx = window.getScreenWidth() * fx;
		double sy = window.getScreenHeight() * fy;
		((TrailerMouseAccessor) mc.mouseHandler).temple$setX(sx);
		((TrailerMouseAccessor) mc.mouseHandler).temple$setY(sy);
		if (mc.gui.screen() != null) {
			mc.gui.screen().mouseMoved(gx(mc, fx), gy(mc, fy));
		}
	}

	static void hideCursor() {
		cursorX = -1;
		cursorY = -1;
	}

	static void click(Minecraft mc, int button) {
		var screen = mc.gui.screen();
		if (screen == null) {
			return;
		}
		var event = new MouseButtonEvent(gx(mc, cursorX), gy(mc, cursorY), new MouseButtonInfo(button, 0));
		cursorDown = true;
		screen.mouseClicked(event, false);
		screen.mouseReleased(event);
	}

	static void release() {
		cursorDown = false;
	}

	static void type(Minecraft mc, String text) {
		var screen = mc.gui.screen();
		if (screen != null) {
			text.codePoints().forEach(c -> screen.charTyped(new CharacterEvent(c)));
		}
	}

	static void key(Minecraft mc, int glfwKey) {
		var screen = mc.gui.screen();
		if (screen != null) {
			screen.keyPressed(new KeyEvent(glfwKey, 0, 0));
		}
	}

	private static double gx(Minecraft mc, double fx) {
		return mc.getWindow().getGuiScaledWidth() * fx;
	}

	private static double gy(Minecraft mc, double fy) {
		return mc.getWindow().getGuiScaledHeight() * fy;
	}

	static Object client(String method, Object... args) {
		try {
			Class<?> client = Class.forName("nz.temple.client.TempleNzClient");
			for (Method m : client.getMethods()) {
				if (m.getName().equals(method) && m.getParameterCount() == args.length) {
					Object result = m.invoke(null, args);
					if (Boolean.FALSE.equals(result) && method.startsWith("showcase")) {
						System.out.println("TEMPLE_TRAILER_WARN " + method + " " + java.util.Arrays.toString(args) + " failed");
					}
					return result;
				}
			}
			throw new IllegalStateException("no " + method);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	static void deathAt(Minecraft mc, net.minecraft.world.entity.Entity dummy) {
		if (dummy == null) {
			return;
		}
		Vec3 at = dummy.position();
		mc.player.connection.sendCommand("tp @e[type=minecraft:mannequin,limit=1,sort=nearest] ~ -400 ~");
		try {
			Object modules = client("modules");
			Object effect = modules.getClass().getMethod("byName", String.class).invoke(modules, "Death Effects");
			effect.getClass().getMethod("preview", Vec3.class).invoke(effect, at);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
	static void flicker(Trailer.Shot shot, double start, double step, int count, String... modules) {
		for (int i = 0; i <= count; i++) {
			boolean on = i < count && i % 2 == 0;
			shot.cue(start + i * step, () -> {
				for (String m : modules) {
					client("showcaseRealism", m, on);
				}
			});
		}
	}
	static void originals(String... modules) {
		for (String m : modules) {
			client("showcaseRealism", m, false);
		}
	}

	static void dropOption(String module, String setting, String option) {
		try {
			Object modules = client("modules");
			Object m = modules.getClass().getMethod("byName", String.class).invoke(modules, module);
			Object mode = null;
			for (Method method : m.getClass().getMethods()) {
				if (method.getName().equals("setting") && method.getParameterCount() == 1) {
					mode = method.invoke(m, setting);
				}
			}
			java.lang.reflect.Field field = mode.getClass().getDeclaredField("options");
			field.setAccessible(true);
			java.util.List<?> options = (java.util.List<?>) field.get(mode);
			field.set(mode, options.stream().filter(o -> !o.equals(option)).toList());
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
	static String ffmpeg() {
		String configured = System.getProperty("temple.trailer.ffmpeg", "");
		if (!configured.isBlank()) {
			return configured;
		}
		Path root = Path.of("").toAbsolutePath();
		for (Path dir = root; dir != null; dir = dir.getParent()) {
			Path bundled = dir.resolve("trailer/node_modules/@remotion/compositor-win32-x64-msvc/ffmpeg.exe");
			if (Files.isRegularFile(bundled)) {
				return bundled.toString();
			}
		}
		return "ffmpeg";
	}
	static void cam(Vec3 pos, float yaw, float pitch, float fov) {
		Trailer.camera(new Trailer.Cam(pos, yaw, pitch, fov, true));
	}
	static void lookAt(Vec3 pos, Vec3 target, float fov) {
		Vec3 d = target.subtract(pos);
		float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
		float pitch = (float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);
		cam(pos, yaw, pitch, fov);
	}

	static double ease(double x) {
		x = Mth.clamp(x, 0, 1);
		return x * x * x * (x * (x * 6 - 15) + 10);
	}
	static double easeOut(double x) {
		x = Mth.clamp(x, 0, 1);
		return 1 - Math.pow(1 - x, 3);
	}
	static Vec3 lerp(double t, Vec3 a, Vec3 b) {
		return a.add(b.subtract(a).scale(t));
	}
	static void hud(Minecraft mc, boolean visible) {
		if (mc.gui.hud.isHidden() == visible) {
			mc.gui.hud.toggle();
		}
	}
	static void location(Trailer tr, Minecraft mc, int x, int z, int time) {
		tr.command(mc, "gamemode spectator");
		tr.command(mc, "tp @s " + x + " 140 " + z);
		tr.command(mc, "time set " + time);
		tr.add(160, () -> { });
		tr.waitFor(() -> mc.levelRenderer.hasRenderedAllSections(), 1200);
	}
	static int[] flat(Minecraft mc, int x, int z, int radius) {
		int[] best = null;
		double bestDist = Double.MAX_VALUE;
		for (int dx = -radius; dx <= radius; dx += 3) {
			for (int dz = -radius; dz <= radius; dz += 3) {
				double dist = dx * dx + dz * dz;
				if (dist >= bestDist) {
					continue;
				}
				int cx = x + dx;
				int cz = z + dz;
				int lo = Integer.MAX_VALUE;
				int hi = Integer.MIN_VALUE;
				boolean ok = true;
				for (int i = -6; i <= 6 && ok; i += 2) {
					for (int j = -6; j <= 6 && ok; j += 2) {
						int h = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, cx + i, cz + j);
						lo = Math.min(lo, h);
						hi = Math.max(hi, h);
						var below = mc.level.getBlockState(new BlockPos(cx + i, h - 1, cz + j));
						ok = hi - lo <= 1 && below.getFluidState().isEmpty() && !below.is(net.minecraft.tags.BlockTags.LEAVES);
					}
				}
				if (ok) {
					best = new int[] {cx, cz};
					bestDist = dist;
				}
			}
		}
		System.out.println("TEMPLE_TRAILER flat ground near " + x + "," + z + " -> " + (best == null ? "none" : best[0] + "," + best[1]));
		return best == null ? new int[] {x, z} : best;
	}
	static void hudParts(boolean moduleList, boolean targetHud, boolean elytra, boolean map) {
		client("showcaseSet", "HUD", "Module list", Boolean.toString(moduleList));
		client("showcaseSet", "HUD", "Target HUD", Boolean.toString(targetHud));
		client("showcaseSet", "HUD", "Elytra HUD", Boolean.toString(elytra));
		for (String part : new String[] {"Potions", "Coordinates", "FPS", "Speed", "Keybinds", "Armor"}) {
			client("showcaseSet", "HUD", part, "false");
		}
		client("showcaseEnable", "Map", map);
	}

	static double surface(Minecraft mc, double x, double z) {
		return mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
	}
	static void plan(Trailer tr, Minecraft mc) {
		tr.command(mc, "gamemode creative");
		tr.command(mc, "gamerule advance_time false");
		tr.command(mc, "gamerule advance_weather false");
		tr.command(mc, "gamerule spawn_mobs false");
		tr.command(mc, "time set 1000");
		tr.command(mc, "weather clear");
		tr.command(mc, "effect clear @s");
		tr.command(mc, "gamerule announce_advancements false");
		tr.add(1, () -> {
			mc.options.renderDistance().set(20);
			mc.options.simulationDistance().set(8);
			mc.options.fov().set(70);
			mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
			mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
		});
		if (tr.wanted("scout") && !tr.only.isBlank()) {
			scout(tr, mc);
		}
		if (tr.wanted("test") && !tr.only.isBlank()) {
			test(tr, mc);
		}
		if (!(tr.wanted("scout") || tr.wanted("test")) || tr.only.isBlank()) {
			TrailerScenes.all(tr, mc);
		}
	}
	private static void scout(Trailer tr, Minecraft mc) {
		String[] biomes = {"cherry_grove", "jagged_peaks", "meadow", "frozen_peaks", "stony_peaks", "flower_forest",
				"badlands", "snowy_slopes", "dark_forest", "warm_ocean", "mangrove_swamp", "savanna_plateau"};
		tr.command(mc, "gamemode spectator");
		for (String biome : biomes) {
			BlockPos[] found = new BlockPos[1];
			tr.add(1, () -> {
				var server = mc.getSingleplayerServer();
				ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, Identifier.withDefaultNamespace(biome));
				found[0] = server.submit(() -> {
					var level = server.overworld();
					var result = level.findClosestBiome3d(h -> h.is(key), BlockPos.ZERO.atY(80), 3000, 32, 64);
					return result == null ? null : result.getFirst();
				}).join();
				System.out.println("TEMPLE_SCOUT " + biome + " -> " + found[0]);
				if (found[0] != null) {
					mc.player.connection.sendCommand("tp @s " + found[0].getX() + " 200 " + found[0].getZ());
				}
			});
			tr.add(200, () -> { });
			tr.waitFor(() -> mc.levelRenderer.hasRenderedAllSections(), 1200);
			for (int i = 0; i < 4; i++) {
				int yaw = i * 90;
				tr.add(1, () -> {
					if (found[0] == null) {
						return;
					}
					int surface = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, found[0].getX(), found[0].getZ());
					cam(new Vec3(found[0].getX() + 0.5, surface + 24, found[0].getZ() + 0.5), yaw, 18, 80);
				});
				tr.add(30, () -> { });
				tr.waitFor(() -> mc.levelRenderer.hasRenderedAllSections(), 600);
				tr.add(10, () -> {
					if (found[0] != null) {
						Screenshot.grab(mc.gameDirectory, "scout-" + biome + "-" + yaw + "-" + found[0].getX()
								+ "_" + found[0].getZ() + ".png", mc.gameRenderer.mainRenderTarget(), 2, c -> { });
					}
				});
			}
		}
		tr.add(1, () -> Trailer.camera((Trailer.Cam) null));
	}
	private static void test(Trailer tr, Minecraft mc) {
		Vec3[] center = new Vec3[1];
		location(tr, mc, 416, -1280, 11800);
		tr.add(1, () -> {
			hud(mc, false);
			client("showcaseEnable", "Skybox", true);
			client("showcaseEnable", "Waving Foliage", true);
			var p = mc.player.blockPosition();
			int y = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ());
			center[0] = new Vec3(p.getX() + 0.5, y + 2, p.getZ() + 0.5);
		});
		tr.record(mc, new Trailer.Shot("test-orbit", 4) {
			@Override
			void update(Minecraft mc, double t) {
				double a = Math.toRadians(30 + 40 * ease(t / seconds));
				Vec3 pos = center[0].add(Math.cos(a) * 14, 6 + 2 * t, Math.sin(a) * 14);
				lookAt(pos, center[0], 70);
			}
		});
		tr.record(mc, new Trailer.Shot("test-menu", 5) {
			{
				cue(0.2, () -> client("openMenu", mc.gui.screen()));
				cue(1.6, () -> type(mc, "k"));
				cue(1.75, () -> type(mc, "i"));
				cue(1.9, () -> type(mc, "l"));
				cue(2.05, () -> type(mc, "l"));
			}
			@Override
			void update(Minecraft mc, double t) {
				double a = Math.toRadians(70);
				Vec3 pos = center[0].add(Math.cos(a) * 14, 14, Math.sin(a) * 14);
				lookAt(pos, center[0], 70);
				double k = ease((t - 0.6) / 1.2);
				cursor(mc, 0.8 - 0.3 * k, 0.75 - 0.3 * k);
			}
			@Override
			void end(Minecraft mc) {
				mc.gui.setScreen(null);
				hideCursor();
				Trailer.camera((Trailer.Cam) null);
			}
		});
	}
}
