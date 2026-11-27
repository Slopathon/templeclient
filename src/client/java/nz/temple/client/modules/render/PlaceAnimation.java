package nz.temple.client.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import nz.temple.client.util.WorldEvents;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blocks that appear grow into place from their centre instead of popping in, and broken blocks shrink away.
 *
 * <p>The real block is left out of its chunk mesh while it animates (see {@code SectionRegionHideMixin}), and a
 * copy is drawn in its place with vanilla's moving-block renderer - the same one pistons and falling sand use, so
 * it has the right model, tint and light. When the animation ends the block goes back into the mesh, and the copy
 * keeps drawing at full size for a moment so there is no gap while the section is rebuilt.
 */
public final class PlaceAnimation extends Module implements nz.temple.client.modules.cosmetic.Previewable {
	/** Positions left out of chunk meshes right now. Read by the section compiler threads. */
	private static final ConcurrentHashMap<Long, Boolean> HIDDEN = new ConcurrentHashMap<>();
	/** How long the copy lingers at full size after the real block is back, covering the mesh rebuild. */
	private static final double HANDOFF = 0.3;
	private static final int MAX_ANIMS = 96;

	private final ModeSetting style;
	private final NumberSetting duration;
	private final BoolSetting breaking;
	private final NumberSetting breakDuration;
	private final BoolSetting others;
	private final NumberSetting range;
	private final List<Anim> anims = new ArrayList<>();
	private final boolean meshHookAvailable;
	private ClientLevel level;

	private static final class Anim {
		final BlockPos pos;
		final BlockState state;
		final boolean placing;
		/** False for previews and breaks: nothing in the chunk mesh to hide or give back. */
		final boolean hides;
		final long born = System.nanoTime();
		final MovingBlockRenderState render = new MovingBlockRenderState();
		boolean released;

		Anim(BlockPos pos, BlockState state, boolean placing, boolean hides) {
			this.pos = pos.immutable();
			this.state = state;
			this.placing = placing;
			this.hides = hides;
		}

		double age() {
			return (System.nanoTime() - born) / 1e9;
		}
	}

	public PlaceAnimation() {
		super("Place Animation", Category.COSMETICS, "Placed blocks grow in from the centre instead of appearing instantly, and broken "
				+ "blocks shrink away.");
		style = add(new ModeSetting("Style", List.of("Grow", "Pop", "Drop", "Spin"), "Grow")
				.describe("Grow expands from the centre with a slight overshoot. Pop expands with a squash and stretch. Drop falls into "
						+ "place and bounces. Spin expands while rotating a quarter turn."));
		duration = add(new NumberSetting("Duration", 0.28, 0.08, 1.2, 0.02, " s").describe("How long a placed block takes to grow in."));
		breaking = add(new BoolSetting("Animate breaking", true).describe("Broken blocks shrink into their centre."));
		breakDuration = add(new NumberSetting("Break duration", 0.2, 0.06, 1, 0.02, " s").describe("How long a broken block takes to shrink.")
				.visibleWhen(breaking::get));
		group();
		others = add(new BoolSetting("Other players", true).describe("Also animates blocks placed and broken by other players."));
		range = add(new NumberSetting("Range", 24, 4, 64, 2, " m").describe("Only animates blocks within this distance."));
		// Sodium builds chunk meshes itself, so the real block cannot be held back; animating would draw the copy
		// inside a block that is already fully there.
		meshHookAvailable = !FabricLoader.getInstance().isModLoaded("sodium");
		WorldEvents.onBlockChanged(this::changed);
		LevelRenderEvents.COLLECT_SUBMITS.register(this::submit);
	}

	/** Called from chunk-meshing threads for every block with a model. */
	public static boolean hidden(BlockPos pos) {
		return !HIDDEN.isEmpty() && HIDDEN.containsKey(pos.asLong());
	}

	@Override
	public void preview(Vec3 at) {
		level = Minecraft.getInstance().level;
		BlockPos pos = BlockPos.containing(at.x, at.y + 0.5, at.z);
		anims.add(new Anim(pos, Blocks.GOLD_BLOCK.defaultBlockState(), true, false));
		anims.add(new Anim(pos.above(2), Blocks.AMETHYST_BLOCK.defaultBlockState(), false, false));
	}

	private boolean animatable(BlockState state) {
		return state.getRenderShape() == RenderShape.MODEL && state.getFluidState().isEmpty();
	}

