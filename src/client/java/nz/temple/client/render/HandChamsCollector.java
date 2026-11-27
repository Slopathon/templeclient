package nz.temple.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import nz.temple.client.mixin.RenderSetupAccessor;
import nz.temple.client.mixin.RenderTypeStateAccessor;
import nz.temple.client.mixin.TextureBindingAccessor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stands in for the collector the first-person hand is submitted to (see ItemInHandChamsMixin). Everything is passed
 * straight through, except that the arm and the held item are also submitted again with the hand chams render types -
 * and, in Replace mode, not in their original form at all.
 *
 * <p>Arms and special item models (shields, tridents) arrive as {@code submitModel}; ordinary items as
 * {@code submitItem}, which carries baked quads rather than a render type, so those are drawn again as custom geometry.
 */
public final class HandChamsCollector implements SubmitNodeCollector {
	/** What to draw: Hand Chams for your own first-person hand, Chams for items held by other entities. */
	public interface Source {
		boolean handOn();

		boolean weaponOn(ItemStack stack);

		boolean replaces(boolean arm);

		int overlay(boolean arm);

		int argb(boolean arm);

		boolean glowOn();

		int[] glowLayers();

		int glowArgb(boolean arm, int layer);

		/** Draw the chams item over terrain too (entity chams in Walls / Both mode). */
		default boolean throughWalls() {
			return false;
		}
	}

	/** Submit orders for the extra passes: after anything vanilla puts in the hand pass, glow last. */
	private static final int ORDER_BODY = 40;
	private static final int ORDER_GLOW = 41;
	private static final Identifier WHITE = Identifier.withDefaultNamespace("textures/misc/white.png");

	/** Above zero while AvatarRenderer is drawing a first-person arm. */
	private static int armDepth;
	/** The item ItemInHandRenderer is drawing right now. */
	private static ItemStack stack = ItemStack.EMPTY;

	private final OrderedSubmitNodeCollector out;
	private final SubmitNodeCollector root;
	private final Source chams;
	/**
	 * Wrapping a whole entity (you, seen in third person) rather than the first-person hand: every model part is
	 * body, and only what is submitted while a held item is being drawn counts as the item.
	 */
	private final boolean body;

	private HandChamsCollector(OrderedSubmitNodeCollector out, SubmitNodeCollector root, Source chams, boolean body) {
		this.out = out;
		this.root = root;
		this.chams = chams;
		this.body = body;
	}

	/**
	 * The collector to hand the first-person renderer: this wrapper while Hand Chams is on or the hand's afterimages
	 * are being recorded, otherwise the original.
	 */
	public static SubmitNodeCollector wrap(SubmitNodeCollector collector) {
		Source chams = nz.temple.client.modules.render.HandChams.active();
		if (chams == null && Afterimages.recording() && !(collector instanceof HandChamsCollector)) {
			return new HandChamsCollector(collector, collector, null, false);
		}
		return wrap(collector, chams);
	}

	/** Wraps {@code collector} so what is submitted through it gets {@code chams}; null leaves it alone. */
	public static SubmitNodeCollector wrap(SubmitNodeCollector collector, Source chams) {
		if (chams == null || collector instanceof HandChamsCollector) {
			return collector;
		}
		return new HandChamsCollector(collector, collector, chams, false);
	}

	/**
	 * Wraps the collector your own body is submitted to when you see yourself from outside, so the body takes the
	 * hand look and held items the weapon look.
	 */
	public static SubmitNodeCollector wrapBody(SubmitNodeCollector collector, Source chams) {
		if (chams == null || collector instanceof HandChamsCollector) {
			return collector;
		}
		return new HandChamsCollector(collector, collector, chams, true);
	}

	public static void armBegin() {
		armDepth++;
	}

	public static void armEnd() {
		armDepth = Math.max(0, armDepth - 1);
	}

	public static void item(ItemStack current) {
		stack = current == null ? ItemStack.EMPTY : current;
	}

