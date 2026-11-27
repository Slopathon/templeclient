package nz.temple.client.modules.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

/**
 * A cinematic camera in the style of GTA's: while you move, a director cuts or glides between shots (straight down from
 * above, low on the floor as you run past, tracking alongside, orbiting, chasing low, a fixed wide shot), always framing
 * you. You keep full control. When you stop, the camera eases back to your normal view.
 */
public final class CinematicCamera extends Module {
	private static CinematicCamera instance;

	private final ModeSetting transition;
	private final NumberSetting shotLength;
	private final BoolSetting onlyMoving;
	private final BoolSetting letterbox;
	private final BoolSetting topDown;
	private final BoolSetting ground;
	private final BoolSetting side;
	private final BoolSetting orbit;
	private final BoolSetting chase;
	private final BoolSetting wide;

	private enum Shot { TOP_DOWN, GROUND, SIDE, ORBIT, CHASE, WIDE }

	/** A camera position and rotation. */
	public record Pose(Vec3 pos, float yaw, float pitch) {
	}

	private final Random random = new Random();
	private Shot shot;
	private long shotStart;
	/** Where the shot was set up: fixed shots stay put, moving shots keep their offset. */
	private Vec3 anchor = Vec3.ZERO;
	private float shotYaw;
	private double shotSide = 1;
	/** The pose the previous shot ended on, blended from at the start of the next one. */
	private Pose from;
	private long blendStart;
	private Pose last;
	private long stillSince;
	private boolean directing;
	/** 0 → 1 while easing back to the player's own view after stopping. */
	private float release;
	private long lastFrame;
	private float bars;
	private long lastBars;
	private float travelYaw;

	public CinematicCamera() {
		super("Cinematic Camera", Category.RENDER, "A GTA-style cinematic camera: while you move it cuts between dramatic angles (top "
				+ "down, low on the ground, tracking alongside, orbiting) and keeps you in frame. You keep full control.");
		transition = add(new ModeSetting("Transition", List.of("Glide", "Cut"), "Glide")
				.describe("Glide sweeps the camera smoothly from one shot to the next. Cut switches instantly, like a film edit."));
		shotLength = add(new NumberSetting("Shot length", 4, 2, 10, 0.5, " s").describe("How long each angle lasts."));
		onlyMoving = add(new BoolSetting("Only while moving", true)
				.describe("Hands the camera back to your normal view when you stand still."));
		letterbox = add(new BoolSetting("Letterbox", true).describe("Black bars at the top and bottom while a shot is running."));
		group("Shots");
		topDown = add(new BoolSetting("Top down", true).describe("Straight down from high above, turning with you."));
		ground = add(new BoolSetting("Floor view", true).describe("Low on the ground ahead of you, looking up as you run past."));
		side = add(new BoolSetting("Side tracking", true).describe("Moves alongside you at shoulder height."));
		orbit = add(new BoolSetting("Orbit", true).describe("Slowly circles around you from above."));
		chase = add(new BoolSetting("Low chase", true).describe("Follows close behind at knee height."));
		wide = add(new BoolSetting("Wide", true).describe("A fixed camera off to the side that pans to follow you."));
		instance = this;
	}

	@Override
	protected void onEnable() {
		shot = null;
		directing = false;
		release = 1;
		from = null;
	}

	@Override
	protected void onDisable() {
		directing = false;
		shot = null;
	}

	/** Whether the director is placing the camera this frame. */
	public static boolean active() {
		CinematicCamera c = instance;
		Minecraft mc = Minecraft.getInstance();
		return c != null && c.isEnabled() && mc.player != null && mc.level != null && mc.player.isAlive() && c.directing;
	}

	private static double now() {
		return System.nanoTime() / 1e9;
	}

	/** Updates the director and returns where the camera goes this frame. Called from the camera every frame. */
	public static Pose pose(float delta) {
		return instance.place(delta);
	}

