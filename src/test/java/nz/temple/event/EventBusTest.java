package nz.temple.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {
	@Test
	void pausedOwnersResumeWithoutLosingSubscriptions() {
		EventBus bus = new EventBus();
		List<String> got = new ArrayList<>();
		bus.subscribe(new EventBus.Subscription<>("movement", String.class, e -> got.add("move:" + e)));
		bus.subscribe(new EventBus.Subscription<>("camera", String.class, e -> got.add("camera:" + e)));
		bus.post("freecam", owner -> owner.equals("camera"));
		assertEquals(List.of("camera:freecam"), got);
		assertEquals(2, bus.listenerCount(String.class));
		bus.post("normal");
		assertEquals(List.of("camera:freecam", "move:normal", "camera:normal"), got);
	}

	@Test
	void deliversToSubscribersOfTheExactType() {
		EventBus bus = new EventBus();
		List<String> got = new ArrayList<>();
		bus.subscribe(new EventBus.Subscription<>("a", String.class, got::add));
		bus.post("hello");
		bus.post(TickEvent.INSTANCE);
		assertEquals(List.of("hello"), got);
	}

	@Test
	void unsubscribeStopsDelivery() {
		EventBus bus = new EventBus();
		List<String> got = new ArrayList<>();
		EventBus.Subscription<String> sub = new EventBus.Subscription<>("a", String.class, got::add);
		bus.subscribe(sub);
		bus.unsubscribe(sub);
		bus.post("x");
		assertTrue(got.isEmpty());
		assertEquals(0, bus.listenerCount(String.class));
	}

	@Test
	void oneFailingListenerDoesNotStopOthersAndIsReported() {
		EventBus bus = new EventBus();
		List<Object> failedOwners = new ArrayList<>();
		bus.setErrorHandler((owner, error) -> failedOwners.add(owner));
		List<String> got = new ArrayList<>();
		bus.subscribe(new EventBus.Subscription<>("bad", String.class, s -> {
			throw new IllegalStateException("boom");
		}));
		bus.subscribe(new EventBus.Subscription<>("good", String.class, got::add));
		bus.post("x");
		assertEquals(List.of("bad"), failedOwners);
		assertEquals(List.of("x"), got);
	}

	@Test
	void listenerMayUnsubscribeItselfWhileEventIsDelivered() {
		EventBus bus = new EventBus();
		List<EventBus.Subscription<String>> holder = new ArrayList<>();
		EventBus.Subscription<String> sub = new EventBus.Subscription<>("self", String.class, s -> bus.unsubscribe(holder.get(0)));
		holder.add(sub);
		bus.subscribe(sub);
		assertDoesNotThrow(() -> bus.post("x"));
		assertEquals(0, bus.listenerCount(String.class));
	}
}
