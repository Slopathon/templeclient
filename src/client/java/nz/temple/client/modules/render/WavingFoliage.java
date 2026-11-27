package nz.temple.client.modules.render;

import nz.temple.client.compat.Gpu;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import nz.temple.TempleNz;
import nz.temple.client.util.Place;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.NumberSetting;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class WavingFoliage extends Module {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/waving-foliage");
	private static final int CONFIG_BYTES = 16;
	private static final Set<String> PLANTS = Set.of("short_grass", "tall_grass", "fern", "large_fern", "dead_bush", "bush",
			"short_dry_grass", "tall_dry_grass", "firefly_bush", "sweet_berry_bush", "azalea", "flowering_azalea",
			"leaf_litter", "wildflowers", "pink_petals", "cactus_flower", "pitcher_plant", "torchflower_crop", "pitcher_crop");
	private static final int NONE = 0;
	private static final int LEAVES = 1;
	private static final int VINE = 2;
	private static final int PLANT = 3;
	private static final int TRUNK = 4;
	private static volatile WavingFoliage instance;
	private static volatile boolean tagging;
	private static volatile boolean tagLeaves;
	private static volatile boolean tagPlants;
	private static volatile boolean tagTrees;
	private static final Map<Block, Integer> KINDS = new ConcurrentHashMap<>();
	private static boolean failed;
	private static RenderPipeline solid;
	private static RenderPipeline cutout;
	private static GpuBuffer configBuffer;
	private static final ByteBuffer CONFIG = ByteBuffer.allocateDirect(CONFIG_BYTES).order(ByteOrder.nativeOrder());
	private final BoolSetting leaves;
	private final BoolSetting plants;
	private final BoolSetting trees;
	private final NumberSetting strength;
	private final NumberSetting speed;
	private final NumberSetting gusts;
	public WavingFoliage() {
		super("Waving Foliage", Category.COSMETICS, "Trees, grass, flowers and crops sway in the wind, with occasional gusts.");
		leaves = add(new BoolSetting("Leaves", true).describe("Leaves and vines sway."));
		trees = add(new BoolSetting("Trees", true)
				.describe("Whole trees sway, bending more toward the top. Only logs with natural leaves count as trees, so log builds stay "
						+ "still.")
				.visibleWhen(leaves::get));
		plants = add(new BoolSetting("Plants", true).describe("Grass, flowers, crops and saplings bend from the base."));
		strength = add(new NumberSetting("Strength", 1, 0.2, 3, 0.1, "x").describe("How far things sway."));
		speed = add(new NumberSetting("Speed", 1, 0.2, 3, 0.1, "x").describe("Wind speed."));
		gusts = add(new NumberSetting("Gusts", 60, 0, 100, 5, "%").describe("How much the wind varies in gusts."));
		leaves.addListener(this::retag);
		plants.addListener(this::retag);
		trees.addListener(this::retag);
		instance = this;
	}
	@Override
	protected void onEnable() {
		failed = false;
		retag();
	}
	@Override
	protected void onDisable() {
		tagging = false;
		rebuild();
	}
	private void retag() {
		tagLeaves = leaves.get();
		tagPlants = plants.get();
		tagTrees = leaves.get() && trees.get();
		tagging = isEnabled();
		if (isEnabled()) {
			rebuild();
		}
	}

	private static void rebuild() {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (mc.level != null) {
				mc.levelExtractor.allChanged();
			}
		});
	}

	public static boolean tagging() {
		return tagging;
	}
	private record Current(BlockState state, float baseY, float treeHeight) {
	}

	private static final ThreadLocal<Current> CURRENT = new ThreadLocal<>();

	public static boolean beginBlock(BlockState state, float baseY, net.minecraft.world.level.BlockGetter level,
			net.minecraft.core.BlockPos pos) {
		int kind = !tagging ? NONE : kind(state.getBlock());
		if (kind == NONE || kind == TRUNK && !tagTrees) {
			return false;
		}
		float tree = tagTrees && (kind == TRUNK || kind == LEAVES) ? treeHeight(state, kind, level, pos) : -1;
		if (kind == TRUNK && tree < 0) {
			return false;
		}
		CURRENT.set(new Current(state, baseY, tree));
		return true;
	}
	private static float treeHeight(BlockState state, int kind, net.minecraft.world.level.BlockGetter level,
			net.minecraft.core.BlockPos pos) {
		net.minecraft.core.BlockPos.MutableBlockPos p = pos.mutable();
		int above = 0;
		if (kind == LEAVES) {
			if (naturalLeaf(state) == 0) {
				return -1;
			}
			boolean found = false;
			while (above < 10) {
				p.move(0, -1, 0);
				above++;
				BlockState below = level.getBlockState(p);
				if (kind(below.getBlock()) == TRUNK) {
					found = true;
					break;
				}
				if (kind(below.getBlock()) != LEAVES && !below.isAir()) {
					return -1;
				}
			}
			if (!found) {
				return -1;
			}
		} else {
			net.minecraft.core.BlockPos.MutableBlockPos top = pos.mutable();
			int up = 0;
			while (up < 24 && kind(level.getBlockState(top.above()).getBlock()) == TRUNK) {
				top.move(0, 1, 0);
				up++;
			}
			int leaves = 0;
			for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
				if (d != net.minecraft.core.Direction.DOWN) {
					leaves += naturalLeaf(level.getBlockState(top.relative(d)));
				}
			}
			if (leaves == 0) {
				return -1;
			}
		}
		int logs = 0;
		while (logs < 24 && kind(level.getBlockState(p.move(0, -1, 0)).getBlock()) == TRUNK) {
			logs++;
		}
		return logs + above;
	}
	private static int naturalLeaf(BlockState state) {
		if (kind(state.getBlock()) != LEAVES) {
			return 0;
		}
		return state.hasProperty(BlockStateProperties.PERSISTENT) && state.getValue(BlockStateProperties.PERSISTENT) ? 0 : 1;
	}
	public static void endBlock() {
		CURRENT.remove();
	}
	public static int tagVertex(int argb, float y) {
		Current c = CURRENT.get();
		if (c == null) {
			return argb;
		}
		int alpha = c.treeHeight() >= 0 ? treeAlpha(c.state(), c.treeHeight() + Math.max(0, Math.min(1, y - c.baseY())))
				: alpha(c.state(), y - c.baseY());
		return alpha == 255 ? argb : (alpha << 24) | (argb & 0xFFFFFF);
	}
	private static int kind(Block block) {
		return KINDS.computeIfAbsent(block, b -> {
			BlockState s = b.defaultBlockState();
			String path = Place.path(s);
			if (path.endsWith("_leaves")) {
				return LEAVES;
			}
			if (path.equals("vine")) {
				return VINE;
			}
			if ((path.endsWith("_log") || path.endsWith("_stem")) && !path.startsWith("stripped_")) {
				return TRUNK;
			}
			if (PLANTS.contains(path) || s.is(BlockTags.SMALL_FLOWERS) || s.is(BlockTags.FLOWERS) || s.is(BlockTags.CROPS)
					|| path.endsWith("_sapling")) {
				return PLANT;
			}
			return NONE;
		});
	}
	private static int treeAlpha(BlockState state, float height) {
		if (kind(state.getBlock()) == TRUNK) {
			return 100 + Math.min(49, Math.round(height * 2));
		}
		return 2 + Math.min(97, Math.round(height * 4));
	}
	public static int alpha(BlockState state, float y) {
		int kind = kind(state.getBlock());
		if (kind == LEAVES) {
			return tagLeaves ? 254 : 255;
		}
		if (kind == VINE) {
			return tagLeaves ? 253 : 255;
		}
		if (kind != PLANT || !tagPlants) {
			return 255;
		}
		float h = Math.max(0, Math.min(1, y));
		boolean upper = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
				&& state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
		float w = upper ? 1 + h * 0.8f : h;
		if (w <= 0.001f) {
			return 255;
		}
		return 150 + Math.round(w * 50);
	}
	private static boolean active() {
		WavingFoliage m = instance;
		return m != null && m.isEnabled() && tagging && !failed;
	}
	public static void prepare(ChunkSectionLayerGroup group) {
		WavingFoliage m = instance;
		boolean waving = active();
		if (m == null || failed || !waving && !WetGround.active() || group != ChunkSectionLayerGroup.OPAQUE) {
			return;
		}
		try {
			ensurePipelines();
			if (configBuffer == null) {
				configBuffer = RenderSystem.getDevice().createBuffer(() -> "Temple.nz foliage wind",
						GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, CONFIG_BYTES);
			}
			float seconds = (float) ((System.nanoTime() % 3_600_000_000_000L) / 1e9) * m.speed.getFloat();
			CONFIG.clear();
			Std140Builder.intoBuffer(CONFIG).putVec4(seconds, waving ? m.strength.getFloat() : 0, m.gusts.getFloat() / 100f, 0);
			CONFIG.clear();
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(configBuffer.slice(), CONFIG);
		} catch (RuntimeException e) {
			fail("could not prepare the wind", e);
		}
	}
	public static GpuBuffer windBuffer() {
		return configBuffer;
	}
	public static RenderPipeline pipeline(ChunkSectionLayerGroup group, RenderPipeline original) {
		if (!active() || group != ChunkSectionLayerGroup.OPAQUE) {
			return original;
		}
		if (original != RenderPipelines.CUTOUT_TERRAIN && original != RenderPipelines.SOLID_TERRAIN) {
			return original;
		}
		ensurePipelines();
		return original == RenderPipelines.CUTOUT_TERRAIN ? cutout : solid;
	}
	public static void bind(RenderPass pass, RenderPipeline selected) {
		if ((selected == solid || selected == cutout) && selected != null) {
			pass.setUniform("WaveConfig", configBuffer);
		}
	}

	public static List<RenderPipeline> pipelines() {
		ensurePipelines();
		return List.of(solid, cutout);
	}
	private static void ensurePipelines() {
		if (solid == null) {
			solid = build(RenderPipelines.SOLID_TERRAIN, "solid");
			cutout = build(RenderPipelines.CUTOUT_TERRAIN, "cutout");
		}
	}
	private static RenderPipeline build(RenderPipeline source, String name) {
		RenderPipeline.Builder builder = RenderPipeline.builder()
				.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "pipeline/foliage_" + name))
				.withVertexShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/foliage_wave"))
				.withFragmentShader(nz.temple.client.compat.Gpu.fragmentShader(source))
				.withPrimitiveTopology(Gpu.topology(source))
				.withPolygonMode(source.getPolygonMode())
				.withCull(source.isCull())
				.withDepthStencilState(java.util.Optional.ofNullable(Gpu.depthStencil(source)));
		for (var layout : Gpu.bindGroupLayouts(source)) {
			builder.withBindGroupLayout(layout);
		}
		builder.withBindGroupLayout(BindGroupLayout.builder().withUniform("WaveConfig", UniformType.UNIFORM_BUFFER).build());
		for (int i = 0; i < nz.temple.client.compat.Gpu.colorTargets(source).size(); i++) {
			builder.withColorTargetState(i, nz.temple.client.compat.Gpu.colorTargets(source).get(i));
		}
		for (int i = 0; i < nz.temple.client.compat.Gpu.vertexBindings(source).size(); i++) {
			builder.withVertexBinding(i, nz.temple.client.compat.Gpu.vertexBindings(source).get(i));
		}
		source.getShaderDefines().flags().forEach(builder::withShaderDefine);
		source.getShaderDefines().values().forEach((key, value) -> {
			try {
				if (value.contains(".")) {
					builder.withShaderDefine(key, Float.parseFloat(value));
				} else {
					builder.withShaderDefine(key, Integer.parseInt(value));
				}
			} catch (NumberFormatException e) {
				builder.withShaderDefine(key);
			}
		});
		return RenderPipelines.register(builder.build());
	}
	private static void fail(String message, RuntimeException error) {
		if (!failed) {
			failed = true;
			LOGGER.error("Waving foliage disabled: {}", message, error);
		}
	}
}
