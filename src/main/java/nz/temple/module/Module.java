package nz.temple.module;

import nz.temple.event.EventBus;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.KeybindSetting;
import nz.temple.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public abstract class Module {
	private final String name;
	private Category category;
	private final String description;
	private final boolean alwaysOn;
	private final List<List<Setting<?>>> groups = new ArrayList<>();
	private final List<String> groupTitles = new ArrayList<>();
	private final List<EventBus.Subscription<?>> subscriptions = new ArrayList<>();
	private final List<Runnable> changeListeners = new ArrayList<>();
	private final List<Consumer<Module>> toggleListeners = new ArrayList<>();
	private final KeybindSetting keybind = new KeybindSetting("Keybind", KeybindSetting.NONE);
	private final BoolSetting disableOnKeyRelease = new BoolSetting("Disable on key release", false);
	private final BoolSetting visible = new BoolSetting("Show in module list", true);
	private EventBus bus;
	private boolean enabled;
	private Module legacy;
	private java.util.function.BooleanSupplier useLegacy = () -> false;
	private List<Setting<?>> legacyHeader = List.of();
	private java.util.Set<String> legacyHidden = java.util.Set.of();
	private boolean legacyAlongside;
	private boolean subscribed;

	protected Module(String name, Category category, String description) {
		this(name, category, description, false);
	}

	protected Module(String name, Category category, String description, boolean alwaysOn) {
		this.name = name;
		this.category = category;
		this.description = description;
		this.alwaysOn = alwaysOn;
		this.enabled = alwaysOn;
		keybind.describe("Press this key in game (with no menu open) to toggle the module. Esc or Backspace clears it.");
		disableOnKeyRelease.describe("Only enabled while the key is held.")
				.visibleWhen(keybind::isBound);
		visible.describe("Show this module in the HUD module list while it is on.");
		keybind.addListener(this::fireChanged);
		disableOnKeyRelease.addListener(this::fireChanged);
		visible.addListener(this::fireChanged);
	}

	protected final void group() {
		group(null);
	}

	protected final void group(String title) {
		if (groups.isEmpty() || !groups.get(groups.size() - 1).isEmpty()) {
			groups.add(new ArrayList<>());
			groupTitles.add(title);
		} else {
			groupTitles.set(groups.size() - 1, title);
		}
	}

	protected final <S extends Setting<?>> S add(S setting) {
		if (groups.isEmpty()) {
			groups.add(new ArrayList<>());
			groupTitles.add(null);
		}
		groups.get(groups.size() - 1).add(setting);
		setting.addListener(this::fireChanged);
		return setting;
	}

	protected final void hideWhenLegit(nz.temple.setting.BoolSetting legit, Setting<?>... settings) {
		for (Setting<?> s : settings) {
			s.andVisibleWhen(() -> !legit.get());
		}
	}

	public nz.temple.setting.BoolSetting legitSetting() {
		return setting("Legit") instanceof nz.temple.setting.BoolSetting b ? b : null;
	}

	protected final <E> void on(Class<E> type, Consumer<? super E> handler) {
		subscriptions.add(new EventBus.Subscription<>(this, type, handler));
	}

	public void attach(EventBus bus) {
		this.bus = bus;
		if (legacy != null) {
			legacy.attach(bus);
		}
		syncSubscriptions();
	}

	private void syncSubscriptions() {
		boolean want = enabled && !legacyActive();
		if (bus == null || want == subscribed) {
			return;
		}
		subscribed = want;
		if (want) {
			subscriptions.forEach(bus::subscribe);
		} else {
			subscriptions.forEach(bus::unsubscribe);
		}
	}

	protected final void legacy(Module original, java.util.function.BooleanSupplier useOriginal, List<Setting<?>> header,
			String... hidden) {
		legacy = original;
		useLegacy = useOriginal;
		legacyHeader = header;
		legacyHidden = java.util.Set.of(hidden);
		for (Setting<?> s : original.allSettings()) {
			s.keyAs("Legacy: " + s.name());
		}
		original.addChangeListener(this::fireChanged);
		addChangeListener(this::refreshLegacy);
	}
	public final boolean legacyActive() {
		return legacy != null && useLegacy.getAsBoolean();
	}
	public final Module legacyModule() {
		return legacy;
	}
	protected final void runLegacyAlongside(boolean on) {
		if (legacyAlongside != on) {
			legacyAlongside = on;
			refreshLegacy();
		}
	}

	protected void syncToLegacy() {
		for (Setting<?> mine : ownSettings()) {
			Setting<?> theirs = legacyTwin(mine);
			if (theirs != null) {
				theirs.mirror(mine);
			}
		}
	}
	private Setting<?> legacyTwin(Setting<?> mine) {
		for (Setting<?> theirs : legacy.allSettings()) {
			if (theirs.name().equals(mine.name()) && theirs.getClass() == mine.getClass()) {
				return theirs;
			}
		}
		return null;
	}
	private List<Setting<?>> ownSettings() {
		return groups.stream().flatMap(List::stream).toList();
	}
	private void refreshLegacy() {
		if (legacy == null) {
			return;
		}
		boolean run = enabled && (legacyAlongside || legacyActive());
		if (run) {
			syncToLegacy();
		}
		if (legacy.enabled != run) {
			legacy.setEnabled(run);
		}
		syncSubscriptions();
	}

	public void setEnabled(boolean on) {
		if (alwaysOn || on == enabled) {
			return;
		}
		enabled = on;
		syncSubscriptions();
		refreshLegacy();
		if (on) {
			if (hooksDeferred) {
				pendingEnable = true;
			} else {
				onEnable();
			}
		} else if (pendingEnable) {
			pendingEnable = false;
		} else {
			onDisable();
		}
		toggleListeners.forEach(l -> l.accept(this));
		fireChanged();
	}
	private static boolean hooksDeferred;
	private boolean pendingEnable;
	public static void deferHooks() {
		hooksDeferred = true;
	}
	public static void runDeferredHooks(Iterable<Module> modules) {
		hooksDeferred = false;
		for (Module m : modules) {
			for (Module each : m.legacy == null ? List.of(m) : List.of(m, m.legacy)) {
				if (each.pendingEnable) {
					each.pendingEnable = false;
					if (each.enabled) {
						each.onEnable();
					}
				}
			}
			if (m instanceof LazyPayloadModule lazy) {
				lazy.runDeferredHooks();
			}
		}
	}

	public boolean ownsKey() {
		return false;
	}
	public void keyPressed() {
	}
	public void keyReleased() {
	}
	public final void toggle() {
		setEnabled(!enabled);
	}
	protected void onEnable() {
	}
	protected void onDisable() {
	}
	protected final void fireChanged() {
		changeListeners.forEach(Runnable::run);
	}
	public void addChangeListener(Runnable listener) {
		changeListeners.add(listener);
	}
	public void addToggleListener(Consumer<Module> listener) {
		toggleListeners.add(listener);
	}
	public String name() {
		return name;
	}

	public String displayName() {
		return name;
	}
	public String info() {
		return null;
	}
	/** Moves the module to another tab (used for the HvH tab, which is built from a list of module names). */
	public void moveTo(Category category) {
		this.category = java.util.Objects.requireNonNull(category);
	}

	public Category category() {
		return category;
	}
	public String description() {
		return description;
	}
	public String disableWarning() {
		return null;
	}
	public boolean isEnabled() {
		return enabled;
	}
	public boolean isAlwaysOn() {
		return alwaysOn;
	}
	public boolean listedInCategory() {
		return true;
	}
	public KeybindSetting keybind() {
		return keybind;
	}
	public BoolSetting disableOnKeyRelease() {
		return disableOnKeyRelease;
	}
	public BoolSetting visible() {
		return visible;
	}

	public String groupTitle(int index) {
		if (legacyActive()) {
			List<String> titles = new ArrayList<>();
			composedLegacy(titles);
			return index < titles.size() ? titles.get(index) : "Options";
		}
		String given = index < groupTitles.size() ? groupTitles.get(index) : null;
		if (given != null) {
			return given;
		}
		List<Setting<?>> group = groups.get(index);
		int colours = 0;
		int targets = 0;
		int timing = 0;
		int look = 0;
		int movement = 0;
		for (Setting<?> s : group) {
			String n = s.name().toLowerCase(java.util.Locale.ROOT);
			if (s instanceof nz.temple.setting.ColorSetting || n.contains("colour") || n.contains("color") || n.contains("hue")
					|| n.contains("theme") || n.contains("opacity") || n.contains("brightness")) {
				colours++;
			}
			if (s instanceof nz.temple.setting.EntitySelection sel && !sel.isBlocks() || n.equals("players") || n.equals("hostiles") || n.equals("animals")
					|| n.startsWith("ignore") || n.contains("range") || n.contains("target") || n.equals("priority") || n.equals("fov")
					|| n.contains("friends") || n.equals("self") || n.equals("who")) {
				targets++;
			}
			if (n.contains("cps") || n.contains("delay") || n.contains("cooldown") || n.contains("jitter") || n.contains("timing")
					|| n.contains("interval") || n.contains("duration") || n.contains("length") || n.contains("time")) {
				timing++;
			}
			if (n.contains("style") || n.contains("material") || n.contains("size") || n.contains("shape") || n.contains("width")
					|| n.contains("glow") || n.contains("outline") || n.contains("fill") || n.contains("look") || n.contains("mode")) {
				look++;
			}
			if (n.contains("speed") || n.contains("jump") || n.contains("sprint") || n.contains("strafe") || n.contains("rotation")
					|| n.contains("radius") || n.contains("pace") || n.contains("height")) {
				movement++;
			}
		}
		int size = Math.max(1, group.size());
		if (colours * 2 >= size) {
			return "Colours";
		}
		if (targets * 2 >= size) {
			return "Targets";
		}
		if (timing * 2 >= size) {
			return "Timing";
		}
		if (index == 0) {
			return "General";
		}
		if (look * 2 >= size) {
			return "Appearance";
		}
		if (movement * 2 >= size) {
			return "Movement";
		}
		return "Options";
	}
	public List<List<Setting<?>>> groups() {
		return legacyActive() ? composedLegacy(null) : Collections.unmodifiableList(groups);
	}
	private List<List<Setting<?>>> composedLegacy(List<String> titles) {
		List<List<Setting<?>>> out = new ArrayList<>();
		if (!legacyHeader.isEmpty()) {
			out.add(legacyHeader);
			if (titles != null) {
				titles.add("General");
			}
		}
		List<List<Setting<?>>> theirs = legacy.groups();
		for (int i = 0; i < theirs.size(); i++) {
			List<Setting<?>> group = new ArrayList<>();
			for (Setting<?> s : theirs.get(i)) {
				if (legacyHidden.contains(s.name())) {
					continue;
				}
				Setting<?> shown = s;
				for (Setting<?> mine : ownSettings()) {
					if (mine.name().equals(s.name()) && mine.getClass() == s.getClass()) {
						shown = mine;
						break;
					}
				}
				if (!legacyHeader.contains(shown)) {
					group.add(shown);
				}
			}
			if (!group.isEmpty()) {
				out.add(group);
				if (titles != null) {
					titles.add(legacy.groupTitle(i));
				}
			}
		}
		return out;
	}
	public List<Setting<?>> allSettings() {
		List<Setting<?>> own = ownSettings();
		if (legacy == null) {
			return own;
		}
		List<Setting<?>> all = new ArrayList<>(own);
		all.addAll(legacy.allSettings());
		return all;
	}
	public Setting<?> setting(String settingName) {
		for (List<Setting<?>> group : groups()) {
			for (Setting<?> s : group) {
				if (s.name().equalsIgnoreCase(settingName)) {
					return s;
				}
			}
		}
		return allSettings().stream().filter(s -> s.name().equalsIgnoreCase(settingName)).findFirst().orElse(null);
	}
}