	/** Decides each frame whether the director runs (called before the camera asks for a pose). */
	public static void frame(float delta) {
		CinematicCamera c = instance;
		if (c == null || !c.isEnabled()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			c.directing = false;
			return;
		}
		double speed = Math.hypot(p.getX() - p.xo, p.getZ() - p.zo);
		long nowMs = System.currentTimeMillis();
		boolean moving = speed > 0.02 || p.isFallFlying() || Math.abs(p.getY() - p.yo) > 0.05;
		if (moving) {
			c.stillSince = 0;
		} else if (c.stillSince == 0) {
			c.stillSince = nowMs;
		}
		boolean wantDirect = !c.onlyMoving.get() || moving || nowMs - c.stillSince < 1200;
		if (wantDirect && !c.directing && mc.options.getCameraType().isFirstPerson()) {
			// Start from where the player is looking, so the first shot glides out of their own view.
			c.from = new Pose(p.getEyePosition(delta), p.getViewYRot(delta), p.getViewXRot(delta));
			c.blendStart = System.nanoTime();
			c.shot = null;
			c.directing = true;
			c.release = 0;
		} else if (wantDirect && !c.directing) {
			c.from = null;
			c.shot = null;
			c.directing = true;
			c.release = 0;
		}
		if (!wantDirect && c.directing) {
			c.release = Math.min(1, c.release + c.frameDt() / 0.7f);
			if (c.release >= 1) {
				c.directing = false;
				c.shot = null;
			}
		} else {
			c.release = 0;
		}
	}

