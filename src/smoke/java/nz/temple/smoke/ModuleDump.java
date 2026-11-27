package nz.temple.smoke;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class ModuleDump {
	private ModuleDump() {
	}
	static void write(Path out) throws Exception {
		Class<?> client = Class.forName("nz.temple.client.TempleNzClient");
		Object manager = client.getMethod("modules").invoke(null);
		@SuppressWarnings("unchecked")
		Iterable<nz.temple.module.Module> all = (Iterable<nz.temple.module.Module>) manager.getClass().getMethod("all").invoke(manager);
		JsonArray modules = new JsonArray();
		List<String> drifted = new java.util.ArrayList<>();
		for (nz.temple.module.Module m : all) {
			if (m instanceof nz.temple.module.LazyPayloadModule lazy && !lazy.load()) {
				throw new IllegalStateException("could not load " + m.name());
			}
			if (m instanceof nz.temple.module.LazyPayloadModule lazy && !m.description().equals(lazy.implementationIfLoaded().description())) {
				drifted.add(m.name() + " -> " + lazy.implementationIfLoaded().description());
			}
			JsonObject mo = new JsonObject();
			mo.addProperty("name", m.name());
			mo.addProperty("category", m.category().displayName());
			mo.addProperty("description", m.description());
			JsonArray groups = new JsonArray();
			List<? extends List<nz.temple.setting.Setting<?>>> list = m.groups();
			JsonObject last = null;
			for (int i = 0; i < list.size(); i++) {
				JsonArray settings = new JsonArray();
				for (nz.temple.setting.Setting<?> s : list.get(i)) {
					if (!s.isInternal()) {
						settings.add(setting(s));
					}
				}
				if (settings.isEmpty()) {
					continue;
				}
				String title = m.groupTitle(i);
				if (last != null && last.get("title").getAsString().equals(title)) {
					last.getAsJsonArray("settings").addAll(settings);
					continue;
				}
				JsonObject go = new JsonObject();
				go.addProperty("title", title);
				go.add("settings", settings);
				groups.add(go);
				last = go;
			}
			mo.add("groups", groups);
			modules.add(mo);
		}
		if (!drifted.isEmpty()) {
			throw new IllegalStateException("shell descriptions differ from the implementations': " + String.join(" | ", drifted));
		}
		Files.createDirectories(out.toAbsolutePath().getParent());
		Files.writeString(out, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(modules));
		System.out.println("TEMPLE_DUMP " + modules.size() + " modules -> " + out.toAbsolutePath());
	}
	private static JsonObject setting(nz.temple.setting.Setting<?> s) throws ReflectiveOperationException {
		JsonObject so = new JsonObject();
		so.addProperty("name", s.name());
		String type = s.getClass().getSimpleName().replace("Setting", "").toLowerCase(java.util.Locale.ROOT);
		so.addProperty("type", type);
		String description = s.description();
		if (description != null && !description.isBlank()) {
			so.addProperty("description", description);
		}
		if (s instanceof nz.temple.setting.NumberSetting n) {
			so.addProperty("min", n.min());
			so.addProperty("max", n.max());
			so.addProperty("default", (Double) n.defaultValue());
			Field unit = nz.temple.setting.NumberSetting.class.getDeclaredField("unit");
			unit.setAccessible(true);
			so.addProperty("unit", ((String) unit.get(n)).trim());
		} else if (s instanceof nz.temple.setting.ModeSetting mode) {
			JsonArray options = new JsonArray();
			mode.options().forEach(options::add);
			so.add("options", options);
			so.addProperty("default", mode.defaultValue());
		} else if (s instanceof nz.temple.setting.BoolSetting b) {
			so.addProperty("default", b.defaultValue());
		} else if (s instanceof nz.temple.setting.ColorSetting c) {
			so.addProperty("default", String.format("#%06X", c.defaultValue() & 0xFFFFFF));
		}
		return so;
	}
	@SuppressWarnings("unused")
	private static Object call(Object target, String name) throws ReflectiveOperationException {
		Method m = target.getClass().getMethod(name);
		return m.invoke(target);
	}
}
