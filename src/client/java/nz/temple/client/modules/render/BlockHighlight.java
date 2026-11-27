package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import nz.temple.client.hud.BlockInfo;
import nz.temple.client.render.WorldEsp;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;
import java.util.Locale;

public final class BlockHighlight extends Module {
	private final ModeSetting shape;
	private final BoolSetting outline;
	private final ColorSetting color;
	private final ModeSetting thickness;
	private final BoolSetting fill;
	private final ColorSetting fillColor;
	private final NumberSetting fillOpacity;
	private final BoolSetting glide;
	private final BoolSetting info;
	private final BoolSetting infoMining;
	private final BoolSetting infoCoords;
	private final BoolSetting infoDetails;
	private final double[] shown = new double[6];
	private boolean hasShown;
	private long lastNanos = System.nanoTime();
	public BlockHighlight() {
		super("Block Highlight", Category.RENDER, "Outlines or fills the block you are looking at, with an optional info card.");
		shape = add(new ModeSetting("Shape", List.of("Cube", "Face"), "Cube")
				.describe("Cube highlights the whole block. Face highlights only the side you are aiming at."));
		outline = add(new BoolSetting("Outline", true).describe("Draws the edges."));
		color = add(new ColorSetting("Color", 0xFFFF5A5A).themed().describe("Outline colour.").visibleWhen(outline::get));
		thickness = add(new ModeSetting("Line width", List.of("Thin", "Medium", "Thick"), "Thin").describe("Outline thickness.")
				.visibleWhen(outline::get));
		fill = add(new BoolSetting("Filled", false).describe("Fills the block or face with colour."));
		fillColor = add(new ColorSetting("Fill color", 0xFFE9B949).themed().describe("Fill colour.").visibleWhen(fill::get));
		fillOpacity = add(new NumberSetting("Fill opacity", 22, 3, 90, 1, "%").describe("How solid the fill is.").visibleWhen(fill::get));
		glide = add(new BoolSetting("Glide", true).describe("Slides between blocks instead of jumping."));
		group();
		info = add(new BoolSetting("Block info", true)
				.describe("A card next to the block showing what it is and how long it takes to mine. Its position (follow the block, or a "
						+ "fixed spot) is set in Client > HUD."));
		infoMining = add(new BoolSetting("Mining time", true).describe("Time to break it with your held item, and whether it drops.")
				.visibleWhen(info::get));
		infoCoords = add(new BoolSetting("Coordinates", true).describe("The block's position.").visibleWhen(info::get));
		infoDetails = add(new BoolSetting("Details", false).describe("Hardness, blast resistance and light level.").visibleWhen(info::get));
	}
	private BlockHitResult target(Minecraft mc) {
		if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) {
			return null;
		}
		return hit;
	}
	private AABB bounds(Minecraft mc, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		VoxelShape vs = mc.level.getBlockState(pos).getShape(mc.level, pos);
		AABB b = vs.isEmpty() ? new AABB(pos) : vs.bounds().move(pos);
		if (!shape.is("Face")) {
			return b;
		}
		Direction d = hit.getDirection();
		double lift = 0.003;
		return switch (d) {
			case UP -> new AABB(b.minX, b.maxY, b.minZ, b.maxX, b.maxY + lift, b.maxZ);
			case DOWN -> new AABB(b.minX, b.minY - lift, b.minZ, b.maxX, b.minY, b.maxZ);
			case NORTH -> new AABB(b.minX, b.minY, b.minZ - lift, b.maxX, b.maxY, b.minZ);
			case SOUTH -> new AABB(b.minX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ + lift);
			case WEST -> new AABB(b.minX - lift, b.minY, b.minZ, b.minX, b.maxY, b.maxZ);
			case EAST -> new AABB(b.maxX, b.minY, b.minZ, b.maxX + lift, b.maxY, b.maxZ);
		};
	}
	public void collect(List<WorldEsp.Box> out) {
		long now = System.nanoTime();
		double dt = Math.min((now - lastNanos) / 1e9, 0.1);
		lastNanos = now;
		Minecraft mc = Minecraft.getInstance();
		BlockHitResult hit = isEnabled() ? target(mc) : null;
		if (hit == null) {
			hasShown = false;
			return;
		}
		AABB b = bounds(mc, hit);
		double[] want = {b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ};
		if (!hasShown || !glide.get()) {
			System.arraycopy(want, 0, shown, 0, 6);
			hasShown = true;
		} else {
			double k = 1 - Math.exp(-24 * dt);
			for (int i = 0; i < 6; i++) {
				shown[i] += (want[i] - shown[i]) * k;
			}
		}
		AABB box = new AABB(shown[0], shown[1], shown[2], shown[3], shown[4], shown[5]);
		int line = outline.get() ? 0xFF000000 | (color.get() & 0xFFFFFF) : 0;
		int body = fill.get() ? Math.round(fillOpacity.getFloat() / 100f * 255) << 24 | (fillColor.get() & 0xFFFFFF) : 0;
		float width = switch (thickness.get()) {
			case "Medium" -> 0.02f;
			case "Thick" -> 0.03f;
			default -> 0.012f;
		};
		out.add(new WorldEsp.Box(box, line, body, outline.get() ? width : 0));
	}
	public BlockInfo info(boolean placeholder) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || !info.get() || mc.level == null || mc.player == null) {
			return null;
		}
		BlockHitResult hit = target(mc);
		if (hit == null) {
			if (!placeholder) {
				return null;
			}
			BlockPos pos = mc.player.blockPosition().below();
			return new BlockInfo(pos, new ItemStack(Blocks.STONE), "Stone", coords(pos), infoMining.get() ? "Breaks in 0.35 s" : null, true,
					infoDetails.get() ? "Hardness 1.5  ·  Blast 6  ·  Light 15" : null);
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = mc.level.getBlockState(pos);
		ItemStack icon = new ItemStack(state.getBlock().asItem());
		String name = state.getBlock().getName().getString();
		String mining = null;
		boolean good = true;
		if (infoMining.get()) {
			float per = state.getDestroyProgress(mc.player, mc.level, pos);
			good = !state.requiresCorrectToolForDrops() || mc.player.hasCorrectToolForDrops(state);
			if (per <= 0) {
				mining = "Unbreakable";
				good = false;
			} else if (per >= 1) {
				mining = "Breaks instantly";
			} else {
				int ticks = (int) Math.ceil(1 / per);
				mining = String.format(Locale.ROOT, "Breaks in %.2f s", ticks / 20.0) + (good ? "" : "  ·  no drop");
			}
		}
		String details = null;
		if (infoDetails.get()) {
			int light = mc.level.getMaxLocalRawBrightness(pos.relative(hit.getDirection()));
			details = String.format(Locale.ROOT, "Hardness %s  ·  Blast %s  ·  Light %d", trim(state.getDestroySpeed(mc.level, pos)),
					trim(state.getBlock().getExplosionResistance()), light);
		}
		if (name.isEmpty()) {
			name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		}
		return new BlockInfo(pos, icon, name, infoCoords.get() ? coords(pos) : null, mining, good, details);
	}
	private static String coords(BlockPos pos) {
		return nz.temple.client.modules.client.StreamerMode.alert(pos.getX(), pos.getY(), pos.getZ());
	}
	private static String trim(float v) {
		return v == Math.floor(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v);
	}
}
