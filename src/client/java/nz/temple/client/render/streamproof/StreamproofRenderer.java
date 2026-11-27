package nz.temple.client.render.streamproof;

import nz.temple.client.compat.Gpu;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.GuiBannerResultRenderer;
import net.minecraft.client.gui.render.pip.GuiBookModelRenderer;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.gui.render.pip.GuiProfilerChartRenderer;
import net.minecraft.client.gui.render.pip.GuiSkinRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import nz.temple.TempleNz;
import nz.temple.client.TempleNzClient;
import nz.temple.client.mixin.GameRendererTargetAccessor;
import nz.temple.client.mixin.GuiStateAccessor;
import nz.temple.client.modules.client.Streamproof;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Streamproof's frame. Each frame is drawn twice over, once as you see it and once as plain Minecraft:
 * <ul>
 * <li>World: every render pass that draws onto the game's frame is recorded and played back onto a second, clean
 * frame without Temple's draws (see {@link PassRecorder}), so vanilla drawing lands in both and Temple's only in
 * yours.</li>
 * <li>GUI: the HUD and screens are extracted a second time with everything of Temple's left out (its HUD, its menus,
 * its restyled widgets) and drawn onto the clean frame.</li>
 * </ul>
 * The full frame is read back and painted into {@link OverlayWindow}, which capture skips; the clean one is what the
 * game window presents, so it is all a recorder gets.
 */
public final class StreamproofRenderer {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/streamproof");
	private static final int SLOTS = 3;
	/** Whether the game has a borderless fullscreen to fall back to (from 26.1; before, fullscreen was always exclusive). */
	//? if >=26 {
	private static final boolean BORDERLESS = true;
	//?} else {
	/*private static final boolean BORDERLESS = false;
	*///?}
	private static final Set<Object> HIDDEN_WIDGETS = Collections.newSetFromMap(new WeakHashMap<>());

	/** Whether this frame is being drawn twice. Decided when the frame's GUI is extracted. */
	private static boolean frame;
	/** Extracting or drawing the clean GUI. */
	private static boolean cleanPass;
	/** Recording the world's passes for the clean frame. */
	private static boolean recording;
	/** Playing a pass back, or mirroring a clear or copy: nothing done then is recorded. */
	private static boolean replaying;
	private static boolean worldCaptured;
	private static boolean overlayShown;
	private static boolean warnedFullscreen;
	/** Exclusive fullscreen has been switched to borderless once this session (it is not fought over every frame). */
	private static boolean triedBorderless;

	private static TextureTarget clean;
	private static GuiRenderState cleanState;
	private static GuiRenderer cleanGui;
	private static OverlayWindow overlay;
	private static final Slot[] slots = new Slot[SLOTS];

	private StreamproofRenderer() {
	}

	public static boolean cleanPass() {
		return cleanPass;
	}

	/** Keeps a widget Temple adds to a vanilla screen (a button in the pause menu) off the recording. */
	public static <T> T hideFromRecording(T widget) {
		HIDDEN_WIDGETS.add(widget);
		return widget;
	}

	public static boolean hiddenWidget(Object widget) {
		return cleanPass && HIDDEN_WIDGETS.contains(widget);
	}

	/** Temple's own screens (the menus, the map, the editors) are left out of the clean frame entirely. */
	public static boolean hiddenScreen(Screen screen) {
		return cleanPass && screen != null && OURS.get(screen.getClass());
	}

