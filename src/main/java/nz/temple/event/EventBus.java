package nz.temple.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class EventBus {
	@FunctionalInterface
	public interface ErrorHandler {
		void onError(Object owner, Throwable error);
	}

	public record Subscription<E>(Object owner, Class<E> type, Consumer<? super E> handler) {
	}

	private final Map<Class<?>, List<Subscription<?>>> byType = new ConcurrentHashMap<>();
	private final Map<Subscription<?>, nz.temple.util.Perf.Section> timers = new java.util.IdentityHashMap<>();
	private ErrorHandler errorHandler = (owner, error) -> {
		throw new RuntimeException("Unhandled error in listener owned by " + owner, error);
	};

	public void setErrorHandler(ErrorHandler errorHandler) {
		this.errorHandler = errorHandler;
	}

	public void subscribe(Subscription<?> subscription) {
		byType.computeIfAbsent(subscription.type(), t -> new CopyOnWriteArrayList<>()).add(subscription);
	}

	public void unsubscribe(Subscription<?> subscription) {
		List<Subscription<?>> list = byType.get(subscription.type());
		if (list != null) {
			list.remove(subscription);
		}
	}

	public int listenerCount(Class<?> type) {
		List<Subscription<?>> list = byType.get(type);
		return list == null ? 0 : list.size();
	}

	private synchronized nz.temple.util.Perf.Section timer(Subscription<?> sub) {
		return timers.computeIfAbsent(sub, s -> nz.temple.util.Perf.section(s.type().getSimpleName() + ":"
				+ (s.owner() instanceof nz.temple.module.Module m ? m.name() : s.owner().getClass().getSimpleName())));
	}

	@SuppressWarnings("unchecked")
	public <E> E post(E event) {
		return post(event, owner -> true);
	}

	@SuppressWarnings("unchecked")
	public <E> E post(E event, java.util.function.Predicate<Object> allowed) {
		List<Subscription<?>> list = byType.get(event.getClass());
		if (list == null) {
			return event;
		}
		for (Subscription<?> raw : list) {
			if (!allowed.test(raw.owner())) continue;
			Subscription<E> sub = (Subscription<E>) raw;
			long began = nz.temple.util.Perf.ON ? System.nanoTime() : 0;
			try {
				sub.handler().accept(event);
			} catch (RuntimeException | LinkageError error) {
				errorHandler.onError(sub.owner(), error);
			}
			if (nz.temple.util.Perf.ON) {
				timer(sub).end(began);
			}
		}
		return event;
	}
}
