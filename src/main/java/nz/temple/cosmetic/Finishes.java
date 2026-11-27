package nz.temple.cosmetic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Finishes {
	public static final int CAPACITY = 64;
	private static final List<Finish> ALL = new ArrayList<>();
	private static final Map<String, Integer> BY_NAME = new LinkedHashMap<>();
	public static final Finish LEGACY_MATTE = add(legacy("Legacy matte", 0));
	public static final Finish LEGACY_METAL = add(legacy("Legacy metal", 1));
	public static final Finish LEGACY_GLASS = add(legacy("Legacy glass", 2));
	public static final Finish LEGACY_NEON = add(legacy("Legacy neon", 3));
	public static final Finish KERATIN = add(Finish.of("Keratin", 0.45f, 0, 0xFF3A2C22, 0xFF8C7458, 0xFFE6D6B8).clearcoat(0.3f).subsurface(0.2f));
	public static final Finish OBSIDIAN = add(Finish.of("Obsidian", 0.12f, 0, 0xFF07060A, 0xFF1C1724, 0xFF4A3F5C).clearcoat(0.7f));
	public static final Finish BONE = add(Finish.of("Bone", 0.6f, 0, 0xFF8A7D66, 0xFFD9CDB2, 0xFFF6F0E0).subsurface(0.15f));
	public static final Finish GOLD = add(Finish.of("Gold", 0.22f, 1, 0xFF6B4A12, 0xFFD9A635, 0xFFFFE9A8));
	public static final Finish SILVER = add(Finish.of("Silver", 0.2f, 1, 0xFF4A4E55, 0xFFB9BEC6, 0xFFF4F6FA));
	public static final Finish STEEL = add(Finish.of("Steel", 0.28f, 1, 0xFF3C4148, 0xFF9AA2AC, 0xFFE8EDF2));
	public static final Finish BRASS = add(Finish.of("Brass", 0.3f, 1, 0xFF5A3E14, 0xFFB48A3C, 0xFFF0D490));
	public static final Finish GEMSTONE = add(Finish.of("Gemstone", 0.02f, 0, 0xFF300810, 0xFFC0182E, 0xFFFF8090).transmission(0.3f)
			.clearcoat(1).iridescence(0.15f));
	public static final Finish PEARL = add(Finish.of("Pearl", 0.25f, 0, 0xFFB8AE9C, 0xFFF0EADC, 0xFFFFFFFF).sheen(0.3f).clearcoat(0.6f)
			.iridescence(0.5f));
	public static final Finish ERMINE = add(Finish.of("Ermine", 0.9f, 0, 0xFFBDB8B0, 0xFFF2F0EC, 0xFFFFFFFF).sheen(0.7f).subsurface(0.3f));
	public static final Finish CRYSTAL = add(Finish.of("Crystal", 0.05f, 0, 0xFF6F93B8, 0xFFD8ECFF, 0xFFFFFFFF).transmission(0.85f).iridescence(0.2f));
	public static final Finish QUARTZ = add(Finish.of("Quartz", 0.06f, 0, 0xFFB8C0C8, 0xFFF0F4F8, 0xFFFFFFFF).transmission(0.7f));
	public static final Finish AMETHYST = add(Finish.of("Amethyst", 0.08f, 0, 0xFF2A0E4A, 0xFF7A3EC0, 0xFFD8B0FF).transmission(0.6f));
	public static final Finish GLASS = add(Finish.of("Glass", 0.03f, 0, 0xFF8FB0C8, 0xFFE4F2FF, 0xFFFFFFFF).transmission(0.9f));
	public static final Finish ENERGY = add(Finish.of("Energy", 0.1f, 0, 0xFF1A3A6A, 0xFF5AA0FF, 0xFFE0F0FF).transmission(0.92f));
	public static final Finish FUR = add(Finish.of("Fur", 0.9f, 0, 0xFF4A3424, 0xFF9C7A5A, 0xFFE8D6C0).sheen(0.6f).subsurface(0.3f));
	public static final Finish VELVET = add(Finish.of("Velvet", 0.85f, 0, 0xFF2A0A12, 0xFF6E1A2C, 0xFFB2405A).sheen(0.9f));
	public static final Finish SILK = add(Finish.of("Silk", 0.35f, 0, 0xFF5A4A3A, 0xFFC8B49A, 0xFFF8EEDC).sheen(0.5f));
	public static final Finish SATIN = add(Finish.of("Satin", 0.25f, 0, 0xFF3A1A2A, 0xFF9A4A6A, 0xFFF0B8D0).sheen(0.4f).clearcoat(0.3f));
	public static final Finish STRAW = add(Finish.of("Straw", 0.85f, 0, 0xFF6E5420, 0xFFC8A458, 0xFFF2DFA0).subsurface(0.1f));
	public static final Finish LACQUERED = add(Finish.of("Lacquered", 0.15f, 0, 0xFF3A0808, 0xFF8E1A14, 0xFFD8503C).clearcoat(0.9f));
	public static final Finish LIGHT = add(Finish.of("Light", 0.3f, 0, 0xFFB89A50, 0xFFFFE8B0, 0xFFFFFFFF).subsurface(0.6f));
	public static final Finish FLAME = add(Finish.of("Flame", 1, 0, 0xFF5A1404, 0xFFE8661A, 0xFFFFE6A0).subsurface(1));
	public static final Finish WISP = add(Finish.of("Wisp", 1, 0, 0xFF4A6A8A, 0xFFB8E0FF, 0xFFFFFFFF).subsurface(1).transmission(0.3f));
	public static final Finish SCALES = add(Finish.of("Scales", 0.35f, 0, 0xFF0E3A3C, 0xFF2E8C8F, 0xFF8EE0D8).clearcoat(0.4f).iridescence(0.2f));
	public static final Finish STONE = add(Finish.of("Stone", 0.85f, 0, 0xFF1E1C24, 0xFF4A4654, 0xFF8A8494));
	public static final Finish ANGEL = add(Finish.of("Angel white", 0.7f, 0, 0xFF9C958A, 0xFFE0DBD1, 0xFFF8F6F1).sheen(0.25f).subsurface(0.25f));
	public static final Finish RAVEN = add(Finish.of("Raven", 0.5f, 0, 0xFF07080C, 0xFF1A1C24, 0xFF3E4458).sheen(0.3f).iridescence(0.35f));
	public static final Finish PHOENIX = add(Finish.of("Phoenix", 0.6f, 0, 0xFF5A0E04, 0xFFD8460E, 0xFFFFD05A).subsurface(0.4f));
	public static final Finish ASH = add(Finish.of("Ash", 0.8f, 0, 0xFF141212, 0xFF3A3434, 0xFF6E6464));
	public static final Finish SOOT = add(Finish.of("Soot", 0.75f, 0, 0xFF050505, 0xFF161414, 0xFF34302E).sheen(0.2f));
	public static final Finish LEATHER = add(Finish.of("Leather", 0.65f, 0, 0xFF1E1410, 0xFF4A3024, 0xFF8A6450).subsurface(0.5f));
	public static final Finish NIGHT = add(Finish.of("Night", 0.6f, 0, 0xFF08080E, 0xFF1A1A2A, 0xFF3C3C58).subsurface(0.4f));
	public static final Finish CRIMSON = add(Finish.of("Crimson", 0.55f, 0, 0xFF2A0406, 0xFF6E0C12, 0xFFC0303A).subsurface(0.5f));
	public static final Finish MONARCH = add(Finish.of("Monarch", 0.5f, 0, 0xFF3A1804, 0xFFE07A10, 0xFFFFC060).subsurface(0.3f));
	public static final Finish MORPHO = add(Finish.of("Morpho blue", 0.35f, 0, 0xFF04102A, 0xFF1A5AD8, 0xFF8AD0FF).iridescence(0.9f));
	public static final Finish GLASSWING = add(Finish.of("Glasswing", 0.08f, 0, 0xFF2A2420, 0xFFE8E4DE, 0xFFFFFFFF).transmission(0.85f).iridescence(0.3f));
	private Finishes() {
	}
	private static Finish legacy(String name, int code) {
		return new Finish(name, 0.5f, 0, 0, 0, 0, 0, 0, new int[] {0xFF000000, 0xFF808080, 0xFFFFFFFF}, code);
	}

	private static Finish add(Finish finish) {
		if (ALL.size() >= CAPACITY) {
			throw new IllegalStateException("Finish table is full");
		}
		if (BY_NAME.putIfAbsent(finish.name(), ALL.size()) != null) {
			throw new IllegalStateException("Duplicate finish " + finish.name());
		}
		ALL.add(finish);
		return finish;
	}
	public static List<Finish> all() {
		return Collections.unmodifiableList(ALL);
	}

	public static Finish get(String name, Finish fallback) {
		Integer i = BY_NAME.get(name);
		return i == null ? fallback : ALL.get(i);
	}
	public static int index(Finish finish) {
		Integer i = BY_NAME.get(finish.name());
		if (i == null || ALL.get(i) != finish) {
			throw new IllegalArgumentException("Unregistered finish " + finish.name());
		}
		return i;
	}
}
