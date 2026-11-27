package nz.temple.client.modules;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.KeybindSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;
import nz.temple.setting.Setting;
import nz.temple.setting.TextSetting;

/**
 * Base class for modules whose settings are declared as data instead of in code. Modules read them with a string-keyed
 * {@code getSetting(key, default)}; every value is a real Temple {@link Setting} built from
 * {@code assets/templenz/module_settings.json}. The modules therefore show up in Temple's own GUI with Temple's widgets and
 * save through Temple's config. Range ("minmax") settings become two number settings keyed {@code Name_lo} / {@code Name_hi}.
 */
public abstract class SchemaModule extends Module {
	private static JsonObject schema;
	private static volatile int revision = 1;
	private final String sourceCategory;
	private final Map<String, Setting<?>> byKey = new HashMap<>();
	private final Map<String, Map<BoolSetting, String>> multiSelects = new HashMap<>();
	private final Map<String, Map<String, String>> modeValues = new HashMap<>();
	private final Map<String, Double> scales = new HashMap<>();
	private final Map<String, Object> fixedValues = new HashMap<>();

	protected SchemaModule(String name, String category) {
		this(name, category, false);
	}

	protected SchemaModule(String name, String category, boolean alwaysOn) {
		super(name, templeCategory(name, category), description(name), alwaysOn);
		this.sourceCategory = category;
		buildSettings(name);
		addChangeListener(() -> revision++);
		SchemaModules.register(this);
	}

	private static Category templeCategory(String name, String category) {
		Category listed = SchemaModules.CATEGORY.get(name);
		if (listed != null) {
			return listed;
		}
		return switch (category.toLowerCase(Locale.ROOT)) {
			case "combat" -> Category.COMBAT;
			case "movement" -> Category.MOVEMENT;
			case "client" -> Category.CLIENT;
			case "utility" -> Category.PLAYER;
			default -> Category.COMBAT;
		};
	}

	private static synchronized JsonObject schema() {
		if (schema == null) {
			try (InputStream in = SchemaModule.class.getResourceAsStream("/assets/templenz/module_settings.json")) {
				schema = in == null ? new JsonObject()
						: JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			} catch (Exception e) {
				schema = new JsonObject();
			}
		}
		return schema;
	}

	private static String description(String name) {
		JsonObject m = schema().getAsJsonObject(name);
		return m != null && m.has("description") ? m.get("description").getAsString() : "";
	}

