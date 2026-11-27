package nz.temple.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

public final class ModeSetting extends Setting<String> {
	private List<String> options;
	private final boolean open;

	public ModeSetting(String name, List<String> options, String defaultValue) {
		this(name, options, defaultValue, false);
	}

	private ModeSetting(String name, List<String> options, String defaultValue, boolean open) {
		super(name, defaultValue);
		if (!open && !options.contains(defaultValue)) {
			throw new IllegalArgumentException("Default " + defaultValue + " not in " + options);
		}
		this.open = open;
		this.options = List.copyOf(options);
	}

	public static ModeSetting open(String name, List<String> options, String defaultValue) {
		return new ModeSetting(name, options, defaultValue, true);
	}
	public void setOptions(List<String> options) {
		if (!open) {
			throw new IllegalStateException(name() + " has a fixed list");
		}
		this.options = List.copyOf(options);
	}
	public List<String> options() {
		return options;
	}
	public int index() {
		return options.indexOf(value);
	}
	public boolean is(String option) {
		return value.equals(option);
	}
	@Override
	protected String validate(String candidate) {
		if (open) {
			return candidate == null || candidate.isBlank() ? value : candidate;
		}
		return options == null || options.contains(candidate) ? candidate : value;
	}
	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}
	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
			set(json.getAsString());
		}
	}
	@Override
	public void writeCompact(java.io.DataOutput out) throws java.io.IOException {
		writeVarInt(out, index());
	}
	@Override
	public void readCompact(java.io.DataInput in) throws java.io.IOException {
		int i = readVarInt(in);
		if (i >= 0 && i < options.size()) {
			set(options.get(i));
		}
	}
	@Override
	public boolean trySet(String raw) {
		String r = raw.trim();
		for (String option : options) {
			if (option.equalsIgnoreCase(r) || option.replace(" ", "").equalsIgnoreCase(r.replace(" ", ""))) {
				set(option);
				return true;
			}
		}
		return false;
	}
}
