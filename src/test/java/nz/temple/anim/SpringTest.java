package nz.temple.anim;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpringTest {
	@Test
	void settlesOnTarget() {
		Spring s = new Spring(0, 260, 26);
		s.target = 100;
		for (int i = 0; i < 120; i++) {
			Spring.advance(1 / 60.0, List.of(s));
		}
		assertEquals(100, s.x, 0.01);
		assertTrue(s.atRest());
	}

	@Test
	void underdampedSquashOvershootsButStaysBounded() {
		Spring s = new Spring(0.18, 150, 11);
		s.target = 1;
		double peak = 0;
		for (int i = 0; i < 240; i++) {
			Spring.advance(1 / 60.0, List.of(s));
			peak = Math.max(peak, s.x);
		}
		assertTrue(peak > 1.0, "should overshoot for the bouncy feel");
		assertTrue(peak < 1.35, "overshoot too large: " + peak);
	}

	@Test
	void frameRateDoesNotChangeTheMotion() {
		Spring a = new Spring(0, 300, 30);
		Spring b = new Spring(0, 300, 30);
		a.target = b.target = 50;
		for (int i = 0; i < 30; i++) {
			Spring.advance(1 / 30.0, List.of(a));
		}
		for (int i = 0; i < 144; i++) {
			Spring.advance(1 / 144.0, List.of(b));
		}
		assertEquals(a.x, b.x, 0.05);
	}

	@Test
	void snapStopsMotion() {
		Spring s = new Spring(0, 200, 22);
		s.target = 10;
		Spring.advance(0.05, List.of(s));
		s.snap(3);
		assertEquals(3, s.x);
		assertEquals(3, s.target);
		assertEquals(0, s.velocity);
	}
}
