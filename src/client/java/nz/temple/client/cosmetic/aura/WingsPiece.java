package nz.temple.client.cosmetic.aura;

import nz.temple.client.cosmetic.PieceLook;
import nz.temple.cosmetic.Finishes;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;

public final class WingsPiece extends AuraPiece {
	private final ModeSetting style;
	private final NumberSetting feathers;
	private final ModeSetting atRest;
	private final PieceLook feather;
	private final PieceLook fallen;
	private final PieceLook bat;
	private final PieceLook demon;
	private final PieceLook butterfly;
	public WingsPiece(Host host) {
		super(host, "Wings", true, "Wings that beat slowly at rest and open wide as you move.", true);
		style = host.add(new ModeSetting("Wing style", List.of("Feathered", "Fallen angel", "Bat", "Demon", "Butterfly"), "Feathered")
				.describe("Feathered has layered flight feathers and coverts. Fallen angel is ragged and burnt, shedding feathers and ash. "
						+ "Bat has skin stretched between finger bones. Demon is larger, torn and spiked. Butterfly has veined, patterned wings.")
				.visibleWhen(on::get));
		atRest = host.add(new ModeSetting("Wings at rest", List.of("Spread", "Folded"), "Spread")
				.describe("Standing still, Spread keeps the wings out with a slow beat. Folded lays them against your back.")
				.visibleWhen(on::get));
		feathers = host.add(new NumberSetting("Feathers", 9, 5, 14, 1, "").describe("Flight feathers at the tip of each wing.")
				.visibleWhen(() -> on.get() && (style.is("Feathered") || style.is("Fallen angel"))));
		feather = new PieceLook(host::add, "Feather", PieceLook.of(Finishes.ANGEL, Finishes.RAVEN, Finishes.PHOENIX, Finishes.GOLD,
				Finishes.GLASS), Finishes.ANGEL, 30, () -> showsNew() && style.is("Feathered"));
		fallen = new PieceLook(host::add, "Fallen wing", PieceLook.of(Finishes.SOOT, Finishes.ASH, Finishes.BONE), Finishes.SOOT, 50,
				() -> showsNew() && style.is("Fallen angel"));
		bat = new PieceLook(host::add, "Bat wing", PieceLook.of(Finishes.LEATHER, Finishes.NIGHT, Finishes.CRIMSON), Finishes.LEATHER, 40,
				() -> showsNew() && style.is("Bat"));
		demon = new PieceLook(host::add, "Demon wing", PieceLook.of(Finishes.CRIMSON, Finishes.ASH, Finishes.OBSIDIAN), Finishes.CRIMSON, 60,
				() -> showsNew() && style.is("Demon"));
		butterfly = new PieceLook(host::add, "Butterfly", PieceLook.of(Finishes.MONARCH, Finishes.MORPHO, Finishes.GLASSWING), Finishes.MONARCH,
				30, () -> showsNew() && style.is("Butterfly"));
	}
	@Override
	protected boolean hasOriginal() {
		return style.is("Feathered") || style.is("Bat") || style.is("Butterfly");
	}
	@Override
	public void draw(AuraContext c) {
		WingRig.State st = state(c.player.getUUID(), WingRig.State::new);
		boolean isDemon = style.is("Demon");
		WingRig[] rigs = WingRig.pose(c, st, c.speed, isDemon ? 1.2 : 1, atRest.is("Spread"));
		c.mesh.lightFrom(c.lightPos);
		int primaries = (int) Math.round(feathers.get());
		for (WingRig w : rigs) {
			switch (style.get()) {
				case "Fallen angel" -> FeatheredWings.draw(c, w, fallen, primaries, true);
				case "Bat" -> MembraneWings.draw(c, w, bat, false);
				case "Demon" -> MembraneWings.draw(c, w, demon, true);
				case "Butterfly" -> ButterflyWings.draw(c, w, butterfly);
				default -> FeatheredWings.draw(c, w, feather, primaries, false);
			}
		}
		c.mesh.lightFrom(null);
	}
}
