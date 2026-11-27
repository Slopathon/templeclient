package nz.temple.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class KeybindSetting extends Setting<Integer> {
	public static final int NONE = -1;

	public KeybindSetting(String name, int defaultKey) {
		super(name, defaultKey);
	}

	public boolean isBound() {
		return value != NONE;
	}

	@Override
	protected Integer validate(Integer candidate) {
		return candidate < 0 ? NONE : candidate;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			set(json.getAsInt());
		}
	}
}
