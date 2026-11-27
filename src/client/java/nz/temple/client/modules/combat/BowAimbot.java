package nz.temple.client.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.AimSmoother;
import nz.temple.client.util.SilentShot;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;
import nz.temple.social.FriendManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BowAimbot extends Module {
	private static final double GRAVITY = 0.05;
	private static final double DRAG = 0.99;
	private final FriendManager friends;
	private final ModeSetting mode;
	private final NumberSetting range;
	private final NumberSetting fov;
	private final ModeSetting priority;
	private final BoolSetting predict;
	private final NumberSetting speed;
	private final BoolSetting players;
	private final BoolSetting hostiles;
	private final BoolSetting animals;
	private final BoolSetting ignoreFriends;
	private final BoolSetting ignoreInvisible;
	private final BoolSetting throughWalls;
	private final Map<Integer, ArrayDeque<Vec3>> history = new HashMap<>();
	private static final int HISTORY = 20;
	private LivingEntity aimedAt;
	private long aimedAtMillis;
	public BowAimbot(FriendManager friends) {
		super("Bow Aimbot", Category.COMBAT, "Aims your bow, crossbow or trident at the nearest target, leading it and allowing for the "
				+ "drop over distance.");
		this.friends = friends;
		mode = add(new ModeSetting("Mode", List.of("Silent", "Camera"), "Silent")
				.describe("Silent turns only the shot: your view stays where it is and the projectile leaves toward the target. Camera "
						+ "turns your view onto the aim while you draw."));
		range = add(new NumberSetting("Range", 60, 5, 120, 1, " m").describe("Maximum target distance."));
		fov = add(new NumberSetting("FOV", 120, 10, 360, 10, "°")
				.describe("Only targets within this angle of your crosshair. 360 shoots in any direction."));
		priority = add(new ModeSetting("Priority", List.of("Angle", "Distance", "Health"), "Angle")
				.describe("Angle picks the target nearest your crosshair. Distance picks the closest. Health picks the lowest health."));
		predict = add(new BoolSetting("Predict movement", true)
				.describe("Aims where a moving target will be when the projectile arrives."));
		speed = add(new NumberSetting("Turn speed", 40, 5, 180, 5, "°/t")
				.describe("How fast the view turns onto the aim.")
				.visibleWhen(() -> mode.is("Camera")));
		group("Targets");
		players = add(new BoolSetting("Players", true).describe("Shoot at other players."));
		hostiles = add(new BoolSetting("Hostiles", true).describe("Shoot at hostile mobs."));
		animals = add(new BoolSetting("Animals", false).describe("Shoot at passive animals."));
		ignoreFriends = add(new BoolSetting("Ignore friends", true).describe("Never aim at players on your friends list."));
		ignoreInvisible = add(new BoolSetting("Ignore invisible", false).describe("Skip invisible entities."));
		throughWalls = add(new BoolSetting("Through walls", false).describe("Also aim at targets you cannot see."));
		on(TickEvent.class, e -> tick());
		SilentShot.setAim(this::silentAim);
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			history.clear();
			return;
		}
		record(mc, player);
		ItemStack held = player.getUseItem();
		boolean drawing = player.isUsingItem() && (held.getItem() instanceof BowItem || held.getItem() instanceof TridentItem);
		boolean loaded = CrossbowItem.isCharged(player.getMainHandItem()) || CrossbowItem.isCharged(player.getOffhandItem());
		if (!drawing && !loaded) {
			return;
		}
		LivingEntity lined = pick(player);
		if (lined != null) {
			aimedAt = lined;
			aimedAtMillis = System.currentTimeMillis();
		}
		if (!mode.is("Camera") || mc.gui.screen() != null) {
			return;
		}
		float[] aim = aim(player, 1, true);
		if (aim != null) {
			Vec3 dir = Vec3.directionFromRotation(aim[1], aim[0]);
			AimSmoother.look(player, player.getEyePosition().add(dir.scale(64)), speed.getFloat(), 0.4f);
		}
	}
	public LivingEntity target() {
		LivingEntity t = aimedAt;
		if (!isEnabled() || t == null || !t.isAlive() || System.currentTimeMillis() - aimedAtMillis > 8000
				|| t.level() != Minecraft.getInstance().level) {
			return null;
		}
		return t;
	}
	private float[] silentAim(LocalPlayer player) {
		if (!isEnabled() || !mode.is("Silent") || Minecraft.getInstance().level == null) {
			return null;
		}
		return aim(player, 0, false);
	}

	private float[] aim(LocalPlayer player, int extraTicks, boolean fullDraw) {
		double velocity;
		double gravity = GRAVITY;
		ItemStack use = player.getUseItem();
		if (player.isUsingItem() && use.getItem() instanceof BowItem) {
			int drawn = player.getTicksUsingItem() + extraTicks;
			velocity = BowItem.getPowerForTime(fullDraw ? Math.max(drawn, 20) : drawn) * 3.0;
			if (velocity < 0.3) {
				return null;
			}
		} else if (player.isUsingItem() && use.getItem() instanceof TridentItem) {
			velocity = 2.5;
		} else {
			ItemStack crossbow = CrossbowItem.isCharged(player.getMainHandItem()) ? player.getMainHandItem() : player.getOffhandItem();
			if (!CrossbowItem.isCharged(crossbow)) {
				return null;
			}
			ChargedProjectiles loaded = crossbow.get(DataComponents.CHARGED_PROJECTILES);
			boolean rocket = loaded != null && loaded.contains(Items.FIREWORK_ROCKET);
			velocity = rocket ? 1.6 : 3.15;
			gravity = rocket ? 0 : GRAVITY;
		}
		LivingEntity target = pick(player);
		if (target != null) {
			aimedAt = target;
			aimedAtMillis = System.currentTimeMillis();
		}
		return target == null ? null : solve(player, target, velocity, gravity, extraTicks);
	}
	private float[] solve(LocalPlayer player, LivingEntity target, double velocity, double gravity, int extraTicks) {
		Vec3 origin = new Vec3(player.getX(), player.getEyeY() - 0.1, player.getZ());
		Vec3 moved = player.position().subtract(player.xo, player.yo, player.zo);
		Vec3 carry = new Vec3(moved.x, player.onGround() ? 0 : moved.y, moved.z);
		double flight = 0;
		float[] result = null;
		for (int i = 0; i < 5; i++) {
			Vec3 at = predict(player, target, flight + extraTicks);
			float yaw = yawToward(origin, at.subtract(carry.scale(flight)));
			double[] shot = pitchFor(origin, at, yaw, velocity, gravity, carry);
			if (shot == null) {
				return result;
			}
			result = new float[] {yaw, (float) shot[0]};
			flight = shot[1];
		}
		return result;
	}
	private void record(Minecraft mc, LocalPlayer player) {
		double reach = range.get() + 16;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity) || e == player || e.distanceToSqr(player) > reach * reach) {
				continue;
			}
			ArrayDeque<Vec3> h = history.computeIfAbsent(e.getId(), id -> new ArrayDeque<>());
			Vec3 at = e.position();
			if (!h.isEmpty() && h.peekLast().distanceToSqr(at) > 64) {

				h.clear();
			}
			h.addLast(at);
			while (h.size() > HISTORY) {
				h.removeFirst();
			}
		}
		if (mc.player.tickCount % 40 == 0) {
			history.keySet().removeIf(id -> mc.level.getEntity(id) == null);
		}
	}
	private Vec3 predict(LocalPlayer player, LivingEntity target, double ticks) {
		Vec3 now = target.position();
		double half = target.getBbHeight() * 0.5;
		if (!predict.get() || ticks <= 0) {
			return now.add(0, half, 0);
		}
		ArrayDeque<Vec3> h = history.get(target.getId());
		List<Vec3> pts = h == null ? List.of() : new ArrayList<>(h);
		Vec3 last = now.subtract(target.xo, target.yo, target.zo);

		Vec3 recent = velocityOver(pts, 3, last);
		Vec3 drift = velocityOver(pts, pts.size() - 1, recent);
		if (target.isFallFlying()) {
			return clipMove(now, now.add(recent.scale(ticks)), target).add(0, half, 0);
		}
		Vec3 toTarget = now.subtract(player.position());
		Vec3 across = new Vec3(-toTarget.z, 0, toTarget.x);
		across = across.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : across.normalize();
		int flips = 0;
		int lastSign = 0;
		for (int i = 1; i < pts.size(); i++) {
			double side = pts.get(i).subtract(pts.get(i - 1)).dot(across);
			int sign = Math.abs(side) < 0.03 ? 0 : side > 0 ? 1 : -1;
			if (sign != 0) {
				if (lastSign != 0 && sign != lastSign) {
					flips++;
				}
				lastSign = sign;
			}
		}
		Vec3 flat = new Vec3(recent.x, 0, recent.z);
		if (flips >= 2) {
			double halfPeriod = (double) Math.max(1, pts.size() - 1) / flips;
			double toward = Math.min(1, ticks / Math.max(1, halfPeriod));
			flat = flat.add(new Vec3(drift.x, 0, drift.z).subtract(flat).scale(toward));
		}
		Vec3 ahead = clipMove(now, now.add(flat.scale(ticks)), target);
		double y = now.y;
		if (!target.onGround()) {
			double floor = floorUnder(ahead, now.y);
			double vy = last.y;
			int whole = (int) Math.ceil(ticks);
			double prev = y;
			for (int i = 0; i < whole; i++) {
				prev = y;
				y += vy;
				vy = (vy - 0.08) * 0.98;
				if (y <= floor) {
					y = floor;
					prev = Math.max(prev, floor);
					break;
				}
			}
			double frac = ticks - Math.floor(ticks);
			if (frac > 0 && y > floor) {
				y = prev + (y - prev) * frac;
			}
		}
		return new Vec3(ahead.x, y + half, ahead.z);
	}
	private static Vec3 velocityOver(List<Vec3> pts, int n, Vec3 fallback) {
		if (n < 1 || pts.size() < n + 1) {
			return fallback;
		}
		return pts.get(pts.size() - 1).subtract(pts.get(pts.size() - 1 - n)).scale(1.0 / n);
	}
	private static Vec3 clipMove(Vec3 from, Vec3 to, LivingEntity target) {
		Vec3 lift = new Vec3(0, Math.min(0.5, target.getBbHeight() * 0.3), 0);
		var hit = target.level().clip(new net.minecraft.world.level.ClipContext(from.add(lift), to.add(lift),
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, target));
		if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
			return to;
		}
		Vec3 stop = hit.getLocation().subtract(lift);
		Vec3 back = to.subtract(from);
		double len = back.length();
		return len < 1e-6 ? from : stop.subtract(back.scale(Math.min(1, target.getBbWidth() * 0.5 / len)));
	}
	private static double floorUnder(Vec3 at, double fromY) {
		var level = Minecraft.getInstance().level;
		Vec3 top = new Vec3(at.x, fromY + 0.5, at.z);
		var hit = level.clip(new net.minecraft.world.level.ClipContext(top, top.add(0, -64, 0),
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY,
				Minecraft.getInstance().player));
		return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? hit.getLocation().y : fromY - 64;
	}
	private static float yawToward(Vec3 from, Vec3 to) {
		return (float) Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90f;
	}
	private static double[] pitchFor(Vec3 origin, Vec3 at, float yaw, double velocity, double gravity, Vec3 carry) {
		double distance = Math.hypot(at.x - origin.x, at.z - origin.z);
		double lastPitch = 89;
		double[] last = fly(origin, yaw, 89, velocity, gravity, carry, distance);
		for (double pitch = 88; pitch >= -89; pitch -= 1) {
			double[] now = fly(origin, yaw, pitch, velocity, gravity, carry, distance);
			if (last != null && now != null && (last[0] - at.y) * (now[0] - at.y) <= 0) {
				double lo = pitch, hi = lastPitch;
				double[] best = now;
				for (int i = 0; i < 20; i++) {
					double mid = (lo + hi) / 2;
					double[] m = fly(origin, yaw, mid, velocity, gravity, carry, distance);
					if (m == null) {
						break;
					}
					best = m;
					if ((m[0] - at.y) * (now[0] - at.y) <= 0) {
						hi = mid;
					} else {
						lo = mid;
					}
				}
				return new double[] {(lo + hi) / 2, best[1]};
			}
			last = now;
			lastPitch = pitch;
		}
		return null;
	}
	private static double[] fly(Vec3 origin, float yaw, double pitch, double velocity, double gravity, Vec3 carry, double distance) {
		Vec3 v = Vec3.directionFromRotation((float) pitch, yaw).scale(velocity).add(carry);
		double x = 0, y = origin.y, z = 0;
		for (int tick = 1; tick <= 200; tick++) {
			double nx = x + v.x, ny = y + v.y, nz = z + v.z;
			double before = Math.hypot(x, z), after = Math.hypot(nx, nz);
			if (after >= distance) {
				double f = after - before < 1e-9 ? 1 : (distance - before) / (after - before);
				return new double[] {y + (ny - y) * f, tick - 1 + f};
			}
			if (after <= before && tick > 1) {
				return null;
			}
			x = nx;
			y = ny;
			z = nz;
			v = new Vec3(v.x * DRAG, v.y * DRAG - gravity, v.z * DRAG);
			if (y < origin.y - 128) {
				return null;
			}
		}
		return null;
	}
	private LivingEntity pick(LocalPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		double maxSq = range.get() * range.get();
		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity living) || living == player || !living.isAlive() || living.isDeadOrDying()
					|| living.distanceToSqr(eye) > maxSq || !matches(living) || ignoreInvisible.get() && living.isInvisible()
					|| !throughWalls.get() && !player.hasLineOfSight(living)) {
				continue;
			}
			Vec3 to = living.getBoundingBox().getCenter().subtract(eye).normalize();
			double angle = Math.toDegrees(Math.acos(Mth.clamp(look.dot(to), -1, 1)));
			if (fov.get() < 360 && angle > fov.get() / 2) {
				continue;
			}
			double score = switch (priority.get()) {
				case "Distance" -> living.distanceToSqr(eye);
				case "Health" -> living.getHealth() + living.getAbsorptionAmount();
				default -> angle;
			};
			if (score < bestScore) {
				bestScore = score;
				best = living;
			}
		}
		return best;
	}
	private boolean matches(LivingEntity living) {
		if (living instanceof Player p) {
			return players.get() && !p.isSpectator() && !p.isCreative()
					&& !(ignoreFriends.get() && friends.isFriend(p.getName().getString()));
		}
		if (living instanceof Enemy) {
			return hostiles.get();
		}
		return living instanceof Animal && animals.get();
	}
}
