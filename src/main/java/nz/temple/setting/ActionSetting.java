package nz.temple.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.function.Supplier;

public final class ActionSetting extends Setting<Boolean> {
	private final Runnable action;
	private final Supplier<String> label;
	public ActionSetting(String name, Supplier<String> label, Runnable action) {
		super(name, false);
		this.action = action;
		this.label = label;
	}
	public ActionSetting(String name, String label, Runnable action) {
		this(name, () -> label, action);
	}
	public String label() {
		return label.get();
	}
	public void press() {
		action.run();
	}
	@Override
	public boolean isDefaultValue() {
		return true;
	}
	@Override
	protected Boolean validate(Boolean candidate) {
		return false;
	}
	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(false);
	}
	@Override
	public void fromJson(JsonElement json) {
	}
	@Override
	public boolean trySet(String raw) {
		press();
		return true;
	}
}
