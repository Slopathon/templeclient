package nz.temple.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import nz.temple.module.Module;
import nz.temple.module.ModuleManager;
import nz.temple.module.LazyPayloadModule;
import nz.temple.setting.FileSetting;
import nz.temple.setting.KeybindSetting;
import nz.temple.setting.Setting;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.DataFormatException;
import java.util.function.LongSupplier;

public final class ConfigManager {
	public static final long SAVE_DELAY_MS = 500;

	public enum LoadResult { LOADED, MISSING, CORRUPT }

	public static final String CODE_PREFIX = ShareCode.PREFIX;
	private static final String LEGACY_PREFIX = "TEMPLE1-";
	private static final int CODE_VERSION = 3;
	private final Map<Integer, Module> byHash = new HashMap<>();

	public record ImportResult(boolean ok, int modules, String error) {
		static ImportResult failed(String error) {
			return new ImportResult(false, 0, error);
		}
	}

	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
	private final Path dir;
	private final ModuleManager modules;
	private final LongSupplier clockMillis;
	private JsonObject extras = new JsonObject();
	private boolean dirty;
	private boolean applying;
	private long lastChange;

	public ConfigManager(Path dir, ModuleManager modules, LongSupplier clockMillis) {
		this.dir = dir;
		this.modules = modules;
		this.clockMillis = clockMillis;
		for (Module m : modules.all()) {
			m.addChangeListener(this::markDirty);
		}
		indexHashes();
	}

	public Path file() {
		return dir.resolve("config.json");
	}

	public boolean isDirty() {
		return dirty;
	}

	public void markDirty() {
		if (applying) {
			return;
		}
		dirty = true;
		lastChange = clockMillis.getAsLong();
	}

	public void tick() {
		if (dirty && clockMillis.getAsLong() - lastChange >= SAVE_DELAY_MS) {
			save();
		}
	}

	public double getExtra(String key, double fallback) {
		JsonElement e = extras.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : fallback;
	}

	public void putExtra(String key, double value) {
		extras.addProperty(key, value);
		markDirty();
	}

	public void save() {
		JsonObject root = new JsonObject();
		root.addProperty("version", 2);
		JsonObject mods = new JsonObject();
		for (Module m : modules.all()) {
			JsonObject o = new JsonObject();
			o.addProperty("enabled", m.isEnabled());
			o.add("bind", m.keybind().toJson());
			o.add("disableOnKeyRelease", m.disableOnKeyRelease().toJson());
			o.add("visible", m.visible().toJson());
			JsonObject settings = m instanceof LazyPayloadModule lazy ? lazy.savedSettings() : new JsonObject();
			if (!(m instanceof LazyPayloadModule)) {
				for (Setting<?> s : m.allSettings()) {
					settings.add(s.key(), s.toJson());
				}
			}
			o.add("settings", settings);
			mods.add(m.name(), o);
		}
		root.add("modules", mods);
		root.add("extras", extras);
		try {
			Files.createDirectories(dir);
			Path tmp = dir.resolve("config.json.tmp");
			Files.writeString(tmp, gson.toJson(root), StandardCharsets.UTF_8);
			Files.move(tmp, file(), StandardCopyOption.REPLACE_EXISTING);
			dirty = false;
		} catch (IOException e) {
			throw new UncheckedIOException("Could not save " + file(), e);
		}
	}

	public LoadResult load() {
		Path file = file();
		if (!Files.exists(file)) {
			return LoadResult.MISSING;
		}
		JsonObject root;
		try {
			root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException | JsonParseException | IllegalStateException e) {
			backUpBroken(file);
			return LoadResult.CORRUPT;
		}
		applying = true;
		try {
			JsonObject mods = root.has("modules") && root.get("modules").isJsonObject() ? root.getAsJsonObject("modules") : new JsonObject();
			int version = root.has("version") ? root.get("version").getAsInt() : 1;
			gatherRealisticWorld(mods);
			for (Module m : modules.all()) {
				String savedName = version < 2 ? switch (m.name()) {
					case "Air Effects" -> "Ambience";
					case "Hand Tweaks" -> "Animations";
					case "Ambience" -> "";
					default -> m.name();
				} : m.name();
				JsonElement entry = mods.get(savedName);
				if (entry == null && m.name().equals("All Legit")) {
					entry = mods.get("Legit Everywhere");
				}
				if (entry != null && entry.isJsonObject()) {
					apply(m, entry.getAsJsonObject());
				}
			}
			extras = root.has("extras") && root.get("extras").isJsonObject() ? root.getAsJsonObject("extras") : new JsonObject();
		} finally {
			applying = false;
		}
		dirty = false;
		return LoadResult.LOADED;
	}

