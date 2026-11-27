package nz.temple.client.util;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public final class BaritoneBridge {
	private static final Map<String, String[]> BUILDS = Map.of(
			"1.21.11", new String[] {"1.17.0", "e6a737f4b603b43a17b00285a788d99e5ce0852897dbae7adc347ecc331efc93"},
			"26.2", new String[] {"1.19.0", "ba1a268856e0caee7f828b6663f26670138d5c348fdb81b8c7859809f73e8d2f"},
			"26.3", new String[] {"1.20.0", "7836a235f37339897425ec303b5c0d7f3cf9d99dbdaa1952240b5099aafd8776"});
	private static volatile boolean humanize;
	private static volatile float turnSpeed = 25;
	private static long lastTick = -1;
	private static float[] lastOut;
	private static float lastTargetYaw;
	private static float lastTargetPitch;
	private BaritoneBridge() {
	}
	public static boolean installed() {
		return FabricLoader.getInstance().isModLoaded("baritone");
	}

	public static boolean supported() {
		return BUILDS.containsKey(SharedConstants.getCurrentVersion().name());
	}

	public static void setHumanize(boolean on, float speed) {
		humanize = on;
		turnSpeed = speed;
	}

	private static Object baritone() throws ReflectiveOperationException {
		Object provider = Class.forName("baritone.api.BaritoneAPI").getMethod("getProvider").invoke(null);
		return Class.forName("baritone.api.IBaritoneProvider").getMethod("getPrimaryBaritone").invoke(provider);
	}

	private static Object call(Object target, String type, String method) throws ReflectiveOperationException {
		return Class.forName(type).getMethod(method).invoke(target);
	}

	public static boolean command(String command) {
		try {
			Object manager = call(baritone(), "baritone.api.IBaritone", "getCommandManager");
			Method execute = Class.forName("baritone.api.command.manager.ICommandManager").getMethod("execute", String.class);
			return (Boolean) execute.invoke(manager, command);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return false;
		}
	}
	public static void stop() {
		try {
			Object pathing = call(baritone(), "baritone.api.IBaritone", "getPathingBehavior");
			call(pathing, "baritone.api.behavior.IPathingBehavior", "cancelEverything");
		} catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
		}
	}
	public static boolean busy() {
		try {
			Object b = baritone();
			Object control = call(b, "baritone.api.IBaritone", "getPathingControlManager");
			Optional<?> process = (Optional<?>) call(control, "baritone.api.pathing.calc.IPathingControlManager", "mostRecentInControl");
			Object pathing = call(b, "baritone.api.IBaritone", "getPathingBehavior");
			return process.isPresent() || (Boolean) call(pathing, "baritone.api.behavior.IPathingBehavior", "isPathing");
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return false;
		}
	}
	public static boolean set(String name, Object value) {
		try {
			Object settings = Class.forName("baritone.api.BaritoneAPI").getMethod("getSettings").invoke(null);
			Field field = settings.getClass().getField(name);
			Object setting = field.get(settings);
			Field v = setting.getClass().getField("value");
			Object current = v.get(setting);
			if (current instanceof Integer && value instanceof Number n) {
				value = n.intValue();
			} else if (current instanceof Long && value instanceof Number n) {
				value = n.longValue();
			} else if (current instanceof Float && value instanceof Number n) {
				value = n.floatValue();
			} else if (current instanceof Double && value instanceof Number n) {
				value = n.doubleValue();
			}
			v.set(setting, value);
			return true;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return false;
		}
	}
	public static float[] humanize(float yaw, float pitch) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (!humanize || player == null || mc.level == null) {
			return null;
		}
		long tick = mc.level.getGameTime();
		if (tick == lastTick && lastOut != null && yaw == lastTargetYaw && pitch == lastTargetPitch) {
			return lastOut.clone();
		}
		float fromYaw = Rotations.serverYaw(player);
		float fromPitch = Rotations.serverPitch(player);
		float toYaw = fromYaw + Mth.wrapDegrees(yaw - fromYaw);
		float remaining = Rotations.angle(fromYaw, fromPitch, toYaw, pitch);
		ThreadLocalRandom r = ThreadLocalRandom.current();
		float speed = turnSpeed * (0.85f + 0.3f * r.nextFloat());
		float step = Math.min(speed, Math.max(2.5f, remaining * 0.55f + 1.5f));
		float[] next = Rotations.step(fromYaw, fromPitch, toYaw, pitch, step);
		if (remaining > step) {
			next[0] += (float) r.nextGaussian() * 0.15f;
			next[1] = Mth.clamp(next[1] + (float) r.nextGaussian() * 0.1f, -90, 90);
		}
		lastTick = tick;
		lastTargetYaw = yaw;
		lastTargetPitch = pitch;
		lastOut = next.clone();
		return next;
	}
	public static CompletableFuture<String> install() {
		String[] build = BUILDS.get(SharedConstants.getCurrentVersion().name());
		if (build == null) {
			return CompletableFuture.completedFuture("Baritone has no build for Minecraft " + SharedConstants.getCurrentVersion().name() + ".");
		}
		String file = "baritone-unoptimized-fabric-" + build[0] + ".jar";
		URI url = URI.create("https://github.com/cabaletta/baritone/releases/download/v" + build[0] + "/" + file);
		return CompletableFuture.supplyAsync(() -> {
			try {
				Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
				Files.createDirectories(mods);
				Path target = mods.resolve(file);
				if (Files.exists(target)) {
					return null;
				}
				HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
				HttpResponse<InputStream> res = http.send(HttpRequest.newBuilder(url).timeout(Duration.ofMinutes(3)).GET().build(),
						HttpResponse.BodyHandlers.ofInputStream());
				if (res.statusCode() / 100 != 2) {
					return "The download answered " + res.statusCode() + ".";
				}
				Path part = mods.resolve(file + ".part");
				MessageDigest sha = MessageDigest.getInstance("SHA-256");
				try (InputStream in = res.body(); OutputStream out = Files.newOutputStream(part)) {
					byte[] buf = new byte[1 << 16];
					int n;
					long total = 0;
					while ((n = in.read(buf)) > 0) {
						total += n;
						if (total > 32L * 1024 * 1024) {
							throw new java.io.IOException("too large");
						}
						sha.update(buf, 0, n);
						out.write(buf, 0, n);
					}
				}
				if (!HexFormat.of().formatHex(sha.digest()).equals(build[1])) {
					Files.deleteIfExists(part);
					return "The download did not match Baritone's release, so it was not installed.";
				}
				Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				return null;
			} catch (Exception e) {
				return "Could not download Baritone. Check your connection.";
			}
		});
	}
}
