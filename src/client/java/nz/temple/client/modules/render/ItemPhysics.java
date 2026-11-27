package nz.temple.client.modules.render;

import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import nz.temple.client.render.ItemPhysicsState;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.NumberSetting;

import java.util.HashMap;
import java.util.Map;

public final class ItemPhysics extends Module {
	private static ItemPhysics instance;
	private final NumberSetting tumble;
	private final BoolSetting rockInWater;
	private final Map<Integer, Pose> poses = new HashMap<>();
	private long lastPrune;
	public ItemPhysics() {
		super("Item Physics", Category.COSMETICS, "Dropped items tumble while falling and lie flat on the ground at random angles, instead "
				+ "of floating and spinning.");
		tumble = add(new NumberSetting("Tumble speed", 100, 0, 300, 10, "%").describe("How fast items rotate while in the air. 0 keeps them "
				+ "level."));
		rockInWater = add(new BoolSetting("Rock in water", true).describe("Items in water rock gently."));
		instance = this;
	}
	public static ItemPhysics active() {
		return instance != null && instance.isEnabled() ? instance : null;
	}
	private static boolean flat(AABB box) {
		return box.getZsize() <= 0.0625;
	}
	public float restHeight(ItemEntityRenderState state) {
		ItemPhysicsState s = (ItemPhysicsState) state;
		AABB box = state.item.getModelBoundingBox();
		boolean flat = flat(box);
		Pose pose = step(s, flat);
		float hover = -(float) box.minY + 0.0625f;
		if (!flat) {
			return s.temple$grounded() ? -(float) box.minY + 0.005f : hover;
		}
		float depth = (float) box.getZsize();
		float lying = depth / 2 + depth * 1.5f * Math.max(0, state.count - 1) / 2 + 0.005f;
		float down = (float) Math.abs(Math.sin(pose.tumble));
		return hover + (lying - hover) * down;
	}
	public Quaternionf rotation(ItemEntityRenderState state) {
		Pose pose = poses.get(((ItemPhysicsState) state).temple$id());
		if (pose == null) {
			return new Quaternionf();
		}
		return new Quaternionf().rotationY(pose.yaw).rotateX(pose.tumble).rotateZ(pose.roll);
	}
	private static final class Pose {
		float tumble;
		final float yaw;
		float roll;
		long last;

		Pose(float tumble, float yaw, long now) {
			this.tumble = tumble;
			this.yaw = yaw;
			this.last = now;
		}
	}
	private Pose step(ItemPhysicsState s, boolean flat) {
		long now = System.nanoTime();
		Pose pose = poses.computeIfAbsent(s.temple$id(), k -> {
			long h = k * 2654435761L;
			float yaw = (float) ((h & 0xFFFF) / 65536.0 * Math.PI * 2);

			return new Pose(flat ? (float) (Math.PI / 2) : 0, yaw, now);
		});
		float dt = (float) Math.min(0.1, Math.max(0, (now - pose.last) / 1e9));
		pose.last = now;
		double quarter = Math.PI / 2;
		if (!s.temple$grounded() && !s.temple$inWater()) {
			pose.tumble += dt * tumble.getFloat() / 100f * (2.5f + s.temple$speed() * 25f);
			pose.roll = (float) Math.sin(pose.tumble * 0.5) * 0.25f;
		} else {
			double target = flat ? Math.round((pose.tumble - quarter) / Math.PI) * Math.PI + quarter : Math.round(pose.tumble / quarter) * quarter;
			float ease = (float) (1 - Math.exp(-dt * 14));
			pose.tumble += (float) (target - pose.tumble) * ease;
			pose.roll -= pose.roll * ease;
			if (s.temple$inWater() && rockInWater.get()) {
				pose.roll = (float) Math.sin(now / 1e9 * 1.7 + pose.yaw * 3) * 0.12f;
			}
		}
		if (now - lastPrune > 5_000_000_000L) {
			lastPrune = now;
			poses.values().removeIf(p -> now - p.last > 5_000_000_000L);
		}
		return pose;
	}
	@Override
	protected void onDisable() {
		poses.clear();
	}
}
