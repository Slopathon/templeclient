package nz.temple.module;

import com.google.gson.JsonObject;
import nz.temple.event.EventBus;
import nz.temple.setting.Setting;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class LazyPayloadModule extends Module {
	private final Supplier<? extends Module> implementationFactory;
	private final Runnable prefetch;
	private Module implementation;
	private EventBus bus;
	private JsonObject pendingSettings;
	private boolean loading;
	private boolean enableWanted;
	private static volatile BiConsumer<Module, Throwable> loadFailureHandler = (module, error) -> {
	};
	private static volatile Executor background;
	private static volatile Executor foreground;
	public LazyPayloadModule(PayloadModuleDescriptor descriptor, Supplier<? extends Module> implementationFactory) {
		this(descriptor, implementationFactory, null);
	}

	public LazyPayloadModule(PayloadModuleDescriptor descriptor, Supplier<? extends Module> implementationFactory,
			Runnable prefetch) {
		super(descriptor.name(), descriptor.category(), descriptor.description(), descriptor.alwaysOn());
		this.implementationFactory = Objects.requireNonNull(implementationFactory, "implementationFactory");
		this.prefetch = prefetch;
	}
	public static void asyncLoader(Executor background, Executor foreground) {
		LazyPayloadModule.background = background;
		LazyPayloadModule.foreground = foreground;
	}
	@Override
	public void attach(EventBus bus) {
		super.attach(bus);
		this.bus = bus;
		if (isAlwaysOn()) {
			implementation().setEnabled(true);
		} else if (implementation != null) {
			implementation.attach(bus);
		}
	}
	public static void onLoadFailure(BiConsumer<Module, Throwable> handler) {
		loadFailureHandler = Objects.requireNonNull(handler, "handler");
	}
	public boolean load() {
		if (implementation != null) {
			return true;
		}
		try {
			implementation();
			return true;
		} catch (RuntimeException | LinkageError e) {
			loadFailureHandler.accept(this, e);
			return false;
		}
	}
	@Override
	public void setEnabled(boolean on) {
		Executor bg = background;
		Executor fg = foreground;
		if (on && implementation == null && bg != null && fg != null) {
			enableWanted = true;
			if (loading) {
				return;
			}
			loading = true;
			bg.execute(() -> {
				Throwable error = null;
				try {
					if (prefetch != null) {
						prefetch.run();
					}
				} catch (Throwable t) {
					error = t;
				}
				Throwable captured = error;
				fg.execute(() -> finishAsyncEnable(captured));
			});
			return;
		}
		if (on && implementation == null) {
			if (!load()) {
				return;
			}
			super.setEnabled(true);
			implementation.setEnabled(true);
			return;
		}
		if (on && !isEnabled()) {
			super.setEnabled(true);
			implementation.setEnabled(true);
			return;
		}
		if (!on) {
			enableWanted = false;
		}
		if (!on && isEnabled() && implementation != null) {
			implementation.setEnabled(false);
		}
		super.setEnabled(on);
	}
	private void finishAsyncEnable(Throwable error) {
		loading = false;
		if (!enableWanted) {
			return;
		}
		if (error != null) {
			loadFailureHandler.accept(this, error);
			return;
		}

		if (!load()) {
			return;
		}
		super.setEnabled(true);
		implementation.setEnabled(true);
	}
	public Module implementationIfLoaded() {
		return implementation;
	}
	public <T> T implementationIfLoaded(Class<T> type) {
		return type.isInstance(implementation) ? type.cast(implementation) : null;
	}

	public void restoreSettings(JsonObject settings) {
		pendingSettings = settings == null ? null : settings.deepCopy();
		if (implementation != null) {
			applySettings(implementation, pendingSettings);
			pendingSettings = null;
		}
	}
	public JsonObject savedSettings() {
		if (implementation == null) {
			return pendingSettings == null ? new JsonObject() : pendingSettings.deepCopy();
		}
		JsonObject settings = new JsonObject();
		for (Setting<?> setting : implementation.allSettings()) {
			settings.add(setting.key(), setting.toJson());
		}
		return settings;
	}
	@Override
	public List<List<Setting<?>>> groups() {
		return implementation == null ? List.of() : implementation.groups();
	}
	@Override
	public String groupTitle(int index) {
		return implementation == null ? super.groupTitle(index) : implementation.groupTitle(index);
	}
	@Override
	public List<Setting<?>> allSettings() {
		return implementation == null ? List.of() : implementation.allSettings();
	}
	@Override
	public Setting<?> setting(String settingName) {
		return implementation == null ? null : implementation.setting(settingName);
	}
	private Module implementation() {
		if (implementation != null) {
			return implementation;
		}
		Module created = Objects.requireNonNull(implementationFactory.get(), "payload implementation");
		if (!name().equalsIgnoreCase(created.name())) {
			throw new IllegalStateException("payload module name mismatch: expected " + name() + ", got " + created.name());
		}
		implementation = created;
		created.addChangeListener(this::fireChanged);
		if (bus != null) {
			created.attach(bus);
		}
		applySettings(created, pendingSettings);
		pendingSettings = null;
		return created;
	}
	private static void applySettings(Module module, JsonObject settings) {
		if (settings == null) {
			return;
		}
		for (Setting<?> setting : module.allSettings()) {
			if (settings.has(setting.key())) {
				setting.fromJson(settings.get(setting.key()));
			} else if (!setting.key().equals(setting.name()) && settings.has(setting.name())) {
				setting.fromJson(settings.get(setting.name()));
			}
		}
	}
	void runDeferredHooks() {
		if (implementation != null) {
			Module.runDeferredHooks(List.of(implementation));
		}
	}
}
