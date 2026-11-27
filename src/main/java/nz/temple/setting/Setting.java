package nz.temple.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public abstract class Setting<T> {
	private final String name;
	private String key;
	private final T defaultValue;
	private final List<Runnable> listeners = new ArrayList<>();
	private String description = "";
	private BooleanSupplier visibility = () -> true;
	private boolean internal;
	protected T value;

	protected Setting(String name, T defaultValue) {
		this.name = name;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	public String name() {
		return name;
	}

	public String key() {
		return key != null ? key : name;
	}

	public void keyAs(String key) {
		this.key = key;
	}

	public T get() {
		return value;
	}

	public String description() {
		return description;
	}

	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S describe(String text) {
		description = text;
		return (S) this;
	}

	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S visibleWhen(BooleanSupplier condition) {
		visibility = condition;
		return (S) this;
	}

	@SuppressWarnings("unchecked")
	public <S extends Setting<T>> S internal() {
		internal = true;
		visibility = () -> false;
		return (S) this;
	}
	public boolean isInternal() {
		return internal;
	}
	public void andVisibleWhen(BooleanSupplier condition) {
		BooleanSupplier before = visibility;
		visibility = () -> before.getAsBoolean() && condition.getAsBoolean();
	}
	public boolean isVisible() {
		return visibility.getAsBoolean();
	}
	public T defaultValue() {
		return defaultValue;
	}
	public boolean isDefaultValue() {
		return Objects.equals(value, defaultValue);
	}
	public void set(T newValue) {
		T validated = newValue == null ? value : validate(newValue);
		if (!Objects.equals(validated, value)) {
			T before = value;
			value = validated;
			SettingHistory.changed(this, before, validated);
			listeners.forEach(Runnable::run);
		}
	}
	public void reset() {
		set(defaultValue);
	}
	/** Tells listeners the setting changed in a way its value alone does not show (a colour that now follows the theme). */
	protected void fireChanged() {
		listeners.forEach(Runnable::run);
	}
	public void setQuietly(T newValue) {
		value = validate(newValue);
	}
	@SuppressWarnings("unchecked")
	public boolean mirror(Setting<?> source) {
		if (source.getClass() != getClass()) {
			return false;
		}
		value = validate((T) source.value);
		return true;
	}
	public void addListener(Runnable listener) {
		listeners.add(listener);
	}
	protected abstract T validate(T candidate);
	public abstract JsonElement toJson();

	public abstract void fromJson(JsonElement json);

	public boolean trySet(String raw) {
		return false;
	}

	public void writeCompact(DataOutput out) throws IOException {
		byte[] json = toJson().toString().getBytes(StandardCharsets.UTF_8);
		writeVarInt(out, json.length);
		out.write(json);
	}
	public void readCompact(DataInput in) throws IOException {
		int length = readVarInt(in);
		if (length < 0 || length > 1_000_000) {
			throw new IOException("bad length");
		}
		byte[] json = new byte[length];
		in.readFully(json);
		try {
			fromJson(JsonParser.parseString(new String(json, StandardCharsets.UTF_8)));
		} catch (JsonParseException e) {
			throw new IOException(e);
		}
	}
	public static void writeVarInt(DataOutput out, long value) throws IOException {
		long v = value;
		while ((v & ~0x7FL) != 0) {
			out.writeByte((int) ((v & 0x7F) | 0x80));
			v >>>= 7;
		}
		out.writeByte((int) v);
	}

	public static int readVarInt(DataInput in) throws IOException {
		long v = 0;
		for (int shift = 0; shift < 35; shift += 7) {
			int b = in.readUnsignedByte();
			v |= (long) (b & 0x7F) << shift;
			if ((b & 0x80) == 0) {
				return (int) v;
			}
		}
		throw new IOException("varint too long");
	}
	public String describeValue() {
		return String.valueOf(value);
	}
}
