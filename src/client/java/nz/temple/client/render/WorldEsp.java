package nz.temple.client.render;

import nz.temple.client.compat.Gpu;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import nz.temple.TempleNz;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;

public final class WorldEsp {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/worldesp");
	private static final Vector4f WHITE = new Vector4f(1, 1, 1, 1);
	private static final Vector3f NO_OFFSET = new Vector3f();
	private static final Matrix4f IDENTITY_TEX = new Matrix4f();
	private static final int KEEP_FROM = 48;
	private static final double RESORT_DISTANCE = 16;
	public record Box(AABB bounds, int outlineArgb, int fillArgb, float lineThickness) {
	}
	public record Line(double x0, double y0, double z0, double x1, double y1, double z1, int argb, float width) {
	}

	@FunctionalInterface
	public interface LineSource {
		void collect(List<Line> out, CameraRenderState camera, float partialTick);
	}
	private final class Batch {
		private VertexRing fillRing;
		private VertexRing lineRing;
		private VertexConsumer fill;
		private VertexConsumer line;
		private GpuBuffer fillVertices;
		private GpuBuffer lineVertices;
		private int fillIndices;
		private int lineIndices;
		private double ox;
		private double oy;
		private double oz;
		private float widthScale;
		void begin(double x, double y, double z, float widthScale) {
			ox = x;
			oy = y;
			oz = z;
			this.widthScale = widthScale;
			if (fillRing == null) {
				fillRing = new VertexRing(() -> "Temple.nz ESP", Gpu.vertexFormat(fillPipeline), Gpu.topology(fillPipeline),
						RenderType.SMALL_BUFFER_SIZE);
				lineRing = new VertexRing(() -> "Temple.nz ESP lines", Gpu.vertexFormat(linePipeline),
						Gpu.topology(linePipeline), RenderType.SMALL_BUFFER_SIZE);
			}
			fill = null;
			line = null;
		}
		void box(Box box) {
			AABB a = box.bounds();
			float x0 = (float) (a.minX - ox), y0 = (float) (a.minY - oy), z0 = (float) (a.minZ - oz);
			float x1 = (float) (a.maxX - ox), y1 = (float) (a.maxY - oy), z1 = (float) (a.maxZ - oz);
			int argb = box.fillArgb();
			if ((argb >>> 24) != 0) {
				if (fill == null) {
					fill = fillRing.consumer();
				}
				VertexConsumer vc = fill;
				int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF, al = (argb >>> 24) & 0xFF;
				quad(vc, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, al);
				quad(vc, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, r, g, b, al);
				quad(vc, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, al);
				quad(vc, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, r, g, b, al);
				quad(vc, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, r, g, b, al);
				quad(vc, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, al);
			}
			argb = box.outlineArgb();
			if ((argb >>> 24) != 0 && box.lineThickness() > 0) {
				float w = Math.max(1f, Math.min(3f, box.lineThickness() * 100f)) * widthScale;
				segment(x0, y0, z0, x1, y0, z0, argb, w);
				segment(x0, y0, z1, x1, y0, z1, argb, w);
				segment(x0, y1, z0, x1, y1, z0, argb, w);
				segment(x0, y1, z1, x1, y1, z1, argb, w);
				segment(x0, y0, z0, x0, y0, z1, argb, w);
				segment(x1, y0, z0, x1, y0, z1, argb, w);
				segment(x0, y1, z0, x0, y1, z1, argb, w);
				segment(x1, y1, z0, x1, y1, z1, argb, w);
				segment(x0, y0, z0, x0, y1, z0, argb, w);
				segment(x1, y0, z0, x1, y1, z0, argb, w);
				segment(x0, y0, z1, x0, y1, z1, argb, w);
				segment(x1, y0, z1, x1, y1, z1, argb, w);
			}
		}
		void line(Line l) {
			segment((float) (l.x0() - ox), (float) (l.y0() - oy), (float) (l.z0() - oz),
					(float) (l.x1() - ox), (float) (l.y1() - oy), (float) (l.z1() - oz), l.argb(), l.width() * widthScale);
		}
		private void segment(float ax, float ay, float az, float bx, float by, float bz, int argb, float width) {
			float dx = bx - ax, dy = by - ay, dz = bz - az;
			float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (len < 1e-6f) {
				return;
			}
			dx /= len;
			dy /= len;
			dz /= len;
			if (line == null) {
				line = lineRing.consumer();
			}
			int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF, a = (argb >>> 24) & 0xFF;
			line.addVertex(ax, ay, az).setColor(r, g, b, a).setNormal(dx, dy, dz).setLineWidth(width);
			line.addVertex(bx, by, bz).setColor(r, g, b, a).setNormal(dx, dy, dz).setLineWidth(width);
		}
		void end() {
			VertexRing.Upload f = fill == null ? null : fillRing.upload();
			VertexRing.Upload l = line == null ? null : lineRing.upload();
			fill = null;
			line = null;
			fillVertices = f == null ? null : f.vertices();
			fillIndices = f == null ? 0 : f.indexCount();
			lineVertices = l == null ? null : l.vertices();
			lineIndices = l == null ? 0 : l.indexCount();
		}

