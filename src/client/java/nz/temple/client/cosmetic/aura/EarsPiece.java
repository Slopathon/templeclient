package nz.temple.client.cosmetic.aura;

import nz.temple.client.cosmetic.PieceLook;
import nz.temple.client.render.Meshes;
import nz.temple.client.render.ModelCache;
import nz.temple.cosmetic.Colours;
import nz.temple.cosmetic.Finish;
import nz.temple.cosmetic.Finishes;
import nz.temple.cosmetic.Model;
import nz.temple.setting.ModeSetting;
import org.joml.Matrix4d;

import java.util.List;

public final class EarsPiece extends AuraPiece {
	private static final int INNER_PINK = 0xFFF0A3B5;
	private final ModeSetting style;
	private final PieceLook look;

	public EarsPiece(Host host) {
		super(host, "Ears", false, "Animal ears that fold back as you move and twitch now and then.", true);
		style = host.add(new ModeSetting("Ear style", List.of("Cat", "Bunny"), "Cat")
				.describe("Cat ears are small and pointed. Bunny ears are long and flop back when you run.")
				.visibleWhen(on::get));
		look = new PieceLook(host::add, "Ear", PieceLook.of(Finishes.FUR, Finishes.VELVET, Finishes.CRYSTAL), Finishes.FUR, 30, this::showsNew);
	}
	private static final class State {
		double moving;
		double flop;
		double flopVel;
		double turn;
		double turnVel;
		double lastYaw = Double.NaN;
	}
	@Override
	public void draw(AuraContext c) {
		boolean bunny = style.is("Bunny");
		Model model = c.model(bunny ? "ear_bunny" : "ear_cat");
		if (model == null || c.cameraInHead) {
			return;
		}
		State st = state(c.player.getUUID(), State::new);
		st.moving += (c.moving() - st.moving) * (1 - Math.exp(-4 * c.dt));
		double target = st.moving * 1.1 + (c.player.onGround() ? 0 : -0.25);
		st.flopVel += ((target - st.flop) * 40 - st.flopVel * 7) * c.dt;
		st.flop += st.flopVel * c.dt;
		double twitch = Math.pow(Math.max(0, Math.sin(c.time * 1.3 + c.player.getId())), 40);
		double dt = Math.max(1e-3, Math.min(0.1, c.dt));
		if (Double.isNaN(st.lastYaw)) {
			st.lastYaw = c.player.getYHeadRot();
		}
		double yawVel = net.minecraft.util.Mth.wrapDegrees(c.player.getYHeadRot() - st.lastYaw) / dt;
		st.lastYaw = c.player.getYHeadRot();
		st.turnVel += ((Math.max(-0.3, Math.min(0.3, -yawVel * 0.0015)) - st.turn) * 70 - st.turnVel * 9) * dt;
		st.turn += st.turnVel * dt;
		double hurt = c.player.hurtTime > 0 ? c.player.hurtTime / 10.0 : 0;
		Finish finish = look.finish();
		Finish innerFinish = finish == Finishes.FUR ? Finishes.VELVET : finish;
		Meshes.ModelPainter painter = new Meshes.ModelPainter() {
			@Override
			public boolean part(Meshes m, Model.Part part) {
				boolean inner = part.name().equals("inner");
				look.apply(m);
				if (inner) {
					m.finish(innerFinish);
				}
				m.pattern(inner ? Meshes.PATTERN_NONE : Meshes.PATTERN_FUR, 1);
				m.accent(0, look.glowColour(0));
				return true;
			}
			@Override
			public int colour(Model.Part part, double ramp) {
				int c0 = look.colour(ramp);
				return switch (part.name()) {
					case "inner" -> Colours.lerp(c0, INNER_PINK, 0.65);
					case "tufts" -> Colours.lerp(c0, 0xFFFFFFFF, 0.55);
					default -> c0;
				};
			}
			@Override
			public double glow(Model.Part part, double mask) {
				return mask * look.glow();
			}
		};
		Meshes mesh = c.mesh;
		mesh.lightFrom(c.lightPos);
		double top = 8 / 16.0 - 0.01;
		for (int side = -1; side <= 1; side += 2) {
			double back = bunny ? 0.15 + st.moving * 0.35 : st.moving * 0.3;
			back += side > 0 ? twitch * 0.4 : 0;
			back += hurt * (bunny ? 0.9 : 0.6);
			double swivel = 0.45 * Math.pow(Math.max(0, Math.sin(c.time * 0.55 + side * 2.1 + c.player.getId() * 0.37)), 8);
			double tilt = bunny ? 0.1 : 0.28;
			double x = bunny ? 0.11 : 0.17;
			double z = bunny ? -0.03 : -0.01;
			Matrix4d base = c.anchor.head().translate(side * x, top, z).rotateY(side * swivel).rotateX(-back)
					.rotateZ(-side * tilt + st.turn).scale(side * c.size, c.size, c.size);
			if (bunny) {
				double hinge = model.joints().size() > 1 ? model.joints().get(1).py() : 0.2;
				double flop = Math.max(-0.3, st.flop) * 1.2 + 0.08 * Math.sin(c.time * 2.1 + side);
				Matrix4d tip = new Matrix4d(base).translate(0, hinge, 0).rotateX(-flop).translate(0, -hinge, 0);
				mesh.model(model, base, new Matrix4d[] {base, tip}, painter);
			} else {
				mesh.model(model, base, null, painter);
			}
		}
		mesh.lightFrom(null);
	}
}