	private boolean applies(boolean arm) {
		return chams != null && (arm ? chams.handOn() : chams.weaponOn(stack));
	}

	@Override
	public OrderedSubmitNodeCollector order(int order) {
		return new HandChamsCollector(root.order(order), root, chams, body);
	}

	@Override
	// 26.3 takes any UV mapping where a sprite went, and dropped the block-cracking overlay.
	//? if >=26.3 {
	/*public <S> void submitModel(Model<? super S> model, S state, PoseStack pose, RenderType type, int light, int overlay, int color,
			net.minecraft.client.renderer.texture.UvMapping uv, int outlineColor) {
		TextureAtlasSprite sprite = uv instanceof TextureAtlasSprite s ? s : null;
		Runnable original = () -> out.submitModel(model, state, pose, type, light, overlay, color, uv, outlineColor);
	*///?} else {
	public <S> void submitModel(Model<? super S> model, S state, PoseStack pose, RenderType type, int light, int overlay, int color,
			TextureAtlasSprite sprite, int outlineColor, ModelFeatureRenderer.CrumblingOverlay crumbling) {
		Runnable original = () -> out.submitModel(model, state, pose, type, light, overlay, color, sprite, outlineColor, crumbling);
	//?}
		chamsModel(model, state, pose, type, light, sprite, original);
	}

	//? if <26 {
	/*// 1.21.11 submits the first-person arm as a bare model part. It takes the same path as a model: a model whose root is
	// that part, holding the pose the part had when it was submitted.
	@Override
	public void submitModelPart(net.minecraft.client.model.geom.ModelPart part, PoseStack pose, RenderType type, int light, int overlay,
			TextureAtlasSprite sprite, boolean sheeted, boolean foil, int color, ModelFeatureRenderer.CrumblingOverlay crumbling,
			int outlineColor) {
		chamsModel(new PartModel(part, type), null, pose, type, light, sprite,
				() -> out.submitModelPart(part, pose, type, light, overlay, sprite, sheeted, foil, color, crumbling, outlineColor));
	}

	private static final class PartModel extends Model<Object> {
		private final net.minecraft.client.model.geom.PartPose saved;

		PartModel(net.minecraft.client.model.geom.ModelPart part, RenderType type) {
			super(part, texture -> type);
			saved = part.storePose();
		}

		@Override
		public void setupAnim(Object state) {
			root().loadPose(saved);
		}
	}
	*///?}

	private <S> void chamsModel(Model<? super S> model, S state, PoseStack pose, RenderType type, int light, TextureAtlasSprite sprite,
			Runnable original) {
		boolean arm = body ? stack.isEmpty() : armDepth > 0;
		if (!body) {
			Afterimages.model(arm, model, state, pose, sprite != null ? sprite.atlasLocation() : texture(type), sprite, light, stack);
		}
		if (!applies(arm)) {
			original.run();
			return;
		}
		if (!chams.replaces(arm)) {
			original.run();
		}
		Identifier texture = sprite != null ? sprite.atlasLocation() : texture(type);
		nz.temple.client.compat.Submit.model(root.order(ORDER_BODY), model, state, pose, body(texture), light, chams.overlay(arm),
				chams.argb(arm), sprite, 0);
		if (chams.glowOn()) {
			int[] layers = chams.glowLayers();
			for (int i = 0; i < layers.length; i++) {
				nz.temple.client.compat.Submit.model(root.order(ORDER_GLOW), model, state, pose, ChamsRenderTypes.handGlow(texture), light, layers[i],
						chams.glowArgb(arm, i), sprite, 0);
			}
		}
	}

