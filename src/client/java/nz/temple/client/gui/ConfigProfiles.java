package nz.temple.client.gui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import nz.temple.client.TempleNzClient;
import nz.temple.config.ConfigManager;

/**
 * Configs saved on this computer ("My configs"). Each one is a share code (the same format the cloud library and Copy
 * code use) in config/templenz/profiles/&lt;name&gt;.txt.
 */
public final class ConfigProfiles {
	public record Profile(String name, long savedMillis, Path file) {
	}

	private ConfigProfiles() {
	}

	private static Path dir() {
		ConfigManager config = TempleNzClient.config();
		return config == null ? null : config.file().getParent().resolve("profiles");
	}

	/** Saved configs, newest first. */
	public static List<Profile> list() {
		List<Profile> out = new ArrayList<>();
		Path dir = dir();
		if (dir == null || !Files.isDirectory(dir)) {
			return out;
		}
		try (Stream<Path> files = Files.list(dir)) {
			files.filter(f -> f.getFileName().toString().endsWith(".txt")).forEach(f -> {
				String name = f.getFileName().toString();
				try {
					out.add(new Profile(name.substring(0, name.length() - 4), Files.getLastModifiedTime(f).toMillis(), f));
				} catch (IOException ignored) {
				}
			});
		} catch (IOException ignored) {
		}
		out.sort(Comparator.comparingLong(Profile::savedMillis).reversed());
		return out;
	}

	/** A file-safe version of a name the player typed. */
	public static String clean(String name) {
		String n = name.trim().replaceAll("[\\\\/:*?\"<>|]", "").replaceAll("\\s+", " ");
		return n.length() > 32 ? n.substring(0, 32).trim() : n;
	}

	/** Saves the current setup under {@code name}, replacing a config with the same name. */
	public static boolean save(String name) {
		ConfigManager config = TempleNzClient.config();
		Path dir = dir();
		String n = clean(name);
		if (config == null || dir == null || n.isEmpty()) {
			return false;
		}
		try {
			Files.createDirectories(dir);
			Files.writeString(dir.resolve(n + ".txt"), config.exportCode(), StandardCharsets.UTF_8);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	public static String code(Profile profile) {
		try {
			return Files.readString(profile.file(), StandardCharsets.UTF_8).trim();
		} catch (IOException e) {
			return null;
		}
	}

	public static boolean delete(Profile profile) {
		try {
			return Files.deleteIfExists(profile.file());
		} catch (IOException e) {
			return false;
		}
	}
}
