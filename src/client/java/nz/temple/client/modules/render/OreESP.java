package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.render.WorldEsp;
import nz.temple.client.util.ChunkScan;
import nz.temple.client.util.Place;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;

public final class OreESP extends Module {
	private record Ore(BlockPos pos, int color) {
	}
	private record Kind(String word, BoolSetting on, int color) {
	}

	private final NumberSetting range;
	private final NumberSetting maxShown;
	private final BoolSetting exposedOnly;
	private final ModeSetting style;
	private final NumberSetting fillOpacity;
	private final List<Kind> kinds = new ArrayList<>();
	private final it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap<Block> colors = unknownColors();
	private final ChunkScan<Ore> scan = new ChunkScan<>();
	private final List<Ore> shown = new ArrayList<>();
	private List<WorldEsp.Box> boxes = List.of();
	private int seenScan = -1;
	private long seenLook = -1;
	private BlockPos seenAt;
	public OreESP() {
		super("Ore ESP", Category.RENDER, "Shows boxes around ores through walls, coloured by ore type. No resource pack needed.");
		range = add(new NumberSetting("Range", 64, 8, 256, 8, " m")
				.describe("Scan distance. Nearby chunks are scanned first."));
		maxShown = add(new NumberSetting("Max shown", 1024, 64, 4096, 64, "").describe("Maximum ores drawn at once, nearest first."));
		exposedOnly = add(new BoolSetting("Exposed only", false)
				.describe("Only shows ores touching air or water. Servers with anti-xray (such as DonutSMP) send fake ores inside stone, "
						+ "but exposed ores in caves are real."));
		style = add(new ModeSetting("Style", List.of("Box", "Outline", "Fill"), "Box")
				.describe("Box draws an outline with a light fill. Outline draws edges only. Fill draws a tinted block only."));
		fillOpacity = add(new NumberSetting("Fill opacity", 35, 5, 90, 5, "%").visibleWhen(() -> !style.is("Outline")));
		group();
		kind("diamond", "Diamonds", true, 0x5AC8FA);
		kind("gold", "Gold", true, 0xFFD60A);
		kind("iron", "Iron", true, 0xD8A077);
		kind("copper", "Copper", false, 0xE0773B);
		kind("coal", "Coal", false, 0x3A3A3C);
		kind("redstone", "Redstone", true, 0xFF453A);
		kind("lapis", "Lapis", true, 0x0A84FF);
		kind("emerald", "Emeralds", true, 0x34C759);
		kind("quartz", "Quartz", true, 0xF2F2F7);
		kind("debris", "Debris", true, 0xAF52DE);
		exposedOnly.addListener(scan::clear);
		on(TickEvent.class, e -> tick());
	}
	private void kind(String word, String name, boolean on, int color) {
		BoolSetting toggle = add(new BoolSetting(name, on).describe("Show " + name.toLowerCase() + " ore."));
		toggle.addListener(() -> {
			colors.clear();
			scan.clear();
		});
		kinds.add(new Kind(word, toggle, color));
	}
	private static it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap<Block> unknownColors() {
		var map = new it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap<Block>();
		map.defaultReturnValue(-1);
		return map;
	}
	private int colorOf(BlockState state) {
		Block block = state.getBlock();
		int c = colors.getInt(block);
		if (c == -1) {
			c = lookUp(block);
			colors.put(block, c);
		}
		return c;
	}
	private int lookUp(Block block) {
		String p = Place.path(block.defaultBlockState());
		if (!p.endsWith("_ore") && !p.equals("ancient_debris")) {
			return 0;
		}
		for (Kind k : kinds) {
			if (p.contains(k.word())) {
				return k.on().get() ? k.color() : 0;
			}
		}
		return 0;
	}
	private void scanChunk(ClientLevel level, LevelChunk chunk, List<Ore> out) {
		LevelChunkSection[] sections = chunk.getSections();
		int baseX = chunk.getPos().getMinBlockX();
		int baseZ = chunk.getPos().getMinBlockZ();
		boolean exposed = exposedOnly.get();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int s = 0; s < sections.length; s++) {
			LevelChunkSection section = sections[s];
			if (section == null || section.hasOnlyAir() || !section.maybeHas(st -> colorOf(st) != 0)) {
				continue;
			}
			int baseY = chunk.getMinY() + s * 16;
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						int c = colorOf(section.getBlockState(x, y, z));
						if (c == 0) {
							continue;
						}
						pos.set(baseX + x, baseY + y, baseZ + z);
						if (exposed && !touchesOpen(level, pos)) {
							continue;
						}
						out.add(new Ore(pos.immutable(), c));
					}
				}
			}
		}
	}
	private static boolean touchesOpen(ClientLevel level, BlockPos pos) {
		for (Direction d : Direction.values()) {
			BlockState n = level.getBlockState(pos.relative(d));
			if (n.isAir() || !n.getFluidState().isEmpty()) {
				return true;
			}
		}
		return false;
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			scan.clear();
			shown.clear();
			boxes = List.of();
			seenAt = null;
			return;
		}
		int r = (int) Math.ceil(range.get());
		scan.tick((r + 15) / 16, 3, this::scanChunk);
		int cap = (int) Math.round(maxShown.get());
		int fill = style.is("Outline") ? 0 : Math.round(fillOpacity.getFloat() / 100f * 255);
		boolean outline = !style.is("Fill");
		long look = ((long) r << 40) | ((long) cap << 16) | (fill << 1) | (outline ? 1 : 0);
		BlockPos at = player.blockPosition();
		if (scan.version() == seenScan && look == seenLook && seenAt != null && seenAt.distManhattan(at) < 2) {
			return;
		}
		seenScan = scan.version();
		seenLook = look;
		seenAt = at;
		Vec3 eye = player.getEyePosition();
		scan.nearest(o -> o.pos().distToCenterSqr(eye), (double) r * r, cap, shown);
		List<WorldEsp.Box> next = new ArrayList<>(shown.size());
		for (Ore o : shown) {
			next.add(new WorldEsp.Box(new AABB(o.pos()), outline ? 0xFF000000 | o.color() : 0, (fill << 24) | o.color(), outline ? 0.008f : 0));
		}
		boxes = next;
	}
	@Override
	protected void onDisable() {
		scan.clear();
		shown.clear();
		boxes = List.of();
		seenAt = null;
	}
	public void collect(List<WorldEsp.Box> out) {
		if (isEnabled()) {
			out.addAll(boxes);
		}
	}
}
