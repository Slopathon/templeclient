package nz.temple.client.render;

import nz.temple.client.compat.Gpu;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import nz.temple.TempleNz;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;

public final class GlassRenderer {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/glass");
	public static final int MAX_GLASS = 128;
	public static final int MAX_UI = 672;
	private static final int UI_BLOCK = MAX_UI / 2;
	private static final int UI_BLOCK_BYTES = UI_BLOCK * 3 * 16;
	private static final int CONFIG_BYTES = 432;
	private static final int GLASS_BYTES = MAX_GLASS * 2 * 16;
	private static final int UI_BYTES = UI_BLOCK_BYTES * 2;
	private static final Vector4f CLEAR = new Vector4f(0, 0, 0, 0);
	public record Params(float bend, float bendSize, float flowSpeed, float softness, float stretch, float edgeWidth, float colorSplit, float dim,
			int tint, float tintAmount, float shine, float saturation, float cursorLight, float brightness, float roundness) {
		public static final Params DEFAULTS = new Params(100, 20, 100, 8, 50, 44, 30, 0, 0xFFE9B949, 0, 100, 110, 30, 0, 100);
	}
	private final Supplier<Params> params;
	private final Runnable onFailure;
	private final float[] glass = new float[MAX_GLASS * 8];
	private final float[] lens = new float[MAX_GLASS * 8];
	private final float[] ui = new float[MAX_UI * 16];
	private final int[] uiColor = new int[MAX_UI];
	private final Stacking stacking = new Stacking();
	private final float[] overlayGlass = new float[MAX_GLASS * 8];
	private final float[] overlayUi = new float[MAX_UI * 16];
	private final int[] overlayUiColor = new int[MAX_UI];
	private final Stacking overlayStacking = new Stacking();
	private final ByteBuffer configBytes = direct(CONFIG_BYTES);
	private final ByteBuffer lensConfigBytes = direct(CONFIG_BYTES);
	private final ByteBuffer glassBytes = direct(GLASS_BYTES);
	private final ByteBuffer lensBytes = direct(GLASS_BYTES);
	private final ByteBuffer uiBytes = direct(UI_BYTES);
	private final Random random = new Random();
	private int glassCount;
	private int lensCount;
	private int uiCount;
	private int overlayGlassCount;
	private int overlayUiCount;
	private boolean placedOverlay;
	private boolean overlayHasContent;
	private RenderPipeline pipeline;
	private RenderPipeline lensPipeline;
	private GpuTexture layer;
	private GpuTextureView layerView;
	private GpuTexture mid;
	private GpuTextureView midView;
	private GpuTexture overlay;
	private GpuTextureView overlayView;
	private int layerWidth;
	private int layerHeight;
	private GpuBuffer configBuffer;
	private GpuBuffer lensConfigBuffer;
	private GpuBuffer glassBuffer;
	private GpuBuffer lensBuffer;
	private GpuBuffer uiBuffer;
	private GpuBuffer overlayConfigBuffer;
	private GpuBuffer overlayGlassBuffer;
	private GpuBuffer overlayUiBuffer;
	private final ByteBuffer overlayConfigBytes = direct(CONFIG_BYTES);
	private final ByteBuffer overlayGlassBytes = direct(GLASS_BYTES);
	private final ByteBuffer overlayUiBytes = direct(UI_BYTES);
	private boolean placedThisFrame;
	private boolean layerHasContent;
	private boolean failed;
	private double flowTime;
	private float seed;
	private long lastNanos = System.nanoTime();
	public GlassRenderer(Supplier<Params> params, Runnable onFailure) {
		this.params = params;
		this.onFailure = onFailure;
		reseed();
	}
	private static ByteBuffer direct(int bytes) {
		return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
	}