		void draw(Matrix4f view, double cx, double cy, double cz) {
			if (fillVertices == null && lineVertices == null) {
				return;
			}
			drawMatrix.set(view).translate((float) (ox - cx), (float) (oy - cy), (float) (oz - cz));
			if (fillVertices != null) {
				execute(fillPipeline, fillVertices, fillIndices, drawMatrix);
			}
			if (lineVertices != null) {
				execute(linePipeline, lineVertices, lineIndices, drawMatrix);
			}
		}
	}
	private final class Feed {
		final Consumer<List<Box>> source;
		List<Box> now = new ArrayList<>();
		List<Box> before = new ArrayList<>();
		Batch kept;
		boolean keptCurrent;
		Feed(Consumer<List<Box>> source) {
			this.source = source;
		}
	}
	private final List<Feed> feeds = new ArrayList<>();
	private final List<LineSource> lineSources = new ArrayList<>();
	private final List<Box> loose = new ArrayList<>();
	private final List<Box> sorting = new ArrayList<>();
	private final List<Line> lines = new ArrayList<>();
	private final Batch frame = new Batch();
	private final Matrix4f drawMatrix = new Matrix4f();
	private final Matrix4f view = new Matrix4f();
	private RenderPipeline fillPipeline;
	private RenderPipeline linePipeline;
	private boolean failed;
	private boolean late;
	private CameraRenderState lateCamera;
	public void register() {
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(ctx -> nz.temple.client.compat.Gpu.outsidePass(() -> draw(ctx)));
		LevelRenderEvents.END_MAIN.register(context -> nz.temple.client.compat.Gpu.outsidePass(this::drawLate));
	}
	public void addSource(Consumer<List<Box>> source) {
		feeds.add(new Feed(source));
	}
	public void addLineSource(LineSource source) {
		lineSources.add(source);
	}
	private void draw(LevelRenderContext context) {
		late = false;
		if (failed) {
			return;
		}
		CameraRenderState camera = context.levelState().cameraRenderState;
		view.set(Gpu.modelView()).mul(context.poseStack().last().pose());
		if (nz.temple.client.modules.render.RealisticWater.underwaterView()) {
			late = true;
			lateCamera = camera;
			return;
		}
		long began = TIMER.begin();
		draw(camera);
		TIMER.end(began);
	}
	private static final nz.temple.util.Perf.Section TIMER = nz.temple.util.Perf.section("world esp");
	private void drawLate() {
		if (!late || failed || lateCamera == null) {
			return;
		}
		late = false;
		long began = TIMER.begin();
		draw(lateCamera);
		TIMER.end(began);
	}
	private void draw(CameraRenderState camera) {
		loose.clear();
		lines.clear();
		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		boolean any = false;
		for (Feed feed : feeds) {
			List<Box> swap = feed.before;
			feed.before = feed.now;
			feed.now = swap;
			swap.clear();
			try {
				feed.source.accept(swap);
			} catch (RuntimeException e) {
				LOGGER.error("ESP source failed", e);
			}
			any |= !swap.isEmpty();
		}
		for (LineSource source : lineSources) {
			try {
				source.collect(lines, camera, partial);
			} catch (RuntimeException e) {
				LOGGER.error("ESP line source failed", e);
			}
		}
		if (!any && lines.isEmpty()) {
			return;
		}
		try {
			if (fillPipeline == null) {
				fillPipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
						.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "pipeline/esp"))
						.withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT))
						.withDepthStencilState(Optional.empty())
						.build());
				linePipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
						.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "pipeline/esp_lines"))
						.withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT))
						.withDepthStencilState(Optional.empty())
						.withCull(false)
						.build());
			}
			double cx = camera.pos.x;
			double cy = camera.pos.y;
			double cz = camera.pos.z;
			float widthScale = Minecraft.getInstance().getWindow().getAppropriateLineWidth() / 2.5f;
			for (Feed feed : feeds) {
				List<Box> boxes = feed.now;
				if (boxes.size() < KEEP_FROM || !same(boxes, feed.before)) {
					feed.keptCurrent = false;
					loose.addAll(boxes);
					continue;
				}
				Batch kept = feed.kept;
				if (kept == null) {
					kept = feed.kept = new Batch();
				}
				double dx = cx - kept.ox, dy = cy - kept.oy, dz = cz - kept.oz;
				if (!feed.keptCurrent || kept.widthScale != widthScale || dx * dx + dy * dy + dz * dz > RESORT_DISTANCE * RESORT_DISTANCE) {
					build(kept, boxes, cx, cy, cz, widthScale);
					feed.keptCurrent = true;
				}
				kept.draw(view, cx, cy, cz);
			}
			if (!loose.isEmpty() || !lines.isEmpty()) {
				build(frame, loose, cx, cy, cz, widthScale);
				frame.draw(view, cx, cy, cz);
			}
		} catch (RuntimeException e) {
			failed = true;
			LOGGER.error("World ESP failed; disabling in-world ESP", e);
		}
	}
	private static boolean same(List<Box> a, List<Box> b) {
		int n = a.size();
		if (n != b.size()) {
			return false;
		}
		for (int i = 0; i < n; i++) {
			if (a.get(i) != b.get(i)) {
				return false;
			}
		}
		return true;
	}

	private void build(Batch batch, List<Box> boxes, double cx, double cy, double cz, float widthScale) {
		batch.begin(cx, cy, cz, widthScale);
		List<Box> ordered = boxes;
		if (boxes.size() > 1 && mixedFills(boxes)) {
			sorting.clear();
			sorting.addAll(boxes);
			sorting.sort((a, b) -> Double.compare(distance(b.bounds(), cx, cy, cz), distance(a.bounds(), cx, cy, cz)));
			ordered = sorting;
		}
		for (int i = 0, n = ordered.size(); i < n; i++) {
			batch.box(ordered.get(i));
		}
		sorting.clear();
		if (batch == frame) {
			for (int i = 0, n = lines.size(); i < n; i++) {
				batch.line(lines.get(i));
			}
		}
		batch.end();
	}
	private static boolean mixedFills(List<Box> boxes) {
		int first = 0;
		for (int i = 0, n = boxes.size(); i < n; i++) {
			int fill = boxes.get(i).fillArgb();
			if ((fill >>> 24) == 0) {
				continue;
			}
			if (first == 0) {
				first = fill;
			} else if (fill != first) {
				return true;
			}
		}
		return false;
	}
	private static double distance(AABB a, double cx, double cy, double cz) {
		double dx = (a.minX + a.maxX) * 0.5 - cx, dy = (a.minY + a.maxY) * 0.5 - cy, dz = (a.minZ + a.maxZ) * 0.5 - cz;
		return dx * dx + dy * dy + dz * dz;
	}
	private void execute(RenderPipeline pipeline, GpuBuffer vertices, int indexCount, Matrix4f modelView) {
		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
				.writeTransform(modelView, WHITE, NO_OFFSET, IDENTITY_TEX);
		RenderSystem.AutoStorageIndexBuffer indices = Gpu.sequentialBuffer(Gpu.topology(pipeline));
		GpuBuffer indexBuffer = indices.getBuffer(indexCount);
		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		try (RenderPass pass = Gpu.openPass(RenderSystem.getDevice().createCommandEncoder(), () -> "Temple.nz ESP", main.getColorTextureView(), Optional.empty(), main.getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(nz.temple.client.compat.Gpu.pipeline(pipeline));
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			Gpu.vertices(pass, vertices);
			pass.setIndexBuffer(indexBuffer, indices.type());
			Gpu.drawIndexed(pass, indexCount, 0, 0);
		}
	}
	private static void quad(VertexConsumer vc,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz,
			int r, int g, int b, int al) {
		vc.addVertex(ax, ay, az).setColor(r, g, b, al);
		vc.addVertex(bx, by, bz).setColor(r, g, b, al);
		vc.addVertex(cx, cy, cz).setColor(r, g, b, al);
		vc.addVertex(dx, dy, dz).setColor(r, g, b, al);
	}
}
