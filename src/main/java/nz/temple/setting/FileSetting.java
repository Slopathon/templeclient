package nz.temple.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

public final class FileSetting extends Setting<String> {
	private final List<String> extensions;
	public FileSetting(String name, List<String> extensions) {
		super(name, "");
		this.extensions = List.copyOf(extensions);
	}
	public List<String> extensions() {
		return extensions;
	}
	public boolean isDefault() {
		return value.isBlank();
	}
	public String fileName() {
		if (value.isBlank()) {
			return "";
		}
		String v = value.replace('\\', '/');
		return v.substring(v.lastIndexOf('/') + 1);
	}
	@Override
	protected String validate(String candidate) {
		return candidate.trim();
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
	public boolean trySet(String raw) {
		String r = raw.trim();
		set(r.equalsIgnoreCase("default") || r.equalsIgnoreCase("none") ? "" : r);
		return true;
	}
	@Override
	public String describeValue() {
		return value.isBlank() ? "Default" : value;
	}
}
