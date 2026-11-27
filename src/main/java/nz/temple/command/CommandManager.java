package nz.temple.command;

import nz.temple.module.Module;
import nz.temple.module.ModuleManager;
import nz.temple.setting.Setting;
import nz.temple.social.FriendManager;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class CommandManager {
	private final ModuleManager modules;
	private final FriendManager friends;
	private final Consumer<String> feedback;
	private String prefix = ".";
	public CommandManager(ModuleManager modules, FriendManager friends, Consumer<String> feedback) {
		this.modules = modules;
		this.friends = friends;
		this.feedback = feedback;
	}
	@FunctionalInterface
	public interface Extra {
		void run(String[] parts, Consumer<String> feedback);
	}
	private final java.util.Map<String, Extra> extras = new java.util.LinkedHashMap<>();
	private final java.util.List<String> extraHelp = new java.util.ArrayList<>();

	public void register(String help, Extra command, String... names) {
		for (String name : names) {
			extras.put(name.toLowerCase(Locale.ROOT), command);
		}
		extraHelp.add(help);
	}
	public String prefix() {
		return prefix;
	}
	public void setPrefix(String prefix) {
		if (!prefix.isBlank()) {
			this.prefix = prefix;
		}
	}
	public boolean handle(String message) {
		if (!message.startsWith(prefix)) {
			return false;
		}
		String body = message.substring(prefix.length()).trim();
		if (body.isEmpty()) {
			return false;
		}
		String[] parts = body.split("\\s+");
		String cmd = parts[0].toLowerCase(Locale.ROOT);
		try {
			run(cmd, parts);
		} catch (RuntimeException e) {
			feedback.accept("Error: " + e.getMessage());
		}
		return true;
	}
	private void run(String cmd, String[] parts) {
		switch (cmd) {
			case "help", "?" -> help();
			case "toggle", "t" -> toggle(parts);
			case "list" -> list();
			case "bind" -> bind(parts);
			case "unbind" -> unbind(parts);
			case "set" -> set(parts);
			case "friend", "f" -> friend(parts);
			case "defaults", "reset" -> resetAll();
			case "prefix" -> {
				if (parts.length >= 2) {
					setPrefix(parts[1]);
					feedback.accept("Prefix set to " + prefix);
				}
			}
			default -> {
				Extra extra = extras.get(cmd);
				if (extra != null) {
					extra.run(parts, feedback);
					return;
				}
				Module m = find(cmd);
				if (m != null && !m.isAlwaysOn()) {
					m.toggle();
					feedback.accept(m.name() + " " + (m.isEnabled() ? "enabled" : "disabled"));
				} else {
					feedback.accept("Unknown command: " + cmd + " (try " + prefix + "help)");
				}
			}
		}
	}
	private void help() {
		feedback.accept("Temple.nz commands (" + prefix + "):");
		feedback.accept(prefix + "toggle <module> – turn a module on or off");
		feedback.accept(prefix + "bind <module> <key> / " + prefix + "unbind <module>");
		feedback.accept(prefix + "set <module> <setting> <value>");
		feedback.accept(prefix + "friend add|remove|list [name]");
		feedback.accept(prefix + "list – enabled modules");
		feedback.accept(prefix + "defaults – everything off except protections, settings reset (keybinds kept)");
		for (String line : extraHelp) {
			feedback.accept(prefix + line);
		}
	}
	private void resetAll() {
		int off = 0;
		for (Module m : modules.all()) {
			for (Setting<?> s : m.allSettings()) {
				s.reset();
			}
			boolean protection = m.disableWarning() != null;
			if (!m.isAlwaysOn() && m.isEnabled() != protection) {
				m.setEnabled(protection);
				off++;
			} else if (!m.isAlwaysOn() && protection && !m.isEnabled()) {
				m.setEnabled(true);
			}
		}
		feedback.accept("Reset to defaults: " + off + " modules changed, settings restored, keybinds kept.");
	}
	private void toggle(String[] parts) {
		Module m = find(join(parts, 1));
		if (m == null) {
			feedback.accept("No module named " + join(parts, 1));
		} else if (m.isAlwaysOn()) {
			feedback.accept(m.name() + " is always on");
		} else {
			m.toggle();
			feedback.accept(m.name() + " " + (m.isEnabled() ? "enabled" : "disabled"));
		}
	}
	private void list() {
		StringBuilder sb = new StringBuilder("Enabled: ");
		boolean any = false;
		for (Module m : modules.all()) {
			if (m.isEnabled() && !m.isAlwaysOn()) {
				sb.append(any ? ", " : "").append(m.name());
				any = true;
			}
		}
		feedback.accept(any ? sb.toString() : "No modules enabled");
	}
	private void bind(String[] parts) {
		if (parts.length < 3) {
			feedback.accept("Usage: " + prefix + "bind <module> <key>");
			return;
		}
		Module m = find(parts[1]);
		if (m == null) {
			feedback.accept("No module named " + parts[1]);
			return;
		}
		feedback.accept("Use the menu's keybind row to bind keys – " + m.name() + " selected.");
	}
	private void unbind(String[] parts) {
		Module m = find(join(parts, 1));
		if (m != null) {
			m.keybind().set(-1);
			feedback.accept(m.name() + " unbound");
		}
	}
	private void set(String[] parts) {
		if (parts.length < 4) {
			feedback.accept("Usage: " + prefix + "set <module> <setting> <value>");
			return;
		}
		Module m = find(parts[1]);
		if (m == null) {
			feedback.accept("No module named " + parts[1]);
			return;
		}
		Setting<?> s = findSetting(m, parts[2]);
		if (s == null) {
			feedback.accept(m.name() + " has no setting " + parts[2]);
			return;
		}
		String value = join(parts, 3);
		if (s.trySet(value)) {
			feedback.accept(m.name() + " " + s.name() + " = " + s.describeValue());
		} else {
			feedback.accept("Could not set " + s.name() + " to " + value);
		}
	}
	private void friend(String[] parts) {
		if (parts.length < 2) {
			feedback.accept("Usage: " + prefix + "friend add|remove|list [name]");
			return;
		}
		switch (parts[1].toLowerCase(Locale.ROOT)) {
			case "add" -> {
				if (parts.length >= 3) {
					friends.add(parts[2]);
					feedback.accept("Added friend " + parts[2]);
				}
			}
			case "remove", "del", "rem" -> {
				if (parts.length >= 3) {
					friends.remove(parts[2]);
					feedback.accept("Removed friend " + parts[2]);
				}
			}
			case "list" -> feedback.accept(friends.size() == 0 ? "No friends" : "Friends: " + String.join(", ", friends.all()));
			default -> feedback.accept("Usage: " + prefix + "friend add|remove|list [name]");
		}
	}
	private Module find(String name) {
		String key = name.toLowerCase(Locale.ROOT).replace(" ", "");
		for (Module m : modules.all()) {
			if (m.name().toLowerCase(Locale.ROOT).replace(" ", "").equals(key)) {
				return m;
			}
		}
		return null;
	}
	private Setting<?> findSetting(Module m, String name) {
		String key = name.toLowerCase(Locale.ROOT).replace(" ", "");
		for (Setting<?> s : m.allSettings()) {
			if (s.name().toLowerCase(Locale.ROOT).replace(" ", "").equals(key)) {
				return s;
			}
		}
		return null;
	}
	private static String join(String[] parts, int from) {
		return from < parts.length ? String.join(" ", List.of(parts).subList(from, parts.length)) : "";
	}
}