	public boolean available() {
		return !failed;
	}
	public boolean hasRoom(int panels, int shapes) {
		return !failed && glassCount + panels <= MAX_GLASS && uiCount + shapes <= MAX_UI;
	}
	public boolean hasLensRoom(int lenses) {
		return !failed && lensCount + lenses <= MAX_GLASS;
	}
	public boolean placedThisFrame() {
		return placedThisFrame;
	}
	public void reseed() {
		seed = random.nextFloat() * 500f;
	}
	public void placeLayer(GuiGraphicsExtractor g) {
		if (failed || placedThisFrame || cleanPass()) {
			return;
		}
		try {
			Minecraft mc = Minecraft.getInstance();
			RenderTarget main = mc.gameRenderer.mainRenderTarget();
			ensureLayer(main.width, main.height);
			Window win = mc.getWindow();
			int guiW = win.getGuiScaledWidth();
			int guiH = win.getGuiScaledHeight();
			int scale = win.getGuiScale();
			float u1 = (float) (guiW * scale) / layerWidth;
			float v1 = 1f - (float) (guiH * scale) / layerHeight;
			Gpu.blit(g, layerView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST), 0, 0, guiW, guiH, 0f, u1, 1f, v1);
			placedThisFrame = true;
		} catch (RuntimeException e) {
			fail(e);
		}
	}
	/** Colours of the HUD's glass (see {@link #hudGlass}): tint at the top and bottom, the rim's accent and the shade amount. */
	private static final float[] HUD_GLASS = new float[12];

	public static void hudGlassColours(int tintTop, int tintBottom, int accent, float shade) {
		put(HUD_GLASS, 0, tintTop);
		put(HUD_GLASS, 4, tintBottom);
		put(HUD_GLASS, 8, accent);
		HUD_GLASS[11] = shade;
	}

	private static void put(float[] into, int o, int argb) {
		into[o] = ((argb >> 16) & 0xFF) / 255f;
		into[o + 1] = ((argb >> 8) & 0xFF) / 255f;
		into[o + 2] = (argb & 0xFF) / 255f;
		into[o + 3] = (argb >>> 24) / 255f;
	}

	/**
	 * A HUD glass panel: a refracting bevel, a light frost, a tint running from the top colour to the bottom one and a rim lit by
	 * the scene behind it (x.monolith's glass). Uses the colours from {@link #hudGlassColours}.
	 */
	public void hudGlass(float x, float y, float w, float h, float radius, float opacity) {
		int before = glassCount;
		glass(x, y, w, h, radius, opacity);
		if (glassCount > before) {
			glass[before * 8 + 6] = -1;
		}
	}

	public void glass(float x, float y, float w, float h, float radius, float opacity) {
		if (!cleanPass() && glassCount < MAX_GLASS && putShape(glass, glassCount, x, y, w, h, radius, opacity, 0)) {
			int o = glassCount * 8;
			int layer = stacking.pane(glass[o] - glass[o + 2], glass[o + 1] - glass[o + 3], glass[o] + glass[o + 2], glass[o + 1] + glass[o + 3]);
			glass[o + 7] = layer;
			if (layer > 0) {
				dropLensesUnder(glass[o] - glass[o + 2], glass[o + 1] - glass[o + 3], glass[o] + glass[o + 2], glass[o + 1] + glass[o + 3]);
			}
			glassCount++;
		}
	}
	private void dropLensesUnder(float x0, float y0, float x1, float y1) {
		int kept = 0;
		for (int i = 0; i < lensCount; i++) {
			int o = i * 8;
			boolean covered = lens[o] - lens[o + 2] < x1 && x0 < lens[o] + lens[o + 2]
					&& lens[o + 1] - lens[o + 3] < y1 && y0 < lens[o + 1] + lens[o + 3];
			if (!covered) {
				if (kept != i) {
					System.arraycopy(lens, o, lens, kept * 8, 8);
				}
				kept++;
			}
		}
		lensCount = kept;
	}
	public void lens(float x, float y, float w, float h, float radius, float opacity, float boost) {
		if (!cleanPass() && lensCount < MAX_GLASS && putShape(lens, lensCount, x, y, w, h, radius, opacity, boost)) {
			lensCount++;
		}
	}
	public void lensRing(float x, float y, float w, float h, float radius, float opacity, float boost, float ringWidth) {
		if (!cleanPass() && lensCount < MAX_GLASS && putShape(lens, lensCount, x, y, w, h, radius, opacity, boost)) {
			lens[lensCount * 8 + 7] = Math.max(0.5f, ringWidth) * scale();
			lensCount++;
		}
	}
	public void overlayGlass(float x, float y, float w, float h, float radius, float opacity) {
		if (!cleanPass() && overlayGlassCount < MAX_GLASS && putShape(overlayGlass, overlayGlassCount, x, y, w, h, radius, opacity, 0)) {
			int o = overlayGlassCount * 8;
			overlayGlass[o + 7] = overlayStacking.pane(overlayGlass[o] - overlayGlass[o + 2], overlayGlass[o + 1] - overlayGlass[o + 3],
					overlayGlass[o] + overlayGlass[o + 2], overlayGlass[o + 1] + overlayGlass[o + 3]);
			overlayGlassCount++;
		}
	}
	public void overlayRect(float x, float y, float w, float h, float radius, int argb, float ring, float softness) {
		if (cleanPass() || overlayUiCount >= MAX_UI || w <= 0 || h <= 0 || (argb >>> 24) == 0) {
			return;
		}
		putUi(overlayUi, overlayUiColor, overlayStacking, overlayUiCount, x, y, w, h, radius, argb, ring, softness, null, false);
		overlayUiCount++;
	}

	/** An overlay shape whose colour runs from {@code argb} to {@code argb2} (mode 1 vertical, 2 horizontal, 3 diagonal). */
	public void overlayGradient(float x, float y, float w, float h, float radius, int argb, int argb2, int mode, float ring) {
		if (cleanPass() || overlayUiCount >= MAX_UI || w <= 0 || h <= 0 || ((argb | argb2) >>> 24) == 0) {
			return;
		}
		putUi(overlayUi, overlayUiColor, overlayStacking, overlayUiCount, x, y, w, h, radius, argb, ring, 0, null, false);
		gradient(overlayUi, overlayUiCount, argb2, mode);
		overlayUiCount++;
	}
	public boolean hasOverlayRoom(int panels, int shapes) {
		return !failed && overlayGlassCount + panels <= MAX_GLASS && overlayUiCount + shapes <= MAX_UI;
	}
	public void placeOverlayLayer(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
		if (failed || overlayView == null || cleanPass()) {
			return;
		}
		try {
			Minecraft mc = Minecraft.getInstance();
			Window win = mc.getWindow();
			int guiW = win.getGuiScaledWidth();
			int guiH = win.getGuiScaledHeight();
			int scale = win.getGuiScale();
			float u1 = (float) (guiW * scale) / layerWidth;
			float v1 = 1f - (float) (guiH * scale) / layerHeight;
			int pad = 8;
			int ax = Math.max(0, x0 - pad);
			int ay = Math.max(0, y0 - pad);
			int bx = Math.min(guiW, x1 + pad);
			int by = Math.min(guiH, y1 + pad);
			if (bx <= ax || by <= ay) {
				return;
			}
			Gpu.blit(g, overlayView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST), ax, ay, bx, by,
					u1 * ax / guiW, u1 * bx / guiW, 1f + (v1 - 1f) * ay / guiH, 1f + (v1 - 1f) * by / guiH);
			placedOverlay = true;
		} catch (RuntimeException e) {
			fail(e);
		}
	}
	private boolean putShape(float[] into, int index, float x, float y, float w, float h, float radius, float opacity, float boost) {
		if (w <= 0 || h <= 0 || opacity <= 0.01f) {
			return false;
		}
		float s = scale();
		int o = index * 8;
		into[o] = (x + w / 2) * s;
		into[o + 1] = (y + h / 2) * s;
		into[o + 2] = w / 2 * s;
		into[o + 3] = h / 2 * s;
		into[o + 4] = radius * s;
		into[o + 5] = Math.min(opacity, 1f);
		into[o + 6] = Math.max(0, Math.min(1, boost));
		into[o + 7] = 0;
		return true;
	}
	public void rect(float x, float y, float w, float h, float radius, int argb, float ring, float softness, float[] clip) {
		if (cleanPass() || uiCount >= MAX_UI || w <= 0 || h <= 0 || (argb >>> 24) == 0) {
			return;
		}
		putUi(ui, uiColor, stacking, uiCount, x, y, w, h, radius, argb, ring, softness, clip, false);
		uiCount++;
	}
	/** A shape whose colour runs from {@code argb} to {@code argb2} (mode 1 vertical, 2 horizontal, 3 diagonal). */
	public void gradientRect(float x, float y, float w, float h, float radius, int argb, int argb2, int mode, float ring,
			float softness, float[] clip) {
		if (cleanPass() || uiCount >= MAX_UI || w <= 0 || h <= 0 || ((argb | argb2) >>> 24) == 0) {
			return;
		}
		putUi(ui, uiColor, stacking, uiCount, x, y, w, h, radius, argb, ring, softness, clip, false);
		gradient(ui, uiCount, argb2, mode);
		uiCount++;
	}

	/** Stores a gradient's end colour in the shape's spare floats (8-10), as 16-bit halves so the bits survive copying. */
	private static void gradient(float[] into, int index, int argb2, int mode) {
		int o = index * 16;
		into[o + 8] = mode & 3;
		into[o + 9] = (argb2 >>> 16) & 0xFFFF;
		into[o + 10] = argb2 & 0xFFFF;
	}

	public void sheet(float x, float y, float w, float h, int argb) {
		if (cleanPass() || uiCount >= MAX_UI || w <= 0 || h <= 0 || (argb >>> 24) == 0) {
			return;
		}
		putUi(ui, uiColor, stacking, uiCount, x, y, w, h, 0, argb, 0, 0, null, true);
		uiCount++;
	}

	private static final int LAYER_SLOTS = Stacking.MAX_LAYER + 1;
	private final float[] sortedGlass = new float[MAX_GLASS * 8];
	private final float[] sortedUi = new float[MAX_UI * 16];
	private final int[] sortedColor = new int[MAX_UI];
	private final int[] layerRange = new int[LAYER_SLOTS * 4];
	private final float[] layerBox = new float[LAYER_SLOTS * 4];
	private void sortByLayer(float[] glassIn, int glassN, float[] uiIn, int[] colorIn, int uiN, int layers, float scale) {
		int g = 0;
		int u = 0;
		float margin = 14 * scale;
		for (int l = 0; l < LAYER_SLOTS; l++) {
			float x0 = Float.MAX_VALUE;
			float y0 = Float.MAX_VALUE;
			float x1 = -Float.MAX_VALUE;
			float y1 = -Float.MAX_VALUE;
			layerRange[l * 4] = g;
			for (int i = 0; i < glassN && l <= layers; i++) {
				int o = i * 8;
				if ((int) glassIn[o + 7] != l) {
					continue;
				}
				System.arraycopy(glassIn, o, sortedGlass, g * 8, 8);
				g++;
				x0 = Math.min(x0, glassIn[o] - glassIn[o + 2] - margin);
				y0 = Math.min(y0, glassIn[o + 1] - glassIn[o + 3] - margin);
				x1 = Math.max(x1, glassIn[o] + glassIn[o + 2] + margin);
				y1 = Math.max(y1, glassIn[o + 1] + glassIn[o + 3] + margin);
			}
			layerRange[l * 4 + 1] = g;
			layerRange[l * 4 + 2] = u;
			for (int i = 0; i < uiN && l <= layers; i++) {
				int o = i * 16;
				if ((int) uiIn[o + 7] != l) {
					continue;
				}
				System.arraycopy(uiIn, o, sortedUi, u * 16, 16);
				sortedColor[u] = colorIn[i];
				u++;
				float reach = uiIn[o + 6] + 3;
				x0 = Math.min(x0, uiIn[o] - uiIn[o + 2] - reach);
				y0 = Math.min(y0, uiIn[o + 1] - uiIn[o + 3] - reach);
				x1 = Math.max(x1, uiIn[o] + uiIn[o + 2] + reach);
				y1 = Math.max(y1, uiIn[o + 1] + uiIn[o + 3] + reach);
			}
			layerRange[l * 4 + 3] = u;
			setLayerBox(l, x0, y0, x1, y1);
		}
	}
	private void lensLayers(int lenses) {
		for (int l = 0; l < LAYER_SLOTS; l++) {
			layerRange[l * 4] = 0;
			layerRange[l * 4 + 1] = l == 0 ? lenses : 0;
			layerRange[l * 4 + 2] = 0;
			layerRange[l * 4 + 3] = 0;
			if (l == 0) {
				setLayerBox(0, -Float.MAX_VALUE / 4, -Float.MAX_VALUE / 4, Float.MAX_VALUE / 4, Float.MAX_VALUE / 4);
			} else {
				setLayerBox(l, 1, 1, -1, -1);
			}
		}
	}
	private void setLayerBox(int l, float x0, float y0, float x1, float y1) {
		boolean empty = x1 < x0 || y1 < y0;
		layerBox[l * 4] = empty ? 1 : x0;
		layerBox[l * 4 + 1] = empty ? 1 : y0;
		layerBox[l * 4 + 2] = empty ? -1 : x1;
		layerBox[l * 4 + 3] = empty ? -1 : y1;
	}
	private void putUi(float[] into, int[] colors, Stacking stack, int index, float x, float y, float w, float h, float radius, int argb,
			float ring, float softness, float[] clip, boolean sheet) {
		float s = scale();
		int o = index * 16;
		into[o] = (x + w / 2) * s;
		into[o + 1] = (y + h / 2) * s;
		into[o + 2] = w / 2 * s;
		into[o + 3] = h / 2 * s;
		into[o + 4] = radius * s;
		into[o + 5] = ring * s;
		into[o + 6] = softness * s;
		into[o + 8] = 0;
		into[o + 9] = 0;
		into[o + 10] = 0;
		colors[index] = argb;
		float reach = softness * s;
		float x0 = into[o] - into[o + 2] - reach;
		float y0 = into[o + 1] - into[o + 3] - reach;
		float x1 = into[o] + into[o + 2] + reach;
		float y1 = into[o + 1] + into[o + 3] + reach;
		if (clip == null) {
			into[o + 12] = 0;
			into[o + 13] = 0;
			into[o + 14] = -1;
			into[o + 15] = -1;
		} else {
			into[o + 12] = clip[0] * s;
			into[o + 13] = clip[1] * s;
			into[o + 14] = clip[2] * s;
			into[o + 15] = clip[3] * s;
			x0 = Math.max(x0, into[o + 12]);
			y0 = Math.max(y0, into[o + 13]);
			x1 = Math.min(x1, into[o + 14]);
			y1 = Math.min(y1, into[o + 15]);
		}
		into[o + 7] = sheet ? stack.pane(x0, y0, x1, y1) : stack.shape(x0, y0, x1, y1);
	}
	private static final class Stacking {
		static final int MAX_LAYER = 7;
		private final float[] boxes = new float[(MAX_GLASS + MAX_UI) * 4];
		private final int[] layers = new int[MAX_GLASS + MAX_UI];
		private int count;
		private int top;
		void reset() {
			count = 0;
			top = 0;
		}
		int top() {
			return top;
		}
		int pane(float x0, float y0, float x1, float y1) {
			return add(x0, y0, x1, y1, Math.min(MAX_LAYER, under(x0, y0, x1, y1) + 1));
		}
		int shape(float x0, float y0, float x1, float y1) {
			return add(x0, y0, x1, y1, Math.max(0, under(x0, y0, x1, y1)));
		}
		private int under(float x0, float y0, float x1, float y1) {
			int best = -1;
			for (int i = 0; i < count; i++) {
				int o = i * 4;
				if (x0 < boxes[o + 2] && boxes[o] < x1 && y0 < boxes[o + 3] && boxes[o + 1] < y1 && layers[i] > best) {
					best = layers[i];
				}
			}
			return best;
		}
		private int add(float x0, float y0, float x1, float y1, int layer) {
			if (count < layers.length && x1 > x0 && y1 > y0) {
				int o = count * 4;
				boxes[o] = x0;
				boxes[o + 1] = y0;
				boxes[o + 2] = x1;
				boxes[o + 3] = y1;
				layers[count++] = layer;
			}
			top = Math.max(top, layer);
			return layer;
		}
	}

	public float roundness() {
		return params.get().roundness() / 100f;
	}

	public void render() {
		boolean placed = placedThisFrame;
		placedThisFrame = false;
		long now = System.nanoTime();
		double dt = Math.min((now - lastNanos) / 1e9, 0.5);
		lastNanos = now;
		Params p = params.get();
		flowTime += dt * p.flowSpeed() / 100.0 * 0.3;
		try {
			if (failed || layer == null) {
				return;
			}
			boolean overlayWanted = placedOverlay && (overlayGlassCount > 0 || overlayUiCount > 0);
			if (overlayWanted) {
				drawOverlay(p);
				overlayHasContent = true;
			} else if (overlay != null && overlayHasContent) {
				Gpu.clear(RenderSystem.getDevice().createCommandEncoder(), overlay, CLEAR);
				overlayHasContent = false;
			}
			if (!placed || (glassCount == 0 && uiCount == 0 && lensCount == 0)) {
				if (layerHasContent) {
					Gpu.clear(RenderSystem.getDevice().createCommandEncoder(), layer, CLEAR);
					layerHasContent = false;
				}
				return;
			}
			draw(p);
			layerHasContent = true;
		} catch (RuntimeException e) {
			fail(e);
		} finally {
			glassCount = 0;
			lensCount = 0;
			uiCount = 0;
			overlayGlassCount = 0;
			overlayUiCount = 0;
			stacking.reset();
			overlayStacking.reset();
			placedOverlay = false;
		}
	}
	private void draw(Params p) {
		GpuDevice device = RenderSystem.getDevice();
		if (pipeline == null) {
			pipeline = buildPipeline(false);
			lensPipeline = buildPipeline(true);
		}
		if (configBuffer == null) {
			int usage = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
			configBuffer = device.createBuffer(() -> "Temple.nz glass config", usage, CONFIG_BYTES);
			lensConfigBuffer = device.createBuffer(() -> "Temple.nz lens config", usage, CONFIG_BYTES);
			glassBuffer = device.createBuffer(() -> "Temple.nz glass shapes", usage, GLASS_BYTES);
			lensBuffer = device.createBuffer(() -> "Temple.nz lens shapes", usage, GLASS_BYTES);
			uiBuffer = device.createBuffer(() -> "Temple.nz ui shapes", usage, UI_BYTES);
		}
		Minecraft mc = Minecraft.getInstance();
		Window win = mc.getWindow();
		float s = win.getGuiScale();
		float mouseX = (float) mc.mouseHandler.getScaledXPos(win) * s;
		float mouseY = (float) mc.mouseHandler.getScaledYPos(win) * s;
		boolean lenses = lensCount > 0;
		CommandEncoder encoder = device.createCommandEncoder();
		sortByLayer(glass, glassCount, ui, uiColor, uiCount, stacking.top(), s);
		writeConfig(configBytes, p, p.bend() * 0.6f, p.softness(), glassCount, uiCount, mouseX, mouseY, s, bounds(glass, glassCount, s), stacking.top());
		writeShapes(glassBytes, sortedGlass);
		writeUi(uiBytes, sortedUi, sortedColor);
		encoder.writeToBuffer(configBuffer.slice(), configBytes);
		encoder.writeToBuffer(glassBuffer.slice(), glassBytes);
		encoder.writeToBuffer(uiBuffer.slice(), uiBytes);
		if (lenses) {
			lensLayers(lensCount);
			writeConfig(lensConfigBytes, p, 0f, 0f, lensCount, 0, mouseX, mouseY, s, bounds(lens, lensCount, s), 0);
			writeShapes(lensBytes, lens);
			encoder.writeToBuffer(lensConfigBuffer.slice(), lensConfigBytes);
			encoder.writeToBuffer(lensBuffer.slice(), lensBytes);
		}
		GpuTextureView backdrop = mc.gameRenderer.mainRenderTarget().getColorTextureView();
		GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		beginRegions(s);
		addRegions(glass, glassCount, 8, 14 * s);
		addRegions(ui, uiCount, 16, -1);
		addRegions(lens, lensCount, 8, 14 * s);
		try (RenderPass pass = Gpu.openPass(encoder, () -> "Temple.nz glass", lenses ? midView : layerView, Optional.of(CLEAR))) {
			pass.setPipeline(nz.temple.client.compat.Gpu.pipeline(pipeline));
			RenderSystem.bindDefaultUniforms(pass);
			pass.bindTexture("BackdropSampler", backdrop, linear);
			pass.setUniform("GlassConfig", configBuffer);
			pass.setUniform("GlassShapes", glassBuffer);
			bindUi(pass, uiBuffer);
			drawRegions(pass);
		}
		if (lenses) {
			try (RenderPass pass = Gpu.openPass(encoder, () -> "Temple.nz glass lenses", layerView, Optional.of(CLEAR))) {
				pass.setPipeline(nz.temple.client.compat.Gpu.pipeline(lensPipeline));
				RenderSystem.bindDefaultUniforms(pass);
				pass.bindTexture("BackdropSampler", backdrop, linear);
				pass.bindTexture("LayerSampler", midView, linear);
				pass.setUniform("GlassConfig", lensConfigBuffer);
				pass.setUniform("GlassShapes", lensBuffer);
				bindUi(pass, uiBuffer);
				drawRegions(pass);
			}
		}
	}
	private void drawOverlay(Params p) {
		GpuDevice device = RenderSystem.getDevice();
		if (pipeline == null) {
			pipeline = buildPipeline(false);
			lensPipeline = buildPipeline(true);
		}
		if (overlayConfigBuffer == null) {
			int usage = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
			overlayConfigBuffer = device.createBuffer(() -> "Temple.nz overlay config", usage, CONFIG_BYTES);
			overlayGlassBuffer = device.createBuffer(() -> "Temple.nz overlay shapes", usage, GLASS_BYTES);
			overlayUiBuffer = device.createBuffer(() -> "Temple.nz overlay ui", usage, UI_BYTES);
		}
		Minecraft mc = Minecraft.getInstance();
		Window win = mc.getWindow();
		float s = win.getGuiScale();
		float mouseX = (float) mc.mouseHandler.getScaledXPos(win) * s;
		float mouseY = (float) mc.mouseHandler.getScaledYPos(win) * s;
		CommandEncoder encoder = device.createCommandEncoder();
		sortByLayer(overlayGlass, overlayGlassCount, overlayUi, overlayUiColor, overlayUiCount, overlayStacking.top(), s);
		writeConfig(overlayConfigBytes, p, p.bend() * 0.6f, p.softness(), overlayGlassCount, overlayUiCount, mouseX, mouseY, s,
				bounds(overlayGlass, overlayGlassCount, s), overlayStacking.top());
		writeShapes(overlayGlassBytes, sortedGlass);
		writeUi(overlayUiBytes, sortedUi, sortedColor);
		encoder.writeToBuffer(overlayConfigBuffer.slice(), overlayConfigBytes);
		encoder.writeToBuffer(overlayGlassBuffer.slice(), overlayGlassBytes);
		encoder.writeToBuffer(overlayUiBuffer.slice(), overlayUiBytes);
		GpuTextureView backdrop = mc.gameRenderer.mainRenderTarget().getColorTextureView();
		GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		beginRegions(s);
		addRegions(overlayGlass, overlayGlassCount, 8, 14 * s);
		addRegions(overlayUi, overlayUiCount, 16, -1);
		try (RenderPass pass = Gpu.openPass(encoder, () -> "Temple.nz glass overlay", overlayView, Optional.of(CLEAR))) {
			pass.setPipeline(nz.temple.client.compat.Gpu.pipeline(pipeline));
			RenderSystem.bindDefaultUniforms(pass);
			pass.bindTexture("BackdropSampler", backdrop, linear);
			pass.setUniform("GlassConfig", overlayConfigBuffer);
			pass.setUniform("GlassShapes", overlayGlassBuffer);
			bindUi(pass, overlayUiBuffer);
			drawRegions(pass);
		}
	}
	private static final int MAX_REGIONS = 12;
	private final float[] regions = new float[MAX_REGIONS * 4];
	private int regionCount;
	private float regionPad;
	private void beginRegions(float scale) {
		regionCount = 0;
		regionPad = 6 * scale;
	}
	private void addRegions(float[] shapes, int count, int stride, float margin) {
		for (int i = 0; i < count; i++) {
			int o = i * stride;
			float reach = margin >= 0 ? margin : shapes[o + 6] + 3;
			addRegion(shapes[o] - shapes[o + 2] - reach, shapes[o + 1] - shapes[o + 3] - reach,
					shapes[o] + shapes[o + 2] + reach, shapes[o + 1] + shapes[o + 3] + reach);
		}
	}
	private void addRegion(float x0, float y0, float x1, float y1) {
		boolean merged = true;
		while (merged) {
			merged = false;
			for (int r = 0; r < regionCount; r++) {
				int o = r * 4;
				if (x0 <= regions[o + 2] + regionPad && regions[o] <= x1 + regionPad
						&& y0 <= regions[o + 3] + regionPad && regions[o + 1] <= y1 + regionPad) {
					x0 = Math.min(x0, regions[o]);
					y0 = Math.min(y0, regions[o + 1]);
					x1 = Math.max(x1, regions[o + 2]);
					y1 = Math.max(y1, regions[o + 3]);
					removeRegion(r);
					merged = true;
					break;
				}
			}
		}
		if (regionCount == MAX_REGIONS) {
			for (int r = 0; r < regionCount; r++) {
				int o = r * 4;
				x0 = Math.min(x0, regions[o]);
				y0 = Math.min(y0, regions[o + 1]);
				x1 = Math.max(x1, regions[o + 2]);
				y1 = Math.max(y1, regions[o + 3]);
			}
			regionCount = 0;
		}
		int o = regionCount * 4;
		regions[o] = x0;
		regions[o + 1] = y0;
		regions[o + 2] = x1;
		regions[o + 3] = y1;
		regionCount++;
	}
	private void removeRegion(int r) {
		int last = (regionCount - 1) * 4;
		System.arraycopy(regions, last, regions, r * 4, 4);
		regionCount--;
	}
	private void drawRegions(RenderPass pass) {
		for (int r = 0; r < regionCount; r++) {
			int o = r * 4;
			int x0 = Math.max(0, (int) Math.floor(regions[o]));
			int y0 = Math.max(0, (int) Math.floor(regions[o + 1]));
			int x1 = Math.min(layerWidth, (int) Math.ceil(regions[o + 2]));
			int y1 = Math.min(layerHeight, (int) Math.ceil(regions[o + 3]));
			if (x1 <= x0 || y1 <= y0) {
				continue;
			}
			pass.enableScissor(x0, layerHeight - y1, x1 - x0, y1 - y0);
			Gpu.drawFullscreen(pass);
		}
		pass.disableScissor();
	}
	private float[] bounds(float[] shapes, int count, float scale) {
		if (count <= 0) {
			return new float[] {1, 1, -1, -1};
		}
		float margin = 14 * scale;
		float x0 = Float.MAX_VALUE;
		float y0 = Float.MAX_VALUE;
		float x1 = -Float.MAX_VALUE;
		float y1 = -Float.MAX_VALUE;
		for (int i = 0; i < count; i++) {
			int o = i * 8;
			x0 = Math.min(x0, shapes[o] - shapes[o + 2]);
			y0 = Math.min(y0, shapes[o + 1] - shapes[o + 3]);
			x1 = Math.max(x1, shapes[o] + shapes[o + 2]);
			y1 = Math.max(y1, shapes[o + 1] + shapes[o + 3]);
		}
		return new float[] {x0 - margin, y0 - margin, x1 + margin, y1 + margin};
	}
	private void writeConfig(ByteBuffer bytes, Params p, float warp, float frost, int shapeCount, int uiShapeCount, float mouseX, float mouseY, float scale, float[] box, int layers) {
		bytes.clear();
		Std140Builder b = Std140Builder.intoBuffer(bytes);
		b.putVec2(layerWidth, layerHeight)
				.putVec2(mouseX, mouseY)
				.putFloat(scale)
				.putFloat((float) flowTime)
				.putFloat(seed)
				.putFloat(p.stretch() / 100f)
				.putFloat(p.edgeWidth())
				.putFloat(frost)
				.putFloat(p.dim() / 100f)
				.putFloat(p.colorSplit() / 100f)
				.putFloat(warp)
				.putFloat(p.bendSize())
				.putInt(shapeCount)
				.putInt(uiShapeCount)
				.putVec4(box[0], box[1], box[2], box[3])
				.putVec4(((p.tint() >> 16) & 0xFF) / 255f, ((p.tint() >> 8) & 0xFF) / 255f, (p.tint() & 0xFF) / 255f, p.tintAmount() / 100f)
				.putFloat(p.shine() / 100f)
				.putFloat(p.saturation() / 100f)
				.putFloat(p.cursorLight() / 100f)
				.putFloat(p.brightness() / 100f)
				.putInt(layers)
				.align(16);
		for (int l = 0; l < LAYER_SLOTS; l++) {
			b.putIVec4(layerRange[l * 4], layerRange[l * 4 + 1], layerRange[l * 4 + 2], layerRange[l * 4 + 3]);
		}
		for (int l = 0; l < LAYER_SLOTS; l++) {
			b.putVec4(layerBox[l * 4], layerBox[l * 4 + 1], layerBox[l * 4 + 2], layerBox[l * 4 + 3]);
		}
		b.putVec4(HUD_GLASS[0], HUD_GLASS[1], HUD_GLASS[2], HUD_GLASS[3]);
		b.putVec4(HUD_GLASS[4], HUD_GLASS[5], HUD_GLASS[6], HUD_GLASS[7]);
		b.putVec4(HUD_GLASS[8], HUD_GLASS[9], HUD_GLASS[10], HUD_GLASS[11]);
		bytes.clear();
	}
	private static void writeShapes(ByteBuffer bytes, float[] shapes) {
		bytes.clear();
		Std140Builder b = Std140Builder.intoBuffer(bytes);
		for (int i = 0; i < MAX_GLASS; i++) {
			int o = i * 8;
			b.putVec4(shapes[o], shapes[o + 1], shapes[o + 2], shapes[o + 3]);
		}
		for (int i = 0; i < MAX_GLASS; i++) {
			int o = i * 8;
			b.putVec4(shapes[o + 4], shapes[o + 5], shapes[o + 6], shapes[o + 7]);
		}
		bytes.clear();
	}
	private static void bindUi(RenderPass pass, GpuBuffer buffer) {
		pass.setUniform("UiShapes", buffer.slice(0, UI_BLOCK_BYTES));
		pass.setUniform("UiShapesMore", buffer.slice(UI_BLOCK_BYTES, UI_BLOCK_BYTES));
	}
	private static void writeUi(ByteBuffer bytes, float[] shapes, int[] colors) {
		bytes.clear();
		Std140Builder b = Std140Builder.intoBuffer(bytes);
		for (int start = 0; start < MAX_UI; start += UI_BLOCK) {
			for (int block = 0; block < 2; block++) {
				for (int i = start; i < start + UI_BLOCK; i++) {
					int o = i * 16 + block * 4;
					b.putVec4(shapes[o], shapes[o + 1], shapes[o + 2], shapes[o + 3]);
				}
			}
			for (int i = start; i < start + UI_BLOCK; i++) {
				int o = i * 16;
				boolean clipped = shapes[o + 14] >= shapes[o + 12];
				int mode = (int) shapes[o + 8] & 3;
				int gradient = 0;
				if (mode != 0) {
					int argb2 = (int) shapes[o + 9] << 16 | (int) shapes[o + 10];
					gradient = mode | ((argb2 >>> 26) & 63) << 2 | (argb2 & 0xFFFFFF) << 8;
				}
				// A clipped shape has a non-zero clip end (meta.z); meta.w carries the gradient.
				b.putIVec4(colors[i],
						clipped ? pack(Math.floor(shapes[o + 12]), Math.floor(shapes[o + 13])) : 0,
						clipped ? Math.max(1, pack(Math.ceil(shapes[o + 14]), Math.ceil(shapes[o + 15]))) : 0,
						gradient);
			}
		}
		bytes.clear();
	}
	private static int pack(double x, double y) {
		int px = (int) Math.max(0, Math.min(0xFFFF, x));
		int py = (int) Math.max(0, Math.min(0xFFFF, y));
		return px | py << 16;
	}
	private static RenderPipeline buildPipeline(boolean lensPass) {
		BindGroupLayout.Builder layout = BindGroupLayout.builder().withSampler("BackdropSampler");
		if (lensPass) {
			layout = layout.withSampler("LayerSampler");
		}
		layout = layout
				.withUniform("GlassConfig", UniformType.UNIFORM_BUFFER)
				.withUniform("GlassShapes", UniformType.UNIFORM_BUFFER)
				.withUniform("UiShapes", UniformType.UNIFORM_BUFFER)
				.withUniform("UiShapesMore", UniformType.UNIFORM_BUFFER);
		RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
				.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, lensPass ? "pipeline/glass_lens" : "pipeline/glass"))
				.withVertexShader("core/screenquad")
				.withFragmentShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "core/glass"))
				.withBindGroupLayout(layout.build())
				.withColorTargetState(ColorTargetState.DEFAULT)
				.withDepthStencilState(Optional.empty());
		if (lensPass) {
			builder = builder.withShaderDefine("LENS_PASS");
		}
		return builder.build();
	}
	private void ensureLayer(int width, int height) {
		if (layer != null && width == layerWidth && height == layerHeight) {
			return;
		}
		for (AutoCloseable c : new AutoCloseable[] {layerView, layer, midView, mid, overlayView, overlay}) {
			if (c != null) {
				try {
					c.close();
				} catch (Exception ignored) {
				}
			}
		}
		GpuDevice device = RenderSystem.getDevice();
		int usage = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST;
		layer = Gpu.createTexture(() -> "Temple.nz glass layer", usage, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
		layerView = device.createTextureView(layer);
		mid = Gpu.createTexture(() -> "Temple.nz glass base layer", usage, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
		midView = device.createTextureView(mid);
		overlay = Gpu.createTexture(() -> "Temple.nz glass overlay layer", usage, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
		overlayView = device.createTextureView(overlay);
		layerWidth = width;
		layerHeight = height;
		layerHasContent = true;
	}
	private static boolean cleanPass() {
		return nz.temple.client.render.streamproof.StreamproofRenderer.cleanPass();
	}
	private float scale() {
		return Minecraft.getInstance().getWindow().getGuiScale();
	}
	private void fail(RuntimeException e) {
		if (!failed) {
			failed = true;
			LOGGER.error("Glass rendering failed; falling back to plain panels", e);
			onFailure.run();
		}
	}
}
