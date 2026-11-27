package nz.temple.cosmetic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinishesTest {
	@Test
	void legacyMaterialsKeepTheirShaderCodes() {
		assertEquals(0, Finishes.index(Finishes.LEGACY_MATTE));
		assertEquals(1, Finishes.index(Finishes.LEGACY_METAL));
		assertEquals(2, Finishes.index(Finishes.LEGACY_GLASS));
		assertEquals(3, Finishes.index(Finishes.LEGACY_NEON));
		for (int i = 0; i < 4; i++) {
			assertEquals(i, Finishes.all().get(i).legacy());
		}
		assertTrue(Finishes.all().stream().skip(4).noneMatch(Finish::isLegacy));
	}

	@Test
	void everyFinishFitsTheShaderTableAndLooksUpByName() {
		assertTrue(Finishes.all().size() <= Finishes.CAPACITY);
		for (Finish f : Finishes.all()) {
			assertSame(f, Finishes.get(f.name(), null));
		}
		assertSame(Finishes.GOLD, Finishes.get("No such finish", Finishes.GOLD));
	}

	@Test
	void unregisteredFinishIsRejected() {
		Finish stray = Finish.of("Gold", 0.2f, 1, 0, 0, 0);
		assertThrows(IllegalArgumentException.class, () -> Finishes.index(stray));
	}

	@Test
	void naturalRampRunsDarkToLight() {
		Finish f = Finishes.KERATIN;
		assertEquals(f.ramp()[0], f.natural(0));
		assertEquals(f.ramp()[1], f.natural(0.5));
		assertEquals(f.ramp()[2], f.natural(1));
		assertTrue(Colours.luminance(f.natural(0.2)) < Colours.luminance(f.natural(0.8)));
	}

	@Test
	void tintKeepsTheRampLightnessAndTakesTheTintHue() {
		Finish f = Finishes.KERATIN;
		int gold = 0xFFE9B949;
		int dark = f.tinted(0, gold);
		int light = f.tinted(1, gold);
		assertTrue(Colours.luminance(dark) < Colours.luminance(light));
		int mid = f.tinted(0.5, gold);
		assertTrue(((mid >> 16) & 0xFF) > (mid & 0xFF));
	}

	@Test
	void surfaceDataSurvivesTheSignedVertexSlots() {
		int a = SurfacePacking.throughShort(SurfacePacking.finishAndPattern(63, 200));
		assertEquals(63, SurfacePacking.finish(a));
		assertEquals(200, SurfacePacking.pattern(a));
		int b = SurfacePacking.throughShort(SurfacePacking.accentAndOcclusion(1, 0.5));
		assertEquals(1, SurfacePacking.accent(b), 1e-9);
		assertEquals(0.5, SurfacePacking.occlusion(b), 0.003);
		assertEquals(0xFFE9B949 & 0xFFFFFF, SurfacePacking.accentColour(SurfacePacking.accentColour(0xFFE9B949)));
		assertEquals(0xFFFFFF, SurfacePacking.accentColour(SurfacePacking.accentColour(0xFFFFFFFF)));
	}

	@Test
	void vividKeepsHueAndLeavesGreys() {
		int gold = Colours.vivid(0xFFE9B949);
		assertEquals(255, (gold >> 16) & 0xFF);
		assertTrue(((gold >> 8) & 0xFF) > (gold & 0xFF));
		assertEquals(0xFF808080, Colours.vivid(0xFF808080));
		assertEquals(0x80000000, Colours.vivid(0x80FFE3A1) & 0xFF000000);
	}
}
