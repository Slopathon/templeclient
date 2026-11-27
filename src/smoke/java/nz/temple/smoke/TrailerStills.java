package nz.temple.smoke;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Consumer;

import static nz.temple.smoke.TrailerShots.client;
import static nz.temple.smoke.TrailerShots.ease;
import static nz.temple.smoke.TrailerShots.hud;
import static nz.temple.smoke.TrailerShots.location;
import static nz.temple.smoke.TrailerShots.lookAt;

final class TrailerStills {
	private TrailerStills() {
	}
	private static final int SX = 30000;
	private static final int SZ = 30000;
	private static final int FLOOR = 200;
	private static final String BACKDROP = "2B2A30";
	static final List<String> COSMETICS = List.of("Hit Particles", "Jump Circles", "Trails", "Death Effects", "Air Effects",
			"Block Effects", "Place Animation", "Place Preview", "Player Aura", "Cape", "Damage Effects",
			"Footsteps", "Projectile Trails", "Pickup Streaks", "Teleport Effects", "Hit Markers", "Contrails", "Aurora", "Pets",
			"Walk Style", "Emotes", "Break Animation", "Explosion Effects", "Storm Effects", "Vitals", "Hand Tweaks", "Spinbot");

	private static final List<String> NOT_COSMETICS = List.of("Target ESP", "Kill Aura", "Criticals", "Chams", "Hand Chams",
			"Crystal Chams", "Nametags", "Damage Numbers", "Totem Pops", "Trajectories", "Animations", "Motion Blur", "Wet Ground");
	static String slug(String module) {
		return "still-" + module.toLowerCase(java.util.Locale.ROOT).replace(" ", "-");
	}
	private static boolean wanted(Trailer tr, String module) {
		return tr.wanted("still-all") || tr.wanted(slug(module));
	}
	static void all(Trailer tr, Minecraft mc) {
		if (tr.only.isBlank() || COSMETICS.stream().noneMatch(m -> wanted(tr, m))) {
			return;
		}
		Vec3 at = new Vec3(SX + 0.5, FLOOR + 1, SZ + 0.5);
		studio(tr, mc);
		for (String module : COSMETICS) {
			if (wanted(tr, module)) {
				reset(tr, mc, at);
				take(tr, mc, module, at);
			}
		}
		reset(tr, mc, at);
		tr.add(1, () -> {
			client("showcaseEnable", "Ambience", false);
			client("showcaseEnable", "Skybox", true);
		});
	}
	private static void studio(Trailer tr, Minecraft mc) {
		location(tr, mc, SX, SZ, 6000);
		tr.command(mc, "fill " + (SX - 32) + " " + FLOOR + " " + (SZ - 32) + " " + (SX + 32) + " " + FLOOR + " " + (SZ + 32)
				+ " minecraft:light_gray_concrete");
		tr.command(mc, "gamerule doDaylightCycle false");
		tr.command(mc, "gamerule do_daylight_cycle false");
		tr.command(mc, "gamerule doWeatherCycle false");
		tr.command(mc, "gamerule do_weather_cycle false");
		tr.command(mc, "gamerule announceAdvancements false");
		tr.command(mc, "gamerule announce_advancements false");
		tr.command(mc, "advancement grant @s everything");
		tr.command(mc, "recipe give @s *");
		tr.add(1, () -> {
			client("showcaseEnable", "Skybox", false);
			client("showcaseEnable", "Waving Foliage", false);
			look(BACKDROP, "5", "22", "100");
			client("showcaseSet", "Ambience", "Custom fog", "true");
			client("showcaseSet", "Ambience", "Sky", "Custom");
			client("showcaseSet", "Ambience", "Clouds", "Hidden");
			client("showcaseSet", "Ambience", "Star brightness", "0");
			client("showcaseSet", "Ambience", "Weather", "Hidden");
			client("showcaseEnable", "Ambience", true);
			TrailerShots.hudParts(false, false, false, false);
			client("showcaseSet", "HUD", "Watermark", "false");
			client("showcaseSet", "HUD", "Notifications", "false");
		});
	}
	private static void look(String colour, String start, String end, String thickness) {
		client("showcaseSet", "Ambience", "Fog color", colour);
		client("showcaseSet", "Ambience", "Sky color", colour);
		client("showcaseSet", "Ambience", "Fog start", start);
		client("showcaseSet", "Ambience", "Fog end", end);
		client("showcaseSet", "Ambience", "Fog thickness", thickness);
	}
	private static void reset(Trailer tr, Minecraft mc, Vec3 at) {
		tr.add(1, () -> {
			var server = mc.getSingleplayerServer();
			server.execute(() -> TrailerDummy.clear(server));
		});
		tr.command(mc, "kill @e[type=!minecraft:player]");
		tr.command(mc, "fill " + (SX - 14) + " " + (FLOOR - 4) + " " + (SZ - 14) + " " + (SX + 14) + " " + (FLOOR + 8) + " " + (SZ + 14)
				+ " minecraft:air");
		tr.command(mc, "fill " + (SX - 32) + " " + FLOOR + " " + (SZ - 32) + " " + (SX + 32) + " " + FLOOR + " " + (SZ + 32)
				+ " minecraft:light_gray_concrete");
		tr.command(mc, "clear @s");
		tr.command(mc, "gamemode creative");
		tr.command(mc, "effect clear @s");
		tr.command(mc, "effect give @s minecraft:instant_health 1 10 true");
		tr.command(mc, "weather clear");
		tr.command(mc, "time set 6000");
		tr.command(mc, "tp @s " + at.x + " " + at.y + " " + at.z + " 0 0");
		tr.add(1, () -> {
			for (String m : COSMETICS) {
				client("showcaseEnable", m, false);
			}
			for (String m : NOT_COSMETICS) {
				client("showcaseEnable", m, false);
			}
			look(BACKDROP, "5", "22", "100");
			client("showcaseSet", "Ambience", "Weather", "Hidden");
			mc.player.getAbilities().flying = false;
			mc.player.setDeltaMovement(Vec3.ZERO);
			mc.player.setSprinting(false);
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			hud(mc, false);
		});
		tr.add(12, () -> { });
	}
	private static void orbit(Vec3 c, double deg, double dist, double height, float fov) {
		double a = Math.toRadians(deg);
		lookAt(c.add(Math.cos(a) * dist, height, Math.sin(a) * dist), c, fov);
	}

