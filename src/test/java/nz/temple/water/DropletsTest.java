package nz.temple.water;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropletsTest {
	private static Droplets.World floor(java.util.function.Predicate<int[]> extra, java.util.function.Predicate<int[]> water) {
		return new Droplets.World() {
			@Override
			public boolean solid(int x, int y, int z) {
				return y < 60 || extra.test(new int[] {x, y, z});
			}

			@Override
			public boolean water(int x, int y, int z) {
				return water.test(new int[] {x, y, z});
			}
		};
	}

	private static Droplets.World flat() {
		return floor(p -> false, p -> false);
	}

	private static void run(Droplets d, Droplets.World world, float seconds) {
		for (int i = 0; i < Math.round(seconds * 60); i++) {
			d.step(1 / 60f, world, List.of());
		}
	}

	@Test
	void aBucketIsThousandsOfDroplets() {
		Droplets d = new Droplets(50000);
		d.spawnBlock(0, 61, 0, 0, 0, 0);
		assertEquals(Droplets.PER_SIDE * Droplets.PER_SIDE * Droplets.PER_SIDE, d.count());
		assertTrue(d.count() > 2500);
	}

	@Test
	void aPlacedBlockFillsItsBlock() {
		Droplets d = new Droplets(50000);
		d.spawnBlock(0, 61, 0, 0, 0, 0);
		double minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
		for (int i = 0; i < d.count(); i++) {
			minX = Math.min(minX, d.x(i));
			maxX = Math.max(maxX, d.x(i));
			minY = Math.min(minY, d.y(i));
			maxY = Math.max(maxY, d.y(i));
		}
		assertTrue(minX < 0.08 && maxX > 0.92 && minY < 61.08 && maxY > 61.92, "edge to edge: " + minX + ".." + maxX);
	}

	@Test
	void waterInAHoleKeepsItsVolume() {
		Droplets.World hole = new Droplets.World() {
			@Override
			public boolean solid(int x, int y, int z) {
				return y < 59 || y == 59 && !(x == 0 && z == 0);
			}

			@Override
			public boolean water(int x, int y, int z) {
				return false;
			}
		};
		Droplets d = new Droplets(50000);
		d.spawnBlock(0, 59, 0, 0, 0, 0);
		run(d, hole, 3);
		double top = 0;
		for (int i = 0; i < d.count(); i++) {
			top = Math.max(top, d.y(i));
		}
		assertTrue(top > 59.85, "still fills the hole: top at " + top);
	}

	@Test
	void dropletsRestOnTheShapesOfBlocksNotWholeCubes() {
		Droplets.World slabs = new Droplets.World() {
			@Override
			public boolean solid(int x, int y, int z) {
				return y < 60;
			}

			@Override
			public float[] boxes(int x, int y, int z) {
				if (y < 60) {
					return FULL;
				}
				return y == 60 && x >= 0 && x <= 3 && z >= -3 && z <= 3 ? new float[] {0, 0, 0, 1, 0.5f, 1} : null;
			}

			@Override
			public boolean water(int x, int y, int z) {
				return false;
			}
		};
		Droplets d = new Droplets(50000);
		d.spawnBlock(1, 62, 0, 0, 0, 0);
		run(d, slabs, 2);
		int onSlab = 0;
		int sunk = 0;
		for (int i = 0; i < d.count(); i++) {
			if (d.x(i) > 0.2 && d.x(i) < 3.8 && Math.abs(d.z(i)) < 2.8) {
				onSlab++;
				if (d.y(i) < 60.5) {
					sunk++;
				}
			}
		}
		assertTrue(onSlab > 100, "water lies on the slabs: " + onSlab);
		assertEquals(0, sunk, "and none of it sinks into them");
	}

	@Test
	void separatePiecesStayApartUntilTheyTouch() {
		Droplets d = new Droplets(50000);
		d.spawnBlock(0, 70, 0, 0, 0, 0);
		d.spawnBlock(2, 70, 0, 0, 0, 0);
		Droplets.World air = floor(p -> false, p -> false);
		for (int i = 0; i < 12; i++) {
			d.step(1 / 60f, air, List.of());
		}
		double gapLeft = -1e9, gapRight = 1e9;
		for (int i = 0; i < d.count(); i++) {
			if (d.x(i) < 1.5) {
				gapLeft = Math.max(gapLeft, d.x(i));
			} else {
				gapRight = Math.min(gapRight, d.x(i));
			}
		}
		assertTrue(gapRight - gapLeft > 0.7, "the gap stays open: " + (gapRight - gapLeft));
	}

	@Test
	void dropletsFallAndComeToRestOnTheGround() {
		Droplets d = new Droplets(50000);
		d.spawnBlock(0, 63, 0, 0, 0, 0);
		run(d, flat(), 4);
		for (int i = 0; i < d.count(); i++) {
			assertTrue(d.y(i) >= 60 - 1e-3, "never sinks into the ground: " + d.y(i));
			assertTrue(d.y(i) < 61, "has come down: " + d.y(i));
		}
	}

	@Test
	void aBlockOfWaterSpreadsOutButHoldsTogether() {
		Droplets d = new Droplets(50000);
		d.spawnBlock(0, 60, 0, 0, 0, 0);
		run(d, flat(), 3);
		double minX = 1e9, maxX = -1e9;
		for (int i = 0; i < d.count(); i++) {
			minX = Math.min(minX, d.x(i));
			maxX = Math.max(maxX, d.x(i));
		}
		assertTrue(maxX - minX > 1.6, "spreads wider than the block it came from: " + (maxX - minX));
		assertTrue(maxX - minX < 16, "but does not fly apart: " + (maxX - minX));
		int loose = 0;
		for (int i = 0; i < d.count(); i++) {
			boolean near = false;
			for (int j = 0; j < d.count() && !near; j++) {
				if (i != j && dist(d, i, j) < Droplets.SPACING * 2.5) {
					near = true;
				}
			}
			loose += near ? 0 : 1;
		}
		assertTrue(loose < d.count() / 10, "loose droplets: " + loose);
	}

	@Test
	void aHoleHoldsItsWaterPouredFromAbove() {
		Droplets.World hole = new Droplets.World() {
			@Override
			public boolean solid(int x, int y, int z) {
				return y < 59 || y == 59 && !(x == 0 && z == 0);
			}

			@Override
			public boolean water(int x, int y, int z) {
				return false;
			}
		};
		Droplets d = new Droplets(50000);
		d.spawnBlock(0, 60, 0, 0, 0, 0);
		run(d, hole, 4);
		int inside = 0;
		for (int i = 0; i < d.count(); i++) {
			if (d.x(i) >= 0 && d.x(i) < 1 && d.z(i) >= 0 && d.z(i) < 1) {
				inside++;
			}
		}
		assertTrue(inside > d.count() * 0.6, "most of it ends up in the hole: " + inside + " of " + d.count());
	}

	@Test
	void dropletsReachingStandingWaterJoinIt() {
		Droplets.World lake = floor(p -> false, p -> p[0] >= 2 && p[1] == 60);
		Droplets d = new Droplets(50000);
		d.spawnBlock(1, 61, 0, 3, 0, 0);
		int poured = d.count();
		run(d, lake, 4);
		assertTrue(d.count() < poured * 0.04, "most droplets merged into the lake: " + d.count() + " of " + poured + " left");
	}

	@Test
	void waterAtRestFallsAsleepAndStays() {
		Droplets d = new Droplets(50000);
		int group = d.spawnBlock(0, 61, 0, 0, 0, 0);
		run(d, flat(), 12);
		assertTrue(d.asleep(group), "at rest it sleeps");
		assertEquals(Droplets.PER_SIDE * Droplets.PER_SIDE * Droplets.PER_SIDE, d.count(), "and none of it goes away");
		assertTrue(d.takeSettled().isEmpty());
	}

	@Test
	void waterPouredOnSleepingWaterWakesIt() {
		Droplets d = new Droplets(50000);
		int below = d.spawnBlock(0, 60, 0, 0, 0, 0);
		run(d, flat(), 12);
		int restless = d.awake(below);
		assertTrue(restless < d.count() / 10, "nearly all of it sleeps: " + restless + " awake");
		d.spawnBlock(0, 61, 0, 0, -3, 0);
		run(d, flat(), 0.6f);
		assertTrue(d.awake(below) > restless + 100, "the new water landing on it wakes it: " + d.awake(below) + " awake");
	}

	@Test
	void aGroupThatMergedAwayIsReported() {
		Droplets.World lake = floor(p -> false, p -> p[0] >= 1 && p[1] == 60);
		Droplets d = new Droplets(50000);
		int group = d.spawnBlock(1, 60, 0, 0, 0, 0);
		List<Integer> settled = new ArrayList<>();
		for (int i = 0; i < 60 && settled.isEmpty(); i++) {
			d.step(1 / 60f, lake, List.of());
			settled.addAll(d.takeSettled());
		}
		assertEquals(List.of(group), settled);
	}

	@Test
	void somethingWalkingThroughPushesDropletsAside() {
		Droplets d = new Droplets(50000);
		d.spawnBlock(0, 60, 0, 0, 0, 0);
		run(d, flat(), 3);
		for (int i = 0; i < 90; i++) {
			double x = -3 + i * 0.07;
			d.step(1 / 60f, flat(), List.of(new double[] {x - 0.3, 60, 0.2, x + 0.3, 61.8, 0.8, 4.2, 0, 0}));
		}
		int inPath = 0;
		for (int i = 0; i < d.count(); i++) {
			if (d.z(i) > 0.25 && d.z(i) < 0.75 && d.x(i) > -1 && d.x(i) < 2) {
				inPath++;
			}
		}
		assertTrue(inPath < d.count() / 8, "the path is cleared: " + inPath);
	}

	private static double dist(Droplets d, int i, int j) {
		double dx = d.x(i) - d.x(j), dy = d.y(i) - d.y(j), dz = d.z(i) - d.z(j);
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
