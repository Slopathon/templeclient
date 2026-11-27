package nz.temple.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DonutRegionsTest {
	@Test
	void mapsPublishedGridBoundaries() {
		assertEquals(new DonutRegions.Result(1, "Oceania"), DonutRegions.locate(-225_000, -225_000));
		assertEquals(new DonutRegions.Result(2, "NA West"), DonutRegions.locate(-175_000, -225_000));
		assertEquals(new DonutRegions.Result(41, "NA East"), DonutRegions.locate(0, 0));
		assertEquals(new DonutRegions.Result(81, "EU Central"), DonutRegions.locate(224_999.99, 224_999.99));
	}

	@Test
	void serversAreTheGoliathNumbersFromTheRegionMap() {
		assertEquals("Goliath 5", DonutRegions.locate(0, 0).server());
		assertEquals(82, DonutRegions.locate(-225_000, -225_000).goliath());
		assertEquals(91, DonutRegions.locate(224_999, -225_000).goliath());
		assertEquals(90, DonutRegions.locate(-225_000, 224_999).goliath());
		assertEquals(99, DonutRegions.locate(224_999, 224_999).goliath());
		assertEquals(71, DonutRegions.locate(-30_000, 0).goliath());
		assertEquals(1, DonutRegions.locate(30_000, -30_000).goliath());
		assertEquals(59, DonutRegions.locate(110_000, 160_000).goliath());
	}

	@Test
	void everyGoliathNumberIsOnOneCellAndGapsHaveNone() {
		Set<Integer> seen = new HashSet<>();
		for (int cell = 1; cell <= 81; cell++) {
			int n = DonutRegions.goliath(cell);
			assertTrue(seen.add(n), "Goliath " + n + " appears twice");
			assertEquals(cell, DonutRegions.cellOf(n));
		}
		assertEquals(81, seen.size());
		assertEquals(0, DonutRegions.cellOf(6));
		assertEquals(0, DonutRegions.cellOf(114));
		assertEquals(0, DonutRegions.goliath(0));
	}

	@Test
	void regionsMatchTheMap() {
		assertEquals("NA East", DonutRegions.regionOf(DonutRegions.cellOf(5)));
		assertEquals("Oceania", DonutRegions.regionOf(DonutRegions.cellOf(74)));
		assertEquals("Asia", DonutRegions.regionOf(DonutRegions.cellOf(88)));
		assertEquals("EU West", DonutRegions.regionOf(DonutRegions.cellOf(26)));
		assertEquals("EU Central", DonutRegions.regionOf(DonutRegions.cellOf(97)));
		assertEquals("EU West", DonutRegions.regionOf(DonutRegions.cellOf(113)));
		assertEquals("NA East", DonutRegions.regionOf(DonutRegions.cellOf(96)));
		assertEquals("NA West", DonutRegions.regionOf(DonutRegions.cellOf(3)));
	}

	@Test
	void cellsKnowTheirRegionAndBounds() {
		assertNull(DonutRegions.regionOf(0));
		assertNull(DonutRegions.regionOf(82));
		int[] b = DonutRegions.bounds(41);
		assertEquals(41, DonutRegions.locate(b[0], b[1]).cell());
		assertEquals(41, DonutRegions.locate(b[2] - 0.01, b[3] - 0.01).cell());
		for (int cell = 1; cell <= 81; cell++) {
			int[] c = DonutRegions.bounds(cell);
			assertEquals(DonutRegions.regionOf(cell), DonutRegions.locate(c[0] + 1, c[1] + 1).region());
		}
	}

	@Test
	void rejectsCoordinatesOutsidePublishedGrid() {
		assertNull(DonutRegions.locate(-225_000.01, 0));
		assertNull(DonutRegions.locate(225_000, 0));
		assertNull(DonutRegions.locate(0, -225_000.01));
		assertNull(DonutRegions.locate(0, 225_000));
	}
}
