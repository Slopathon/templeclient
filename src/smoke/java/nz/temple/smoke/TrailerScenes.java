package nz.temple.smoke;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static nz.temple.smoke.TrailerShots.client;
import static nz.temple.smoke.TrailerShots.cursor;
import static nz.temple.smoke.TrailerShots.ease;
import static nz.temple.smoke.TrailerShots.hud;
import static nz.temple.smoke.TrailerShots.lerp;
import static nz.temple.smoke.TrailerShots.location;
import static nz.temple.smoke.TrailerShots.lookAt;
import static nz.temple.smoke.TrailerShots.surface;

final class TrailerScenes {
	private TrailerScenes() {
	}
	static final int[] CHERRY = {416, -1280};
	static final int[] BADLANDS = {2400, -1760};
	static final int SUNSET = 12650;
	static final String DEATH_STYLE = System.getProperty("temple.trailer.death", "Ghost");
	static final int[] PEAKS = {-2144, -2048};
	static final double BEAT = 60 / 135.086;
	static double onBeat(double start, double n) {
		return start + n * BEAT;
	}
	static final double ROW0 = 0.114;
	static final double ROW = 30 / 1080.0;
	static final double[] COLUMN = {0.018, 0.145, 0.272, 0.400, 0.527, 0.653, 0.780};
	static final double SEARCH_X = 0.49;
	static final double SEARCH_Y = 0.026;
	static double[] row(int column, int row) {
		return new double[] {COLUMN[column] + 0.045, ROW0 + row * ROW};
	}

