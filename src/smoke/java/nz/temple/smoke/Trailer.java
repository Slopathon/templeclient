package nz.temple.smoke;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import nz.temple.trailer.VirtualClock;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.BooleanSupplier;

public final class Trailer {
	static final boolean ON = System.getProperty("temple.trailer") != null;
	static final int FPS = 60;
	static final String WORLD = "Trailer";
	static final String SEED = System.getProperty("temple.trailer.seed", "-4172144997902289642");
	public record Cam(Vec3 position, float yaw, float pitch, float fov, boolean detached) {
	}
	private static Trailer instance;

	private static volatile java.util.function.Function<Float, Cam> cam;
	public static Cam camera() {
		var source = cam;
		return source == null ? null : source.apply(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
	}

	static void camera(Cam c) {
		cam = c == null ? null : pt -> c;
	}
	static void camera(java.util.function.Function<Float, Cam> source) {
		cam = source;
	}
	public static boolean exactTime() {
		return ON;
	}

	public static volatile boolean vanillaHud;
	public static boolean cleanHud() {
		return ON && instance != null && instance.stage >= 3;
	}
	static void install() {
		instance = new Trailer();
		ClientTickEvents.END_CLIENT_TICK.register(instance::tick);
	}

	public static void frameStart(Minecraft mc) {
		if (ON && instance != null) {
			instance.onFrameStart(mc);
		}
	}
	public static void frameEnd(Minecraft mc) {
		if (ON && instance != null) {
			instance.onFrameEnd(mc);
		}
	}
	final int width;
	final int height;
	final boolean preview = Boolean.getBoolean("temple.trailer.preview");
	final int subframes = Integer.getInteger("temple.trailer.subframes", preview ? 1 : 4);
	final double shutter = 0.5;
	final Path out = Path.of(System.getProperty("temple.trailer.out", "footage"));
	final String only = System.getProperty("temple.trailer.shots", "");
	private Trailer() {
		String[] size = System.getProperty("temple.trailer.size", "1920x1080").toLowerCase(Locale.ROOT).split("x");
		width = Integer.parseInt(size[0]);
		height = Integer.parseInt(size[1]);
	}
	private record Step(int delay, Runnable action, BooleanSupplier until, int timeout) {
	}

	private final Deque<Step> steps = new ArrayDeque<>();
	private int stage;
	private int waited;
	private int timer;
	private int ticks;
	private Step waiting;
	void add(int delay, Runnable action) {
		steps.add(new Step(delay, action, null, 0));
	}

	void waitFor(BooleanSupplier until, int timeout) {
		steps.add(new Step(0, () -> { }, until, timeout));
	}

	void command(Minecraft mc, String command) {
		add(2, () -> mc.player.connection.sendCommand(command));
	}
	private void tick(Minecraft mc) {
		mc.options.pauseOnLostFocus = false;
		mc.getFramerateLimitTracker().onInputReceived();
		if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) {
			mc.gui.setScreen(null);
		}
		ticks++;
		try {
			switch (stage) {
				case 0 -> {
					var current = mc.gui.screen();
					boolean ready = current instanceof TitleScreen
							|| current instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
					if (ready && mc.gui.overlay() == null && ++waited > 20) {
						mc.options.enableVsync().set(false);
						mc.options.framerateLimit().set(260);
						if (Files.isDirectory(mc.gameDirectory.toPath().resolve("saves").resolve(WORLD))) {
							System.out.println("TEMPLE_TRAILER opening existing world");
							mc.createWorldOpenFlows().openWorld(WORLD, () -> { });
							stage = 2;
						} else {
							CreateWorldScreen.openFresh(mc, () -> { });
							stage = 1;
						}
						waited = 0;
					}
				}
				case 1 -> {
					if (mc.gui.screen() instanceof CreateWorldScreen screen) {
						WorldCreationUiState state = screen.getUiState();
						state.setName(WORLD);
						state.setSeed(SEED);
						state.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
						state.setAllowCommands(true);
						for (var child : screen.children()) {
							if (child instanceof AbstractButton button && button.getMessage().getString().toLowerCase(Locale.ROOT).contains("create")) {
								System.out.println("TEMPLE_TRAILER creating world, seed " + SEED);
								button.onPress(null);
								stage = 2;
								break;
							}
						}
					}
				}
				case 2 -> {
					if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.ConfirmScreen confirm) {
						for (var child : confirm.children()) {
							if (child instanceof AbstractButton button) {
								String label = button.getMessage().getString().toLowerCase(Locale.ROOT);
								if (!label.contains("back") && !label.contains("cancel") && !label.equals("no")) {
									button.onPress(null);
									break;
								}
							}
						}
						return;
					}
					if (mc.level != null && mc.player != null && mc.gui.screen() == null && ++waited > 60) {
						resizeWindow(mc);
						TrailerShots.plan(this, mc);
						add(1, () -> {
							System.out.println("TEMPLE_TRAILER_DONE");
							mc.stop();
						});
						stage = 3;
					}
				}
				case 3 -> runSteps();
				default -> {
				}
			}
		} catch (RuntimeException e) {
			e.printStackTrace();
			System.out.println("TEMPLE_TRAILER_FAIL " + e);
			mc.stop();
		}
	}
	private void runSteps() {
		if (recording != null) {
			return;
		}
		if (waiting != null) {
			if (waiting.until().getAsBoolean() || ++waited > waiting.timeout()) {
				if (waited > waiting.timeout()) {
					System.out.println("TEMPLE_TRAILER wait timed out");
				}
				waiting = null;
			}
			return;
		}
		if (timer > 0) {
			timer--;
			return;
		}
		Step step = steps.poll();
		if (step == null) {
			return;
		}
		step.action().run();
		timer = step.delay();
		if (step.until() != null) {
			waiting = step;
			waited = 0;
		}
	}
	private void resizeWindow(Minecraft mc) {
		long handle = mc.getWindow().handle();
		GLFW.glfwSetWindowSizeLimits(handle, GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE, 8192, 8192);
		GLFW.glfwSetWindowPos(handle, 0, 0);
		GLFW.glfwSetWindowSize(handle, width, height);
		int scale = Math.max(1, Math.round(4f * height / 1080f));
		add(10, () -> {
			mc.options.guiScale().set(scale);
			mc.resizeGui();
			var target = mc.gameRenderer.mainRenderTarget();
			System.out.println("TEMPLE_TRAILER window " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight()
					+ " target " + target.width + "x" + target.height + " gui scale " + scale);
		});
	}
	abstract static class Shot {
		final String name;
		final double seconds;
		private final List<double[]> firedAt = new ArrayList<>();
		private final List<Runnable> cues = new ArrayList<>();
		private final List<Boolean> fired = new ArrayList<>();
		Shot(String name, double seconds) {
			this.name = name;
			this.seconds = seconds;
		}

		void cue(double at, Runnable action) {
			firedAt.add(new double[] {at});
			cues.add(action);
			fired.add(false);
		}
		void runCues(double t) {
			for (int i = 0; i < cues.size(); i++) {
				if (!fired.get(i) && t >= firedAt.get(i)[0]) {
					fired.set(i, true);
					cues.get(i).run();
				}
			}
		}
		void begin(Minecraft mc) {
		}

		abstract void update(Minecraft mc, double t);
		Vec3 subject(Minecraft mc, float partial) {
			return null;
		}

		void end(Minecraft mc) {
		}
	}
	private Shot recording;
	private long baseNanos;
	private int frame;
	private int sub;
	private int totalFrames;
	private Encoder encoder;
	private int pendingCaptures;
	private PrintWriter cursorLog;
	private PrintWriter focusLog;
	boolean wanted(String shot) {
		if (only.isBlank()) {
			return true;
		}
		for (String s : only.split(",")) {
			if (s.trim().equalsIgnoreCase(shot)) {
				return true;
			}
		}
		return false;
	}
	void record(Minecraft mc, Shot shot) {
		add(1, () -> {
			shot.update(mc, 0);
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.toastManager().clear();
		});
		waitFor(() -> mc.levelRenderer.hasRenderedAllSections() && mc.levelRenderer.sectionRenderDispatcher() != null
				&& mc.levelRenderer.sectionRenderDispatcher().getCompileQueueSize() == 0, 1200);
		add(40, () -> { });
		waitFor(() -> mc.levelRenderer.hasRenderedAllSections(), 600);
		add(1, () -> start(mc, shot));
		waitFor(() -> recording == null && pendingCaptures == 0 && closing == null, 1_000_000);
	}
	private void start(Minecraft mc, Shot shot) {
		System.out.println("TEMPLE_TRAILER recording " + shot.name + " (" + shot.seconds + " s)");
		try {
			Files.createDirectories(out);
			cursorLog = new PrintWriter(Files.newBufferedWriter(out.resolve(shot.name + ".cursor.csv")));
			cursorLog.println("frame,x,y,down");
			focusLog = new PrintWriter(Files.newBufferedWriter(out.resolve(shot.name + ".focus.csv")));
			focusLog.println("frame,x,y");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		mc.gui.hud.getChat().clearMessages(false);
		VirtualClock.lock();
		baseNanos = VirtualClock.nanoTime();
		frame = 0;
		sub = 0;
		totalFrames = (int) Math.round(shot.seconds * FPS);
		recording = shot;
		var target = mc.gameRenderer.mainRenderTarget();
		encoder = preview ? null : new Encoder(out.resolve(shot.name + ".mp4"), target.width, target.height, subframes);
		shot.begin(mc);
	}
	private String focusLine(Minecraft mc, Shot shot) {
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 p = shot.subject(mc, pt);
		Cam c = camera();
		if (p == null || c == null) {
			return "-1,-1";
		}
		double yaw = Math.toRadians(c.yaw());
		double pitch = Math.toRadians(c.pitch());
		Vec3 f = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
		Vec3 r = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 up = r.cross(f);
		Vec3 d = p.subtract(c.position());
		double z = d.dot(f);
		if (z < 0.05) {
			return "-1,-1";
		}
		double tan = Math.tan(Math.toRadians(c.fov()) / 2);
		double aspect = (double) width / height;
		double x = 0.5 + d.dot(r) / z / (2 * tan * aspect);
		double y = 0.5 - d.dot(up) / z / (2 * tan);
		return String.format(Locale.ROOT, "%.4f,%.4f", x, y);
	}
	private double shotTime() {
		return (frame + (subframes <= 1 ? 0 : shutter * sub / subframes)) / FPS;
	}
	private void onFrameStart(Minecraft mc) {
		Shot shot = recording;
		if (shot == null) {
			return;
		}
		mc.gui.toastManager().clear();
		double t = shotTime();
		VirtualClock.advanceTo(baseNanos + Math.round(t * 1e9));
		syncServer(mc);
		shot.runCues(t);
		shot.update(mc, t);
	}
	private static void syncServer(Minecraft mc) {
		var server = mc.getSingleplayerServer();
		if (server == null) {
			return;
		}
		long now = net.minecraft.util.Util.getNanos();
		long deadline = System.nanoTime() + 250_000_000L;
		while (server.isRunning() && !server.isPaused() && ((nz.temple.smoke.mixin.TrailerServerAccessor) server).temple$nextTickNanos() <= now) {
			if (System.nanoTime() > deadline) {
				System.out.println("TEMPLE_TRAILER server did not catch up (next tick " + (((nz.temple.smoke.mixin.TrailerServerAccessor) server).temple$nextTickNanos() - now) / 1e6 + " ms)");
				break;
			}
			Thread.onSpinWait();
		}
	}
	private void onFrameEnd(Minecraft mc) {
		if (closing != null && pendingCaptures == 0) {
			closing.finish();
			closing = null;
		}
		Shot shot = recording;
		if (shot == null) {
			return;
		}
		if (sub == 0) {
			cursorLog.println(frame + "," + TrailerShots.cursorLine());
			focusLog.println(frame + "," + focusLine(mc, shot));
		}
		if (encoder != null) {
			capture(mc, encoder, sub == subframes - 1);
		}
		if (++sub >= subframes) {
			sub = 0;
			frame++;
			if (frame % 60 == 0) {
				System.out.println("TEMPLE_TRAILER " + shot.name + " frame " + frame + "/" + totalFrames);
			}
		}
		if (frame >= totalFrames) {
			recording = null;
			shot.end(mc);
			cursorLog.close();
			focusLog.close();
			VirtualClock.unlock();
			Encoder done = encoder;
			if (done != null) {
				closing = done;
			}
			encoder = null;
			System.out.println("TEMPLE_TRAILER finished " + shot.name);
		}
	}
	private Encoder closing;
	private void capture(Minecraft mc, Encoder target, boolean lastOfFrame) {
		var renderTarget = mc.gameRenderer.mainRenderTarget();
		GpuTexture texture = renderTarget.getColorTexture();
		if (texture == null || renderTarget.width != target.width || renderTarget.height != target.height) {
			throw new IllegalStateException("render target changed size mid-shot");
		}
		int w = renderTarget.width;
		int h = renderTarget.height;
		GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Trailer capture", 9, (long) w * h * texture.getFormat().blockSize());
		pendingCaptures++;
		RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(texture, buffer, 0L, () -> {
			try (GpuBufferSlice.MappedView read = buffer.map(true, false)) {
				target.accumulate(read.data(), lastOfFrame);
			}
			buffer.close();
			pendingCaptures--;
		}, 0);
	}
	static final class Encoder {
		final int width;
		final int height;
		private final int subframes;
		private final int[] sum;
		private int count;
		private final Process process;
		private final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(3);
		private final Thread writer;
		private final Path file;
		Encoder(Path file, int width, int height, int subframes) {
			this.file = file;
			this.width = width;
			this.height = height;
			this.subframes = subframes;
			this.sum = new int[width * height * 3];
			String ffmpeg = TrailerShots.ffmpeg();
			try {
				process = new ProcessBuilder(ffmpeg, "-y", "-loglevel", "error",
						"-f", "image2pipe", "-c:v", "bmp", "-framerate", Integer.toString(FPS), "-i", "-",
						"-c:v", "libx264", "-preset", "slow", "-crf", "10", "-pix_fmt", "yuv420p",
						"-color_primaries", "bt709", "-color_trc", "bt709", "-colorspace", "bt709", "-movflags", "+faststart",
						file.toString())
						.redirectErrorStream(true)
						.redirectOutput(ProcessBuilder.Redirect.INHERIT)
						.start();
			} catch (IOException e) {
				throw new UncheckedIOException("could not start ffmpeg (" + ffmpeg + ")", e);
			}
			OutputStream pipe = new BufferedOutputStream(process.getOutputStream(), 1 << 22);
			writer = new Thread(() -> {
				boolean failed = false;
				while (true) {
					byte[] frame;
					try {
						frame = queue.take();
					} catch (InterruptedException e) {
						break;
					}
					if (frame.length == 0) {
						break;
					}
					if (failed) {
						continue;
					}
					try {
						pipe.write(frame);
					} catch (IOException e) {
						failed = true;
						System.out.println("TEMPLE_TRAILER ffmpeg pipe failed: " + e);
					}
				}
				try {
					pipe.close();
				} catch (IOException ignored) {
				}
			}, "Trailer encoder");
			writer.start();
		}
		private byte[] newFrame() {
			int size = BMP_HEADER + width * height * 3;
			ByteBuffer h = ByteBuffer.allocate(BMP_HEADER).order(ByteOrder.LITTLE_ENDIAN);
			h.put((byte) 'B').put((byte) 'M').putInt(size).putInt(0).putInt(BMP_HEADER);
			h.putInt(40).putInt(width).putInt(height).putShort((short) 1).putShort((short) 24).putInt(0)
					.putInt(width * height * 3).putInt(2835).putInt(2835).putInt(0).putInt(0);
			byte[] frame = new byte[size];
			System.arraycopy(h.array(), 0, frame, 0, BMP_HEADER);
			return frame;
		}
		private static final int BMP_HEADER = 54;
		void accumulate(ByteBuffer data, boolean last) {
			ByteBuffer bytes = data.duplicate().order(ByteOrder.LITTLE_ENDIAN);
			int n = width * height;
			if (count == 0 && last) {
				byte[] frame = newFrame();
				for (int i = 0, j = BMP_HEADER; i < n; i++, j += 3) {
					int abgr = bytes.getInt(i * 4);
					frame[j] = (byte) (abgr >> 16);
					frame[j + 1] = (byte) (abgr >> 8);
					frame[j + 2] = (byte) abgr;
				}
				emit(frame);
				return;
			}
			for (int i = 0, j = 0; i < n; i++, j += 3) {
				int abgr = bytes.getInt(i * 4);
				sum[j] += abgr & 0xFF;
				sum[j + 1] += (abgr >> 8) & 0xFF;
				sum[j + 2] += (abgr >> 16) & 0xFF;
			}
			count++;
			if (last) {
				byte[] frame = newFrame();
				int half = count / 2;
				for (int j = 0; j < sum.length; j += 3) {
					frame[BMP_HEADER + j] = (byte) ((sum[j + 2] + half) / count);
					frame[BMP_HEADER + j + 1] = (byte) ((sum[j + 1] + half) / count);
					frame[BMP_HEADER + j + 2] = (byte) ((sum[j] + half) / count);
					sum[j] = 0;
					sum[j + 1] = 0;
					sum[j + 2] = 0;
				}
				count = 0;
				emit(frame);
			}
		}
		private void emit(byte[] frame) {
			try {
				queue.put(frame);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		void finish() {
			emit(new byte[0]);
			try {
				writer.join();
				int code = process.waitFor();
				System.out.println("TEMPLE_TRAILER wrote " + file + (code == 0 ? "" : " (ffmpeg exit " + code + ")"));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}
}
