package nz.temple.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class TextSetting extends Setting<String> {
	private final int maxLength;
	private final String placeholder;

	public TextSetting(String name, String defaultValue, int maxLength, String placeholder) {
		super(name, defaultValue);
		this.maxLength = maxLength;
		this.placeholder = placeholder;
	}

	public int maxLength() {
		return maxLength;
	}

	public String placeholder() {
		return placeholder;
	}

	@Override
	protected String validate(String candidate) {
		String v = candidate.replace("\n", "").replace("\r", "");
		return v.length() > maxLength ? v.substring(0, maxLength) : v;
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
		set(raw.trim());
		return true;
	}
}
