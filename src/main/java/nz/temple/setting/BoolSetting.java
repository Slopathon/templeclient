package nz.temple.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class BoolSetting extends Setting<Boolean> {
	private String enableWarning;

	public BoolSetting(String name, boolean defaultValue) {
		super(name, defaultValue);
	}

	public BoolSetting enableWarning(String text) {
		enableWarning = text;
		return this;
	}

	public String enableWarning() {
		return enableWarning;
	}

	public void toggle() {
		set(!get());
	}

	@Override
	protected Boolean validate(Boolean candidate) {
		return candidate;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) {
			set(json.getAsBoolean());
		}
	}

	@Override
	public void writeCompact(java.io.DataOutput out) throws java.io.IOException {
		out.writeByte(value ? 1 : 0);
	}

	@Override
	public void readCompact(java.io.DataInput in) throws java.io.IOException {
		set(in.readUnsignedByte() != 0);
	}

	@Override
	public boolean trySet(String raw) {
		String r = raw.trim().toLowerCase();
		switch (r) {
			case "true", "on", "yes", "1" -> set(true);
			case "false", "off", "no", "0" -> set(false);
			case "toggle", "" -> toggle();
			default -> {
				return false;
			}
		}
		return true;
	}
}