	private void buildSettings(String name) {
		JsonObject m = schema().getAsJsonObject(name);
		if (m == null || !m.has("settings")) {
			return;
		}
		List<Object[]> conditions = new ArrayList<>();
		String currentGroup = null;
		for (JsonElement el : m.getAsJsonArray("settings")) {
			JsonObject def = el.getAsJsonObject();
			String key = def.get("key").getAsString();
			String type = def.get("type").getAsString();
			if (def.has("fixed") && def.get("fixed").getAsBoolean()) {
				lock(key, type, def);
				continue;
			}
			String label = str(def, "label", key);
			String group = str(def, "group", "General");
			if (!group.equals(currentGroup)) {
				group(group);
				currentGroup = group;
			}
			List<Setting<?>> made = new ArrayList<>();
			switch (type) {
				case "check" -> made.add(put(key, new BoolSetting(label, def.get("default").getAsBoolean())));
				case "range", "minmax" -> {
					double scale = num(def, "scale", 1);
					double min = def.get("min").getAsDouble();
					double max = def.get("max").getAsDouble();
					boolean whole = whole(min) && whole(max) && whole(num(def, "default", num(def, "low", 0)))
							&& whole(num(def, "high", 0));
					double step = num(def, "step", whole ? 1 : type.equals("range") ? 0.1 : 0.01) * scale;
					String unit = str(def, "unit", "");
					if (scale != 1) {
						scales.put(key, scale);
					}
					if (type.equals("range")) {
						made.add(put(key, new NumberSetting(label, def.get("default").getAsDouble() * scale, min * scale, max * scale,
								step, unit)));
					} else {
						scales.put(key + "_lo", scale);
						scales.put(key + "_hi", scale);
						made.add(put(key + "_lo", new NumberSetting("Min " + lower(label), def.get("low").getAsDouble() * scale,
								min * scale, max * scale, step, unit)));
						made.add(put(key + "_hi", new NumberSetting("Max " + lower(label), def.get("high").getAsDouble() * scale,
								min * scale, max * scale, step, unit)));
					}
				}
				case "select" -> {
					Map<String, String> shownToInternal = new LinkedHashMap<>();
					JsonObject labels = def.has("optionLabels") ? def.getAsJsonObject("optionLabels") : new JsonObject();
					for (JsonElement o : def.getAsJsonArray("options")) {
						String internal = o.getAsString();
						shownToInternal.put(labels.has(internal) ? labels.get(internal).getAsString() : internal, internal);
					}
					String dflt = str(def, "default", null);
					String shownDefault = shownToInternal.keySet().iterator().next();
					for (Map.Entry<String, String> e : shownToInternal.entrySet()) {
						if (e.getValue().equals(dflt)) {
							shownDefault = e.getKey();
						}
					}
					modeValues.put(key, shownToInternal);
					made.add(put(key, new ModeSetting(label, new ArrayList<>(shownToInternal.keySet()), shownDefault)));
				}
				case "color" -> {
					ColorSetting c = new ColorSetting(label, parseColor(str(def, "default", "#FFFFFF")));
					made.add(put(key, c));
				}
				case "keybind" -> made.add(put(key, new KeybindSetting(label, KeybindSetting.NONE)));
				case "multiselect" -> {
					List<String> dflt = new ArrayList<>();
					if (def.has("default") && def.get("default").isJsonArray()) {
						def.getAsJsonArray("default").forEach(o -> dflt.add(o.getAsString()));
					}
					Map<BoolSetting, String> parts = new LinkedHashMap<>();
					for (JsonElement o : def.getAsJsonArray("options")) {
						String option = o.getAsString();
						BoolSetting b = put(key + ": " + option, new BoolSetting(label + " " + option.toLowerCase(Locale.ROOT),
								dflt.contains(option)));
						parts.put(b, option);
						made.add(b);
					}
					multiSelects.put(key, parts);
				}
				case "curve" -> made.add(put(key, new TextSetting(label, str(def, "default", ""), 200, "25:16, 50:50, 75:84")));
				case "itempicker", "blockpicker" -> made.add(put(key, new TextSetting(label, "", 2000,
						"minecraft:obsidian, minecraft:cobweb")));
				case "text" -> made.add(put(key, new TextSetting(label, str(def, "default", ""), 200, "")));
				default -> {
				}
			}
			String info = str(def, "info", null);
			for (Setting<?> made1 : made) {
				if (info != null) {
					made1.describe(info);
				}
				if (def.has("hidden") && def.get("hidden").getAsBoolean()) {
					made1.visibleWhen(() -> false);
				} else {
					conditions.add(new Object[]{made1, def});
				}
			}
		}
		for (Object[] c : conditions) {
			BooleanSupplier when = condition((JsonObject) c[1]);
			if (when != null) {
				((Setting<?>) c[0]).visibleWhen(when);
			}
		}
	}

	/** A setting marked "fixed" in the schema: not shown in the menu, always reads its default. */
	private void lock(String key, String type, JsonObject def) {
		switch (type) {
			case "check" -> fixedValues.put(key, def.get("default").getAsBoolean());
			case "range" -> fixedValues.put(key, def.get("default").getAsDouble());
			case "minmax" -> {
				fixedValues.put(key + "_lo", def.get("low").getAsDouble());
				fixedValues.put(key + "_hi", def.get("high").getAsDouble());
			}
			default -> {
				if (def.has("default") && def.get("default").isJsonPrimitive()) {
					fixedValues.put(key, def.get("default").getAsString());
				}
			}
		}
	}

