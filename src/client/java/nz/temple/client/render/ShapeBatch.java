package nz.temple.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;

import java.util.Arrays;

/**
 * Flat GUI rectangles collected and handed to the game as one element. Each {@code g.fill} is its own element, which the
 * game copies a pose for, bounds and checks against the others in its layer; a crowd's ESP frames and label pills were
 * well over a thousand of them a frame. Batched, they cost one element per flush.
 *
 * <p>Coordinates are real screen pixels, like {@link Draw2D}'s pixel shapes. Everything in one flush is drawn in the
 * order it was added and under anything submitted after the flush, so flush before drawing text that goes on top.
 */
public final class ShapeBatch implements Draw2D.Rects {
	/** x0, y0, x1, y1, top colour, bottom colour per rectangle. */
	private int[] data = new int[6 * 128];
	private int count;
	private int minX, minY, maxX, maxY;

	public ShapeBatch() {
		reset();
	}

	@Override
	public void fill(int x0, int y0, int x1, int y1, int argb) {
		gradient(x0, y0, x1, y1, argb, argb);
	}

	/** A rectangle fading from {@code top} to {@code bottom}, as {@code GuiGraphicsExtractor.fillGradient} draws it. */
	public void gradient(int x0, int y0, int x1, int y1, int top, int bottom) {
		if (x1 < x0) {
			int t = x0;
			x0 = x1;
			x1 = t;
		}
		if (y1 < y0) {
			int t = y0;
			y0 = y1;
			y1 = t;
		}
		if (x0 == x1 || y0 == y1 || (top >>> 24) == 0 && (bottom >>> 24) == 0) {
			return;
		}
		int o = count * 6;
		if (o + 6 > data.length) {
			data = Arrays.copyOf(data, data.length * 2);
		}
		data[o] = x0;
		data[o + 1] = y0;
		data[o + 2] = x1;
		data[o + 3] = y1;
		data[o + 4] = top;
		data[o + 5] = bottom;
		count++;
		minX = Math.min(minX, x0);
		minY = Math.min(minY, y0);
		maxX = Math.max(maxX, x1);
		maxY = Math.max(maxY, y1);
	}

	public boolean isEmpty() {
		return count == 0;
	}

	/** Submits what has been added, under the GUI's current pose and scissor, and starts empty again. */
	public void flush(GuiGraphicsExtractor g) {
		if (count == 0) {
			return;
		}
		float s = Draw2D.guiScale();
		Matrix3x2f pose = new Matrix3x2f(g.pose()).scale(1 / s, 1 / s);
		ScreenRectangle scissor = g.scissorStack.peek();
		ScreenRectangle bounds = new ScreenRectangle(minX, minY, maxX - minX, maxY - minY).transformMaxBounds(pose);
		if (scissor != null) {
			bounds = scissor.intersection(bounds);
		}
		Batch batch = new Batch(Arrays.copyOf(data, count * 6), pose, scissor, bounds);
		reset();
		if (bounds == null) {
			return;
		}
		//? if >=26 {
		g.guiRenderState.addGuiElement(batch);
		//?} else {
		/*g.guiRenderState.submitGuiElement(batch);
		*///?}
	}

	private void reset() {
		count = 0;
		minX = Integer.MAX_VALUE;
		minY = Integer.MAX_VALUE;
		maxX = Integer.MIN_VALUE;
		maxY = Integer.MIN_VALUE;
	}

	private record Batch(int[] rects, Matrix3x2f pose, ScreenRectangle scissorArea, ScreenRectangle bounds)
			implements GuiElementRenderState {
		@Override
		public void buildVertices(VertexConsumer vc) {
			int[] d = rects;
			for (int o = 0; o < d.length; o += 6) {
				float x0 = d[o], y0 = d[o + 1], x1 = d[o + 2], y1 = d[o + 3];
				// The same corners in the same order as the game's own rectangles.
				vc.addVertexWith2DPose(pose, x0, y0).setColor(d[o + 4]);
				vc.addVertexWith2DPose(pose, x0, y1).setColor(d[o + 5]);
				vc.addVertexWith2DPose(pose, x1, y1).setColor(d[o + 5]);
				vc.addVertexWith2DPose(pose, x1, y0).setColor(d[o + 4]);
			}
		}

		@Override
		public RenderPipeline pipeline() {
			return RenderPipelines.GUI;
		}

		@Override
		public TextureSetup textureSetup() {
			return TextureSetup.noTexture();
		}
	}
}