	private static void gatherRealisticWorld(JsonObject mods) {
		String[][] moved = {{"Realistic Water", "Water"}, {"Wet Ground", "Wet ground"}, {"Waving Foliage", "Foliage"}};
		if (mods.has("Realistic World")) {
			return;
		}
		JsonObject settings = new JsonObject();
		boolean any = false;
		boolean on = false;
		for (String[] pair : moved) {
			JsonElement e = mods.get(pair[0]);
			if (e == null || !e.isJsonObject()) {
				continue;
			}
			any = true;
			JsonObject old = e.getAsJsonObject();
			boolean was = old.has("enabled") && old.get("enabled").isJsonPrimitive() && old.get("enabled").getAsJsonPrimitive().isBoolean()
					&& old.get("enabled").getAsBoolean();
			on |= was;
			settings.addProperty(pair[1], was);
			if (old.has("settings") && old.get("settings").isJsonObject()) {
				for (Map.Entry<String, JsonElement> s : old.getAsJsonObject("settings").entrySet()) {
					settings.add(pair[1] + ": " + s.getKey(), s.getValue());
				}
			}
		}
		if (!any) {
			return;
		}
		settings.addProperty("Grass", false);
		settings.addProperty("Lighting", false);
		JsonObject world = new JsonObject();
		world.addProperty("enabled", on);
		world.add("settings", settings);
		mods.add("Realistic World", world);
	}
	private void apply(Module m, JsonObject o) {
		if (o.has("bind")) {
			m.keybind().fromJson(o.get("bind"));
		}
		if (o.has("disableOnKeyRelease")) {
			m.disableOnKeyRelease().fromJson(o.get("disableOnKeyRelease"));
		}
		if (o.has("visible")) {
			m.visible().fromJson(o.get("visible"));
		}
		if (o.has("settings") && o.get("settings").isJsonObject()) {
			JsonObject settings = o.getAsJsonObject("settings");
			if (m instanceof LazyPayloadModule lazy) {
				lazy.restoreSettings(settings);
			} else {
			for (Setting<?> s : m.allSettings()) {
				if (settings.has(s.key())) {
					s.fromJson(settings.get(s.key()));
				} else if (!s.key().equals(s.name()) && settings.has(s.name())) {
					s.fromJson(settings.get(s.name()));
				}
			}
			}
		}
		JsonElement enabled = o.get("enabled");
		if (enabled != null && enabled.isJsonPrimitive() && enabled.getAsJsonPrimitive().isBoolean()) {
			m.setEnabled(enabled.getAsBoolean());
		}
	}
	public String exportCode() {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			out.writeByte(CODE_VERSION);
			for (Module m : modules.all()) {
				List<Setting<?>> changed = new ArrayList<>();
				for (Setting<?> s : m.allSettings()) {
					if (shareable(s) && !s.isDefaultValue()) {
						changed.add(s);
					}
				}
				boolean on = !m.isAlwaysOn() && m.isEnabled();
				if (!on && changed.isEmpty()) {
					continue;
				}
				int hash = moduleHash(m.name());
				out.writeByte(hash >> 16);
				out.writeShort(hash);
				out.writeByte(on ? 1 : 0);
				Setting.writeVarInt(out, changed.size());
				for (Setting<?> s : changed) {
					out.writeShort(settingHash(s.key()));
					ByteArrayOutputStream value = new ByteArrayOutputStream();
					try (DataOutputStream v = new DataOutputStream(value)) {
						s.writeCompact(v);
					}
					Setting.writeVarInt(out, value.size());
					value.writeTo(out);
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return ShareCode.encode(bytes.toByteArray());
	}
	public ImportResult importCode(String code) {
		if (code == null || code.isBlank()) {
			return ImportResult.failed("Nothing to import.");
		}
		Map<Module, Shared> shared;
		try {
			shared = code.contains(LEGACY_PREFIX) ? readLegacy(code) : readCode(code);
		} catch (IllegalArgumentException e) {
			return ImportResult.failed(e.getMessage());
		}
		for (Module m : modules.all()) {
			Shared entry = shared.get(m);
			for (Setting<?> s : m.allSettings()) {
				if (shareable(s)) {
					s.reset();
				}
			}
			if (entry != null) {
				entry.apply.forEach(Runnable::run);
			}
			if (!m.isAlwaysOn()) {
				boolean on = entry != null && entry.enabled;
				if (on || m.disableWarning() == null) {
					m.setEnabled(on);
				}
			}
		}
		markDirty();
		return new ImportResult(true, shared.size(), null);
	}
	public static boolean looksLikeCode(String text) {
		return text != null && (text.contains(LEGACY_PREFIX) || ShareCode.looksLike(text));
	}
	private static final class Shared {
		boolean enabled;
		final List<Runnable> apply = new ArrayList<>();
	}
	private Map<Module, Shared> readCode(String code) {
		if (!ShareCode.looksLike(code)) {
			throw new IllegalArgumentException("That isn't a Temple.nz config code.");
		}
		byte[] payload = ShareCode.decode(code);
		Map<Module, Shared> result = new LinkedHashMap<>();
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
			int version = in.readUnsignedByte();
			if (version != 2 && version != CODE_VERSION) {
				throw new IllegalArgumentException("That code is from a newer Temple.nz. Update to load it.");
			}
			while (in.available() > 0) {
				int hash = (in.readUnsignedByte() << 16) | in.readUnsignedShort();
				Module m = version == 2 && hash == moduleHash("Ambience") ? modules.byName("Air Effects")
						: version == 2 && hash == moduleHash("Animations") ? modules.byName("Hand Tweaks") : byHash.get(hash);
				boolean on = in.readUnsignedByte() != 0;
				int count = Setting.readVarInt(in);
				Shared entry = new Shared();
				entry.enabled = on;
				for (int i = 0; i < count; i++) {
					int settingHash = in.readUnsignedShort();
					int length = Setting.readVarInt(in);
					if (length < 0 || length > in.available()) {
						throw new IOException("bad length");
					}
					byte[] value = new byte[length];
					in.readFully(value);
					Setting<?> s = m == null ? null : settingByHash(m, settingHash);
					if (s != null && shareable(s)) {
						Setting<?> target = s;
						entry.apply.add(() -> {
							try {
								target.readCompact(new DataInputStream(new ByteArrayInputStream(value)));
							} catch (IOException ignored) {
							}
						});
					}
				}
				if (m != null) {
					result.put(m, entry);
				}
			}
		} catch (IOException e) {
			throw new IllegalArgumentException("The code is damaged or incomplete. Copy the whole thing again.");
		}
		return result;
	}
	private Map<Module, Shared> readLegacy(String code) {
		String trimmed = code.replaceAll("\\s+", "");
		trimmed = trimmed.substring(trimmed.indexOf(LEGACY_PREFIX) + LEGACY_PREFIX.length());
		JsonObject root;
		try {
			byte[] packed = Base64.getUrlDecoder().decode(trimmed);
			root = JsonParser.parseString(new String(ShareCode.inflate(packed), StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IllegalArgumentException | IllegalStateException | JsonParseException | DataFormatException e) {
			throw new IllegalArgumentException("The code is damaged or incomplete. Copy the whole thing again.");
		}
		if (!root.has("m") || !root.get("m").isJsonObject()) {
			throw new IllegalArgumentException("The code has no settings in it.");
		}
		JsonObject mods = root.getAsJsonObject("m");
		Map<Module, Shared> result = new LinkedHashMap<>();
		for (Module m : modules.all()) {
			String legacyName = switch (m.name()) {
				case "Air Effects" -> "Ambience";
				case "Hand Tweaks" -> "Animations";
				case "Ambience" -> "";
				default -> m.name();
			};
			JsonElement element = mods.get(legacyName);
			if (element == null || !element.isJsonObject()) {
				continue;
			}
			JsonObject o = element.getAsJsonObject();
			Shared entry = new Shared();
			entry.enabled = o.has("e") && o.get("e").isJsonPrimitive() && o.get("e").getAsBoolean();
			JsonObject settings = o.has("s") && o.get("s").isJsonObject() ? o.getAsJsonObject("s") : new JsonObject();
			for (Setting<?> s : m.allSettings()) {
				if (shareable(s) && settings.has(s.name())) {
					JsonElement value = settings.get(s.name());
					entry.apply.add(() -> s.fromJson(value));
				}
			}
			result.put(m, entry);
		}
		return result;
	}
	private static boolean shareable(Setting<?> s) {
		return !(s instanceof KeybindSetting) && !(s instanceof FileSetting);
	}
	private static Setting<?> settingByHash(Module m, int hash) {
		for (Setting<?> s : m.allSettings()) {
			if (settingHash(s.key()) == hash) {
				return s;
			}
		}
		return null;
	}
	private static int fnv(String text) {
		int h = 0x811C9DC5;
		for (byte b : text.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8)) {
			h ^= b & 0xFF;
			h *= 0x01000193;
		}
		return h;
	}
	static int moduleHash(String name) {
		return fnv(name) & 0xFFFFFF;
	}
	static int settingHash(String name) {
		int h = fnv(name);
		return (h ^ (h >>> 16)) & 0xFFFF;
	}
	private void indexHashes() {
		for (Module m : modules.all()) {
			Module clash = byHash.put(moduleHash(m.name()), m);
			if (clash != null) {
				throw new IllegalStateException("Share-code hash clash: " + clash.name() + " / " + m.name());
			}
			Map<Integer, String> seen = new HashMap<>();
			for (Setting<?> s : m.allSettings()) {
				String other = seen.put(settingHash(s.name()), s.name());
				if (other != null && !other.equalsIgnoreCase(s.name())) {
					throw new IllegalStateException("Share-code hash clash in " + m.name() + ": " + other + " / " + s.name());
				}
			}
		}
	}
	private void backUpBroken(Path file) {
		try {
			Files.move(file, dir.resolve("config.broken-" + clockMillis.getAsLong() + ".json"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ignored) {
		}
	}
}