	private static void firstPerson(Minecraft mc) {
		Trailer.camera((Trailer.Cam) null);
		mc.options.setCameraType(CameraType.FIRST_PERSON);
		hud(mc, true);
	}
	private static void dummy(Minecraft mc, Vec3 p, float health) {
		mc.player.connection.sendCommand("summon minecraft:mannequin " + p.x + " " + p.y + " " + p.z
				+ " {CustomName:\"xNotch\",hide_description:1b,immovable:1b,Rotation:[180f,0f],Health:" + health + "f,"
				+ "attributes:[{id:\"minecraft:max_health\",base:" + Math.max(20, health) + "}],"
				+ "equipment:{head:{id:\"minecraft:diamond_helmet\"},chest:{id:\"minecraft:diamond_chestplate\"},"
				+ "legs:{id:\"minecraft:diamond_leggings\"},feet:{id:\"minecraft:diamond_boots\"},"
				+ "mainhand:{id:\"minecraft:diamond_sword\"}}}");
	}
	private static void hit(Minecraft mc) {
		Entity foe = TrailerScenes.nearestFoe(mc);
		if (foe != null) {
			mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, foe.getEyePosition());
			mc.gameMode.attack(mc.player, foe);
			mc.player.swing(InteractionHand.MAIN_HAND);
		}
	}
	private static void faceFoe(Minecraft mc) {
		Entity foe = TrailerScenes.nearestFoe(mc);
		if (foe != null) {
			mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, foe.getEyePosition().add(0, -0.3, 0));
			mc.player.yRotO = mc.player.getYRot();
			mc.player.xRotO = mc.player.getXRot();
		}
	}
	private static void place(Minecraft mc, int x, int z) {
		BlockPos below = new BlockPos(x, FLOOR, z);
		mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
				new BlockHitResult(new Vec3(x + 0.5, FLOOR + 1, z + 0.5), Direction.UP, below, false));
		mc.player.swing(InteractionHand.MAIN_HAND);
	}
	interface Frame {
		void at(Minecraft mc, double t, double k);
	}
	private static void shoot(Trailer tr, Minecraft mc, String module, double seconds, Consumer<Trailer.Shot> cues, Frame frame) {
		tr.record(mc, new Trailer.Shot(slug(module), seconds) {
			{
				cues.accept(this);
			}
			@Override
			void update(Minecraft mc, double t) {
				frame.at(mc, t, ease(t / seconds));
			}
		});
	}
	private static void travel(Minecraft mc, double speed, boolean sprint) {
		mc.player.setYRot(-90);
		mc.player.yRotO = -90;
		mc.player.setSprinting(sprint);
		mc.player.setDeltaMovement(speed, mc.player.getDeltaMovement().y, 0);
	}
	private static void take(Trailer tr, Minecraft mc, String module, Vec3 at) {
		Vec3 body = at.add(0, 1.05, 0);
		int[] lastTick = {-1};
		java.util.function.BooleanSupplier newTick = () -> {
			if (mc.player.tickCount == lastTick[0]) {
				return false;
			}
			lastTick[0] = mc.player.tickCount;
			return true;
		};
		switch (module) {
			case "Player Aura" -> {
				tr.add(1, () -> {
					for (String part : List.of("Crown", "Gyroscope", "Crystals", "Shield shell", "Horns", "Ears", "Ribbons")) {
						client("showcaseSet", "Player Aura", part, "false");
					}
					for (String part : List.of("Wings", "Halo", "Spirits", "Rune circle", "Orbiting blades")) {
						client("showcaseSet", "Player Aura", part, "true");
					}
					client("showcaseSet", "Player Aura", "Material", "Glass");
					client("showcaseEnable", "Player Aura", true);
				});
				tr.add(20, () -> { });
				shoot(tr, mc, module, 2.4, s -> { }, (m, t, k) -> orbit(body.add(0, -0.05, 0), 68 + 24 * k, 3.9, 0.55, 46));
			}
			case "Cape" -> {
				tr.add(1, () -> {
					client("showcaseSet", "Cape", "Style", "Gradient");
					client("showcaseEnable", "Cape", true);
					mc.player.connection.sendCommand("tp @s " + (at.x - 2) + " " + at.y + " " + at.z + " -90 0");
				});
				tr.add(10, () -> { });
				shoot(tr, mc, module, 2.4, s -> { }, (m, t, k) -> {
					var p = m.player;
					Trailer.camera(pt -> {
						Vec3 me = p.getPosition(pt);
						Vec3 cam = me.add(-2.4, 1.5, -1.6);
						Vec3 d = me.add(0, 1.1, 0).subtract(cam);
						return new Trailer.Cam(cam, (float) (Math.atan2(d.z, d.x) * 180 / Math.PI) - 90f,
								(float) -(Math.atan2(d.y, d.horizontalDistance()) * 180 / Math.PI), 46, true);
					});
					if (newTick.getAsBoolean()) {
						travel(m, 0.14, false);
					}
				});
			}
			case "Pets" -> {
				tr.add(1, () -> {
					client("showcaseSet", "Pets", "Pet", "Dragon");
					client("showcaseSet", "Pets", "Position", "Orbit");
					client("showcaseSet", "Pets", "Size", "1.8");
					client("showcaseEnable", "Pets", true);
				});
				tr.add(30, () -> { });
				shoot(tr, mc, module, 2.4, s -> { }, (m, t, k) -> orbit(body.add(0, 0.45, 0), 58 + 26 * k, 3.2, 0.45, 48));
			}
			case "Emotes" -> {
				tr.add(1, () -> {
					client("showcaseSet", "Emotes", "Emote", "Dance");
					client("showcaseSet", "Emotes", "Loop", "true");
					client("showcaseSet", "Emotes", "Stop when moving", "false");
					client("showcaseEnable", "Emotes", true);
				});
				tr.add(10, () -> { });
				shoot(tr, mc, module, 2.4, s -> { }, (m, t, k) -> orbit(body, 74 + 18 * k, 3.0, 0.25, 46));
			}
			case "Spinbot" -> {
				tr.add(1, () -> client("showcaseEnable", "Spinbot", true));
				tr.add(10, () -> { });
				shoot(tr, mc, module, 2.4, s -> { }, (m, t, k) -> orbit(body, 72 + 18 * k, 3.0, 0.4, 46));
			}
			case "Air Effects" -> {
				tr.add(1, () -> {
					client("showcaseSet", "Air Effects", "Snow", "true");
					client("showcaseSet", "Air Effects", "Fireflies", "false");
					client("showcaseEnable", "Air Effects", true);
				});
				tr.add(40, () -> { });
				shoot(tr, mc, module, 2.4, s -> { }, (m, t, k) -> orbit(body.add(0, 0.3, 0), 64 + 20 * k, 3.6, 0.4, 50));
			}
			case "Aurora" -> {
				tr.command(mc, "time set 18000");
				tr.add(1, () -> {
					look("0B0B12", "20", "90", "35");
					client("showcaseEnable", "Aurora", true);
				});
				tr.add(40, () -> { });
				shoot(tr, mc, module, 2.4, s -> { },
						(m, t, k) -> lookAt(at.add(1.5, 0.6, -5.5 + 0.8 * k), at.add(0, 3.2 + 0.5 * k, 2), 72));
			}
			case "Jump Circles" -> {
				tr.add(1, () -> client("showcaseEnable", "Jump Circles", true));
				tr.add(5, () -> { });
				shoot(tr, mc, module, 2.4, s -> {
					s.cue(0.15, () -> mc.player.jumpFromGround());
					s.cue(1.2, () -> mc.player.jumpFromGround());
				}, (m, t, k) -> orbit(at.add(0, 0.55, 0), 60 + 18 * k, 3.5, 1.0, 48));
			}
			case "Trails", "Footsteps", "Walk Style", "Contrails" -> {
				boolean sprint = !module.equals("Walk Style") && !module.equals("Footsteps");
				double speed = switch (module) {
					case "Contrails" -> 0.55;
					case "Trails" -> 0.28;
					default -> 0.13;
				};
				tr.add(1, () -> {
					switch (module) {
						case "Trails" -> {
							client("showcaseSet", "Trails", "Style", "Afterimages");
							client("showcaseSet", "Trails", "Afterimage glow", "true");
						}
						case "Footsteps" -> client("showcaseSet", "Footsteps", "Style", "Prints");
						case "Contrails" -> client("showcaseSet", "Contrails", "Style", "Streamers");
						case "Walk Style" -> client("showcaseSet", "Walk Style", "Style", "Swagger");
						default -> { }
					}
					client("showcaseEnable", module, true);
					double back = speed * 20 * 1.4;
					mc.player.connection.sendCommand("tp @s " + (at.x - back) + " " + (at.y + (module.equals("Contrails") ? 1.5 : 0))
							+ " " + at.z + " -90 0");
					if (module.equals("Contrails")) {
						mc.player.getAbilities().flying = true;
					}
				});
				tr.add(12, () -> { });
				Vec3 fixed = at.add(1.8, 1.1, -4.0);
				Vec3 ride = switch (module) {
					case "Footsteps" -> new Vec3(1.2, 0.9, -2.2);
					case "Contrails" -> new Vec3(1.4, 0.6, -3.2);
					default -> new Vec3(2.2, 0.9, 2.2);
				};
				shoot(tr, mc, module, 2.6, s -> { }, (m, t, k) -> {
					var p = m.player;
					Trailer.camera(pt -> {
						Vec3 me = p.getPosition(pt);
						Vec3 cam = module.equals("Trails") ? fixed : me.add(ride);
						Vec3 aim = module.equals("Footsteps") ? me.add(-1.2, 0.1, 0)
								: module.equals("Trails") ? me.add(0.4, 0.9, 0) : me.add(-0.4, 0.9, 0);
						Vec3 d = aim.subtract(cam);
						float yaw = (float) (Math.atan2(d.z, d.x) * 180 / Math.PI) - 90f;
						float pitch = (float) -(Math.atan2(d.y, d.horizontalDistance()) * 180 / Math.PI);
						return new Trailer.Cam(cam, yaw, pitch, module.equals("Trails") ? 58 : 50, true);
					});
					if (newTick.getAsBoolean()) {
						travel(m, speed, sprint);
					}
				});
			}
			case "Hit Particles" -> {
				tr.add(1, () -> {
					dummy(mc, at.add(0, 0, 2.3), 1000);
					client("showcaseSet", "Hit Particles", "Shock ring", "false");
					client("showcaseSet", "Hit Particles", "Sparks", "30");
					client("showcaseEnable", "Hit Particles", true);
				});
				tr.add(20, () -> { });
				shoot(tr, mc, module, 2.4, s -> {
					for (double c : new double[] {0.2, 0.7, 1.2, 1.7}) {
						s.cue(c, () -> hit(mc));
					}
				}, (m, t, k) -> orbit(at.add(0, 1.0, 1.15), -8 + 18 * k, 3.4, 0.3, 48));
			}
			case "Death Effects" -> {
				tr.add(1, () -> {
					client("showcaseSet", "Death Effects", "Who", "Everything");
					client("showcaseSet", "Death Effects", "Style", "Ghost");
					client("showcaseSet", "Death Effects", "Material", "Glow");
					client("showcaseEnable", "Death Effects", true);
					dummy(mc, at.add(0, 0, 2.5), 20);
				});
				tr.add(20, () -> { });
				shoot(tr, mc, module, 2.6, s -> s.cue(0.3, () -> TrailerShots.deathAt(mc, TrailerScenes.nearestFoe(mc))),
						(m, t, k) -> orbit(at.add(0, 1.0, 2.5), 22 + 16 * k, 3.5, 0.35, 48));
			}
			case "Damage Effects" -> {
				tr.add(1, () -> {
					dummy(mc, at.add(0, 0, 2.3), 1000);
					client("showcaseSet", "Damage Effects", "Style", "Shockwave");
					client("showcaseEnable", "Damage Effects", true);
				});
				tr.add(20, () -> { });
				shoot(tr, mc, module, 2.4, s -> {
					for (double c : new double[] {0.2, 0.9, 1.6}) {
						s.cue(c, () -> hit(mc));
					}
				}, (m, t, k) -> orbit(at.add(0, 1.0, 1.6), 18 + 16 * k, 3.6, 0.35, 48));
			}
			case "Hit Markers", "Vitals" -> {
				boolean hurt = module.equals("Vitals");
				if (hurt) {
					tr.command(mc, "gamemode survival");
				}
				tr.add(1, () -> {
					dummy(mc, at.add(0.4, 0, 2.6), 1000);
					if (module.equals("Hit Markers")) {
						client("showcaseSet", "Hit Markers", "Size", "14");
						client("showcaseSet", "Hit Markers", "Thickness", "4");
						client("showcaseSet", "Hit Markers", "Flakes", "36");
						client("showcaseSet", "Hit Markers", "Explosion spread", "48");
						client("showcaseSet", "Hit Markers", "Duration", "1.5");
					}
					if (module.equals("Vitals")) {
						client("showcaseSet", "Vitals", "Threshold", "20");
						client("showcaseSet", "Vitals", "Strength", "100");
					}
					client("showcaseEnable", module, true);
					firstPerson(mc);
				});
				tr.add(20, () -> { });
				shoot(tr, mc, module, 2.4, s -> {
					for (double c : new double[] {0.2, 0.7, 1.2, 1.7}) {
						s.cue(c, () -> {
							if (hurt) {
								mc.player.connection.sendCommand("damage @s 3 minecraft:generic");
							} else {
								hit(mc);
							}
						});
					}
				}, (m, t, k) -> faceFoe(m));
			}
			case "Hand Tweaks" -> {
				tr.command(mc, "item replace entity @s hotbar.0 with minecraft:golden_sword");
				tr.add(1, () -> {
					mc.player.getInventory().setSelectedSlot(0);
					client("showcaseSet", "Hand Tweaks", "Swing", "Smooth");
					client("showcaseSet", "Hand Tweaks", "Hold mid-swing", "false");
					client("showcaseEnable", "Hand Tweaks", true);
					firstPerson(mc);
				});
				tr.add(10, () -> { });
				shoot(tr, mc, module, 2.4, s -> {
					for (double c : new double[] {0.2, 0.7, 1.2, 1.7}) {
						s.cue(c, () -> mc.player.swing(InteractionHand.MAIN_HAND));
					}
				}, (m, t, k) -> {
					m.player.setYRot((float) (10 + 4 * k));
					m.player.setXRot((float) (4 - 2 * k));
					m.player.yRotO = m.player.getYRot();
					m.player.xRotO = m.player.getXRot();
				});
			}
			case "Place Preview" -> {
				tr.command(mc, "item replace entity @s hotbar.0 with minecraft:gold_block 64");
				tr.add(1, () -> {
					mc.player.getInventory().setSelectedSlot(0);
					client("showcaseEnable", "Place Preview", true);
					firstPerson(mc);
				});
				tr.add(10, () -> { });
				shoot(tr, mc, module, 2.4, s -> { }, (m, t, k) -> {
					m.player.setYRot((float) (-8 + 16 * k));
					m.player.setXRot(52);
					m.player.yRotO = m.player.getYRot();
					m.player.xRotO = m.player.getXRot();
				});
			}
			case "Place Animation" -> {
				tr.command(mc, "item replace entity @s hotbar.0 with minecraft:gold_block 64");
				tr.add(1, () -> {
					mc.player.getInventory().setSelectedSlot(0);
					client("showcaseEnable", "Place Animation", true);
				});
				tr.add(10, () -> { });
				int bx = (int) Math.floor(at.x);
				int bz = (int) Math.floor(at.z) + 2;
				shoot(tr, mc, module, 2.4, s -> {
					s.cue(0.15, () -> place(mc, bx - 1, bz));
					s.cue(0.55, () -> place(mc, bx, bz));
					s.cue(0.95, () -> place(mc, bx + 1, bz));
					s.cue(1.35, () -> place(mc, bx, bz + 1));
				}, (m, t, k) -> orbit(new Vec3(bx + 0.5, FLOOR + 1.3, bz + 0.8), 28 + 16 * k, 3.6, 1.1, 48));
			}
			case "Block Effects", "Break Animation" -> {
				boolean anim = module.equals("Break Animation");
				int bx = (int) Math.floor(at.x);
				int bz = (int) Math.floor(at.z) + 2;
				BlockPos block = new BlockPos(bx, FLOOR + 1, bz);
				tr.command(mc, "setblock " + bx + " " + (FLOOR + 1) + " " + bz + " minecraft:gold_block");
				if (anim) {
					tr.command(mc, "gamemode survival");
					tr.command(mc, "item replace entity @s hotbar.0 with minecraft:stone_pickaxe");
				}
				tr.add(1, () -> {
					mc.player.getInventory().setSelectedSlot(0);
					if (anim) {
						client("showcaseSet", "Break Animation", "Style", "Liquid");
					}
					client("showcaseEnable", module, true);
				});
				tr.add(10, () -> { });
				Vec3 centre = Vec3.atCenterOf(block);
				shoot(tr, mc, module, 2.4, s -> {
					if (!anim) {
						s.cue(0.5, () -> {
							mc.gameMode.startDestroyBlock(block, Direction.NORTH);
							mc.player.swing(InteractionHand.MAIN_HAND);
						});
					}
				}, (m, t, k) -> {
					orbit(centre, 35 + 12 * k, 2.6, 0.6, 46);
					if (anim && newTick.getAsBoolean() && !m.level.getBlockState(block).isAir()) {
						m.player.lookAt(EntityAnchorArgument.Anchor.EYES, centre);
						m.gameMode.continueDestroyBlock(block, Direction.NORTH);
						m.player.swing(InteractionHand.MAIN_HAND);
					}
				});
			}
			case "Explosion Effects" -> {
				Vec3 blast = at.add(0, 0, 4);
				tr.command(mc, "summon end_crystal " + blast.x + " " + blast.y + " " + blast.z + " {ShowBottom:0b}");
				tr.add(1, () -> client("showcaseEnable", "Explosion Effects", true));
				tr.add(10, () -> { });
				shoot(tr, mc, module, 2.4, s -> s.cue(0.35, () -> {
					for (Entity e : mc.level.entitiesForRendering()) {
						if (e instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal) {
							mc.gameMode.attack(mc.player, e);
						}
					}
				}), (m, t, k) -> orbit(blast.add(0, 1.5, 0), 115 + 8 * k, 9.5, 1.6, 58));
			}
			case "Storm Effects" -> {
				tr.command(mc, "weather thunder");
				tr.add(1, () -> {
					look("15151B", "10", "60", "70");
					client("showcaseEnable", "Storm Effects", true);
				});
				tr.add(40, () -> { });
				Vec3 bolt = at.add(2.5, 0, 6.5);
				shoot(tr, mc, module, 2.6, s -> {
					s.cue(0.3, () -> mc.player.connection.sendCommand("summon lightning_bolt " + bolt.x + " " + bolt.y + " " + bolt.z));
					s.cue(1.5, () -> mc.player.connection.sendCommand(
							"summon lightning_bolt " + (bolt.x - 5) + " " + bolt.y + " " + (bolt.z + 2)));
				}, (m, t, k) -> lookAt(at.add(-1.2, 1.4, -2.6), at.add(1.2 - k, 3.2, 6), 64));
			}
			case "Teleport Effects" -> {
				Vec3 from = at.add(-14, 0, 7);
				Vec3 to = at.add(2.5, 0, 3.4);
				tr.add(1, () -> {
					client("showcaseSet", "Teleport Effects", "Trigger distance", "3");
					client("showcaseEnable", "Teleport Effects", true);
					var server = mc.getSingleplayerServer();
					server.execute(() -> TrailerDummy.kit(TrailerDummy.spawn(server, "xPearl", from, 180).player, false));
				});
				tr.add(30, () -> { });
				shoot(tr, mc, module, 2.8, s -> s.cue(0.5, () -> {
					var server = mc.getSingleplayerServer();
					server.execute(() -> {
						for (TrailerDummy.Dummy d : TrailerDummy.ALL) {
							d.player.teleportTo(server.overworld(), to.x, to.y, to.z, java.util.Set.of(), 180, 0, true);
						}
					});
				}), (m, t, k) -> lookAt(at.add(-1.0, 1.7, -3.0), at.add(-0.5, 1.1, 4.2), 58));
			}
			case "Pickup Streaks" -> {
				tr.command(mc, "gamemode survival");
				tr.add(1, () -> client("showcaseEnable", "Pickup Streaks", true));
				tr.add(5, () -> { });
				shoot(tr, mc, module, 2.4, s -> {
					for (int i = 0; i < 6; i++) {
						double a = i * Math.PI / 3;
						double x = at.x + Math.cos(a) * 1.3;
						double z = at.z + Math.sin(a) * 1.3;
						s.cue(0.15 + i * 0.18, () -> mc.player.connection.sendCommand(
								"summon item " + x + " " + (at.y + 0.6) + " " + z + " {Item:{id:\"minecraft:gold_ingot\",count:3},PickupDelay:6}"));
					}
				}, (m, t, k) -> orbit(body, 62 + 18 * k, 3.1, 0.7, 48));
			}
			case "Projectile Trails" -> {
				tr.add(1, () -> {
					client("showcaseSet", "Projectile Trails", "Style", "Comet");
					client("showcaseSet", "Projectile Trails", "Impact burst", "true");
					client("showcaseEnable", "Projectile Trails", true);
				});
				tr.add(5, () -> { });
				shoot(tr, mc, module, 2.4, s -> {
					for (int i = 0; i < 3; i++) {
						double y = at.y + 1.3 + i * 0.35;
						s.cue(0.15 + i * 0.35, () -> mc.player.connection.sendCommand(
								"summon arrow " + (at.x - 7) + " " + y + " " + (at.z + 3) + " {Motion:[1.5,0.12,0.0]}"));
					}
				}, (m, t, k) -> lookAt(at.add(-3.0, 1.5, -0.2), at.add(1.5, 1.9, 3.2), 56));
			}
			default -> throw new IllegalStateException("no take for " + module);
		}
	}
}