	@Override
	// 26.3 passes the quads split by blending; the chams draw all of them alike.
	//? if >=26.3 {
	/*public void submitItem(PoseStack pose, ItemDisplayContext context, int light, int overlay, int outlineColor, int[] tints,
			net.minecraft.client.resources.model.geometry.ItemQuads itemQuads, ItemStackRenderState.FoilType foil) {
		List<BakedQuad> quads = itemQuads.all();
		Runnable original = () -> out.submitItem(pose, context, light, overlay, outlineColor, tints, itemQuads, foil);
	*///?} else if >=26 {
	public void submitItem(PoseStack pose, ItemDisplayContext context, int light, int overlay, int outlineColor, int[] tints,
			List<BakedQuad> quads, ItemStackRenderState.FoilType foil) {
		Runnable original = () -> out.submitItem(pose, context, light, overlay, outlineColor, tints, quads, foil);
	//?} else {
	/*public void submitItem(PoseStack pose, ItemDisplayContext context, int light, int overlay, int outlineColor, int[] tints,
			List<BakedQuad> quads, RenderType type, ItemStackRenderState.FoilType foil) {
		Runnable original = () -> out.submitItem(pose, context, light, overlay, outlineColor, tints, quads, type, foil);
	*///?}
		if (!body) {
			Afterimages.item(pose, quads, light, stack);
		}
		if (!applies(false) || quads.isEmpty()) {
			original.run();
			return;
		}
		if (!chams.replaces(false)) {
			original.run();
		}
		// Baked quads name their own atlas; nearly always one, but group them in case an item mixes two.
		Map<Identifier, List<BakedQuad>> byAtlas = new LinkedHashMap<>();
		for (BakedQuad quad : quads) {
			byAtlas.computeIfAbsent(quad.materialInfo().sprite().atlasLocation(), k -> new ArrayList<>()).add(quad);
		}
		int body = chams.argb(false);
		int bodyOverlay = chams.overlay(false);
		for (Map.Entry<Identifier, List<BakedQuad>> group : byAtlas.entrySet()) {
			List<BakedQuad> list = List.copyOf(group.getValue());
			root.order(ORDER_BODY).submitCustomGeometry(pose, body(group.getKey()),
					(p, consumer) -> put(p, consumer, list, body, light, bodyOverlay));
			if (chams.glowOn()) {
				int[] layers = chams.glowLayers();
				for (int i = 0; i < layers.length; i++) {
					int colour = chams.glowArgb(false, i);
					int push = layers[i];
					root.order(ORDER_GLOW).submitCustomGeometry(pose, ChamsRenderTypes.handGlow(group.getKey()),
							(p, consumer) -> put(p, consumer, list, colour, light, push));
				}
			}
		}
	}

	private RenderType body(Identifier texture) {
		return chams.throughWalls() ? ChamsRenderTypes.handWalls(texture) : ChamsRenderTypes.hand(texture);
	}

	private static void put(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer consumer, List<BakedQuad> quads, int argb,
			int light, int overlay) {
		QuadInstance instance = new QuadInstance();
		instance.setColor(argb);
		instance.setLightCoords(light);
		instance.setOverlayCoords(overlay);
		for (BakedQuad quad : quads) {
			consumer.putBakedQuad(pose, quad, instance);
		}
	}

	/** The texture a render type samples as Sampler0 (the skin, for an arm), or plain white when it has none. */
	private static Identifier texture(RenderType type) {
		try {
			Map<String, ?> textures = ((RenderSetupAccessor) (Object) ((RenderTypeStateAccessor) type).templenz$state()).templenz$textures();
			Object binding = textures.get("Sampler0");
			if (binding instanceof TextureBindingAccessor b && b.templenz$location() != null) {
				return b.templenz$location();
			}
		} catch (RuntimeException ignored) {
			// Not a render type built from a setup we understand: fall back to a solid silhouette.
		}
		return WHITE;
	}

	// ---- everything else passes straight through ----------------------------------------------------------------

	@Override
	public void submitShadow(PoseStack pose, float radius, List<EntityRenderState.ShadowPiece> pieces) {
		out.submitShadow(pose, radius, pieces);
	}

	//? if >=26 {
	@Override
	public void submitNameTag(PoseStack pose, Vec3 at, int y, Component name, boolean seeThrough, int light, CameraRenderState camera) {
		out.submitNameTag(pose, at, y, name, seeThrough, light, camera);
	}
	//?} else {
	/*@Override
	public void submitNameTag(PoseStack pose, Vec3 at, int y, Component name, boolean seeThrough, int light, double distance,
			CameraRenderState camera) {
		out.submitNameTag(pose, at, y, name, seeThrough, light, distance, camera);
	}
	*///?}

