package nz.temple.client.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.Motion;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

public final class BoatFly extends Module {
	private final NumberSetting speed;
	private final NumberSetting vertical;
	private final nz.temple.setting.BoolSetting noclip;

	private static BoatFly instance;
	private AbstractBoat phased;

	public BoatFly() {
		super("Boat Fly", Category.MOVEMENT, "Lets your boat fly. Steer with the movement keys; Space goes up, Shift goes down.");
		speed = add(new NumberSetting("Speed", 1.5, 0.2, 5, 0.1, " b/t")
				.describe("Horizontal boat speed."));
		vertical = add(new NumberSetting("Vertical speed", 0.8, 0.1, 3, 0.1, " b/t")
				.describe("How fast Space and Shift move the boat up and down."));
		group("Noclip");
		noclip = add(new nz.temple.setting.BoolSetting("Noclip", false)
				.describe("Drives the boat through blocks and lets you ride through without suffocating. Works in singleplayer and on "
						+ "servers that do not check vehicle collisions."));
		instance = this;
		on(TickEvent.class, e -> tick());
	}

	public static boolean applies(net.minecraft.world.entity.player.Player player) {
		BoatFly fly = instance;
		if (fly == null || fly.phased == null || !(player.getVehicle() instanceof AbstractBoat)) {
			return false;
		}
		LocalPlayer local = Minecraft.getInstance().player;
		return local != null && (player == local || player.getUUID().equals(local.getUUID()));
	}
	private void stopPhasing() {
		if (phased != null) {
			nz.temple.client.util.VehiclePhase.set(phased, false);
			phased = null;
		}
	}
	@Override
	protected void onDisable() {
		stopPhasing();
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		Entity vehicle = player.getVehicle();
		if (!(vehicle instanceof AbstractBoat boat)) {
			stopPhasing();
			return;
		}
		boolean phase = noclip.get();
		if (phased != null && (phased != boat || !phase)) {
			stopPhasing();
		}
		if (phase) {
			nz.temple.client.util.VehiclePhase.set(boat, true);
			phased = boat;
		}
		Vec3 dir = Motion.wishDirection(player).scale(speed.get());
		double y = (mc.options.keyJump.isDown() ? 1 : 0) - (mc.options.keyShift.isDown() ? 1 : 0);
		boat.setDeltaMovement(dir.x, y * vertical.get(), dir.z);
		player.fallDistance = 0;
	}
}
