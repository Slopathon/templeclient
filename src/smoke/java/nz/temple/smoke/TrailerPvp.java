package nz.temple.smoke;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.module.ModuleManager;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static nz.temple.smoke.TrailerScenes.CursorPath;
import static nz.temple.smoke.TrailerScenes.drive;
import static nz.temple.smoke.TrailerScenes.onBeat;
import static nz.temple.smoke.TrailerScenes.press;
import static nz.temple.smoke.TrailerScenes.row;
import static nz.temple.smoke.TrailerShots.client;
import static nz.temple.smoke.TrailerShots.ease;
import static nz.temple.smoke.TrailerShots.hud;
import static nz.temple.smoke.TrailerShots.lookAt;

final class TrailerPvp {
	private TrailerPvp() {
	}
	static final List<String> SHOTS = List.of("pvp-menu", "pvp-sword", "pvp-crystal", "pvp-anchor", "pvp-surround",
			"pvp-trap", "pvp-holes", "pvp-web", "pvp-tpaura", "pvp-pearl", "pvp-totem", "pvp-shield", "pvp-trapwarn",
			"pvp-cover", "pvp-noclip", "pvp-mace");

	static final int[] SPOT = {539, -1240};
	static final int TIME = 12150;
	static final int HALF = 10;
	static Vec3 centre;

	static final Set<String> KEEP = Set.of("Nametags", "Totem Pops");
	static void all(Trailer tr, Minecraft mc) {
		if (tr.only.isBlank() || SHOTS.stream().noneMatch(tr::wanted)) {
			return;
		}
		arena(tr, mc);
		if (tr.wanted("pvp-menu")) {
			menu(tr, mc);
		}
		if (tr.wanted("pvp-sword")) {
			sword(tr, mc);
		}
		if (tr.wanted("pvp-crystal")) {
			crystal(tr, mc);
		}
		if (tr.wanted("pvp-anchor")) {
			anchor(tr, mc);
		}
		if (tr.wanted("pvp-surround")) {
			surround(tr, mc);
		}
		if (tr.wanted("pvp-trap")) {
			trap(tr, mc);
		}
		if (tr.wanted("pvp-holes")) {
			holes(tr, mc);
		}
		if (tr.wanted("pvp-web")) {
			web(tr, mc);
		}
		if (tr.wanted("pvp-tpaura")) {
			tpAura(tr, mc);
		}
		if (tr.wanted("pvp-pearl")) {
			pearl(tr, mc);
		}
		if (tr.wanted("pvp-totem")) {
			totem(tr, mc);
		}
		if (tr.wanted("pvp-shield")) {
			shield(tr, mc);
		}
		if (tr.wanted("pvp-trapwarn")) {
			trapWarn(tr, mc);
		}
		if (tr.wanted("pvp-cover")) {
			cover(tr, mc);
		}
		if (tr.wanted("pvp-noclip")) {
			noclip(tr, mc);
		}
		if (tr.wanted("pvp-mace")) {
			mace(tr, mc);
		}
	}
	static ModuleManager modules() {
		return (ModuleManager) client("modules");
	}

