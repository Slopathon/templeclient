package nz.temple.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SusZonesTest {
	@Test
	void chunkKeysRoundTripNegativeCoordinates() {
		long key = SusZones.chunkKey(-3, 7);
		assertEquals(-3, SusZones.chunkX(key));
		assertEquals(7, SusZones.chunkZ(key));
		assertEquals(SusZones.chunkKey(-1, -1), new SusZones.Cell(-1, 0, -16).chunk());
	}

	@Test
	void geodesSixApartStaySeparate() {
		List<SusZones.Geode> geodes = SusZones.clusterGeodes(List.of(new SusZones.Cell(0, 10, 0), new SusZones.Cell(6, 10, 0)));
		assertEquals(2, geodes.size());
	}

	@Test
	void geodesFiveApartMerge() {
		List<SusZones.Geode> geodes = SusZones.clusterGeodes(List.of(new SusZones.Cell(0, 10, 0), new SusZones.Cell(5, 15, -5)));
		assertEquals(1, geodes.size());
		assertEquals(12, geodes.get(0).score());
	}

	@Test
	void geodesChainAcrossBucketsAndChunkBorders() {
		List<SusZones.Cell> cells = new ArrayList<>();
		for (int x = -20; x <= 20; x += 4) {
			cells.add(new SusZones.Cell(x, -3, 15));
		}
		List<SusZones.Geode> geodes = SusZones.clusterGeodes(cells);
		assertEquals(1, geodes.size());
		assertTrue(geodes.get(0).chunks().size() >= 3);
	}

	@Test
	void amethystScoreIsCapped() {
		List<SusZones.Cell> cells = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			cells.add(new SusZones.Cell(i % 4, 20 + i / 4, 0));
		}
		assertEquals(6 * 16, SusZones.clusterGeodes(cells).get(0).score());
	}

	@Test
	void plantScoreAppliesWeightAndCap() {
		int[] counts = new int[SusZones.Signal.values().length];
		counts[SusZones.Signal.KELP.ordinal()] = 10;
		counts[SusZones.Signal.BERRIES.ordinal()] = 2;
		counts[SusZones.Signal.AMETHYST.ordinal()] = 50;
		assertEquals(2 * 6 + 2 * 2, SusZones.plantScore(counts));
		assertEquals(15, SusZones.threshold(3));
	}

	@Test
	void zonesMergeWithinRadiusOnly() {
		List<SusZones.Flag> flags = List.of(flag(0, 0, 20), flag(3, 3, 20), flag(7, 0, 20));
		assertEquals(2, SusZones.buildZones(flags, 3).size());
		assertEquals(1, SusZones.buildZones(flags, 4).size());
		assertEquals(3, SusZones.buildZones(flags, 1).size());
		assertEquals(3, SusZones.buildZones(List.of(flag(0, 0, 20), flag(1, 0, 20), flag(2, 0, 20)), 0).size());
	}

	@Test
	void zonesSortByTotalScoreAndUseLowestHit() {
		SusZones.Flag a = flag(0, 0, 10);
		a.hit(2, 4.5, 30, 4.5);
		SusZones.Flag b = flag(1, 0, 10);
		b.hit(2, 20.5, 12, 4.5);
		SusZones.Flag lone = flag(20, 20, 15);
		List<SusZones.Zone> zones = SusZones.buildZones(List.of(lone, a, b), 3);
		assertEquals(20, zones.get(0).totalScore());
		assertEquals(12.5, zones.get(0).x(), 1e-9);
		assertEquals(12, zones.get(0).y());
		assertEquals(SusZones.chunkKey(0, 0), zones.get(0).key());
	}

	@Test
	void centroidFallsBackToScoreWeightedChunkCentres() {
		List<SusZones.Zone> zones = SusZones.buildZones(List.of(flag(0, 0, 30), flag(1, 0, 10)), 3);
		SusZones.Zone zone = zones.get(0);
		assertEquals((8 * 30 + 24 * 10) / 40.0, zone.x(), 1e-9);
		assertEquals(8, zone.z(), 1e-9);
		assertEquals(64, zone.y());
	}

	private static SusZones.Flag flag(int cx, int cz, int score) {
		SusZones.Flag f = new SusZones.Flag(SusZones.chunkKey(cx, cz));
		f.score = score;
		return f;
	}
}
