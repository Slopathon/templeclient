package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.phys.AABB;
import nz.temple.client.render.WorldEsp;
import nz.temple.client.util.ChunkScan;
import nz.temple.client.util.Place;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.NumberSetting;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class StorageRadar extends Module {
	private final boolean spawners;
	private final NumberSetting radius;
	private final NumberSetting minimum;
	private final BoolSetting alerts;
	private final ColorSetting color;
	private final ChunkScan<BlockPos> scan = new ChunkScan<>();
	private final Set<Long> announced = new HashSet<>();

	protected StorageRadar(String name, boolean spawners) {
		super(name, Category.RENDER, spawners ? "Highlights loaded spawners and reports their coordinates locally." : "Finds loaded chunks with concentrated storage. Naturally generated storage can also match.");
		this.spawners = spawners;
		radius = add(new NumberSetting("Radius", 8, 1, 32, 1, " chunks")
				.describe("Search radius in chunks. Limited by the server's view distance."));
		minimum = add(new NumberSetting("Minimum count", spawners ? 1 : 5, 1, 30, 1, "")
				.describe(spawners ? "Spawners a chunk needs before it is shown." : "Storage blocks a chunk needs before it counts as a stash."));
		alerts = add(new BoolSetting("Chat alerts", true).describe("Sends each new find's coordinates to chat (only visible to you)."));
		color = add(new ColorSetting("Color", spawners ? 0xFFAF52DE : 0xFFFFD60A).describe("Box colour."));
		minimum.addListener(scan::clear);
		on(TickEvent.class, e -> tick());
	}

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			reset();
			return;
		}
		int need = (int) Math.round(minimum.get());
		scan.tick((int) Math.round(radius.get()), 16, (level, chunk, out) -> {
			for (var entry : chunk.getBlockEntities().entrySet()) {
				String path = Place.path(chunk.getBlockState(entry.getKey()));
				boolean match = spawners ? entry.getValue() instanceof SpawnerBlockEntity
						: path.equals("chest") || path.equals("trapped_chest") || path.equals("barrel") || path.endsWith("shulker_box");
				if (match) {
					out.add(entry.getKey().immutable());
				}
			}
			if (out.size() < need) {
				out.clear();
			}
		});
		for (Map.Entry<Long, List<BlockPos>> e : scan.byChunk().entrySet()) {
			if (!e.getValue().isEmpty() && announced.add(e.getKey()) && alerts.get()) {
				BlockPos p = e.getValue().getFirst();
				nz.temple.client.compat.Players.message(mc.player, Component.literal("§8[§bTemple§8] §f" + name() + " §7" + e.getValue().size() + " at "
						+ nz.temple.client.modules.client.StreamerMode.alert(p.getX(), p.getY(), p.getZ())));
			}
		}
	}

	private List<WorldEsp.Box> shown = List.of();
	private int shownScan = -1;
	private int shownRgb;

	public void collect(List<WorldEsp.Box> boxes) {
		if (!isEnabled()) {
			return;
		}
		int rgb = color.get() & 0xFFFFFF;
		if (scan.version() != shownScan || rgb != shownRgb) {
			shownScan = scan.version();
			shownRgb = rgb;
			List<WorldEsp.Box> next = new java.util.ArrayList<>();
			build:
			for (List<BlockPos> positions : scan.results()) {
				for (BlockPos pos : positions) {
					if (next.size() >= 2048) {
						break build;
					}
					next.add(new WorldEsp.Box(new AABB(pos), 0xFF000000 | rgb, 0x30000000 | rgb, 0.012f));
				}
			}
			shown = next;
		}
		boxes.addAll(shown);
	}

	private void reset() {
		scan.clear();
		announced.clear();
	}

	@Override
	protected void onDisable() {
		reset();
	}
}
