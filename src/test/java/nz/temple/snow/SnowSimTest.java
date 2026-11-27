package nz.temple.snow;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowSimTest {
	private static final class World implements SnowSim.Ground {
		final Map<Long, Float> heights = new HashMap<>();
		final Set<Long> foliage = new HashSet<>();
		final Map<Long, Integer> heat = new HashMap<>();

		static long k(int x, int z) {
			return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
		}

		@Override
		public float surface(int x, int z) {
			return heights.getOrDefault(k(x, z), 64f);
		}

		@Override
		public boolean underFoliage(int x, int z) {
			return foliage.contains(k(x, z));
		}

		@Override
		public int heat(int x, int z) {
			return heat.getOrDefault(k(x, z), 0);
		}
	}

	private static void run(SnowSim sim, double seconds, float windX, float windZ) {
		for (double t = 0; t < seconds; t += 0.25) {
			sim.step(0.25, 0.5, 0.5, 1, windX, windZ);
		}
	}

	@Test
	void openGroundGathersSlowlyAndLevelsOutAtItsLimit() {
		World w = new World();
		SnowSim sim = new SnowSim(w);
		run(sim, 30, 0, 0);
		float early = sim.depthAt(2.5, 2.5);
		assertTrue(early > 0.003f && early < 0.03f, "a light dusting after half a minute, was " + early);
		run(sim, 3600, 0, 0);
		float settled = sim.depthAt(2.5, 2.5);
		assertEquals(SnowSim.OPEN_CAP, settled, 0.02f, "open ground fills to its limit and stops");
	}

	@Test
	void snowOnlyBuildsAroundThePlayer() {
		World w = new World();
		SnowSim sim = new SnowSim(w);
		run(sim, 600, 0, 0);
		assertTrue(sim.depthAt(3.5, 3.5) > 0.1f);
		assertEquals(0, sim.depthAt(SnowSim.RADIUS + 20, 0.5), 1e-6, "nothing where the player has never been");
	}

	@Test
	void underACanopySnowOnlyReachesInFromTheEdge() {
		World w = new World();
		for (int x = 0; x <= 8; x++) {
			for (int z = 0; z <= 8; z++) {
				w.foliage.add(World.k(x, z));
			}
		}
		SnowSim sim = new SnowSim(w);
		run(sim, 900, 0, 0);
		float open = sim.depthAt(-3.5, 4.5);
		float edge = sim.depthAt(0.2, 4.5);
		float deep = sim.depthAt(4.5, 4.5);
		assertTrue(open > 0.2f);
		assertTrue(edge > 0.05f && edge < open, "some snow just inside the canopy edge, was " + edge);
		assertEquals(0, deep, 1e-6, "none deep under the tree");
	}

	@Test
	void theWindPilesADriftInTheLeeOfAWall() {
		World w = new World();
		for (int z = -10; z <= 10; z++) {
			w.heights.put(World.k(0, z), 66f);
		}
		SnowSim sim = new SnowSim(w);
		for (double t = 0; t < 2400; t += 0.25) {
			sim.step(0.25, 2.5, 0.5, 1, 1, 0);
		}
		float lee = sim.depthAt(1.3, 0.5);
		float far = sim.depthAt(9.5, 0.5);
		float windward = sim.depthAt(-0.3, 0.5);
		assertTrue(lee > far + 0.2f, "deep drift behind the wall (" + lee + ") against open ground (" + far + ")");
		assertTrue(lee > 0.6f, "the drift climbs well up the wall, was " + lee);
		assertTrue(windward > far, "snow banks against the windward face too");
	}

	@Test
	void breakingOrPlacingABlockClearsTheSnowThere() {
		World w = new World();
		SnowSim sim = new SnowSim(w);
		run(sim, 600, 0, 0);
		assertTrue(sim.depthAt(3.5, 3.5) > 0.1f);
		w.heights.put(World.k(3, 3), 65f);
		sim.step(0.25, 0.5, 0.5, 1, 0, 0);
		assertTrue(sim.depthAt(3.5, 3.5) < 0.01f, "the new block's top starts bare");
		w.heights.put(World.k(5, 5), Float.NaN);
		sim.step(0.25, 0.5, 0.5, 1, 0, 0);
		assertEquals(0, sim.depthAt(5.5, 5.5), 0.02f, "nothing over water");
	}

	@Test
	void theRimOfAFreshHoleSlumpsAndSpillsIn() {
		World w = new World();
		SnowSim sim = new SnowSim(w);
		run(sim, 1800, 0, 0);
		float rimBefore = sim.depthAt(4.1, 3.5);
		assertTrue(rimBefore > 0.25f);
		w.heights.put(World.k(3, 3), 63f);
		sim.step(0.25, 0.5, 0.5, 1, 0, 0);
		float rimAfter = sim.cellDepth(4 * 4, 3 * 4 + 2);
		float inset = sim.cellDepth(4 * 4 + 3, 3 * 4 + 2);
		assertTrue(rimAfter < rimBefore * 0.6f, "the edge right at the hole slumps, was " + rimAfter);
		assertTrue(inset > rimAfter, "further back it keeps more, a rounded shoulder");
		assertTrue(sim.cellDepth(3 * 4 + 3, 3 * 4 + 2) > 0.05f, "some snow fell in at the foot of the new wall");
		run(sim, 1800, 0, 0);
		assertTrue(sim.cellDepth(4 * 4, 3 * 4 + 2) < sim.cellDepth(4 * 4 + 3, 3 * 4 + 2), "the shoulder stays rounded as snow keeps falling");
	}

	@Test
	void heatKeepsSnowAwayAndMeltsIt() {
		World w = new World();
		SnowSim sim = new SnowSim(w);
		run(sim, 900, 0, 0);
		assertTrue(sim.depthAt(4.5, 4.5) > 0.2f);
		w.heat.put(World.k(4, 4), 14);
		run(sim, 120, 0, 0);
		assertTrue(sim.depthAt(4.5, 4.5) < 0.05f, "melted beside a torch");
	}

	@Test
	void feetPressPrintsThatFillBackIn() {
		World w = new World();
		SnowSim sim = new SnowSim(w);
		assertFalse(sim.stamp(3.5, 3.5, 0, 1, 1), "no print without snow");
		run(sim, 900, 0, 0);
		float before = sim.depthAt(3.5, 3.5);
		assertTrue(sim.stamp(3.5, 3.5, 0, 1, 1));
		float after = sim.depthAt(3.5, 3.5);
		assertTrue(after < before * 0.8f, "pressed down from " + before + " to " + after);
		SnowSim.Print print = sim.prints().iterator().next();
		assertEquals(1, sim.printFade(print), 1e-6);
		run(sim, 1200, 0, 0);
		assertTrue(sim.printFade(print) < 0.05f, "fresh snow has filled the print");
	}

	@Test
	void wadingThroughDeepSnowPloughsItIntoBanks() {
		World w = new World();
		SnowSim sim = new SnowSim(w);
		run(sim, 2400, 0, 0);
		float before = sim.depthAt(3.5, 3.5);
		float sideBefore = sim.depthAt(4.0, 3.5);
		assertTrue(before > 0.3f, "deep enough to wade through, was " + before);
		for (double z = 2.0; z < 5.0; z += 0.1) {
			sim.plow(3.5, z, 0, 1, 0.3, 0.1);
		}
		float trench = sim.depthAt(3.5, 3.5);
		float bank = sim.depthAt(4.0, 3.5);
		assertTrue(trench < before * 0.7f, "a trench is left behind, " + before + " to " + trench);
		assertTrue(bank > sideBefore, "the snow is banked up beside it, " + sideBefore + " to " + bank);
	}
}
