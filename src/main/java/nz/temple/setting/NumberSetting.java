package nz.temple.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.util.Locale;

public final class NumberSetting extends Setting<Double> {
	private final double min;
	private final double max;
	private final double step;
	private final String unit;

	public NumberSetting(String name, double defaultValue, double min, double max, double step, String unit) {
		super(name, defaultValue);
		this.min = min;
		this.max = max;
		this.step = step;
		this.unit = unit;
		this.value = validate(defaultValue);
	}

	public double min() {
		return min;
	}

	public double max() {
		return max;
	}

	public double step() {
		return step;
	}

	@Override
	public void writeCompact(java.io.DataOutput out) throws java.io.IOException {
		if (step > 0) {
			writeVarInt(out, Math.max(0, Math.round((value - min) / step)));
		} else {
			out.writeDouble(value);
		}
	}

	@Override
	public void readCompact(java.io.DataInput in) throws java.io.IOException {
		if (step > 0) {
			set(min + readVarInt(in) * step);
		} else {
			set(in.readDouble());
		}
	}

	public float getFloat() {
		return value.floatValue();
	}

	public double fraction() {
		return max == min ? 0 : (value - min) / (max - min);
	}

	public void setFraction(double t) {
		set(min + Math.max(0, Math.min(1, t)) * (max - min));
	}

	public String display() {
		int decimals = Math.max(0, BigDecimal.valueOf(step).stripTrailingZeros().scale());
		return String.format(Locale.ROOT, "%." + decimals + "f", value) + unit;
	}

	@Override
	protected Double validate(Double candidate) {
		double snapped = min + Math.round((candidate - min) / step) * step;
		double clamped = Math.max(min, Math.min(max, snapped));
		return Math.round(clamped * 1e6) / 1e6;
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
			set(json.getAsDouble());
		}
	}

	@Override
	public boolean trySet(String raw) {
		try {
			set(Double.parseDouble(raw.trim()));
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	@Override
	public String describeValue() {
		return display();
	}
}