	private void changed(BlockPos pos, BlockState oldState, BlockState newState) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.level == null || mc.player == null) {
			return;
		}
		if (level != mc.level) {
			reset();
			level = mc.level;
		}
		double r = range.get();
		double dist = mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		if (dist > r * r || !others.get() && dist > 6.5 * 6.5) {
			return;
		}
		// Whatever was animating here is over: the block changed again (or the server said no to a placement).
		boolean wasPlacing = false;
		for (Iterator<Anim> it = anims.iterator(); it.hasNext(); ) {
			Anim a = it.next();
			if (a.pos.equals(pos)) {
				wasPlacing |= a.placing;
				release(a);
				it.remove();
			}
		}
		boolean placed = oldState.isAir() && !newState.isAir();
		boolean broke = !oldState.isAir() && newState.isAir();
		if (anims.size() >= MAX_ANIMS) {
			return;
		}
		if (placed && animatable(newState) && meshHookAvailable) {
			Anim a = new Anim(pos, newState, true, true);
			HIDDEN.put(a.pos.asLong(), Boolean.TRUE);
			anims.add(a);
		} else if (broke && breaking.get() && !wasPlacing && animatable(oldState)) {
			anims.add(new Anim(pos, oldState, false, false));
		}
	}

	/** Puts a hidden block back into its chunk mesh. */
	private void release(Anim a) {
		if (a.released) {
			return;
		}
		a.released = true;
		if (a.hides && HIDDEN.remove(a.pos.asLong()) != null) {
			ClientLevel lvl = Minecraft.getInstance().level;
			if (lvl != null) {
				// Neighbouring sections too: their faces against this block were left open while it was hidden.
				BlockPos p = a.pos;
				lvl.setSectionRangeDirty(SectionPos.blockToSectionCoord(p.getX() - 1), SectionPos.blockToSectionCoord(p.getY() - 1),
						SectionPos.blockToSectionCoord(p.getZ() - 1), SectionPos.blockToSectionCoord(p.getX() + 1),
						SectionPos.blockToSectionCoord(p.getY() + 1), SectionPos.blockToSectionCoord(p.getZ() + 1));
			}
		}
	}

	private void reset() {
		for (Anim a : anims) {
			release(a);
		}
		anims.clear();
		HIDDEN.clear();
	}

	@Override
	protected void onDisable() {
		reset();
	}

	// ---- drawing ------------------------------------------------------------------------------------------------

	private static double backOut(double t, double overshoot) {
		double u = t - 1;
		return 1 + (overshoot + 1) * u * u * u + overshoot * u * u;
	}

	private static double bounceOut(double t) {
		if (t < 1 / 2.75) {
			return 7.5625 * t * t;
		} else if (t < 2 / 2.75) {
			t -= 1.5 / 2.75;
			return 7.5625 * t * t + 0.75;
		} else if (t < 2.5 / 2.75) {
			t -= 2.25 / 2.75;
			return 7.5625 * t * t + 0.9375;
		}
		t -= 2.625 / 2.75;
		return 7.5625 * t * t + 0.984375;
	}

	private void submit(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (anims.isEmpty()) {
			return;
		}
		if (!isEnabled() || mc.level == null || level != mc.level) {
			reset();
			return;
		}
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		PoseStack pose = ctx.poseStack();
		double grow = duration.get();
		double shrink = breakDuration.get();
		for (Iterator<Anim> it = anims.iterator(); it.hasNext(); ) {
			Anim a = it.next();
			double age = a.age();
			double length = a.placing ? grow : shrink;
			if (a.placing && age >= length) {
				release(a);
			}
			if (age >= length + (a.placing && a.hides ? HANDOFF : 0)) {
				it.remove();
				continue;
			}
			double t = Math.min(1, age / length);
			pose.pushPose();
			pose.translate(a.pos.getX() - cam.x, a.pos.getY() - cam.y, a.pos.getZ() - cam.z);
			if (a.placing) {
				transformIn(pose, t);
			} else {
				// Shrinks with gathering speed and a slight twist, like it is being pulled into its centre.
				double s = 1 - t * t;
				pose.translate(0.5f, 0.5f, 0.5f);
				pose.mulPose(new Quaternionf().rotationY((float) (t * t * 0.6)));
				pose.scale((float) s, (float) s, (float) s);
				pose.translate(-0.5f, -0.5f, -0.5f);
			}
			MovingBlockRenderState state = a.render;
			state.randomSeedPos = a.pos;
			state.blockPos = a.pos;
			state.blockState = a.state;
			state.biome = mc.level.getBiome(a.pos);
			//? if >=26 {
			state.cardinalLighting = mc.level.cardinalLighting();
			state.lightEngine = mc.level.getLightEngine();
			ctx.submitNodeCollector().submitMovingBlock(pose, state, 0);
			//?} else {
			/*state.level = mc.level;
			ctx.submitNodeCollector().submitMovingBlock(pose, state);
			*///?}
			pose.popPose();
		}
	}

	private void transformIn(PoseStack pose, double t) {
		switch (style.get()) {
			case "Pop" -> {
				// Squash and stretch: shoots up tall and thin, then settles wide and back.
				double s = backOut(t, 1.9);
				double wobble = Math.sin(t * Math.PI * 2.5) * (1 - t) * 0.22;
				float sy = (float) Math.max(0, s * (1 + wobble));
				float sxz = (float) Math.max(0, s * (1 - wobble * 0.6));
				pose.translate(0.5f, 0, 0.5f);
				pose.scale(sxz, sy, sxz);
				pose.translate(-0.5f, 0, -0.5f);
			}
			case "Drop" -> {
				double fall = 1 - bounceOut(t);
				float s = (float) (0.7 + 0.3 * Math.min(1, t * 2.5));
				pose.translate(0.5f, (float) (fall * 0.9), 0.5f);
				pose.scale(s, s, s);
				pose.translate(-0.5f, 0, -0.5f);
			}
			case "Spin" -> {
				float s = (float) backOut(t, 1.2);
				pose.translate(0.5f, 0.5f, 0.5f);
				pose.mulPose(new Quaternionf().rotationY((float) ((1 - t) * (1 - t) * Math.PI / 2)));
				pose.scale(s, s, s);
				pose.translate(-0.5f, -0.5f, -0.5f);
			}
			default -> {
				float s = (float) Math.max(0, backOut(t, 1.35));
				pose.translate(0.5f, 0.5f, 0.5f);
				pose.scale(s, s, s);
				pose.translate(-0.5f, -0.5f, -0.5f);
			}
		}
	}
}
