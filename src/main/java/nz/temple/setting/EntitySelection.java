package nz.temple.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public final class EntitySelection extends Setting<Set<String>> {
	private boolean items;
	private java.util.function.Function<String, Collection<String>> blockPatterns;
	public EntitySelection(String name, Collection<String> defaults) {
		super(name, new LinkedHashSet<>(defaults));
	}
	public EntitySelection items() {
		items = true;
		return this;
	}
	public boolean isItems() {
		return items;
	}
	public EntitySelection blocks(java.util.function.Function<String, Collection<String>> patterns) {
		blockPatterns = patterns;
		return this;
	}
	public boolean isBlocks() {
		return blockPatterns != null;
	}
	public boolean has(String id) {
		return value.contains(id);
	}
	public void toggle(String id) {
		Set<String> next = new LinkedHashSet<>(value);
		if (!next.remove(id)) {
			next.add(id);
		}
		set(next);
	}
	public void setAll(Collection<String> ids) {
		set(new LinkedHashSet<>(ids));
	}
	public void clear() {
		set(new LinkedHashSet<>());
	}
	@Override
	public boolean trySet(String raw) {
		java.util.List<String> ids = new java.util.ArrayList<>();
		for (String part : raw.split(",")) {
			String id = part.trim().toLowerCase(java.util.Locale.ROOT);
			if (!id.isEmpty()) {
				id = (id.contains(":") ? id : "minecraft:" + id).replace(' ', '_');
				if (blockPatterns != null && id.contains("*")) {
					ids.addAll(blockPatterns.apply(id));
				} else {
					ids.add(id);
				}
			}
		}
		setAll(ids);
		return true;
	}
	public int count() {
		return value.size();
	}
	@Override
	protected Set<String> validate(Set<String> candidate) {
		return new LinkedHashSet<>(candidate);
	}
	@Override
	public JsonElement toJson() {
		JsonArray array = new JsonArray();
		value.forEach(array::add);
		return array;
	}
	@Override
	public void fromJson(JsonElement json) {
		if (isBlocks() && json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
			trySet(json.getAsString());
			return;
		}
		if (json == null || !json.isJsonArray()) {
			return;
		}
		Set<String> next = new LinkedHashSet<>();
		for (JsonElement e : json.getAsJsonArray()) {
			if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
				next.add(e.getAsString());
			}
		}
		set(next);
	}
}
