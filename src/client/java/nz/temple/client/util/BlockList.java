package nz.temple.client.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class BlockList {
	private String parsedFrom;
	private Set<Block> blocks = Set.of();
	private Collection<String> parsedIds;
	public Set<Block> of(Collection<String> ids) {
		if (!ids.equals(parsedIds)) {
			Set<Block> out = Collections.newSetFromMap(new IdentityHashMap<>());
			for (String id : ids) {
				BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.Identifier.tryParse(id)).ifPresent(out::add);
			}
			blocks = Collections.unmodifiableSet(out);
			parsedIds = java.util.List.copyOf(ids);
			parsedFrom = null;
		}
		return blocks;
	}
	public Set<Block> of(String text) {
		String t = text == null ? "" : text;
		if (!t.equals(parsedFrom)) {
			blocks = parse(t);
			parsedFrom = t;
			parsedIds = null;
		}
		return blocks;
	}
	public static Set<Block> parse(String text) {
		Set<Block> out = Collections.newSetFromMap(new IdentityHashMap<>());
		for (String raw : text.split(",")) {
			String entry = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
			if (entry.isEmpty()) {
				continue;
			}
			boolean namespaced = entry.contains(":");
			Pattern pattern = Pattern.compile(java.util.Arrays.stream(entry.split("\\*", -1)).map(Pattern::quote)
					.collect(java.util.stream.Collectors.joining("[a-z0-9_./:-]*")));
			for (Block block : BuiltInRegistries.BLOCK) {
				var id = BuiltInRegistries.BLOCK.getKey(block);
				String name = namespaced ? id.toString() : id.getPath();
				if (pattern.matcher(name).matches()) {
					out.add(block);
				}
			}
		}
		return Collections.unmodifiableSet(out);
	}
}
