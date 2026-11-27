package nz.temple.module;

import nz.temple.event.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class ModuleManager {
	private final EventBus bus;
	private final List<Module> modules = new ArrayList<>();
	private final List<Consumer<Module>> toggleListeners = new ArrayList<>();
	private final List<BiConsumer<Module, Throwable>> errorListeners = new ArrayList<>();
	private final Map<Class<?>, Object> byType = new ConcurrentHashMap<>();
	private static final Object NONE = new Object();
	private volatile List<Module> shownList;
	private volatile long shownAt;
	public ModuleManager(EventBus bus) {
		this.bus = bus;
		bus.setErrorHandler((owner, error) -> {
			if (owner instanceof Module module) {
				module.setEnabled(false);
				errorListeners.forEach(l -> l.accept(module, error));
			}
		});
	}
	public EventBus bus() {
		return bus;
	}
	public <M extends Module> M register(M module) {
		if (byName(module.name()) != null) {
			throw new IllegalArgumentException("Duplicate module name: " + module.name());
		}
		modules.add(module);
		byType.clear();
		shownList = null;
		module.attach(bus);
		module.addToggleListener(m -> {
			shownList = null;
			toggleListeners.forEach(l -> l.accept(m));
		});
		if (module.visible() != null) {
			module.visible().addListener(() -> shownList = null);
		}
		return module;
	}
	public List<Module> all() {
		return Collections.unmodifiableList(modules);
	}
	public Module byName(String name) {
		for (Module m : modules) {
			if (m.name().equalsIgnoreCase(name)) {
				return m;
			}
		}
		return null;
	}
	public <M extends Module> M get(Class<M> type) {
		Object hit = byType.get(type);
		if (hit == null) {
			hit = NONE;
			for (Module m : modules) {
				if (type.isInstance(m)) {
					hit = m;
					break;
				}
			}
			byType.put(type, hit);
		}
		return hit == NONE ? null : type.cast(hit);
	}
	public List<Module> inCategory(Category category) {
		return modules.stream().filter(m -> m.category() == category && m.listedInCategory()).toList();
	}
	public List<Module> search(String term) {
		String t = term.trim().toLowerCase(Locale.ROOT);
		if (t.isEmpty()) {
			return List.of();
		}
		return modules.stream()
				.filter(m -> m.name().toLowerCase(Locale.ROOT).contains(t))
				.sorted(Comparator.<Module>comparingInt(m -> m.name().toLowerCase(Locale.ROOT).indexOf(t))
						.thenComparing(Module::name))
				.toList();
	}
	public record SettingMatch(Module module, nz.temple.setting.Setting<?> setting) {
	}
	public List<SettingMatch> searchSettings(String term) {
		String t = term.trim().toLowerCase(Locale.ROOT);
		if (t.isEmpty()) {
			return List.of();
		}
		List<SettingMatch> out = new ArrayList<>();
		for (Module m : modules) {
			if (m.name().toLowerCase(Locale.ROOT).contains(t)) {
				continue;
			}
			for (nz.temple.setting.Setting<?> s : m.allSettings()) {
				if (s.isVisible() && s.name().toLowerCase(Locale.ROOT).contains(t)) {
					out.add(new SettingMatch(m, s));
				}
			}
		}
		out.sort(Comparator.<SettingMatch>comparingInt(r -> r.setting().name().toLowerCase(Locale.ROOT).indexOf(t))
				.thenComparing(r -> r.setting().name())
				.thenComparing(r -> r.module().name()));
		return out;
	}
	public List<Module> moduleList() {
		List<Module> list = shownList;
		long now = System.nanoTime();
		if (list == null || now - shownAt > 1_000_000_000L) {
			shownAt = now;
			list = modules.stream()
					.filter(m -> m.isEnabled() && !m.isAlwaysOn() && m.visible().get())
					.sorted(Comparator.<Module>comparingInt(m -> m.name().length()).reversed().thenComparing(Module::name))
					.toList();
			shownList = list;
		}
		return list;
	}
	public void addToggleListener(Consumer<Module> listener) {
		toggleListeners.add(listener);
	}
	public void removeToggleListener(Consumer<Module> listener) {
		toggleListeners.remove(listener);
	}
	public void addErrorListener(BiConsumer<Module, Throwable> listener) {
		errorListeners.add(listener);
	}
}
