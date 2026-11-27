package nz.temple.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public final class VehiclePhase {
	private VehiclePhase() {
	}
	public static void set(Entity vehicle, boolean on) {
		vehicle.noPhysics = on;
		vehicle.setNoGravity(on);
		var server = Minecraft.getInstance().getSingleplayerServer();
		if (server == null) {
			return;
		}
		var dimension = vehicle.level().dimension();
		java.util.UUID id = vehicle.getUUID();
		server.execute(() -> {
			var level = server.getLevel(dimension);
			Entity copy = level == null ? null : level.getEntity(id);
			if (copy != null) {
				copy.noPhysics = on;
				copy.setNoGravity(on);
			}
		});
	}
}