	static void quiet() {
		for (Module m : modules().all()) {
			Category c = m.category();
			boolean decorative = (c == Category.RENDER || c == Category.COSMETICS) && !KEEP.contains(m.name());
			boolean fighting = c == Category.COMBAT || c == Category.HVH
					|| List.of("Auto Totem", "Auto Gapple", "Auto Armor", "Refill").contains(m.name());
			if ((decorative || fighting || KEEP.contains(m.name())) && m.isEnabled() && !m.isAlwaysOn()) {
				m.setEnabled(false);
			}
		}
		client("showcaseSet", "Totem Pops", "World effect", "false");
		client("showcaseSet", "Totem Pops", "Spirit", "false");
		client("showcaseSet", "Totem Pops", "Sound", "false");
	}
	static void on(String... names) {
		for (String n : names) {
			client("showcaseEnable", n, true);
		}
	}
	static void set(String module, String setting, Object value) {
		if (!(Boolean) client("showcaseSet", module, setting, String.valueOf(value))) {
			throw new IllegalStateException("no setting " + module + " / " + setting);
		}
	}
	static void arena(Trailer tr, Minecraft mc) {
		TrailerShots.location(tr, mc, SPOT[0], SPOT[1], TIME);
		tr.add(1, () -> {
			int y = (int) TrailerShots.surface(mc, SPOT[0] + HALF, SPOT[1] + HALF);
			centre = new Vec3(SPOT[0] + 0.5, y, SPOT[1] + 0.5);
			mc.gui.hud.getChat().clearMessages(false);
		});
		tr.command(mc, "gamerule send_command_feedback false");
		tr.command(mc, "gamerule spawn_mobs false");
		tr.command(mc, "gamerule advance_time false");
		tr.command(mc, "weather clear");
		tr.command(mc, "time set " + TIME);
		reset(tr, mc);
		tr.add(1, () -> {
			quiet();
			mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
		});
	}
	static void reset(Trailer tr, Minecraft mc) {
		tr.add(2, () -> {
			var server = mc.getSingleplayerServer();
			server.execute(() -> TrailerDummy.clear(server));
			int x = (int) Math.floor(centre.x);
			int y = (int) centre.y;
			int z = (int) Math.floor(centre.z);
			int r = 24;
			for (int[] half : new int[][] {{x - r, x - 1}, {x, x + r}}) {
				String a = half[0] + " ";
				String b = half[1] + " ";
				mc.player.connection.sendCommand("fill " + a + y + " " + (z - r) + " " + b + (y + 14) + " " + (z + r) + " minecraft:air");
				mc.player.connection.sendCommand("fill " + a + (y - 3) + " " + (z - r) + " " + b + (y - 2) + " " + (z + r) + " minecraft:dirt");
				mc.player.connection.sendCommand("fill " + a + (y - 1) + " " + (z - r) + " " + b + (y - 1) + " " + (z + r) + " minecraft:grass_block");
			}
			mc.player.connection.sendCommand("fill " + (x - HALF) + " " + (y - 1) + " " + (z - HALF) + " " + (x + HALF) + " "
					+ (y - 1) + " " + (z + HALF) + " minecraft:bedrock");
		});
		tr.command(mc, "kill @e[type=minecraft:end_crystal]");
		tr.command(mc, "kill @e[type=minecraft:item]");
		tr.command(mc, "kill @e[type=minecraft:ender_pearl]");
		tr.command(mc, "kill @e[type=minecraft:mannequin]");
		tr.add(4, () -> { });
	}
	static void you(Trailer tr, Minecraft mc, Vec3 offset, float yaw, boolean resistance, String... hotbar) {
		tr.command(mc, "gamemode survival");
		tr.command(mc, "clear @s");
		tr.command(mc, "effect clear @s");
		tr.command(mc, "attribute @s minecraft:max_health base set 20");
		tr.add(2, () -> {
			Vec3 p = centre.add(offset);
			mc.player.connection.sendCommand("tp @s " + p.x + " " + p.y + " " + p.z + " " + yaw + " 0");
			for (String slot : List.of("head", "chest", "legs", "feet")) {
				String piece = switch (slot) {
					case "head" -> "helmet";
					case "chest" -> "chestplate";
					case "legs" -> "leggings";
					default -> "boots";
				};
				mc.player.connection.sendCommand("item replace entity @s armor." + slot + " with minecraft:netherite_" + piece);
			}
			for (int i = 0; i < hotbar.length; i++) {
				mc.player.connection.sendCommand("item replace entity @s hotbar." + i + " with minecraft:" + hotbar[i]);
			}
			mc.player.connection.sendCommand("effect give @s instant_health 1 10 true");
			mc.player.connection.sendCommand("effect give @s saturation infinite 10 true");
			if (resistance) {
				mc.player.connection.sendCommand("effect give @s resistance infinite 3 true");
			}
		});
		tr.add(6, () -> {
			mc.player.getInventory().setSelectedSlot(0);
			mc.player.setDeltaMovement(Vec3.ZERO);
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			hud(mc, true);
			Trailer.vanillaHud = false;
			cleanHud(false);
		});
	}
	static void cleanHud(boolean targetHud) {
		TrailerShots.hudParts(false, targetHud, false, false);
		for (String part : List.of("Watermark", "Speed graph", "Notifications")) {
			set("HUD", part, false);
		}
	}
	static void notices(Trailer.Shot shot, double at) {
		shot.cue(at, () -> set("HUD", "Notifications", true));
	}
	static void blastProof(TrailerDummy.Dummy d) {
		d.player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.EXPLOSION_KNOCKBACK_RESISTANCE).setBaseValue(1);
	}
	static TrailerDummy.Dummy[] foe(Trailer tr, Minecraft mc, String name, Vec3 offset, boolean totems, Consumer<ServerPlayer> brain) {
		TrailerDummy.Dummy[] out = new TrailerDummy.Dummy[1];
		tr.add(1, () -> {
			var server = mc.getSingleplayerServer();
			Vec3 at = centre.add(offset);
			server.execute(() -> {
				TrailerDummy.Dummy d = TrailerDummy.spawn(server, name, at, 180);
				TrailerDummy.kit(d.player, false);
				d.totems = totems;
				if (totems) {
					d.player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
				}
				d.brain = brain;
				out[0] = d;
			});
		});
		tr.add(10, () -> { });
		return out;
	}
	static ServerPlayer serverYou(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (TrailerDummy.ALL.stream().noneMatch(d -> d.player == p)) {
				return p;
			}
		}
		return null;
	}
	static Consumer<ServerPlayer> holdGround(MinecraftServer server, Vec3 home, double sway) {
		int[] tick = {0};
		return p -> {
			tick[0]++;
			ServerPlayer you = serverYou(server);
			if (you != null) {
				TrailerDummy.face(p, you.getEyePosition());
			}
			Vec3 back = home.subtract(p.position());
			double dist = back.horizontalDistance();
			p.zza = 0;
			p.xxa = 0;
			if (dist > 1.2) {
				double yaw = Math.toRadians(p.getYRot());
				double fx = -Math.sin(yaw);
				double fz = Math.cos(yaw);
				double f = (back.x * fx + back.z * fz) / dist;
				double s = (back.x * fz - back.z * fx) / dist;
				p.zza = (float) Mth.clamp(f, -1, 1);
				p.xxa = (float) Mth.clamp(s, -1, 1);
			} else if (sway > 0) {
				p.xxa = (float) (Math.sin(tick[0] * 0.09) * sway);
			}
			p.setJumping(false);
		};
	}
	static void orbit(Vec3 target, double angle, double distance, double height, float fov) {
		double a = Math.toRadians(angle);
		Vec3 pos = target.add(-Math.sin(a) * distance, height, Math.cos(a) * distance);
		lookAt(pos, target, fov);
	}

	static void orbitLive(java.util.function.Function<Float, Vec3> target, java.util.function.DoubleSupplier angle, double distance,
			double height, float fov) {
		Trailer.camera(pt -> {
			Vec3 t = target.apply(pt);
			double a = Math.toRadians(angle.getAsDouble());
			Vec3 pos = t.add(-Math.sin(a) * distance, height, Math.cos(a) * distance);
			Vec3 d = t.subtract(pos);
			float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
			float pitch = (float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);
			return new Trailer.Cam(pos, yaw, pitch, fov, true);
		});
	}
	static void aim(net.minecraft.world.entity.player.Player p, Vec3 target, float dip) {
		Vec3 d = target.subtract(p.getEyePosition());
		p.setYRot((float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90));
		p.setXRot((float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance())) + dip);
		p.yRotO = p.getYRot();
		p.xRotO = p.getXRot();
	}
	static Vec3 at(Minecraft mc, net.minecraft.world.entity.Entity e, float pt) {
		return e == null ? centre : e.getPosition(pt);
	}
	static net.minecraft.world.entity.player.Player seen(Minecraft mc, TrailerDummy.Dummy[] d) {
		if (d[0] == null || mc.level == null) {
			return null;
		}
		var e = mc.level.getEntity(d[0].player.getId());
		return e instanceof net.minecraft.world.entity.player.Player p ? p : null;
	}
	static void cleanup(Trailer tr, Minecraft mc) {
		tr.add(1, () -> {
			quiet();
			Trailer.camera((Trailer.Cam) null);
			Trailer.vanillaHud = false;
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		});
		tr.add(20, () -> { });
		reset(tr, mc);
	}
	static void menu(Trailer tr, Minecraft mc) {
		tr.command(mc, "gamemode spectator");
		tr.add(1, () -> {
			hud(mc, true);
			cleanHud(false);
		});
		double[][] rows = {row(0, 0), row(0, 5), row(1, 0), row(1, 2), row(1, 3), row(6, 12)};
		double s0 = 0.15;
		CursorPath path = new CursorPath().at(onBeat(s0, 1.2), 0.93, 0.95);
		for (int i = 0; i < rows.length; i++) {
			double click = 3 + i * 2;
			path.at(onBeat(s0, click - 0.9), rows[i]).at(onBeat(s0, click + 0.4), rows[i]);
		}
		path.at(8.5, rows[rows.length - 1]);
		tr.record(mc, new Trailer.Shot("pvp-menu", 8.5) {
			{
				cue(onBeat(s0, 0) - 0.05, () -> client("openMenu", mc.gui.screen()));
				for (int i = 0; i < rows.length; i++) {
					press(this, mc, onBeat(s0, 3 + i * 2), 0);
				}
			}
			@Override
			void update(Minecraft mc, double t) {
				Vec3 pos = centre.add(-9 + 0.5 * t, 13, -11);
				TrailerShots.cam(pos, (float) (-38 + t * 1.1), 38f, 70);
				drive(mc, path, t);
			}
			@Override
			void end(Minecraft mc) {
				mc.gui.setScreen(null);
				TrailerShots.hideCursor();
			}
		});
		cleanup(tr, mc);
	}
	static void sword(Trailer tr, Minecraft mc) {
		you(tr, mc, new Vec3(0, 0, -2), 0, true, "netherite_sword");
		var server = new MinecraftServer[1];
		tr.add(1, () -> server[0] = mc.getSingleplayerServer());
		TrailerDummy.Dummy[] foe = foe(tr, mc, "Vxlt", new Vec3(0, 0, 2.5), true, p -> { });
		tr.add(1, () -> {
			set("Kill Aura", "Rotation", "Client");
			set("Kill Aura", "Range", 4.2);
			set("Kill Aura", "Other living", false);
			set("Kill Aura", "Timing", "CPS");
			set("Kill Aura", "Min CPS", 14);
			set("Kill Aura", "Max CPS", 18);
			set("Kill Aura", "Wait for crit", false);
			set("Criticals", "Mode", "Packet");
			set("Velocity", "Horizontal", 0);
			set("Velocity", "Vertical", 0);
			set("Target Strafe", "Style", "Hug");
			set("Target Strafe", "Hug distance", 0.35);
			set("Target Strafe", "Weave", true);
			set("Target Strafe", "Sprint", true);
			cleanHud(true);
			on("Nametags", "Totem Pops");
			server[0].execute(() -> foe[0].brain = chaser(server[0]));
		});
		tr.record(mc, new Trailer.Shot("pvp-sword", 7) {
			@Override
			void begin(Minecraft mc) {
				on("Kill Aura", "Criticals", "Target Strafe", "Velocity");
			}
			@Override
			Vec3 subject(Minecraft mc, float pt) {
				return mc.player.getPosition(pt).add(at(mc, seen(mc, foe), pt)).scale(0.5).add(0, 1, 0);
			}
			@Override
			void update(Minecraft mc, double t) {
				orbitLive(pt -> mc.player.getPosition(pt).add(at(mc, seen(mc, foe), pt)).scale(0.5).add(0, 1.0, 0),
						() -> 200 - 16 * t, 6.2 - 0.4 * ease(t / 7), 1.3, 60);
			}
		});
		cleanup(tr, mc);
	}

	static Consumer<ServerPlayer> chaser(MinecraftServer server) {
		int[] tick = {0};
		return p -> {
			tick[0]++;
			ServerPlayer you = serverYou(server);
			if (you == null) {
				return;
			}
			TrailerDummy.face(p, you.getEyePosition());
			double d = p.distanceTo(you);
			p.zza = d > 2.4 ? 1f : d < 1.2 ? 0f : 0.25f;
			p.xxa = (float) (Math.sin(tick[0] * 0.07) > 0 ? 0.8 : -0.8);
			p.setSprinting(d > 2.4);
			p.setJumping(p.onGround() && tick[0] % 17 == 5);
			if (d < 3.2 && p.getAttackStrengthScale(0.5f) >= 0.95f && tick[0] > 10) {
				p.swing(InteractionHand.MAIN_HAND, true);
				p.attack(you);
			}
		};
	}
	static void crystal(Trailer tr, Minecraft mc) {
		you(tr, mc, new Vec3(0, 0, -2), 0, true, "netherite_sword", "end_crystal 64", "end_crystal 64");
		var server = new MinecraftServer[1];
		tr.add(1, () -> server[0] = mc.getSingleplayerServer());
		TrailerDummy.Dummy[] foe = foe(tr, mc, "Kxmi", new Vec3(0, 0, 2.2), true, p -> { });
		tr.add(1, () -> {
			set("Crystal Aura", "Place delay", 2);
			set("Crystal Aura", "Break delay", 1);
			set("Crystal Aura", "Rotate", true);
			on("Nametags", "Totem Pops");
			cleanHud(true);
			set("Velocity", "Horizontal", 0);
			set("Velocity", "Vertical", 0);
			Vec3 home = centre.add(0, 0, 2.2);
			server[0].execute(() -> {
				blastProof(foe[0]);
				foe[0].brain = holdGround(server[0], home, 0.5);
			});
		});
		tr.record(mc, new Trailer.Shot("pvp-crystal", 7) {
			@Override
			void begin(Minecraft mc) {
				on("Crystal Aura", "Velocity");
				notices(this, 0.3);
			}
			@Override
			Vec3 subject(Minecraft mc, float pt) {
				return at(mc, seen(mc, foe), pt).add(0, 1, 0);
			}
			@Override
			void update(Minecraft mc, double t) {
				orbitLive(pt -> mc.player.getPosition(pt).add(at(mc, seen(mc, foe), pt)).scale(0.5).add(0, 0.8, 0),
						() -> 235 + 10 * t, 9.5, 3.4, 60);
			}
		});
		cleanup(tr, mc);
	}

	static void anchor(Trailer tr, Minecraft mc) {
		you(tr, mc, new Vec3(0, 0, -2), 0, true, "netherite_sword", "respawn_anchor 64", "glowstone 64");
		var server = new MinecraftServer[1];
		tr.add(1, () -> server[0] = mc.getSingleplayerServer());
		TrailerDummy.Dummy[] foe = foe(tr, mc, "Zeph_", new Vec3(0, 0, 2.2), true, p -> { });
		tr.add(1, () -> {
			set("Anchor Aura", "Delay", 3);
			on("Nametags", "Totem Pops");
			cleanHud(true);
			set("Velocity", "Horizontal", 0);
			set("Velocity", "Vertical", 0);
			Vec3 home = centre.add(0, 0, 2.2);
			server[0].execute(() -> {
				blastProof(foe[0]);
				Consumer<ServerPlayer> hold = holdGround(server[0], home, 0.4);
				foe[0].brain = p -> {
					hold.accept(p);
					BlockPos c = BlockPos.containing(centre);
					for (BlockPos b : BlockPos.betweenClosed(c.offset(-HALF, 0, -HALF), c.offset(HALF, 3, HALF))) {
						if (p.level().getBlockState(b).is(net.minecraft.world.level.block.Blocks.FIRE)) {
							p.level().removeBlock(b, false);
						}
					}
				};
			});
		});
		tr.record(mc, new Trailer.Shot("pvp-anchor", 7) {
			@Override
			void begin(Minecraft mc) {
				on("Anchor Aura", "Velocity");
				notices(this, 0.3);
			}
			@Override
			Vec3 subject(Minecraft mc, float pt) {
				return at(mc, seen(mc, foe), pt).add(0, 1, 0);
			}
			@Override
			void update(Minecraft mc, double t) {
				orbitLive(pt -> mc.player.getPosition(pt).add(at(mc, seen(mc, foe), pt)).scale(0.5).add(0, 0.8, 0),
						() -> 125 - 10 * t, 9.5, 3.4, 60);
			}
		});
		cleanup(tr, mc);
	}

	static void surround(Trailer tr, Minecraft mc) {
		you(tr, mc, new Vec3(0.3, 0, 0.2), 0, true, "netherite_sword", "obsidian 64");
		var server = new MinecraftServer[1];
		tr.add(1, () -> server[0] = mc.getSingleplayerServer());
		TrailerDummy.Dummy[] foe = foe(tr, mc, "Rhxz", new Vec3(0, 0, 7), false, p -> { });
		tr.add(1, () -> {
			set("Surround", "Blocks per tick", 1);
			set("Surround", "Delay", 2);
			set("Surround", "Off when moved", false);
			server[0].execute(() -> {
				foe[0].player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.END_CRYSTAL, 64));
				Vec3 home = centre.add(0, 0, 3.2);
				foe[0].brain = holdGround(server[0], home, 0);
			});
		});
		tr.record(mc, new Trailer.Shot("pvp-surround", 5) {
			{
				cue(1.2, () -> on("Surround"));
			}
			@Override
			Vec3 subject(Minecraft mc, float pt) {
				return mc.player.getPosition(pt).add(0, 1, 0);
			}
			@Override
			void update(Minecraft mc, double t) {
				orbitLive(pt -> mc.player.getPosition(pt).add(0, 0.6, 0), () -> 150 + 8 * t, 5.2 - 0.6 * ease(t / 5), 3.6, 60);
			}
		});
		cleanup(tr, mc);
	}
	static void trap(Trailer tr, Minecraft mc) {
		you(tr, mc, new Vec3(0, 0, -1), 0, true, "netherite_sword", "obsidian 64");
		var server = new MinecraftServer[1];
		tr.add(1, () -> server[0] = mc.getSingleplayerServer());
		TrailerDummy.Dummy[] foe = foe(tr, mc, "Lumnr", new Vec3(0, 0, 2), false, p -> { });
		tr.add(1, () -> {
			set("Auto Trap", "Blocks per tick", 1);
			set("Auto Trap", "Delay", 1);
			on("Nametags");
			Vec3 home = centre.add(0, 0, 2);
			server[0].execute(() -> foe[0].brain = holdGround(server[0], home, 0));
		});
		tr.record(mc, new Trailer.Shot("pvp-trap", 5) {
			{
				cue(1.0, () -> on("Auto Trap"));
			}
			@Override
			Vec3 subject(Minecraft mc, float pt) {
				return at(mc, seen(mc, foe), pt).add(0, 1, 0);
			}
			@Override
			void update(Minecraft mc, double t) {
				orbitLive(pt -> at(mc, seen(mc, foe), pt).add(0, 1.0, 0), () -> 62 + 12 * t, 5.6, 2.2, 60);
			}
		});
		cleanup(tr, mc);
	}
	static void holes(Trailer tr, Minecraft mc) {
		tr.add(1, () -> {
			int x = (int) Math.floor(centre.x);
			int y = (int) centre.y;
			int z = (int) Math.floor(centre.z);
			mc.player.connection.sendCommand("fill " + (x - 4) + " " + y + " " + (z + 1) + " " + (x + 4) + " " + y + " " + (z + 6) + " minecraft:obsidian");
			for (int[] h : new int[][] {{-2, 2}, {0, 2}, {2, 2}, {-3, 4}, {-1, 4}, {1, 4}, {3, 4}}) {
				mc.player.connection.sendCommand("setblock " + (x + h[0]) + " " + y + " " + (z + h[1]) + " minecraft:air");
			}
		});
		you(tr, mc, new Vec3(0, 0, -0.6), 0, true, "netherite_sword", "obsidian 64");
		var server = new MinecraftServer[1];
		tr.add(1, () -> server[0] = mc.getSingleplayerServer());
		TrailerDummy.Dummy[] foe = foe(tr, mc, "Qwill", new Vec3(0, 1, 3.2), false, p -> { });
		tr.add(1, () -> {
			set("Hole Filler", "Blocks per tick", 1);
			set("Hole Filler", "Delay", 3);
			set("Hole Filler", "Enemy distance", 3.5);
			on("Nametags");
			Vec3 home = centre.add(0, 1, 3.2);
			server[0].execute(() -> foe[0].brain = holdGround(server[0], home, 0.5));
		});
		tr.record(mc, new Trailer.Shot("pvp-holes", 6) {
			{
				cue(1.6, () -> on("Hole Filler"));
			}
			@Override
			Vec3 subject(Minecraft mc, float pt) {
				return centre.add(0, 1, 3);
			}
			@Override
			void update(Minecraft mc, double t) {
				orbit(centre.add(0, 0.5, 2.6), 200 + 6 * t, 7.2, 5.6, 60);
			}
		});
		cleanup(tr, mc);
	}
	static void web(Trailer tr, Minecraft mc) {
		you(tr, mc, new Vec3(0, 0, -5), 0, true, "netherite_sword", "cobweb 64");
		var server = new MinecraftServer[1];
		tr.add(1, () -> server[0] = mc.getSingleplayerServer());
		TrailerDummy.Dummy[] foe = foe(tr, mc, "Draxn", new Vec3(0, 0, 7), false, p -> { });
		tr.add(1, () -> {
			on("Nametags");
			server[0].execute(() -> foe[0].brain = p -> TrailerDummy.face(p, serverYou(server[0]).getEyePosition()));
		});
		tr.record(mc, new Trailer.Shot("pvp-web", 5.5) {
			@Override
			void begin(Minecraft mc) {
				on("Auto Web");
				server[0].execute(() -> foe[0].brain = runner(server[0]));
			}
			@Override
			Vec3 subject(Minecraft mc, float pt) {
				return at(mc, seen(mc, foe), pt).add(0, 1, 0);
			}
			@Override
			void update(Minecraft mc, double t) {
				orbitLive(pt -> mc.player.getPosition(pt).add(at(mc, seen(mc, foe), pt)).scale(0.5).add(0, 0.9, 0), () -> 262, 8.5, 1.4, 58);
			}
		});
		cleanup(tr, mc);
	}
	static Consumer<ServerPlayer> runner(MinecraftServer server) {
		int[] tick = {0};
		return p -> {
			tick[0]++;
			ServerPlayer you = serverYou(server);
			if (you == null) {
				return;
			}
			TrailerDummy.face(p, you.getEyePosition());
			p.zza = p.distanceTo(you) > 1.8 ? 1f : 0f;
			p.setSprinting(false);
		};
	}
	static void tpAura(Trailer tr, Minecraft mc) {
		you(tr, mc, new Vec3(0, 0, 0), 0, true, "netherite_sword");
		var server = new MinecraftServer[1];
		tr.add(1, () -> server[0] = mc.getSingleplayerServer());
		Vec3[] spots = {new Vec3(0, 0, 8), new Vec3(7, 0, -4.5), new Vec3(-7, 0, -4.5)};
		String[] names = {"Nyxie", "B4ltic", "Oryn"};
		TrailerDummy.Dummy[][] foes = new TrailerDummy.Dummy[3][];
		for (int i = 0; i < 3; i++) {
			foes[i] = foe(tr, mc, names[i], spots[i], false, p -> { });
		}
		tr.add(1, () -> {
			set("TP Aura", "Priority", "Health");
			set("TP Aura", "Travel", "Chase");
			on("Nametags", "Totem Pops");
			for (int i = 0; i < 3; i++) {
				Vec3 home = centre.add(spots[i]);
				TrailerDummy.Dummy d = foes[i][0];
				server[0].execute(() -> {
					d.player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(6);
					d.player.setHealth(6);
					d.brain = holdGround(server[0], home, 0.6);
				});
			}
		});
		tr.record(mc, new Trailer.Shot("pvp-tpaura", 6.5) {
			@Override
			void begin(Minecraft mc) {
				on("TP Aura");
				notices(this, 0.3);
			}
			@Override
			Vec3 subject(Minecraft mc, float pt) {
				return mc.player.getPosition(pt).add(0, 1, 0);
			}
			@Override
			void update(Minecraft mc, double t) {
				orbit(centre.add(0, 0.5, 0.5), 180 + 9 * t, 16, 11, 62);
			}
		});
		cleanup(tr, mc);
	}
	static void pearl(Trailer tr, Minecraft mc) {
		you(tr, mc, new Vec3(-5, 0, -6), 0, true, "netherite_sword");
		var server = new MinecraftServer[1];
		tr.add(1, () -> server[0] = mc.getSingleplayerServer());
		TrailerDummy.Dummy[] foe = foe(tr, mc, "Sairo", new Vec3(6, 0, -4), false, p -> { });
		tr.add(1, () -> {
			on("Nametags");
			set("Pearl Trap", "Hit on arrival", true);
			server[0].execute(() -> foe[0].brain = p -> TrailerDummy.face(p, serverYou(server[0]).getEyePosition()));
		});
		tr.record(mc, new Trailer.Shot("pvp-pearl", 6) {
			{
				cue(1.2, () -> server[0].execute(() -> {
					ServerPlayer p = foe[0].player;
					Vec3 aim = centre.add(-6, 0, 6);
					Vec3 d = aim.subtract(p.getEyePosition());
					float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90);
					p.setYRot(yaw);
					p.setYHeadRot(yaw);
					p.setXRot(-15);
					p.swing(InteractionHand.MAIN_HAND, true);
					var level = p.level();
					var thrown = new ThrownEnderpearl(level, p, new ItemStack(Items.ENDER_PEARL));
					thrown.shootFromRotation(p, -15, yaw, 0, 0.8f, 0);
					level.addFreshEntity(thrown);
					foe[0].brain = q -> { };
				}));
			}
			@Override
			void begin(Minecraft mc) {
				on("Pearl Trap");
				notices(this, 0.3);
			}
			@Override
			Vec3 subject(Minecraft mc, float pt) {
				return mc.player.getPosition(pt).add(0, 1, 0);
			}
			@Override
			void update(Minecraft mc, double t) {
				orbit(centre.add(-0.5, 1, 0.5), 205 + 5 * t, 12.5, 7.5, 60);
			}
		});
		cleanup(tr, mc);
	}
	static void totem(Trailer tr, Minecraft mc) {
		you(tr, mc, new Vec3(0, 0, -2), 0, false, "netherite_sword", "end_crystal 64", "obsidian 64", "golden_apple 16");
		tr.command(mc, "item replace entity @s weapon.offhand with minecraft:totem_of_undying");
		tr.command(mc, "item replace entity @s inventory.0 with minecraft:totem_of_undying");
		tr.command(mc, "item replace entity @s inventory.1 with minecraft:totem_of_undying");
		tr.command(mc, "item replace entity @s inventory.2 with minecraft:totem_of_undying");
		var server = new MinecraftServer[1];
		tr.add(1, () -> server[0] = mc.getSingleplayerServer());
		TrailerDummy.Dummy[] foe = foe(tr, mc, "Veyra", new Vec3(0, 0, 3), true, p -> { });
		EndCrystal[] crystal = new EndCrystal[1];
		tr.add(1, () -> {
			on("Nametags");
			Trailer.vanillaHud = true;
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			Vec3 home = centre.add(0, 0, 3);
			server[0].execute(() -> {
				blastProof(foe[0]);
				foe[0].brain = holdGround(server[0], home, 0.4);
			});
		});
		tr.record(mc, new Trailer.Shot("pvp-totem", 6) {
			{
				cue(0.8, () -> server[0].execute(() -> {
					var level = foe[0].player.level();
					Vec3 c = centre.add(0.5, 0, -0.2);
					crystal[0] = new EndCrystal(level, c.x, c.y, c.z);
					crystal[0].setShowBottom(false);
					level.addFreshEntity(crystal[0]);
					foe[0].player.swing(InteractionHand.MAIN_HAND, true);
				}));
				cue(1.35, () -> server[0].execute(() -> {
					ServerPlayer you = serverYou(server[0]);
					you.setHealth(5);
					foe[0].player.swing(InteractionHand.MAIN_HAND, true);
					crystal[0].hurtServer((net.minecraft.server.level.ServerLevel) crystal[0].level(),
							crystal[0].damageSources().playerAttack(foe[0].player), 1);
				}));
			}
			@Override
			void begin(Minecraft mc) {
				on("Auto Totem", "Auto Gapple");
			}
			@Override
			void update(Minecraft mc, double t) {
				Trailer.camera((Trailer.Cam) null);
				var p = mc.player;
				aim(p, at(mc, seen(mc, foe), 1).add(0, 1.2, 0), 6);
			}
		});
		cleanup(tr, mc);
	}

	static void shield(Trailer tr, Minecraft mc) {
		you(tr, mc, new Vec3(0, 0, -1), 0, true, "netherite_sword", "netherite_axe");
		var server = new MinecraftServer[1];
		tr.add(1, () -> server[0] = mc.getSingleplayerServer());
		TrailerDummy.Dummy[] foe = foe(tr, mc, "Halbrd", new Vec3(0, 0, 1.9), true, p -> { });
		tr.add(1, () -> {
			on("Nametags");
			set("Kill Aura", "Rotation", "Client");
			set("Shield Breaker", "Legit", true);
			Trailer.vanillaHud = true;
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			server[0].execute(() -> {
				foe[0].totems = false;
				foe[0].player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
				Vec3 home = centre.add(0, 0, 1.9);
				Consumer<ServerPlayer> hold = holdGround(server[0], home, 0.3);
				foe[0].brain = p -> {
					hold.accept(p);
					if (!p.isUsingItem() && !p.getCooldowns().isOnCooldown(new ItemStack(Items.SHIELD))) {
						p.startUsingItem(InteractionHand.OFF_HAND);
					}
				};
			});
		});
		tr.record(mc, new Trailer.Shot("pvp-shield", 5) {
			{
				cue(0.9, () -> on("Kill Aura", "Shield Breaker"));
			}
			@Override
			void update(Minecraft mc, double t) {
				Trailer.camera((Trailer.Cam) null);
				if (t == 0) {
					aim(mc.player, at(mc, seen(mc, foe), 1).add(0, 1.2, 0), 4);
				}
			}
		});
		cleanup(tr, mc);
	}
	static void trapWarn(Trailer tr, Minecraft mc) {
		you(tr, mc, new Vec3(0, 0, 0), 0, true, "netherite_sword");
		var server = new MinecraftServer[1];
		tr.add(1, () -> server[0] = mc.getSingleplayerServer());
		TrailerDummy.Dummy[] foe = foe(tr, mc, "Morrow", new Vec3(2.5, 0, 1.5), false, p -> { });
		tr.add(1, () -> {
			on("Nametags");
			server[0].execute(() -> {
				foe[0].player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OBSIDIAN, 64));
				foe[0].brain = p -> TrailerDummy.face(p, serverYou(server[0]).position());
			});
		});
		int[][] cage = {{0, 0, 1}, {1, 0, 0}, {0, 0, -1}, {0, 1, 1}, {1, 1, 0}, {0, 1, -1}, {0, 2, 0}};
		tr.record(mc, new Trailer.Shot("pvp-trapwarn", 6.5) {
			{
				for (int i = 0; i < cage.length; i++) {
					int[] c = cage[i];
					cue(0.5 + i * 0.32, () -> server[0].execute(() -> {
						BlockPos p = BlockPos.containing(centre).offset(c[0], c[1], c[2]);
						var level = foe[0].player.level();
						level.setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState());
						foe[0].player.swing(InteractionHand.MAIN_HAND, true);
						level.playSound(null, p, net.minecraft.sounds.SoundEvents.STONE_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 1, 1);
					}));
				}
			}
			@Override
			void begin(Minecraft mc) {
				on("Trap Early Warning");
				notices(this, 0.3);
			}
			@Override
			Vec3 subject(Minecraft mc, float pt) {
				return mc.player.getPosition(pt).add(0, 1, 0);
			}
			@Override
			void update(Minecraft mc, double t) {
				if (t > 4.6 && mc.player.getX() > centre.x - 1.6) {
					mc.player.setDeltaMovement(-0.22, mc.player.getDeltaMovement().y, 0);
					mc.player.setSprinting(true);
				}
				double out = ease((t - 4.6) / 1.2);
				orbitLive(pt -> centre.add(0, 1, 0).lerp(mc.player.getPosition(pt).add(0, 1, 0), 0.35 + 0.4 * out), () -> 215 + 7 * t, 6.8, 5.0, 60);
			}
		});
		cleanup(tr, mc);
	}
	static void cover(Trailer tr, Minecraft mc) {
		tr.add(1, () -> {
			int x = (int) Math.floor(centre.x);
			int y = (int) centre.y;
			int z = (int) Math.floor(centre.z);
			String[] walls = {(x + 2) + " " + y + " " + (z + 1) + " " + (x + 4) + " " + (y + 1) + " " + (z + 1),
					(x - 4) + " " + y + " " + (z + 2) + " " + (x - 3) + " " + (y + 1) + " " + (z + 2),
					(x - 1) + " " + y + " " + (z + 4) + " " + (x + 1) + " " + (y + 1) + " " + (z + 4),
					(x + 4) + " " + y + " " + (z - 3) + " " + (x + 4) + " " + (y + 1) + " " + (z - 1)};
			for (String w : walls) {
				mc.player.connection.sendCommand("fill " + w + " minecraft:obsidian");
			}
		});
		you(tr, mc, new Vec3(0, 0, -1), 0, true, "netherite_sword", "end_crystal 64");
		var server = new MinecraftServer[1];
		tr.add(1, () -> server[0] = mc.getSingleplayerServer());
		TrailerDummy.Dummy[] foe = foe(tr, mc, "Ashgrv", new Vec3(0.5, 0, 7.5), false, p -> { });
		tr.add(1, () -> {
			on("Nametags");
			Vec3 home = centre.add(0.5, 0, 7.5);
			server[0].execute(() -> {
				foe[0].player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.END_CRYSTAL, 64));
				foe[0].brain = holdGround(server[0], home, 0.7);
			});
		});
		tr.record(mc, new Trailer.Shot("pvp-cover", 6) {
			@Override
			void begin(Minecraft mc) {
				on("Cover Planner");
			}
			@Override
			Vec3 subject(Minecraft mc, float pt) {
				return centre.add(0, 1, 2);
			}
			@Override
			void update(Minecraft mc, double t) {
				orbit(centre.add(0, 0, 2.5), 160 + 8 * t, 9, 9.5, 62);
			}
		});
		cleanup(tr, mc);
	}
	static void noclip(Trailer tr, Minecraft mc) {
		you(tr, mc, new Vec3(0, 0, 0), -90, true, "netherite_sword");
		tr.add(1, () -> {
			int x = (int) Math.floor(centre.x);
			int y = (int) centre.y;
			int z = (int) Math.floor(centre.z);
			for (int[] c : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
				mc.player.connection.sendCommand("fill " + (x + c[0]) + " " + y + " " + (z + c[1]) + " " + (x + c[0]) + " " + (y + 1) + " "
						+ (z + c[1]) + " minecraft:obsidian");
			}
			mc.player.connection.sendCommand("setblock " + x + " " + (y + 2) + " " + z + " minecraft:obsidian");
		});
		var server = new MinecraftServer[1];
		tr.add(1, () -> server[0] = mc.getSingleplayerServer());
		TrailerDummy.Dummy[] foe = foe(tr, mc, "Morrow", new Vec3(-2.5, 0, 1.8), false, p -> { });
		tr.add(1, () -> {
			on("Nametags");
			set("Noclip", "Mode", "Fly");
			set("Noclip", "Speed", 0.16);
			set("Noclip", "Hover", true);
			server[0].execute(() -> {
				foe[0].player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OBSIDIAN, 64));
				foe[0].brain = p -> TrailerDummy.face(p, serverYou(server[0]).getEyePosition());
			});
		});
		tr.record(mc, new Trailer.Shot("pvp-noclip", 6) {
			{
				cue(0.9, () -> on("Noclip"));
			}
			@Override
			Vec3 subject(Minecraft mc, float pt) {
				return mc.player.getPosition(pt).add(0, 1, 0);
			}
			@Override
			void update(Minecraft mc, double t) {
				mc.player.setYRot(-90);
				mc.player.yRotO = -90;
				mc.options.keyUp.setDown(t > 1.2 && t < 3.6);
				orbitLive(pt -> centre.add(0, 1, 0).lerp(mc.player.getPosition(pt).add(0, 1, 0), 0.5), () -> 200 + 5 * t, 9, 3.4, 60);
			}
			@Override
			void end(Minecraft mc) {
				mc.options.keyUp.setDown(false);
			}
		});
		cleanup(tr, mc);
	}

	static void mace(Trailer tr, Minecraft mc) {
		you(tr, mc, new Vec3(0, 0, -1), 0, true, "mace");
		var server = new MinecraftServer[1];
		tr.add(1, () -> server[0] = mc.getSingleplayerServer());
		TrailerDummy.Dummy[] foe = foe(tr, mc, "Vxlt", new Vec3(0, 0, 0.9), true, p -> { });
		tr.add(1, () -> {
			set("High Jump", "Height", 1.4);
			set("Kill Aura", "Rotation", "Silent");
			set("Kill Aura", "Rotation speed", 180);
			set("Kill Aura", "Aim range", 4);
			set("Kill Aura", "Attack jitter", 0);
			set("Kill Aura", "Range", 5);
			on("Nametags", "Totem Pops", "High Jump");
			cleanHud(true);
			Vec3 home = centre.add(0, 0, 0.9);
			server[0].execute(() -> foe[0].brain = holdGround(server[0], home, 0));
		});
		tr.record(mc, new Trailer.Shot("pvp-mace", 5.5) {
			{
				cue(1.6, () -> on("Kill Aura"));
			}
			@Override
			Vec3 subject(Minecraft mc, float pt) {
				return mc.player.getPosition(pt).add(0, 1, 0);
			}
			@Override
			void update(Minecraft mc, double t) {
				mc.options.keyJump.setDown(t > 0.8 && t < 1.1);
				orbitLive(pt -> {
					Vec3 you = mc.player.getPosition(pt);
					return new Vec3(centre.x, centre.y + 1 + 0.55 * (you.y - centre.y), centre.z);
				}, () -> 250 + 4 * t, 10.5, 1.5, 66);
			}
			@Override
			void end(Minecraft mc) {
				mc.options.keyJump.setDown(false);
			}
		});
		cleanup(tr, mc);
	}
}
