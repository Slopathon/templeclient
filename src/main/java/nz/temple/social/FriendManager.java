package nz.temple.social;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class FriendManager {
	private final Set<String> friends = new LinkedHashSet<>();
	private Runnable onChange = () -> { };

	public void setOnChange(Runnable onChange) {
		this.onChange = onChange == null ? () -> { } : onChange;
	}

	public void load(java.util.Collection<String> names) {
		friends.clear();
		for (String n : names) {
			friends.add(n.toLowerCase(Locale.ROOT));
		}
	}

	public boolean add(String name) {
		boolean changed = friends.add(name.toLowerCase(Locale.ROOT));
		if (changed) {
			onChange.run();
		}
		return changed;
	}

	public boolean remove(String name) {
		boolean changed = friends.remove(name.toLowerCase(Locale.ROOT));
		if (changed) {
			onChange.run();
		}
		return changed;
	}

	public boolean isFriend(String name) {
		return name != null && friends.contains(name.toLowerCase(Locale.ROOT));
	}

	public Set<String> all() {
		return Set.copyOf(friends);
	}

	public int size() {
		return friends.size();
	}
}
