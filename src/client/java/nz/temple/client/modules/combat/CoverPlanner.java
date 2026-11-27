package nz.temple.client.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.render.WorldEsp;
import nz.temple.client.util.Blast;
import nz.temple.client.util.Place;
import nz.temple.client.util.Targets;
import nz.temple.client.util.Teleport;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.NumberSetting;
import nz.temple.social.FriendManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CoverPlanner extends Module {
	private record BlastSpot(Vec3 center, float enemyDamage, boolean enemyReachable) {
	}

	private record Tile(BlockPos pos, float danger, float outgoing, int exits, int mineTicks, double score) {
	}

	private final FriendManager friends;
	private final NumberSetting radius;
	private final NumberSetting targetRange;
	private final NumberSetting placeReach;
	private final NumberSetting updateEvery;
	private final NumberSetting shown;
	private final NumberSetting dangerWeight;
	private final NumberSetting attackWeight;
	private final BoolSetting showRoute;
	private final ColorSetting bestColor;
	private final ColorSetting safeColor;
	private final ColorSetting dangerColor;

	private List<Tile> tiles = List.of();
	private List<Vec3> route = List.of();
	private int wait;

	public CoverPlanner(FriendManager friends) {
		super("Cover Planner", Category.HVH, "Ranks nearby positions by explosion cover, attack opportunities, escape routes and mining "
				+ "time, and draws the route to the best one.");
		this.friends = friends;
		radius = add(new NumberSetting("Radius", 6, 2, 10, 1, " m").describe("How far around you to search for reachable positions."));
		targetRange = add(new NumberSetting("Target range", 12, 4, 20, 1, " m").describe("Maximum range for the enemy used to score outgoing damage."));
		placeReach = add(new NumberSetting("Crystal reach", 4.5, 3, 6, 0.1, " m").describe("How far a crystal opportunity may be from a planned position."));
		updateEvery = add(new NumberSetting("Update every", 8, 1, 20, 1, " ticks").describe("How often to recalculate the plan."));
		shown = add(new NumberSetting("Shown tiles", 18, 1, 50, 1, "").describe("How many of the best reachable positions to colour."));
		group();
		dangerWeight = add(new NumberSetting("Danger weight", 1.25, 0, 3, 0.05, "x").describe("How strongly incoming explosion damage lowers a position's rank."));
		attackWeight = add(new NumberSetting("Attack weight", 0.8, 0, 3, 0.05, "x").describe("How strongly outgoing crystal damage raises a position's rank."));
		showRoute = add(new BoolSetting("Show route", true).describe("Draw the walking route to the highest-ranked position."));
		group();
		bestColor = add(new ColorSetting("Best color", 0xFFE9B949).themed().describe("Colour of the recommended position."));
		safeColor = add(new ColorSetting("Safe color", 0xFF43D17B).describe("Colour of low-danger alternatives."));
		dangerColor = add(new ColorSetting("Danger color", 0xFFFF4D5E).describe("Colour of exposed alternatives."));
		on(TickEvent.class, e -> tick());
	}

	@Override
	protected void onDisable() {
		tiles = List.of();
		route = List.of();
		wait = 0;
	}

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || player.isDeadOrDying()) {
			tiles = List.of();
			route = List.of();
			return;
		}
		if (wait-- > 0) {
			return;
		}
		wait = Math.max(0, (int) Math.round(updateEvery.get()) - 1);
		plan(mc, player);
	}

	private void plan(Minecraft mc, LocalPlayer player) {
		BlockPos start = Targets.feet(player).immutable();
		int range = (int) Math.round(radius.get());
		Map<BlockPos, BlockPos> parent = reachable(player, start, range);
		Player enemy = Targets.nearestEnemy(player, targetRange.get(), friends, false);
		List<BlastSpot> spots = blastSpots(mc, player, enemy, range);
		List<Vec3> liveCrystals = new ArrayList<>();
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (entity instanceof EndCrystal crystal && crystal.isAlive()
					&& crystal.distanceToSqr(player) <= (range + 12.0) * (range + 12.0)) {
				liveCrystals.add(crystal.position());
			}
		}

		List<BlockPos> candidates = new ArrayList<>(parent.keySet());
		candidates.sort(Comparator.comparingDouble(pos -> pathLength(parent, pos) - exits(player, pos) * 1.5));
		if (candidates.size() > 48) {
			candidates = candidates.subList(0, 48);
		}
		List<Tile> result = new ArrayList<>();
		float health = player.getHealth() + player.getAbsorptionAmount();
		for (BlockPos pos : candidates) {
			Vec3 feet = Vec3.atBottomCenterOf(pos);
			float danger = 0;
			float outgoing = 0;
			for (Vec3 crystal : liveCrystals) {
				danger = Math.max(danger, Blast.damageAt(player, feet, crystal, Blast.CRYSTAL));
			}
			for (BlastSpot spot : spots) {
				float self = -1;
				if (spot.enemyReachable()) {
					self = Blast.damageAt(player, feet, spot.center(), Blast.CRYSTAL);
					danger = Math.max(danger, self);
				}
				if (enemy != null && spot.enemyDamage() > 0
						&& feet.add(0, player.getEyeHeight(), 0).distanceToSqr(spot.center()) <= placeReach.get() * placeReach.get()) {
					if (self < 0) {
						self = Blast.damageAt(player, feet, spot.center(), Blast.CRYSTAL);
					}
					if (self < health - 1) {
						outgoing = Math.max(outgoing, spot.enemyDamage());
					}
				}
			}
			int exits = exits(player, pos);
			int mining = exits == 0 ? quickestMineTicks(player, pos) : 0;
			int steps = pathLength(parent, pos);
			double score = outgoing * attackWeight.get() - danger * dangerWeight.get()
					+ exits * 1.5 - steps * 0.2 - Math.min(200, mining) * 0.025;
			result.add(new Tile(pos.immutable(), danger, outgoing, exits, mining, score));
		}
		result.sort(Comparator.comparingDouble(Tile::score).reversed());
		int count = Math.min((int) Math.round(shown.get()), result.size());
		tiles = List.copyOf(result.subList(0, count));
		route = result.isEmpty() ? List.of() : buildRoute(parent, result.get(0).pos());
	}

	private Map<BlockPos, BlockPos> reachable(LocalPlayer player, BlockPos start, int radius) {
		Map<BlockPos, BlockPos> parent = new LinkedHashMap<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		parent.put(start, null);
		queue.add(start);
		while (!queue.isEmpty() && parent.size() < 500) {
			BlockPos at = queue.removeFirst();
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				for (int dy : new int[] {0, 1, -1}) {
					BlockPos next = at.relative(direction).offset(0, dy, 0).immutable();
					int dx = next.getX() - start.getX();
					int dz = next.getZ() - start.getZ();
					if (parent.containsKey(next) || dx * dx + dz * dz > radius * radius || Math.abs(next.getY() - start.getY()) > 2
							|| !standable(player, next)
							|| !Teleport.clear(player, Vec3.atBottomCenterOf(at), Vec3.atBottomCenterOf(next))) {
						continue;
					}
					parent.put(next, at);
					queue.addLast(next);
					break;
				}
			}
		}
		return parent;
	}

	private List<BlastSpot> blastSpots(Minecraft mc, LocalPlayer player, Player enemy, int radius) {
		List<BlastSpot> out = new ArrayList<>();
		BlockPos origin = Targets.feet(player);
		int r = radius + 5;
		for (BlockPos cursor : BlockPos.betweenClosed(origin.offset(-r, -2, -r), origin.offset(r, 3, r))) {
			String id = Place.path(mc.level.getBlockState(cursor));
			if ((!id.equals("obsidian") && !id.equals("bedrock")) || !mc.level.isEmptyBlock(cursor.above())) {
				continue;
			}
			AABB crystalSpace = new AABB(cursor.getX(), cursor.getY() + 1, cursor.getZ(),
					cursor.getX() + 1, cursor.getY() + 3, cursor.getZ() + 1);
			if (!mc.level.getEntities((Entity) null, crystalSpace).isEmpty()) {
				continue;
			}
			Vec3 center = new Vec3(cursor.getX() + 0.5, cursor.getY() + 1, cursor.getZ() + 0.5);
			float enemyDamage = enemy == null ? 0 : Blast.damage(enemy, center, Blast.CRYSTAL);
			boolean enemyReachable = enemy != null && enemy.getEyePosition().distanceToSqr(center) <= placeReach.get() * placeReach.get();
			if (enemyDamage > 0.5f || enemyReachable) {
				out.add(new BlastSpot(center, enemyDamage, enemyReachable));
			}
		}
		out.sort(Comparator.comparingDouble(spot -> (spot.enemyReachable() ? -100 : 0)
				- spot.enemyDamage() * 4 + spot.center().distanceTo(player.position())));
		return out.size() <= 16 ? out : new ArrayList<>(out.subList(0, 16));
	}

	private static boolean standable(LocalPlayer player, BlockPos feetBlock) {
		Minecraft mc = Minecraft.getInstance();
		BlockPos below = feetBlock.below();
		return Teleport.fits(player, Vec3.atBottomCenterOf(feetBlock))
				&& mc.level.getBlockState(below).isFaceSturdy(mc.level, below, Direction.UP)
				&& mc.level.getFluidState(feetBlock).isEmpty();
	}

	private static int exits(LocalPlayer player, BlockPos pos) {
		int exits = 0;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos next = pos.relative(direction);
			if (standable(player, next) && Teleport.clear(player, Vec3.atBottomCenterOf(pos), Vec3.atBottomCenterOf(next))) {
				exits++;
			}
		}
		return exits;
	}

	private static int quickestMineTicks(LocalPlayer player, BlockPos pos) {
		Minecraft mc = Minecraft.getInstance();
		int best = 1_000;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			int ticks = breakTicks(player, mc.level.getBlockState(pos.relative(direction)), pos.relative(direction));
			ticks += breakTicks(player, mc.level.getBlockState(pos.above().relative(direction)), pos.above().relative(direction));
			best = Math.min(best, ticks);
		}
		return best;
	}

	private static int breakTicks(LocalPlayer player, BlockState state, BlockPos pos) {
		if (state.getCollisionShape(player.level(), pos).isEmpty()) {
			return 0;
		}
		if (state.getDestroySpeed(player.level(), pos) < 0) {
			return 1_000;
		}
		float progress = state.getDestroyProgress(player, player.level(), pos);
		return progress <= 0 ? 1_000 : Math.max(1, (int) Math.ceil(1.0 / progress));
	}

	private static int pathLength(Map<BlockPos, BlockPos> parent, BlockPos end) {
		int length = 0;
		for (BlockPos at = end; parent.get(at) != null; at = parent.get(at)) {
			length++;
		}
		return length;
	}

	private static List<Vec3> buildRoute(Map<BlockPos, BlockPos> parent, BlockPos end) {
		ArrayDeque<Vec3> reversed = new ArrayDeque<>();
		for (BlockPos at = end; at != null; at = parent.get(at)) {
			reversed.addFirst(Vec3.atBottomCenterOf(at).add(0, 0.08, 0));
		}
		return List.copyOf(reversed);
	}

	public void collect(List<WorldEsp.Box> out) {
		if (!isEnabled()) {
			return;
		}
		for (int i = 0; i < tiles.size(); i++) {
			Tile tile = tiles.get(i);
			int color;
			if (i == 0) {
				color = bestColor.get();
			} else {
				float risk = Math.min(1, tile.danger() / 16f);
				color = blend(safeColor.get(), dangerColor.get(), risk);
			}
			out.add(new WorldEsp.Box(new AABB(tile.pos()).inflate(-0.04).setMaxY(tile.pos().getY() + 0.08),
					withAlpha(color, i == 0 ? 240 : 170), withAlpha(color, i == 0 ? 80 : 35), i == 0 ? 0.025f : 0.012f));
		}
	}

	public void lines(List<WorldEsp.Line> out, net.minecraft.client.renderer.state.level.CameraRenderState camera, float partialTick) {
		if (!isEnabled() || !showRoute.get() || route.size() < 2) {
			return;
		}
		int color = withAlpha(bestColor.get(), 235);
		for (int i = 1; i < route.size(); i++) {
			Vec3 a = route.get(i - 1);
			Vec3 b = route.get(i);
			out.add(new WorldEsp.Line(a.x, a.y, a.z, b.x, b.y, b.z, color, 2.2f));
		}
	}

	private static int withAlpha(int color, int alpha) {
		return alpha << 24 | color & 0xFFFFFF;
	}

	private static int blend(int a, int b, float amount) {
		amount = Math.max(0, Math.min(1, amount));
		int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * amount);
		int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * amount);
		int blue = Math.round((a & 255) + ((b & 255) - (a & 255)) * amount);
		return 0xFF000000 | r << 16 | g << 8 | blue;
	}
}
