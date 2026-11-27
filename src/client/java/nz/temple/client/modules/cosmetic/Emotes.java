package nz.temple.client.modules.cosmetic;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;

public final class Emotes extends Module {
	private static volatile Emotes instance;
	private final ModeSetting emote;
	private final NumberSetting length;
	private final BoolSetting loop;
	private final BoolSetting stopOnMove;
	private final BoolSetting frontCamera;
	private long started;
	private CameraType before;
	private float pitchBefore = Float.NaN;

	public Emotes() {
		super("Emotes", Category.COSMETICS, "Plays an emote on your player, such as Take the L, the Worm, floss, wave, bow or sit. Tap the "
				+ "key to play or stop, or hold it for the emote wheel. Only visible to you, in third person.");
		emote = add(new ModeSetting("Emote", List.of("Take the L", "The Worm", "Jerk Off", "Dance", "Floss", "Dab", "Headbang",
				"T-Pose", "Zombie", "Wave", "Clap", "Salute", "Bow", "Shrug", "Sit", "Cheer"), "Take the L").describe("Which emote plays."));
		loop = add(new BoolSetting("Loop", false).describe("Repeat until turned off."));
		length = add(new NumberSetting("Length", 4, 1, 20, 0.5, " s").describe("How long it plays.")
				.visibleWhen(() -> !loop.get()));
		stopOnMove = add(new BoolSetting("Stop when moving", true).describe("Walking, jumping or sneaking stops the emote."));
		frontCamera = add(new BoolSetting("Face the camera", true)
				.describe("Turns the camera to face you while the emote plays, then back."));
		on(TickEvent.class, e -> tick());
		instance = this;
	}
	private static final long HOLD_NANOS = 180_000_000L;
	private long pressedAt = -1;
	private long wheelOpened;
	private boolean wheel;
	private double wheelX;
	private double wheelY;
	private int wheelPick = -1;
	private static final int PAGE = 8;
	private static final float PAGE_TURN_SECONDS = 0.28f;
	private int page;
	private int pageFrom;
	private int pageTurn = 1;
	private long pageTurnedAt;
	@Override
	public boolean ownsKey() {
		return true;
	}
	@Override
	public void keyPressed() {
		pressedAt = System.nanoTime();
		wheelX = 0;
		wheelY = 0;
		wheelPick = -1;
	}
	@Override
	public void keyReleased() {
		if (wheel) {
			wheel = false;
			if (wheelPick >= 0) {
				play(emote.options().get(wheelPick));
			}
		} else if (pressedAt >= 0) {
			toggle();
		}
		pressedAt = -1;
	}
	private void play(String name) {
		emote.set(name);
		if (isEnabled()) {
			setEnabled(false);
		}
		setEnabled(true);
	}
	public static boolean wheelTurn(double dx, double dy) {
		Emotes m = instance;
		if (m == null || !m.wheel) {
			return false;
		}
		m.wheelX += dx * 0.35;
		m.wheelY += dy * 0.35;
		double len = Math.hypot(m.wheelX, m.wheelY);
		if (len > 60) {
			m.wheelX *= 60 / len;
			m.wheelY *= 60 / len;
			len = 60;
		}
		m.repick();
		return true;
	}
	private void repick() {
		if (Math.hypot(wheelX, wheelY) <= 14) {
			wheelPick = -1;
			return;
		}
		double a = Math.atan2(wheelY, wheelX) + Math.PI / 2;
		int slot = (int) Math.floorMod(Math.round(a / (Math.PI * 2) * PAGE), PAGE);
		int i = page * PAGE + slot;
		wheelPick = i < emote.options().size() ? i : -1;
	}
	private int pages() {
		return (emote.options().size() + PAGE - 1) / PAGE;
	}