	private static String str(JsonObject o, String key, String fallback) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
	}

	private static double num(JsonObject o, String key, double fallback) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsDouble() : fallback;
	}

	private static boolean whole(double d) {
		return d == Math.rint(d);
	}

	private static String lower(String label) {
		return label.isEmpty() ? label : Character.toLowerCase(label.charAt(0)) + label.substring(1);
	}

	private BooleanSupplier condition(JsonObject opts) {
		List<BooleanSupplier> all = new ArrayList<>();
		if (opts.has("showif")) {
			JsonArray a = opts.getAsJsonArray("showif");
			all.add(equalsCondition(a.get(0).getAsString(), a.get(1)));
		}
		if (opts.has("showif2")) {
			JsonArray a = opts.getAsJsonArray("showif2");
			for (int i = 0; i + 1 < a.size(); i += 2) {
				all.add(equalsCondition(a.get(i).getAsString(), a.get(i + 1)));
			}
		}
		if (opts.has("showifOr")) {
			JsonArray a = opts.getAsJsonArray("showifOr");
			String key = a.get(0).getAsString();
			List<BooleanSupplier> any = new ArrayList<>();
			a.get(1).getAsJsonArray().forEach(v -> any.add(equalsCondition(key, v)));
			all.add(() -> any.stream().anyMatch(BooleanSupplier::getAsBoolean));
		}
		return all.isEmpty() ? null : () -> all.stream().allMatch(BooleanSupplier::getAsBoolean);
	}

	/** A setting's value as the ported code sees it (mode settings report their internal option name). */
	private Object internalValue(String key) {
		if (fixedValues.containsKey(key)) {
			return fixedValues.get(key);
		}
		Setting<?> s = byKey.get(key);
		if (s == null) {
			return null;
		}
		Map<String, String> modes = modeValues.get(key);
		return modes != null ? modes.getOrDefault(String.valueOf(s.get()), String.valueOf(s.get())) : s.get();
	}

	private BooleanSupplier equalsCondition(String key, JsonElement value) {
		return () -> {
			Object v = internalValue(key);
			if (v == null) {
				return true;
			}
			if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
				return v instanceof Boolean b && b == value.getAsBoolean();
			}
			return value.isJsonPrimitive() && String.valueOf(v).equals(value.getAsString());
		};
	}

	private <S extends Setting<?>> S put(String key, S setting) {
		byKey.put(key, setting);
		return add(setting);
	}

	private static int parseColor(String hex) {
		try {
			String h = hex.startsWith("#") ? hex.substring(1) : hex;
			long v = Long.parseLong(h, 16);
			return h.length() <= 6 ? (int) (0xFF000000L | v) : (int) v;
		} catch (RuntimeException e) {
			return 0xFFFFFFFF;
		}
	}

	/** Bumped whenever any ported module's setting changes; lets modules that cache settings reload them. */
	public static int settingsRevision() {
		return revision;
	}

	public void onTick() {
	}

	@Override
	public void onEnable() {
	}

	@Override
	public void onDisable() {
	}

	public String getName() {
		return name();
	}

	public String getCategory() {
		return sourceCategory;
	}

	@SuppressWarnings("unchecked")
	protected <T> T getSetting(String key, T defaultValue) {
		Map<BoolSetting, String> multi = multiSelects.get(key);
		if (multi != null) {
			List<String> on = new ArrayList<>();
			multi.forEach((b, option) -> {
				if (b.get()) {
					on.add(option);
				}
			});
			return (T) on;
		}
		Setting<?> s = byKey.get(key);
		if (s == null && !fixedValues.containsKey(key)) {
			return defaultValue;
		}
		Object v = internalValue(key);
		Double scale = scales.get(key);
		if (scale != null && v instanceof Number n) {
			v = n.doubleValue() / scale;
		}
		if (s instanceof KeybindSetting kb) {
			return (T) (kb.isBound() ? "TK:" + kb.get() : "NONE");
		}
		if (s instanceof ColorSetting c) {
			v = String.format("#%06X", c.get() & 0xFFFFFF);
		}
		if (s instanceof TextSetting && defaultValue instanceof List<?>) {
			List<String> out = new ArrayList<>();
			for (String part : String.valueOf(v).split(",")) {
				if (!part.isBlank()) {
					out.add(part.trim());
				}
			}
			return (T) out;
		}
		if (defaultValue == null) {
			return (T) v;
		}
		if (v instanceof Number n) {
			if (defaultValue instanceof Integer) {
				return (T) Integer.valueOf((int) Math.round(n.doubleValue()));
			}
			if (defaultValue instanceof Long) {
				return (T) Long.valueOf(Math.round(n.doubleValue()));
			}
			if (defaultValue instanceof Float) {
				return (T) Float.valueOf(n.floatValue());
			}
			if (defaultValue instanceof Double) {
				return (T) Double.valueOf(n.doubleValue());
			}
			if (defaultValue instanceof Boolean) {
				return (T) Boolean.valueOf(n.doubleValue() != 0);
			}
		}
		if (defaultValue instanceof String) {
			return (T) String.valueOf(v);
		}
		return defaultValue.getClass().isInstance(v) ? (T) v : defaultValue;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	protected void putSetting(String key, Object value) {
		Setting s = byKey.get(key);
		if (s == null || value == null) {
			return;
		}
		if (s instanceof NumberSetting && value instanceof Number n) {
			s.set(n.doubleValue() * scales.getOrDefault(key, 1.0));
		} else if (modeValues.containsKey(key)) {
			modeValues.get(key).forEach((shown, internal) -> {
				if (internal.equals(String.valueOf(value))) {
					s.set(shown);
				}
			});
		} else if (s.get() != null && s.get().getClass().isInstance(value)) {
			s.set(value);
		} else {
			s.trySet(String.valueOf(value));
		}
	}
}
