package nz.temple.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

import static nz.temple.smoke.TrailerShots.client;

final class TrailerChecks {
	private TrailerChecks() {
	}
	static void all(Trailer tr, Minecraft mc) {
		if (!(tr.wanted("check-noclip") || tr.wanted("check-boatnoclip") || tr.wanted("check-noclipvehicle")) || tr.only.isBlank()) {
			return;
		}
		TrailerPvp.arena(tr, mc);
		if (tr.wanted("check-noclip")) {
			noclip(tr, mc);
		}
		if (tr.wanted("check-boatnoclip")) {
			boatNoclip(tr, mc, "Boat Fly");
		}
		if (tr.wanted("check-noclipvehicle")) {
			boatNoclip(tr, mc, "Noclip");
		}
	}
	static void wall(Tr tr) {
		tr.t.add(1, () -> {
			Vec3 c = TrailerPvp.centre;
			int x = (int) Math.floor(c.x);
			int y = (int) c.y;
			int z = (int) Math.floor(c.z);
			tr.mc.player.connection.sendCommand("fill " + (x - 3) + " " + y + " " + (z + 3) + " " + (x + 3) + " " + (y + 3) + " " + (z + 5)
					+ " minecraft:stone");
		});
		tr.t.add(10, () -> { });
	}
	record Tr(Trailer t, Minecraft mc) {
	}

	static void holdForward(Trailer tr, Minecraft mc, int ticks) {
		for (int i = 0; i < ticks; i++) {
			tr.add(1, () -> mc.options.keyUp.setDown(true));
		}
	}
	static String pos(Entity e) {
		return e == null ? "none" : String.format(Locale.ROOT, "%.2f,%.2f,%.2f", e.getX(), e.getY(), e.getZ());
	}

	static void report(Minecraft mc, String what, Entity client) {
		var server = mc.getSingleplayerServer();
		Entity onServer = client == null ? null : server.overworld().getEntity(client.getUUID());
		double wallFar = TrailerPvp.centre.z + 6;
		System.out.println("TEMPLE_CHECK " + what + " client=" + pos(client) + " server=" + pos(onServer)
				+ " passedWall(client)=" + (client != null && client.getZ() > wallFar)
				+ " passedWall(server)=" + (onServer != null && onServer.getZ() > wallFar)
				+ " health=" + mc.player.getHealth());
	}
	static void noclip(Trailer tr, Minecraft mc) {
		TrailerPvp.you(tr, mc, new Vec3(0, 0, 0), 0, false, "netherite_sword");
		wall(new Tr(tr, mc));
		tr.add(1, () -> {
			mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
			mc.player.setYRot(0);
			mc.player.setXRot(0);
			client("showcaseSet", "Noclip", "Mode", "Fly");
			client("showcaseEnable", "Noclip", true);
			report(mc, "noclip-start", mc.player);
		});
		holdForward(tr, mc, 60);
		tr.add(1, () -> {
			mc.options.keyUp.setDown(false);
			report(mc, "noclip-after-3s", mc.player);
		});
		tr.add(20, () -> report(mc, "noclip-held", mc.player));
		tr.add(1, () -> client("showcaseEnable", "Noclip", false));
		tr.add(40, () -> report(mc, "noclip-off", mc.player));
		TrailerPvp.cleanup(tr, mc);
	}
	static void boatNoclip(Trailer tr, Minecraft mc, String module) {
		TrailerPvp.you(tr, mc, new Vec3(0, 0, 0), 0, false, "netherite_sword");
		wall(new Tr(tr, mc));
		tr.add(1, () -> {
			Vec3 c = TrailerPvp.centre;
			mc.player.connection.sendCommand("summon minecraft:oak_boat " + c.x + " " + c.y + " " + c.z + " {Rotation:[0f,0f]}");
		});
		tr.add(10, () -> mc.player.connection.sendCommand("ride @s mount @e[type=minecraft:oak_boat,limit=1,sort=nearest]"));
		tr.add(20, () -> {
			mc.player.setYRot(0);
			client("showcaseSet", "Boat Fly", "Noclip", "true");
			client("showcaseSet", "Noclip", "Vehicles", "true");
			client("showcaseEnable", module, true);
			report(mc, module + " boat-start", mc.player.getVehicle());
		});
		holdForward(tr, mc, 60);
		tr.add(1, () -> {
			mc.options.keyUp.setDown(false);
			report(mc, module + " boat-after-3s", mc.player.getVehicle());
		});
		tr.add(20, () -> report(mc, module + " boat-held", mc.player.getVehicle()));
		tr.add(40, () -> report(mc, module + " boat-held-3s", mc.player.getVehicle()));
		tr.add(1, () -> {
			client("showcaseEnable", module, false);
			mc.player.connection.sendCommand("kill @e[type=minecraft:oak_boat]");
		});
		TrailerPvp.cleanup(tr, mc);
	}
}