	private float frameDt() {
		long n = System.nanoTime();
		float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (n - lastFrame) / 1e9f);
		lastFrame = n;
		return dt;
	}

	private Pose place(float delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		Vec3 feet = p.getPosition(delta);
		Vec3 target = feet.add(0, p.getBbHeight() * 0.6, 0);
		double vx = p.getX() - p.xo;
		double vz = p.getZ() - p.zo;
		if (vx * vx + vz * vz > 0.0004) {
			float want = (float) Math.toDegrees(Math.atan2(-vx, vz));
			travelYaw += Mth.wrapDegrees(want - travelYaw) * 0.08f;
		} else if (shot == null) {
			travelYaw = p.getYRot();
		}
		double t = now();
		if (shot == null || t - shotStart / 1e9 > shotLength.get()) {
			Pose current = last != null ? last : new Pose(p.getEyePosition(delta), p.getViewYRot(delta), p.getViewXRot(delta));
			nextShot(p, feet);
			from = transition.is("Glide") && shot != null ? current : null;
			blendStart = System.nanoTime();
		}
		Pose want = shot == null ? new Pose(p.getEyePosition(delta), p.getViewYRot(delta), p.getViewXRot(delta)) : shotPose(p, feet, target, delta);
		Pose out = want;
		if (from != null) {
			float b = (float) Math.min(1, (System.nanoTime() - blendStart) / 1e9 / 1.1);
			float e = b * b * (3 - 2 * b);
			out = mix(from, want, e);
			if (b >= 1) {
				from = null;
			}
		}
		if (release > 0) {
			Pose own = new Pose(p.getEyePosition(delta), p.getViewYRot(delta), p.getViewXRot(delta));
			float e = release * release * (3 - 2 * release);
			out = mix(out, own, e);
		}
		last = out;
		return out;
	}

	private static Pose mix(Pose a, Pose b, float t) {
		return new Pose(a.pos().lerp(b.pos(), t), a.yaw() + Mth.wrapDegrees(b.yaw() - a.yaw()) * t, Mth.lerp(t, a.pitch(), b.pitch()));
	}

	private void nextShot(LocalPlayer p, Vec3 feet) {
		List<Shot> pool = new ArrayList<>();
		if (topDown.get()) pool.add(Shot.TOP_DOWN);
		if (ground.get()) pool.add(Shot.GROUND);
		if (side.get()) pool.add(Shot.SIDE);
		if (orbit.get()) pool.add(Shot.ORBIT);
		if (chase.get()) pool.add(Shot.CHASE);
		if (wide.get()) pool.add(Shot.WIDE);
		if (pool.size() > 1 && shot != null) {
			pool.remove(shot);
		}
		if (pool.isEmpty()) {
			shot = null;
			return;
		}
		// Top down and floor view alternate when both are on, as the most striking pair.
		Shot next = pool.get(random.nextInt(pool.size()));
		if (shot == Shot.TOP_DOWN && pool.contains(Shot.GROUND) && random.nextFloat() < 0.6f) {
			next = Shot.GROUND;
		} else if (shot == Shot.GROUND && pool.contains(Shot.TOP_DOWN) && random.nextFloat() < 0.6f) {
			next = Shot.TOP_DOWN;
		}
		shot = next;
		shotStart = System.nanoTime();
		shotYaw = travelYaw;
		shotSide = random.nextBoolean() ? 1 : -1;
		Vec3 ahead = Vec3.directionFromRotation(0, travelYaw);
		Vec3 right = new Vec3(-ahead.z, 0, ahead.x);
		switch (shot) {
			case GROUND -> {
				// A spot on the floor some way ahead, slightly to one side: the player runs towards and past it.
				Vec3 spot = feet.add(ahead.scale(7)).add(right.scale(1.6 * shotSide));
				anchor = groundAt(p, spot).add(0, 0.25, 0);
			}
			case WIDE -> anchor = clear(p, feet.add(0, 1.5, 0), feet.add(ahead.scale(6)).add(right.scale(8 * shotSide)).add(0, 4, 0));
			default -> anchor = feet;
		}
	}

	private Pose shotPose(LocalPlayer p, Vec3 feet, Vec3 target, float delta) {
		double age = (System.nanoTime() - shotStart) / 1e9;
		Vec3 eye = p.getEyePosition(delta);
		Vec3 ahead = Vec3.directionFromRotation(0, travelYaw);
		Vec3 right = new Vec3(-ahead.z, 0, ahead.x);
		return switch (shot) {
			case TOP_DOWN -> {
				double height = 9 + age * 0.35;
				Vec3 cam = clear(p, eye, feet.add(0, height, 0));
				yield new Pose(cam, travelYaw, 89.9f);
			}
			case GROUND -> look(anchor, target.add(0, -0.3, 0));
			case SIDE -> {
				Vec3 cam = clear(p, eye, feet.add(right.scale(4.5 * shotSide)).add(ahead.scale(0.6 - age * 0.15)).add(0, 1.2, 0));
				yield look(cam, target);
			}
			case ORBIT -> {
				double a = Math.toRadians(shotYaw) + age * 0.35 * shotSide;
				Vec3 cam = clear(p, eye, feet.add(-Math.sin(a) * 7, 4.5, Math.cos(a) * 7));
				yield look(cam, target);
			}
			case CHASE -> {
				Vec3 cam = clear(p, eye, feet.add(ahead.scale(-2.6)).add(right.scale(0.5 * shotSide)).add(0, 0.45, 0));
				yield look(cam, target.add(ahead.scale(3)).add(0, 0.4, 0));
			}
			case WIDE -> look(anchor, target);
		};
	}

	private static Pose look(Vec3 cam, Vec3 at) {
		double dx = at.x - cam.x, dy = at.y - cam.y, dz = at.z - cam.z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		return new Pose(cam, yaw, Mth.clamp(pitch, -89.9f, 89.9f));
	}

	/** Pulls the camera in front of any block between the player and where it wants to be. */
	private static Vec3 clear(LocalPlayer p, Vec3 from, Vec3 to) {
		BlockHitResult hit = p.level().clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
		if (hit.getType() == HitResult.Type.MISS) {
			return to;
		}
		Vec3 back = to.subtract(from).normalize().scale(0.35);
		return hit.getLocation().subtract(back);
	}

	/** The floor under (x, z) near the player's height. */
	private static Vec3 groundAt(LocalPlayer p, Vec3 spot) {
		Vec3 top = new Vec3(spot.x, p.getY() + 3, spot.z);
		Vec3 bottom = new Vec3(spot.x, p.getY() - 6, spot.z);
		BlockHitResult hit = p.level().clip(new ClipContext(top, bottom, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
		Vec3 floor = hit.getType() == HitResult.Type.MISS ? new Vec3(spot.x, p.getY(), spot.z) : hit.getLocation();
		return clear(p, p.getEyePosition(), floor.add(0, 0.05, 0));
	}

	/** Letterbox bars while a shot runs. */
	public void renderBars(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker delta) {
		long n = System.nanoTime();
		float dt = lastBars == 0 ? 0 : Math.min(0.1f, (n - lastBars) / 1e9f);
		lastBars = n;
		boolean want = isEnabled() && letterbox.get() && active() && release < 0.5f;
		bars += ((want ? 1 : 0) - bars) * (1 - (float) Math.exp(-6 * dt));
		if (bars < 0.005f) {
			return;
		}
		int h = Math.round(g.guiHeight() * 0.11f * bars);
		g.fill(0, 0, g.guiWidth(), h, 0xFF000000);
		g.fill(0, g.guiHeight() - h, g.guiWidth(), g.guiHeight(), 0xFF000000);
	}
}
