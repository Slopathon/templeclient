package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.render.WorldEsp;
import nz.temple.client.util.ChunkScan;
import nz.temple.client.util.Place;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class StorageESP extends Module {
	private record Hit(AABB bounds, int kind, int dye) {
	}
	private record Kind(BoolSetting on, ColorSetting color, String[] ids) {
		boolean matches(String path) {
			for (String id : ids) {
				if (path.equals(id) || id.startsWith("*") && path.endsWith(id.substring(1))) {
					return true;
				}
			}
			return false;
		}
	}
	private static final Map<String, Integer> DYES = Map.ofEntries(
			Map.entry("white", 0xF9FFFE), Map.entry("orange", 0xF9801D), Map.entry("magenta", 0xC74EBD),
			Map.entry("light_blue", 0x3AB3DA), Map.entry("yellow", 0xFED83D), Map.entry("lime", 0x80C71F),
			Map.entry("pink", 0xF38BAA), Map.entry("gray", 0x474F52), Map.entry("light_gray", 0x9D9D97),
			Map.entry("cyan", 0x169C9C), Map.entry("purple", 0x8932B8), Map.entry("blue", 0x3C44AA),
			Map.entry("brown", 0x835432), Map.entry("green", 0x5E7C16), Map.entry("red", 0xB02E26),
			Map.entry("black", 0x1D1D21));
	private final NumberSetting range;
	private final NumberSetting maxShown;
	private final ModeSetting style;
	private final NumberSetting fillOpacity;
	private final NumberSetting lineWidth;
	private final BoolSetting tracers;
	private final BoolSetting shulkerDye;
	private final BoolSetting cartsOn;
	private final ColorSetting cartColor;
	private final List<Kind> kinds = new ArrayList<>();
	private final ChunkScan<Hit> scan = new ChunkScan<>();
	private final List<Hit> shown = new ArrayList<>();
	private final List<Hit> carts = new ArrayList<>();
	private List<WorldEsp.Box> boxes = List.of();
	private int seenScan = -1;
	private long seenLook = -1;
	private BlockPos seenAt;
	private final it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap<net.minecraft.world.level.block.Block> kindOf = unknown();
	private final it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap<net.minecraft.world.level.block.Block> dyeOf = unknown();
	private final it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap<net.minecraft.world.entity.EntityType<?>> cartTypes =
			new it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap<>();
	private static final int UNKNOWN = Integer.MIN_VALUE;
	private static it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap<net.minecraft.world.level.block.Block> unknown() {
		var map = new it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap<net.minecraft.world.level.block.Block>();
		map.defaultReturnValue(UNKNOWN);
		return map;
	}
	public StorageESP() {
		super("Storage ESP", Category.RENDER, "Highlights chests, shulker boxes, barrels, spawners and other storage through walls, up to "
				+ "your render distance.");
		range = add(new NumberSetting("Range", 256, 16, 1024, 16, " m")
				.describe("How far out storage is shown. Limited by the server's view distance."));
		maxShown = add(new NumberSetting("Max shown", 1024, 64, 4096, 64, "")
				.describe("Maximum boxes drawn at once, nearest first. Lower it if large bases cost frames."));
		group("Look");
		style = add(new ModeSetting("Style", List.of("Box", "Outline", "Fill"), "Box")
				.describe("Box draws an outline with a light fill. Outline draws edges only. Fill draws a tinted block only."));
		fillOpacity = add(new NumberSetting("Fill opacity", 18, 2, 80, 2, "%")
				.describe("How solid the fill is.")
				.visibleWhen(() -> !style.is("Outline")));
		lineWidth = add(new NumberSetting("Line width", 1.5, 1, 3, 0.5, " px")
				.describe("Outline thickness.")
				.visibleWhen(() -> !style.is("Fill")));
		tracers = add(new BoolSetting("Tracers", false).describe("Also draws a line from your crosshair to each container."));
		group("Storage types");
		kind("Chests", true, 0xFFFFB347, "chest");
		kind("Trapped chests", true, 0xFFFF453A, "trapped_chest");
		kind("Ender chests", true, 0xFFAF52DE, "ender_chest");
		kind("Copper chests", true, 0xFFE0875A, "*copper_chest");
		kind("Barrels", true, 0xFFC08A5A, "barrel");
		Kind shulkers = kind("Shulkers", true, 0xFFFF6FD8, "shulker_box", "*_shulker_box");
		shulkerDye = add(new BoolSetting("Shulker dye colour", true)
				.describe("Draws each shulker box in its dye colour instead of the Shulkers colour.")
				.visibleWhen(shulkers.on()::get));
		kind("Furnaces", false, 0xFF98989D, "furnace", "blast_furnace", "smoker");
		kind("Hoppers", false, 0xFF5E5CE6, "hopper");
		kind("Dispensers", false, 0xFF8E8E93, "dispenser", "dropper");
		kind("Crafters", false, 0xFFB0B3B8, "crafter");
		kind("Shelves", false, 0xFFA87B4F, "*_shelf", "chiseled_bookshelf");
		kind("Brewing stands", false, 0xFFFFD60A, "brewing_stand");
		kind("Spawners", true, 0xFF34C759, "spawner", "trial_spawner", "vault");
		kind("Decorated pots", false, 0xFFD8A077, "decorated_pot");
		cartsOn = add(new BoolSetting("Minecarts & boats", true)
				.describe("Includes chest and hopper minecarts and chest boats."));
		cartColor = add(new ColorSetting("Minecart colour", 0xFFFFB347).describe("Colour for storage minecarts and chest boats.")
				.visibleWhen(cartsOn::get));
		for (Kind k : kinds) {
			k.on().addListener(() -> {
				kindOf.clear();
				scan.clear();
			});
		}
		on(TickEvent.class, e -> tick());
	}
	private Kind kind(String name, boolean on, int color, String... ids) {
		BoolSetting toggle = add(new BoolSetting(name, on).describe("Show " + name.toLowerCase() + "."));
		ColorSetting c = add(new ColorSetting(name + " colour", color).describe("Colour for " + name.toLowerCase() + ".")
				.visibleWhen(toggle::get));
		Kind k = new Kind(toggle, c, ids);
		kinds.add(k);
		return k;
	}
	private int kindOf(net.minecraft.world.level.block.Block block) {
		int kind = kindOf.getInt(block);
		if (kind == UNKNOWN) {
			kind = -1;
			String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
			for (int i = 0; i < kinds.size(); i++) {
				if (kinds.get(i).on().get() && kinds.get(i).matches(path)) {
					kind = i;
					break;
				}
			}
			kindOf.put(block, kind);
			dyeOf.put(block, path.endsWith("_shulker_box") ? DYES.getOrDefault(path.substring(0, path.length() - 12), 0) : 0);
		}
		return kind;
	}
	private boolean isCart(Entity e) {
		net.minecraft.world.entity.EntityType<?> type = e.getType();
		if (!cartTypes.containsKey(type)) {
			String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
			cartTypes.put(type, id.equals("chest_minecart") || id.equals("hopper_minecart") || id.contains("chest_boat") || id.contains("chest_raft"));
		}
		return cartTypes.getBoolean(type);
	}
	private void tick() {
		carts.clear();
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			scan.clear();
			shown.clear();
			boxes = List.of();
			seenAt = null;
			return;
		}
		int r = (int) Math.ceil(nz.temple.client.util.VisualRange.of(range.get()));
		scan.tick((r + 15) / 16, 24, (level, chunk, out) -> {
			for (BlockPos pos : chunk.getBlockEntities().keySet()) {
				net.minecraft.world.level.block.Block block = chunk.getBlockState(pos).getBlock();
				int kind = kindOf(block);
				if (kind >= 0) {
					out.add(new Hit(new AABB(pos).deflate(0.025), kind, dyeOf.getInt(block)));
				}
			}
		});
		double maxSq = (double) r * r;
		Vec3 eye = player.getEyePosition();
		if (cartsOn.get()) {
			for (Entity e : mc.level.entitiesForRendering()) {
				if (isCart(e) && e.distanceToSqr(eye) <= maxSq) {
					carts.add(new Hit(e.getBoundingBox(), -1, 0));
				}
			}
		}
		int cap = (int) Math.round(maxShown.get());
		long look = look() * 31 + r * 4099L + cap;
		BlockPos at = player.blockPosition();
		if (scan.version() == seenScan && look == seenLook && seenAt != null && seenAt.distManhattan(at) < 2) {
			return;
		}
		seenScan = scan.version();
		seenLook = look;
		seenAt = at;
		scan.nearest(h -> h.bounds().distanceToSqr(eye), maxSq, cap, shown);
		List<WorldEsp.Box> next = new ArrayList<>(shown.size());
		for (Hit h : shown) {
			next.add(box(h));
		}
		boxes = next;
	}
	private long look() {
		long look = style.index() * 131L + Math.round(fillOpacity.getFloat() * 10) * 17L + Math.round(lineWidth.getFloat() * 100)
				+ (shulkerDye.get() ? 7919 : 0);
		for (Kind k : kinds) {
			look = look * 31 + k.color().get();
		}
		return look;
	}
	private WorldEsp.Box box(Hit h) {
		int fill = style.is("Outline") ? 0 : Math.round(fillOpacity.getFloat() / 100f * 255);
		boolean outline = !style.is("Fill");
		int c = rgb(h);
		return new WorldEsp.Box(h.bounds(), outline ? 0xE6000000 | c : 0, (fill << 24) | c, outline ? lineWidth.getFloat() / 100f : 0);
	}
	private int rgb(Hit h) {
		if (h.kind() < 0) {
			return cartColor.get() & 0xFFFFFF;
		}
		if (h.dye() != 0 && shulkerDye.get()) {
			return h.dye();
		}
		return kinds.get(h.kind()).color().get() & 0xFFFFFF;
	}
	@Override
	protected void onDisable() {
		scan.clear();
		shown.clear();
		carts.clear();
		boxes = List.of();
		seenAt = null;
	}
	public void collect(List<WorldEsp.Box> out) {
		if (isEnabled()) {
			out.addAll(boxes);
		}
	}
	public void collectCarts(List<WorldEsp.Box> out) {
		if (isEnabled()) {
			for (Hit h : carts) {
				out.add(box(h));
			}
		}
	}
	public void lines(List<WorldEsp.Line> out, CameraRenderState camera, float partial) {
		if (!isEnabled() || !tracers.get() || camera.pos == null) {
			return;
		}
		Vec3 forward = Vec3.directionFromRotation(camera.xRot, camera.yRot);
		org.joml.Vector3f ahead = nz.temple.client.render.LevelProjection.ahead(1.5f);
		Vec3 start = ahead != null ? camera.pos.add(ahead.x, ahead.y, ahead.z) : camera.pos.add(forward.scale(1.5));
		tracers(out, shown, camera.pos, forward, start);
		tracers(out, carts, camera.pos, forward, start);
	}
	private void tracers(List<WorldEsp.Line> out, List<Hit> hits, Vec3 cam, Vec3 forward, Vec3 start) {
		double startAhead = 1.5;
		for (Hit h : hits) {
			AABB b = h.bounds();
			double ex = (b.minX + b.maxX) * 0.5, ey = (b.minY + b.maxY) * 0.5, ez = (b.minZ + b.maxZ) * 0.5;
			double endAhead = (ex - cam.x) * forward.x + (ey - cam.y) * forward.y + (ez - cam.z) * forward.z;
			if (endAhead < 0.5) {
				double t = (0.5 - startAhead) / (endAhead - startAhead);
				if (!(t > 0) || !(t < 1)) {
					continue;
				}
				ex = start.x + (ex - start.x) * t;
				ey = start.y + (ey - start.y) * t;
				ez = start.z + (ez - start.z) * t;
			}
			out.add(new WorldEsp.Line(start.x, start.y, start.z, ex, ey, ez, 0xB4000000 | rgb(h), 1.2f));
		}
	}
}
