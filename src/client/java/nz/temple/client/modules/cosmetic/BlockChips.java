package nz.temple.client.modules.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import nz.temple.client.util.BlockHider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

final class BlockChips {
	private static final int N = 4;
	private static final int COUNT = N * N * N;
	private static final double MAX_OFF = 0.9;
	private static final class Chip {
		final int voxel;
		double x;
		double y;
		double z;
		double vx;
		double vy;
		double vz;
		final Vector3f axis;
		final double spin;
		double age;
		final double life;
		Chip(int voxel, double x, double y, double z, double vx, double vy, double vz, Vector3f axis, double spin, double life) {
			this.voxel = voxel;
			this.x = x;
			this.y = y;
			this.z = z;
			this.vx = vx;
			this.vy = vy;
			this.vz = vz;
			this.axis = axis;
			this.spin = spin;
			this.life = life;
		}
	}
	private BlockPos pos;
	private BlockState state;
	private ClientLevel level;
	private int[] order;
	private final boolean[] gone = new boolean[COUNT];
	private int goneCount;
	private final List<Chip> flying = new ArrayList<>();
	private final TextureAtlasSprite[] sprites = new TextureAtlasSprite[6];
	private final int[] tints = new int[6];
	private int light;
	private long lastNanos = System.nanoTime();
	void mine(BlockPos minedPos, BlockState minedState, float progress) {
		Minecraft mc = Minecraft.getInstance();
		if (!minedPos.equals(pos)) {
			stop(false);
			if (minedState.getRenderShape() != RenderShape.MODEL || !minedState.getFluidState().isEmpty() || !capture(mc, minedPos, minedState)) {
				return;
			}
			pos = minedPos.immutable();
			state = minedState;
			level = mc.level;
			order = order(pos.asLong());
			Arrays.fill(gone, false);
			goneCount = 0;
			BlockHider.hide(pos);
		}
		int target = (int) Math.floor(Math.min(1, progress) * COUNT * MAX_OFF);
		while (goneCount < target) {
			launch(order[goneCount], 1);
			gone[order[goneCount]] = true;
			goneCount++;
		}
	}
	void stop(boolean shatter) {
		if (pos == null) {
			return;
		}
		if (shatter) {
			for (int i = 0; i < COUNT; i++) {
				if (!gone[i]) {
					launch(i, 1.3);
				}
			}
		}
		BlockHider.show(pos);
		pos = null;
		if (!shatter) {
			flying.clear();
		}
	}
	private void launch(int voxel, double force) {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		double[] c = centre(voxel);
		double ox = c[0] - 0.5;
		double oy = c[1] - 0.5;
		double oz = c[2] - 0.5;
		double len = Math.max(0.05, Math.sqrt(ox * ox + oy * oy + oz * oz));
		double speed = r.nextDouble(0.8, 1.8) * force;
		Vector3f axis = new Vector3f((float) r.nextGaussian(), (float) r.nextGaussian(), (float) r.nextGaussian()).normalize();
		flying.add(new Chip(voxel, pos.getX() + c[0], pos.getY() + c[1], pos.getZ() + c[2],
				ox / len * speed + r.nextGaussian() * 0.3, oy / len * speed + r.nextDouble(1.2, 2.4), oz / len * speed + r.nextGaussian() * 0.3,
				axis, r.nextDouble(5, 12) * (r.nextBoolean() ? 1 : -1), r.nextDouble(0.7, 1.1)));
	}
	private boolean capture(Minecraft mc, BlockPos at, BlockState blockState) {
		BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(blockState);
		List<BlockStateModelPart> parts = new ArrayList<>();
		model.collectParts(RandomSource.create(at.asLong()), parts);
		TextureAtlasSprite particle = model.particleMaterial().sprite();
		for (Direction d : Direction.values()) {
			TextureAtlasSprite sprite = null;
			int tint = 0xFFFFFFFF;
			for (BlockStateModelPart part : parts) {
				List<BakedQuad> quads = part.getQuads(d);
				if (!quads.isEmpty()) {
					BakedQuad q = quads.getFirst();
					sprite = q.materialInfo().sprite();
					if (q.materialInfo().isTinted()) {
						tint = nz.temple.client.compat.States.tint(blockState, mc.level, at);
					}
					break;
				}
			}
			sprites[d.ordinal()] = sprite != null ? sprite : particle;
			tints[d.ordinal()] = tint;
		}
		int sky = 0;
		int block = 0;
		for (Direction d : Direction.values()) {
			BlockPos n = at.relative(d);
			sky = Math.max(sky, mc.level.getBrightness(LightLayer.SKY, n));
			block = Math.max(block, mc.level.getBrightness(LightLayer.BLOCK, n));
		}
		light = LightCoordsUtil.pack(block, sky);
		return sprites[0] != null;
	}
	void submit(LevelRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		long now = System.nanoTime();
		double dt = Math.min((now - lastNanos) / 1e9, 0.1);
		lastNanos = now;
		if (mc.level == null || level != null && level != mc.level) {
			if (pos != null) {
				BlockHider.show(pos);
			}
			pos = null;
			flying.clear();
			level = mc.level;
			return;
		}
		if (pos == null && flying.isEmpty()) {
			return;
		}
		for (int i = flying.size() - 1; i >= 0; i--) {
			Chip c = flying.get(i);
			c.age += dt;
			if (c.age > c.life) {
				flying.remove(i);
				continue;
			}
			c.vy -= 16 * dt;
			c.x += c.vx * dt;
			c.y += c.vy * dt;
			c.z += c.vz * dt;
		}
		Vec3 cam = ctx.levelState().cameraRenderState.pos;
		PoseStack pose = ctx.poseStack();
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		var atlas = sprites[0].atlasLocation();
		BlockPos at = pos;
		List<Chip> pieces = List.copyOf(flying);
		ctx.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.entityCutout(atlas), (p, consumer) -> {
			if (at != null) {
				for (int v = 0; v < COUNT; v++) {
					if (!gone[v]) {
						voxel(p, consumer, v, at.getX(), at.getY(), at.getZ(), null, 1, true);
					}
				}
			}
			for (Chip c : pieces) {
				double left = 1 - c.age / c.life;
				float shrink = (float) Math.min(1, left * 3);
				Quaternionf spin = new Quaternionf().rotationAxis((float) (c.age * c.spin), c.axis);
				double[] ctr = centre(c.voxel);
				voxel(p, consumer, c.voxel, c.x - ctr[0], c.y - ctr[1], c.z - ctr[2], spin, shrink, false);
			}
		});
		pose.popPose();
	}
	private void voxel(PoseStack.Pose p, VertexConsumer out, int v, double bx, double by, double bz, Quaternionf spin, float scale,
			boolean onlyOpen) {
		int ix = v % N;
		int iy = (v / N) % N;
		int iz = v / (N * N);
		float s = 1f / N;
		float x0 = ix * s;
		float y0 = iy * s;
		float z0 = iz * s;
		float cx = x0 + s / 2;
		float cy = y0 + s / 2;
		float cz = z0 + s / 2;
		for (Direction d : Direction.values()) {
			if (onlyOpen) {
				int nx = ix + d.getStepX();
				int ny = iy + d.getStepY();
				int nz = iz + d.getStepZ();
				boolean inside = nx >= 0 && ny >= 0 && nz >= 0 && nx < N && ny < N && nz < N;
				if (inside && !gone[nx + ny * N + nz * N * N]) {
					continue;
				}
			}
			float[][] corners = corners(d, x0, y0, z0, x0 + s, y0 + s, z0 + s);
			TextureAtlasSprite sprite = sprites[d.ordinal()];
			int tint = tints[d.ordinal()];
			int r = (tint >> 16) & 255;
			int g = (tint >> 8) & 255;
			int b = tint & 255;
			int color = 0xFF000000 | r << 16 | g << 8 | b;
			Vector3f n = new Vector3f(d.getStepX(), d.getStepY(), d.getStepZ());
			if (spin != null) {
				spin.transform(n);
			}
			float[][] pts = new float[4][];
			float[][] uvs = new float[4][];
			for (int i = 0; i < 4; i++) {
				float[] c = corners[i];
				Vector3f local = new Vector3f(c[0] - cx, c[1] - cy, c[2] - cz).mul(scale);
				if (spin != null) {
					spin.transform(local);
				}
				pts[i] = new float[] {(float) (bx + cx + local.x), (float) (by + cy + local.y), (float) (bz + cz + local.z)};
				float[] uv = faceUv(d, c[0], c[1], c[2]);
				uvs[i] = new float[] {sprite.getU(uv[0]), sprite.getV(uv[1])};
			}
			for (int i = 0; i < 4; i++) {
				put(out, p, pts[i], uvs[i], color, n);
			}
			for (int i = 3; i >= 0; i--) {
				put(out, p, pts[i], uvs[i], color, n);
			}
		}
	}
	private void put(VertexConsumer out, PoseStack.Pose p, float[] pt, float[] uv, int color, Vector3f n) {
		out.addVertex(p, pt[0], pt[1], pt[2]).setColor(color).setUv(uv[0], uv[1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(p, n.x, n.y, n.z);
	}
	private static float[] faceUv(Direction d, float x, float y, float z) {
		return switch (d) {
			case UP -> new float[] {x, z};
			case DOWN -> new float[] {x, 1 - z};
			case NORTH -> new float[] {1 - x, 1 - y};
			case SOUTH -> new float[] {x, 1 - y};
			case WEST -> new float[] {z, 1 - y};
			case EAST -> new float[] {1 - z, 1 - y};
		};
	}
	private static float[][] corners(Direction d, float x0, float y0, float z0, float x1, float y1, float z1) {
		return switch (d) {
			case UP -> new float[][] {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
			case DOWN -> new float[][] {{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}};
			case NORTH -> new float[][] {{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}};
			case SOUTH -> new float[][] {{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}};
			case WEST -> new float[][] {{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}};
			case EAST -> new float[][] {{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}};
		};
	}
	private static double[] centre(int v) {
		double s = 1.0 / N;
		return new double[] {(v % N + 0.5) * s, ((v / N) % N + 0.5) * s, (v / (N * N) + 0.5) * s};
	}
	private static int[] order(long seed) {
		Integer[] idx = new Integer[COUNT];
		double[] key = new double[COUNT];
		java.util.Random r = new java.util.Random(seed);
		for (int v = 0; v < COUNT; v++) {
			double[] c = centre(v);
			double d = Math.abs(c[0] - 0.5) + Math.abs(c[1] - 0.5) + Math.abs(c[2] - 0.5);
			key[v] = d + r.nextDouble() * 0.45;
			idx[v] = v;
		}
		Arrays.sort(idx, (a, b) -> Double.compare(key[b], key[a]));
		int[] out = new int[COUNT];
		for (int i = 0; i < COUNT; i++) {
			out[i] = idx[i];
		}
		return out;
	}
}
