package nz.temple.social;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FriendManagerTest {
	@Test
	void addIsCaseInsensitiveAndDeduplicates() {
		FriendManager f = new FriendManager();
		assertTrue(f.add("Notch"));
		assertFalse(f.add("notch"));
		assertTrue(f.isFriend("NOTCH"));
		assertEquals(1, f.size());
	}

	@Test
	void removeAndUnknownName() {
		FriendManager f = new FriendManager();
		f.add("Alex");
		assertTrue(f.remove("alex"));
		assertFalse(f.isFriend("Alex"));
		assertFalse(f.isFriend(null));
	}
}
