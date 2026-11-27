package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.VisualRange;
import nz.temple.client.render.WorldEsp;
import nz.temple.client.util.Inv;
import nz.temple.client.util.ProjectileSim;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Trajectories extends Module {
	private final BoolSetting own;
	private final BoolSetting pearls;
	private final BoolSetting arrows;
	private final BoolSetting thrown;
	private final BoolSetting hitEntities;
	private final NumberSetting range;
	private final ColorSetting color;
	private final ColorSetting pearlColor;
	private static final class Physics {
		static final ProjectileSim.Physics ARROW = ProjectileSim.Physics.ARROW;
		static final ProjectileSim.Physics TRIDENT = ProjectileSim.Physics.TRIDENT;
		static final ProjectileSim.Physics THROWN = ProjectileSim.Physics.THROWN;
		static final ProjectileSim.Physics POTION = ProjectileSim.Physics.POTION;
	}
	public Trajectories() {
		super("Trajectories", Category.RENDER, "Shows where projectiles will land: the arc of what you are about to throw, and the path of "
				+ "projectiles already in flight, including other players' ender pearls.");
		own = add(new BoolSetting("Your throws", true)
				.describe("The arc of the bow, crossbow, trident, pearl or other throwable you are holding."));
		pearls = add(new BoolSetting("Others' pearls", true)
				.describe("Predicts where other players' ender pearls will land and marks the spot."));
		arrows = add(new BoolSetting("Arrows in flight", false)
				.describe("Predicts the rest of the path for arrows and tridents in flight."));
		thrown = add(new BoolSetting("Other throwables", false)
				.describe("Predicts the path of potions, snowballs, eggs and other thrown items in flight."));
		hitEntities = add(new BoolSetting("Stop at entities", true)
				.describe("Ends the path at the first entity it would hit and highlights that entity."));
		range = add(new NumberSetting("Range", 96, 16, 256, 8, " m").describe("Only predicts projectiles within this distance."));
		group();
		color = add(new ColorSetting("Color", 0xFF5AC8FA).themed().describe("Colour for your own throws and other projectiles."));
		pearlColor = add(new ColorSetting("Pearl color", 0xFFB86BFF).themedSecondary().describe("Colour for other players' pearls."));
	}
	public void collect(List<WorldEsp.Box> out) {
		for (Shown shown : compute()) {
			int c = shown.colour;
			int outline = 0xFF000000 | (c & 0xFFFFFF);
			if (shown.flight.hitEntity() != null) {
				out.add(new WorldEsp.Box(shown.flight.hitEntity().getBoundingBox().inflate(0.05), outline, (60 << 24) | (c & 0xFFFFFF), 0.008f));
			} else if (shown.flight.hitBlock() != null) {
				out.add(new WorldEsp.Box(new AABB(shown.flight.hitBlock()), outline, (70 << 24) | (c & 0xFFFFFF), 0.006f));
			}
			Vec3 e = shown.flight.end();
			double pulse = 0.08 + 0.03 * Math.sin(System.nanoTime() / 1e9 * (6 + 20.0 / Math.max(1, shown.flight.ticks())));
			out.add(new WorldEsp.Box(new AABB(e.x - pulse, e.y - pulse, e.z - pulse, e.x + pulse, e.y + pulse, e.z + pulse),
					outline, (160 << 24) | (c & 0xFFFFFF), 0.006f));
		}
	}
	public void lines(List<WorldEsp.Line> out, CameraRenderState camera, float partialTick) {
		for (Shown shown : compute()) {
			List<Vec3> pts = shown.flight.points();
			int c = shown.colour;
			for (int i = 1; i < pts.size(); i++) {
				double k = 1 - 0.6 * i / pts.size();
				int argb = ((int) Math.round(255 * k) << 24) | (c & 0xFFFFFF);
				Vec3 a = pts.get(i - 1);
				Vec3 b = pts.get(i);
				out.add(new WorldEsp.Line(a.x, a.y, a.z, b.x, b.y, b.z, argb, shown.own ? 2f : 1.5f));
			}
		}
	}
	private record Shown(ProjectileSim.Flight flight, int colour, boolean own) {
	}
	private List<Shown> lastShown = List.of();
	private long lastComputed;

	private List<Shown> compute() {
		long now = System.nanoTime();
		if (now - lastComputed < 5_000_000L) {
			return lastShown;
		}
		lastComputed = now;
		List<Shown> result = new ArrayList<>();
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (!isEnabled() || player == null || mc.level == null) {
			lastShown = result;
			return result;
		}
		if (own.get()) {
			ProjectileSim.Flight f = ownFlight(mc, player);
			if (f != null) {
				result.add(new Shown(f, color.get(), true));
			}
		}
		double maxSq = VisualRange.of(range.get()) * VisualRange.of(range.get());
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (!(entity instanceof Projectile projectile) || entity.distanceToSqr(player) > maxSq) {
				continue;
			}
			String id = EntityType.getKey(entity.getType()).getPath();
			boolean isPearl = id.contains("ender_pearl");
			ProjectileSim.Physics physics = ProjectileSim.physicsFor(id);
			if (physics == null || isPearl && !pearls.get() || !isPearl && (id.contains("arrow") || id.contains("trident")) && !arrows.get()
					|| !isPearl && !(id.contains("arrow") || id.contains("trident")) && !thrown.get()) {
				continue;
			}
			if (entity.getDeltaMovement().lengthSqr() < 1e-4 || entity.onGround()) {
				continue;
			}
			Entity owner = projectile.getOwner();
			if (isPearl && owner == player) {
				continue;
			}
			ProjectileSim.Flight f = ProjectileSim.simulate(entity.position(), entity.getDeltaMovement(), physics, owner, entity, 200,
					hitEntities.get());
			if (f != null) {
				result.add(new Shown(f, isPearl ? pearlColor.get() : color.get(), false));
			}
		}
		lastShown = result;
		return result;
	}
	private ProjectileSim.Flight ownFlight(Minecraft mc, LocalPlayer player) {
		ItemStack hand = player.getMainHandItem();
		String p = Inv.path(hand);
		double speed;
		double pitchOffset = 0;
		ProjectileSim.Physics physics;
		if (p.equals("bow")) {
			if (!player.isUsingItem()) {
				return null;
			}
			float charge = BowItem.getPowerForTime(player.getTicksUsingItem());
			if (charge < 0.1f) {
				return null;
			}
			speed = charge * 3.0;
			physics = Physics.ARROW;
		} else if (p.equals("crossbow")) {
			if (!CrossbowItem.isCharged(hand)) {
				return null;
			}
			speed = 3.15;
			physics = Physics.ARROW;
		} else if (p.equals("trident")) {
			if (!player.isUsingItem() || player.getTicksUsingItem() < 10) {
				return null;
			}
			speed = 2.5;
			physics = Physics.TRIDENT;
		} else if (p.equals("ender_pearl") || p.equals("snowball") || p.equals("egg")) {
			speed = 1.5;
			physics = Physics.THROWN;
		} else if (p.contains("splash_potion") || p.contains("lingering_potion")) {
			speed = 0.5;
			pitchOffset = -20;
			physics = Physics.POTION;
		} else if (p.equals("experience_bottle")) {
			speed = 0.7;
			pitchOffset = -20;
			physics = Physics.POTION;
		} else {
			return null;
		}
		Vec3 look = Vec3.directionFromRotation(player.getXRot() + (float) pitchOffset, player.getYRot());
		Vec3 motion = player.getDeltaMovement();
		Vec3 inherited = new Vec3(motion.x, player.onGround() ? 0 : motion.y, motion.z);
		Vec3 start = player.getEyePosition().subtract(0, 0.1, 0);
		return ProjectileSim.simulate(start, look.scale(speed).add(inherited), physics, player, null, 300, hitEntities.get());
	}
}
