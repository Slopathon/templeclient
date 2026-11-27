package nz.temple.client.modules.world;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import nz.temple.TempleNz;
import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.player.AutoReconnect;
import nz.temple.client.util.AimSmoother;
import nz.temple.client.util.ChunkScan;
import nz.temple.client.util.LegitAim;
import nz.temple.client.util.Mining;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.EntitySelection;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AutoTunnel extends Module {
	private static final int INSTANT_PER_TICK = 4;
	private static final int CLUSTER = 12;
	private static final boolean TRACE = Boolean.getBoolean("temple.showcase");
	private final BoolSetting legit;
	private final ModeSetting size;
	private final NumberSetting height;
	private final BoolSetting baseFinder;
	private final NumberSetting lookAhead;
	private final BoolSetting avoidLava;
	private final BoolSetting avoidWater;
	private final BoolSetting avoidDrops;
	private final BoolSetting avoidFalling;
	private final NumberSetting detour;
	private final EntitySelection storageBlocks;
	private final NumberSetting storageMin;
	private final BoolSetting skipNatural;
	private final NumberSetting ignoreNearStart;
	private final ModeSetting onFind;
	private final BoolSetting logFinds;
	private final NumberSetting saveTool;
	private final NumberSetting stopHealth;
	private static final List<String> DEFAULT_STORAGE = defaultStorage();
	private static List<String> defaultStorage() {
		List<String> ids = new ArrayList<>(List.of("minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel",
				"minecraft:ender_chest", "minecraft:hopper", "minecraft:spawner", "minecraft:shulker_box"));
		for (String colour : List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray",
				"cyan", "purple", "blue", "brown", "green", "red", "black")) {
			ids.add("minecraft:" + colour + "_shulker_box");
		}
		return ids;
	}
	private record Hit(BlockPos pos, Block block) {
	}

	private volatile Set<Block> wanted;

	private record Find(BlockPos pos, String why) {
	}

	private final ChunkScan<Hit> scan = new ChunkScan<>();
	private final Set<Long> reported = new HashSet<>();
	private int clusterTimer;
	private final ArrayDeque<Find> finds = new ArrayDeque<>();
	private Direction mainHeading;

	private Direction heading;
	private BlockPos legStart;
	private BlockPos start;
	private boolean lastLeft;
	private int retreatTicks;
	private int retreats;
	private boolean driving;
	private boolean legitBusy;
	public AutoTunnel() {
		super("Auto Tunnel", Category.WORLD, "Digs a tunnel forward as you walk. Turn on Base finder to let it steer, avoid hazards and "
				+ "search for bases.");
		legit = add(new BoolSetting("Legit", false)
				.describe("Digs one block at a time, looking at each with the right tool and only breaking what your crosshair is on. "
						+ "Always on while Base finder is steering."));
		size = add(new ModeSetting("Width", List.of("1x2", "3x3"), "1x2")
				.describe("Tunnel size. 1x2 is fastest; 3x3 fits minecarts."));
		height = add(new NumberSetting("Reach", 4, 2, 6, 1, " b")
				.describe("How far ahead to mine."));
		group("Base finder");
		baseFinder = add(new BoolSetting("Base finder", false)
				.describe("Steers the tunnel for you: walks forward on a fixed heading, avoids hazards ahead, and stops when a base is "
						+ "found in nearby chunks. Start it at the depth you want to dig."));
		lookAhead = add(new NumberSetting("Hazard scan", 6, 3, 12, 1, " b")
				.describe("How far ahead to check for lava, water and drops.")
				.visibleWhen(baseFinder::get));
		avoidLava = add(new BoolSetting("Avoid lava", true)
				.describe("Turns away from lava next to, above or below the tunnel.")
				.visibleWhen(baseFinder::get));
		avoidWater = add(new BoolSetting("Avoid water", true)
				.describe("Turns away from water, which floods the tunnel and slows digging.")
				.visibleWhen(baseFinder::get));
		avoidDrops = add(new BoolSetting("Avoid drops", true)
				.describe("Turns away from drops two or more blocks deep (caves, ravines). One-block steps are walked down.")
				.visibleWhen(baseFinder::get));
		avoidFalling = add(new BoolSetting("Avoid gravel", false)
				.describe("Turns away from gravel or sand above the tunnel. Off digs out whatever falls.")
				.visibleWhen(baseFinder::get));
		detour = add(new NumberSetting("Detour", 4, 2, 12, 1, " b")
				.describe("How far to go sideways around a hazard before returning to the main heading.")
				.visibleWhen(baseFinder::get));
		group();
		storageBlocks = add(new EntitySelection("Storage blocks", DEFAULT_STORAGE).items()
				.describe("Blocks that count toward a base.")
				.visibleWhen(baseFinder::get));
		storageMin = add(new NumberSetting("Blocks to flag", 5, 1, 64, 1, "")
				.describe("How many of the selected blocks within 12 blocks of each other count as a base.")
				.visibleWhen(baseFinder::get));
		skipNatural = add(new BoolSetting("Skip dungeons", true)
				.describe("Ignores spawners on cobblestone floors (dungeons) or in cobwebs (mineshafts).")
				.visibleWhen(() -> baseFinder.get() && storageBlocks.has("minecraft:spawner")));
		ignoreNearStart = add(new NumberSetting("Ignore near start", 48, 0, 256, 8, " m")
				.describe("Ignores finds this close to where the tunnel started, so your own base does not stop it.")
				.visibleWhen(baseFinder::get));
		onFind = add(new ModeSetting("On find", List.of("Notify & stop", "Leave"), "Notify & stop")
				.describe("Notify & stop tells you the location and stops digging. Leave disconnects from the server immediately.")
				.visibleWhen(baseFinder::get));
		logFinds = add(new BoolSetting("Log finds", true)
				.describe("Logs every find to config/templenz/bases.txt with the time, server and coordinates.")
				.visibleWhen(baseFinder::get));
		group();
		saveTool = add(new NumberSetting("Save tool at", 30, 0, 200, 5, " uses")
				.describe("Stops before your pickaxe breaks. 0 lets it break.")
				.visibleWhen(baseFinder::get));
		stopHealth = add(new NumberSetting("Stop at health", 8, 0, 20, 1, " hp")
				.describe("Stops if your health drops to this. 0 never stops.")
				.visibleWhen(baseFinder::get));
		storageBlocks.addListener(() -> {
			wanted = null;
			scan.clear();
		});
		on(TickEvent.class, e -> tick());
	}
	@Override
	protected void onEnable() {
		mainHeading = null;
		heading = null;
		legStart = null;
		start = null;
		retreatTicks = 0;
		retreats = 0;
		finds.clear();
		reported.clear();
		scan.clear();
		Mining.stop();
	}
	@Override
	protected void onDisable() {
		releaseKeys();
		Mining.stop();
		scan.clear();
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.gameMode == null) {
			return;
		}
		if (!baseFinder.get()) {
			if (driving) {
				releaseKeys();
			}
			dig(mc, player, facingOf(player));
			return;
		}
		Direction dir = drive(mc, player);
		if (dir == null) {
			return;
		}
		boolean working = dig(mc, player, dir);
		walk(mc, player, dir, working);
		if (TRACE && player.tickCount % 10 == 0) {
			TempleNzClient.LOGGER.info("TEMPLE_TUNNEL_TRACE heading={} main={} route={} working={} fwd={}", dir, mainHeading,
					route(mc.level, player.blockPosition(), dir), working, mc.options.keyUp.isDown());
		}
	}

	private boolean dig(Minecraft mc, LocalPlayer player, Direction facing) {
		int reach = (int) Math.round(height.get());
		BlockPos base = player.blockPosition().relative(facing);
		legitBusy = false;
		int instant = 0;
		boolean working = false;
		for (int f = 0; f < reach; f++) {
			for (BlockPos pos : crossSection(base.relative(facing, f), facing)) {
				BlockState state = mc.level.getBlockState(pos);
				if (!mineable(mc.level, pos, state) || baseFinder.get() && state.hasBlockEntity()) {
					continue;
				}
				if (legitMining()) {
					LegitAim.Step step = LegitAim.mine(player, pos, baseFinder.get() ? 22 : 35);
					if (step == LegitAim.Step.WORKING) {
						legitBusy = true;
						return true;
					}
					continue;
				}
				if (player.isCreative()) {
					mc.gameMode.destroyBlock(pos);
					if (++instant >= INSTANT_PER_TICK) {
						nz.temple.client.compat.Players.swing(player, InteractionHand.MAIN_HAND);
						return true;
					}
					continue;
				}
				boolean oneHit = state.getDestroyProgress(player, mc.level, pos) >= 1f;
				Mining.dig(mc, pos, facing.getOpposite());
				working = true;
				if (!oneHit || ++instant >= INSTANT_PER_TICK) {
					nz.temple.client.compat.Players.swing(player, InteractionHand.MAIN_HAND);
					return true;
				}
			}
		}
		if (working) {
			nz.temple.client.compat.Players.swing(player, InteractionHand.MAIN_HAND);
		}
		return working || legitBusy;
	}
	private List<BlockPos> crossSection(BlockPos center, Direction facing) {
		List<BlockPos> out = new ArrayList<>(9);
		if (size.is("3x3")) {
			for (int up = 0; up <= 2; up++) {
				out.add(center.above(up));
				out.add(side(center, facing, -1).above(up));
				out.add(side(center, facing, 1).above(up));
			}
		} else {
			out.add(center);
			out.add(center.above());
		}
		return out;
	}
	private static BlockPos side(BlockPos pos, Direction facing, int amount) {
		return pos.relative(facing.getClockWise(), amount);
	}
	private boolean legitMining() {
		return legit.get() || baseFinder.get();
	}
	private static boolean mineable(Level level, BlockPos pos, BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty() && state.getDestroySpeed(level, pos) >= 0;
	}
	private static Direction facingOf(LocalPlayer player) {
		return Direction.fromYRot(player.getYRot());
	}

	private Direction drive(Minecraft mc, LocalPlayer player) {
		driving = true;
		BlockPos feet = player.blockPosition();
		if (mainHeading == null) {
			mainHeading = heading = facingOf(player);
			start = feet;
		}
		if (handleFinds(mc, player) || !isEnabled()) {
			return null;
		}
		if (stopHealth.get() > 0 && player.getHealth() + player.getAbsorptionAmount() <= stopHealth.get()) {
			stop("low health");
			return null;
		}
		ItemStack tool = player.getMainHandItem();
		if (saveTool.get() > 0 && tool.isDamageableItem() && tool.getMaxDamage() - tool.getDamageValue() <= saveTool.get()) {
			stop(tool.getHoverName().getString() + " is nearly broken");
			return null;
		}
		if (player.isUsingItem()) {
			setKeys(mc, false, false);
			return null;
		}
		if (retreatTicks > 0) {
			retreatTicks--;
			setKeys(mc, false, true);
			return null;
		}
		if (!fluidClose(mc.level, feet, heading)) {
			retreats = 0;
		} else {
			if (++retreats > 4) {
				stop("fluid keeps following into the tunnel");
				return null;
			}
			retreatTicks = 12;
			Mining.stop();
			say(mc, "fluid in the tunnel, backing off");
			heading = mainHeading;
			return null;
		}
		if (heading != mainHeading) {
			int along = Math.abs(dot(feet.subtract(legStart), heading));
			if (along >= detour.get() && route(mc.level, feet, mainHeading) == Route.CLEAR) {
				heading = mainHeading;
			} else if (along > detour.get() * 4) {
				stop("could not get back on course around the hazard");
				return null;
			}
		}
		Route ahead = route(mc.level, feet, heading);
		if (ahead == Route.UNLOADED) {
			setKeys(mc, false, false);
			return null;
		}
		if (ahead == Route.BLOCKED) {
			Direction next = chooseTurn(mc.level, feet);
			if (next == null) {
				stop("boxed in by hazards");
				return null;
			}
			heading = next;
			legStart = feet;
			Mining.stop();
		}
		return heading;
	}
	private enum Route { CLEAR, BLOCKED, UNLOADED }
	private Route route(Level level, BlockPos from, Direction dir) {
		int n = (int) Math.round(lookAhead.get());
		for (int f = 1; f <= n; f++) {
			BlockPos center = from.relative(dir, f);
			if (!level.isLoaded(center)) {
				return Route.UNLOADED;
			}
			List<BlockPos> slice = crossSection(center, dir);
			for (BlockPos pos : slice) {
				BlockState state = level.getBlockState(pos);
				if (dangerous(state.getFluidState()) || !state.isAir() && state.getDestroySpeed(level, pos) < 0 || state.hasBlockEntity()) {
					return Route.BLOCKED;
				}
				for (Direction d : Direction.values()) {
					BlockPos n2 = pos.relative(d);
					if (!slice.contains(n2) && dangerous(level.getFluidState(n2))) {
						return Route.BLOCKED;
					}
				}
			}
			if (avoidFalling.get()) {
				BlockPos roof = center.above(size.is("3x3") ? 3 : 2);
				if (level.getBlockState(roof).getBlock() instanceof FallingBlock) {
					return Route.BLOCKED;
				}
			}
			if (avoidDrops.get() && open(level, center.below()) && open(level, center.below(2))) {
				return Route.BLOCKED;
			}
		}
		return Route.CLEAR;
	}
	private boolean dangerous(FluidState fluid) {
		return avoidLava.get() && fluid.is(FluidTags.LAVA) || avoidWater.get() && fluid.is(FluidTags.WATER);
	}
	private static boolean open(Level level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}
	private boolean fluidClose(Level level, BlockPos feet, Direction dir) {
		for (int f = -1; f <= 2; f++) {
			for (BlockPos pos : crossSection(feet.relative(dir, f), dir)) {
				if (dangerous(level.getFluidState(pos))) {
					return true;
				}
			}
		}
		return false;
	}
	private Direction chooseTurn(Level level, BlockPos feet) {
		List<Direction> options = new ArrayList<>(3);
		if (heading == mainHeading) {
			Direction left = mainHeading.getCounterClockWise();
			Direction right = mainHeading.getClockWise();
			int offset = dot(feet.subtract(start), right);
			boolean preferLeft = offset != 0 ? offset > 0 : !lastLeft;
			options.add(preferLeft ? left : right);
			options.add(preferLeft ? right : left);
		} else {
			options.add(mainHeading);
			options.add(heading.getOpposite());
		}
		for (Direction d : options) {
			if (route(level, feet, d) == Route.CLEAR) {
				if (heading == mainHeading) {
					lastLeft = d == mainHeading.getCounterClockWise();
				}
				return d;
			}
		}
		return null;
	}
	private static int dot(BlockPos v, Direction d) {
		return v.getX() * d.getStepX() + v.getZ() * d.getStepZ();
	}
	private void walk(Minecraft mc, LocalPlayer player, Direction dir, boolean working) {
		BlockPos next = player.blockPosition().relative(dir);
		boolean clear = open(mc.level, next) && open(mc.level, next.above());
		setKeys(mc, clear, false);
		if (working) {
			return;
		}
		BlockPos feet = player.blockPosition();
		Vec3 aim = new Vec3(feet.getX() + 0.5 + dir.getStepX() * 4, player.getEyeY() - 0.4, feet.getZ() + 0.5 + dir.getStepZ() * 4);
		AimSmoother.look(player, aim, 14f, 0.4f);
	}
	private void setKeys(Minecraft mc, boolean forward, boolean back) {
		mc.options.keyUp.setDown(forward);
		mc.options.keyDown.setDown(back);
	}
	private void releaseKeys() {
		if (driving) {
			Minecraft mc = Minecraft.getInstance();
			setKeys(mc, false, false);
			driving = false;
		}
	}
	private boolean handleFinds(Minecraft mc, LocalPlayer player) {
		Set<Block> blocks = wanted();
		boolean skipDungeons = skipNatural.get();
		int radius = Math.min(16, mc.options.getEffectiveRenderDistance());
		scan.tick(radius, 2, (level, chunk, out) -> {
			if (blocks.isEmpty()) {
				return;
			}
			LevelChunkSection[] sections = chunk.getSections();
			int baseX = chunk.getPos().getMinBlockX();
			int baseZ = chunk.getPos().getMinBlockZ();
			for (int i = 0; i < sections.length; i++) {
				LevelChunkSection section = sections[i];
				if (section == null || section.hasOnlyAir() || !section.maybeHas(st -> blocks.contains(st.getBlock()))) {
					continue;
				}
				int baseY = level.getMinY() + i * 16;
				for (int y = 0; y < 16; y++) {
					for (int z = 0; z < 16; z++) {
						for (int x = 0; x < 16; x++) {
							Block block = section.getBlockState(x, y, z).getBlock();
							if (!blocks.contains(block)) {
								continue;
							}
							BlockPos pos = new BlockPos(baseX + x, baseY + y, baseZ + z);
							if (block == Blocks.SPAWNER && skipDungeons && natural(level, pos)) {
								continue;
							}
							out.add(new Hit(pos, block));
						}
					}
				}
			}
		});
		if (++clusterTimer >= 10) {
			clusterTimer = 0;
			cluster((int) Math.round(storageMin.get()));
		}
		Find find;
		while ((find = finds.poll()) != null) {
			if (start != null && ignoreNearStart.get() > 0
					&& Math.sqrt(start.distSqr(new BlockPos(find.pos().getX(), start.getY(), find.pos().getZ()))) < ignoreNearStart.get()) {
				continue;
			}
			if (found(mc, player, find)) {
				return true;
			}
		}
		return false;
	}
	private Set<Block> wanted() {
		Set<Block> set = wanted;
		if (set == null) {
			set = new HashSet<>();
			for (String id : storageBlocks.get()) {
				Identifier key = Identifier.tryParse(id);
				if (key != null) {
					BuiltInRegistries.BLOCK.getOptional(key).filter(b -> b != Blocks.AIR).ifPresent(set::add);
				}
			}
			wanted = set;
		}
		return set;
	}
	private void cluster(int need) {
		List<Hit> all = new ArrayList<>();
		for (List<Hit> hits : scan.results()) {
			all.addAll(hits);
		}
		if (all.size() > 3000) {
			all = all.subList(0, 3000);
		}
		for (Hit seed : all) {
			if (reported.contains(seed.pos().asLong())) {
				continue;
			}
			List<Hit> group = new ArrayList<>();
			for (Hit h : all) {
				if (Math.abs(h.pos().getX() - seed.pos().getX()) <= CLUSTER && Math.abs(h.pos().getZ() - seed.pos().getZ()) <= CLUSTER
						&& Math.abs(h.pos().getY() - seed.pos().getY()) <= CLUSTER) {
					group.add(h);
				}
			}
			if (group.size() >= need) {
				group.forEach(h -> reported.add(h.pos().asLong()));
				Map<String, Integer> counts = new LinkedHashMap<>();
				group.forEach(h -> counts.merge(h.block().getName().getString(), 1, Integer::sum));
				List<String> parts = new ArrayList<>();
				counts.forEach((name, n) -> parts.add(n + " " + name));
				finds.add(new Find(seed.pos(), String.join(", ", parts)));
			}
		}
	}
	private static boolean natural(Level level, BlockPos spawner) {
		BlockState floor = level.getBlockState(spawner.below());
		if (floor.is(Blocks.COBBLESTONE) || floor.is(Blocks.MOSSY_COBBLESTONE)) {
			return true;
		}
		for (BlockPos pos : BlockPos.betweenClosed(spawner.offset(-2, -1, -2), spawner.offset(2, 1, 2))) {
			if (level.getBlockState(pos).is(Blocks.COBWEB)) {
				return true;
			}
		}
		return false;
	}
	private boolean found(Minecraft mc, LocalPlayer player, Find find) {
		String where = find.pos().getX() + ", " + find.pos().getY() + ", " + find.pos().getZ();
		int dist = (int) Math.round(Math.sqrt(player.blockPosition().distSqr(find.pos())));
		String shown = nz.temple.client.modules.client.StreamerMode.alert(find.pos().getX(), find.pos().getY(), find.pos().getZ());
		nz.temple.client.compat.Players.message(player, Component.literal("§8[§bTemple§8] §fBase found §7at §f" + shown + " §7(" + dist + " m): " + find.why()));
		if (TempleNzClient.notifications() != null) {
			TempleNzClient.notifications().info("Base found", shown);
		}
		mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.4f));
		if (logFinds.get()) {
			log(mc, where + "  " + find.why());
		}
		TempleNzClient.LOGGER.info("Auto Tunnel base finder: {} ({})", where, find.why());
		setEnabled(false);
		if (onFind.is("Leave")) {
			AutoReconnect.leftOnPurpose();
			mc.disconnectFromWorld(Component.literal("Temple.nz Auto Tunnel: base found at " + shown + " (" + find.why() + ")"));
		}
		return true;
	}
	private static void log(Minecraft mc, String line) {
		ServerData server = mc.getCurrentServer();
		String dim = mc.level == null ? "?" : mc.level.dimension().identifier().toString();
		String entry = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + "  "
				+ (server == null ? "singleplayer" : server.ip) + "  " + dim + "  " + line + System.lineSeparator();
		Path file = FabricLoader.getInstance().getConfigDir().resolve(TempleNz.MOD_ID).resolve("bases.txt");
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, entry, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			TempleNzClient.LOGGER.warn("Could not write {}", file, e);
		}
	}
	private void stop(String why) {
		Minecraft mc = Minecraft.getInstance();
		say(mc, "stopped: " + why);
		if (TempleNzClient.notifications() != null) {
			TempleNzClient.notifications().error("Auto Tunnel stopped", why);
		}
		setEnabled(false);
	}

	private void say(Minecraft mc, String text) {
		if (mc.player != null) {
			nz.temple.client.compat.Players.message(mc.player, Component.literal("§8[§bTemple§8] §fAuto Tunnel §7" + text));
		}
	}
}
