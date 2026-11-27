package nz.temple.client.music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import nz.temple.TempleNz;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class MediaSession {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/music");
	public record Track(String title, String artist, String album, String app, boolean spotify, boolean playing,
			double position, double duration, long updated, boolean timed) {
		public String key() {
			return title + "\u0000" + artist;
		}
	}
	private final Path dir = FabricLoader.getInstance().getConfigDir().resolve(TempleNz.MOD_ID).resolve("music");
	private volatile Track track;
	private volatile boolean nothing;
	private volatile String error;
	private volatile Path art;
	private volatile long artVersion;
	private volatile long lastLine;
	private Process process;
	private Thread reader;
	private String estimateKey = "";
	private long estimateMark;
	private double estimateSeconds;
	private boolean estimatePlaying;
	public static boolean supported() {
		return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
	}

	public synchronized boolean running() {
		return process != null && process.isAlive();
	}

	public synchronized void start() {
		if (running() || !supported()) {
			return;
		}
		try {
			Files.createDirectories(dir);
			Path script = dir.resolve("media.ps1");
			byte[] wanted;
			try (InputStream in = MediaSession.class.getResourceAsStream("/assets/" + TempleNz.MOD_ID + "/music/media.ps1")) {
				if (in == null) {
					error = "helper missing";
					return;
				}
				wanted = in.readAllBytes();
			}
			if (!Files.exists(script) || !Arrays.equals(Files.readAllBytes(script), wanted)) {
				Files.write(script, wanted);
			}
			Files.deleteIfExists(dir.resolve("command.txt"));
			ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
					"-WindowStyle", "Hidden", "-File", script.toString(), "-Dir", dir.toString(),
					"-ParentPid", Long.toString(ProcessHandle.current().pid()));
			builder.redirectErrorStream(true);
			process = builder.start();
			Process started = process;
			error = null;
			reader = new Thread(() -> read(started), "temple-music");
			reader.setDaemon(true);
			reader.start();
		} catch (Exception e) {
			error = "could not start: " + e.getMessage();
			LOGGER.warn("Music helper failed to start", e);
		}
	}
	public synchronized void stop() {
		if (process != null) {
			process.destroy();
			process = null;
		}
		track = null;
		nothing = false;
	}
	private void read(Process p) {
		try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = in.readLine()) != null) {
				lastLine = System.currentTimeMillis();
				line = line.trim();
				if (!line.startsWith("{")) {
					continue;
				}
				try {
					accept(JsonParser.parseString(line).getAsJsonObject());
				} catch (RuntimeException ignored) {
				}
			}
		} catch (Exception ignored) {
		}
	}
	private void accept(JsonObject o) {
		if (o.has("error")) {
			error = string(o, "error");
			return;
		}
		error = null;
		if (o.has("none")) {
			nothing = true;
			track = null;
			return;
		}
		nothing = false;
		if (o.has("art")) {
			String path = string(o, "art");
			if (!path.isEmpty()) {
				art = Path.of(path);
				artVersion++;
			}
		}
		double duration = number(o, "duration");
		track = new Track(string(o, "title"), string(o, "artist"), string(o, "album"), string(o, "app"),
				o.has("spotify") && o.get("spotify").getAsBoolean(), "Playing".equals(string(o, "status")),
				number(o, "position"), duration, (long) number(o, "updated"), duration > 0);
	}
	private static String string(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}
	private static double number(JsonObject o, String key) {
		JsonElement e = o.get(key);
		try {
			return e == null || e.isJsonNull() ? 0 : e.getAsDouble();
		} catch (RuntimeException ex) {
			return 0;
		}
	}
	public Track track() {
		Track fake = fake();
		return fake != null ? fake : track;
	}
	private long fakeStart;
	private Track fake() {
		String spec = System.getProperty("temple.showcase") != null ? System.getProperty("temple.music.fake") : null;
		if (spec == null) {
			return null;
		}
		String[] p = spec.split("[|]");
		long now = System.currentTimeMillis();
		if (fakeStart == 0) {
			fakeStart = now;
		}
		double start = Double.parseDouble(p[4]);
		return new Track(p[0], p[1], p[2], "showcase", true, true, start + (now - fakeStart) / 1000.0, Double.parseDouble(p[3]), now, true);
	}
	public boolean nothingPlaying() {
		return nothing;
	}
	public String error() {
		return error;
	}
	public Path art() {
		String fake = fakeArt();
		return fake != null ? Path.of(fake) : art;
	}
	public long artVersion() {
		return fakeArt() != null ? 1 : artVersion;
	}
	private static String fakeArt() {
		return System.getProperty("temple.showcase") != null ? System.getProperty("temple.music.fakeArt") : null;
	}
	public synchronized double position(Track t) {
		long now = System.currentTimeMillis();
		if (t == null) {
			return 0;
		}
		if (t.timed()) {
			double p = t.position() + (t.playing() ? Math.max(0, now - t.updated()) / 1000.0 : 0);
			return Math.min(p, t.duration());
		}
		if (!t.key().equals(estimateKey)) {
			estimateKey = t.key();
			estimateSeconds = 0;
			estimateMark = now;
			estimatePlaying = t.playing();
		}
		if (estimatePlaying) {
			estimateSeconds += (now - estimateMark) / 1000.0;
		}
		estimateMark = now;
		estimatePlaying = t.playing();
		return estimateSeconds;
	}
	public void command(String command) {
		if (!running()) {
			return;
		}
		try {
			Files.writeString(dir.resolve("command.txt"), command, StandardCharsets.UTF_8);
		} catch (Exception e) {
			LOGGER.warn("Music command failed", e);
		}
	}
	public Path folder() {
		return dir;
	}
}
