package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.cosmetic.aura.AuraContext;
import nz.temple.client.cosmetic.aura.AuraPiece;
import nz.temple.client.modules.cosmetic.Previewable;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Theme;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;
import nz.temple.setting.Setting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PlayerAura extends Module implements Previewable, nz.temple.client.render.FxSource {
	private final ModeSetting who;
	private final NumberSetting size;
	private final NumberSetting speed;
	private final BoolSetting firstPerson;
	private final List<AuraPiece> pieces = new ArrayList<>();
	private long lastNanos = System.nanoTime();
	private double frameDt;
	public PlayerAura() {
		super("Player Aura", Category.COSMETICS, "Wings, halos, crowns, horns, ears and other 3D pieces worn by players. "
				+ "Each piece has its own look, and any combination can be worn.");
		who = add(new ModeSetting("Who", List.of("Self", "Players", "Both"), "Self").describe("Who wears the pieces."));
		size = add(new NumberSetting("Size", 1, 0.3, 3, 0.05, "x").describe("Overall scale."));
		speed = add(new NumberSetting("Speed", 1, 0.2, 3, 0.1, "x").describe("Animation speed."));
		firstPerson = add(new BoolSetting("Show in first person", true)
				.describe("Shows your own pieces in first person where they are not in the way. Wings show when you look down or turn."));
		AuraPiece.Host host = new AuraPiece.Host() {
			@Override
			public <S extends Setting<?>> S add(S setting) {
				return PlayerAura.this.add(setting);
			}
			@Override
			public void group(String title) {
				PlayerAura.this.group(title);
			}
		};
		pieces.add(new nz.temple.client.cosmetic.aura.WingsPiece(host));
		pieces.add(new nz.temple.client.cosmetic.aura.HaloPiece(host));
		pieces.add(new nz.temple.client.cosmetic.aura.CrownPiece(host));
		pieces.add(new nz.temple.client.cosmetic.aura.CrownOfFirePiece(host));
		pieces.add(new nz.temple.client.cosmetic.aura.HornsPiece(host));
		pieces.add(new nz.temple.client.cosmetic.aura.EarsPiece(host));
		nz.temple.client.cosmetic.aura.HatPiece hat = new nz.temple.client.cosmetic.aura.HatPiece(host);
		pieces.add(hat);
		pieces.add(new nz.temple.client.cosmetic.aura.CrystalsPiece(host));
		pieces.add(new nz.temple.client.cosmetic.aura.BladesPiece(host));
		pieces.add(new nz.temple.client.cosmetic.aura.GyroscopePiece(host));
		pieces.add(new nz.temple.client.cosmetic.aura.ShieldPiece(host));
		pieces.add(new nz.temple.client.cosmetic.aura.RunesPiece(host));
		pieces.add(new nz.temple.client.cosmetic.aura.SpiritsPiece(host));
		pieces.add(new nz.temple.client.cosmetic.aura.RibbonsPiece(host));
		legacy(new nz.temple.client.modules.legacy.LegacyPlayerAura(), () -> false, List.of());
		List<List<Setting<?>>> theirs = legacyModule().groups();
		originalLook = new ArrayList<>(theirs.get(theirs.size() - 1));
		((nz.temple.client.modules.legacy.LegacyPlayerAura) legacyModule()).followPieces(
				name -> pieces.stream().filter(piece -> piece.name().equals(name)).findFirst().map(AuraPiece::inFirstPerson).orElse(true),
				hat::hatSize);
		for (Setting<?> s : originalLook) {
			s.andVisibleWhen(() -> pieces.stream().anyMatch(AuraPiece::usesOriginal));
		}
	}
	private final List<Setting<?>> originalLook;
	@Override
	public List<List<Setting<?>>> groups() {
		List<List<Setting<?>>> all = new ArrayList<>(super.groups());
		all.add(originalLook);
		return all;
	}
	@Override
	public String groupTitle(int index) {
		return index == super.groups().size() ? "Original look" : super.groupTitle(index);
	}
	@Override
	protected void syncToLegacy() {
		super.syncToLegacy();
		for (AuraPiece piece : pieces) {
			Setting<?> toggle = legacyModule().setting(piece.name());
			if (toggle instanceof BoolSetting b && piece.realismToggle() != null) {
				b.setQuietly(piece.usesOriginal());
			}
		}
	}
	@Override
	public void preview(Vec3 at) {
	}
	public void emit(Fx fx, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null || mc.player == null) {
			return;
		}
		boolean anyOriginal = pieces.stream().anyMatch(AuraPiece::usesOriginal);
		runLegacyAlongside(anyOriginal);
		if (anyOriginal) {
			((nz.temple.client.modules.legacy.LegacyPlayerAura) legacyModule()).emit(fx, partial);
		}
		long now = System.nanoTime();
		frameDt = Math.min(0.1, (now - lastNanos) / 1e9);
		lastNanos = now;
		boolean seesSelf = !mc.options.getCameraType().isFirstPerson() || nz.temple.client.util.DetachedCamera.active();
		Set<UUID> present = new HashSet<>();
		for (Player player : mc.level.players()) {
			present.add(player.getUUID());
			boolean self = player == mc.player;
			if (nz.temple.client.util.Wearer.mine(player) ? who.is("Players") : who.is("Self")) {
				continue;
			}
			if (!player.isAlive() || player.isInvisible() || player.distanceToSqr(mc.player) > 96 * 96) {
				continue;
			}
			if (self && !seesSelf && !firstPerson.get()) {
				continue;
			}
			draw(fx, player, partial);
		}
		for (AuraPiece piece : pieces) {
			piece.forget(present);
		}
	}
	private void draw(Fx fx, Player player, float partial) {
		fx.mesh().transform(nz.temple.client.modules.cosmetic.Emotes.auraTransform(player, partial));
		try {
			AuraContext ctx = null;
			Minecraft client = Minecraft.getInstance();
			boolean ownFirstPerson = player == client.player && client.options.getCameraType().isFirstPerson()
					&& !nz.temple.client.util.DetachedCamera.active();
			for (AuraPiece piece : pieces) {
				if (piece.on() && !piece.usesOriginal() && !(ownFirstPerson && !piece.inFirstPerson())) {
					if (ctx == null) {
						Minecraft mc = Minecraft.getInstance();
						boolean detailed = player == mc.player || player.distanceToSqr(mc.player) < 20 * 20;
						ctx = new AuraContext(fx, player, partial, Theme.time() * speed.get(), frameDt, size.get(), speed.get(), detailed);
					}
					piece.draw(ctx);
				}
			}
		} finally {
			fx.mesh().transform(null);
		}
	}
}
