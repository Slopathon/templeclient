package nz.temple.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EntitySelectionTest {
	@Test
	void toggleAddsAndRemoves() {
		EntitySelection s = new EntitySelection("Entities", List.of("minecraft:zombie"));
		assertTrue(s.has("minecraft:zombie"));
		s.toggle("minecraft:zombie");
		assertFalse(s.has("minecraft:zombie"));
		s.toggle("minecraft:creeper");
		assertTrue(s.has("minecraft:creeper"));
		assertEquals(1, s.count());
	}

	@Test
	void roundTripsThroughJson() {
		EntitySelection s = new EntitySelection("Entities", List.of("minecraft:zombie", "minecraft:skeleton"));
		JsonArray json = (JsonArray) s.toJson();
		assertEquals(2, json.size());
		EntitySelection copy = new EntitySelection("Entities", List.of());
		copy.fromJson(json);
		assertTrue(copy.has("minecraft:zombie"));
		assertTrue(copy.has("minecraft:skeleton"));
	}

	@Test
	void blockSelectionExpandsWildcards() {
		EntitySelection s = new EntitySelection("Blocks", List.of()).blocks(pattern ->
				pattern.equals("minecraft:*_ore") ? List.of("minecraft:iron_ore", "minecraft:gold_ore") : List.of());
		assertTrue(s.trySet("spawner, *_ore"));
		assertTrue(s.has("minecraft:spawner"));
		assertTrue(s.has("minecraft:iron_ore"));
		assertTrue(s.has("minecraft:gold_ore"));
		assertEquals(3, s.count());
		assertTrue(s.isBlocks());
	}

	@Test
	void blockSelectionReadsTheOldTypedList() {
		EntitySelection s = new EntitySelection("Blocks", List.of("minecraft:stone")).blocks(pattern ->
				List.of("minecraft:chest", "minecraft:trapped_chest"));
		s.fromJson(new JsonPrimitive("beacon, *chest"));
		assertTrue(s.has("minecraft:beacon"));
		assertTrue(s.has("minecraft:chest"));
		assertTrue(s.has("minecraft:trapped_chest"));
		assertFalse(s.has("minecraft:stone"));
	}

	@Test
	void fromJsonIgnoresNonArraysAndNonStrings() {
		EntitySelection s = new EntitySelection("Entities", List.of("minecraft:pig"));
		s.fromJson(new JsonPrimitive("nope"));
		assertTrue(s.has("minecraft:pig"));
		JsonArray mixed = new JsonArray();
		mixed.add("minecraft:cow");
		mixed.add(5);
		s.fromJson(mixed);
		assertTrue(s.has("minecraft:cow"));
		assertEquals(1, s.count());
	}
}