	@Override
	public void submitText(PoseStack pose, float x, float y, FormattedCharSequence text, boolean shadow, Font.DisplayMode mode,
			int light, int color, int background, int outline) {
		out.submitText(pose, x, y, text, shadow, mode, light, color, background, outline);
	}

	@Override
	public void submitFlame(PoseStack pose, EntityRenderState state, Quaternionf rotation) {
		out.submitFlame(pose, state, rotation);
	}

	@Override
	public void submitLeash(PoseStack pose, EntityRenderState.LeashState leash) {
		out.submitLeash(pose, leash);
	}

	//? if >=26 {
	@Override
	public void submitMovingBlock(PoseStack pose, MovingBlockRenderState state, int light) {
		out.submitMovingBlock(pose, state, light);
	}

	@Override
	public void submitBlockModel(PoseStack pose, RenderType type, List<BlockStateModelPart> parts, int[] tints, int light, int overlay,
			int outline) {
		out.submitBlockModel(pose, type, parts, tints, light, overlay, outline);
	}
	//?} else {
	/*@Override
	public void submitMovingBlock(PoseStack pose, MovingBlockRenderState state) {
		out.submitMovingBlock(pose, state);
	}

	@Override
	public void submitBlock(PoseStack pose, net.minecraft.world.level.block.state.BlockState state, int light, int overlay, int outline) {
		out.submitBlock(pose, state, light, overlay, outline);
	}

	@Override
	public void submitBlockModel(PoseStack pose, RenderType type, net.minecraft.client.renderer.block.model.BlockStateModel model, float r,
			float g, float b, int light, int overlay, int outline) {
		out.submitBlockModel(pose, type, model, r, g, b, light, overlay, outline);
	}
	*///?}

	//? if >=26.3 {
	/*@Override
	public <S> void submitCrumblingOverlay(Model<? super S> model, S state, PoseStack pose, RenderType type, int light, int overlay,
			int outlineColor, ModelFeatureRenderer.CrumblingOverlay crumbling) {
		out.submitCrumblingOverlay(model, state, pose, type, light, overlay, outlineColor, crumbling);
	}

	@Override
	public void submitTextBackground(PoseStack pose, float x0, float y0, float x1, float y1, int color, Font.DisplayMode mode,
			int light) {
		out.submitTextBackground(pose, x0, y0, x1, y1, color, mode, light);
	}
	*///?}

	//? if >=26.3 {
	/*@Override
	public void submitBreakingBlockModel(PoseStack pose, List<BlockStateModelPart> parts, int progress, boolean flag) {
		out.submitBreakingBlockModel(pose, parts, progress, flag);
	}
	*///?} else if >=26 {
	@Override
	public void submitBreakingBlockModel(PoseStack pose, List<BlockStateModelPart> parts, int progress) {
		out.submitBreakingBlockModel(pose, parts, progress);
	}
	//?}

	@Override
	public void submitCustomGeometry(PoseStack pose, RenderType type, CustomGeometryRenderer renderer) {
		out.submitCustomGeometry(pose, type, renderer);
	}

	//? if >=26 {
	@Override
	public void submitShapeOutline(PoseStack pose, VoxelShape shape, RenderType type, int color, float width, boolean translucent) {
		out.submitShapeOutline(pose, shape, type, color, width, translucent);
	}

	@Override
	public void submitQuadParticleGroup(QuadParticleRenderState state) {
		out.submitQuadParticleGroup(state);
	}

	@Override
	public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group, CameraRenderState camera, boolean onTop) {
		out.submitGizmoPrimitives(group, camera, onTop);
	}
	//?} else {
	/*@Override
	public void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer renderer) {
		out.submitParticleGroup(renderer);
	}
	*///?}
}
