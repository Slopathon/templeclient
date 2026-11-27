package nz.temple.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class NamespaceListSetting extends Setting<Set<String>> {
	public NamespaceListSetting(String name, Collection<String> defaultValue) {
		super(name, normalize(defaultValue));
		value = normalize(defaultValue);
	}

	@Override
	protected Set<String> validate(Set<String> candidate) {
		return normalize(candidate);
	}

	@Override
	public JsonElement toJson() {
		JsonArray array = new JsonArray();
		value.forEach(array::add);
		return array;
	}

	@Override
	public void fromJson(JsonElement json) {
		if (json == null || !json.isJsonArray()) {
			return;
		}
		Set<String> parsed = new LinkedHashSet<>();
		for (JsonElement entry : json.getAsJsonArray()) {
			if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
				parsed.add(entry.getAsString());
			}
		}
		set(parsed);
	}

	@Override
	public boolean trySet(String raw) {
		if (raw == null) {
			return false;
		}
		if (raw.trim().equalsIgnoreCase("none")) {
			set(Set.of());
			return true;
		}
		set(java.util.Arrays.stream(raw.split(","))
				.map(String::trim)
				.collect(Collectors.toCollection(LinkedHashSet::new)));
		return true;
	}

	@Override
	public String describeValue() {
		return value.isEmpty() ? "none" : String.join(", ", value);
	}

	private static Set<String> normalize(Collection<String> values) {
		if (values == null) {
			return Set.of();
		}
		Set<String> result = values.stream()
				.filter(s -> s != null && s.matches("[a-z0-9_.-]+"))
				.map(s -> s.toLowerCase(Locale.ROOT))
				.collect(Collectors.toCollection(LinkedHashSet::new));
		return Set.copyOf(result);
	}
}