	static final class CursorPath {
		private final List<double[]> keys = new ArrayList<>();
		CursorPath at(double t, double[] p) {
			keys.add(new double[] {t, p[0], p[1]});
			return this;
		}
		CursorPath at(double t, double x, double y) {
			return at(t, new double[] {x, y});
		}
		double[] sample(double t) {
			if (keys.isEmpty() || t < keys.get(0)[0]) {
				return null;
			}
			for (int i = 0; i < keys.size() - 1; i++) {
				double[] a = keys.get(i);
				double[] b = keys.get(i + 1);
				if (t < b[0]) {
					double k = ease((t - a[0]) / (b[0] - a[0]));
					double dx = b[1] - a[1];
					double dy = b[2] - a[2];
					double arc = Math.sin(k * Math.PI) * 0.12;
					return new double[] {a[1] + dx * k - dy * arc * 0.5625, a[2] + dy * k + dx * arc * 1.7778 * 0.5625};
				}
			}
			double[] last = keys.get(keys.size() - 1);
			return new double[] {last[1], last[2]};
		}
	}
	static void drive(Minecraft mc, CursorPath path, double t) {
		double[] p = path.sample(t);
		if (p == null) {
			TrailerShots.hideCursor();
		} else {
			cursor(mc, p[0], p[1]);
		}
	}
	static void press(Trailer.Shot shot, Minecraft mc, double at, int button) {
		shot.cue(at, () -> TrailerShots.click(mc, button));
		shot.cue(at + 0.09, TrailerShots::release);
	}
	static void trailerSky() {
		client("showcaseSet", "Skybox", "Style", "Plasma");
		client("showcaseSet", "Skybox", "Use sky colors", "true");
	}
	static void all(Trailer tr, Minecraft mc) {
		tr.add(1, () -> {

			trailerSky();
			client("showcaseEnable", "Skybox", true);
			client("showcaseEnable", "Waving Foliage", true);
			client("showcaseEnable", "Contrails", false);
			client("showcaseEnable", "Waypoints", false);
			mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
			client("showcaseSet", "Player Aura", "Material", "Glass");
			client("showcaseSet", "Pets", "Color", "Theme");
			client("showcaseSet", "Cape", "Material", "Glass");
			client("showcaseSet", "Trails", "Material", "Glow");
			client("showcaseSet", "Jump Circles", "Material", "Glow");
			client("showcaseSet", "Death Effects", "Style", "Ghost");
			client("showcaseSet", "Death Effects", "Material", "Glow");
			for (String off : List.of("Chams", "Storage ESP", "Explosion Effects", "Storm Effects", "Aurora", "Trails", "Jump Circles", "Wet Ground")) {
				client("showcaseEnable", off, false);
			}
			mc.options.biomeBlendRadius().set(7);
			TrailerShots.originals("Player Aura", "Pets", "Death Effects", "Trails", "Footsteps", "Contrails", "Aurora");
			client("openMenu", mc.gui.screen());
		});
		tr.add(5, () -> mc.gui.setScreen(null));
		if (tr.wanted("vista")) {
			vista(tr, mc);
		}
		if (tr.wanted("menu")) {
			menu(tr, mc);
		}
		if (tr.wanted("search")) {
			search(tr, mc);
		}
		if (tr.wanted("combat")) {
			combat(tr, mc, "combat", false);
		}
		if (tr.wanted("combat-real")) {
			combat(tr, mc, "combat-real", true);
		}
		if (tr.wanted("cosmetics")) {
			cosmetics(tr, mc);
		}
		if (tr.wanted("flight")) {
			flight(tr, mc);
		}
		if (tr.wanted("peaks")) {
			peaks(tr, mc);
		}
		if (tr.wanted("esp")) {
			esp(tr, mc);
		}
		if (tr.wanted("break")) {
			breakBlocks(tr, mc);
		}
		if (tr.wanted("map")) {
			map(tr, mc);
		}
		if (tr.wanted("skies")) {
			skies(tr, mc);
		}
		if (tr.wanted("emotelab")) {
			emoteLab(tr, mc);
		}
		if (tr.wanted("chams")) {
			chams(tr, mc);
		}
		if (tr.wanted("aura")) {
			aura(tr, mc);
		}
		if (tr.wanted("trails")) {
			trails(tr, mc);
		}
		if (tr.wanted("place")) {
			place(tr, mc);
		}
		if (tr.wanted("hands")) {
			hands(tr, mc);
		}
		if (tr.wanted("hero")) {
			hero(tr, mc);
		}
		if (tr.wanted("deaths")) {
			deathTest(tr, mc);
		}
		if (tr.wanted("emotes")) {
			emotes(tr, mc);
		}
		if (tr.wanted("cosmeticswide")) {
			cosmetics(tr, mc, "cosmeticswide", 1.7);
		}
		if (tr.wanted("menutoggles")) {
			menuToggles(tr, mc);
		}
		if (tr.wanted("breakstyles")) {
			breakStyles(tr, mc);
		}
		if (tr.wanted("hud")) {
			hudShot(tr, mc);
		}
		if (tr.wanted("zoom")) {
			zoom(tr, mc);
		}
		if (tr.wanted("skyswap")) {
			skySwap(tr, mc);
		}
		if (tr.wanted("ambience")) {
			ambience(tr, mc);
		}
		if (tr.wanted("arrows")) {
			arrows(tr, mc);
		}
		if (tr.wanted("totem")) {
			totem(tr, mc);
		}
		if (tr.wanted("storm")) {
			storm(tr, mc);
		}
		if (tr.wanted("stormskies")) {
			storm(tr, mc, "Nebula", "storm-nebula");
			storm(tr, mc, "Storm", "storm-storm");
		}
		if (tr.wanted("explosion")) {
			explosion(tr, mc);
		}
		if (tr.wanted("jumps")) {
			jumps(tr, mc);
		}
		if (tr.wanted("aurora")) {
			aurora(tr, mc);
		}
		if (tr.wanted("themes")) {
			themes(tr, mc);
		}
		TrailerPvp.all(tr, mc);
		TrailerStills.all(tr, mc);
		TrailerChecks.all(tr, mc);
	}
	static void vista(Trailer tr, Minecraft mc) {
		location(tr, mc, CHERRY[0], CHERRY[1], SUNSET);
		tr.add(1, () -> hud(mc, false));
		Vec3 from = new Vec3(CHERRY[0] - 18, 0, CHERRY[1] + 10);
		Vec3 to = new Vec3(CHERRY[0] - 4, 0, CHERRY[1] - 6);
		tr.record(mc, new Trailer.Shot("vista", 6) {
			double ground;
			@Override
			void update(Minecraft mc, double t) {
				if (ground == 0) {
					ground = Math.max(surface(mc, from.x, from.z), surface(mc, to.x, to.z));
				}
				double k = ease(t / seconds);
				Vec3 pos = lerp(k, from, to).add(0, ground + 3 + 16 * k, 0);
				float yaw = (float) (-60 + 18 * k);
				float pitch = (float) (6 - 14 * k);
				TrailerShots.cam(pos, yaw, pitch, 72);
			}
		});
	}
	static void menuBackdrop(Minecraft mc, double t) {
		double ground = surface(mc, CHERRY[0], CHERRY[1]);
		Vec3 pos = new Vec3(CHERRY[0] + 0.5, ground + 30, CHERRY[1] + 0.5);
		TrailerShots.cam(pos, (float) (-45 + t * 1.2), -4f, 70);
	}
	static void menu(Trailer tr, Minecraft mc) {
		location(tr, mc, CHERRY[0], CHERRY[1], SUNSET);
		tr.add(1, () -> {
			hud(mc, true);
			TrailerShots.hudParts(false, false, false, false);
			for (String m : List.of("Target ESP", "Player Aura", "Pets", "Kill Aura")) {
				client("showcaseEnable", m, false);
			}
		});
		double[] targetEsp = row(3, 25);
		double[] playerAura = row(4, 15);
		double[] pets = row(4, 28);
		double[] killAura = row(0, 1);
		double s0 = 0.15;
		CursorPath path = new CursorPath()
				.at(onBeat(s0, 1.5), 0.93, 0.95)
				.at(onBeat(s0, 3.6), targetEsp)
				.at(onBeat(s0, 4.4), targetEsp)
				.at(onBeat(s0, 6.6), playerAura)
				.at(onBeat(s0, 7.3), playerAura)
				.at(onBeat(s0, 8.6), pets)
				.at(onBeat(s0, 9.3), pets)
				.at(onBeat(s0, 11.5), killAura)
				.at(8.5, killAura);
		tr.record(mc, new Trailer.Shot("menu", 8.5) {
			{
				cue(onBeat(s0, 0) - 0.05, () -> client("openMenu", mc.gui.screen()));
				press(this, mc, onBeat(s0, 4), 0);
				press(this, mc, onBeat(s0, 7), 0);
				press(this, mc, onBeat(s0, 9), 0);
				press(this, mc, onBeat(s0, 12), 1);
			}
			@Override
			void update(Minecraft mc, double t) {
				menuBackdrop(mc, t);
				drive(mc, path, t);
			}
			@Override
			void end(Minecraft mc) {
				mc.gui.setScreen(null);
				TrailerShots.hideCursor();
			}
		});
	}
	static void search(Trailer tr, Minecraft mc) {
		location(tr, mc, CHERRY[0], CHERRY[1], SUNSET);
		tr.add(1, () -> {
			hud(mc, true);
			TrailerShots.hudParts(false, false, false, false);
		});
		double s0 = 0.95;
		CursorPath path = new CursorPath()
				.at(0.4, 0.62, 0.30)
				.at(onBeat(s0, 0.8), SEARCH_X, SEARCH_Y + 0.004)
				.at(4.5, SEARCH_X, SEARCH_Y + 0.004);
		String word = "elytra";
		tr.record(mc, new Trailer.Shot("search", 4.5) {
			{
				cue(0.0, () -> client("openMenu", mc.gui.screen()));
				press(this, mc, onBeat(s0, 1), 0);
				for (int i = 0; i < word.length(); i++) {
					String c = word.substring(i, i + 1);
					cue(onBeat(s0, 2 + i * 0.5), () -> TrailerShots.type(mc, c));
				}
				cue(onBeat(s0, 5), () -> TrailerShots.key(mc, GLFW.GLFW_KEY_DOWN));
			}
			@Override
			void update(Minecraft mc, double t) {
				menuBackdrop(mc, 2 + t);
				drive(mc, path, t);
			}
			@Override
			void end(Minecraft mc) {
				mc.gui.setScreen(null);
				TrailerShots.hideCursor();
			}
		});
	}
	static void stage(Trailer tr, Minecraft mc, int x, int z, Vec3[] at) {
		tr.add(1, () -> {
			int[] spot = TrailerShots.flat(mc, x, z, 48);
			int y = (int) surface(mc, spot[0], spot[1]);
			at[0] = new Vec3(spot[0] + 0.5, y, spot[1] + 0.5);
			int r = 32;
			int cz = spot[1];
			for (int sx = spot[0] - r; sx <= spot[0] + r; sx += 16) {
				String a = sx + " ";
				String b = Math.min(sx + 15, spot[0] + r) + " ";
				var c = mc.player.connection;
				c.sendCommand("fill " + a + y + " " + (cz - r) + " " + b + (y + 24) + " " + (cz + r) + " minecraft:air");
				c.sendCommand("fill " + a + (y - 3) + " " + (cz - r) + " " + b + (y - 2) + " " + (cz + r) + " minecraft:dirt");
				c.sendCommand("fill " + a + (y - 1) + " " + (cz - r) + " " + b + (y - 1) + " " + (cz + r) + " minecraft:grass_block");
			}
			mc.player.connection.sendCommand("tp @s " + at[0].x + " " + at[0].y + " " + at[0].z + " 0 0");
		});
		tr.add(30, () -> { });
	}
	static void combat(Trailer tr, Minecraft mc, String name, boolean realism) {
		TrailerPvp.arena(tr, mc);
		tr.add(2, () -> {
			Vec3 c = TrailerPvp.centre;
			int x = (int) Math.floor(c.x);
			int y = (int) c.y - 1;
			int z = (int) Math.floor(c.z);
			int h = TrailerPvp.HALF;
			mc.player.connection.sendCommand("fill " + (x - h) + " " + y + " " + (z - h) + " " + (x + h) + " " + y + " " + (z + h)
					+ " minecraft:grass_block replace minecraft:bedrock");
		});
		tr.command(mc, "time set " + SUNSET);
		tr.command(mc, "gamerule show_death_messages false");
		tr.command(mc, "gamerule keep_inventory true");
		TrailerPvp.you(tr, mc, new Vec3(0, 0, -1.6), 0, true, "netherite_sword");
		tr.command(mc, "effect give @s strength infinite 1 true");
		var server = new net.minecraft.server.MinecraftServer[1];
		tr.add(1, () -> server[0] = mc.getSingleplayerServer());
		TrailerDummy.Dummy[] foe = TrailerPvp.foe(tr, mc, "xNotch", new Vec3(0, 0, 1.6), false, p -> { });
		tr.add(1, () -> {
			trailerSky();
			for (String m : List.of("Skybox", "Waving Foliage", "Cape")) {
				client("showcaseEnable", m, true);
			}
			client("showcaseSet", "Kill Aura", "Rotation", "Client");
			client("showcaseSet", "Kill Aura", "Range", "4.2");
			client("showcaseSet", "Kill Aura", "Other living", "false");
			client("showcaseSet", "Kill Aura", "Timing", "CPS");
			client("showcaseSet", "Kill Aura", "Min CPS", "14");
			client("showcaseSet", "Kill Aura", "Max CPS", "18");
			client("showcaseSet", "Kill Aura", "Wait for crit", "false");
			client("showcaseSet", "Kill Aura", "Rotation speed", "180");
			client("showcaseSet", "Kill Aura", "Smoothing", "0");
			client("showcaseSet", "Kill Aura", "Hit angle", "45");
			client("showcaseSet", "Kill Aura", "Aim point", "Chest");
			client("showcaseSet", "Criticals", "Mode", "Packet");
			client("showcaseSet", "Velocity", "Horizontal", "0");
			client("showcaseSet", "Velocity", "Vertical", "0");
			client("showcaseSet", "Target Strafe", "Style", "Hug");
			client("showcaseSet", "Target Strafe", "Hug distance", "0.35");
			client("showcaseSet", "Target Strafe", "Weave", "true");
			client("showcaseSet", "Target Strafe", "Sprint", "true");
			client("showcaseSet", "Target Strafe", "Natural pace", "false");
			client("showcaseSet", "Target Strafe", "Only on ground", "false");
			client("showcaseSet", "Target ESP", "Style", "Ghosts");
			client("showcaseSet", "Damage Numbers", "Who", "Targets");
			client("showcaseSet", "Chams", "Style", "Hologram");
			client("showcaseSet", "Chams", "Self", "false");
			client("showcaseSet", "Death Effects", "Style", DEATH_STYLE);
			client("showcaseSet", "Death Effects", "Who", "Everything");
			client("showcaseRealism", "Death Effects", realism);
			client("showcaseEnable", "Damage Effects", false);
			client("showcaseEnable", "Nametags", false);
			for (String m : List.of("Chams", "Target ESP", "Hit Particles", "Damage Numbers", "Hit Markers", "Death Effects")) {
				client("showcaseEnable", m, true);
			}
			TrailerShots.hudParts(false, true, false, false);
			server[0].execute(() -> {
				var p = foe[0].player;
				p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(20);
				p.setHealth(20);
				p.getFoodData().setFoodLevel(6);
				foe[0].brain = TrailerPvp.chaser(server[0]);
			});
		});
		tr.record(mc, new Trailer.Shot(name, 7) {
			double killedAt = -1;
			Vec3 killPos;
			{
				cue(2.8, () -> server[0].execute(() -> {
					if (foe[0] != null && foe[0].player.isAlive()) {
						foe[0].player.setHealth(Math.min(foe[0].player.getHealth(), 1));
					}
				}));
			}
			@Override
			void begin(Minecraft mc) {
				TrailerPvp.on("Kill Aura", "Criticals", "Target Strafe", "Velocity");
			}
			Vec3 mid(Minecraft mc, float pt) {
				Vec3 you = mc.player.getPosition(pt);
				if (killPos != null) {
					return killPos.add(0, 1.2, 0).lerp(you.add(0, 1, 0), 0.25);
				}
				return you.add(TrailerPvp.at(mc, TrailerPvp.seen(mc, foe), pt)).scale(0.5).add(0, 1.0, 0);
			}

			@Override
			Vec3 subject(Minecraft mc, float pt) {
				return mid(mc, pt);
			}
			@Override
			void update(Minecraft mc, double t) {
				var seen = TrailerPvp.seen(mc, foe);
				if (killedAt < 0 && t > 0.5 && (seen == null || seen.isDeadOrDying())) {
					killedAt = t;
					killPos = seen == null ? mc.player.position() : seen.position();
					System.out.println("TEMPLE_TRAILER kill at " + t);
					client("showcaseEnable", "Kill Aura", false);
					client("showcaseEnable", "Target Strafe", false);
					client("showcaseSet", "HUD", "Target HUD", "false");
				}
				double kill = killedAt;
				TrailerPvp.orbitLive(pt -> {
					Vec3 m = mid(mc, pt);
					return kill < 0 ? m : m.add(0, 1.4 * ease((t - kill) / 1.4), 0);
				}, () -> 200 - 16 * t, 5.4 - 0.4 * ease(t / seconds), 0.9, 62);
			}
			@Override
			void end(Minecraft mc) {
				TrailerPvp.quiet();
				client("showcaseRealism", "Death Effects", false);
				client("showcaseSet", "HUD", "Target HUD", "true");
				for (String m : List.of("Skybox", "Waving Foliage")) {
					client("showcaseEnable", m, true);
				}
				Trailer.camera((Trailer.Cam) null);
				mc.player.setSprinting(false);
				server[0].execute(() -> TrailerDummy.clear(server[0]));
			}
		});
		tr.command(mc, "gamemode creative");
	}
	static Entity nearestFoe(Minecraft mc) {
		Entity best = null;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof net.minecraft.world.entity.decoration.Mannequin && e.isAlive()
					&& (best == null || e.distanceToSqr(mc.player) < best.distanceToSqr(mc.player))) {
				best = e;
			}
		}
		return best;
	}
	static void map(Trailer tr, Minecraft mc) {
		int[][] spots = {{CHERRY[0], CHERRY[1]}, {CHERRY[0] + 140, CHERRY[1] - 90}, {CHERRY[0] - 170, CHERRY[1] + 60}, {CHERRY[0] + 60, CHERRY[1] + 180}};
		String[] names = {"Base", "Stash", "Portal", "Farm"};
		tr.command(mc, "gamemode spectator");
		for (int i = 0; i < spots.length; i++) {
			int[] sp = spots[i];
			String name = names[i];
			tr.add(1, () -> mc.player.connection.sendCommand("tp @s " + sp[0] + " 120 " + sp[1]));
			tr.add(40, () -> { });
			tr.add(10, () -> mc.player.connection.sendChat(".wp add " + name));
		}
		location(tr, mc, CHERRY[0] + 20, CHERRY[1] + 10, SUNSET);
		tr.add(1, () -> {
			hud(mc, true);
			TrailerShots.hudParts(false, false, false, true);
			client("showcaseEnable", "Waypoints", true);
		});
		CursorPath path = new CursorPath()
				.at(1.4, 0.62, 0.64)
				.at(2.1, 0.55, 0.55)
				.at(3.6, 0.40, 0.48)
				.at(5.0, 0.40, 0.48);
		tr.record(mc, new Trailer.Shot("map", 5) {
			{
				cue(0.1, () -> mc.player.connection.sendChat(".map"));
			}
			double lastT;
			@Override
			void update(Minecraft mc, double t) {
				menuBackdrop(mc, t);
				drive(mc, path, t);
				var screen = mc.gui.screen();
				if (screen == null || screen instanceof net.minecraft.client.gui.screens.ChatScreen) {
					lastT = t;
					return;
				}
				double dt = t - lastT;
				lastT = t;
				double w = mc.getWindow().getGuiScaledWidth();
				double h = mc.getWindow().getGuiScaledHeight();
				if (t > 0.5 && t < 2.2) {
					screen.mouseScrolled(w * 0.5, h * 0.5, 0, -2.2 * dt);
				}
				if (t > 2.1 && t < 3.6) {
					double k = dt / 1.5;
					var down = new net.minecraft.client.input.MouseButtonEvent(w * 0.55, h * 0.55, new net.minecraft.client.input.MouseButtonInfo(0, 0));
					if (t - dt <= 2.1) {
						screen.mouseClicked(down, false);
					}
					screen.mouseDragged(new net.minecraft.client.input.MouseButtonEvent(w * (0.55 - 0.15 * ease((t - 2.1) / 1.5)),
							h * (0.55 - 0.07 * ease((t - 2.1) / 1.5)), new net.minecraft.client.input.MouseButtonInfo(0, 0)), -w * 0.15 * k, -h * 0.07 * k);
				}
			}
			@Override
			void end(Minecraft mc) {
				mc.gui.setScreen(null);
				TrailerShots.hideCursor();
				client("showcaseEnable", "Waypoints", false);
			}
		});
	}
	static void cosmetics(Trailer tr, Minecraft mc) {
		cosmetics(tr, mc, "cosmetics", 1.0);
	}
	static void cosmetics(Trailer tr, Minecraft mc, String name, double far) {
		location(tr, mc, CHERRY[0] - 30, CHERRY[1] - 20, SUNSET);
		Vec3[] at = new Vec3[1];
		tr.command(mc, "gamemode creative");
		tr.command(mc, "clear @s");
		stage(tr, mc, CHERRY[0] - 30, CHERRY[1] - 20, at);

		tr.add(1, () -> mc.player.connection.sendCommand("fill " + ((int) at[0].x - 6) + " " + (int) at[0].y + " " + ((int) at[0].z - 6) + " "
				+ ((int) at[0].x + 6) + " " + ((int) at[0].y + 3) + " " + ((int) at[0].z + 6) + " minecraft:air replace #minecraft:mineable/pickaxe"));
		tr.add(1, () -> mc.player.connection.sendCommand("fill " + ((int) at[0].x - 6) + " " + ((int) at[0].y - 1) + " " + ((int) at[0].z - 6) + " "
				+ ((int) at[0].x + 6) + " " + ((int) at[0].y - 1) + " " + ((int) at[0].z + 6) + " minecraft:grass_block replace #minecraft:dirt"));
		tr.add(10, () -> {
			hud(mc, false);
			client("showcaseEnable", "Criticals", false);
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			client("showcaseSet", "Player Aura", "Wings", "true");
			client("showcaseSet", "Player Aura", "Halo", "true");
			client("showcaseSet", "Player Aura", "Spirits", "true");
			client("showcaseSet", "Pets", "Pet", "Jellyfish");
			client("showcaseSet", "Pets", "Position", "Orbit");
			client("showcaseSet", "Pets", "Size", "1");
			client("showcaseSet", "Cape", "Style", "Emblem");
			for (String m : List.of("Player Aura", "Pets", "Cape")) {
				client("showcaseEnable", m, true);
			}
		});
		warmUp(tr, "Player Aura", "Pets");
		tr.record(mc, new Trailer.Shot(name, 6) {
			@Override
			Vec3 subject(Minecraft mc, float pt) {
				return mc.player.getPosition(pt).add(0, 1.0, 0);
			}
			{
				TrailerShots.flicker(this, 1.1, BEAT, 6, "Player Aura", "Pets");
			}
			@Override
			void update(Minecraft mc, double t) {
				Vec3 body = at[0].add(0, 1.15, 0);
				double k = ease(t / seconds);
				double a = Math.toRadians(-100 + 150 * k);
				double r = (3.9 - 0.5 * k) * far;
				Vec3 pos = body.add(Math.cos(a) * r, (0.1 + 0.35 * Math.sin(k * Math.PI)) * far, Math.sin(a) * r);
				lookAt(pos, body.add(0, 0.15, 0), 58);
			}
		});
	}
	static void flight(Trailer tr, Minecraft mc) {
		location(tr, mc, BADLANDS[0] - 60, BADLANDS[1], SUNSET);
		tr.command(mc, "gamemode creative");
		tr.command(mc, "clear @s");
		tr.command(mc, "item replace entity @s armor.chest with minecraft:elytra");
		Vec3[] start = new Vec3[1];
		tr.add(1, () -> {
			double y = surface(mc, BADLANDS[0] - 60, BADLANDS[1]) + 22;
			start[0] = new Vec3(BADLANDS[0] - 60, y, BADLANDS[1]);
			mc.player.connection.sendCommand("tp @s " + start[0].x + " " + start[0].y + " " + start[0].z + " -90 10");
			client("showcaseSet", "Contrails", "Style", "Streamers");
			client("showcaseEnable", "Contrails", true);
			client("showcaseEnable", "Player Aura", false);
			client("showcaseEnable", "Pets", false);
			client("showcaseEnable", "Cape", false);
			hud(mc, true);
			TrailerShots.hudParts(false, false, true, true);
		});
		tr.add(20, () -> { });
		tr.record(mc, new Trailer.Shot("flight", 6) {
			boolean live;
			@Override
			void begin(Minecraft mc) {
				live = true;
			}
			@Override
			void update(Minecraft mc, double t) {
				var p = mc.player;
				if (!live) {
					Vec3 body = p.position().add(0, 0.6, 0);
					lookAt(body.add(-5.2, 1.7, 2.4), body.add(4, 0, 0), 78);
					return;
				}
				if (!p.isFallFlying()) {
					p.getAbilities().flying = false;
					p.startFallFlying();
				}
				double ground = surface(mc, p.getX() + 12, p.getZ());
				double wantY = ground + 14;
				double vy = Mth.clamp((wantY - p.getY()) * 0.06, -0.3, 0.3);
				double vz = 0.35 * Math.sin(t * 1.3);
				p.setDeltaMovement(1.6, vy, vz);
				p.setYRot((float) (-90 + Math.toDegrees(Math.atan2(vz, 1.6))));
				p.setXRot(8);
				Trailer.camera(pt -> {
					Vec3 body = p.getPosition(pt).add(0, 0.6, 0);
					Vec3 pos = body.add(-5.2, 1.7, 2.4 - 1.2 * Math.sin(t * 0.6));
					Vec3 d = body.add(4, 0, 0).subtract(pos);
					float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
					float pitch = (float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);
					return new Trailer.Cam(pos, yaw, pitch, 78, true);
				});
			}
			@Override
			void end(Minecraft mc) {
				mc.player.stopFallFlying();
				mc.player.getAbilities().flying = true;
			}
		});
	}

	static void peaks(Trailer tr, Minecraft mc) {
		location(tr, mc, PEAKS[0], PEAKS[1], SUNSET);
		Vec3[] summit = new Vec3[1];
		tr.add(1, () -> {
			hud(mc, false);
			int bx = PEAKS[0];
			int bz = PEAKS[1];
			int best = -1;
			for (int dx = -64; dx <= 64; dx += 4) {
				for (int dz = -64; dz <= 64; dz += 4) {
					int h = (int) surface(mc, PEAKS[0] + dx, PEAKS[1] + dz);
					if (h > best) {
						best = h;
						bx = PEAKS[0] + dx;
						bz = PEAKS[1] + dz;
					}
				}
			}
			summit[0] = new Vec3(bx + 0.5, best, bz + 0.5);
			System.out.println("TEMPLE_TRAILER summit " + summit[0]);
			mc.player.connection.sendCommand("tp @s " + bx + " " + (best + 20) + " " + bz);
		});
		tr.add(80, () -> { });
		tr.record(mc, new Trailer.Shot("peaks", 7) {
			@Override
			void update(Minecraft mc, double t) {
				if (summit[0] == null) {
					return;
				}
				double k = ease(t / seconds);
				double a = Math.toRadians(200 + 35 * k);
				double r = 58 - 10 * k;
				Vec3 pos = summit[0].add(Math.cos(a) * r, -6 + 5 * k, Math.sin(a) * r);
				lookAt(pos, summit[0].add(0, -4, 0), 64);
			}
		});
	}
	static void esp(Trailer tr, Minecraft mc) {
		location(tr, mc, CHERRY[0] - 30, CHERRY[1] - 20, 18000);
		tr.add(1, () -> client("showcaseEnable", "Waypoints", false));
		Vec3[] at = new Vec3[1];
		tr.add(1, () -> {
			hud(mc, false);
			for (String m : List.of("Player Aura", "Pets", "Cape", "Kill Aura", "Target ESP", "Criticals")) {
				client("showcaseEnable", m, false);
			}
			int x = CHERRY[0] - 30;
			int z = CHERRY[1] - 12;
			int y = (int) surface(mc, x, z) - 7;
			at[0] = new Vec3(x + 0.5, y, z + 0.5);
			var c = mc.player.connection;
			c.sendCommand("fill " + (x - 6) + " " + (y - 1) + " " + (z - 5) + " " + (x + 6) + " " + (y + 3) + " " + (z + 5) + " minecraft:air");
			c.sendCommand("fill " + (x - 5) + " " + y + " " + (z - 4) + " " + (x + 5) + " " + y + " " + (z - 4) + " minecraft:chest[facing=south]");
			c.sendCommand("fill " + (x - 5) + " " + (y + 1) + " " + (z - 4) + " " + (x + 5) + " " + (y + 1) + " " + (z - 4) + " minecraft:barrel");
			c.sendCommand("fill " + (x - 5) + " " + y + " " + (z + 4) + " " + (x + 5) + " " + y + " " + (z + 4) + " minecraft:chest[facing=north]");
			String[] shulkers = {"purple", "yellow", "black", "white", "orange", "light_blue", "pink"};
			for (int i = 0; i < shulkers.length; i++) {
				c.sendCommand("setblock " + (x - 3 + i) + " " + y + " " + z + " minecraft:" + shulkers[i] + "_shulker_box");
			}
			c.sendCommand("setblock " + (x + 6) + " " + y + " " + (z + 5) + " minecraft:ender_chest");
		});
		tr.add(10, () -> {
			client("showcaseSet", "Storage ESP", "Style", "Box");
			client("showcaseSet", "Storage ESP", "Fill opacity", "30");
			client("showcaseEnable", "Storage ESP", true);
			client("showcaseSet", "Storage ESP", "Tracers", "false");
		});
		tr.record(mc, new Trailer.Shot("esp", 6) {
			@Override
			Vec3 subject(Minecraft mc, float pt) {
				return at[0] == null ? null : at[0].add(0, 1, 0);
			}
			@Override
			void update(Minecraft mc, double t) {
				if (at[0] == null) {
					return;
				}
				double k = ease(t / seconds);
				Vec3 target = at[0].add(0, 1, 0);
				double a = Math.toRadians(60 + 45 * k);
				double r = 15 - 4 * k;
				double top = surface(mc, target.x, target.z);
				Vec3 pos = new Vec3(target.x + Math.cos(a) * r, top + 12 - 3 * k, target.z + Math.sin(a) * r);
				lookAt(pos, target, 66);
			}
			@Override
			void end(Minecraft mc) {
				client("showcaseEnable", "Storage ESP", false);
			}
		});
	}

	static void breakBlocks(Trailer tr, Minecraft mc) {
		location(tr, mc, CHERRY[0] + 40, CHERRY[1] + 30, SUNSET);
		Vec3[] at = new Vec3[1];
		stage(tr, mc, CHERRY[0] + 40, CHERRY[1] + 30, at);
		tr.command(mc, "kill @e[type=minecraft:mannequin]");
		tr.command(mc, "gamemode survival");
		tr.command(mc, "clear @s");
		tr.command(mc, "item replace entity @s hotbar.0 with minecraft:netherite_pickaxe");
		BlockPos[] ores = new BlockPos[3];
		tr.add(1, () -> {
			for (String m : List.of("Player Aura", "Pets", "Cape")) {
				client("showcaseEnable", m, false);
			}
			hud(mc, false);
			BlockPos base = BlockPos.containing(at[0]).offset(0, 0, 2);
			String[] kinds = {"deepslate_diamond_ore", "deepslate_emerald_ore", "deepslate_gold_ore"};
			for (int i = 0; i < 3; i++) {
				ores[i] = base.offset(i - 1, 0, 0);
				mc.player.connection.sendCommand("setblock " + ores[i].getX() + " " + ores[i].getY() + " " + ores[i].getZ() + " minecraft:" + kinds[i]);
			}
			client("showcaseSet", "Break Animation", "Style", "Chip");
			client("showcaseEnable", "Break Animation", true);
			mc.player.getInventory().setSelectedSlot(0);
		});
		tr.add(10, () -> { });
		tr.record(mc, new Trailer.Shot("break", 4) {
			@Override
			Vec3 subject(Minecraft mc, float pt) {
				int i = Math.max(0, Math.min(ores.length - 1, current));
				return ores[i] == null ? null : Vec3.atCenterOf(ores[i]);
			}
			int current = -1;
			int lastTick = -1;

			@Override
			void begin(Minecraft mc) {
				current = 0;
			}
			@Override
			void update(Minecraft mc, double t) {
				Vec3 mid = at[0].add(0, 0.6, 2.5);
				Vec3 pos = mid.add(3.2 - 1.2 * ease(t / seconds), 0.9, -1.6);
				lookAt(pos, mid, 55);
				if (current < 0 || current >= ores.length) {
					return;
				}
				BlockPos ore = ores[current];
				if (mc.level.getBlockState(ore).isAir()) {
					mc.gameMode.stopDestroyBlock();
					current++;
					return;
				}
				mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(ore));
				if (mc.player.tickCount == lastTick) {
					return;
				}
				lastTick = mc.player.tickCount;
				mc.gameMode.continueDestroyBlock(ore, Direction.NORTH);
				mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
			}
			@Override
			void end(Minecraft mc) {
				mc.gameMode.stopDestroyBlock();
				client("showcaseEnable", "Break Animation", false);
				for (BlockPos ore : ores) {
					mc.player.connection.sendCommand("setblock " + ore.getX() + " " + ore.getY() + " " + ore.getZ() + " minecraft:air");
				}
			}
		});
		tr.command(mc, "gamemode creative");
	}
	static void skies(Trailer tr, Minecraft mc) {
		location(tr, mc, CHERRY[0], CHERRY[1], SUNSET);
		tr.add(1, () -> hud(mc, false));
		for (String take : List.of("Tendrils", "Silk", "Void", "Plasma", "Tendrils/gold", "Silk/gold", "Plasma/gold")) {
			String style = take.split("/")[0];
			boolean gold = take.endsWith("/gold");
			tr.add(1, () -> {
				client("showcaseSet", "Skybox", "Style", style);
				client("showcaseSet", "Skybox", "Use sky colors", String.valueOf(gold));
				client("showcaseEnable", "Skybox", true);
			});
			tr.add(10, () -> { });
			tr.record(mc, new Trailer.Shot("sky-" + take.replace('/', '-').toLowerCase(java.util.Locale.ROOT), 0.5) {
				@Override
				void update(Minecraft mc, double t) {
					double ground = surface(mc, CHERRY[0] - 10, CHERRY[1]);
					TrailerShots.cam(new Vec3(CHERRY[0] - 10, ground + 10, CHERRY[1]), -50, -8, 75);
				}
			});
		}
		tr.add(1, TrailerScenes::trailerSky);
	}
	static void emoteLab(Trailer tr, Minecraft mc) {
		location(tr, mc, CHERRY[0] - 30, CHERRY[1] - 20, SUNSET);
		Vec3[] at = new Vec3[1];
		tr.command(mc, "gamemode creative");
		stage(tr, mc, CHERRY[0] - 30, CHERRY[1] - 20, at);
		tr.add(1, () -> {
			hud(mc, false);
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			for (String m : List.of("Player Aura", "Pets", "Cape", "Chams")) {
				client("showcaseEnable", m, false);
			}
			client("showcaseSet", "Emotes", "Stop when moving", "false");
			client("showcaseSet", "Emotes", "Loop", "true");
			client("showcaseSet", "Emotes", "Face the camera", "false");
		});
		for (String emote : List.of("Jerk Off", "Dance", "Floss")) {
			tr.add(1, () -> {
				client("showcaseEnable", "Emotes", false);
				client("showcaseSet", "Emotes", "Emote", emote);
				mc.player.setYRot(0);
			});
			tr.record(mc, new Trailer.Shot("emote-" + emote.toLowerCase(java.util.Locale.ROOT).replace(' ', '-'), 2.4) {
				{
					cue(0.0, () -> client("showcaseEnable", "Emotes", true));
				}
				@Override
				void update(Minecraft mc, double t) {
					Vec3 body = at[0].add(0, 1.0, 0);
					lookAt(body.add(1.2, 0.2, 3.4), body, 60);
				}
			});
		}
		tr.add(1, () -> client("showcaseEnable", "Emotes", false));
	}
	static void closeUp(Trailer tr, Minecraft mc, Vec3[] at) {
		location(tr, mc, CHERRY[0] - 30, CHERRY[1] - 20, SUNSET);
		tr.command(mc, "gamemode creative");
		tr.command(mc, "clear @s");
		stage(tr, mc, CHERRY[0] - 30, CHERRY[1] - 20, at);
		tr.add(1, () -> {
			hud(mc, false);
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			for (String m : List.of("Player Aura", "Pets", "Cape", "Chams", "Trails", "Footsteps", "Jump Circles", "Emotes")) {
				client("showcaseEnable", m, false);
			}
		});
	}
	static void warmUp(Trailer tr, String... modules) {
		Minecraft mc = Minecraft.getInstance();
		tr.add(20, () -> {
			mc.player.getAbilities().flying = false;
			mc.player.onUpdateAbilities();
		});
		tr.add(80, () -> {
			System.out.println("TEMPLE_TRAILER warm-up grounded " + mc.player.onGround() + " flying " + mc.player.getAbilities().flying);
			for (String m : modules) {
				client("showcaseRealism", m, true);
			}
		});
		tr.add(10, () -> TrailerShots.originals(modules));
	}
	static void chams(Trailer tr, Minecraft mc) {
		Vec3[] at = new Vec3[1];
		closeUp(tr, mc, at);
		tr.add(1, () -> {
			client("showcaseSet", "Chams", "Self", "true");
			client("showcaseSet", "Chams", "Style", "Astral");
			client("showcaseSet", "Chams", "Outline", "true");
			client("showcaseSet", "Chams", "Outline width", "5");
			client("showcaseEnable", "Chams", true);
			client("showcaseEnable", "Cape", true);
		});
		tr.add(10, () -> { });
		tr.record(mc, new Trailer.Shot("chams", 2.6) {
			@Override
			Vec3 subject(Minecraft mc, float pt) {
				return mc.player.getPosition(pt).add(0, 1.0, 0);
			}
			{
				String[] styles = {"Void", "Plasma", "Hologram"};
				for (int i = 0; i < styles.length; i++) {
					String st = styles[i];
					cue(0.2 + (i + 1) * BEAT, () -> client("showcaseSet", "Chams", "Style", st));
				}
			}
			@Override
			void update(Minecraft mc, double t) {
				Vec3 body = at[0].add(0, 1.0, 0);
				double k = ease(t / seconds);
				double a = Math.toRadians(40 + 60 * k);
				lookAt(body.add(Math.cos(a) * 3.2, 0.3, Math.sin(a) * 3.2), body.add(0, 0.05, 0), 56);
			}
			@Override
			void end(Minecraft mc) {
				client("showcaseEnable", "Chams", false);
				client("showcaseSet", "Chams", "Self", "false");
			}
		});
	}
	static void aura(Trailer tr, Minecraft mc) {
		Vec3[] at = new Vec3[1];
		closeUp(tr, mc, at);
		tr.add(1, () -> {
			for (String part : List.of("Wings", "Halo", "Crystals", "Shield shell", "Horns", "Ears", "Ribbons")) {
				client("showcaseSet", "Player Aura", part, "false");
			}
			for (String part : List.of("Crown", "Gyroscope", "Orbiting blades", "Rune circle", "Spirits")) {
				client("showcaseSet", "Player Aura", part, "true");
			}
			client("showcaseEnable", "Player Aura", true);
		});
		warmUp(tr, "Player Aura");
		tr.record(mc, new Trailer.Shot("aura", 3) {
			{
				TrailerShots.flicker(this, 0.3, BEAT / 2, 4, "Player Aura");
			}
			@Override
			Vec3 subject(Minecraft mc, float pt) {
				return mc.player.getPosition(pt).add(0, 1.0, 0);
			}
			@Override
			void update(Minecraft mc, double t) {
				Vec3 body = at[0].add(0, 1.0, 0);
				double k = ease(t / seconds);
				double a = Math.toRadians(200 + 70 * k);
				lookAt(body.add(Math.cos(a) * 4.0, 1.2 - 0.6 * k, Math.sin(a) * 4.0), body, 60);
			}
			@Override
			void end(Minecraft mc) {
				client("showcaseEnable", "Player Aura", false);
				for (String part : List.of("Crown", "Gyroscope", "Orbiting blades", "Rune circle", "Spirits")) {
					client("showcaseSet", "Player Aura", part, "false");
				}
				client("showcaseSet", "Player Aura", "Wings", "true");
				client("showcaseSet", "Player Aura", "Halo", "true");
				client("showcaseSet", "Player Aura", "Spirits", "true");
			}
		});
	}
	static void trails(Trailer tr, Minecraft mc) {
		Vec3[] at = new Vec3[1];
		closeUp(tr, mc, at);
		tr.command(mc, "gamemode survival");
		tr.add(1, () -> {
			client("showcaseSet", "Trails", "Type", "Afterimages");
			client("showcaseSet", "Trails", "Afterimage glow", "true");
			client("showcaseEnable", "Trails", true);
			client("showcaseSet", "Footsteps", "Style", "Petals");
			client("showcaseEnable", "Footsteps", true);
			client("showcaseEnable", "Cape", true);
			mc.player.connection.sendCommand("tp @s " + (at[0].x - 6) + " " + at[0].y + " " + at[0].z + " -90 0");
		});
		tr.add(20, () -> { });
		tr.record(mc, new Trailer.Shot("trails", 3) {
			@Override
			Vec3 subject(Minecraft mc, float pt) {
				return mc.player.getPosition(pt).add(0, 1.0, 0);
			}
			int lastTick = -1;
			boolean live;
			@Override
			void begin(Minecraft mc) {
				live = true;
			}
			@Override
			void update(Minecraft mc, double t) {
				var p = mc.player;
				Trailer.camera(pt -> {
					Vec3 pos = at[0].add(1.5, 1.1, -4.2);
					Vec3 d = p.getPosition(pt).add(0.8, 0.9, 0).subtract(pos);
					float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
					float pitch = (float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);
					return new Trailer.Cam(pos, yaw, pitch, 64, true);
				});
				if (!live || p.tickCount == lastTick) {
					return;
				}
				lastTick = p.tickCount;
				p.setYRot(-90);
				p.setSprinting(true);
				p.setDeltaMovement(0.26, p.getDeltaMovement().y, 0);
			}
			@Override
			void end(Minecraft mc) {
				live = false;
				mc.player.setSprinting(false);
				client("showcaseEnable", "Trails", false);
				client("showcaseEnable", "Footsteps", false);
			}
		});
		tr.command(mc, "gamemode creative");
	}

	static void place(Trailer tr, Minecraft mc) {
		Vec3[] at = new Vec3[1];
		closeUp(tr, mc, at);
		tr.add(1, () -> {
			client("showcaseSet", "Place Animation", "Style", "Pop");
			client("showcaseSet", "Place Animation", "Other players", "true");
			client("showcaseEnable", "Place Animation", true);
		});
		tr.add(10, () -> { });
		tr.record(mc, new Trailer.Shot("place", 3) {
			{
				String[] blocks = {"gold_block", "yellow_stained_glass", "gold_block", "yellow_stained_glass", "gold_block", "yellow_stained_glass"};
				int[][] cells = {{-1, 0}, {0, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};
				for (int i = 0; i < cells.length; i++) {
					int[] c = cells[i];
					String block = blocks[i];
					cue(0.2 + i * BEAT / 2, () -> mc.player.connection.sendCommand("setblock " + ((int) Math.floor(at[0].x) + c[0]) + " "
							+ ((int) at[0].y + c[1]) + " " + ((int) Math.floor(at[0].z) + 3) + " minecraft:" + block));
				}
			}
			@Override
			void update(Minecraft mc, double t) {
				Vec3 wall = at[0].add(0, 1.0, 3.5);
				lookAt(wall.add(2.2, 0.6, -3.4), wall, 58);
			}
			@Override
			void end(Minecraft mc) {
				client("showcaseEnable", "Place Animation", false);
				mc.player.connection.sendCommand("fill " + ((int) Math.floor(at[0].x) - 1) + " " + (int) at[0].y + " " + ((int) Math.floor(at[0].z) + 3) + " "
						+ ((int) Math.floor(at[0].x) + 1) + " " + ((int) at[0].y + 1) + " " + ((int) Math.floor(at[0].z) + 3) + " minecraft:air");
			}
		});
	}
	static void hero(Trailer tr, Minecraft mc) {
		Vec3[] at = new Vec3[1];
		closeUp(tr, mc, at);
		tr.add(1, () -> {
			for (String part : List.of("Halo", "Crystals", "Shield shell", "Horns", "Ears", "Ribbons", "Gyroscope", "Orbiting blades", "Spirits")) {
				client("showcaseSet", "Player Aura", part, "false");
			}
			for (String part : List.of("Wings", "Crown", "Rune circle")) {
				client("showcaseSet", "Player Aura", part, "true");
			}
			client("showcaseEnable", "Player Aura", true);
			client("showcaseSet", "Pets", "Pet", "Jellyfish");
			client("showcaseSet", "Pets", "Position", "Orbit");
			client("showcaseSet", "Pets", "Size", "1");
			client("showcaseEnable", "Pets", true);
			client("showcaseEnable", "Cape", true);
		});
		warmUp(tr, "Player Aura", "Pets");
		tr.record(mc, new Trailer.Shot("hero", 3) {
			{
				TrailerShots.flicker(this, 0.4, BEAT, 2, "Player Aura", "Pets");
			}
			@Override
			Vec3 subject(Minecraft mc, float pt) {
				return mc.player.getPosition(pt).add(0, 1.0, 0);
			}
			@Override
			void update(Minecraft mc, double t) {
				Vec3 body = at[0].add(0, 1.1, 0);
				double k = ease(t / seconds);
				double a = Math.toRadians(-60 + 80 * k);
				lookAt(body.add(Math.cos(a) * (3.6 - 0.4 * k), -0.5 + 0.5 * k, Math.sin(a) * (3.6 - 0.4 * k)), body.add(0, 0.3, 0), 62);
			}
			@Override
			void end(Minecraft mc) {
				client("showcaseEnable", "Player Aura", false);
				client("showcaseEnable", "Pets", false);
				client("showcaseSet", "Player Aura", "Crown", "false");
				client("showcaseSet", "Player Aura", "Rune circle", "false");
				client("showcaseSet", "Player Aura", "Halo", "true");
				client("showcaseSet", "Player Aura", "Spirits", "true");
			}
		});
	}
	static void hands(Trailer tr, Minecraft mc) {
		Vec3[] at = new Vec3[1];
		closeUp(tr, mc, at);
		tr.command(mc, "item replace entity @s hotbar.0 with minecraft:netherite_sword");
		tr.add(1, () -> {
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			hud(mc, true);
			TrailerShots.hudParts(false, false, false, false);
			mc.player.getInventory().setSelectedSlot(0);
			client("showcaseSet", "Hand Chams", "Hand style", "Plasma");
			client("showcaseSet", "Hand Chams", "Use theme colour", "true");
			client("showcaseSet", "Hand Chams", "Weapon style", "Electric");
			client("showcaseEnable", "Hand Chams", true);
			client("showcaseSet", "Hand Tweaks", "Afterimages", "true");
			client("showcaseSet", "Hand Tweaks", "Frame gap", "5");
			client("showcaseSet", "Hand Tweaks", "Trail length", "0.18");
			client("showcaseEnable", "Hand Tweaks", true);
		});
		tr.add(10, () -> { });
		tr.record(mc, new Trailer.Shot("hands", 3) {
			{
				cue(0.08, () -> mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND));
				cue(0.08 + 1.5 * BEAT, () -> mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND));
			}
			@Override
			void update(Minecraft mc, double t) {
				Trailer.camera((Trailer.Cam) null);
				mc.player.setYRot((float) (20 + 12 * ease(t / seconds)));
				mc.player.setXRot((float) (8 - 4 * t / seconds));
				mc.player.yRotO = mc.player.getYRot();
				mc.player.xRotO = mc.player.getXRot();
			}
			@Override
			void end(Minecraft mc) {
				client("showcaseEnable", "Hand Chams", false);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			}
		});
	}

	static void dummy(Minecraft mc, Vec3 at, String extra) {
		mc.player.connection.sendCommand("summon minecraft:mannequin " + at.x + " " + at.y + " " + at.z
				+ " {CustomName:\"xNotch\",hide_description:1b,immovable:1b,Rotation:[180f,0f],"
				+ "equipment:{head:{id:\"minecraft:diamond_helmet\"},chest:{id:\"minecraft:diamond_chestplate\"},"
				+ "legs:{id:\"minecraft:diamond_leggings\"},feet:{id:\"minecraft:diamond_boots\"},mainhand:{id:\"minecraft:diamond_sword\"}" + extra + "}}");
	}

	static void deathTest(Trailer tr, Minecraft mc) {
		location(tr, mc, CHERRY[0] + 40, CHERRY[1] + 30, SUNSET);
		Vec3[] at = new Vec3[1];
		stage(tr, mc, CHERRY[0] + 40, CHERRY[1] + 30, at);
		tr.command(mc, "kill @e[type=minecraft:mannequin]");
		tr.add(1, () -> {
			hud(mc, false);
			client("showcaseSet", "Death Effects", "Who", "Everything");
			client("showcaseEnable", "Death Effects", true);
		});
		for (String combo : List.of("Ghost/Glass", "Ghost/Glow")) {
			String style = combo.split("/")[0];
			String material = combo.split("/")[1];
			tr.add(1, () -> {
				client("showcaseSet", "Death Effects", "Style", style);
				client("showcaseSet", "Death Effects", "Material", material);
				client("showcaseSet", "Death Effects", "Pieces", "700");
				client("showcaseSet", "Death Effects", "Crumble time", "1.8");
			});
			tr.add(2, () -> dummy(mc, at[0].add(0, 0, 3), ""));
			tr.add(10, () -> { });
			tr.record(mc, new Trailer.Shot("death-" + combo.replace('/', '-').toLowerCase(java.util.Locale.ROOT), 3.2) {
				{
					cue(0.3, () -> TrailerShots.deathAt(mc, nearestFoe(mc)));
				}
				@Override
				void update(Minecraft mc, double t) {
					Vec3 body = at[0].add(0, 1.0, 3);
					lookAt(body.add(1.7, 0.2, -1.1), body.add(0, 0.1, 0), 58);
				}
			});
		}
	}
	static void emotes(Trailer tr, Minecraft mc) {
		location(tr, mc, CHERRY[0] - 30, CHERRY[1] - 20, SUNSET);
		Vec3[] at = new Vec3[1];
		tr.command(mc, "gamemode creative");
		tr.command(mc, "clear @s");
		stage(tr, mc, CHERRY[0] - 30, CHERRY[1] - 20, at);
		tr.add(10, () -> {
			hud(mc, true);
			TrailerShots.hudParts(false, false, false, false);
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			for (String m : List.of("Player Aura", "Pets", "Criticals", "Kill Aura")) {
				client("showcaseEnable", m, false);
			}
			client("showcaseEnable", "Cape", true);
			client("showcaseEnable", "Chams", false);
			client("showcaseSet", "Emotes", "Stop when moving", "false");
			client("showcaseSet", "Emotes", "Loop", "true");
			client("showcaseEnable", "Emotes", true);
			client("showcaseEnable", "Emotes", false);
		});
		tr.record(mc, new Trailer.Shot("emotes", 5) {
			@Override
			Vec3 subject(Minecraft mc, float pt) {
				return mc.player.getPosition(pt).add(0, 1.0, 0);
			}
			{
				cue(0.05, () -> client("showcaseWheel", "press", 0f));
				cue(0.3, () -> client("showcaseWheel", "scroll", -1f));
				cue(0.3 + 0.75 * BEAT, () -> client("showcaseWheel", "scroll", 1f));
				double[] angles = {90, 45, 0};
				for (int i = 0; i < angles.length; i++) {
					float a = (float) angles[i];
					cue(0.3 + 1.5 * BEAT + i * BEAT / 2, () -> {
						for (int k = 0; k < 4; k++) {
							client("showcaseWheel", "point", a);
						}
					});
				}
				cue(0.3 + 3 * BEAT, () -> client("showcaseWheel", "release", 0f));
			}
			float head = Float.NaN;
			float bodyYaw;
			@Override
			void update(Minecraft mc, double t) {
				if (Float.isNaN(head)) {
					head = mc.player.getYHeadRot();
					bodyYaw = mc.player.yBodyRot;
				}
				mc.player.setYRot(head);
				mc.player.setYHeadRot(head);
				mc.player.yHeadRotO = head;
				mc.player.yRotO = head;
				mc.player.yBodyRot = bodyYaw;
				mc.player.yBodyRotO = bodyYaw;
				Vec3 body = at[0].add(0, 1.1, 0);
				double k = ease(t / seconds);
				double a = Math.toRadians(70 + 40 * k);
				Vec3 pos = body.add(Math.cos(a) * (4.2 - 0.6 * k), 0.35, Math.sin(a) * (4.2 - 0.6 * k));
				lookAt(pos, body.add(0, 0.1, 0), 58);
			}
			@Override
			void end(Minecraft mc) {
				client("showcaseEnable", "Emotes", false);
			}
		});
	}
	static void menuToggles(Trailer tr, Minecraft mc) {
		location(tr, mc, CHERRY[0], CHERRY[1], SUNSET);
		tr.add(1, () -> {
			hud(mc, true);
			TrailerShots.hudParts(false, false, false, false);
			for (String m : List.of("Chams", "Target ESP", "Jump Circles", "Trails", "Player Aura", "Pets")) {
				client("showcaseEnable", m, false);
			}
		});

		double[][] rows = {row(3, 5), row(3, 25), row(4, 7), row(4, 8), row(4, 15), row(4, 28)};
		double s0 = 0.15;
		CursorPath path = new CursorPath().at(onBeat(s0, 1.2), 0.93, 0.95);
		for (int i = 0; i < rows.length; i++) {
			double click = 3 + i * 2;
			path.at(onBeat(s0, click - 0.9), rows[i]).at(onBeat(s0, click + 0.4), rows[i]);
		}
		path.at(8.5, rows[rows.length - 1]);
		tr.record(mc, new Trailer.Shot("menutoggles", 8.5) {
			{
				cue(onBeat(s0, 0) - 0.05, () -> client("openMenu", mc.gui.screen()));
				for (int i = 0; i < rows.length; i++) {
					press(this, mc, onBeat(s0, 3 + i * 2), 0);
				}
			}
			@Override
			void update(Minecraft mc, double t) {
				menuBackdrop(mc, t);
				drive(mc, path, t);
			}
			@Override
			void end(Minecraft mc) {
				mc.gui.setScreen(null);
				TrailerShots.hideCursor();
				for (String m : List.of("Chams", "Target ESP", "Jump Circles", "Trails", "Player Aura", "Pets")) {
					client("showcaseEnable", m, false);
				}
			}
		});
	}

	static void breakStyles(Trailer tr, Minecraft mc) {
		location(tr, mc, CHERRY[0] + 40, CHERRY[1] + 30, SUNSET);
		Vec3[] at = new Vec3[1];
		stage(tr, mc, CHERRY[0] + 40, CHERRY[1] + 30, at);
		tr.command(mc, "kill @e[type=minecraft:mannequin]");
		tr.command(mc, "gamemode survival");
		tr.command(mc, "clear @s");
		tr.command(mc, "item replace entity @s hotbar.0 with minecraft:netherite_pickaxe");
		String[] styles = {"Liquid", "Brackets", "Nuker"};
		BlockPos[] ores = new BlockPos[3];
		tr.add(1, () -> {
			for (String m : List.of("Player Aura", "Pets", "Cape")) {
				client("showcaseEnable", m, false);
			}
			hud(mc, false);
			BlockPos base = BlockPos.containing(at[0]).offset(0, 0, 2);
			String[] kinds = {"deepslate_gold_ore", "deepslate_diamond_ore", "amethyst_block"};
			for (int i = 0; i < 3; i++) {
				ores[i] = base.offset(i - 1, 0, 0);
				mc.player.connection.sendCommand("setblock " + ores[i].getX() + " " + ores[i].getY() + " " + ores[i].getZ() + " minecraft:" + kinds[i]);
			}
			client("showcaseSet", "Break Animation", "Style", styles[0]);
			client("showcaseEnable", "Break Animation", true);
			mc.player.getInventory().setSelectedSlot(0);
		});
		tr.add(10, () -> { });
		tr.record(mc, new Trailer.Shot("breakstyles", 4.5) {
			int current = -1;
			int lastTick = -1;
			@Override
			void begin(Minecraft mc) {
				current = 0;
			}
			@Override
			Vec3 subject(Minecraft mc, float pt) {
				return Vec3.atCenterOf(ores[Math.max(0, Math.min(2, current))]);
			}
			@Override
			void update(Minecraft mc, double t) {
				Vec3 mid = at[0].add(0, 0.6, 2.5);
				lookAt(mid.add(-2.8 + 1.2 * ease(t / seconds), 0.9, -1.6), mid, 55);
				if (current < 0 || current >= ores.length) {
					return;
				}
				BlockPos ore = ores[current];
				if (mc.level.getBlockState(ore).isAir()) {
					mc.gameMode.stopDestroyBlock();
					current++;
					if (current < ores.length) {
						client("showcaseSet", "Break Animation", "Style", styles[current]);
					}
					return;
				}
				mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(ore));
				if (mc.player.tickCount == lastTick) {
					return;
				}
				lastTick = mc.player.tickCount;
				mc.gameMode.continueDestroyBlock(ore, Direction.NORTH);
				mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
			}
			@Override
			void end(Minecraft mc) {
				mc.gameMode.stopDestroyBlock();
				client("showcaseEnable", "Break Animation", false);
				client("showcaseSet", "Break Animation", "Style", "Chip");
				for (BlockPos ore : ores) {
					mc.player.connection.sendCommand("setblock " + ore.getX() + " " + ore.getY() + " " + ore.getZ() + " minecraft:air");
				}
			}
		});
		tr.command(mc, "gamemode creative");
	}
	static void hudShot(Trailer tr, Minecraft mc) {
		location(tr, mc, CHERRY[0], CHERRY[1], SUNSET);
		tr.command(mc, "gamemode creative");
		tr.command(mc, "clear @s");
		tr.command(mc, "item replace entity @s armor.head with minecraft:netherite_helmet");
		tr.command(mc, "item replace entity @s armor.chest with minecraft:netherite_chestplate");
		tr.command(mc, "item replace entity @s armor.legs with minecraft:netherite_leggings");
		tr.command(mc, "item replace entity @s armor.feet with minecraft:netherite_boots");
		tr.command(mc, "item replace entity @s hotbar.0 with minecraft:netherite_sword");
		tr.command(mc, "effect give @s speed 60 1 true");
		tr.command(mc, "effect give @s strength 60 0 true");
		Vec3[] start = new Vec3[1];
		tr.add(1, () -> {
			for (String m : List.of("Player Aura", "Pets", "Cape", "Trails", "Footsteps", "Jump Circles", "Emotes", "Storage ESP")) {
				client("showcaseEnable", m, false);
			}
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			hud(mc, true);
			client("showcaseSet", "HUD", "Module list", "true");
			for (String part : new String[] {"Keybinds", "Speed", "Speed graph", "Potions", "Armor", "Watermark"}) {
				client("showcaseSet", "HUD", part, "true");
			}
			client("showcaseSet", "HUD", "Coordinates", "false");
			for (String m : List.of("Criticals", "Waving Foliage", "Skybox")) {
				client("showcaseEnable", m, true);
			}
			client("showcaseBind", "Kill Aura", 82);
			client("showcaseBind", "Freelook", 342);
			mc.player.getInventory().setSelectedSlot(0);
			double ground = surface(mc, CHERRY[0] - 24, CHERRY[1] + 16);
			start[0] = new Vec3(CHERRY[0] - 24, ground + 7, CHERRY[1] + 16);
			mc.player.connection.sendCommand("tp @s " + start[0].x + " " + start[0].y + " " + start[0].z + " -120 14");
		});
		tr.add(30, () -> { });
		tr.record(mc, new Trailer.Shot("hud", 4) {
			int lastTick = -1;
			@Override
			void update(Minecraft mc, double t) {
				Trailer.camera((Trailer.Cam) null);
				var p = mc.player;
				p.getAbilities().flying = true;
				p.setYRot((float) (-120 + 22 * ease(t / seconds)));
				p.setXRot((float) (14 - 4 * ease(t / seconds)));
				if (p.tickCount == lastTick) {
					return;
				}
				lastTick = p.tickCount;
				p.setSprinting(true);
				double yaw = Math.toRadians(p.getYRot());
				p.setDeltaMovement(-Math.sin(yaw) * 0.35, 0, Math.cos(yaw) * 0.35);
			}
			@Override
			void end(Minecraft mc) {
				mc.player.setSprinting(false);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				for (String m : List.of("Criticals")) {
					client("showcaseEnable", m, false);
				}
				TrailerShots.hudParts(false, false, false, false);
			}
		});
		tr.command(mc, "clear @s");
		tr.command(mc, "effect clear @s");
	}
	static void zoom(Trailer tr, Minecraft mc) {
		location(tr, mc, CHERRY[0], CHERRY[1], SUNSET);
		tr.add(1, () -> {
			hud(mc, false);
			double ground = surface(mc, CHERRY[0] - 10, CHERRY[1]);
			mc.player.connection.sendCommand("tp @s " + (CHERRY[0] - 10) + " " + (ground + 6) + " " + CHERRY[1] + " -40 12");
			mc.options.setCameraType(CameraType.FIRST_PERSON);
		});
		tr.add(30, () -> { });
		tr.record(mc, new Trailer.Shot("zoom", 3.4) {
			{
				cue(0.3, () -> client("showcaseEnable", "Zoom", true));
				cue(0.3 + BEAT, () -> client("showcaseZoom", 6));
				cue(0.3 + 3 * BEAT, () -> client("showcaseZoom", 6));
				cue(0.3 + 5.5 * BEAT, () -> client("showcaseEnable", "Zoom", false));
			}
			@Override
			void update(Minecraft mc, double t) {
				Trailer.camera((Trailer.Cam) null);
				mc.player.setYRot(-40);
				mc.player.setXRot(12);
			}
			@Override
			void end(Minecraft mc) {
				client("showcaseEnable", "Zoom", false);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			}
		});
	}
	static void skySwap(Trailer tr, Minecraft mc) {
		location(tr, mc, CHERRY[0], CHERRY[1], SUNSET);
		tr.add(1, () -> {
			hud(mc, false);
			trailerSky();
		});
		tr.add(10, () -> { });
		tr.record(mc, new Trailer.Shot("skyswap", 3.2) {
			{

				String[] skies = {"Tendrils", "Silk", "Void"};
				for (int i = 0; i < skies.length; i++) {
					String sky = skies[i];
					cue(0.15 + (i + 1) * BEAT / 2, () -> {
						client("showcaseSet", "Skybox", "Use sky colors", "false");
						client("showcaseSet", "Skybox", "Style", sky);
					});
				}
				cue(0.15 + 2 * BEAT, TrailerScenes::trailerSky);
			}
			@Override
			void update(Minecraft mc, double t) {
				double k = ease(t / seconds);
				double ground = surface(mc, CHERRY[0] - 10, CHERRY[1]);
				TrailerShots.cam(new Vec3(CHERRY[0] - 10, ground + 6, CHERRY[1]), (float) (-40 + 12 * k), (float) (-4 - 22 * k), 76);
			}
			@Override
			void end(Minecraft mc) {
				trailerSky();
			}
		});
	}
	static final String[] WORLD_EFFECTS = {"Midnight", "Golden hour", "Blood moon", "Arctic"};
	static void ambience(Trailer tr, Minecraft mc) {
		location(tr, mc, CHERRY[0], CHERRY[1], SUNSET);
		tr.add(1, () -> {
			hud(mc, false);
			client("showcaseEnable", "Skybox", false);
			mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.FANCY);
			client("showcaseSet", "Ambience", "Custom fog", "false");
			client("showcaseSet", "Ambience", "Sky", "Vanilla");
			client("showcaseSet", "Ambience", "Clouds", "Vanilla");
			client("showcaseSet", "Ambience", "Star brightness", "100");
			client("showcaseSet", "Ambience", "Intensity", "100");
			client("showcaseSet", "Ambience", "World effect", WORLD_EFFECTS[0]);
			client("showcaseEnable", "Ambience", true);
		});
		tr.add(20, () -> { });
		tr.record(mc, new Trailer.Shot("ambience", 1.4) {
			{
				for (int i = 1; i < WORLD_EFFECTS.length; i++) {
					String effect = WORLD_EFFECTS[i];
					cue(0.2 + i * BEAT / 2, () -> client("showcaseSet", "Ambience", "World effect", effect));
				}
			}
			@Override
			void update(Minecraft mc, double t) {
				double k = ease(t / seconds);
				double ground = surface(mc, CHERRY[0] - 10, CHERRY[1]);
				TrailerShots.cam(new Vec3(CHERRY[0] - 10 + 3 * k, ground + 5 + 1.5 * k, CHERRY[1] - 2 * k), (float) (-55 + 18 * k), (float) (-3 - 7 * k), 76);
			}
			@Override
			void end(Minecraft mc) {
				client("showcaseEnable", "Ambience", false);
				client("showcaseSet", "Ambience", "World effect", "None");
				client("showcaseEnable", "Skybox", true);
				mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
			}
		});
	}
	static void arrows(Trailer tr, Minecraft mc) {
		Vec3[] at = new Vec3[1];
		closeUp(tr, mc, at);
		tr.command(mc, "gamemode spectator");
		tr.add(1, () -> {
			client("showcaseSet", "Projectile Trails", "Style", "Comet");
			client("showcaseSet", "Projectile Trails", "Length", "1.6");
			client("showcaseSet", "Projectile Trails", "Width", "0.24");
			client("showcaseSet", "Projectile Trails", "Impact burst", "true");
			client("showcaseEnable", "Projectile Trails", true);
			mc.player.connection.sendCommand("kill @e[type=minecraft:arrow]");
		});
		tr.add(10, () -> { });
		tr.record(mc, new Trailer.Shot("arrows", 3.2) {
			{
				double[][] shots = {{8, 2.6, 6, -0.95, 0.32, 0.05}, {8, 2.9, 8, -1.0, 0.3, 0.02}, {8, 2.4, 5, -0.9, 0.35, 0.08}, {8, 2.7, 7, -1.02, 0.28, 0.04}};
				for (int i = 0; i < shots.length; i++) {
					double[] a = shots[i];
					cue(0.15 + i * BEAT, () -> mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT,
							"summon minecraft:arrow %.2f %.2f %.2f {Motion:[%.3f,%.3f,%.3f],pickup:0b}",
							at[0].x + a[0], at[0].y + a[1], at[0].z + a[2], a[3], a[4], a[5])));
				}
			}
			@Override
			Vec3 subject(Minecraft mc, float pt) {
				return at[0].add(1, 2.2, 8);
			}
			@Override
			void update(Minecraft mc, double t) {
				double k = ease(t / seconds);
				lookAt(at[0].add(-0.5 + 0.8 * k, 1.3, -1.5), at[0].add(1, 2.2, 8), 70);
			}
			@Override
			void end(Minecraft mc) {
				client("showcaseEnable", "Projectile Trails", false);
				mc.player.connection.sendCommand("kill @e[type=minecraft:arrow]");
			}
		});
		tr.command(mc, "gamemode creative");
	}
	static void totem(Trailer tr, Minecraft mc) {
		location(tr, mc, CHERRY[0] + 40, CHERRY[1] + 30, SUNSET);
		Vec3[] at = new Vec3[1];
		stage(tr, mc, CHERRY[0] + 40, CHERRY[1] + 30, at);
		tr.command(mc, "kill @e[type=minecraft:mannequin]");
		tr.add(1, () -> {
			hud(mc, false);
			client("showcaseSet", "Totem Pops", "Who", "Everything");
			client("showcaseSet", "Totem Pops", "Spirit", "false");
			client("showcaseSet", "Totem Pops", "Size", "1.4");
			client("showcaseEnable", "Totem Pops", true);
			client("showcaseSet", "Death Effects", "Style", DEATH_STYLE);
			client("showcaseEnable", "Death Effects", true);
			dummy(mc, at[0].add(0, 0, 3), ",offhand:{id:\"minecraft:totem_of_undying\"}");
		});
		tr.add(10, () -> { });
		tr.record(mc, new Trailer.Shot("totem", 3.5) {
			@Override
			Vec3 subject(Minecraft mc, float pt) {
				return at[0].add(0, 1.0, 3);
			}
			{
				cue(0.4, () -> mc.player.connection.sendCommand("damage @e[type=minecraft:mannequin,limit=1,sort=nearest] 100 minecraft:player_attack by @s"));
				cue(0.4 + 4 * BEAT, () -> TrailerShots.deathAt(mc, nearestFoe(mc)));
			}
			@Override
			void update(Minecraft mc, double t) {
				Vec3 body = at[0].add(0, 1.0, 3);
				double k = ease(t / seconds);
				lookAt(body.add(3.2 - 0.8 * k, 0.5, -2.4 + 0.4 * k), body.add(0, 0.3, 0), 58);
			}
		});
	}
	static void storm(Trailer tr, Minecraft mc) {
		storm(tr, mc, STORM_SKY, "storm");
	}

	static final String STORM_SKY = "Storm";
	static void storm(Trailer tr, Minecraft mc, String sky, String name) {
		location(tr, mc, CHERRY[0] - 30, CHERRY[1] - 20, 11600);
		Vec3[] at = new Vec3[1];
		stage(tr, mc, CHERRY[0] - 30, CHERRY[1] - 20, at);
		tr.command(mc, "gamemode spectator");
		tr.add(1, () -> {
			hud(mc, false);
			mc.player.connection.sendCommand("weather thunder");
			client("showcaseSet", "Waving Foliage", "Strength", "3");
			client("showcaseSet", "Waving Foliage", "Speed", "2.2");
			client("showcaseSet", "Waving Foliage", "Gusts", "100");
			client("showcaseSet", "Wet Ground", "Puddles", "90");
			client("showcaseSet", "Wet Ground", "Shine", "100");
			client("showcaseEnable", "Wet Ground", true);
			client("showcaseEnable", "Storm Effects", true);
			client("showcaseSet", "Ambience", "Weather", "Vanilla");
			for (String m : List.of("Pets", "Player Aura", "Cape", "Chams")) {
				client("showcaseEnable", m, false);
			}
			client("showcaseSet", "Skybox", "Style", sky);
			client("showcaseSet", "Skybox", "Use sky colors", "false");
			client("showcaseEnable", "Skybox", true);
		});
		tr.add(60, () -> { });
		tr.record(mc, new Trailer.Shot(name, 4) {
			@Override
			Vec3 subject(Minecraft mc, float pt) {
				return at[0] == null ? null : at[0].add(20, 5, 0);
			}
			{
				double[][] hits = {{16, 1.2}, {21, -1.5}, {24, 0.4}};
				for (int i = 0; i < hits.length; i++) {
					double[] h = hits[i];
					cue(0.35 + i * 2 * BEAT, () -> mc.player.connection.sendCommand("summon lightning_bolt " + (at[0].x + h[0]) + " "
							+ (int) surface(mc, at[0].x + h[0], at[0].z + h[1]) + " " + (at[0].z + h[1])));
				}
			}

			@Override
			void update(Minecraft mc, double t) {
				if (at[0] == null) {
					return;
				}
				double k = ease(t / seconds);
				Vec3 pos = at[0].add(4, 0.9, -1.2 + 1.2 * k);
				lookAt(pos, at[0].add(20, 7.5, 0), 78);
			}
			@Override
			void end(Minecraft mc) {
				trailerSky();
				client("showcaseEnable", "Storm Effects", false);
				client("showcaseEnable", "Wet Ground", false);
				client("showcaseSet", "Waving Foliage", "Strength", "1");
				client("showcaseSet", "Waving Foliage", "Speed", "1");
				client("showcaseSet", "Waving Foliage", "Gusts", "60");
				mc.player.connection.sendCommand("weather clear");
			}
		});
	}

	static void explosion(Trailer tr, Minecraft mc) {
		location(tr, mc, CHERRY[0] + 140, CHERRY[1] + 20, SUNSET);
		Vec3[] at = new Vec3[1];
		stage(tr, mc, CHERRY[0] + 140, CHERRY[1] + 20, at);
		tr.add(1, () -> {
			hud(mc, false);
			client("showcaseEnable", "Explosion Effects", true);
			mc.player.connection.sendCommand("summon end_crystal " + at[0].x + " " + at[0].y + " " + (at[0].z + 7) + " {ShowBottom:0b}");
		});
		tr.add(10, () -> { });
		tr.record(mc, new Trailer.Shot("explosion", 3) {
			{
				cue(0.5, () -> mc.player.connection.sendCommand("damage @e[type=minecraft:end_crystal,limit=1,sort=nearest] 1"));
			}
			@Override
			void update(Minecraft mc, double t) {
				Vec3 target = at[0].add(0, 1.4, 7);
				lookAt(at[0].add(4.5 - t * 0.4, 2.2, -1.5), target, 64);
			}
			@Override
			void end(Minecraft mc) {
				client("showcaseEnable", "Explosion Effects", false);
			}
		});
	}

	static void jumps(Trailer tr, Minecraft mc) {
		location(tr, mc, CHERRY[0] - 30, CHERRY[1] - 20, SUNSET);
		Vec3[] at = new Vec3[1];
		stage(tr, mc, CHERRY[0] - 30, CHERRY[1] - 20, at);
		tr.command(mc, "gamemode survival");
		tr.add(1, () -> {
			hud(mc, false);
			client("showcaseSet", "Trails", "Type", "Ribbon");
			client("showcaseEnable", "Trails", true);
			client("showcaseEnable", "Jump Circles", true);
			client("showcaseEnable", "Cape", true);
			client("showcaseSet", "Chams", "Style", "Plasma");
			client("showcaseSet", "Chams", "Self", "true");
			client("showcaseEnable", "Chams", true);
			mc.player.connection.sendCommand("tp @s " + (at[0].x - 5) + " " + at[0].y + " " + at[0].z + " -90 0");
		});
		tr.add(20, () -> { });
		tr.record(mc, new Trailer.Shot("jumps", 3.5) {
			@Override
			Vec3 subject(Minecraft mc, float pt) {
				return mc.player.getPosition(pt).add(0, 1.0, 0);
			}
			int lastTick = -1;
			boolean live;
			@Override
			void begin(Minecraft mc) {
				live = true;
			}
			@Override
			void update(Minecraft mc, double t) {
				var p = mc.player;
				Trailer.camera(pt -> {
					Vec3 body = p.getPosition(pt).add(0, 0.9, 0);
					Vec3 pos = body.add(-1.5, 0.8, -4.6);
					Vec3 d = body.add(1.2, 0, 0).subtract(pos);
					float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
					float pitch = (float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);
					return new Trailer.Cam(pos, yaw, pitch, 66, true);
				});
				if (!live || p.tickCount == lastTick) {
					return;
				}
				lastTick = p.tickCount;
				p.setYRot(-90);
				p.setSprinting(true);
				p.setDeltaMovement(0.28, p.getDeltaMovement().y, Math.sin(t * 2.2) * 0.06);
				if (p.onGround()) {
					p.jumpFromGround();
				}
			}
			@Override
			void end(Minecraft mc) {
				live = false;
				mc.player.setSprinting(false);
				client("showcaseEnable", "Trails", false);
				client("showcaseEnable", "Jump Circles", false);
				client("showcaseEnable", "Chams", false);
				client("showcaseSet", "Chams", "Self", "false");
			}
		});
		tr.command(mc, "gamemode creative");
	}

	static void aurora(Trailer tr, Minecraft mc) {
		location(tr, mc, CHERRY[0] - 30, CHERRY[1] - 20, 18000);
		Vec3[] at = new Vec3[1];
		stage(tr, mc, CHERRY[0] - 30, CHERRY[1] - 20, at);
		tr.add(1, () -> {
			hud(mc, false);
			client("showcaseSet", "Aurora", "Crown", "true");
			client("showcaseEnable", "Aurora", true);
			client("showcaseEnable", "Cape", true);
		});
		tr.add(20, () -> { });
		tr.record(mc, new Trailer.Shot("aurora", 3.5) {
			@Override
			void update(Minecraft mc, double t) {
				Vec3 body = at[0].add(0, 1.2, 0);
				double k = ease(t / seconds);
				double a = Math.toRadians(200 + 50 * k);
				Vec3 pos = body.add(Math.cos(a) * 7, 0.6 + 2.2 * k, Math.sin(a) * 7);
				lookAt(pos, body.add(0, 1.2 + 0.8 * k, 0), 70);
			}
			@Override
			void end(Minecraft mc) {
				client("showcaseEnable", "Aurora", false);
			}
		});
		tr.command(mc, "time set " + SUNSET);
	}
	static void themes(Trailer tr, Minecraft mc) {
		location(tr, mc, CHERRY[0], CHERRY[1], SUNSET);
		tr.add(1, () -> {
			hud(mc, true);
			TrailerShots.hudParts(false, false, false, false);
		});
		tr.record(mc, new Trailer.Shot("themes", 3.2) {
			{
				cue(0.0, () -> client("openMenu", mc.gui.screen()));
				int[] order = {1, 2, 3, 4, 5, 0};
				for (int i = 0; i < order.length; i++) {
					int theme = order[i];
					cue(0.3 + i * BEAT, () -> client("showcaseTheme", theme));
				}
			}
			@Override
			void update(Minecraft mc, double t) {
				menuBackdrop(mc, 4 + t);
			}
			@Override
			void end(Minecraft mc) {
				client("showcaseTheme", 0);
				mc.gui.setScreen(null);
			}
		});
	}
	@SuppressWarnings("unused")
	private static LivingEntity nearest(Minecraft mc, Class<? extends Entity> type) {
		LivingEntity best = null;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (type.isInstance(e) && e instanceof LivingEntity l && l != mc.player
					&& (best == null || l.distanceToSqr(mc.player) < best.distanceToSqr(mc.player))) {
				best = l;
			}
		}
		return best;
	}
	@SuppressWarnings("unused")
	private static void mine(Minecraft mc, BlockPos pos) {
		mc.gameMode.continueDestroyBlock(pos, Direction.NORTH);
	}
}
