package nz.temple.client.util;

import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class TotemTracker {
	public record Pop(int entityId, double x, double y, double z, long nanos) {
	}
	private static final Map<Integer, Integer> COUNTS = new ConcurrentHashMap<>();
	private static final Map<Integer, Long> LAST = new ConcurrentHashMap<>();
	private static final ConcurrentLinkedQueue<Pop> RECENT = new ConcurrentLinkedQueue<>();

	private static final java.util.List<java.util.function.Consumer<LivingEntity>> LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();
	private TotemTracker() {
	}

	public static void onPop(java.util.function.Consumer<LivingEntity> listener) {
		LISTENERS.add(listener);
	}
	public static void popped(LivingEntity entity) {
		int id = entity.getId();
		COUNTS.merge(id, 1, Integer::sum);
		LAST.put(id, System.currentTimeMillis());
		RECENT.add(new Pop(id, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(), System.nanoTime()));
		while (RECENT.size() > 32) {
			RECENT.poll();
		}
		for (var l : LISTENERS) {
			l.accept(entity);
		}
	}
	public static int count(int entityId) {
		return COUNTS.getOrDefault(entityId, 0);
	}

	public static long lastPopMillis(int entityId) {
		return LAST.getOrDefault(entityId, 0L);
	}

	public static Iterable<Pop> recent(long maxAgeNanos) {
		long now = System.nanoTime();
		RECENT.removeIf(p -> now - p.nanos() > maxAgeNanos);
		return RECENT;
	}
	public static void clear() {
		COUNTS.clear();
		LAST.clear();
		RECENT.clear();
	}
}