	public static boolean wheelScroll(double amount) {
		Emotes m = instance;
		if (m == null || !m.wheel || amount == 0) {
			return false;
		}
		int pages = m.pages();
		if (pages > 1) {
			int step = amount > 0 ? -1 : 1;
			m.pageFrom = m.page;
			m.page = Math.floorMod(m.page + step, pages);
			m.pageTurn = step;
			m.pageTurnedAt = System.nanoTime();
			m.repick();
		}
		return true;
	}
	public void renderWheel(net.minecraft.client.gui.GuiGraphicsExtractor g, nz.temple.client.render.Ui ui) {
		Minecraft mc = Minecraft.getInstance();
		if (pressedAt >= 0 && !wheel && System.nanoTime() - pressedAt > HOLD_NANOS && mc.gui.screen() == null && mc.player != null) {
			wheel = true;
			wheelOpened = System.nanoTime();
		}
		if (!wheel || mc.player == null) {
			return;
		}
		float t = Math.min(1, (System.nanoTime() - wheelOpened) / 1e9f / 0.22f);
		float ease = 1 - (1 - t) * (1 - t) * (1 - t);
		float k = nz.temple.client.render.UiScale.factor() * (0.85f + 0.15f * ease);
		float cx = g.guiWidth() / 2f;
		float cy = g.guiHeight() / 2f;
		List<String> names = emote.options();
		int pages = pages();
		float labelR = 64 * k;
		float ring = labelR + 30 * k;
		ui.begin(g);
		ui.alpha(ease);
		ui.glass(cx - ring, cy - ring, ring * 2, ring * 2, ring);
		ui.fill(cx - ring, cy - ring, ring * 2, ring * 2, ring, 0x40000000);
		float inner = 34 * k;
		ui.fill(cx - inner, cy - inner, inner * 2, inner * 2, inner, 0x66000000);
		int accent = nz.temple.client.render.Theme.primary();
		float turn = Math.min(1, (System.nanoTime() - pageTurnedAt) / 1e9f / PAGE_TURN_SECONDS);
		float turnEase = 1 - (1 - turn) * (1 - turn) * (1 - turn);
		double step = Math.PI * 2 / PAGE;
		if (turn < 1) {
			drawPage(ui, names, pageFrom, cx, cy, labelR, k, accent, pageTurn * step * 0.6 * turnEase, (1 - turnEase) * ease, -1);
		}
		drawPage(ui, names, page, cx, cy, labelR, k, accent, -pageTurn * step * 0.6 * (1 - turnEase), turnEase * ease, wheelPick);
		ui.alpha(ease);
		if (pages > 1) {
			float dot = 2.2f * k;
			float gap = 7 * k;
			float x0 = cx - (pages - 1) * gap / 2;
			for (int p = 0; p < pages; p++) {
				float r = p == page ? dot * 1.25f : dot;
				ui.fill(x0 + p * gap - r, cy + 19 * k - r, r * 2, r * 2, r, p == page ? 0xFF000000 | (accent & 0xFFFFFF) : 0x66FFFFFF);
			}
		}
		float dotR = 3.2f * k;
		float px = cx + (float) (wheelX / 60 * 24) * k;
		float py = cy + (float) (wheelY / 60 * 24) * k;
		ui.fill(px - dotR, py - dotR, dotR * 2, dotR * 2, dotR, 0xFF000000 | (accent & 0xFFFFFF));
		String middle = wheelPick >= 0 ? names.get(wheelPick) : "Emotes";
		float mw = ui.textWidth(middle, nz.temple.client.render.Fonts.SEMIBOLD, 9 * k);
		ui.textMiddle(middle, cx - mw / 2, cy - 5 * k, 9 * k, 0xFFFFFFFF, nz.temple.client.render.Fonts.SEMIBOLD);
		String hint = wheelPick >= 0 ? "release to play" : pages > 1 ? "scroll for more" : "point to pick";
		float hw = ui.textWidth(hint, nz.temple.client.render.Fonts.MEDIUM, 7 * k);
		ui.textMiddle(hint, cx - hw / 2, cy + 7 * k, 7 * k, 0x99FFFFFF, nz.temple.client.render.Fonts.MEDIUM);
		ui.alpha(1);
	}
	private void drawPage(nz.temple.client.render.Ui ui, List<String> names, int page, float cx, float cy, float labelR, float k, int accent,
			double spin, float alpha, int pick) {
		if (alpha <= 0.01f) {
			return;
		}
		ui.alpha(alpha);
		for (int slot = 0; slot < PAGE; slot++) {
			int i = page * PAGE + slot;
			if (i >= names.size()) {
				break;
			}
			double a = -Math.PI / 2 + Math.PI * 2 * slot / PAGE + spin;
			float lx = cx + (float) Math.cos(a) * labelR;
			float ly = cy + (float) Math.sin(a) * labelR;
			String label = names.get(i);
			float size = (i == pick ? 9.5f : 8.5f) * k;
			float w = ui.textWidth(label, nz.temple.client.render.Fonts.SEMIBOLD, size);
			float pillW = w + 12 * k;
			float pillH = 15 * k;
			if (i == pick) {
				ui.fill(lx - pillW / 2, ly - pillH / 2, pillW, pillH, pillH / 2, 0xB0000000 | (accent & 0xFFFFFF));
				ui.ring(lx - pillW / 2, ly - pillH / 2, pillW, pillH, pillH / 2, Math.max(0.8f, k), 0xFFFFFFFF);
			} else {
				ui.fill(lx - pillW / 2, ly - pillH / 2, pillW, pillH, pillH / 2, 0x26FFFFFF);
			}
			ui.textMiddle(label, lx - w / 2, ly, size, i == pick ? 0xFFFFFFFF : 0xD0FFFFFF, nz.temple.client.render.Fonts.SEMIBOLD);
		}
	}
	@Override
	protected void onEnable() {
		started = System.nanoTime();
		stoppedAt = -1;
		Minecraft mc = Minecraft.getInstance();
		before = mc.options.getCameraType();
		if (frontCamera.get()) {
			mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			if (mc.player != null) {
				pitchBefore = mc.player.getXRot();
				mc.player.setXRot(22);
				mc.player.xRotO = 22;
			}
		}
	}
	@Override
	protected void onDisable() {
		stoppedAt = System.nanoTime();
		Minecraft mc = Minecraft.getInstance();
		if (frontCamera.get() && before != null && mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
			mc.options.setCameraType(before);
			if (mc.player != null && !Float.isNaN(pitchBefore)) {
				mc.player.setXRot(pitchBefore);
				mc.player.xRotO = pitchBefore;
			}
		}
		before = null;
		pitchBefore = Float.NaN;
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			setEnabled(false);
			return;
		}
		double seconds = (System.nanoTime() - started) / 1e9;
		boolean moving = mc.options.keyUp.isDown() || mc.options.keyDown.isDown() || mc.options.keyLeft.isDown()
				|| mc.options.keyRight.isDown() || mc.options.keyJump.isDown() || mc.options.keyShift.isDown();
		if (stopOnMove.get() && moving && seconds > 0.4 || !loop.get() && seconds > length.get()) {
			setEnabled(false);
		}
	}
	private long stoppedAt = -1;
	private static final double EASE_IN = 0.25;
	private static final double EASE_OUT = 0.35;

	private float weight() {
		long now = System.nanoTime();
		double in = smooth(Math.min(1, (now - started) / 1e9 / EASE_IN));
		if (isEnabled()) {
			return (float) in;
		}
		if (stoppedAt < 0) {
			return -1;
		}
		double out = 1 - (now - stoppedAt) / 1e9 / EASE_OUT;
		if (out <= 0) {
			stoppedAt = -1;
			return -1;
		}
		return (float) (in * smooth(out));
	}
	private static double smooth(double x) {
		return x * x * (3 - 2 * x);
	}
	private double clock() {
		return (System.nanoTime() - started) / 1e9;
	}

	public static boolean pose(HumanoidModel<?> model) {
		Emotes m = instance;
		if (m == null) {
			return false;
		}
		float in = m.weight();
		if (in < 0) {
			return false;
		}
		J bendIn = (re, le, rk, lk) -> nz.temple.client.render.JointedLimbs.bend(re, le, rk, lk);
		double t = m.clock();
		ModelPart head = model.head;
		ModelPart ra = model.rightArm;
		ModelPart la = model.leftArm;
		ModelPart rl = model.rightLeg;
		ModelPart ll = model.leftLeg;
		float lean = 0;
		float twist = 0;
		float tilt = 0;
		switch (m.emote.get()) {
			case "Take the L" -> {
				float beat = (float) Math.sin(t * 9);
				blend(la, -1.95f, -0.4f, -0.6f, in);
				blend(ra, beat * 0.2f, 0, 0.15f + Math.max(0, beat) * 0.3f, in);
				blend(rl, 0, 0, Math.max(0, beat) * 0.75f, in);
				blend(ll, 0, 0, -Math.max(0, -beat) * 0.75f, in);
				blend(head, 0.1f, -0.12f, 0.12f + beat * 0.04f, in);
				tilt = -beat * 0.08f;
				bendIn.bend(0.25f * in, 0.4f * in, (0.15f + Math.max(0, beat) * 0.35f) * in, (0.15f + Math.max(0, -beat) * 0.35f) * in);
			}
			case "The Worm" -> {
				float wave = (float) Math.sin(t * 7);
				blend(ra, -3.05f, 0, 0.12f, in);
				blend(la, -3.05f, 0, -0.12f, in);
				blend(head, -0.7f + wave * 0.35f, 0, 0, in);
				blend(rl, (float) Math.sin(t * 7 - 2.2) * 0.45f, 0, 0.05f, in);
				blend(ll, (float) Math.sin(t * 7 - 2.2) * 0.45f, 0, -0.05f, in);
				float roll = (float) Math.sin(t * 7 - 1.1);
				bendIn.bend(0.15f * in, 0.15f * in, Math.max(0, roll) * 0.9f * in, Math.max(0, roll) * 0.9f * in);
			}
			case "Jerk Off" -> {
				float stroke = (float) Math.sin(t * 15);
				blend(ra, -0.4f + stroke * 0.06f, 0, -0.52f, in);
				blend(la, 0.05f, 0, -0.12f, in);
				blend(head, 0.3f, 0, 0, in);
				lean = 0.08f;
				bendIn.bend((0.2f + (stroke + 1) * 0.14f) * in, 0.1f * in, 0.12f * in, 0.12f * in);
				blend(rl, 0, 0, 0.1f, in);
				blend(ll, 0, 0, -0.1f, in);
			}
			case "Dance" -> {
				double w = Math.PI * 2.25;
				float step = (float) Math.sin(t * w);
				float dip = (float) Math.abs(Math.sin(t * w));
				lean = 0.06f;
				twist = step * 0.32f;
				tilt = step * 0.05f;
				blend(ra, -0.55f - step * 0.5f, 0, 0.22f, in);
				blend(la, -0.55f + step * 0.5f, 0, -0.22f, in);
				blend(rl, -Math.max(0, step) * 0.35f, 0, 0.1f, in);
				blend(ll, -Math.max(0, -step) * 0.35f, 0, -0.1f, in);
				blend(head, 0.12f - dip * 0.18f, -step * 0.35f, 0, in);
				bendIn.bend(1.35f * in, 1.35f * in, (0.15f + dip * 0.45f) * in, (0.15f + dip * 0.45f) * in);
			}
			case "Floss" -> {
				double w = Math.PI * 2.25;
				float side = (float) Math.sin(t * w);
				float depth = (float) (Math.tanh(3 * side) / Math.tanh(3));
				blend(ra, -depth * 0.8f, 0, side * 0.9f, in);
				blend(la, -depth * 0.8f, 0, side * 0.9f, in);
				float roll = depth * 2.5f * in;
				if (side > 0) {
					la.z -= roll;
				} else {
					ra.z -= roll;
				}
				tilt = side * 0.07f;
				blend(rl, 0, 0, 0.04f - side * 0.1f, in);
				blend(ll, 0, 0, -0.04f - side * 0.1f, in);
				blend(head, 0.05f, 0, -side * 0.12f, in);
				bendIn.bend(0, 0, 0.08f * in, 0.08f * in);
			}
			case "Dab" -> {
				blend(ra, -0.35f, 0.2f, 2.2f, in);
				blend(la, -1.65f, 1.05f, -0.1f, in);
				bendIn.bend(0, 2.0f * in, 0.2f * in, 0.2f * in);
				blend(head, 0.65f + (float) Math.sin(t * 5) * 0.05f, 0.45f, 0, in);
				twist = 0.25f;
				lean = 0.1f;
				blend(rl, 0, 0, 0.18f, in);
				blend(ll, 0, 0, -0.18f, in);
			}
			case "Headbang" -> {
				float bang = (float) Math.abs(Math.sin(t * 9));
				blend(head, bang * 0.75f - 0.3f, 0, 0, in);
				blend(ra, -3.0f, 0, -0.75f - bang * 0.1f, in);
				blend(la, -3.0f, 0, 0.75f + bang * 0.1f, in);
				lean = bang * 0.22f;
				bendIn.bend(0.35f * in, 0.35f * in, (0.2f + bang * 0.45f) * in, (0.2f + bang * 0.45f) * in);
				blend(rl, 0, 0, 0.15f, in);
				blend(ll, 0, 0, -0.15f, in);
			}
			case "T-Pose" -> {
				float sway = (float) Math.sin(t * 1.4);
				blend(ra, 0, 0, 1.5708f, in);
				blend(la, 0, 0, -1.5708f, in);
				blend(rl, 0, 0, 0, in);
				blend(ll, 0, 0, 0, in);
				blend(head, 0, 0, 0, in);
				tilt = sway * 0.02f;
			}
			case "Zombie" -> {
				float sway = (float) Math.sin(t * 2.2);
				blend(ra, -1.5708f + sway * 0.08f, -0.1f, 0.05f, in);
				blend(la, -1.5708f - sway * 0.08f, 0.1f, -0.05f, in);
				blend(head, 0.25f, sway * 0.35f, 0.2f, in);
				lean = 0.1f;
				twist = sway * 0.1f;
				tilt = sway * 0.06f;
				blend(rl, sway * 0.3f, 0, 0.05f, in);
				blend(ll, -sway * 0.3f, 0, -0.05f, in);
				bendIn.bend(0.15f * in, 0.15f * in, (0.2f + Math.max(0, sway) * 0.4f) * in, (0.2f + Math.max(0, -sway) * 0.4f) * in);
			}
			case "Wave" -> {
				double c = t * Math.PI * 2 * 1.5;
				float sw = (float) Math.sin(c);
				blend(ra, -2.75f, 0, -0.3f + sw * 0.32f, in);
				blend(la, 0.06f, 0, -0.1f, in);
				blend(head, -0.08f, -0.18f, 0.12f, in);
				tilt = 0.07f + sw * 0.03f;
				bendIn.bend((0.45f + 0.35f * (float) Math.sin(c - 1.2)) * in, 0.15f * in, 0.08f * in, 0.12f * in);
			}
			case "Clap" -> {
				double beat = t * Math.PI * 2.2;
				float open = (float) Math.pow(Math.abs(Math.sin(beat)), 0.7);
				blend(ra, -1.2f, -0.55f + open * 0.35f, 0.1f, in);
				blend(la, -1.2f, 0.55f - open * 0.35f, -0.1f, in);
				blend(head, 0.12f - (1 - open) * 0.08f, 0, 0, in);
				lean = 0.04f;
				bendIn.bend(0.5f * in, 0.5f * in, 0.1f * in, 0.1f * in);
			}
			case "Salute" -> {
				float settle = (float) (Math.exp(-t * 6) * Math.sin(t * 30) * 0.05);
				blend(ra, -2.1f + settle, 0.55f, 0.85f, in);
				blend(la, 0, 0, -0.04f, in);
				blend(rl, 0, 0, 0.02f, in);
				blend(ll, 0, 0, -0.02f, in);
				blend(head, -0.06f, 0.08f, 0, in);
				lean = -0.03f;
				bendIn.bend(1.05f * in, 0, 0, 0);
			}
			case "Bow" -> {
				double cycle = t % 2.8;
				double down = cycle < 0.55 ? smooth(cycle / 0.55) : cycle < 1.5 ? 1 : cycle < 2.1 ? 1 - smooth((cycle - 1.5) / 0.6) : 0;
				lean = (float) (0.95 * down);
				blend(ra, -0.75f, 0, -0.5f, in);
				blend(la, 0.55f, 0, 0.3f, in);
				blend(head, (float) (0.25 * down), 0, 0, in);
				bendIn.bend(1.35f * in, 1.2f * in, 0.05f * in, 0.05f * in);
			}
			case "Shrug" -> {
				double cycle = t % 1.9;
				float sh = (float) (cycle < 0.3 ? smooth(cycle / 0.3) : cycle < 1.2 ? 1 : cycle < 1.6 ? 1 - smooth((cycle - 1.2) / 0.4) : 0);
				blend(ra, -0.2f, 0.75f * sh, 0.3f * sh, in);
				blend(la, -0.2f, -0.75f * sh, -0.3f * sh, in);
				ra.y -= 1.6f * sh * in;
				la.y -= 1.6f * sh * in;
				blend(head, 0.05f, 0, 0.28f * sh, in);
				head.y += 0.4f * sh * in;
				lean = -0.05f * sh;
				bendIn.bend(1.3f * sh * in, 1.3f * sh * in, 0.05f * in, 0.05f * in);
			}
			case "Sit" -> {
				float look = (float) Math.sin(t * 0.55);
				blend(rl, -1.45f, 0.12f, 0.08f, in);
				blend(ll, -1.4f, -0.12f, -0.08f, in);
				blend(ra, 0.65f, 0, 0.3f, in);
				blend(la, 0.65f, 0, -0.3f, in);
				blend(head, 0.05f, look * 0.5f, 0, in);
				lean = -0.22f;
				bendIn.bend(0, 0, 0.35f * in, 0.5f * in);
			}
			case "Cheer" -> {
				double beat = t * Math.PI * 2 * 1.4;
				float pump = (float) Math.abs(Math.sin(beat));
				blend(ra, -2.85f + pump * 0.3f, 0, -0.3f, in);
				blend(la, -2.85f + pump * 0.3f, 0, 0.3f, in);
				blend(head, -0.25f, 0, 0, in);
				lean = -0.08f;
				bendIn.bend((1 - pump) * 0.9f * in, (1 - pump) * 0.9f * in, (1 - pump) * 0.5f * in, (1 - pump) * 0.5f * in);
			}
			default -> {
			}
		}
		torso(model, lean * in, twist * in, tilt * in);
		return true;
	}
	private static void torso(HumanoidModel<?> model, float lean, float twist, float tilt) {
		if (lean == 0 && twist == 0 && tilt == 0) {
			return;
		}
		org.joml.Matrix3f turn = new org.joml.Matrix3f().rotationZYX(tilt, twist, lean);
		org.joml.Vector3f v = new org.joml.Vector3f();
		for (ModelPart part : new ModelPart[] {model.body, model.head, model.rightArm, model.leftArm}) {
			turn.transform(v.set(part.x, part.y - 12, part.z));
			part.x = v.x;
			part.y = v.y + 12;
			part.z = v.z;
			org.joml.Matrix3f own = new org.joml.Matrix3f().rotationZYX(part.zRot, part.yRot, part.xRot);
			new org.joml.Matrix3f(turn).mul(own).getEulerAnglesZYX(v);
			part.xRot = v.x;
			part.yRot = v.y;
			part.zRot = v.z;
		}
	}
	public static void body(com.mojang.blaze3d.vertex.PoseStack pose) {
		org.joml.Matrix4f m = bodyMatrix();
		if (m != null) {
			pose.mulPose(m);
		}
	}
	private static org.joml.Matrix4f bodyMatrix() {
		Emotes m = instance;
		if (m == null) {
			return null;
		}
		float in = m.weight();
		if (in < 0) {
			return null;
		}
		double t = m.clock();
		float bounce = switch (m.emote.get()) {
			case "Take the L" -> (float) Math.abs(Math.cos(t * 9)) * 0.09f;
			case "Dance" -> (float) Math.abs(Math.sin(t * Math.PI * 2.25)) * 0.05f;
			case "Headbang" -> (float) Math.abs(Math.sin(t * 9)) * 0.05f;
			case "Cheer" -> (float) Math.pow(Math.abs(Math.sin(t * Math.PI * 2 * 1.4)), 1.5) * 0.28f;
			case "Sit" -> -0.58f;
			default -> 0;
		};
		if (!m.emote.is("The Worm")) {
			return bounce != 0 ? new org.joml.Matrix4f().translate(0, bounce * in, 0) : null;
		}
		float hop = (float) Math.max(0, Math.sin(t * 7)) * 0.2f;
		float rock = (float) Math.toRadians(Math.sin(t * 7 + 1.2) * 7 * in);
		return new org.joml.Matrix4f()
				.translate(0, (0.24f + hop) * in, 0)
				.rotateY((float) Math.toRadians(90 * in))
				.translate(0, 0, -0.9f * in)
				.rotateX(rock)
				.translate(0, 0, 0.9f * in)
				.rotateX((float) Math.toRadians(-90 * in));
	}
	public static org.joml.Matrix4d auraTransform(net.minecraft.world.entity.player.Player player, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (player != mc.player) {
			return null;
		}
		org.joml.Matrix4f body = bodyMatrix();
		if (body == null) {
			return null;
		}
		net.minecraft.world.phys.Vec3 feet = player.getPosition(partial);
		float yaw = net.minecraft.util.Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
		double turn = Math.toRadians(180 - yaw);
		return new org.joml.Matrix4d()
				.translate(feet.x, feet.y, feet.z)
				.rotateY(turn)
				.mul(new org.joml.Matrix4d(body))
				.rotateY(-turn)
				.translate(-feet.x, -feet.y, -feet.z);
	}
	@FunctionalInterface
	private interface J {
		void bend(float rightElbow, float leftElbow, float rightKnee, float leftKnee);
	}
	private static void blend(ModelPart part, float x, float y, float z, float amount) {
		part.xRot += (x - part.xRot) * amount;
		part.yRot += (y - part.yRot) * amount;
		part.zRot += (z - part.zRot) * amount;
	}
}