	/**
	 * Whether a class ships in Temple's jar. Not by name: the release jar renames and flattens every class into the
	 * default package, so a {@code nz.temple.} prefix matched nothing there and the menus were drawn into both frames,
	 * every glass pane twice over.
	 */
	private static final ClassValue<Boolean> OURS = new ClassValue<>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			if (type.getName().startsWith("nz.temple.")) {
				return true;
			}
			String home = codeSource(TempleNzClient.class);
			return home != null && home.equals(codeSource(type));
		}
	};

	private static @Nullable String codeSource(Class<?> type) {
		var domain = type.getProtectionDomain();
		var source = domain == null ? null : domain.getCodeSource();
		return source == null || source.getLocation() == null ? null : source.getLocation().toString();
	}

	// ---- per frame -------------------------------------------------------------------------------------------------

	/**
	 * Wraps the GUI extraction: when the frame is to be drawn twice, the clean GUI is extracted first into its own
	 * state, then the real one as usual.
	 */
	public static void extract(net.minecraft.client.gui.Gui gui, Runnable extract) {
		frame = decide();
		worldCaptured = false;
		if (frame) {
			GuiStateAccessor accessor = (GuiStateAccessor) gui;
			GuiRenderState real = accessor.templenz$state();
			if (cleanState == null) {
				cleanState = new GuiRenderState();
			}
			accessor.templenz$setState(cleanState);
			cleanPass = true;
			try {
				extract.run();
			} catch (RuntimeException e) {
				fail("could not build the recording's HUD", e);
			} finally {
				cleanPass = false;
				accessor.templenz$setState(real);
			}
		}
		extract.run();
	}

	//? if <26 {
	/*// 1.21.11 has no single call that builds the GUI (GameRenderer.render draws the HUD, the screen and the toasts one by
	// one), so the clean GUI is built here, the way render builds the real one, leaving out what is Temple's.
	public static void extractClean(net.minecraft.client.DeltaTracker delta) {
		frame = decide();
		worldCaptured = false;
		if (!frame) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (cleanState == null) {
			cleanState = new GuiRenderState();
		}
		cleanState.reset();
		int mouseX = (int) mc.mouseHandler.getScaledXPos(mc.getWindow());
		int mouseY = (int) mc.mouseHandler.getScaledYPos(mc.getWindow());
		float partial = delta.getGameTimeDeltaTicks();
		cleanPass = true;
		try {
			var graphics = new net.minecraft.client.gui.GuiGraphics(mc, cleanState, mouseX, mouseY);
			if (mc.level != null) {
				mc.gui.render(graphics, delta);
			}
			if (mc.getOverlay() != null) {
				mc.getOverlay().render(graphics, mouseX, mouseY, partial);
			} else if (mc.screen != null && !hiddenScreen(mc.screen)) {
				mc.screen.renderWithTooltipAndSubtitles(graphics, mouseX, mouseY, partial);
			}
			mc.getToastManager().render(graphics);
		} catch (RuntimeException e) {
			fail("could not build the recording's HUD", e);
		} finally {
			cleanPass = false;
		}
	}
	*///?}

	private static boolean decide() {
		Minecraft mc = Minecraft.getInstance();
		if (!Streamproof.on()) {
			shutdown();
			return false;
		}
		if (overlay == null) {
			if (Util.getPlatform() != Util.OS.WINDOWS) {
				refuse("Streamproof only works on Windows");
				return false;
			}
			try {
				overlay = OverlayWindow.create(nz.temple.client.compat.Platform.hwnd(mc.getWindow()));
			} catch (RuntimeException | LinkageError e) {
				LOGGER.error("Streamproof could not start", e);
				refuse("Streamproof " + (e.getMessage() == null ? "could not start" : e.getMessage()));
				return false;
			}
		}
		// Vanilla's fullscreen is borderless unless Exclusive Fullscreen is on (Video Settings), and the overlay sits over a
		// borderless window like any other. Only exclusive fullscreen takes the screen from it.
		nz.temple.client.mixin.WindowFullscreenAccessor fullscreen = (nz.temple.client.mixin.WindowFullscreenAccessor) (Object) mc.getWindow();
		if (BORDERLESS && mc.options.fullscreen().get() && fullscreen.templenz$exclusiveFullscreen() && !triedBorderless) {
			// Exclusive fullscreen takes the screen from every other window, the overlay included, so you would see
			// only the plain frame. Go borderless instead (out of fullscreen and back in), which looks the same.
			triedBorderless = true;
			// Between frames, not in the middle of drawing one: the window is rebuilt at its new size.
			mc.execute(() -> {
				try {
					//? if >=26.3 {
					/*mc.getWindow().setExclusiveFullscreen(false);
					*///?} else {
					fullscreen.templenz$setExclusiveFullscreen(false);
					mc.getWindow().toggleFullScreen();
					mc.getWindow().toggleFullScreen();
					//?}
					notify("Streamproof went borderless", "Exclusive fullscreen hides the overlay you see your visuals in, so the "
							+ "game switched to borderless fullscreen.", false);
				} catch (RuntimeException e) {
					LOGGER.warn("Could not switch to borderless fullscreen", e);
				}
			});
		}
		if (mc.options.fullscreen().get() && fullscreen.templenz$exclusiveFullscreen()) {
			// The recording stays clean; you lose the visuals until exclusive fullscreen is off.
			if (overlayShown) {
				overlay.hide();
				overlayShown = false;
			}
			if (!warnedFullscreen) {
				warnedFullscreen = true;
				notify("Streamproof needs borderless", (BORDERLESS ? "Turn off Exclusive Fullscreen in Video Settings"
						: "Play in a window (F11)") + " to see your visuals again. Recorders still see plain Minecraft.", false);
			}
		} else {
			warnedFullscreen = false;
			try {
				overlay.follow();
			} catch (RuntimeException e) {
				// Never take the game down over the overlay: switch off and say so.
				fail("could not place the overlay", e);
				return false;
			}
			overlayShown = true;
		}
		return true;
	}

	/** The frame the game draws into: the game renderer's from 26.1, Minecraft's before. */
	private static RenderTarget mainTarget(GameRenderer renderer) {
		//? if >=26 {
		return renderer.mainRenderTarget();
		//?} else {
		/*return Minecraft.getInstance().getMainRenderTarget();
		*///?}
	}

	private static void setMainTarget(GameRendererTargetAccessor accessor, RenderTarget target) {
		//? if >=26 {
		accessor.templenz$setMainTarget(target);
		//?} else {
		/*((nz.temple.client.mixin.LegacyMinecraftTargetAccessor) Minecraft.getInstance()).templenz$setMainTarget(target);
		*///?}
	}

	/** Before the world is drawn: the clean frame starts as a copy of the freshly cleared one. */
	public static void beforeLevel(GameRenderer renderer) {
		if (!frame) {
			return;
		}
		RenderTarget main = mainTarget(renderer);
		ensureClean(main);
		copyColor(main, clean);
		Gpu.clearDepthFar(RenderSystem.getDevice().createCommandEncoder(), clean.getDepthTexture());
		worldCaptured = true;
		recording = true;
	}

	public static void afterLevel() {
		recording = false;
	}

	/** Before the GUI: with no world this frame (a menu), the clean frame is what has been drawn so far. */
	public static void beforeGui(GameRenderer renderer) {
		recording = false;
		if (!frame || worldCaptured) {
			return;
		}
		RenderTarget main = mainTarget(renderer);
		ensureClean(main);
		copyColor(main, clean);
	}

	/**
	 * After the real GUI: the finished frame goes to the overlay, the clean GUI is drawn onto the clean frame, and the
	 * clean frame replaces the real one so the game window presents it.
	 */
	public static void afterGui(GameRenderer renderer) {
		if (!frame || clean == null) {
			return;
		}
		RenderTarget main = mainTarget(renderer);
		if (overlayShown) {
			readBack(main.getColorTexture());
		}
		GameRendererTargetAccessor accessor = (GameRendererTargetAccessor) renderer;
		if (cleanGui == null) {
			Minecraft mc = Minecraft.getInstance();
			//? if >=26 {
			cleanGui = new GuiRenderer(cleanState, accessor.templenz$features(), List.of(
					new GuiEntityRenderer(mc.getEntityRenderDispatcher()),
					new GuiSkinRenderer(),
					new GuiBookModelRenderer(),
					new GuiBannerResultRenderer(mc.getAtlasManager()),
					new GuiProfilerChartRenderer()));
			cleanGui.registerPanoramaTextures(mc.getTextureManager());
			//?} else {
			/*// 1.21.11's GUI renderer draws through the game renderer's buffers and submit storage, as the game's own does.
			var buffers = accessor.templenz$renderBuffers().bufferSource();
			cleanGui = new GuiRenderer(cleanState, buffers, accessor.templenz$submitNodes(), accessor.templenz$features(), List.of(
					new GuiEntityRenderer(buffers, mc.getEntityRenderDispatcher()),
					new GuiSkinRenderer(buffers),
					new GuiBookModelRenderer(buffers),
					new GuiBannerResultRenderer(buffers, mc.getAtlasManager()),
					new net.minecraft.client.gui.render.pip.GuiSignRenderer(buffers, mc.getAtlasManager()),
					new GuiProfilerChartRenderer(buffers)));
			*///?}
		}
		// Everything that draws the GUI asks the game renderer for its main target; for the clean GUI that is the
		// clean frame.
		setMainTarget(accessor, clean);
		cleanPass = true;
		try {
			Gpu.clearDepthFar(RenderSystem.getDevice().createCommandEncoder(), clean.getDepthTexture());
			//? if >=26 {
			cleanGui.render();
			cleanGui.endFrame();
			//?} else {
			/*cleanGui.render(accessor.templenz$fog().getBuffer(net.minecraft.client.renderer.fog.FogRenderer.FogMode.NONE));
			cleanGui.incrementFrameNumber();
			*///?}
		} catch (RuntimeException e) {
			fail("could not draw the recording's HUD", e);
		} finally {
			cleanPass = false;
			setMainTarget(accessor, main);
		}
		copyColor(clean, main);
	}

	// ---- the clean frame ---------------------------------------------------------------------------------------------

	private static void ensureClean(RenderTarget main) {
		if (clean == null) {
			clean = nz.temple.client.compat.Gpu.textureTarget("Temple streamproof", main.width, main.height, true, GpuFormat.RGBA8_UNORM);
		} else if (clean.width != main.width || clean.height != main.height) {
			clean.resize(main.width, main.height);
		}
	}

	private static void copyColor(RenderTarget from, RenderTarget to) {
		replaying = true;
		try {
			RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(from.getColorTexture(), to.getColorTexture(), 0, 0, 0, 0, 0,
					Math.min(from.width, to.width), Math.min(from.height, to.height));
		} finally {
			replaying = false;
		}
	}

	/** The game frame's textures and the clean frame's stand-ins for them. */
	public record Targets(GpuTexture mainColor, GpuTexture mainDepth, TextureTarget clean) {
		GpuTextureView map(GpuTextureView view) {
			if (view.texture() == mainColor) {
				return clean.getColorTextureView();
			}
			if (view.texture() == mainDepth) {
				return clean.getDepthTextureView();
			}
			return view;
		}

		public GpuTexture map(GpuTexture texture) {
			if (texture == mainColor) {
				return clean.getColorTexture();
			}
			if (texture == mainDepth) {
				return clean.getDepthTexture();
			}
			return texture;
		}

		boolean main(GpuTexture texture) {
			return texture == mainColor || texture == mainDepth;
		}
	}

	private static @Nullable Targets targets() {
		if (!recording || replaying || clean == null) {
			return null;
		}
		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		return new Targets(main.getColorTexture(), main.getDepthTexture(), clean);
	}

	/** Mixin hook: a render pass was opened. Passes drawing onto the game's frame are recorded. */
	public static @Nullable PassRecorder onPassCreated(RenderPassDescriptor descriptor) {
		Targets targets = targets();
		if (targets == null) {
			return null;
		}
		var colors = descriptor.colorAttachments();
		boolean main = false;
		if (!colors.isEmpty()) {
			var first = colors.getFirst();
			main = first != null && first.textureView().texture() == targets.mainColor();
		} else if (descriptor.depthAttachment() != null) {
			main = descriptor.depthAttachment().textureView().texture() == targets.mainDepth();
		}
		return main ? new PassRecorder(descriptor) : null;
	}

	/** Mixin hook: a recorded pass closed; play it back onto the clean frame. */
	public static void onPassClosed(PassRecorder recorder) {
		Targets targets = new Targets(Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTexture(),
				Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTexture(), clean);
		replaying = true;
		try {
			recorder.replay(targets);
		} catch (RuntimeException e) {
			fail("could not draw the recording's world", e);
		} finally {
			replaying = false;
		}
	}

	/** Mixin hook: a clear or copy just touched a texture; if it was the game frame's, do the same to the clean one. */
	public static void mirror(GpuTexture texture, java.util.function.Consumer<Targets> action) {
		Targets targets = targets();
		if (targets == null || !targets.main(texture)) {
			return;
		}
		replaying = true;
		try {
			action.accept(targets);
		} catch (RuntimeException e) {
			fail("could not mirror a clear onto the recording", e);
		} finally {
			replaying = false;
		}
	}

	// ---- terrain drawn by another renderer ------------------------------------------------------------------------------

	private static Boolean sodium;
	private static boolean terrainTwice;

	/**
	 * Whether a terrain layer about to be drawn onto {@code output} has to be drawn a second time for the clean frame:
	 * with Sodium, whose terrain the pass recorder cannot play back. Layers drawn elsewhere (the translucent target of
	 * Fabulous graphics) reach the clean frame when the game composites them, which is recorded as usual.
	 */
	public static boolean redrawsTerrain(RenderTarget output) {
		if (!recording || replaying || terrainTwice || clean == null) {
			return false;
		}
		if (sodium == null) {
			sodium = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium");
		}
		return sodium && output == Minecraft.getInstance().gameRenderer.mainRenderTarget();
	}

	/** Runs {@code draw} onto the clean frame, then onto the real one, recording neither. */
	public static void drawTerrainTwice(Runnable draw) {
		GameRenderer renderer = Minecraft.getInstance().gameRenderer;
		RenderTarget main = mainTarget(renderer);
		GameRendererTargetAccessor accessor = (GameRendererTargetAccessor) renderer;
		terrainTwice = true;
		replaying = true;
		try {
			setMainTarget(accessor, clean);
			try {
				draw.run();
			} catch (RuntimeException e) {
				fail("could not draw the recording's terrain", e);
			} finally {
				setMainTarget(accessor, main);
			}
			draw.run();
		} finally {
			replaying = false;
			terrainTwice = false;
		}
	}

	public static CommandEncoder encoder() {
		return RenderSystem.getDevice().createCommandEncoder();
	}

	// ---- the overlay ---------------------------------------------------------------------------------------------------

	private static final class Slot {
		GpuBuffer buffer;
		int width;
		int height;
		boolean busy;
	}

	/** Frames in a row with every read-back buffer still waiting on the GPU. */
	private static int dropped;

	/** Copies the finished frame into a free read-back buffer; once the GPU is done it is painted into the overlay. */
	private static void readBack(GpuTexture texture) {
		int w = texture.getWidth(0);
		int h = texture.getHeight(0);
		Slot slot = null;
		for (int i = 0; i < SLOTS; i++) {
			if (slots[i] == null) {
				slots[i] = new Slot();
			}
			if (!slots[i].busy) {
				slot = slots[i];
				break;
			}
		}
		if (slot == null) {
			// The GPU is behind; drop this frame rather than stall. If it never catches up the overlay would sit frozen
			// (or blank) over the game, so Streamproof gives up instead.
			if (++dropped > 600) {
				dropped = 0;
				refuse("Streamproof turned off: the overlay stopped updating");
			}
			return;
		}
		dropped = 0;
		if (slot.buffer == null || slot.width != w || slot.height != h) {
			if (slot.buffer != null) {
				slot.buffer.close();
			}
			slot.buffer = RenderSystem.getDevice().createBuffer(() -> "Temple streamproof read-back",
					GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, (long) w * h * 4);
			slot.width = w;
			slot.height = h;
		}
		Slot target = slot;
		target.busy = true;
		RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(texture, target.buffer, 0, () -> {
			try {
				OverlayWindow window = overlay;
				if (window != null && overlayShown && !target.buffer.isClosed()) {
					//? if >=26 {
					try (var view = target.buffer.map(true, false)) {
					//?} else {
					/*try (var view = RenderSystem.getDevice().createCommandEncoder().mapBuffer(target.buffer, true, false)) {
					*///?}
						window.present(view.data(), target.width, target.height);
						String dump = dumpTo;
						if (dump != null) {
							dumpTo = null;
							dump(view.data(), target.width, target.height, dump);
						}
					}
				}
			} catch (RuntimeException e) {
				fail("could not show the overlay", e);
			} finally {
				target.busy = false;
			}
		}, 0);
	}

	/** Development only (the showcase): the next frame the overlay shows is also saved to {@code file} as a PNG. */
	private static volatile String dumpTo;

	public static void dumpNextFrame(String file) {
		dumpTo = file;
	}

	/** Development only: whether Streamproof is running and showing its overlay. */
	public static String state() {
		return "frame=" + frame + " overlay=" + (overlay != null) + " shown=" + overlayShown;
	}

	private static void dump(java.nio.ByteBuffer data, int w, int h, String file) {
		try (var image = new com.mojang.blaze3d.platform.NativeImage(w, h, false)) {
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					image.setPixelABGR(x, h - y - 1, data.getInt((x + y * w) * 4) | 0xFF000000);
				}
			}
			image.writeToFile(java.nio.file.Path.of(file));
		} catch (java.io.IOException | RuntimeException e) {
			LOGGER.warn("Could not save the overlay frame", e);
		}
	}

	// ---- turning off -------------------------------------------------------------------------------------------------

	private static void shutdown() {
		if (overlay != null) {
			try {
				overlay.destroy();
			} catch (RuntimeException e) {
				LOGGER.warn("Could not close the Streamproof overlay", e);
			}
			overlay = null;
		}
		overlayShown = false;
		warnedFullscreen = false;
		for (int i = 0; i < SLOTS; i++) {
			Slot slot = slots[i];
			if (slot != null && !slot.busy && slot.buffer != null) {
				slot.buffer.close();
				slots[i] = null;
			}
		}
	}

	/** Something went wrong mid-frame: switch Streamproof off (the game carries on) and say why. */
	private static void fail(String what, RuntimeException e) {
		LOGGER.error("Streamproof: {}", what, e);
		refuse("Streamproof turned off: " + what);
	}

	private static void refuse(String message) {
		frame = false;
		recording = false;
		Streamproof.turnOff();
		shutdown();
		notify("Streamproof is off", message, true);
	}

	private static void notify(String title, String detail, boolean error) {
		var notifications = TempleNzClient.notifications();
		if (notifications == null) {
			return;
		}
		if (error) {
			notifications.error(title, detail);
		} else {
			notifications.info(title, detail);
		}
	}

	/** Wraps one of Temple's HUD elements so it is left out of the recording. */
	public static net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement hud(
			net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement element) {
		return (graphics, delta) -> {
			if (!cleanPass) {
				graphics.nextStratum();
				element.extractRenderState(graphics, delta);
			}
		};
	}

	/**
	 * {@link #hud} with a name for the development timers. Each element starts a layer of its own: the game checks every
	 * shape against all earlier ones in the same layer, so sharing one with the rest of the HUD made each element pay for
	 * everything drawn before it.
	 */
	public static net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement hud(String name,
			net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement element) {
		nz.temple.util.Perf.Section timer = nz.temple.util.Perf.section("hud:" + name);
		return (graphics, delta) -> {
			if (!cleanPass) {
				long began = timer.begin();
				graphics.nextStratum();
				element.extractRenderState(graphics, delta);
				timer.end(began);
			}
		};
	}
}
