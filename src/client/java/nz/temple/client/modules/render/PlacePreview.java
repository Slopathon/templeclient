package nz.temple.client.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import nz.temple.client.modules.cosmetic.Look;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Meshes;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;

public final class PlacePreview extends Module implements nz.temple.client.render.FxSource {
	private static final Quaternionf NONE = new Quaternionf();
	private final ModeSetting ghost;
	private final NumberSetting opacity;
	private final BoolSetting edges;
	private final BoolSetting blocked;
	private final BoolSetting glide;
	private final Look look;
	private double x = Double.NaN;
	private double y;
	private double z;
	private float shown;
	private long lastNanos = System.nanoTime();
	private volatile BlockState state;
	private volatile int tint = 0xFFFFFFFF;
	private volatile int light;
	private volatile boolean obstructedNow;
	public PlacePreview() {
		super("Place Preview", Category.COSMETICS, "Shows a see-through preview of the block you are holding where it will be placed.");
		ghost = add(new ModeSetting("Preview", List.of("Real block", "Glass box"), "Real block")
				.describe("Real block shows the actual block, facing the way it would be placed. Glass box shows a coloured box."));
		opacity = add(new NumberSetting("Opacity", 45, 10, 100, 5, "%").describe("How solid the preview is."));
		edges = add(new BoolSetting("Edges", true).describe("Outlines the cell so the preview is easy to see."));
		blocked = add(new BoolSetting("Show blocked", true).describe("Turns the preview red when something is in the way."));
		glide = add(new BoolSetting("Glide", true).describe("Slide between cells instead of jumping."));
		group();
		look = new Look(s -> add(s), "Glass");
		LevelRenderEvents.COLLECT_SUBMITS.register(this::submit);
	}
	public void emit(Fx fx, float partial) {
		long now = System.nanoTime();
		double dt = Math.min((now - lastNanos) / 1e9, 0.1);
		lastNanos = now;
		Minecraft mc = Minecraft.getInstance();
		state = null;
		if (!isEnabled() || mc.level == null || mc.player == null) {
			x = Double.NaN;
			return;
		}
		BlockPos target = null;
		BlockState placed = null;
		InteractionHand hand = mc.player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof BlockItem ? InteractionHand.MAIN_HAND
				: mc.player.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof BlockItem ? InteractionHand.OFF_HAND : null;
		if (hand != null && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			ItemStack stack = mc.player.getItemInHand(hand);
			try {
				BlockPlaceContext ctx = new BlockPlaceContext(mc.player, hand, stack, hit);
				if (ctx.canPlace() && mc.level.getWorldBorder().isWithinBounds(ctx.getClickedPos())) {
					target = ctx.getClickedPos();
					placed = ((BlockItem) stack.getItem()).getBlock().getStateForPlacement(ctx);
				}
			} catch (RuntimeException e) {
				target = null;
			}
		}
		shown += (float) (((target != null ? 1 : 0) - shown) * (1 - Math.exp(-16 * dt)));
		if (target != null) {
			if (Double.isNaN(x) || !glide.get()) {
				x = target.getX();
				y = target.getY();
				z = target.getZ();
			} else {
				double k = 1 - Math.exp(-28 * dt);
				x += (target.getX() - x) * k;
				y += (target.getY() - y) * k;
				z += (target.getZ() - z) * k;
			}
		}
		if (shown < 0.02f || Double.isNaN(x)) {
			return;
		}
		boolean obstructed = target != null && blocked.get()
				&& mc.level.getEntities(null, new AABB(target)).stream().anyMatch(e -> e.blocksBuilding && !e.isSpectator());
		obstructedNow = obstructed;
		double breathe = 1 + Math.sin(now / 1e9 * 3.2) * 0.012;
		double half = 0.5 * breathe * (0.85 + 0.15 * shown);
		int colour = obstructed ? 0xFFFF4A4A : look.color(0.2);
		float alpha = opacity.getFloat() / 100f * look.alpha() * shown;
		Meshes mesh = fx.mesh();
		boolean real = ghost.is("Real block") && placed != null && hasModel(mc, placed);
		if (real) {
			state = placed;
			tint = tintFor(mc, placed, target);
			light = lightAt(mc, target);
		} else {
			look.apply(mesh, 0.15f);
			mesh.box(x + 0.5, y + 0.5, z + 0.5, NONE, half, half, half, Fx.withAlpha(colour, 255 * alpha));
		}
		if (edges.get()) {
			mesh.material(Meshes.Material.MATTE);
			mesh.emission(0.9f);
			double e = half + 0.004;
			double t = 0.008;
			int edge = Fx.withAlpha(colour, 255 * Math.min(1, alpha * 1.8f));
			for (int i = 0; i < 4; i++) {
				double sx = (i & 1) == 0 ? -e : e;
				double sz = (i & 2) == 0 ? -e : e;
				mesh.box(x + 0.5 + sx, y + 0.5, z + 0.5 + sz, NONE, t, e, t, edge);
				mesh.box(x + 0.5 + sx, y + 0.5 + ((i & 2) == 0 ? -e : e), z + 0.5, NONE, t, t, e, edge);
				mesh.box(x + 0.5, y + 0.5 + ((i & 1) == 0 ? -e : e), z + 0.5 + sz, NONE, e, t, t, edge);
			}
		}
	}
	private void submit(LevelRenderContext ctx) {
		BlockState s = state;
		Minecraft mc = Minecraft.getInstance();
		if (s == null || !isEnabled() || mc.level == null || Double.isNaN(x)) {
			return;
		}
		List<BakedQuad> quads = quads(mc, s);
		if (quads.isEmpty()) {
			return;
		}
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		PoseStack pose = ctx.poseStack();
		pose.pushPose();
		float grow = 0.9f + 0.1f * shown;
		pose.translate(x - cam.x + 0.5, y - cam.y + 0.5, z - cam.z + 0.5);
		pose.scale(grow, grow, grow);
		pose.translate(-0.5, -0.5, -0.5);
		float alpha = opacity.getFloat() / 100f * shown;
		int base = obstructedNow ? 0xFFFF6A6A : 0xFFFFFFFF;
		int tinted = multiply(base, tint);
		int lightCoords = light;
		var atlas = quads.getFirst().materialInfo().sprite().atlasLocation();
		ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityTranslucent(atlas), (p, consumer) -> {
			QuadInstance plain = new QuadInstance();
			plain.setColor(Fx.withAlpha(base, 255 * alpha));
			plain.setLightCoords(lightCoords);
			plain.setOverlayCoords(OverlayTexture.NO_OVERLAY);
			QuadInstance coloured = new QuadInstance();
			coloured.setColor(Fx.withAlpha(tinted, 255 * alpha));
			coloured.setLightCoords(lightCoords);
			coloured.setOverlayCoords(OverlayTexture.NO_OVERLAY);
			for (BakedQuad q : quads) {
				consumer.putBakedQuad(p, q, q.materialInfo().isTinted() ? coloured : plain);
			}
		});
		pose.popPose();
	}
	private static BlockState cachedState;
	private static List<BakedQuad> cachedQuads = List.of();
	private static List<BakedQuad> quads(Minecraft mc, BlockState s) {
		if (s == cachedState) {
			return cachedQuads;
		}
		BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(s);
		List<BlockStateModelPart> parts = new ArrayList<>();
		model.collectParts(RandomSource.create(42), parts);
		List<BakedQuad> out = new ArrayList<>();
		for (BlockStateModelPart part : parts) {
			for (Direction d : Direction.values()) {
				out.addAll(part.getQuads(d));
			}
			out.addAll(part.getQuads(null));
		}
		cachedState = s;
		cachedQuads = List.copyOf(out);
		return cachedQuads;
	}
	private static boolean hasModel(Minecraft mc, BlockState s) {
		return !quads(mc, s).isEmpty();
	}
	private static int tintFor(Minecraft mc, BlockState s, BlockPos at) {
		return nz.temple.client.compat.States.tint(s, mc.level, at);
	}
	private static int lightAt(Minecraft mc, BlockPos at) {
		int sky = mc.level.getBrightness(LightLayer.SKY, at);
		int block = mc.level.getBrightness(LightLayer.BLOCK, at);

		return LightCoordsUtil.pack(Math.max(block, 6), sky);
	}
	private static int multiply(int a, int b) {
		int r = ((a >> 16) & 255) * ((b >> 16) & 255) / 255;
		int g = ((a >> 8) & 255) * ((b >> 8) & 255) / 255;
		int bl = (a & 255) * (b & 255) / 255;
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}
}
