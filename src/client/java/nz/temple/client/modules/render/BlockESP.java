package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.render.WorldEsp;
import nz.temple.client.util.BlockList;
import nz.temple.client.util.ChunkScan;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BlockESP extends Module {
	private record Found(BlockPos pos, int color) {
	}
	private final nz.temple.setting.EntitySelection blocks;
	private final NumberSetting range;
	private final NumberSetting maxShown;
	private final ModeSetting colorMode;
	private final ColorSetting color;
	private final ModeSetting style;
	private final NumberSetting fillOpacity;
	private final BlockList list = new BlockList();
	private final Map<Block, Integer> natural = new IdentityHashMap<>();
	private final ChunkScan<Found> scan = new ChunkScan<>();
	private final List<Found> shown = new ArrayList<>();
	private List<WorldEsp.Box> boxes = List.of();
	private int seenScan = -1;
	private long seenLook = -1;
	private BlockPos seenAt;
	private Set<Block> wanted = Set.of();
	public BlockESP() {
		super("Block ESP", Category.RENDER, "Shows boxes around the blocks you choose, through walls.");
		blocks = add(nz.temple.client.util.BlockCatalog.selection("Blocks", "spawner, ancient_debris, diamond_ore, deepslate_diamond_ore")
				.describe("The blocks to box. Pick them from the list, with their icons."));
		range = add(new NumberSetting("Range", 64, 8, 256, 8, " m").describe("Scan distance. Nearby chunks are scanned first."));
		maxShown = add(new NumberSetting("Max shown", 1024, 64, 4096, 64, "").describe("Most blocks drawn at once, nearest first."));
		group();
		colorMode = add(new ModeSetting("Color", List.of("Block", "Custom"), "Block")
				.describe("Block colours each box like the block itself (its map colour). Custom uses one colour for all."));
		color = add(new ColorSetting("Custom color", 0xFF5AC8FA).themed().describe("Box colour.").visibleWhen(() -> colorMode.is("Custom")));
		style = add(new ModeSetting("Style", List.of("Box", "Outline", "Fill"), "Box")
				.describe("Box draws an outline with a light fill. Outline draws edges only. Fill draws a tinted block only."));
		fillOpacity = add(new NumberSetting("Fill opacity", 30, 5, 90, 5, "%").visibleWhen(() -> !style.is("Outline")));
		blocks.addListener(scan::clear);
		colorMode.addListener(scan::clear);
		color.addListener(scan::clear);
		on(TickEvent.class, e -> tick());
	}
	private int colorOf(BlockState state) {
		if (colorMode.is("Custom")) {
			return color.get() & 0xFFFFFF;
		}
		return natural.computeIfAbsent(state.getBlock(), block -> {
			int c = block.defaultBlockState().getMapColor(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
			return c == 0 ? 0xFFFFFF : c;
		});
	}
	private void scanChunk(ClientLevel level, LevelChunk chunk, List<Found> out) {
		Set<Block> want = wanted;
		if (want.isEmpty()) {
			return;
		}
		LevelChunkSection[] sections = chunk.getSections();
		int baseX = chunk.getPos().getMinBlockX();
		int baseZ = chunk.getPos().getMinBlockZ();
		for (int s = 0; s < sections.length; s++) {
			LevelChunkSection section = sections[s];
			if (section == null || section.hasOnlyAir() || !section.maybeHas(st -> want.contains(st.getBlock()))) {
				continue;
			}
			int baseY = chunk.getMinY() + s * 16;
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						BlockState state = section.getBlockState(x, y, z);
						if (want.contains(state.getBlock())) {
							out.add(new Found(new BlockPos(baseX + x, baseY + y, baseZ + z), colorOf(state)));
						}
					}
				}
			}
		}
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
		wanted = list.of(blocks.get());
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
		scan.nearest(f -> f.pos().distToCenterSqr(eye), (double) r * r, cap, shown);
		List<WorldEsp.Box> next = new ArrayList<>(shown.size());
		for (Found f : shown) {
			next.add(new WorldEsp.Box(new AABB(f.pos()), outline ? 0xFF000000 | f.color() : 0, (fill << 24) | f.color(), outline ? 0.008f : 0));
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
