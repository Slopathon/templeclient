package nz.temple.smoke;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

final class Showcase {
	private record Step(int delay, Runnable action) {
	}
	private static final java.util.Set<String> SWEEP_SKIP = java.util.Set.of("Auto Leave", "Stash Disconnect");

	private static final java.util.Set<String> MENU_SKIP = java.util.Set.of("Freecam", "Freelook", "Zoom", "Blink", "Auto Leave",
			"Stash Disconnect", "Nuker", "Auto Tunnel", "Flatten", "Scaffold", "Tower", "Auto Walk", "Fly", "Noclip", "Speed",
			"Spider", "Anti AFK", "Parkour", "Long Jump", "High Jump", "TP Aura", "Kill Aura", "Aim Assist",
			"Trigger Bot", "Auto Clicker", "Target Strafe", "Pearl Trap", "Crystal Aura", "Anchor Aura", "Air Place",
			"Bed Breaker", "Vein Miner", "Auto Farm", "Pearl Follow", "Key Pearl", "Glide", "Boat Fly", "Elytra Boost");
	private final Deque<Step> steps = new ArrayDeque<>();
	private int stage;
	private int timer;
	private int waited;
	private Class<?> client;
	private int ticks;
	void tick(Minecraft mc) {
		mc.options.pauseOnLostFocus = false;
		if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen
				&& !System.getProperty("temple.showcase.only", "").contains("MusicMenus")) {
			mc.gui.setScreen(null);
		}
		if (++ticks % 100 == 0 && stage < 3) {
			var screen = mc.gui.screen();
			if (System.getProperty("temple.showcase.only", "").contains("FpsProbe")) {
				mc.options.enableVsync().set(false);
				mc.options.framerateLimit().set(260);
				mc.getFramerateLimitTracker().onInputReceived();
				System.out.println("TEMPLE_FPS stage " + stage + " fps " + mc.getFps() + " limit " + mc.getFramerateLimitTracker().getFramerateLimit()
						+ " reason " + mc.getFramerateLimitTracker().getThrottleReason());
			}
			System.out.println("TEMPLE_SHOWCASE stage " + stage + " screen " + (screen == null ? "none" : screen.getClass().getName())
					+ " overlay " + mc.gui.overlay() + " level " + (mc.level != null));
			if (screen != null && stage == 1) {
				for (var child : screen.children()) {
					System.out.println("  child " + child.getClass().getSimpleName()
							+ (child instanceof AbstractButton b ? " '" + b.getMessage().getString() + "'" : ""));
				}
			}
		}
		if (ticks > (System.getProperty("temple.showcase.only", "").contains("PerfProbe") ? 60000
				: System.getProperty("temple.showcase.only", "").contains("Modules") || System.getProperty("temple.showcase.only", "").contains("Gallery") ? 20000 : 6000)) {
			System.out.println("TEMPLE_SHOWCASE_TIMEOUT at stage " + stage);
			mc.stop();
			return;
		}
		try {
			switch (stage) {
				case 0 -> {
					var current = mc.gui.screen();
					boolean ready = current instanceof TitleScreen
							|| current instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
					if ((ready || menu45Tick > 0) && mc.gui.overlay() == null && System.getProperty("temple.showcase.only", "").contains("Corner45") && !menu45Done) {
						if (menu45Tick == 0 && current instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
							mc.options.onboardAccessibility = false;
							mc.gui.setScreen(new TitleScreen());
							return;
						}
						menu45Shots(mc);
						return;
					}
					if ((ready || menuTick > 0) && mc.gui.overlay() == null && System.getProperty("temple.showcase.only", "").contains("Menu44") && !menuDone) {
						if (menuTick == 0 && current instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
							mc.options.onboardAccessibility = false;
							mc.gui.setScreen(new TitleScreen());
							return;
						}
						menuShots(mc);
						return;
					}
					if (System.getProperty("temple.showcase.only", "").contains("TabLoad")) {
						long window = mc.getWindow().handle();
						if (loadTick == 0) {
							org.lwjgl.glfw.GLFW.glfwIconifyWindow(window);
							loadTick = 1;
						} else if (loadTick == 1) {
							if (mc.gui.overlay() == null && ++waited > 60) {
								System.out.println("TEMPLE_TABLOAD loaded while minimized, screen=" + mc.gui.screen());
								org.lwjgl.glfw.GLFW.glfwRestoreWindow(window);
								org.lwjgl.glfw.GLFW.glfwFocusWindow(window);
								loadTick = 2;
								tabTick = 150;
							}
						} else {
							tabMenuShots(mc);
						}
						return;
					}
					if ((ready || tabTick > 0) && System.getProperty("temple.showcase.only", "").contains("TabMenu")) {
						if (mc.gui.overlay() != null && tabTick == 0) {
							return;
						}
						if (tabTick == 0 && current instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
							mc.options.onboardAccessibility = false;
							mc.gui.setScreen(new TitleScreen());
							return;
						}
						tabMenuShots(mc);
						return;
					}
					if ((ready || titleTick > 0) && mc.gui.overlay() == null && System.getProperty("temple.showcase.only", "").contains("Title") && !titleDone) {
						if (titleTick == 0 && current instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
							mc.options.onboardAccessibility = false;
							mc.gui.setScreen(new TitleScreen());
							return;
						}
						titleShots(mc);
						return;
					}
					if (ready && mc.gui.overlay() == null && System.getProperty("temple.showcase.only", "").contains("GrimMove")
							&& ++waited > 20) {
						var data = new net.minecraft.client.multiplayer.ServerData("Grim test", "localhost:25599",
								net.minecraft.client.multiplayer.ServerData.Type.OTHER);
						net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new TitleScreen(), mc,
								net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(data.ip), data, false, null);
						stage = 2;
						waited = 0;
						return;
					}
					if (ready && mc.gui.overlay() == null && ++waited > (System.getProperty("temple.showcase.only", "").contains("FpsProbe") ? 400 : 20)) {
						if (System.getProperty("temple.showcase.only", "").contains("OreSim")) {
							CreateWorldScreen.openFresh(mc, () -> { });
						} else {
							CreateWorldScreen.testWorld(mc, () -> { });
						}
						stage = 1;
					}
				}
				case 1 -> {
					if (mc.gui.screen() instanceof CreateWorldScreen screen) {
						for (var child : screen.children()) {
							if (child instanceof AbstractButton button && button.getMessage().getString().toLowerCase().contains("create")) {
								button.onPress(null);
								stage = 2;
								waited = 0;
								break;
							}
						}
					}
				}
				case 2 -> {
					if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.DeathScreen && mc.player != null) {
						mc.player.respawn();
						mc.gui.setScreen(null);
						return;
					}
					if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.ConfirmScreen confirm) {
						for (var child : confirm.children()) {
							if (child instanceof AbstractButton button) {
								String label = button.getMessage().getString().toLowerCase();
								if (!label.contains("back") && !label.contains("cancel") && !label.equals("no")) {
									System.out.println("TEMPLE_SHOWCASE confirming '" + label + "'");
									button.onPress(null);
									break;
								}
							}
						}
						return;
					}
					if (mc.level != null && mc.player != null && mc.gui.screen() == null && ++waited > 80) {
						client = Class.forName("nz.temple.client.TempleNzClient");
						plan(mc);
						stage = 3;
					}
				}
				case 3 -> {
					if (timer > 0) {
						timer--;
						return;
					}
					Step step = steps.poll();
					if (step == null) {
						System.out.println("TEMPLE_SHOWCASE_DONE");
						mc.stop();
						stage = 4;
						return;
					}
					step.action().run();
					timer = step.delay();
				}
				default -> {
				}
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
	private boolean titleDone;
	private int titleTick;
	private boolean menu45Done;
	private int menu45Tick;
	private void menu45Shots(Minecraft mc) {
		menu45Tick++;
		java.util.function.Consumer<String> grab = name -> Screenshot.grab(mc.gameDirectory, "showcase-" + name + ".png",
				mc.gameRenderer.mainRenderTarget(), 1, c -> { });
		java.util.function.Consumer<String> screen = what -> System.out.println("TEMPLE_M45 " + what + " "
				+ (mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName()));
		try {
			client = Class.forName("nz.temple.client.TempleNzClient");
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		switch (menu45Tick) {
			case 1 -> {
				call("showcaseSet", "Menu", "Menu theme", "Landscape");
				mc.gui.setScreen(new TitleScreen());
			}
			case 70 -> grab.accept("m45-landscape");
			case 72 -> call("showcaseTitle", "button:corner:scene");
			case 77 -> grab.accept("m45-swap-dip");
			case 130 -> {
				screen.accept("after swap");
				grab.accept("m45-hole");
			}
			case 132 -> call("showcaseTitle", "button:corner:suggest");
			case 170 -> {
				screen.accept("after suggest");
				grab.accept("m45-suggest");
			}
			case 172 -> mc.gui.setScreen(new TitleScreen());
			case 200 -> call("showcaseTitle", "button:corner:scene");
			case 260 -> {
				grab.accept("m45-back-to-landscape");
				menu45Done = true;
				if (System.getProperty("temple.showcase.only", "").equals("Corner45")) {
					System.out.println("TEMPLE_SHOWCASE_DONE");
					mc.stop();
				}
			}
			default -> {
			}
		}
	}
	private boolean menuDone;
	private int menuTick;
	private void menuShots(Minecraft mc) {
		menuTick++;
		java.util.function.Consumer<String> grab = name -> Screenshot.grab(mc.gameDirectory, "showcase-" + name + ".png",
				mc.gameRenderer.mainRenderTarget(), 1, c -> { });
		java.util.function.Consumer<String> screen = what -> System.out.println("TEMPLE_MENU " + what + " "
				+ (mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName()));
		try {
			client = Class.forName("nz.temple.client.TempleNzClient");
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		switch (menuTick) {
			case 1 -> {
				mc.options.skipMultiplayerWarning = true;
				call("showcaseSet", "Menu", "Menu theme", "Landscape");
				call("showcaseSet", "Menu", "Menu sounds", "true");
				var list = new net.minecraft.client.multiplayer.ServerList(mc);
				list.load();
				if (list.get("offline.invalid") == null) {
					list.add(new net.minecraft.client.multiplayer.ServerData("Nowhere", "offline.invalid", net.minecraft.client.multiplayer.ServerData.Type.OTHER), false);
					list.save();
				}
				mc.gui.setScreen(new TitleScreen());
			}
			case 40 -> {
				screen.accept("start");
				System.out.println("TEMPLE_MENU sounds " + call("showcaseUiSounds"));
			}
			case 60 -> call("showcaseTitle", "sky:0.78,0.18");
			case 63 -> grab.accept("m44-sky-click");
			case 80 -> call("showcaseTitle", "servers");
			case 120 -> grab.accept("m44-servers");
			case 122 -> call("showcaseTitle", "button:Add server");
			case 132 -> {
				screen.accept("after Add server");
				grab.accept("m44-add-server");
			}
			case 134 -> mc.gui.setScreen(new TitleScreen());
			case 150 -> call("showcaseTitle", "servers");
			case 190 -> call("showcaseTitle", "button:Direct");
			case 196 -> screen.accept("after Direct");
			case 198 -> mc.gui.setScreen(new TitleScreen());
			case 214 -> call("showcaseTitle", "servers");
			case 254 -> call("showcaseTitle", "dblrow:offline.invalid");
			case 258 -> {
				screen.accept("after double-click");
				grab.accept("m44-join");
			}
			case 300 -> mc.gui.setScreen(new TitleScreen());
			case 316 -> call("showcaseTitle", "worlds");
			case 356 -> call("showcaseTitle", "button:Create world");
			case 380 -> {
				screen.accept("after Create world");
				grab.accept("m44-create-world");
			}
			case 384 -> mc.gui.setScreen(new TitleScreen());
			case 400 -> {
				call("showcaseSet", "Menu", "Menu theme", "Black hole");
				mc.gui.setScreen(new TitleScreen());
			}
			case 470 -> {
				screen.accept("black hole");
				grab.accept("m44-hole");
			}
			case 530 -> grab.accept("m44-hole-later");
			case 532 -> call("showcaseTitle", "servers");
			case 580 -> grab.accept("m44-hole-servers");
			case 582 -> call("showcaseTitle", "close");
			case 600 -> mc.gui.setScreen(new net.minecraft.client.gui.screens.options.OptionsScreen(mc.gui.screen(), mc.options, false));
			case 650 -> grab.accept("m44-hole-options");
			case 652 -> mc.gui.setScreen(new TitleScreen());
			case 680 -> {
				call("showcaseSet", "Menu", "Scene quality", "Low");
			}
			case 700 -> grab.accept("m44-hole-low");
			case 702 -> {
				call("showcaseSet", "Menu", "Scene quality", "High");
				call("showcaseSet", "Menu", "Menu theme", "Default");
				mc.gui.setScreen(mc.gui.screen());
			}
			case 730 -> {
				screen.accept("after Default");
				grab.accept("m44-default");
			}
			case 732 -> {
				call("showcaseSet", "Menu", "Menu theme", "Landscape");
				mc.gui.setScreen(new TitleScreen());
			}
			case 764 -> call("openMenu", mc.gui.screen());
			case 800 -> call("showcaseSettings", "Menu");
			case 850 -> grab.accept("m44-menu-settings");
			case 852 -> {
				screen.accept("after Landscape");
				menuDone = true;
				if (System.getProperty("temple.showcase.only", "").equals("Menu44")) {
					System.out.println("TEMPLE_SHOWCASE_DONE");
					mc.stop();
				}
			}
			default -> {
			}
		}
	}
	private int tabTick;
	private int loadTick;
	private void tabMenuShots(Minecraft mc) {
		tabTick++;
		long window = mc.getWindow().handle();
		switch (tabTick) {
			case 1 -> System.out.println("TEMPLE_TABMENU screen " + mc.gui.screen().getClass().getName());
			case 80 -> Screenshot.grab(mc.gameDirectory, "showcase-tabmenu-before.png", mc.gameRenderer.mainRenderTarget(), 1, c -> { });
			case 85 -> desktopShot(mc, "tabmenu-before-desktop");
			case 90 -> org.lwjgl.glfw.GLFW.glfwIconifyWindow(window);
			case 150 -> {
				org.lwjgl.glfw.GLFW.glfwRestoreWindow(window);
				org.lwjgl.glfw.GLFW.glfwFocusWindow(window);
			}
			case 210 -> {
				System.out.println("TEMPLE_TABMENU window " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight() + " target "
						+ mc.gameRenderer.mainRenderTarget().width + "x" + mc.gameRenderer.mainRenderTarget().height + " minimized="
						+ mc.getWindow().isMinimized() + " iconified=" + mc.getWindow().isIconified() + " focused=" + mc.getWindow().isFocused()
						+ " screen=" + mc.gui.screen());
				Screenshot.grab(mc.gameDirectory, "showcase-tabmenu-after.png", mc.gameRenderer.mainRenderTarget(), 1, c -> { });
			}
			case 215 -> desktopShot(mc, "tabmenu-after-desktop");
			case 230 -> {
				System.out.println("TEMPLE_SHOWCASE_DONE");
				mc.stop();
			}
			default -> { }
		}
	}
	private void titleShots(Minecraft mc) {
		titleTick++;
		java.util.function.Consumer<String> grab = name -> Screenshot.grab(mc.gameDirectory, "showcase-" + name + ".png",
				mc.gameRenderer.mainRenderTarget(), 1, c -> { });
		try {
			client = Class.forName("nz.temple.client.TempleNzClient");
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		switch (titleTick) {
			case 1 -> System.out.println("TEMPLE_TITLE screen " + mc.gui.screen().getClass().getName());
			case 40 -> call("showcaseTitle", "state");
			case 80 -> {
				grab.accept("title-idle");
				call("showcaseTitle", "state");
			}
			case 84 -> call("showcaseTitle", "hover:0.5,0.3");
			case 90 -> call("showcaseTitle", "jump");
			case 98 -> grab.accept("title-jump");
			case 130 -> {
				var list = new net.minecraft.client.multiplayer.ServerList(mc);
				list.load();
				if (list.size() == 0) {
					list.add(new net.minecraft.client.multiplayer.ServerData("DonutSMP", "donutsmp.net", net.minecraft.client.multiplayer.ServerData.Type.OTHER), false);
					list.add(new net.minecraft.client.multiplayer.ServerData("Hypixel", "mc.hypixel.net", net.minecraft.client.multiplayer.ServerData.Type.OTHER), false);
					list.add(new net.minecraft.client.multiplayer.ServerData("Nowhere", "offline.invalid", net.minecraft.client.multiplayer.ServerData.Type.OTHER), false);
					list.save();
				}
			}
			case 140 -> call("showcaseTitle", "servers");
			case 200 -> grab.accept("title-servers");
			case 205 -> call("showcaseTitle", "select");
			case 212 -> grab.accept("title-servers-selected");
			case 215 -> call("showcaseTitle", "worlds");
			case 260 -> grab.accept("title-worlds");
			case 262 -> call("showcaseTitle", "close");
			case 300 -> call("showcaseTitle", "sky:0.78,0.18");
			case 306 -> grab.accept("title-sky");
			case 308 -> call("showcaseTitle", "drag:0,0,6");
			case 312 -> call("showcaseTitle", "drag:0,0.5,6.2");
			case 330 -> grab.accept("title-drag");
			case 332 -> call("showcaseTitle", "drop");
			case 372 -> grab.accept("title-thrown");
			case 374 -> call("showcaseTitle", "tabout");
			case 390 -> grab.accept("title-tabout");
			case 392 -> mc.gui.setScreen(new net.minecraft.client.gui.screens.options.OptionsScreen(mc.gui.screen(), mc.options, false));
			case 432 -> grab.accept("title-options");
			case 434 -> mc.gui.setScreen(new net.minecraft.client.gui.screens.options.VideoSettingsScreen(mc.gui.screen(), mc, mc.options));
			case 464 -> grab.accept("title-video");
			case 466 -> mc.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
			case 490 -> {
				System.out.println("TEMPLE_TITLE back to " + mc.gui.screen().getClass().getName());
				titleDone = true;
				if (System.getProperty("temple.showcase.only", "").equals("Title")) {
					System.out.println("TEMPLE_SHOWCASE_DONE");
					mc.stop();
				}
			}
			default -> {
			}
		}
	}
	private void round45(Minecraft mc) {
		command(mc, "gamemode creative");
		command(mc, "time set 1000");
		command(mc, "weather clear");
		add(1, () -> {
			mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			call("showcaseEnable", "Freelook", false);
			call("showcaseEnable", "Fullbright", true);
			call("showcaseEnable", "Step", true);
			call("showcaseSet", "Map", "Minimap", "true");
			call("showcaseSet", "Map", "Coordinates", "true");
			call("showcaseEnable", "Map", true);
			mc.player.setYRot(0);
			mc.player.setXRot(10);
		});
		command(mc, "fill ~3 ~ ~9 ~4 ~7 ~10 minecraft:gold_block");
		command(mc, "fill ~-7 ~ ~12 ~-4 ~3 ~12 minecraft:stone_bricks");
		command(mc, "fill ~-1 ~ ~16 ~-1 ~5 ~16 minecraft:oak_log");
		command(mc, "fill ~-3 ~4 ~14 ~1 ~6 ~18 minecraft:oak_leaves keep");
		add(40, () -> { });
		shot(mc, "r45-hud");
		add(1, () -> call("openMenu", (Object) null));
		add(40, () -> { });
		shot(mc, "r45-menu");
		add(1, () -> mc.gui.setScreen(null));
		add(1, () -> call("showcaseEnable", "Map", false));
		add(1, () -> {
			call("showcaseSet", "Wet Ground", "Style", "Flooded");
			call("showcaseSet", "Wet Ground", "World reflections", "true");
			call("showcaseSet", "Wet Ground", "Raindrops", "false");
			call("showcaseSet", "Wet Ground", "Shine", "80");
			call("showcaseEnable", "Wet Ground", true);
			mc.player.setXRot(14);
		});
		add(60, () -> { });
		shot(mc, "r45-flooded");
		add(1, () -> call("showcaseSet", "Wet Ground", "Raindrops", "true"));
		command(mc, "weather rain");
		add(80, () -> { });
		shot(mc, "r45-flooded-rain");
		add(1, () -> call("showcaseSet", "Wet Ground", "Style", "Puddles"));
		add(30, () -> { });
		shot(mc, "r45-puddles");
		add(1, () -> call("showcaseSet", "Wet Ground", "World reflections", "false"));
		add(20, () -> { });
		shot(mc, "r45-puddles-sky-only");
		add(1, () -> {
			call("showcaseSet", "Wet Ground", "World reflections", "true");
			call("showcaseEnable", "Wet Ground", false);
		});
		command(mc, "weather clear");
		add(1, () -> {
			call("showcaseSet", "Explosion Effects", "Impact frames", "true");
			call("showcaseSet", "Explosion Effects", "Length", "0.8");
			call("showcaseSet", "Explosion Effects", "Range", "64");
			call("showcaseSet", "Explosion Effects", "Cooldown", "0");
			call("showcaseSet", "Explosion Effects", "Shockwave", "false");
			call("showcaseEnable", "Explosion Effects", true);
			mc.player.setXRot(6);
		});
		add(10, () -> { });
		command(mc, "summon creeper ~2 ~ ~9 {Fuse:0,ignited:1b,ExplosionRadius:1b}");
		shot(mc, "r45-impact-a");
		add(3, () -> { });
		shot(mc, "r45-impact-b");
		add(30, () -> {
			call("showcaseSet", "Explosion Effects", "Impact frames", "false");
			call("showcaseSet", "Explosion Effects", "Shockwave", "true");
			call("showcaseSet", "Explosion Effects", "Wave speed", "40");
			call("showcaseSet", "Explosion Effects", "Wave brightness", "85");
			mc.player.setXRot(25);
		});
		command(mc, "summon creeper ~0 ~ ~6 {Fuse:0,ignited:1b,ExplosionRadius:1b}");
		add(4, () -> { });
		shot(mc, "r45-shock-a");
		add(8, () -> { });
		shot(mc, "r45-shock-b");
		add(20, () -> {
			call("showcaseEnable", "Explosion Effects", false);
			call("showcaseSet", "Explosion Effects", "Impact frames", "false");
			call("showcaseSet", "Explosion Effects", "Shockwave", "false");
		});
		add(1, () -> {
			mc.player.setXRot(0);
			call("showcaseSet", "Music", "Hide when paused", "false");
			call("showcaseSet", "Music", "Lyric style", "In world");
			call("showcaseSet", "Music", "Max distance", "32");
			call("showcaseSet", "Music", "Lyric color", "7FD8FF");
			System.setProperty("temple.music.fake", "Calcium|Ecco2k|E|225|40");
			call("showcaseEnable", "Music", true);
		});
		add(160, () -> { });
		shot(mc, "r45-lyrics-world");
		add(80, () -> { });
		shot(mc, "r45-lyrics-world2");
		add(1, () -> call("showcaseEnable", "Music", false));
	}
	private void ambience45(Minecraft mc) {
		command(mc, "gamemode creative");
		command(mc, "time set 1000");
		command(mc, "weather clear");
		command(mc, "fill ~-6 ~ ~10 ~-4 ~5 ~12 minecraft:oak_log");
		command(mc, "fill ~6 ~ ~22 ~8 ~8 ~24 minecraft:stone_bricks");
		command(mc, "fill ~-12 ~ ~40 ~12 ~3 ~41 minecraft:mossy_cobblestone");
		add(1, () -> {
			mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			mc.player.setYRot(0);
			mc.player.setXRot(8);
			call("showcaseSet", "Ambience", "Custom fog", "false");
			call("showcaseSet", "Ambience", "Ground fog", "true");
			call("showcaseSet", "Ambience", "Lies at", "Around you");
			call("showcaseSet", "Ambience", "Fog depth", "3");
			call("showcaseSet", "Ambience", "Fog density", "60");
			call("showcaseSet", "Ambience", "Filter", "None");
			call("showcaseEnable", "Ambience", true);
		});
		add(60, () -> { });
		shot(mc, "a45-groundfog");
		add(1, () -> call("showcaseSet", "Ambience", "Fog depth", "8"));
		add(20, () -> { });
		shot(mc, "a45-groundfog-deep");
		command(mc, "time set 23200");
		add(40, () -> { });
		shot(mc, "a45-groundfog-dawn");
		command(mc, "time set 1000");
		add(1, () -> {
			call("showcaseSet", "Ambience", "Ground fog", "false");
			call("showcaseSet", "Ambience", "Custom fog", "true");
			call("showcaseSet", "Ambience", "Fog start", "0");
			call("showcaseSet", "Ambience", "Fog end", "120");
			call("showcaseSet", "Ambience", "Fog thickness", "90");
			call("showcaseSet", "Ambience", "Fog gradient", "10");
		});
		add(20, () -> { });
		shot(mc, "a45-fog-gradient-10");
		add(1, () -> call("showcaseSet", "Ambience", "Fog gradient", "90"));
		add(20, () -> { });
		shot(mc, "a45-fog-gradient-90");
		add(1, () -> {
			call("showcaseSet", "Ambience", "Custom fog", "false");
			call("showcaseSet", "Ambience", "Filter", "Cinematic");
		});
		add(20, () -> { });
		shot(mc, "a45-filter-cinematic");
		add(1, () -> call("showcaseSet", "Ambience", "Filter", "Noir"));
		add(10, () -> { });
		shot(mc, "a45-filter-noir");
		add(1, () -> call("showcaseSet", "Ambience", "Filter", "Warm"));
		add(10, () -> { });
		shot(mc, "a45-filter-warm");
		add(1, () -> {
			call("showcaseSet", "Ambience", "Filter", "None");
			call("showcaseEnable", "Ambience", false);
		});
	}
	private void chat(Minecraft mc, String line) {
		add(3, () -> mc.player.connection.sendChat(line));
	}
	private void log(Minecraft mc, String tag) {
		add(1, () -> {
			var p = mc.player;
			if (p == null) {
				System.out.println("TEMPLE_NAV " + tag + " no player, screen " + (mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getName()));
				return;
			}
			var chest = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
			System.out.println(String.format(java.util.Locale.ROOT,
					"TEMPLE_NAV %s dim=%s x=%.1f y=%.1f z=%.1f speed=%.2f pitch=%.0f yaw=%.0f flying=%s chest=%s dmg=%d screen=%s",
					tag, mc.level.dimension().identifier().getPath(), p.getX(), p.getY(), p.getZ(),
					p.getDeltaMovement().horizontalDistance(), p.getXRot(), p.getYRot(), p.isFallFlying(),
					chest.isEmpty() ? "-" : chest.getItem().toString(), chest.getDamageValue(),
					mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName()));
		});
	}
	private void navigation(Minecraft mc) {
		add(1, () -> {
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			call("showcaseSet", "HUD", "Speed graph", "true");
		});
		command(mc, "gamemode creative");
		command(mc, "effect give @s resistance 99999 4 true");
		command(mc, "effect give @s night_vision 99999 0 true");
		command(mc, "time set 6000");

		command(mc, "fill ~6 ~-1 ~6 ~16 ~-1 ~14 minecraft:water");
		command(mc, "fill ~-14 ~ ~4 ~-6 ~6 ~12 minecraft:stone");
		command(mc, "fill ~-20 ~-1 ~-20 ~20 ~-1 ~-16 minecraft:sand");
		chat(mc, ".wp add Home");
		for (int i = 1; i <= 6; i++) {
			command(mc, "tp @s ~48 ~ ~" + (i % 2 == 0 ? 24 : -16));
			add(40, () -> { });
		}
		chat(mc, ".wp add Far camp");
		command(mc, "tp @s ~-40 ~ ~-10 -60 10");
		add(60, () -> { });
		log(mc, "waypoints");
		shot(mc, "nav-waypoints");
		add(1, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		add(20, () -> { });
		shot(mc, "nav-minimap");
		add(1, () -> call("showcaseEnable", "Streamer Mode", true));
		add(10, () -> { });
		shot(mc, "nav-streamer");
		add(1, () -> mc.player.connection.sendChat("meet me at " + mc.player.getBlockX() + " " + mc.player.getBlockZ()));
		add(10, () -> call("showcaseEnable", "Streamer Mode", false));
		add(1, () -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		chat(mc, ".map");
		add(20, () -> { });
		log(mc, "map-open");
		shot(mc, "nav-map");
		add(1, () -> {
			if (mc.gui.screen() != null) {
				mc.gui.screen().mouseScrolled(mc.getWindow().getGuiScaledWidth() / 2.0, mc.getWindow().getGuiScaledHeight() / 2.0, 0, -6);
			}
		});
		add(10, () -> { });
		shot(mc, "nav-map-out");
		add(1, () -> {
			if (mc.gui.screen() != null) {
				mc.gui.screen().mouseScrolled(mc.getWindow().getGuiScaledWidth() / 2.0, mc.getWindow().getGuiScaledHeight() / 2.0, 0, 16);
			}
		});
		add(10, () -> { });
		shot(mc, "nav-map-in");
		add(1, () -> mc.gui.setScreen(null));
		chat(mc, ".history");
		add(30, () -> { });
		shot(mc, "nav-history");
		add(1, () -> mc.gui.setScreen(null));
		command(mc, "gamemode survival");
		command(mc, "clear @s");
		command(mc, "item replace entity @s armor.chest with minecraft:elytra[minecraft:damage=421]");
		command(mc, "give @s minecraft:elytra");
		command(mc, "tp @s ~ 300 ~ -90 0");
		add(12, () -> { });
		chat(mc, ".fly 30000 0");
		for (int i = 0; i < 60; i++) {
			log(mc, "glide t=" + i);
			add(19, () -> { });
			if (i == 30) {
				add(1, () -> mc.player.connection.sendChat("hello from the sky"));
				add(5, () -> { });
				shot(mc, "nav-glide");
			}
		}
		command(mc, "give @s minecraft:firework_rocket 32");
		add(5, () -> mc.player.getInventory().setSelectedSlot(0));
		command(mc, "tp @s ~ 250 ~");
		for (int i = 0; i < 20; i++) {
			log(mc, "rescue t=" + i);
			add(19, () -> { });
		}
		command(mc, "execute in minecraft:the_nether run tp @s 0 190 0 -90 0");
		add(40, () -> { });
		command(mc, "fill -40 60 -40 150 60 40 minecraft:netherrack");
		command(mc, "fill -40 60 -6 150 60 6 minecraft:lava");
		command(mc, "fill 20 61 10 60 64 30 minecraft:soul_sand");
		command(mc, "fill 80 61 -30 110 70 -12 minecraft:basalt");
		chat(mc, ".fly 40000 0 overworld");
		for (int i = 0; i < 30; i++) {
			log(mc, "nether t=" + i);
			add(19, () -> { });
		}
		shot(mc, "nav-nether");
		chat(mc, ".map");
		add(20, () -> { });
		shot(mc, "nav-nether-map");
		add(1, () -> {
			if (mc.gui.screen() != null) {
				mc.gui.screen().mouseScrolled(mc.getWindow().getGuiScaledWidth() / 2.0, mc.getWindow().getGuiScaledHeight() / 2.0, 0, -7);
			}
		});
		add(10, () -> { });
		shot(mc, "nav-nether-map-out");
		add(1, () -> mc.gui.setScreen(null));
		add(1, () -> {
			var pos = new net.minecraft.core.BlockPos.MutableBlockPos();
			StringBuilder sb = new StringBuilder("TEMPLE_NAV column");
			for (int y = 130; y >= 0; y -= 6) {
				pos.set(mc.player.getBlockX(), y, mc.player.getBlockZ());
				var st = mc.level.getBlockState(pos);
				sb.append(' ').append(y).append('=').append(st.getBlock().builtInRegistryHolder().key().identifier().getPath())
						.append('/').append(Integer.toHexString(st.getMapColor(mc.level, pos).col));
			}
			sb.append(" logical=").append(mc.level.dimensionType().logicalHeight()).append(" ceiling=").append(mc.level.dimensionType().hasCeiling())
					.append(" loaded=").append(mc.level.hasChunk(mc.player.getBlockX() >> 4, mc.player.getBlockZ() >> 4));
			System.out.println(sb);
		});
		chat(mc, ".fly stop");
		command(mc, "gamemode creative");
		command(mc, "execute in minecraft:overworld run tp @s 0 10 0");
		add(60, () -> { });
		add(1, () -> {
			call("showcaseSet", "Auto Relog", "At or below Y", Integer.toString((int) Math.floor(mc.player.getY()) + 1));
			call("showcaseEnable", "Auto Relog", true);
		});
		for (int i = 0; i < 16; i++) {
			log(mc, "relog t=" + i);
			add(20, () -> { });
		}
		add(1, () -> call("showcaseEnable", "Auto Relog", false));
	}
	private Object call(String method, Object... args) {
		try {
			for (Method m : client.getMethods()) {
				if (m.getName().equals(method) && m.getParameterCount() == args.length) {
					return m.invoke(null, args);
				}
			}
			throw new IllegalStateException("no " + method);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
	private static Object staticCall(String type, String method, Object... args) {
		try {
			for (Method m : Class.forName(type).getMethods()) {
				if (m.getName().equals(method) && m.getParameterCount() == args.length) {
					return m.invoke(null, args);
				}
			}
			throw new IllegalStateException("no " + method);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
	private static net.minecraft.world.entity.LivingEntity entity(Minecraft mc, String tag) {
		net.minecraft.world.entity.LivingEntity best = null;
		for (var e : mc.level.entitiesForRendering()) {
			if (e instanceof net.minecraft.world.entity.monster.zombie.Zombie z && z.isAlive()
					&& (best == null || z.distanceToSqr(mc.player) < best.distanceToSqr(mc.player))) {
				best = z;
			}
		}
		return best;
	}
	private static int countStone(Minecraft mc) {
		int n = 0;
		var origin = mc.player.blockPosition();
		for (var pos : net.minecraft.core.BlockPos.betweenClosed(origin.offset(-4, -1, -4), origin.offset(4, 3, 4))) {
			if (mc.level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.STONE)) {
				n++;
			}
		}
		return n;
	}

	private static float zombieHealth(Minecraft mc, String tag) {
		var e = entity(mc, tag);
		return e == null ? -1 : e.getHealth();
	}
	private void airOnly(String... effects) {
		for (String e : List.of("Fireflies", "Embers", "Snow", "Stardust", "Matrix", "Light lines", "Cubes", "Rings", "Prisms", "Network", "Pillars")) {
			call("showcaseSet", "Air Effects", e, String.valueOf(java.util.Arrays.asList(effects).contains(e)));
		}
	}
	private static void attackNearest(Minecraft mc) {
		var target = mc.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
				mc.player.getBoundingBox().inflate(6), e -> e != mc.player && e.isAlive()).stream().findFirst().orElse(null);
		if (target != null) {
			mc.gameMode.attack(mc.player, target);
			mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		}
	}
	private void add(int wait, Runnable action) {
		steps.add(new Step(wait, action));
	}
	private static void desktopShot(Minecraft mc, String name) {
		try (var arena = java.lang.foreign.Arena.ofConfined()) {
			var linker = java.lang.foreign.Linker.nativeLinker();
			var user32 = java.lang.foreign.SymbolLookup.libraryLookup("user32", arena);
			var gdi32 = java.lang.foreign.SymbolLookup.libraryLookup("gdi32", arena);
			var A = java.lang.foreign.ValueLayout.ADDRESS;
			var I = java.lang.foreign.ValueLayout.JAVA_INT;
			var getDC = linker.downcallHandle(user32.find("GetDC").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(A, A));
			var releaseDC = linker.downcallHandle(user32.find("ReleaseDC").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(I, A, A));
			var clientToScreen = linker.downcallHandle(user32.find("ClientToScreen").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(I, A, A));
			var getClientRect = linker.downcallHandle(user32.find("GetClientRect").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(I, A, A));
			var createDC = linker.downcallHandle(gdi32.find("CreateCompatibleDC").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(A, A));
			var createBitmap = linker.downcallHandle(gdi32.find("CreateCompatibleBitmap").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(A, A, I, I));
			var select = linker.downcallHandle(gdi32.find("SelectObject").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(A, A, A));
			var bitBlt = linker.downcallHandle(gdi32.find("BitBlt").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(I, A, I, I, I, I, A, I, I, I));
			var getDIBits = linker.downcallHandle(gdi32.find("GetDIBits").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(I, A, A, I, I, A, A, I));
			var deleteObject = linker.downcallHandle(gdi32.find("DeleteObject").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(I, A));
			var deleteDC = linker.downcallHandle(gdi32.find("DeleteDC").orElseThrow(), java.lang.foreign.FunctionDescriptor.of(I, A));
			var hwnd = java.lang.foreign.MemorySegment.ofAddress(org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window(mc.getWindow().handle()));
			var point = arena.allocate(8);
			var rect = arena.allocate(16);
			int ignored = (int) clientToScreen.invoke(hwnd, point);
			ignored = (int) getClientRect.invoke(hwnd, rect);
			int x = point.get(I, 0);
			int y = point.get(I, 4);
			int w = rect.get(I, 8);
			int h = rect.get(I, 12);
			var screen = (java.lang.foreign.MemorySegment) getDC.invoke(java.lang.foreign.MemorySegment.NULL);
			var mem = (java.lang.foreign.MemorySegment) createDC.invoke(screen);
			var bitmap = (java.lang.foreign.MemorySegment) createBitmap.invoke(screen, w, h);
			var old = (java.lang.foreign.MemorySegment) select.invoke(mem, bitmap);
			ignored = (int) bitBlt.invoke(mem, 0, 0, w, h, screen, x, y, 0x00CC0020 | 0x40000000);
			var header = arena.allocate(44);
			header.set(I, 0, 40);
			header.set(I, 4, w);
			header.set(I, 8, -h);
			header.set(java.lang.foreign.ValueLayout.JAVA_SHORT, 12, (short) 1);
			header.set(java.lang.foreign.ValueLayout.JAVA_SHORT, 14, (short) 32);
			var pixels = arena.allocate((long) w * h * 4);
			select.invoke(mem, old);
			ignored = (int) getDIBits.invoke(mem, bitmap, 0, h, pixels, header, 0);
			try (var image = new com.mojang.blaze3d.platform.NativeImage(w, h, false)) {
				for (int yy = 0; yy < h; yy++) {
					for (int xx = 0; xx < w; xx++) {
						int bgra = pixels.getAtIndex(I, (long) yy * w + xx);
						int abgr = 0xFF000000 | ((bgra & 0xFF) << 16) | (bgra & 0xFF00) | ((bgra >> 16) & 0xFF);
						image.setPixelABGR(xx, yy, abgr);
					}
				}
				image.writeToFile(new java.io.File(mc.gameDirectory, "screenshots/showcase-" + name + ".png").toPath());
			}
			ignored = (int) deleteObject.invoke(bitmap);
			ignored = (int) deleteDC.invoke(mem);
			ignored = (int) releaseDC.invoke(java.lang.foreign.MemorySegment.NULL, screen);
			System.out.println("TEMPLE_STREAMPROOF desktop grab " + name + " " + w + "x" + h + " at " + x + "," + y);
		} catch (Throwable t) {
			System.out.println("TEMPLE_STREAMPROOF desktop grab failed: " + t);
		}
	}
	private void siteWindow(Minecraft mc) {
		add(1, () -> {
			long handle = mc.getWindow().handle();
			org.lwjgl.glfw.GLFW.glfwSetWindowSizeLimits(handle, org.lwjgl.glfw.GLFW.GLFW_DONT_CARE,
					org.lwjgl.glfw.GLFW.GLFW_DONT_CARE, 8192, 8192);
			org.lwjgl.glfw.GLFW.glfwSetWindowPos(handle, 0, 0);
			org.lwjgl.glfw.GLFW.glfwSetWindowSize(handle, 1920, 1080);
			mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
			mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
			mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
		});
	}

	@SuppressWarnings("unchecked")
	private void perfShots(Minecraft mc) {
		add(1, () -> {
			mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			mc.player.getAbilities().flying = true;
		});
		command(mc, "fill ~-4 ~3 ~8 ~3 ~5 ~10 minecraft:diamond_ore");
		command(mc, "fill ~5 ~3 ~8 ~7 ~4 ~9 minecraft:gold_ore");
		command(mc, "fill ~-8 ~ ~12 ~7 ~ ~13 minecraft:barrel");
		command(mc, "setblock ~-6 ~1 ~8 minecraft:chest");
		for (int i = 0; i < 8; i++) {
			command(mc, "summon husk ~" + (i * 2 - 7) + " ~ ~6 {NoAI:1b,PersistenceRequired:1b}");
		}
		add(40, () -> {
			mc.player.setYRot(0);
			mc.player.setXRot(-5);
		});
		for (String style : List.of("Box", "Outline", "Fill")) {
			add(1, () -> {
				call("showcaseSet", "Ore ESP", "Style", style);
				call("showcaseSet", "Ore ESP", "Gold", "true");
				call("showcaseEnable", "Ore ESP", true);
			});
			add(60, () -> { });
			shot(mc, "perf-ore-" + style.toLowerCase());
		}
		add(1, () -> {
			call("showcaseEnable", "Ore ESP", false);
			call("showcaseSet", "Storage ESP", "Tracers", "true");
			call("showcaseEnable", "Storage ESP", true);
		});
		add(60, () -> { });
		shot(mc, "perf-storage");
		add(1, () -> call("showcaseEnable", "Storage ESP", false));
		for (String mode : (List<String>) call("showcaseOptions", "ESP", "Mode")) {
			add(1, () -> {
				call("showcaseSet", "ESP", "Entities", "minecraft:husk");
				call("showcaseSet", "ESP", "Mode", mode);
				call("showcaseEnable", "ESP", true);
				call("showcaseEnable", "Tracers", true);
			});
			add(20, () -> { });
			shot(mc, "perf-esp-" + mode.toLowerCase());
		}
		add(1, () -> {
			call("showcaseEnable", "ESP", false);
			call("showcaseEnable", "Tracers", false);
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			mc.player.setXRot(12);
		});
		List<String> pieces = (List<String>) call("showcaseAuraPieces");
		for (boolean realism : new boolean[] {false, true}) {
			add(1, () -> {
				for (String piece : pieces) {
					call("showcaseSet", "Player Aura", piece, "true");
				}
				call("showcaseRealism", "Player Aura", realism);
				call("showcaseEnable", "Player Aura", true);
			});
			add(40, () -> { });
			shot(mc, "perf-aura" + (realism ? "-realism" : ""));
		}
		add(1, () -> {
			call("showcaseEnable", "Player Aura", false);
			call("showcaseSet", "Pets", "Pet", "Dragon");
			call("showcaseEnable", "Pets", true);
			call("showcaseEnable", "Cape", true);
			call("showcaseEnable", "Air Effects", true);
			call("showcaseEnable", "Trails", true);
		});
		add(60, () -> { });
		shot(mc, "perf-pet-cape");
		add(1, () -> {
			for (String name : List.of("Pets", "Cape", "Air Effects", "Trails")) {
				call("showcaseEnable", name, false);
			}
		});
	}
	private static double frameMs(Minecraft mc) {
		return 1000.0 / Math.max(1, mc.getFps());
	}
	private double perfBase;
	private double perfBest;
	private java.util.Map<String, Double> perfParts = java.util.Map.of();
	private void perfSettle(Minecraft mc, int seconds, java.util.function.DoubleConsumer then) {
		add(10, () -> mc.getFramerateLimitTracker().onInputReceived());
		add(10, () -> {
			mc.getFramerateLimitTracker().onInputReceived();
			perfBest = Double.MAX_VALUE;
			nz.temple.util.Perf.take();
		});
		for (int k = 0; k < seconds * 2; k++) {
			add(10, () -> {
				mc.getFramerateLimitTracker().onInputReceived();
				perfBest = Math.min(perfBest, frameMs(mc));
			});
		}
		add(1, () -> then.accept(perfBest));
	}
	private void perfCase(Minecraft mc, String label, Runnable on, Runnable off) {
		perfSettle(mc, 2, ms -> {
			perfBase = ms;
			perfParts = nz.temple.util.Perf.take();
		});
		add(1, on);
		perfSettle(mc, 3, ms -> System.out.println(String.format(java.util.Locale.ROOT, "TEMPLE_PERF %-34s frame %+7.3f ms (%6.3f to %6.3f) | parts:%s",
				label, ms - perfBase, perfBase, ms, nz.temple.util.Perf.rise(perfParts, nz.temple.util.Perf.take(), 0.01))));
		add(1, off);
	}
	@SuppressWarnings("unchecked")
	private void perfProbe(Minecraft mc, String only) {
		add(1, () -> {
			mc.options.enableVsync().set(false);
			mc.options.framerateLimit().set(260);
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			mc.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
			mc.player.getAbilities().flying = true;
		});
		command(mc, "fill ~-8 ~6 ~10 ~7 ~13 ~25 minecraft:diamond_ore");
		command(mc, "fill ~-16 ~ ~30 ~15 ~ ~61 minecraft:barrel");
		for (int i = 0; i < 48; i++) {
			command(mc, "summon husk ~" + (i % 12 - 6) + " ~ ~" + (4 + i / 12 * 2) + " {NoAI:1b,PersistenceRequired:1b}");
		}
		for (int i = 0; i < 24; i++) {
			command(mc, "summon item ~" + (i % 12 - 6) + " ~1 ~3 {Item:{id:\"minecraft:diamond\",count:1},PickupDelay:32767}");
		}
		add(60, () -> { });
		if (only.contains("PerfProbeProfile")) {

			add(1, () -> {
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				for (String piece : (List<String>) call("showcaseAuraPieces")) {
					call("showcaseSet", "Player Aura", piece, "true");
				}
				call("showcaseRealism", "Player Aura", only.contains("Realism"));
				call("showcaseEnable", "Player Aura", true);
				call("showcaseEnable", "Cape", true);
				call("showcaseEnable", "Pets", true);
			});
			perfSettle(mc, 40, ms -> System.out.println(String.format(java.util.Locale.ROOT, "TEMPLE_PERF %-34s frame %6.3f ms | parts:%s",
					"profile", ms, nz.temple.util.Perf.rise(java.util.Map.of(), nz.temple.util.Perf.take(), 0.005))));
			return;
		}
		if (only.contains("PerfProbeAvatar")) {
			add(1, () -> {
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				call("showcaseSet", "Custom Models", "Model", "Sendagaya Shino");
				call("showcaseEnable", "Custom Models", true);
			});
			add(200, () -> { });
			for (String[] variant : new String[][] {{"true", "true"}, {"false", "true"}, {"false", "false"}}) {
				add(1, () -> {
					call("showcaseSet", "Custom Models", "Outline", variant[0]);
					call("showcaseSet", "Custom Models", "Physics", variant[1]);
					nz.temple.util.Perf.take();
				});
				add(120, () -> System.out.println("TEMPLE_PERF avatar outline=" + variant[0] + " physics=" + variant[1] + " "
						+ nz.temple.util.Perf.take().entrySet().stream().filter(e -> e.getKey().startsWith("avatar"))
						.map(e -> e.getKey() + "=" + String.format(java.util.Locale.ROOT, "%.3f", e.getValue())).toList()));
			}
			return;
		}
		if (only.contains("PerfProbePieces")) {
			add(1, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			List<String> all = (List<String>) call("showcaseAuraPieces");
			for (int round = 0; round < 2; round++) {
				for (boolean realism : new boolean[] {false, true}) {
					for (String piece : List.of("Wings", "Gyroscope")) {
						perfCase(mc, "Aura " + piece + (realism ? " realism" : ""), () -> {
							for (String other : all) {
								call("showcaseSet", "Player Aura", other, String.valueOf(other.equals(piece)));
							}
							call("showcaseRealism", "Player Aura", realism);
							call("showcaseEnable", "Player Aura", true);
						}, () -> call("showcaseEnable", "Player Aura", false));
					}
				}
			}
			return;
		}
		perfSettle(mc, 3, ms -> System.out.println(String.format(java.util.Locale.ROOT, "TEMPLE_PERF %-34s frame %6.3f ms | parts:%s",
				"idle", ms, nz.temple.util.Perf.rise(java.util.Map.of(), nz.temple.util.Perf.take(), 0.005))));
		for (int view = 0; view < 2; view++) {
			String v = view == 0 ? "facing" : "away";
			float yaw = view == 0 ? 0 : 180;
			add(5, () -> {
				mc.player.setYRot(yaw);
				mc.player.setXRot(0);
			});
			for (String style : List.of("Box", "Outline", "Fill")) {
				perfCase(mc, "Ore ESP " + style + " " + v, () -> {
					call("showcaseSet", "Ore ESP", "Style", style);
					call("showcaseSet", "Ore ESP", "Max shown", "4096");
					call("showcaseEnable", "Ore ESP", true);
				}, () -> call("showcaseEnable", "Ore ESP", false));
			}
			perfCase(mc, "Block ESP " + v, () -> {
				call("showcaseSet", "Block ESP", "Blocks", "diamond_ore");
				call("showcaseSet", "Block ESP", "Max shown", "4096");
				call("showcaseEnable", "Block ESP", true);
			}, () -> call("showcaseEnable", "Block ESP", false));
			perfCase(mc, "Storage ESP " + v, () -> call("showcaseEnable", "Storage ESP", true), () -> call("showcaseEnable", "Storage ESP", false));
			perfCase(mc, "Storage ESP tracers " + v, () -> {
				call("showcaseSet", "Storage ESP", "Tracers", "true");
				call("showcaseEnable", "Storage ESP", true);
			}, () -> {
				call("showcaseSet", "Storage ESP", "Tracers", "false");
				call("showcaseEnable", "Storage ESP", false);
			});
			for (String mode : (List<String>) call("showcaseOptions", "ESP", "Mode")) {
				perfCase(mc, "ESP " + mode + " " + v, () -> {
					call("showcaseSet", "ESP", "Entities", "minecraft:husk,minecraft:item,minecraft:player");
					call("showcaseSet", "ESP", "Mode", mode);
					call("showcaseEnable", "ESP", true);
				}, () -> call("showcaseEnable", "ESP", false));
			}
			perfCase(mc, "Tracers " + v, () -> call("showcaseEnable", "Tracers", true), () -> call("showcaseEnable", "Tracers", false));
			for (String style : (List<String>) call("showcaseOptions", "Item ESP", "Style")) {
				perfCase(mc, "Item ESP " + style + " " + v, () -> {
					call("showcaseSet", "Item ESP", "Style", style);
					call("showcaseSet", "Item ESP", "Beams", "true");
					call("showcaseEnable", "Item ESP", true);
				}, () -> call("showcaseEnable", "Item ESP", false));
			}
			perfCase(mc, "Nametags " + v, () -> call("showcaseEnable", "Nametags", true), () -> call("showcaseEnable", "Nametags", false));
			perfCase(mc, "Chams " + v, () -> {
				call("showcaseSet", "Chams", "Entities", "minecraft:husk,minecraft:player");
				call("showcaseEnable", "Chams", true);
			}, () -> call("showcaseEnable", "Chams", false));
		}
		add(5, () -> {
			mc.player.setYRot(0);
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		});
		List<String> pieces = (List<String>) call("showcaseAuraPieces");
		for (boolean realism : new boolean[] {false, true}) {
			for (String piece : pieces) {
				perfCase(mc, "Aura " + piece + (realism ? " realism" : ""), () -> {
					for (String other : pieces) {
						call("showcaseSet", "Player Aura", other, String.valueOf(other.equals(piece)));
					}
					call("showcaseRealism", "Player Aura", realism);
					call("showcaseEnable", "Player Aura", true);
				}, () -> call("showcaseEnable", "Player Aura", false));
			}
			perfCase(mc, "Aura everything" + (realism ? " realism" : ""), () -> {
				for (String other : pieces) {
					call("showcaseSet", "Player Aura", other, "true");
				}
				call("showcaseRealism", "Player Aura", realism);
				call("showcaseEnable", "Player Aura", true);
			}, () -> call("showcaseEnable", "Player Aura", false));
		}
		for (String pet : (List<String>) call("showcaseOptions", "Pets", "Pet")) {
			perfCase(mc, "Pets " + pet, () -> {
				call("showcaseSet", "Pets", "Pet", pet);
				call("showcaseEnable", "Pets", true);
			}, () -> call("showcaseEnable", "Pets", false));
		}
		List<String> skip = List.of("Freecam", "Freelook", "Zoom", "Music", "Player Aura", "Pets", "Spoof", "World Map", "Xray");
		for (String category : only.contains("PerfProbeSweep") ? List.of("COSMETICS", "RENDER") : List.of("COSMETICS")) {
			for (String name : (List<String>) call("showcaseCategory", category)) {
				if (!skip.contains(name)) {
					perfCase(mc, name, () -> call("showcaseEnable", name, true), () -> call("showcaseEnable", name, false));
				}
			}
		}
	}
	private void parkCursor(Minecraft mc) {
		TrailerShots.cursor(mc, 0.998, 0.998);
	}
	private void grimMove(Minecraft mc) {
		var o = mc.options;
		String only = System.getProperty("temple.showcase.only", "");
		String sel = only.contains(":") ? only.substring(only.indexOf(':') + 1) : "";
		java.util.function.Predicate<String> want = section -> sel.isEmpty() || sel.contains(section);
		net.minecraft.world.phys.Vec3[] start = new net.minecraft.world.phys.Vec3[1];
		java.util.function.Consumer<String> phase = name -> add(3, () -> {
			mc.player.connection.sendCommand("say TEMPLE_PHASE " + name);
			start[0] = mc.player.position();
		});
		java.util.function.Consumer<String> report = name -> add(1, () -> {
			var p = mc.player.position();
			System.out.println("TEMPLE_GRIM " + name + String.format(java.util.Locale.ROOT, " horizontal %.2f vertical %.2f",
					Math.hypot(p.x - start[0].x, p.z - start[0].z), p.y - start[0].y));
		});
		Runnable keysOff = () -> {
			o.keyUp.setDown(false);
			o.keySprint.setDown(false);
			o.keyJump.setDown(false);
			o.keyUse.setDown(false);
		};
		java.util.function.Consumer<String> reset = name -> {
			add(1, keysOff);
			command(mc, "kill @e[type=!player]");
			command(mc, "tp @s 0 -60 0 0 0");
			command(mc, "fill -6 -61 -4 6 -61 80 minecraft:grass_block");
			command(mc, "fill -6 -60 -4 6 -50 80 minecraft:air");
			add(30, () -> { });
		};
		command(mc, "gamemode survival");
		command(mc, "difficulty peaceful");
		command(mc, "time set 6000");
		command(mc, "effect give @s minecraft:saturation infinite 10 true");
		command(mc, "effect give @s minecraft:resistance infinite 10 true");
		command(mc, "gamerule doMobLoot false");
		add(40, () -> { });
		if (want.test("speed")) {
			String[][] runs = {{"control-sprint", ""}, {"speed-grim-open", "Grim"}, {"speed-sprinthop", "Sprint hop"}};
			for (String[] run : runs) {
				reset.accept(run[0]);
				phase.accept(run[0]);
				add(60, () -> {
					if (!run[1].isEmpty()) {
						call("showcaseSet", "Speed", "Profile", run[1]);
						call("showcaseEnable", "Speed", true);
					}
					o.keyUp.setDown(true);
					o.keySprint.setDown(true);
				});
				add(1, keysOff);
				report.accept(run[0]);
				add(1, () -> call("showcaseEnable", "Speed", false));
			}
		}
		if (want.test("crowd")) {
			for (String crowd : new String[] {"villager", "oak_boat"}) {
				for (boolean on : new boolean[] {false, true}) {
					String name = "speed-" + crowd + (on ? "-grim" : "-control");
					reset.accept(name);
					for (int z = 2; z < (crowd.equals("oak_boat") ? 20 : 60); z += 2) {
						command(mc, "summon " + crowd + " 0.95 -60 " + (z + 0.5) + (crowd.equals("villager") ? " {NoAI:1b,Silent:1b}" : ""));
					}
					add(20, () -> { });
					phase.accept(name);
					add(50, () -> {
						if (on) {
							call("showcaseSet", "Speed", "Profile", "Grim");
							call("showcaseEnable", "Speed", true);
						}
						o.keyUp.setDown(true);
						o.keySprint.setDown(true);
					});
					add(1, keysOff);
					report.accept(name);
					add(1, () -> call("showcaseEnable", "Speed", false));
				}
			}
		}
		if (want.test("fly")) {
			reset.accept("fly-grim");
			for (int z = 0; z < 30; z += 1) {
				command(mc, "summon oak_boat 1.2 -60 " + (z + 0.5));
			}
			add(20, () -> { });
			phase.accept("fly-grim-up");
			add(20, () -> {
				call("showcaseSet", "Fly", "Mode", "Grim");
				call("showcaseEnable", "Fly", true);
				o.keyJump.setDown(true);
			});
			add(1, keysOff);
			report.accept("fly-grim-up");
			phase.accept("fly-grim-along");
			add(30, () -> o.keyUp.setDown(true));
			add(1, keysOff);
			report.accept("fly-grim-along");
			add(1, () -> call("showcaseEnable", "Fly", false));
			add(40, () -> { });
		}
		if (want.test("noslow")) {
			int reps = sel.contains("reps=") ? Integer.parseInt(sel.replaceAll(".*reps=(\\d+).*", "$1")) : 1;
			for (String item : new String[] {"shield", "bow"})
			for (int pass = 0; pass < 1 + 2 * reps; pass++) {
				boolean on = pass > 0;
				int parity = (pass - 1) % 2;
				String name = (on ? "noslow-grim-" + item + (parity == 0 ? "-even" : "-odd") + (reps > 1 ? "-" + (pass - 1) / 2 : "")
						: "noslow-control-" + item);
				reset.accept(name);
				command(mc, "item replace entity @s hotbar.0 with minecraft:" + item);
				command(mc, "item replace entity @s hotbar.8 with minecraft:arrow 64");
				if (on && sel.contains("debug")) {
					command(mc, "grim debug TempleShowcase");
				}
				add(10, () -> mc.player.getInventory().setSelectedSlot(0));
				phase.accept(name);
				add(1, () -> {
					if (on) {
						call("showcaseSet", "No Slow", "Mode", "Grim");
						call("showcaseEnable", "No Slow", true);
						if (mc.player.tickCount % 2 == parity) {
							o.keyUse.setDown(true);
							o.keyUp.setDown(true);
						}
					}
				});
				add(60, () -> {
					o.keyUse.setDown(true);
					o.keyUp.setDown(true);
				});
				add(1, keysOff);
				report.accept(name);
				add(1, () -> call("showcaseEnable", "No Slow", false));
			}
			for (String item : new String[] {"shield", "bow"}) {
				String name = "noslow-grim-tap-" + item;
				reset.accept(name);
				command(mc, "item replace entity @s hotbar.0 with minecraft:" + item);
				command(mc, "item replace entity @s hotbar.8 with minecraft:arrow 64");
				if (sel.contains("debug")) {
					command(mc, "grim debug TempleShowcase");
				}
				add(10, () -> mc.player.getInventory().setSelectedSlot(0));
				phase.accept(name);
				add(20, () -> {
					call("showcaseSet", "No Slow", "Mode", "Grim");
					call("showcaseEnable", "No Slow", true);
					o.keyUp.setDown(true);
				});
				for (int tap = 0; tap < 8; tap++) {
					add(7, () -> o.keyUse.setDown(true));
					add(3, () -> o.keyUse.setDown(false));
				}
				add(1, keysOff);
				report.accept(name);
				add(1, () -> call("showcaseEnable", "No Slow", false));
			}
		}
		if (want.test("velocity")) {
			command(mc, "difficulty easy");
			for (boolean on : new boolean[] {false, true}) {
				String name = on ? "velocity-grim" : "velocity-control";
				reset.accept(name);
				command(mc, "item replace entity @s hotbar.0 with minecraft:stone_sword");
				add(1, () -> {
					mc.player.getInventory().setSelectedSlot(0);
					if (on) {
						call("showcaseSet", "Velocity", "Mode", "Grim");
						call("showcaseEnable", "Velocity", true);
					}
				});
				for (int hit = 0; hit < 5; hit++) {
					String tag = name + "-" + hit;
					command(mc, "kill @e[type=husk]");
					command(mc, "tp @s 0 -60 " + (hit * 12) + " 0 0");
					command(mc, "summon husk 0 -60 " + (hit * 12 + 2.6) + " {NoAI:1b,Silent:1b,Health:200f,Attributes:[{id:\"minecraft:max_health\",base:200}]}");
					add(25, () -> { });
					phase.accept(tag);
					add(6, () -> {
						o.keyUp.setDown(true);
						o.keySprint.setDown(true);
					});
					add(1, () -> {
					mc.player.connection.sendCommand("damage @s 1 minecraft:mob_attack by @e[type=husk,limit=1]");
					start[0] = mc.player.position();
				});
					add(12, () -> o.keyUp.setDown(false));
					add(1, keysOff);
					report.accept(tag);
				}
				add(1, () -> call("showcaseEnable", "Velocity", false));
			}
		}
		if (want.test("aura")) {
			command(mc, "difficulty easy");
			for (String run : new String[] {"off", "control", "grim", "grim-jump", "grim-snap"}) {
				boolean on = run.startsWith("grim");
				String name = "aura-" + run;
				reset.accept(name);
				command(mc, "item replace entity @s hotbar.0 with minecraft:stone_sword");
				for (int i = 0; i < 3; i++) {
					command(mc, "summon husk " + (i * 2 - 2) + " -60 5 {Silent:1b,Health:200f,Attributes:[{id:\"minecraft:max_health\",base:200}]}");
				}
				add(10, () -> {
					mc.player.getInventory().setSelectedSlot(0);
					call("showcaseSet", "Kill Aura", "Hostiles", "true");
					call("showcaseSet", "Kill Aura", "Range", "3.6");
					call("showcaseSet", "Kill Aura", "Rotation", "Silent");
					call("showcaseSet", "Kill Aura", "Rotation speed", run.equals("grim-snap") ? "180" : "60");
					call("showcaseSet", "Kill Aura", "Smoothing", run.equals("grim-snap") ? "0" : "55");
					call("showcaseSet", "Kill Aura", "Mode", run.equals("grim-snap") ? "Switch" : "Single");
					call("showcaseSet", "Kill Aura", "Grim", String.valueOf(on));
				});
				phase.accept(name);
				add(20, () -> {
					call("showcaseEnable", "Kill Aura", !run.equals("off"));
					o.keyUp.setDown(true);
				});
				int ticks = run.equals("control") || run.equals("off") ? 80 : 160;
				add(ticks, () -> {
					(run.equals("grim-jump") ? o.keyRight : o.keyLeft).setDown(true);
					o.keyJump.setDown(run.equals("grim-jump"));
				});
				add(1, () -> {
					o.keyLeft.setDown(false);
					o.keyRight.setDown(false);
					call("showcaseEnable", "Kill Aura", false);
				});
				add(1, keysOff);
				add(1, () -> {
					double dealt = 0;
					for (var e : mc.level.entitiesForRendering()) {
						if (e instanceof net.minecraft.world.entity.monster.zombie.Husk h) {
							dealt += h.getMaxHealth() - h.getHealth();
						}
					}
					System.out.println("TEMPLE_GRIM " + name + String.format(java.util.Locale.ROOT, " damage dealt %.1f", dealt));
				});
				command(mc, "execute as @e[type=husk] run data get entity @s Health");
			}
			command(mc, "kill @e[type=husk]");
		}
		add(1, keysOff);
		add(1, () -> mc.player.connection.sendCommand("say TEMPLE_PHASE done"));
		add(40, () -> { });
	}
	private void shot(Minecraft mc, String name) {
		add(2, () -> Screenshot.grab(mc.gameDirectory, "showcase-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1, c -> { }));
	}
	private void command(Minecraft mc, String command) {
		add(3, () -> mc.player.connection.sendCommand(command));
	}
	@SuppressWarnings("unchecked")
	private void plan(Minecraft mc) {
		mc.options.pauseOnLostFocus = false;
		if (System.getProperty("temple.showcase.only", "").contains("GrimMove")) {
			grimMove(mc);
			return;
		}
		command(mc, "gamemode creative");
		command(mc, "time set 6000");
		command(mc, "weather clear");
		command(mc, "tp @s ~ ~ ~ 0 28");
		command(mc, "fill ~-16 ~-1 ~-6 ~16 ~-1 ~24 minecraft:grass_block");
		add(1, () -> System.out.println("TEMPLE_SHOWCASE player at " + mc.player.position()
				+ " light " + mc.level.getMaxLocalRawBrightness(mc.player.blockPosition())));
		add(200, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		String only = System.getProperty("temple.showcase.only", "");
		if (only.contains("OreSim")) {
			add(1, () -> call("showcaseEnable", "OreSim", true));
			for (int i = 0; i < 6; i++) {
				add(100, () -> System.out.println("TEMPLE_ORESIM " + call("oreSimVerify")));
			}
			add(1, () -> call("showcaseSet", "OreSim", "Hidden only", "false"));
			command(mc, "tp @s ~ -40 ~");
			command(mc, "gamemode spectator");
			add(80, () -> { });
			shot(mc, "oresim");
			add(1, () -> System.out.println("TEMPLE_ORESIM " + call("oreSimVerify")));
		}
		if (only.contains("CombatNew")) {
			command(mc, "gamemode survival");
			command(mc, "kill @e[type=!player]");
			command(mc, "fill ~-2 ~ ~1 ~2 ~4 ~20 minecraft:air");
			command(mc, "summon husk ~ ~ ~15 {NoAI:1b,Health:20f}");
			float[] before = new float[1];
			add(20, () -> {
				call("showcaseSet", "Reach", "Infinite", "true");
				call("showcaseEnable", "Reach", !only.contains("Control"));
				mc.player.setYRot(0);
				mc.player.setXRot(0);
			});
			add(5, () -> {
				var husk = mc.level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Husk.class,
						mc.player.getBoundingBox().inflate(30)).stream().findFirst().orElse(null);
				System.out.println("TEMPLE_REACH husk " + (husk == null ? "missing" : husk.distanceTo(mc.player) + "m health " + husk.getHealth())
						+ " reach on " + call("showcaseEnabled", "Reach"));
				if (husk != null) {
					before[0] = husk.getHealth();
					mc.gameMode.attack(mc.player, husk);
				}
			});
			add(10, () -> {
				var husk = mc.level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Husk.class,
						mc.player.getBoundingBox().inflate(30)).stream().findFirst().orElse(null);
				System.out.println("TEMPLE_REACH " + (husk == null ? "missing" : (husk.getHealth() < before[0] ? "HIT " : "MISS ")
						+ before[0] + " -> " + husk.getHealth()) + " player at " + mc.player.position());
			});
			command(mc, "kill @e[type=husk]");
			command(mc, "item replace entity @s hotbar.0 with minecraft:stick");
			command(mc, "item replace entity @s hotbar.1 with minecraft:wooden_sword[enchantments={sharpness:5}]");
			command(mc, "summon husk ~ ~ ~2 {NoAI:1b,Health:20f}");
			float[] swapBefore = new float[1];
			add(20, () -> {
				call("showcaseEnable", "Reach", false);
				call("showcaseEnable", "Attribute Swap", true);
				mc.player.getInventory().setSelectedSlot(0);
			});
			add(40, () -> {
				var husk = mc.level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Husk.class,
						mc.player.getBoundingBox().inflate(6)).stream().findFirst().orElse(null);
				if (husk != null) {
					swapBefore[0] = husk.getHealth();
					mc.gameMode.attack(mc.player, husk);
				}
			});
			add(10, () -> {
				var husk = mc.level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Husk.class,
						mc.player.getBoundingBox().inflate(6)).stream().findFirst().orElse(null);
				System.out.println("TEMPLE_SWAP " + (husk == null ? "missing" : swapBefore[0] + " -> " + husk.getHealth())
						+ " selected slot " + mc.player.getInventory().getSelectedSlot());
				call("showcaseEnable", "Attribute Swap", false);
			});
			long[] mark = new long[2];
			add(1, () -> {
				call("showcaseSet", "Timer", "Speed", "2");
				call("showcaseEnable", "Timer", true);
				mark[0] = System.nanoTime();
				mark[1] = mc.player.tickCount;
			});
			add(60, () -> {
				double secs = (System.nanoTime() - mark[0]) / 1e9;
				System.out.println("TEMPLE_TIMER " + String.format(java.util.Locale.ROOT, "%.1f", (mc.player.tickCount - mark[1]) / secs) + " ticks/s");
				call("showcaseEnable", "Timer", false);
			});
		}
		if (only.contains("MacroTest")) {
			command(mc, "gamemode creative");
			command(mc, "kill @e[type=!player]");
			command(mc, "item replace entity @s hotbar.0 with minecraft:respawn_anchor");
			command(mc, "item replace entity @s hotbar.1 with minecraft:glowstone");
			command(mc, "item replace entity @s hotbar.2 with minecraft:totem_of_undying");
			command(mc, "item replace entity @s hotbar.3 with minecraft:obsidian");
			command(mc, "item replace entity @s hotbar.4 with minecraft:end_crystal");
			net.minecraft.core.BlockPos[] spot = new net.minecraft.core.BlockPos[2];
			add(20, () -> {
				call("showcaseEnable", "Macro", true);
				net.minecraft.core.BlockPos feet = mc.player.blockPosition();
				spot[0] = feet.offset(0, 0, 3);
				mc.player.getInventory().setSelectedSlot(0);
				net.minecraft.core.BlockPos floor = spot[0].below();
				mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(
						net.minecraft.world.phys.Vec3.atCenterOf(floor).add(0, 0.5, 0), net.minecraft.core.Direction.UP, floor, false));
				System.out.println("TEMPLE_MACRO anchor placed " + mc.level.getBlockState(spot[0]));
			});
			add(30, () -> System.out.println("TEMPLE_MACRO anchor after " + mc.level.getBlockState(spot[0]) + " slot "
					+ mc.player.getInventory().getSelectedSlot()));
			command(mc, "fill 40 3 40 46 3 46 minecraft:stone");
			command(mc, "fill 40 4 40 46 8 46 minecraft:air");
			command(mc, "tp @s 43 4 43");
			add(10, () -> spot[1] = new net.minecraft.core.BlockPos(41, 4, 42));
			add(5, () -> {
				mc.player.getInventory().setSelectedSlot(3);
				net.minecraft.core.BlockPos floor = spot[1].below();
				mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(
						net.minecraft.world.phys.Vec3.atCenterOf(floor).add(0, 0.5, 0), net.minecraft.core.Direction.UP, floor, false));
				System.out.println("TEMPLE_MACRO obsidian placed " + mc.level.getBlockState(spot[1]));
			});
			int[] crystalsSeen = new int[1];
			for (int i = 0; i < 30; i++) {
				add(1, () -> {
					if (!mc.level.getEntitiesOfClass(net.minecraft.world.entity.boss.enderdragon.EndCrystal.class,
							new net.minecraft.world.phys.AABB(spot[1]).inflate(2)).isEmpty()) {
						crystalsSeen[0]++;
					}
				});
			}
			add(1, () -> System.out.println("TEMPLE_MACRO crystal seen for " + crystalsSeen[0] + " ticks, now "
					+ mc.level.getEntitiesOfClass(net.minecraft.world.entity.boss.enderdragon.EndCrystal.class,
							new net.minecraft.world.phys.AABB(spot[1]).inflate(2)).size() + " left; slot " + mc.player.getInventory().getSelectedSlot()));
		}
		if (only.contains("Pickers")) {
			add(1, () -> call("openMenu", (Object) null));
			add(30, () -> call("showcaseMenu", "picker", "Block ESP/Blocks"));
			add(20, () -> { });
			shot(mc, "picker-blocks");
			add(1, () -> call("showcaseMenu", "picker", "ESP/Entities"));
			add(20, () -> { });
			shot(mc, "picker-entities");
			add(1, () -> mc.gui.setScreen(null));
		}
		if (only.contains("KnifeShots")) {
			command(mc, "item replace entity @s hotbar.0 with minecraft:netherite_sword");
			add(5, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.getInventory().setSelectedSlot(0);
				mc.player.setXRot(0);
				String skin = System.getProperty("temple.showcase.knife", "Karambit");
				call("showcaseSet", "Knives", "Knife", skin);
				call("showcaseEnable", "Knives", true);
			});
			add(6, () -> { });
			shot(mc, "knife-draw");
			add(40, () -> { });
			shot(mc, "knife-idle");
			for (String h : List.of("0.1", "0.3")) {
				add(1, () -> call("showcaseSet", "Knives", "Height", h));
				add(2, () -> { });
				shot(mc, "knife-idle-" + h);
			}
			add(1, () -> call("showcaseSet", "Knives", "Height", "0.2"));
			add(1, () -> call("showcaseKnife", "swing"));
			for (int i = 1; i <= 4; i++) {
				add(1, () -> { });
				shot(mc, "knife-swing" + i);
			}
			add(30, () -> call("showcaseKnife", "inspect"));
			add(30, () -> { });
			shot(mc, "knife-inspect");
			add(30, () -> { });
			shot(mc, "knife-inspect2");
			add(1, () -> call("showcaseEnable", "Knives", false));
		}
		if (only.contains("KnifeGems")) {
			command(mc, "item replace entity @s hotbar.0 with minecraft:netherite_sword");
			add(5, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.getInventory().setSelectedSlot(0);
				mc.player.setXRot(0);
				call("showcaseEnable", "Knives", true);
			});
			for (String skin : List.of("M9 Bayonet | Emerald", "M9 Bayonet | Sapphire", "Karambit | Obsidian", "Butterfly | Emerald",
					"Stiletto | Tiger tooth", "M9 Bayonet | Autotronic", "Karambit")) {
				String file = skin.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
				add(1, () -> call("showcaseSet", "Knives", "Knife", skin));
				add(50, () -> { });
				shot(mc, "gem-" + file);
				add(1, () -> call("showcaseKnife", "inspect"));
				add(45, () -> { });
				shot(mc, "gem-" + file + "-inspect");
				add(60, () -> { });
			}
			add(1, () -> {
				call("showcaseSet", "Knives", "Knife", "M9 Bayonet | Emerald");
				call("showcaseSet", "Knives", "Finish", "Flat");
			});
			add(50, () -> { });
			shot(mc, "gem-flat-m9-emerald");
			add(1, () -> call("showcaseSet", "Knives", "Finish", "Polished"));
			add(1, () -> call("showcaseEnable", "Knives", false));
		}
		if (only.contains("KnifeArms")) {
			command(mc, "item replace entity @s hotbar.0 with minecraft:netherite_sword");
			add(5, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.getInventory().setSelectedSlot(0);
				mc.player.setXRot(0);
				call("showcaseEnable", "Knives", true);
			});
			for (String skin : List.of("Karambit", "M9 Bayonet", "Butterfly", "Stiletto")) {
				String file = skin.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
				for (String len : List.of("100", "55")) {
					add(1, () -> {
						call("showcaseSet", "Knives", "Knife", skin);
						call("showcaseSet", "Knives", "Arm length", len);
					});
					add(50, () -> { });
					shot(mc, "arms-" + file + "-" + len);
					add(1, () -> call("showcaseKnife", "inspect"));
					add(40, () -> { });
					shot(mc, "arms-" + file + "-" + len + "-inspect");
					add(60, () -> { });
				}
			}
			add(1, () -> {
				call("showcaseSet", "Knives", "Arm length", "60");
				call("showcaseEnable", "Knives", false);
			});
		}
		if (only.contains("KnifeDraw")) {
			command(mc, "item replace entity @s hotbar.0 with minecraft:air");
			add(5, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.getInventory().setSelectedSlot(0);
				mc.player.setXRot(0);
				call("showcaseSet", "Knives", "Knife", System.getProperty("temple.showcase.knife", "M9 Bayonet"));
				call("showcaseEnable", "Knives", true);
			});
			add(20, () -> mc.player.connection.sendCommand("item replace entity @s hotbar.0 with minecraft:netherite_sword"));
			for (int i = 0; i < 12; i++) {
				shot(mc, "kdraw-" + i);
			}
			add(60, () -> mc.player.setXRot(-45));
			add(10, () -> { });
			shot(mc, "kdraw-below");
			add(1, () -> {
				mc.player.setXRot(0);
				call("showcaseSet", "Knives", "Material", "Match sword");
			});
			for (String sword : List.of("wooden", "stone", "iron", "golden", "diamond", "netherite")) {
				add(1, () -> mc.player.connection.sendCommand("item replace entity @s hotbar.0 with minecraft:" + sword + "_sword"));
				add(45, () -> { });
				shot(mc, "kmat-" + sword);
			}
			add(1, () -> {
				call("showcaseSet", "Knives", "Material", "Knife's own");
				call("showcaseEnable", "Knives", false);
			});
		}
		if (only.contains("Library")) {
			add(1, () -> {
				try {
					System.setProperty("temple.showcase.configs", FakeConfigLibrary.start());
				} catch (java.io.IOException e) {
					throw new java.io.UncheckedIOException(e);
				}
				call("openMenu", (Object) null);
			});
			add(30, () -> call("showcaseMenu", "library", ""));
			add(40, () -> { });
			shot(mc, "library-list");
			add(1, () -> call("showcaseMenu", "librarysorts", ""));
			add(10, () -> { });
			shot(mc, "library-sorts");
			add(1, () -> call("showcaseMenu", "librarysort", "comments"));
			add(30, () -> { });
			shot(mc, "library-sorted-comments");
			add(1, () -> call("showcaseMenu", "librarysort", "top"));
			add(30, () -> { });
			add(1, () -> call("showcaseMenu", "libraryopen", "K7Q2M"));
			add(30, () -> { });
			shot(mc, "library-detail");
			add(1, () -> call("showcaseMenu", "publish", "Crystal PvP v2|Faster anchors and a calmer HUD."));
			add(20, () -> { });
			shot(mc, "library-publish");
			add(1, () -> call("showcaseMenu", "publishsend", ""));
			add(30, () -> { });
			shot(mc, "library-published");
			add(1, () -> {
				mc.gui.setScreen(null);
				FakeConfigLibrary.stop();
			});
		}
		if (only.contains("ModelShots")) {
			String[] models = System.getProperty("temple.showcase.models", "Sendagaya Shino").split(",");
			add(5, () -> {
				mc.player.setXRot(15);
				call("showcaseSet", "Custom Models", "Model", models[0]);
				call("showcaseEnable", "Custom Models", true);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			});
			for (String name : models) {
				add(1, () -> call("showcaseSet", "Custom Models", "Model", name));
				add(100, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
				add(20, () -> { });
				shot(mc, "model-" + name + "-back");
				add(1, () -> mc.player.setYRot(mc.player.getYRot() + 90));
				add(10, () -> { });
				shot(mc, "model-" + name + "-side");
				add(1, () -> {
					mc.player.setYRot(mc.player.getYRot() - 90);
					mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				});
				add(10, () -> { });
				shot(mc, "model-" + name + "-front");
				add(1, () -> {
					mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
					mc.player.setYRot(mc.player.getYRot() + 90);
					mc.options.keyUp.setDown(true);
				});
				add(15, () -> { });
				shot(mc, "model-" + name + "-walk");
				add(1, () -> {
					mc.options.keyUp.setDown(false);
					mc.options.keyShift.setDown(true);
				});
				add(10, () -> { });
				shot(mc, "model-" + name + "-sneak");
				add(1, () -> {
					mc.options.keyShift.setDown(false);
					mc.player.setYRot(mc.player.getYRot() - 90);
				});
				add(10, () -> { });
			}
			add(1, () -> call("showcaseEnable", "Custom Models", false));
		}
		if (only.contains("ModelHands")) {
			add(5, () -> {
				mc.player.setXRot(10);
				mc.player.getInventory().setSelectedSlot(8);
				mc.player.getInventory().setItem(8, net.minecraft.world.item.ItemStack.EMPTY);
				call("showcaseSet", "Custom Models", "Model", "Sendagaya Shino");
				call("showcaseSet", "Custom Models", "First-person hands", "true");
				call("showcaseEnable", "Custom Models", true);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
			add(120, () -> { });
			shot(mc, "hands-avatar");
			add(1, () -> mc.options.keyUp.setDown(true));
			add(12, () -> { });
			shot(mc, "hands-avatar-walk");
			add(1, () -> {
				mc.options.keyUp.setDown(false);
				mc.options.keyAttack.setDown(true);
			});
			add(3, () -> mc.options.keyAttack.setDown(false));
			shot(mc, "hands-avatar-swing");
			add(10, () -> call("showcaseSet", "Custom Models", "First-person hands", "false"));
			add(10, () -> { });
			shot(mc, "hands-vanilla");
			add(1, () -> call("showcaseEnable", "Custom Models", false));
		}
		if (only.contains("ModelBlend")) {
			add(5, () -> {
				mc.player.setXRot(15);
				call("showcaseSet", "Custom Models", "Model", "Sendagaya Shino");
				call("showcaseEnable", "Custom Models", true);
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			});
			add(120, () -> mc.options.keyLeft.setDown(true));
			add(20, () -> { });
			shot(mc, "blend-left");
			add(1, () -> {
				mc.options.keyLeft.setDown(false);
				mc.options.keyRight.setDown(true);
			});
			add(2, () -> { });
			shot(mc, "blend-left-to-right-2t");
			add(3, () -> { });
			shot(mc, "blend-left-to-right-5t");
			add(15, () -> { });
			shot(mc, "blend-right");
			add(1, () -> {
				mc.options.keyRight.setDown(false);
			});
			add(20, () -> mc.options.keyShift.setDown(true));
			add(2, () -> { });
			shot(mc, "blend-crouch-2t");
			add(15, () -> { });
			shot(mc, "blend-crouch");
			add(1, () -> mc.options.keyShift.setDown(false));
			add(2, () -> { });
			shot(mc, "blend-stand-2t");
			add(10, () -> call("showcaseEnable", "Custom Models", false));
		}
		if (only.contains("ModelPoses")) {
			String[] models = System.getProperty("temple.showcase.models", "Sendagaya Shino").split(",");
			add(5, () -> {
				call("showcaseSet", "Custom Models", "Model", models[0]);
				call("showcaseEnable", "Custom Models", true);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			});
			for (String name : models) {
				String tag = "pose-" + name.replace(' ', '_');
				add(1, () -> call("showcaseSet", "Custom Models", "Model", name));
				add(100, () -> {
					mc.player.setXRot(15);
					mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				});
				add(20, () -> { });
				shot(mc, tag + "-stand");
				add(1, () -> mc.player.setXRot(85));
				add(10, () -> { });
				shot(mc, tag + "-above");
				add(1, () -> mc.player.setXRot(-60));
				add(10, () -> { });
				shot(mc, tag + "-below");
				add(1, () -> {
					mc.player.setXRot(15);
					mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				});
				add(10, () -> { });
				shot(mc, tag + "-front");
				add(1, () -> mc.options.keyShift.setDown(true));
				add(12, () -> { });
				shot(mc, tag + "-sneak");
				add(1, () -> mc.options.keyUp.setDown(true));
				add(12, () -> { });
				shot(mc, tag + "-sneakwalk");
				add(1, () -> mc.options.keyShift.setDown(false));
				add(15, () -> { });
				shot(mc, tag + "-walk");
				add(1, () -> mc.player.setSprinting(true));
				add(15, () -> mc.player.setSprinting(true));
				shot(mc, tag + "-sprint");
				add(1, () -> {
					mc.player.setSprinting(false);
					mc.options.keyUp.setDown(false);
					mc.options.keyLeft.setDown(true);
				});
				add(15, () -> { });
				shot(mc, tag + "-strafe");
				add(1, () -> {
					mc.options.keyLeft.setDown(false);
					mc.options.keyDown.setDown(true);
				});
				add(15, () -> { });
				shot(mc, tag + "-back");
				add(1, () -> mc.options.keyDown.setDown(false));
				add(20, () -> { });
				for (int i = 0; i < 8; i++) {
					add(1, () -> mc.player.setYRot(mc.player.getYRot() + 9));
				}
				shot(mc, tag + "-turn");
				add(20, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
				add(1, () -> mc.options.keyJump.setDown(true));
				add(5, () -> mc.options.keyJump.setDown(false));
				shot(mc, tag + "-jump");
				add(20, () -> {
					mc.player.getAbilities().flying = true;
					mc.player.setDeltaMovement(0, 0.6, 0);
				});
				add(20, () -> { });
				shot(mc, tag + "-fly");
				add(1, () -> mc.player.setXRot(85));
				add(10, () -> { });
				shot(mc, tag + "-fly-above");
				add(1, () -> {
					mc.player.getAbilities().flying = false;
					mc.player.setXRot(15);
				});
				add(6, () -> { });
				shot(mc, tag + "-fall");
				add(40, () -> { });
				command(mc, "setblock ~ ~1 ~ minecraft:glass");
				add(20, () -> mc.player.setXRot(40));
				shot(mc, tag + "-crawl");
				add(1, () -> mc.options.keyUp.setDown(true));
				add(4, () -> mc.options.keyUp.setDown(false));
				shot(mc, tag + "-crawl-move");
				command(mc, "fill ~-2 ~-1 ~-2 ~2 ~3 ~2 minecraft:air");
				command(mc, "fill ~-2 ~-1 ~-2 ~2 ~-1 ~2 minecraft:grass_block");
				add(20, () -> mc.player.setXRot(15));
			}
			add(1, () -> call("showcaseEnable", "Custom Models", false));
		}
		if (only.contains("ModelGuide")) {
			String name = System.getProperty("temple.showcase.models", "Sendagaya Shino").split(",")[0];
			command(mc, "time set 1000");
			command(mc, "item replace entity @s weapon.mainhand with minecraft:cherry_log");
			add(5, () -> {
				call("showcaseSet", "Custom Models", "Model", name);
				call("showcaseEnable", "Custom Models", true);
				mc.player.setXRot(-12);
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				mc.options.fov().set(30);
			});
			add(260, () -> { });
			shot(mc, "guide-avatar");
			add(1, () -> {
				mc.options.fov().set(70);
				call("showcaseEnable", "Custom Models", false);
			});
		}
		if (only.contains("ModelHands")) {
			String name = System.getProperty("temple.showcase.models", "Sendagaya Shino").split(",")[0];
			add(5, () -> {
				call("showcaseSet", "Custom Models", "Model", name);
				call("showcaseEnable", "Custom Models", true);
				mc.player.setXRot(15);
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			});
			command(mc, "item replace entity @s weapon.mainhand with minecraft:oak_log");
			add(100, () -> { });
			shot(mc, "hands-block-front");
			add(1, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			add(10, () -> { });
			shot(mc, "hands-block-back");
			command(mc, "item replace entity @s weapon.mainhand with minecraft:diamond_sword");
			add(1, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			add(10, () -> { });
			shot(mc, "hands-sword-front");
			command(mc, "gamemode survival");
			command(mc, "item replace entity @s weapon.mainhand with minecraft:iron_pickaxe");
			command(mc, "setblock ^ ^ ^2 minecraft:stone");
			add(10, () -> {
				mc.player.setXRot(30);
				mc.options.keyAttack.setDown(true);
			});
			for (int i = 0; i < 6; i++) {
				add(1, () -> { });
				shot(mc, "hands-mine-" + i);
			}
			add(1, () -> mc.options.keyAttack.setDown(false));
			add(1, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			add(10, () -> { });
			for (int i = 0; i < 3; i++) {
				int n = i;
				add(1, () -> mc.options.keyAttack.setDown(true));
				add(2, () -> { });
				shot(mc, "hands-mine-back-" + n);
			}
			add(1, () -> {
				mc.options.keyAttack.setDown(false);
				mc.player.setXRot(15);
			});
			command(mc, "gamemode creative");
			command(mc, "setblock ^ ^ ^2 minecraft:air");
			add(1, () -> mc.options.keyLeft.setDown(true));
			add(25, () -> { });
			shot(mc, "hands-strafe-left");
			add(1, () -> {
				mc.options.keyLeft.setDown(false);
				mc.options.keyRight.setDown(true);
			});
			add(6, () -> { });
			shot(mc, "hands-strafe-right-6t");
			add(1, () -> mc.options.keyRight.setDown(false));
			add(1, () -> call("showcaseEnable", "Custom Models", false));
		}
		if (only.contains("ModelHeight")) {
			String[] models = System.getProperty("temple.showcase.models", "Sendagaya Shino").split(",");
			command(mc, "tp @s ~ ~ ~ 0 0");
			command(mc, "fill ~-1 ~ ~ ~-1 ~1 ~ minecraft:stone");
			add(5, () -> {
				call("showcaseSet", "Custom Models", "Model", models[0]);
				call("showcaseEnable", "Custom Models", true);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			});
			for (String name : models) {
				add(1, () -> call("showcaseSet", "Custom Models", "Model", name));
				add(200, () -> {
					mc.player.setXRot(0);
					mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				});
				shot(mc, "height-" + name.replace(' ', '_'));
			}
			add(1, () -> call("showcaseEnable", "Custom Models", false));
			add(5, () -> { });
			shot(mc, "height-vanilla");
			command(mc, "fill ~-1 ~ ~ ~-1 ~1 ~ minecraft:air");
		}
		if (only.contains("ModelJiggle")) {
			String name = System.getProperty("temple.showcase.models", "Sendagaya Shino").split(",")[0];
			add(5, () -> {
				call("showcaseSet", "Custom Models", "Model", name);
				call("showcaseEnable", "Custom Models", true);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			});
			for (int i = 0; i < 40; i++) {
				add(60, () -> {
					if (mc.gui.screen() == null) {
						call("showcaseSet", "Custom Models", "Jiggle regions", "");
					}
				});
			}
			add(20, () -> System.out.println("TEMPLE_JIGGLE screen " + mc.gui.screen()));
			shot(mc, "jiggle-painter-empty");
			add(1, () -> {
				call("showcaseSet", "Custom Models", "Brush", "Jiggle");
				for (double y = 0.26; y <= 0.55; y += 0.025) {
					staticCall("nz.temple.client.gui.JiggleEditorScreen", "showcaseStroke", 0.42, y, 0.58, y);
				}
				call("showcaseSet", "Custom Models", "Brush", "Mass");
				staticCall("nz.temple.client.gui.JiggleEditorScreen", "showcaseStroke", 0.33, 0.27, 0.38, 0.27);
				call("showcaseSet", "Custom Models", "Brush", "Jiggle");
			});
			add(10, () -> { });
			shot(mc, "jiggle-painter");
			add(1, () -> staticCall("nz.temple.client.gui.JiggleEditorScreen", "showcaseView", 150f, 10f));
			add(10, () -> { });
			shot(mc, "jiggle-painter-back");
			add(1, () -> {
				mc.gui.setScreen(null);
				mc.player.setXRot(10);
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			});
			add(20, () -> { });
			shot(mc, "jiggle-front");
			add(1, () -> mc.options.keyJump.setDown(true));
			add(4, () -> { });
			shot(mc, "jiggle-front-jump");
			add(5, () -> { });
			shot(mc, "jiggle-front-land");
			add(1, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			for (int i = 0; i < 30; i++) {
				add(1, () -> System.out.println("TEMPLE_JIGGLE reach " + staticCall("nz.temple.client.modules.cosmetic.CustomModels", "showcaseJiggleReach")
						+ " gap " + staticCall("nz.temple.client.modules.cosmetic.CustomModels", "showcaseSeamGap")));
			}
			add(1, () -> {
				mc.options.keyJump.setDown(false);
				mc.options.keyUp.setDown(true);
			});
			add(8, () -> { });
			shot(mc, "jiggle-walk");
			add(1, () -> mc.options.keyUp.setDown(false));
			add(4, () -> System.out.println("TEMPLE_JIGGLE stop reach " + staticCall("nz.temple.client.modules.cosmetic.CustomModels", "showcaseJiggleReach")));
			shot(mc, "jiggle-stop");
			add(1, () -> call("showcaseEnable", "Custom Models", false));
		}
		if (only.contains("SongMenu")) {
			add(1, () -> {
				String dir = System.getProperty("temple.showcase.songDir", ".");
				System.setProperty("temple.music.fake", "Neon Rivers|The Glass Arcade|Night Drive|200|19");
				System.setProperty("temple.music.fakeArt", dir + "/fake_cover.png");
				System.setProperty("temple.music.fakeLyrics", dir + "/fake.lrc");
				call("showcaseEnable", "Music", true);
			});
			add(60, () -> mc.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen()));
			add(60, () -> { });
			shot(mc, "song-menu");
			add(60, () -> { });
			shot(mc, "song-menu-later");
			add(1, () -> call("showcaseTitle", "button:song:Singleplayer"));
			add(40, () -> { });
			shot(mc, "song-menu-panel");
			add(1, () -> call("showcaseTitle", "button:song:Singleplayer"));
			add(60, () -> { });
			add(1, () -> call("showcaseTitle", "button:song:Options"));
			add(40, () -> { });
			shot(mc, "song-menu-options");
			add(1, () -> {
				System.clearProperty("temple.music.fake");
				mc.gui.setScreen(null);
				call("showcaseEnable", "Music", false);
			});
		}
		if (only.contains("BaritoneTest")) {
			double[] start = new double[3];
			float[] last = {Float.NaN};
			float[] biggest = {0};
			int[] snaps = {0};
			add(20, () -> {
				start[0] = mc.player.getX();
				start[2] = mc.player.getZ();
				call("showcaseSet", "Baritone", "Task", "Go to");
				call("showcaseSet", "Baritone", "X", String.valueOf((int) start[0] + 40));
				call("showcaseSet", "Baritone", "Z", String.valueOf((int) start[2] + 25));
				call("showcaseEnable", "Baritone", true);
			});
			for (int i = 0; i < 400; i++) {
				add(1, () -> {
					String r = (String) call("showcaseServerRotation");
					if (r == null || r.isEmpty()) {
						return;
					}
					float yaw = Float.parseFloat(r.split(" ")[0]);
					if (!Float.isNaN(last[0])) {
						float d = Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - last[0]));
						biggest[0] = Math.max(biggest[0], d);
						if (d > 45) {
							snaps[0]++;
						}
						if (d > 20) {
							System.out.println("TEMPLE_BARITONE turn " + d + " at tick " + mc.level.getGameTime());
						}
					}
					last[0] = yaw;
				});
			}
			add(1, () -> System.out.println("TEMPLE_BARITONE moved " + Math.round(mc.player.getX() - start[0]) + "," + Math.round(mc.player.getZ() - start[2])
					+ " biggestTurn=" + biggest[0] + " snaps=" + snaps[0] + " enabled=" + call("showcaseEnabled", "Baritone")));
		}
		if (only.contains("KnifeThird")) {
			command(mc, "item replace entity @s hotbar.0 with minecraft:netherite_sword");
			add(5, () -> {
				mc.player.getInventory().setSelectedSlot(0);
				mc.player.setXRot(10);
				call("showcaseSet", "Knives", "Knife", System.getProperty("temple.showcase.knife", "Karambit"));
				call("showcaseEnable", "Knives", true);
			});
			for (int frame = 0; frame < 1; frame++) {
				int f = frame;
				add(1, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
				add(20, () -> { });
				shot(mc, "knife3p-" + f + "-front");
				add(1, () -> mc.player.setYRot(mc.player.getYRot() + 90));
				add(10, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
				add(10, () -> { });
				shot(mc, "knife3p-" + f + "-side");
				add(1, () -> mc.player.setYRot(mc.player.getYRot() - 90));
			}
			add(1, () -> call("showcaseEnable", "Knives", false));
		}
		if (only.contains("SwingShots")) {
			String[][] sets = {
					{"netherite_sword", "Sword swing", "Crescent", "Backhand", "Spin Cut", "Uppercut", "Execution", "Feint", "Lunge"},
					{"netherite_pickaxe", "Pickaxe swing", "Drive", "Double Tap", "Drill", "Side Swing"},
					{"netherite_axe", "Axe swing", "Lumberjack", "Executioner", "Tomahawk", "Whirl"}};
			add(5, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.getInventory().setSelectedSlot(0);
				mc.player.setXRot(0);
				call("showcaseEnable", "Custom Animations", true);
			});
			for (String[] set : sets) {
				command(mc, "item replace entity @s hotbar.0 with minecraft:" + set[0]);
				for (int m = 2; m < set.length; m++) {
					String mode = set[m];
					add(30, () -> call("showcaseSet", "Custom Animations", set[1], mode));
					add(2, () -> mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND));
					for (int f = 1; f <= 4; f++) {
						add(1, () -> { });
						shot(mc, "swing-" + mode.replace(' ', '_') + "-" + f);
					}
				}
			}
			add(1, () -> call("showcaseEnable", "Custom Animations", false));
		}
		if (only.contains("MapStress")) {
			add(1, () -> {
				java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("templenz").resolve("map")
						.resolve((String) call("showcaseServerKey")).resolve("overworld");
				try {
					java.nio.file.Files.createDirectories(dir);
					for (int tx = -15; tx < 15; tx++) {
						for (int tz = -10; tz < 10; tz++) {
							try (com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(256, 256, true)) {
								img.fillRect(0, 0, 256, 256, 0xFF000000 | ((tx * 40) & 0xFF) << 16 | ((tz * 60) & 0xFF) << 8 | 0x60);
								img.writeToFile(dir.resolve(tx + "_" + tz + ".png"));
							}
						}
					}
				} catch (java.io.IOException e) {
					throw new RuntimeException(e);
				}
				call("showcaseEnable", "Map", true);
				call("showcaseReloadMap");
			});
			add(5, () -> call("showcaseOpenMap"));
			add(5, () -> {
				for (int i = 0; i < 10; i++) {
					mc.gui.screen().mouseScrolled(400, 240, 0, -1);
				}
			});
			add(1, () -> mc.gui.screen().mouseClicked(new net.minecraft.client.input.MouseButtonEvent(400, 240,
					new net.minecraft.client.input.MouseButtonInfo(0, 0)), false));
			for (int i = 1; i <= 60; i++) {
				int k = i;
				add(1, () -> mc.gui.screen().mouseDragged(new net.minecraft.client.input.MouseButtonEvent(400 - k * 3, 240 - k * 2,
						new net.minecraft.client.input.MouseButtonInfo(0, 0)), -3, -2));
			}
			add(1, () -> mc.gui.screen().mouseReleased(new net.minecraft.client.input.MouseButtonEvent(220, 120,
					new net.minecraft.client.input.MouseButtonInfo(0, 0))));
			add(40, () -> { });
			shot(mc, "map-stress");
			add(1, () -> System.out.println("TEMPLE_MAPSTRESS survived"));
			add(1, () -> mc.gui.setScreen(null));
		}
		if (only.contains("Trajectories")) {
			add(1, () -> {
				call("showcaseSet", "Trajectories", "Arrows in flight", "true");
				call("showcaseEnable", "Trajectories", true);
			});
			command(mc, "summon ender_pearl ~-3 ~4 ~6 {Motion:[0.45,0.35,0.2d]}");
			command(mc, "summon arrow ~3 ~3 ~5 {Motion:[-0.9,0.4,0.3d]}");
			add(2, () -> { });
			shot(mc, "trajectories");
			add(1, () -> call("showcaseEnable", "Trajectories", false));
		}
		if (only.contains("Chams")) {
			command(mc, "summon skeleton ~1.6 ~ ~2.5 {NoAI:1b,Rotation:[200f,0f]}");
			add(10, () -> { });
			for (String style : List.of("Color", "Image")) {
				add(1, () -> {
					call("showcaseSet", "Chams", "Self", "true");
					call("showcaseSet", "Chams", "Entities", "minecraft:player,minecraft:skeleton");
					call("showcaseSet", "Chams", "Style", style);
					call("showcaseEnable", "Chams", true);
				});
				add(10, () -> { });
				shot(mc, "chams-" + style.toLowerCase());
			}
			add(1, () -> call("showcaseEnable", "Chams", false));
		}
		if (only.contains("Streamproof")) {
			command(mc, "summon skeleton ~1.6 ~ ~4 {NoAI:1b,Rotation:[200f,0f]}");
			add(10, () -> {
				call("showcaseSet", "Chams", "Entities", "minecraft:skeleton");
				call("showcaseEnable", "Chams", true);
				call("showcaseEnable", "ESP", true);
				call("showcaseEnable", "Tracers", true);
			});
			add(1, () -> {
				mc.options.enableVsync().set(false);
				mc.options.framerateLimit().set(260);
			});
			add(80, () -> System.out.println("TEMPLE_STREAMPROOF fps before " + mc.getFps()));
			shot(mc, "streamproof-off");
			add(1, () -> call("openMenu", (Object) null));
			add(30, () -> { });
			shot(mc, "streamproof-menu-off");
			add(1, () -> mc.gui.setScreen(null));
			add(10, () -> call("showcaseEnable", "Streamproof", true));
			add(60, () -> System.out.println("TEMPLE_STREAMPROOF " + call("showcaseStreamproof", "")));
			add(80, () -> System.out.println("TEMPLE_STREAMPROOF fps on " + mc.getFps()));
			shot(mc, "streamproof-recorded");
			add(1, () -> call("showcaseStreamproof", new java.io.File(mc.gameDirectory, "screenshots/showcase-streamproof-overlay.png").getPath()));
			add(10, () -> desktopShot(mc, "streamproof-desktop"));
			add(1, () -> call("openMenu", (Object) null));
			add(30, () -> { });
			shot(mc, "streamproof-menu-recorded");
			add(1, () -> call("showcaseStreamproof", new java.io.File(mc.gameDirectory, "screenshots/showcase-streamproof-menu-overlay.png").getPath()));
			add(10, () -> desktopShot(mc, "streamproof-menu-desktop"));
			add(1, () -> mc.gui.setScreen(null));
			add(1, () -> call("showcaseEnable", "Streamproof", false));
			add(80, () -> System.out.println("TEMPLE_STREAMPROOF after off " + call("showcaseStreamproof", "") + " fps " + mc.getFps()));
			shot(mc, "streamproof-after");
		}
		if (only.contains("Dummy")) {
			add(1, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				call("showcaseSet", "Fake Player", "Health", "12");
				call("showcaseEnable", "Fake Player", true);
			});
			command(mc, "tp @s ~ ~ ~-3.5 0 12");
			command(mc, "item replace entity @s weapon.mainhand with minecraft:diamond_sword");
			add(30, () -> { });
			shot(mc, "dummy-plain");
			add(1, () -> {
				call("showcaseEnable", "Cape", true);
				call("showcaseSet", "Player Aura", "Wings", "true");
				call("showcaseEnable", "Player Aura", true);
			});
			add(30, () -> { });
			shot(mc, "dummy-cosmetics");
			for (int i = 0; i < 3; i++) {
				int hit = i;
				add(1, () -> {
					var dummy = mc.level.getEntity(-1_337_420);
					if (dummy != null) {
						mc.gameMode.attack(mc.player, dummy);
						mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
					}
				});
				add(2, () -> {
					var dummy = mc.level.getEntity(-1_337_420);
					System.out.println("TEMPLE_DUMMY hit " + hit + " health " + (dummy instanceof net.minecraft.world.entity.LivingEntity l
							? l.getHealth() : "gone") + " hurtTime " + (dummy instanceof net.minecraft.world.entity.LivingEntity l2 ? l2.hurtTime : -1));
				});
				shot(mc, "dummy-hit-" + hit);
				add(25, () -> { });
			}
			for (int i = 0; i < 4; i++) {
				add(20, () -> {
					var dummy = mc.level.getEntity(-1_337_420);
					System.out.println("TEMPLE_DUMMY regen " + (dummy instanceof net.minecraft.world.entity.LivingEntity l
							? l.getHealth() + "/" + l.getMaxHealth() + " at " + dummy.position() : "gone"));
				});
			}
			add(1, () -> {
				var dummy = mc.level.getEntity(-1_337_420);
				System.out.println("TEMPLE_DUMMY before crystal health " + (dummy instanceof net.minecraft.world.entity.LivingEntity l
						? l.getHealth() : "gone"));
				if (dummy != null) {
					mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "summon minecraft:end_crystal %.2f %.2f %.2f {ShowBottom:0b}",
							dummy.getX() + 2.5, dummy.getY(), dummy.getZ()));
				}
			});
			add(10, () -> {
				for (var e : mc.level.entitiesForRendering()) {
					if (e instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal) {
						mc.gameMode.attack(mc.player, e);
					}
				}
			});
			add(3, () -> { });
			shot(mc, "dummy-crystal");
			add(2, () -> {
				var dummy = mc.level.getEntity(-1_337_420);
				System.out.println("TEMPLE_DUMMY after crystal health " + (dummy instanceof net.minecraft.world.entity.LivingEntity l
						? l.getHealth() + " at " + dummy.position() : "gone"));
			});
			add(1, () -> {
				call("showcaseEnable", "Cape", false);
				call("showcaseEnable", "Player Aura", false);
				call("showcaseEnable", "Fake Player", false);
				System.out.println("TEMPLE_DUMMY removed " + (mc.level.getEntity(-1_337_420) == null));
			});
		}
		if (only.contains("Quake")) {
			command(mc, "time set 18000");
			add(1, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
				call("showcaseSet", "Skybox", "Style", "Void");
				call("showcaseSet", "Skybox", "Rotation speed", "0");
				call("showcaseEnable", "Skybox", true);
				mc.player.setYRot(-153.4f);
				mc.player.setXRot(-31.3f);
			});
			add(40, () -> { });
			shot(mc, "quake-void-centre");
			add(1, () -> mc.player.setYRot(-133f));
			add(10, () -> { });
			shot(mc, "quake-void-side");
			add(1, () -> {
				mc.player.setYRot(-153.4f);
				mc.player.setXRot(-12f);
			});
			add(10, () -> { });
			shot(mc, "quake-void-low");
			add(1, () -> {
				call("showcaseSet", "Skybox", "Use sky colors", "true");
				mc.player.setXRot(-31.3f);
			});
			add(10, () -> { });
			shot(mc, "quake-void-themed");
			add(1, () -> {
				call("showcaseSet", "Skybox", "Use sky colors", "false");
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.player.setYRot(0);
				mc.player.setXRot(28);
				call("showcaseEnable", "Jump Circles", true);
			});
			add(20, () -> {
				call("showcaseSet", "Jump Circles", "Style", "Earthquake");
			});
			add(5, () -> call("showcasePreview", "Jump Circles"));
			for (int i = 0; i < 8; i++) {
				int frame = i;
				add(1, () -> Screenshot.grab(mc.gameDirectory, "showcase-quake-ground-" + frame + ".png",
						mc.gameRenderer.mainRenderTarget(), 1, c -> { }));
			}
			add(40, () -> { });
			shot(mc, "quake-ground-after");
			add(1, () -> {
				call("showcaseSet", "Jump Circles", "Style", "Ripple");
				call("showcaseEnable", "Jump Circles", false);
				call("showcaseEnable", "Skybox", false);
			});
			command(mc, "time set 6000");
			command(mc, "gamemode survival");
			net.minecraft.core.BlockPos[] dig = new net.minecraft.core.BlockPos[1];
			add(1, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setYRot(0);
				mc.player.setXRot(55);
				call("showcaseEnable", "Break Animation", true);
			});
			add(20, () -> {
				call("showcaseSet", "Break Animation", "Style", "Chip");
				System.out.println("TEMPLE_CHIP style " + call("showcaseSettingValue", "Break Animation", "Style"));
				dig[0] = mc.player.blockPosition().offset(0, -1, 2);
			});
			add(3, () -> mc.gameMode.startDestroyBlock(dig[0], net.minecraft.core.Direction.UP));
			for (int i = 0; i < 12; i++) {
				add(1, () -> mc.gameMode.continueDestroyBlock(dig[0], net.minecraft.core.Direction.UP));
			}
			add(1, () -> System.out.println("TEMPLE_CHIP mode " + mc.gameMode.getPlayerMode() + " block " + mc.level.getBlockState(dig[0])));
			shot(mc, "quake-chip-ground");
			add(1, () -> {
				mc.gameMode.stopDestroyBlock();
				call("showcaseEnable", "Break Animation", false);
			});
			command(mc, "gamemode creative");
		}
		if (only.contains("Aurora")) {
			command(mc, "time set 18000");
			add(1, () -> {
				call("showcaseEnable", "Skybox", true);
				call("showcaseSet", "Aurora", "Crown", "true");
				call("showcaseSet", "Aurora", "Ribbon trail", "true");
				call("showcaseEnable", "Aurora", true);
				call("showcaseEnable", "Air Effects", true);
			});
			add(60, () -> { });
			shot(mc, "aurora-third");
			add(1, () -> mc.player.setXRot(-35));
			add(10, () -> { });
			shot(mc, "aurora-sky");
			add(1, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setXRot(-55);
			});
			add(10, () -> { });
			shot(mc, "aurora-up");
			add(1, () -> {
				call("showcaseEnable", "Aurora", false);
				call("showcaseEnable", "Air Effects", false);
			});
			for (int i = 0; i < 3; i++) {
				int view = i;
				add(1, () -> {
					mc.player.setXRot(view == 2 ? -70 : -25);
					mc.player.setYRot(view * 120f);
				});
				add(10, () -> { });
				shot(mc, "aurora-skybox-" + view);
			}
			add(1, () -> {
				call("showcaseSet", "Skybox", "Rotation speed", "0");
				mc.player.setYRot(-31);
				mc.player.setXRot(-40);
			});
			add(10, () -> { });
			shot(mc, "aurora-constellation");
			for (String style : List.of("Storm", "Nebula", "Tendrils", "Void")) {
				add(1, () -> {
					call("showcaseSet", "Skybox", "Style", style);
					mc.player.setYRot(20);
					mc.player.setXRot(-30);
				});
				add(40, () -> { });
				shot(mc, "sky-" + style.toLowerCase());
			}
			add(1, () -> {
				call("showcaseSet", "Skybox", "Style", "Painted");
				call("showcaseSet", "Skybox", "Aurora", "false");
			});
			float[][] views = {{180, -12}, {-90, -12}, {0, -12}, {90, -12}, {135, -20}, {180, -89}, {-31, -40}};
			String[] labels = {"north", "east", "south", "west", "corner", "up", "constellation"};
			for (int i = 0; i < views.length; i++) {
				float[] v = views[i];
				String label = labels[i];
				add(1, () -> {
					mc.player.setYRot(v[0]);
					mc.player.setXRot(v[1]);
				});
				add(15, () -> { });
				shot(mc, "painted-" + label);
			}
			add(1, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		}
		if (only.contains("BlockInfo")) {
			add(1, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				call("showcaseSet", "Block Highlight", "Filled", "true");
				call("showcaseEnable", "Block Highlight", true);
			});
			add(20, () -> { });
			shot(mc, "blockinfo-cube");
			add(1, () -> call("showcaseSet", "Block Highlight", "Shape", "Face"));
			add(10, () -> { });
			shot(mc, "blockinfo-face");
			add(1, () -> {
				call("showcaseEnable", "Block Highlight", false);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			});
		}
		if (only.contains("Menu")) {
			List<String> lit = new java.util.ArrayList<>();
			for (String name : (List<String>) call("showcaseModules")) {
				if (lit.size() < Integer.getInteger("temple.showcase.lit", 60) && !MENU_SKIP.contains(name)) {
					lit.add(name);
				}
			}
			add(1, () -> call("openMenu", (Object) null));
			add(5, () -> mc.gui.setScreen(null));
			add(1, () -> lit.forEach(name -> call("showcaseEnable", name, true)));
			add(1, () -> call("openMenu", (Object) null));
			add(40, () -> { });
			shot(mc, "menu");
			add(1, () -> call("showcaseSettings", "Crystal Aura"));
			add(40, () -> { });
			shot(mc, "menu-settings");
			add(1, () -> mc.gui.setScreen(null));
			add(1, () -> lit.forEach(name -> call("showcaseEnable", name, false)));
			add(20, () -> { });
		}
		if (only.contains("TitleVersion")) {
			for (String theme : List.of("Landscape", "Black hole", "Default")) {
				add(1, () -> {
					call("showcaseSet", "Menu", "Menu theme", theme);
					mc.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
				});
				add(60, () -> { });
				shot(mc, "title-version-" + theme.replace(' ', '-').toLowerCase());
			}
			add(1, () -> {
				call("showcaseSet", "Menu", "Menu theme", "Landscape");
				mc.gui.setScreen(null);
			});
			add(20, () -> { });
		}
		if (only.contains("SettingSearch")) {
			add(1, () -> call("openMenu", (Object) null));
			add(40, () -> call("showcaseMenu", "search", "range"));
			add(30, () -> { });
			shot(mc, "setting-search");
			add(1, () -> call("showcaseMenu", "search", "wall"));
			add(30, () -> { });
			shot(mc, "setting-search-wall");
			add(1, () -> mc.gui.setScreen(null));
			add(1, () -> call("showcaseSet", "Menu", "Click GUI", "Simple"));
			add(1, () -> call("openMenu", (Object) null));
			add(30, () -> {
				try {
					java.lang.reflect.Field f = mc.gui.screen().getClass().getDeclaredField("search");
					f.setAccessible(true);
					((StringBuilder) f.get(mc.gui.screen())).append("wall");
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
			});
			add(30, () -> { });
			shot(mc, "setting-search-simple");
			add(1, () -> mc.gui.setScreen(null));
			add(1, () -> call("showcaseSet", "Menu", "Click GUI", "Glass"));
			add(20, () -> { });
		}
		if (only.contains("RegionMap")) {
			add(1, () -> {
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				call("showcaseSet", "Donut Region", "Display", "Map");
				call("showcaseSet", "Donut Region", "Map size", "90");
				call("showcaseEnable", "Donut Region", true);
			});
			command(mc, "tp @s 1200 ~ 800");
			add(40, () -> { });
			shot(mc, "region-map-90");
			add(1, () -> call("showcaseSet", "Donut Region", "Map size", "150"));
			command(mc, "tp @s 30000 ~ -30000");
			add(60, () -> { });
			shot(mc, "region-map-150");
			add(1, () -> call("showcaseEnable", "Donut Region", false));
		}
		if (only.contains("UpdateUi")) {
			add(1, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				call("showcaseEnable", "Donut Region", true);
				call("showcaseEnable", "Staff Display", true);
				call("showcaseSet", "Staff Display", "Show when none", "true");
				call("showcaseEnable", "All Legit", true);
			});
			add(1, () -> call("openMenu", (Object) null));
			add(40, () -> { });
			shot(mc, "update-menu");
			add(1, () -> call("showcaseMenu", "search", "au"));
			add(30, () -> { });
			shot(mc, "update-search");
			add(1, () -> {
				call("showcaseMenu", "search", "");
				call("showcaseSettings", "Donut Region");
			});
			add(30, () -> { });
			shot(mc, "update-settings");
			add(1, () -> {
				call("showcaseSettings", "Donut Region");
				call("showcaseMenu", "suggest", "Add a waypoint list that syncs with the region map");
			});
			add(30, () -> { });
			shot(mc, "update-suggest");
			add(1, () -> mc.gui.setScreen(null));
			add(20, () -> call("openMenu", (Object) null));
			add(20, () -> call("showcaseMenu", "edithud", ""));
			add(30, () -> { });
			shot(mc, "update-edithud");
			add(1, () -> mc.gui.setScreen(null));
			add(20, () -> { });
			shot(mc, "update-hud");
			command(mc, "scoreboard objectives add donut dummy \"DonutSMP\"");
			command(mc, "scoreboard objectives setdisplay sidebar donut");
			command(mc, "scoreboard players set TempleShowcase donut 2");
			command(mc, "scoreboard players set $1.5B donut 1");
			command(mc, "setblock ~3 ~ ~6 chest");
			command(mc, "setblock ~-3 ~ ~8 red_shulker_box");
			command(mc, "setblock ~0 ~ ~12 ender_chest");
			add(1, () -> {
				call("showcaseEnable", "Spoof", true);
				call("showcaseEnable", "Storage ESP", true);
				call("showcaseSet", "Storage ESP", "Tracers", "true");
			});
			add(40, () -> { });
			shot(mc, "update-spoof");
			add(1, () -> call("showcaseEnable", "No Block Rotation", true));
			add(60, () -> { });
			shot(mc, "update-noblockrotation");
			add(1, () -> call("showcaseEnable", "No Block Rotation", false));
			add(1, () -> {
				call("showcaseEnable", "Spoof", false);
				call("showcaseEnable", "Storage ESP", false);
			});
			add(1, () -> {
				call("showcaseEnable", "All Legit", false);
				call("showcaseEnable", "Donut Region", false);
				call("showcaseEnable", "Staff Display", false);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			});
		}
		if (only.contains("Break")) {
			command(mc, "gamemode survival");
			command(mc, "time set 6000");
			command(mc, "setblock ~ ~1 ~2 minecraft:stone");
			add(1, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setYRot(0);
				mc.player.setXRot(10);
			});
			add(5, () -> { });
			for (String style : List.of("Nuker", "Liquid", "Brackets", "Chip", "Chip-grass")) {
				String block = style.equals("Chip-grass") ? "minecraft:grass_block" : "minecraft:stone";
				String mode = style.equals("Chip-grass") ? "Chip" : style;
				net.minecraft.core.BlockPos[] target = new net.minecraft.core.BlockPos[1];
				add(1, () -> {
					call("showcaseSet", "Break Animation", "Style", mode);
					call("showcaseEnable", "Break Animation", true);
					target[0] = mc.player.blockPosition().offset(0, 1, 2);
					mc.player.connection.sendCommand("setblock " + target[0].getX() + " " + target[0].getY() + " " + target[0].getZ()
							+ " " + block);
				});
				add(3, () -> mc.gameMode.startDestroyBlock(target[0], net.minecraft.core.Direction.NORTH));
				for (int i = 0; i < 100; i++) {
					add(1, () -> mc.gameMode.continueDestroyBlock(target[0], net.minecraft.core.Direction.NORTH));
					if (i == 1 || i == 45) {
						shot(mc, "break-" + style.toLowerCase() + "-t" + i);
					}
				}
				shot(mc, "break-" + style.toLowerCase());
				for (int i = 0; i < 80; i++) {
					add(1, () -> {
						if (!mc.level.getBlockState(target[0]).isAir()) {
							mc.gameMode.continueDestroyBlock(target[0], net.minecraft.core.Direction.NORTH);
						}
					});
					if (i == 58) {
						shot(mc, "break-" + style.toLowerCase() + "-shatter");
					}
				}
				add(1, () -> System.out.println("TEMPLE_BREAK " + style + " broke=" + mc.level.getBlockState(target[0]).isAir()));
				add(1, () -> mc.gameMode.stopDestroyBlock());
			}
			add(1, () -> call("showcaseEnable", "Break Animation", false));
			command(mc, "gamemode creative");
		}
		if (only.contains("Auto Eat")) {
			command(mc, "gamemode survival");
			command(mc, "item replace entity @s hotbar.0 with minecraft:bread 16");
			add(2, () -> {
				mc.player.getInventory().setSelectedSlot(4);
				var server = mc.getSingleplayerServer();
				java.util.UUID id = mc.player.getUUID();
				server.execute(() -> server.getPlayerList().getPlayer(id).getFoodData().setFoodLevel(6));
			});
			add(5, () -> { });
			add(160, () -> {
				System.out.println("TEMPLE_AUTOEAT before food=" + mc.player.getFoodData().getFoodLevel());
				call("showcaseEnable", "Auto Eat", true);
			});
			add(1, () -> {
				System.out.println("TEMPLE_AUTOEAT after food=" + mc.player.getFoodData().getFoodLevel()
						+ " slot=" + mc.player.getInventory().getSelectedSlot() + " bread=" + mc.player.getInventory().getItem(0).getCount());
				call("showcaseEnable", "Auto Eat", false);
			});
			command(mc, "gamemode creative");
		}
		if (only.contains("Noclip")) {
			command(mc, "gamemode survival");
			command(mc, "fill ~ ~-3 ~ ~ ~-2 ~ minecraft:air");
			add(3, () -> {
				call("showcaseEnable", "Noclip", true);
				System.out.println("TEMPLE_PHASE before y=" + mc.player.getY());
			});
			add(3, () -> mc.options.keyShift.setDown(true));
			add(2, () -> mc.options.keyShift.setDown(false));
			add(10, () -> { });
			add(1, () -> {
				System.out.println("TEMPLE_PHASE after y=" + mc.player.getY());
				call("showcaseEnable", "Noclip", false);
			});
			command(mc, "gamemode creative");
		}
		if (only.contains("R45")) {
			round45(mc);
		}
		if (only.contains("Amb45")) {
			ambience45(mc);
		}
		if (only.contains("Shock45")) {
			command(mc, "gamemode creative");
			command(mc, "time set 6000");
			add(1, () -> {
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				call("showcaseSet", "Explosion Effects", "Impact frames", "false");
				call("showcaseSet", "Explosion Effects", "Shockwave", "true");
				call("showcaseSet", "Explosion Effects", "Wave speed", "30");
				call("showcaseSet", "Explosion Effects", "Wave brightness", "85");
				call("showcaseEnable", "Explosion Effects", true);
				mc.player.setYRot(0);
				mc.player.setXRot(40);
			});
			add(20, () -> { });
			command(mc, "summon creeper ~0 ~ ~10 {Fuse:0,ignited:1b,ExplosionRadius:0b}");
			for (int i = 0; i < 4; i++) {
				add(5, () -> { });
				shot(mc, "shock45-" + i);
			}
			add(40, () -> call("showcaseEnable", "Explosion Effects", false));
		}
		if (only.contains("Wet")) {
			command(mc, "weather rain");
			command(mc, "time set 1000");
			command(mc, "fill ~-4 ~-2 ~3 ~4 ~-1 ~11 minecraft:stone");
			command(mc, "fill ~-3 ~-1 ~4 ~3 ~-1 ~10 minecraft:water");
			command(mc, "fill ~-10 ~-1 ~-2 ~10 ~-1 ~2 minecraft:stone");
			add(1, () -> {
				call("showcaseEnable", "Wet Ground", true);
				call("showcaseEnable", "Freelook", false);
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setYRot(0);
				mc.player.setXRot(50);
			});
			add(80, () -> { });
			shot(mc, "wet-pool");
			command(mc, "weather clear");
		}
		if (only.contains("Emotes")) {
			for (String emote : List.of("Take the L", "The Worm", "Jerk Off", "Dance", "Floss", "Dab", "Headbang", "T-Pose", "Zombie")) {
				add(1, () -> {
					call("showcaseSet", "Emotes", "Emote", emote);
					call("showcaseSet", "Emotes", "Stop when moving", "false");
					call("showcaseEnable", "Emotes", true);
				});
				add(12, () -> { });
				for (int frame = 0; frame < 4; frame++) {
					shot(mc, "emote-" + emote.toLowerCase().replace(" ", "-") + "-" + frame);
					add(4, () -> { });
				}
				add(1, () -> call("showcaseEnable", "Emotes", false));
			}
		}
		if (only.contains("Pets")) {
			command(mc, "time set 13000");
			add(1, () -> {
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				mc.player.setXRot(15);
				call("showcaseSet", "Pets", "Size", "1.6");
				call("showcaseSet", "Pets", "Color", "Natural");
			});
			for (String pet : List.of("Dragon", "Jellyfish", "Rune Orb", "Ghost")) {
				add(1, () -> {
					call("showcaseSet", "Pets", "Pet", pet);
					call("showcaseEnable", "Pets", true);
				});
				add(40, () -> { });
				shot(mc, "pet-" + pet.toLowerCase().replace(' ', '-'));
			}
			add(1, () -> call("showcaseEnable", "Pets", false));
		}
		if (only.contains("SugScaffold")) {
			command(mc, "gamemode survival");
			command(mc, "effect give @s resistance 99999 4 true");
			command(mc, "time set 6000");
			add(1, () -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
			String[][] runs = {{"God bridge", "false", "true"}, {"Speed bridge", "false", "true"}, {"Blatant", "false", "true"},
					{"God bridge", "true", "false"}, {"Speed bridge", "true", "false"}, {"Blatant", "false", "false"}};
			for (int i = 0; i < runs.length; i++) {
				String[] run = runs[i];
				int x = 1000 + i * 8;
				int index = i;
				command(mc, "tp @s " + x + ".5 100 0.5 0 20");
				add(20, () -> { });
				command(mc, "fill " + (x - 1) + " 99 -1 " + (x + 1) + " 99 1 minecraft:stone");
				command(mc, "fill " + (x - 1) + " 99 2 " + (x + 1) + " 99 60 minecraft:air");
				command(mc, "tp @s " + x + ".5 100 0.5 0 20");
				command(mc, "clear @s");
				command(mc, "give @s minecraft:oak_planks 128");
				command(mc, "give @s minecraft:diamond_sword 1");
				add(10, () -> {
					mc.player.getInventory().setSelectedSlot(2);
					mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
					mc.player.setYRot(0);
					mc.player.setXRot(20);
					call("showcaseSet", "Scaffold", "Mode", run[0]);
					call("showcaseSet", "Scaffold", "Legit", run[1]);
					call("showcaseSet", "Scaffold", "Tower", "true");
					call("showcaseSet", "Scaffold", "Extend", "0");
					call("showcaseSet", "Scaffold", "Sprint", "true");
					call("showcaseSet", "Scaffold", "Safety sneak", "true");
					call("showcaseSet", "Scaffold", "Turn speed", "70");
					call("showcaseEnable", "Scaffold", true);
				});
				for (int t = 0; t < 70; t++) {
					int tick = t;
					add(1, () -> {
						mc.options.keyUp.setDown(true);
						mc.options.keySprint.setDown(run[2].equals("true"));
						if (tick % 5 == 0) {
							System.out.println("TEMPLE_SCAFFOLD_TRACE run=" + index + " t=" + tick + " pos=" + mc.player.position()
									+ " sprint=" + mc.player.isSprinting() + " sneak=" + mc.player.isShiftKeyDown()
									+ " cam=" + mc.player.getYRot() + "/" + mc.player.getXRot());
						}
					});
					if (t == 40) {
						shot(mc, "sug-scaffold-" + index);
					}
				}
				add(1, () -> {
					mc.options.keyUp.setDown(false);
					mc.options.keySprint.setDown(false);
				});
				add(15, () -> {
					int laid = 0;
					for (int z = 2; z < 60; z++) {
						if (!mc.level.getBlockState(new net.minecraft.core.BlockPos(x, 99, z)).isAir()) {
							laid = z - 1;
						}
					}
					System.out.println("TEMPLE_SCAFFOLD run=" + index + " mode=" + run[0] + " legit=" + run[1] + " sprintHeld=" + run[2]
							+ " laid=" + laid + " y=" + String.format("%.2f", mc.player.getY()) + " z=" + String.format("%.2f", mc.player.getZ())
							+ (mc.player.getY() < 99.9 ? " FELL" : " ok"));
					call("showcaseEnable", "Scaffold", false);
				});
			}
			command(mc, "tp @s 1020.5 100 -8.5 0 20");
			add(20, () -> { });
			command(mc, "fill 1020 99 -8 1020 99 -8 minecraft:stone");
			command(mc, "tp @s 1020.5 100 -7.5 0 20");
			command(mc, "give @s minecraft:oak_planks 64");
			for (String towerMode : List.of("God bridge", "Speed bridge", "Blatant")) {
				add(10, () -> {
					call("showcaseSet", "Scaffold", "Mode", towerMode);
					call("showcaseSet", "Scaffold", "Legit", "false");
					call("showcaseSet", "Scaffold", "Tower", "true");
					call("showcaseEnable", "Scaffold", true);
				});
				double[] startY = new double[1];
				add(1, () -> startY[0] = mc.player.getY());
				for (int t = 0; t < 60; t++) {
					add(1, () -> mc.options.keyJump.setDown(true));
				}
				add(1, () -> mc.options.keyJump.setDown(false));
				add(20, () -> {
					System.out.println("TEMPLE_SCAFFOLD tower mode=" + towerMode + " rose=" + String.format("%.2f", mc.player.getY() - startY[0]));
					call("showcaseEnable", "Scaffold", false);
				});
			}
			command(mc, "gamemode creative");
		}
		if (only.contains("Cos46Cape")) {
			command(mc, "gamemode creative");
			command(mc, "time set 6000");
			double[] start = new double[3];
			add(1, () -> {
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				start[0] = mc.player.getX();
				start[1] = mc.player.getY();
				start[2] = mc.player.getZ();
				mc.player.setYRot(0);
				mc.player.setXRot(5);
				call("showcaseSet", "Freelook", "Cinematic", "false");
				call("showcaseSet", "Freelook", "Distance", "3.2");
				call("showcaseEnable", "Freelook", true);
				call("showcaseSet", "Cape", "Who", "Self");
				call("showcaseSet", "Cape", "Cape", "Colours");
				call("showcaseSet", "Cape", "Hem", "Straight");
				call("showcaseSet", "Cape", "Clasp", "Studs");
				call("showcaseSet", "Cape", "Material", "Matte");
				call("showcaseEnable", "Cape", true);
			});
			add(2, () -> call("showcaseOrbit", 25f));
			for (String style : (List<String>) call("showcaseOptions", "Cape", "Style")) {
				add(1, () -> {
					call("showcaseSet", "Cape", "Style", style);
					mc.player.setPos(start[0], start[1], start[2]);
				});
				add(25, () -> { });
				shot(mc, "cos46-cape-" + style.toLowerCase() + "-stand");
				for (int i = 0; i < 18; i++) {
					add(1, () -> {
						mc.player.setYRot(0);
						mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0.28);
					});
				}
				shot(mc, "cos46-cape-" + style.toLowerCase() + "-run");
			}
			add(1, () -> call("showcaseSet", "Cape", "Style", "Royal"));
			for (String hemShape : List.of("Rounded", "Pointed", "Swallowtail", "Tattered")) {
				add(1, () -> {
					call("showcaseSet", "Cape", "Hem", hemShape);
					mc.player.setPos(start[0], start[1], start[2]);
				});
				add(20, () -> { });
				shot(mc, "cos46-cape-hem-" + hemShape.toLowerCase());
			}
			add(1, () -> {
				call("showcaseSet", "Cape", "Hem", "Swallowtail");
				call("showcaseSet", "Cape", "Shimmer", "true");
				call("showcaseSet", "Cape", "Material", "Gold");
				mc.player.setPos(start[0], start[1], start[2]);
			});
			for (int i = 0; i < 3; i++) {
				add(6, () -> { });
				shot(mc, "cos46-cape-shimmer-gold-" + i);
			}
			add(1, () -> {
				call("showcaseSet", "Cape", "Shimmer", "false");
				call("showcaseSet", "Cape", "Material", "Matte");
				mc.options.keyShift.setDown(true);
			});
			add(20, () -> { });
			shot(mc, "cos46-cape-crouch");
			add(1, () -> mc.options.keyShift.setDown(false));
			add(2, () -> call("showcaseOrbit", 65f));
			add(10, () -> { });
			shot(mc, "cos46-cape-side");
			add(1, () -> call("showcaseSet", "Cape", "Clasp", "Chain"));
			add(2, () -> call("showcaseOrbit", 90f));
			add(10, () -> { });
			shot(mc, "cos46-cape-chain-a");
			add(2, () -> call("showcaseOrbit", 180f));
			add(10, () -> { });
			shot(mc, "cos46-cape-chain-b");
			add(1, () -> {
				call("showcaseSet", "Cape", "Cape", "Image");
				call("showcaseSet", "Cape", "Cape image", System.getProperty("temple.showcase.gif", ""));
				mc.player.setPos(start[0], start[1], start[2]);
			});
			add(2, () -> call("showcaseOrbit", 25f));
			add(30, () -> { });
			for (int i = 0; i < 3; i++) {
				shot(mc, "cos46-cape-gif-" + i);
				add(4, () -> { });
			}
			add(1, () -> {
				call("showcaseSet", "Cape", "Cape", "Colours");
				call("showcaseEnable", "Cape", false);
				call("showcaseEnable", "Freelook", false);
			});
		}
		if (only.contains("Cos46Totem")) {
			command(mc, "time set 6000");
			command(mc, "gamemode survival");
			add(1, () -> {
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				mc.player.setYRot(0);
				mc.player.setXRot(-8);
				call("showcaseSet", "Freelook", "Cinematic", "false");
				call("showcaseSet", "Freelook", "Distance", "4.2");
				call("showcaseEnable", "Freelook", true);
				call("showcaseSet", "Totem Pops", "Who", "Players");
				call("showcaseSet", "Totem Pops", "Spirit", "false");
				call("showcaseSet", "Totem Pops", "Notification", "false");
				call("showcaseSet", "Totem Pops", "Screen totem", "Hidden");
				call("showcaseEnable", "Totem Pops", true);
			});
			add(2, () -> call("showcaseOrbit", 180f));
			for (String style : List.of("Effigy", "Shield")) {
				add(1, () -> call("showcaseSet", "Totem Pops", "Style", style));
				command(mc, "item replace entity @s weapon.offhand with minecraft:totem_of_undying");
				command(mc, "damage @s 100 minecraft:generic");
				int frames = style.equals("Effigy") ? 9 : 4;
				for (int i = 0; i < frames; i++) {
					shot(mc, "cos46-totem-" + style.toLowerCase() + "-" + i);
					add(1, () -> { });
				}
				add(30, () -> { });
			}
			add(1, () -> {
				call("showcaseEnable", "Freelook", false);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
			for (String screen : List.of("Smaller", "Vanilla")) {
				add(1, () -> call("showcaseSet", "Totem Pops", "Screen totem", screen));
				command(mc, "item replace entity @s weapon.offhand with minecraft:totem_of_undying");
				command(mc, "damage @s 100 minecraft:generic");
				add(6, () -> { });
				shot(mc, "cos46-totem-screen-" + screen.toLowerCase());
				add(40, () -> { });
			}
			command(mc, "gamemode creative");
			add(1, () -> call("showcaseEnable", "Totem Pops", false));
		}
		if (only.contains("Cos46Steps")) {
			command(mc, "time set 6000");
			command(mc, "effect clear @s");
			for (String style : List.of("Flames", "Frost", "Blossom", "Prints")) {
				add(1, () -> {
					mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
					mc.options.setCameraType(CameraType.FIRST_PERSON);
					if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
					mc.player.setYRot(0);
					mc.player.setXRot(0);
					call("showcaseSet", "Footsteps", "Style", style);
					call("showcaseSet", "Footsteps", "Size", "1.6");
					call("showcaseEnable", "Footsteps", true);
					call("showcasePreview", "Footsteps");
					mc.player.setYRot(34.5f);
					mc.player.yRotO = 34.5f;
					mc.player.setXRot(22);
					mc.player.xRotO = 22;
				});
				add(2, () -> { });
				shot(mc, "cos46-steps-" + style.toLowerCase() + "-a");
				add(26, () -> { });
				shot(mc, "cos46-steps-" + style.toLowerCase() + "-b");
				add(1, () -> call("showcaseEnable", "Footsteps", false));
			}
			add(1, () -> {
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
				mc.player.setXRot(0);
			});
		}
		if (only.contains("WorldFx52")) {
			command(mc, "gamemode creative");
			command(mc, "time set 6000");
			command(mc, "weather clear");
			for (int[] t : new int[][] {{8, 6}, {-7, 9}, {12, -5}, {-10, -8}, {4, -14}, {16, 10}}) {
				command(mc, "place feature minecraft:oak ~" + t[0] + " ~ ~" + t[1]);
			}
			for (int[] t : new int[][] {{3, 2}, {-3, 4}, {5, -3}, {-4, -4}, {9, 1}}) {
				command(mc, "setblock ~" + t[0] + " ~ ~" + t[1] + " minecraft:torch");
			}
			add(20, () -> {
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				call("showcaseEnable", "Fullbright", false);
				call("showcaseEnable", "Skybox", false);
				call("showcaseSet", "Ambience", "Intensity", "100");
				call("showcaseEnable", "Player Aura", false);
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setPos(mc.player.getX(), mc.player.getY() + 3, mc.player.getZ());
				mc.player.getAbilities().flying = true;
				call("showcaseEnable", "Ambience", true);
			});
			for (String effect : List.of("None", "Midnight", "Golden hour", "Blood moon", "Storm", "Dawn mist", "Arctic")) {
				String slug = "worldfx52-" + effect.toLowerCase().replace(' ', '-');
				add(1, () -> call("showcaseSet", "Ambience", "World effect", effect));
				add(1, () -> {
					mc.player.setYRot(-90);
					mc.player.setXRot(-12);
				});
				add(40, () -> { });
				shot(mc, slug + "-east");
				add(1, () -> {
					mc.player.setYRot(180);
					mc.player.setXRot(-18);
				});
				add(20, () -> { });
				shot(mc, slug + "-north");
				add(1, () -> {
					mc.player.setYRot(30);
					mc.player.setXRot(25);
				});
				add(20, () -> { });
				shot(mc, slug + "-ground");
				if (effect.equals("Storm")) {
					for (int k = 0; k < 6; k++) {
						add(1, () -> {
							mc.player.setYRot(-90);
							mc.player.setXRot(-8);
						});
						add(50, () -> { });
						shot(mc, slug + "-sky-" + k);
					}
				}
			}
			for (String effect : List.of("Midnight", "Blood moon")) {
				for (String k : List.of("40", "180")) {
					add(1, () -> {
						call("showcaseSet", "Ambience", "World effect", effect);
						call("showcaseSet", "Ambience", "Intensity", k);
						mc.player.setYRot(-90);
						mc.player.setXRot(-12);
					});
					add(40, () -> { });
					shot(mc, "worldfx52-" + effect.toLowerCase().replace(' ', '-') + "-intensity-" + k);
				}
			}
			add(1, () -> {
				call("showcaseSet", "Ambience", "Intensity", "100");
				call("showcaseSet", "Ambience", "World effect", "None");
				call("showcaseEnable", "Ambience", false);
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
		}
		if (only.contains("Legacy52")) {
			command(mc, "gamemode creative");
			command(mc, "time set 6000");
			add(1, () -> {
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				call("showcaseEnable", "Fullbright", false);
				call("showcaseRealism", "Player Aura", false);
				call("showcaseSet", "Player Aura", "Halo", "true");
				call("showcaseSet", "Player Aura", "Wings", "true");
				call("showcaseSet", "Player Aura", "Wing style", "Feathered");
				call("showcaseSet", "Trails", "Type", "Tube");
				call("showcaseEnable", "Player Aura", true);
				call("openMenu", (Object) null);
			});
			add(10, () -> call("showcaseSettings", "Player Aura"));
			add(10, () -> { });
			shot(mc, "legacy52-settings-new");
			add(1, () -> call("showcasePieceRealism", "Wings", true));
			add(10, () -> { });
			shot(mc, "legacy52-settings-wings-realism");
			add(1, () -> call("showcaseSettings", "Trails"));
			add(10, () -> { });
			shot(mc, "legacy52-settings-trails");
			add(1, () -> {
				mc.gui.setScreen(null);
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
				mc.player.setYRot(0);
				mc.player.setXRot(10);
				call("showcaseSet", "Freelook", "Cinematic", "false");
				call("showcaseSet", "Freelook", "Distance", "3.2");
				call("showcaseEnable", "Freelook", true);
			});
			add(2, () -> call("showcaseOrbit", 150f));
			add(20, () -> { });
			shot(mc, "legacy52-aura-mixed");
			add(1, () -> call("showcaseRealism", "Player Aura", false));
			add(20, () -> { });
			shot(mc, "legacy52-aura-original");
			add(1, () -> call("showcaseEnable", "Player Aura", false));
			for (String module : List.of("Pets", "Trails", "Footsteps", "Aurora")) {
				add(1, () -> {
					call("showcaseRealism", module, false);
					call("showcaseSet", module, "Who", "Self");
					call("showcaseEnable", module, true);
					if (module.equals("Aurora")) {
						mc.player.connection.sendCommand("time set 18000");
					}
				});
				add(30, () -> { });
				for (int i = 0; i < 20; i++) {
					add(1, () -> mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0.2));
				}
				shot(mc, "legacy52-" + module.toLowerCase());
				add(1, () -> {
					call("showcaseEnable", module, false);
					mc.player.connection.sendCommand("time set 6000");
				});
			}
			add(1, () -> {
				call("showcaseEnable", "Freelook", false);
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
		}
		if (only.contains("Fog54")) {
			int[] o = new int[3];
			add(1, () -> {
				o[0] = mc.player.getBlockX();
				o[1] = mc.player.getBlockY();
				o[2] = mc.player.getBlockZ();
			});
			command(mc, "gamemode creative");
			command(mc, "time set 23300");
			command(mc, "weather clear");
			add(3, () -> mc.player.connection.sendCommand("fill %d %d %d %d %d %d minecraft:stone".formatted(o[0] + 8, o[1], o[2] + 10, o[0] + 16, o[1] + 5, o[2] + 18)));
			add(3, () -> mc.player.connection.sendCommand("fill %d %d %d %d %d %d minecraft:stone".formatted(o[0] - 16, o[1], o[2] + 14, o[0] - 6, o[1] + 9, o[2] + 24)));
			add(3, () -> mc.player.connection.sendCommand("fill %d %d %d %d %d %d minecraft:grass_block".formatted(o[0] - 4, o[1], o[2] + 30, o[0] + 12, o[1] + 14, o[2] + 40)));
			add(3, () -> mc.player.connection.sendCommand("fill %d %d %d %d %d %d minecraft:oak_planks".formatted(o[0] + 18, o[1] + 4, o[2] + 4, o[0] + 26, o[1] + 4, o[2] + 12)));
			add(3, () -> {
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
				call("showcaseSet", "Ambience", "Ground fog", "true");
				call("showcaseSet", "Ambience", "Lies at", "On the ground");
				call("showcaseSet", "Ambience", "Fog depth", "3");
				call("showcaseSet", "Ambience", "Fog density", "85");
				call("showcaseEnable", "Ambience", true);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
			add(3, () -> mc.player.connection.sendCommand("tp @s %d %d %d 0 30".formatted(o[0], o[1] + 20, o[2] - 8)));
			add(40, () -> { });
			shot(mc, "fog54-above");
			add(3, () -> mc.player.connection.sendCommand("tp @s %d %d %d 20 4".formatted(o[0], o[1], o[2] - 4)));
			add(40, () -> { });
			shot(mc, "fog54-ground");
			add(3, () -> mc.player.connection.sendCommand("tp @s %d %d %d 0 25".formatted(o[0] - 11, o[1] + 10, o[2] + 19)));
			add(40, () -> { });
			shot(mc, "fog54-mound");
			add(1, () -> {
				call("showcaseEnable", "Ambience", false);
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
		}
		if (only.contains("Matte54")) {
			add(1, () -> {
				call("showcaseSet", "Menu", "Click GUI", "Simple");
				call("showcaseSet", "Glass", "Roundness", "100");
				call("openMenu", (Object) null);
			});
			add(20, () -> { });
			shot(mc, "matte54-grid");
			add(1, () -> call("showcaseSettings", "Auto Clicker"));
			add(15, () -> { });
			shot(mc, "matte54-settings");
			add(1, () -> {
				call("showcaseSet", "Glass", "Roundness", "0");
				mc.gui.setScreen(null);
				call("openMenu", (Object) null);
			});
			add(20, () -> { });
			shot(mc, "matte54-square");
			add(1, () -> {
				call("showcaseSet", "Glass", "Roundness", "100");
				call("showcaseSet", "Menu", "Click GUI", "Glass");
				mc.gui.setScreen(null);
			});
		}
		if (only.contains("Scaffold54")) {
			String[][] runs = {{"God bridge", "false"}, {"Speed bridge", "false"}, {"Blatant", "false"}, {"God bridge", "true"}};
			for (int r = 0; r < runs.length; r++) {
				String[] run = runs[r];
				int lane = r * 6;
				command(mc, "gamemode survival");
				command(mc, "clear @s");
				command(mc, "give @s minecraft:stone 64");
				command(mc, "fill ~" + lane + " 120 ~ ~" + (lane + 2) + " 120 ~2 minecraft:stone");
				command(mc, "fill ~" + lane + " 119 ~3 ~" + (lane + 2) + " 119 ~60 minecraft:air");
				command(mc, "tp @s ~" + (lane + 1.5) + " 121 ~2.6 0 30");
				add(10, () -> {
					call("showcaseSet", "Scaffold", "Mode", run[0]);
					call("showcaseSet", "Scaffold", "Legit", run[1]);
					call("showcaseEnable", "Scaffold", true);
					mc.player.getInventory().setSelectedSlot(0);
				});
				double[] start = new double[3];
				add(5, () -> {
					start[0] = mc.player.getX();
					start[1] = mc.player.getY();
					start[2] = mc.player.getZ();
					mc.options.keyUp.setDown(true);
				});
				add(60, () -> { });
				add(1, () -> {
					mc.options.keyUp.setDown(false);
					int placed = 0;
					for (int z = 0; z < 60; z++) {
						for (int x = -1; x <= 1; x++) {
							if (!mc.level.getBlockState(net.minecraft.core.BlockPos.containing(start[0] + x, 120, start[2] + 1 + z)).isAir()) {
								placed++;
							}
						}
					}
					System.out.println("TEMPLE_SCAFFOLD mode=" + run[0] + " legit=" + run[1] + " placed=" + placed + " travelled="
							+ String.format("%.1f", mc.player.getZ() - start[2]) + " fell=" + (mc.player.getY() < start[1] - 1)
							+ " stoneLeft=" + mc.player.getInventory().countItem(net.minecraft.world.item.Items.STONE));
					call("showcaseEnable", "Scaffold", false);
				});
				shot(mc, "scaffold54-" + r);
			}
			command(mc, "gamemode creative");
		}
		if (only.contains("Vram54")) {
			java.util.Set<String> skip = java.util.Set.of("Freecam", "Freelook", "Zoom", "Xray", "Streamproof", "Hide HUD", "No Render",
					"Ore ESP", "OreSim", "Chunk Finder", "Sus Chunk Finder", "Fake Player", "Spinbot", "Emotes", "Motion Blur");
			java.util.List<String> on = new java.util.ArrayList<>();
			add(1, () -> {
				mc.options.enableVsync().set(false);
				mc.options.framerateLimit().set(260);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				for (String category : List.of("RENDER", "COSMETICS")) {
					@SuppressWarnings("unchecked")
					java.util.List<String> names = (java.util.List<String>) call("showcaseCategory", category);
					for (String name : names) {
						if (!skip.contains(name) && Boolean.TRUE.equals(call("showcaseEnable", name, true))) {
							on.add(name);
						}
					}
				}
				System.out.println("TEMPLE_VRAM enabled " + on.size() + " " + on);
			});
			for (int i = 0; i < 20 * 60 * 3; i++) {
				int f = i;
				add(1, () -> {
					mc.getFramerateLimitTracker().onInputReceived();
					mc.player.setYRot(f * 2);
					mc.player.setDeltaMovement(-Math.sin(Math.toRadians(f * 2)) * 0.25, mc.player.getDeltaMovement().y,
							Math.cos(Math.toRadians(f * 2)) * 0.25);
					if (f % 200 == 0) {
						long w = mc.getWindow().handle();
						System.out.println("TEMPLE_VRAM t=" + f / 20 + "s fps=" + mc.getFps() + " heapMB="
								+ (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576
								+ " minimised=" + org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(w, org.lwjgl.glfw.GLFW.GLFW_ICONIFIED)
								+ " focused=" + org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(w, org.lwjgl.glfw.GLFW.GLFW_FOCUSED)
								+ " quads=" + call("showcaseMeshQuads"));
					}
				});
			}
			add(1, () -> {
				for (String name : on) {
					call("showcaseEnable", name, false);
				}
			});
		}
		if (only.contains("Cos52Fps")) {
			command(mc, "gamemode creative");
			command(mc, "time set 6000");
			String[] worn = {"Wings", "Halo", "Gyroscope", "Crystals", "Crown", "Orbiting blades", "Shield shell", "Horns", "Ears",
					"Rune circle", "Spirits", "Ribbons"};
			String[] modules = {"Player Aura", "Trails", "Footsteps", "Pets", "Aurora", "Contrails"};
			add(1, () -> {
				call("showcaseRealism", "Player Aura", true);
				call("showcaseRealism", "Trails", true);
				call("showcaseRealism", "Footsteps", true);
				call("showcaseRealism", "Pets", true);
				call("showcaseRealism", "Aurora", true);
				call("showcaseRealism", "Contrails", true);
				mc.options.enableVsync().set(false);
				mc.options.framerateLimit().set(260);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				call("showcaseEnable", "Fullbright", false);
				for (String m : modules) {
					call("showcaseEnable", m, false);
				}
			});
			Runnable sample = () -> mc.getFramerateLimitTracker().onInputReceived();
			add(1, () -> {
				call("showcaseSet", "Player Aura", "Wing style", "Feathered");
				call("showcaseSet", "Player Aura", "Who", "Self");
				call("showcaseSet", "Trails", "Type", "Elemental");
				call("showcaseSet", "Trails", "Element", "Fire");
				call("showcaseSet", "Trails", "Who", "Self");
				call("showcaseSet", "Footsteps", "Who", "Self");
				call("showcaseSet", "Pets", "Pet", "Dragon");
				call("showcaseSet", "Aurora", "Who", "Self");
				for (String piece : worn) {
					call("showcaseSet", "Player Aura", piece, "false");
				}
			});
			java.util.List<String> cases = new java.util.ArrayList<>(List.of("none"));
			for (String piece : worn) {
				cases.add("aura:" + piece);
			}
			for (String m : modules) {
				if (!m.equals("Player Aura")) {
					cases.add(m);
				}
			}
			cases.add("all");
			if (only.contains("Cos52Fps:")) {
				java.util.List<String> keep = java.util.Arrays.asList(only.substring(only.indexOf("Cos52Fps:") + 9).split(";"));
				cases.removeIf(cs -> !keep.contains(cs));
			}
			for (String cs : cases) {
				add(1, () -> {
					for (String piece : worn) {
						call("showcaseSet", "Player Aura", piece, String.valueOf(cs.equals("all") || cs.equals("aura:" + piece)));
					}
					for (String m : modules) {
						boolean on = cs.equals("all") || cs.equals(m) || (m.equals("Player Aura") && cs.startsWith("aura:"));
						call("showcaseEnable", m, on);
					}
				});
				for (int k = 0; k < 3; k++) {
					add(25, sample);
				}
				for (int i = 0; i < 60; i++) {
					int f = i;
					add(1, () -> {
						sample.run();
						mc.player.setYRot(f * 3);
						mc.player.setDeltaMovement(-Math.sin(Math.toRadians(f * 3)) * 0.2, mc.player.getDeltaMovement().y,
								Math.cos(Math.toRadians(f * 3)) * 0.2);
						if (f == 59) {
							System.out.println("TEMPLE_FPS case " + cs + " " + mc.getFps() + " quads " + call("showcaseMeshQuads"));
						}
					});
				}
			}
			add(1, () -> {
				for (String m : modules) {
					call("showcaseEnable", m, false);
				}
			});
		}
		if (only.contains("Cos52Final")) {
			command(mc, "gamemode creative");
			command(mc, "time set 6000");
			command(mc, "effect clear @s");
			command(mc, "kill @e[type=!player]");
			double[] home = new double[3];
			add(1, () -> {
				call("showcaseRealism", "Death Effects", true);
				call("showcaseRealism", "Contrails", true);
				call("showcaseRealism", "Aurora", true);
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				call("showcaseEnable", "Fullbright", false);
				call("showcaseEnable", "Player Aura", false);
				call("showcaseEnable", "Pets", false);
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
				home[0] = mc.player.getX();
				home[1] = mc.player.getY();
				home[2] = mc.player.getZ();
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setYRot(0);
				mc.player.setXRot(8);
				call("showcaseSet", "Death Effects", "Who", "Everything");
				call("showcaseEnable", "Death Effects", true);
			});
			for (String style : List.of("Ghost", "Crumble", "Ashes")) {
				for (String mob : List.of("spider", "pig", "husk")) {
					add(1, () -> call("showcaseSet", "Death Effects", "Style", style));
					command(mc, "summon minecraft:" + mob + " ~ ~ ~4 {NoAI:1b,Silent:1b,Rotation:[150f,0f]}");
					add(8, () -> { });
					command(mc, "damage @e[type=" + mob + ",limit=1] 100 minecraft:player_attack by @s");
					int[] waits = {4, 12, 20};
					for (int w = 0; w < waits.length; w++) {
						int k = w;
						add(waits[w], () -> { });
						shot(mc, "cos52-death-" + style.toLowerCase() + "-" + mob + "-" + k);
					}
					add(50, () -> { });
				}
			}
			add(1, () -> call("showcaseEnable", "Death Effects", false));
			command(mc, "item replace entity @s armor.chest with minecraft:elytra");
			add(1, () -> {
				mc.player.setPos(home[0], home[1] + 30, home[2]);
				call("showcaseSet", "Freelook", "Cinematic", "false");
				call("showcaseSet", "Freelook", "Distance", "5");
				call("showcaseEnable", "Freelook", true);
				call("showcaseSet", "Contrails", "Who", "Self");
			});
			for (String style : List.of("Vapour", "Streamers", "Embers")) {
				add(1, () -> {
					mc.player.setPos(home[0], home[1] + 30, home[2] - 30);
					call("showcaseSet", "Contrails", "Style", style);
					call("showcaseEnable", "Contrails", true);
				});
				for (int i = 0; i < 40; i++) {
					int f = i;
					add(1, () -> {
						var p = mc.player;
						if (!p.isFallFlying()) {
							p.getAbilities().flying = false;
							p.startFallFlying();
						}
						p.setDeltaMovement(0.25 * Math.sin(f * 0.15), 0, 1.6);
						p.setYRot((float) Math.toDegrees(-Math.atan2(0.25 * Math.sin(f * 0.15), 1.6)));
						p.setXRot(6);
					});
					if (i == 0) {
						add(1, () -> call("showcaseOrbit", 120f));
					}
				}
				shot(mc, "cos52-contrail-" + style.toLowerCase() + "-side");
				add(1, () -> call("showcaseOrbit", -120f));
				for (int i = 0; i < 8; i++) {
					add(1, () -> mc.player.setDeltaMovement(0, 0, 1.6));
				}
				shot(mc, "cos52-contrail-" + style.toLowerCase() + "-behind");
				add(1, () -> {
					call("showcaseEnable", "Contrails", false);
					mc.player.stopFallFlying();
					mc.player.getAbilities().flying = true;
				});
			}
			command(mc, "item replace entity @s armor.chest with minecraft:air");
			add(1, () -> {
				mc.player.connection.sendCommand("time set 18000");
				mc.player.setPos(home[0], home[1], home[2]);
				mc.player.getAbilities().flying = false;
				call("showcaseSet", "Freelook", "Distance", "6");
				call("showcaseSet", "Aurora", "Veil", "true");
				call("showcaseSet", "Aurora", "Who", "Self");
				call("showcaseEnable", "Aurora", true);
			});
			add(1, () -> call("showcaseOrbit", 80f));
			for (String react : List.of("false", "true")) {
				add(1, () -> {
					mc.player.setPos(home[0], home[1], home[2] - 8);
					call("showcaseSet", "Aurora", "React to movement", react);
				});
				add(30, () -> { });
				shot(mc, "cos52-aurora-react-" + react + "-still");
				for (int i = 0; i < 30; i++) {
					add(1, () -> {
						mc.player.setYRot(0);
						mc.player.setSprinting(true);
						mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0.28);
					});
				}
				shot(mc, "cos52-aurora-react-" + react + "-moving");
				add(25, () -> { });
				shot(mc, "cos52-aurora-react-" + react + "-settled");
			}
			add(1, () -> {
				call("showcaseEnable", "Aurora", false);
				call("showcaseEnable", "Freelook", false);
				mc.player.connection.sendCommand("time set 6000");
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
		}
		if (only.contains("Cos52Pets")) {
			command(mc, "gamemode survival");
			command(mc, "time set 6000");
			command(mc, "effect give @s resistance 99999 4 true");
			command(mc, "summon zombie ~2 ~ ~3 {NoAI:1b,PersistenceRequired:1b,Health:1000f,attributes:[{id:\"minecraft:max_health\",base:1000}]}");
			add(1, () -> {
				call("showcaseRealism", "Pets", true);
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				call("showcaseEnable", "Fullbright", false);
				call("showcaseEnable", "Player Aura", false);
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
				mc.player.setYRot(0);
				mc.player.setXRot(10);
				call("showcaseSet", "Pets", "Position", "Shoulder");
				call("showcaseSet", "Freelook", "Cinematic", "false");
				call("showcaseSet", "Freelook", "Distance", "2.6");
				call("showcaseEnable", "Freelook", true);
			});
			for (String pet : (List<String>) call("showcaseOptions", "Pets", "Pet")) {
				String slug = "cos52-pet-" + pet.toLowerCase().replace(' ', '-');
				add(1, () -> {
					mc.player.connection.sendCommand("time set 6000");
					call("showcaseSet", "Pets", "Pet", pet);
					call("showcaseEnable", "Pets", true);
				});
				add(50, () -> { });
				for (int view = 0; view < 4; view++) {
					int v = view;
					add(2, () -> call("showcaseOrbit", 90f));
					add(4, () -> { });
					shot(mc, slug + "-" + v);
				}
				add(1, () -> {
					mc.player.setYRot(-34);
					call("showcaseOrbit", 120f);
				});
				add(10, () -> {
					net.minecraft.world.entity.Entity z = mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(6),
							e -> e instanceof net.minecraft.world.entity.monster.zombie.Zombie).stream().findFirst().orElse(null);
					if (z != null) {
						mc.gameMode.attack(mc.player, z);
						mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
					}
				});
				add(5, () -> { });
				shot(mc, slug + "-hit");
				add(15, () -> mc.player.connection.sendCommand("damage @s 1"));
				add(3, () -> { });
				shot(mc, slug + "-hurt");
				add(1, () -> {
					mc.player.setYRot(0);
					call("showcaseOrbit", -120f);
					mc.player.connection.sendCommand("time set 18000");
				});
				add(20, () -> { });
				shot(mc, slug + "-night");
				add(1, () -> call("showcaseEnable", "Pets", false));
			}
			add(1, () -> {
				mc.player.connection.sendCommand("time set 6000");
				call("showcaseEnable", "Freelook", false);
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
		}
		if (only.contains("Cos52Trails")) {
			command(mc, "gamemode creative");
			command(mc, "time set 6000");
			double[] home = new double[3];
			add(1, () -> {
				call("showcaseRealism", "Trails", true);
				call("showcaseRealism", "Footsteps", true);
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				call("showcaseEnable", "Fullbright", false);
				call("showcaseEnable", "Player Aura", false);
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
				home[0] = mc.player.getX();
				home[1] = mc.player.getY();
				home[2] = mc.player.getZ();
				call("showcaseSet", "Freelook", "Cinematic", "false");
				call("showcaseSet", "Freelook", "Distance", "4.2");
				call("showcaseEnable", "Freelook", true);
				call("showcaseSet", "Trails", "Who", "Self");
				call("showcaseSet", "Trails", "Length", "1.2");
				call("showcaseEnable", "Trails", true);
			});
			add(2, () -> call("showcaseOrbit", 100f));
			String[][] runs = {{"Elemental", "Element", "Fire"}, {"Elemental", "Element", "Frost"}, {"Elemental", "Element", "Stardust"},
					{"Elemental", "Element", "Smoke"}, {"Elemental", "Element", "Lightning"}, {"Energy", "Ribbons from", "Hands"},
					{"Energy", "Ribbons from", "Feet"}, {"Energy", "Ribbons from", "Back"}, {"Afterimages", "", ""}};
			for (String time : List.of("6000", "18000")) {
				for (String[] run : runs) {
					boolean night = time.equals("18000");
					if (night && !run[0].equals("Elemental") && !run[2].equals("Hands")) {
						continue;
					}
					String slug = "cos52-trail-" + (run[2].isEmpty() ? run[0] : run[2]).toLowerCase() + (night ? "-night" : "");
					add(1, () -> {
						mc.player.connection.sendCommand("time set " + time);
						mc.player.setPos(home[0], home[1], home[2] - 12);
						mc.player.setYRot(0);
						call("showcaseSet", "Trails", "Type", run[0]);
						if (!run[1].isEmpty()) {
							call("showcaseSet", "Trails", run[1], run[2]);
						}
					});
					add(10, () -> { });
					for (int i = 0; i < 30; i++) {
						add(1, () -> {
							mc.player.setYRot(0);
							mc.player.setSprinting(true);
							mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0.3);
							mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
						});
					}
					shot(mc, slug + "-side");
					if (!night) {
						add(1, () -> call("showcaseOrbit", 60f));
						for (int i = 0; i < 3; i++) {
							add(1, () -> mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0.3));
						}
						shot(mc, slug + "-ahead");
						add(1, () -> call("showcaseOrbit", -60f));
						if (run[0].equals("Elemental")) {
							add(1, () -> mc.player.jumpFromGround());
							add(14, () -> { });
							add(3, () -> { });
							shot(mc, slug + "-land");
						}
					}
					add(25, () -> { });
				}
			}
			add(1, () -> {
				mc.player.connection.sendCommand("time set 6000");
				call("showcaseEnable", "Trails", false);
				call("showcaseSet", "Freelook", "Distance", "3.2");
				call("showcaseTilt", 35f);
				call("showcaseSet", "Footsteps", "Who", "Self");
				call("showcaseEnable", "Footsteps", true);
			});
			for (String step : List.of("Prints", "Puddles", "Petals", "Flames", "Frost", "Blossom")) {
				add(1, () -> {
					mc.player.setPos(home[0], home[1], home[2] - 6);
					mc.player.setYRot(0);
					call("showcaseSet", "Footsteps", "Style", step);
				});
				add(5, () -> { });
				for (int i = 0; i < 26; i++) {
					add(1, () -> {
						mc.player.setYRot(0);
						mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0.2);
					});
				}
				shot(mc, "cos52-steps-" + step.toLowerCase());
				add(30, () -> { });
				shot(mc, "cos52-steps-" + step.toLowerCase() + "-later");
			}
			add(1, () -> {
				call("showcaseTilt", -35f);
				call("showcaseEnable", "Footsteps", false);
			});
			add(1, () -> {
				mc.player.connection.sendCommand("time set 6000");
				call("showcaseEnable", "Trails", false);
				call("showcaseEnable", "Freelook", false);
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
		}
		if (only.contains("Cos52Wings")) {
			String filter = only.contains("Cos52Wings:") ? only.substring(only.indexOf("Cos52Wings:") + 11) : "";
			command(mc, "gamemode creative");
			command(mc, "time set 6000");
			command(mc, "effect give @s resistance 99999 4 true");
			double[] home = new double[3];
			add(1, () -> {
				call("showcaseRealism", "Player Aura", true);
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				call("showcaseEnable", "Fullbright", false);
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
				home[0] = mc.player.getX();
				home[1] = mc.player.getY();
				home[2] = mc.player.getZ();
				mc.player.setYRot(0);
				mc.player.setXRot(0);
				call("showcaseSet", "Freelook", "Cinematic", "false");
				call("showcaseSet", "Freelook", "Distance", "3.4");
				call("showcaseEnable", "Freelook", true);
				for (String other : List.of("Halo", "Crown", "Crown of fire", "Horns", "Ears", "China hat", "Crystals", "Orbiting blades",
						"Gyroscope", "Shield shell", "Rune circle", "Spirits", "Ribbons")) {
					call("showcaseSet", "Player Aura", other, "false");
				}
				call("showcaseSet", "Player Aura", "Wings", "true");
				call("showcaseSet", "Player Aura", "Who", "Self");
				call("showcaseSet", "Player Aura", "Size", "1");
				call("showcaseEnable", "Player Aura", true);
			});
			for (String style : List.of("Feathered", "Fallen angel", "Bat", "Demon", "Butterfly")) {
				if (!filter.isEmpty() && java.util.Arrays.stream(filter.split(",")).noneMatch(f -> f.trim().equalsIgnoreCase(style))) {
					continue;
				}
				String slug = "cos52-wings-" + style.toLowerCase().replace(' ', '-');
				add(1, () -> {
					mc.player.connection.sendCommand("time set 6000");
					mc.player.setPos(home[0], home[1], home[2]);
					mc.player.setYRot(0);
					call("showcaseSet", "Player Aura", "Wing style", style);
				});
				add(2, () -> call("showcaseOrbit", 25f));
				add(40, () -> { });
				shot(mc, slug + "-rest");
				add(1, () -> call("showcaseOrbit", 65f));
				for (int i = 0; i < 24; i++) {
					add(1, () -> {
						mc.player.setYRot(0);
						mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0.2);
					});
				}
				shot(mc, slug + "-walk");
				add(1, () -> {
					call("showcaseOrbit", 90f);
					mc.player.setPos(home[0], home[1], home[2]);
					mc.player.setDeltaMovement(0, 0, 0);
				});
				add(20, () -> { });
				add(1, () -> mc.player.jumpFromGround());
				add(6, () -> { });
				shot(mc, slug + "-jump");
				add(1, () -> {
					mc.player.setPos(home[0], home[1] + 14, home[2]);
					mc.player.setDeltaMovement(0, 0, 0);
					mc.player.getAbilities().flying = false;
				});
				add(16, () -> { });
				shot(mc, slug + "-glide");
				add(40, () -> {
					mc.player.setPos(home[0], home[1], home[2]);
					mc.player.setDeltaMovement(0, 0, 0);
				});
				add(1, () -> {
					mc.player.connection.sendCommand("time set 18000");
					call("showcaseOrbit", 155f);
				});
				add(40, () -> { });
				shot(mc, slug + "-night");
				add(1, () -> call("showcaseOrbit", 25f));
			}
			add(1, () -> {
				mc.player.connection.sendCommand("time set 6000");
				call("showcaseEnable", "Player Aura", false);
				call("showcaseEnable", "Freelook", false);
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
		}
		if (only.contains("Cos52Aura")) {
			String[][] looks = {
					{"Horns", "Horn style", "Devil", "1.5"}, {"Horns", "Horn style", "Ram", "1.6"}, {"Horns", "Horn style", "Dragon", "1.7"},
					{"Ears", "Ear style", "Cat", "1.5"}, {"Ears", "Ear style", "Bunny", "1.7"},
					{"Crown", "Crown style", "Royal", "1.5"}, {"Crown", "Crown style", "Spiked", "1.5"}, {"Crown", "Crown style", "Circlet", "1.5"},
					{"Crown of fire", "", "", "1.7"}, {"Halo", "", "", "1.8"}, {"Crystals", "", "", "2.6"},
					{"Orbiting blades", "Blade style", "Sword", "3.2"}, {"Orbiting blades", "Blade style", "Energy", "3.2"},
					{"Gyroscope", "", "", "3.2"}, {"Shield shell", "", "", "3.6"}, {"Rune circle", "", "", "3.6"},
					{"Spirits", "", "", "3.2"}, {"Ribbons", "", "", "3.2"}, {"China hat", "", "", "1.7"}};
			String filter = only.contains("Cos52Aura:") ? only.substring(only.indexOf("Cos52Aura:") + 10) : "";
			command(mc, "gamemode creative");
			command(mc, "time set 6000");
			command(mc, "effect clear @s");
			add(1, () -> {
				call("showcaseRealism", "Player Aura", true);
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				call("showcaseEnable", "Fullbright", false);
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
				mc.player.setYRot(0);
				mc.player.setXRot(0);
				call("showcaseSet", "Freelook", "Cinematic", "false");
				call("showcaseEnable", "Freelook", true);
				call("showcaseSet", "Player Aura", "Who", "Self");
				call("showcaseSet", "Player Aura", "Size", "1");
				call("showcaseEnable", "Player Aura", true);
			});
			java.util.List<String> pieces = java.util.Arrays.stream(looks).map(l -> l[0]).distinct().toList();
			for (String time : List.of("6000", "18000")) {
				command(mc, "time set " + time);
				for (String[] lk : looks) {
					if (!filter.isEmpty() && java.util.Arrays.stream(filter.split(",")).noneMatch(f -> f.trim().equalsIgnoreCase(lk[0]))) {
						continue;
					}
					boolean night = time.equals("18000");
					String slug = "cos52-" + lk[0].toLowerCase().replace(' ', '-') + (lk[2].isEmpty() ? "" : "-" + lk[2].toLowerCase());
					add(1, () -> {
						call("showcaseSet", "Player Aura", "Wings", "false");
						for (String other : pieces) {
							call("showcaseSet", "Player Aura", other, String.valueOf(other.equals(lk[0])));
						}
						if (!lk[1].isEmpty()) {
							call("showcaseSet", "Player Aura", lk[1], lk[2]);
						}
						call("showcaseSet", "Freelook", "Distance", lk[3]);
					});
					add(2, () -> call("showcaseOrbit", 145f));
					add(10, () -> { });
					shot(mc, slug + (night ? "-night" : "-front"));
					if (!night) {
						add(2, () -> call("showcaseOrbit", 80f));
						add(4, () -> { });
						shot(mc, slug + "-side");
						add(2, () -> call("showcaseOrbit", -80f));
					}
					add(2, () -> call("showcaseOrbit", -145f));
				}
			}
			add(1, () -> {
				call("showcaseEnable", "Player Aura", false);
				call("showcaseEnable", "Freelook", false);
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
		}
		if (only.contains("Cos52Light")) {
			command(mc, "gamemode creative");
			command(mc, "time set 6000");
			add(1, () -> {
				call("showcaseRealism", "Player Aura", true);
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				call("showcaseEnable", "Fullbright", false);
				mc.player.setYRot(0);
				mc.player.setXRot(0);
				call("showcaseSet", "Freelook", "Cinematic", "false");
				call("showcaseSet", "Freelook", "Distance", "1.5");
				call("showcaseEnable", "Freelook", true);
				for (String other : List.of("Wings", "Halo", "Gyroscope", "Crystals", "Crown", "Orbiting blades", "Shield shell", "Horns",
						"Ears", "Rune circle", "Spirits", "Ribbons", "China hat")) {
					call("showcaseSet", "Player Aura", other, String.valueOf(other.equals("Horns")));
				}
				call("showcaseSet", "Player Aura", "Horn style", "Devil");
				call("showcaseEnable", "Player Aura", true);
			});
			add(2, () -> call("showcaseOrbit", 145f));
			add(6, () -> { });
			shot(mc, "cos52light-noon");
			command(mc, "time set 18000");
			add(10, () -> { });
			shot(mc, "cos52light-night");
			command(mc, "setblock ~1 ~ ~1 minecraft:torch");
			add(10, () -> { });
			shot(mc, "cos52light-torch");
			command(mc, "setblock ~1 ~ ~1 minecraft:air");
			add(1, () -> call("showcaseEnable", "Freelook", false));
		}
		if (only.contains("Cos52Ground")) {
			command(mc, "gamemode creative");
			command(mc, "time set 6000");
			command(mc, "effect clear @s");
			add(1, () -> {
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				call("showcaseEnable", "Fullbright", false);
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
				mc.player.setYRot(0);
				mc.player.setXRot(0);
				call("showcaseSet", "Freelook", "Cinematic", "false");
				call("showcaseSet", "Freelook", "Distance", "1.5");
				call("showcaseEnable", "Freelook", true);
				for (String other : List.of("Wings", "Halo", "Gyroscope", "Crystals", "Crown", "Orbiting blades", "Shield shell", "Horns",
						"Ears", "Rune circle", "Spirits", "Ribbons", "China hat")) {
					call("showcaseSet", "Player Aura", other, String.valueOf(other.equals("Horns")));
				}
				call("showcaseSet", "Player Aura", "Horn style", "Devil");
				call("showcaseSet", "Player Aura", "Who", "Self");
				call("showcaseSet", "Player Aura", "Size", "1");
				call("showcaseEnable", "Player Aura", true);
			});
			add(2, () -> call("showcaseOrbit", 145f));
			String[] times = {"6000", "18000", "torch"};
			for (String time : times) {
				if (time.equals("torch")) {
					command(mc, "setblock ~1 ~ ~1 minecraft:torch");
				} else {
					command(mc, "time set " + time);
				}
				for (String finish : List.of("Keratin", "Obsidian", "Bone", "Gold", "Crystal")) {
					add(1, () -> call("showcaseSet", "Player Aura", "Horn finish", finish));
					add(6, () -> { });
					shot(mc, "cos52-horn-" + finish.toLowerCase() + "-" + (time.equals("6000") ? "noon" : time.equals("18000") ? "night" : "torch"));
				}
			}
			add(1, () -> call("showcaseSet", "Player Aura", "Horn finish", "Keratin"));
			add(2, () -> call("showcaseOrbit", 60f));
			add(4, () -> { });
			shot(mc, "cos52-horn-keratin-side-torch");
			add(2, () -> call("showcaseOrbit", 120f));
			add(4, () -> { });
			shot(mc, "cos52-horn-keratin-back-torch");
			add(1, () -> call("showcaseSet", "Player Aura", "Horn glow", "0"));
			add(2, () -> call("showcaseOrbit", 180f));
			add(4, () -> { });
			shot(mc, "cos52-horn-keratin-noglow-torch");
			command(mc, "setblock ~1 ~ ~1 minecraft:air");
			command(mc, "time set 6000");
			add(1, () -> {
				call("showcaseSet", "Player Aura", "Horn glow", "60");
				call("showcaseSet", "Player Aura", "Horns", "false");
				call("showcaseSet", "Player Aura", "Crown", "true");
				call("showcaseSet", "Player Aura", "Crown style", "Royal");
				call("showcaseSet", "Player Aura", "Material", "Gold");
			});
			add(6, () -> { });
			shot(mc, "cos52-crown-legacy-noon");
			command(mc, "time set 18000");
			add(6, () -> { });
			shot(mc, "cos52-crown-legacy-night");
			command(mc, "time set 6000");
			add(1, () -> {
				call("showcaseEnable", "Player Aura", false);
				call("showcaseSet", "Pets", "Pet", "Dragon");
				call("showcaseSet", "Pets", "Position", "Shoulder");
				call("showcaseEnable", "Pets", true);
				call("showcaseSet", "Freelook", "Distance", "2.6");
			});
			add(40, () -> { });
			shot(mc, "cos52-pet-dragon-noon");
			add(1, () -> {
				call("showcaseEnable", "Pets", false);
				call("showcaseEnable", "Freelook", false);
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
		}
		if (only.contains("Look51")) {
			command(mc, "gamemode creative");
			command(mc, "time set 6000");
			command(mc, "effect clear @s");
			double[] home = new double[3];
			add(1, () -> {
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
				home[0] = mc.player.getX();
				home[1] = mc.player.getY();
				home[2] = mc.player.getZ();
				mc.player.setYRot(0);
				mc.player.setXRot(0);
				call("showcaseSet", "Freelook", "Cinematic", "false");
				call("showcaseSet", "Freelook", "Distance", "2.8");
				call("showcaseEnable", "Freelook", true);
			});
			List<String> pieces = List.of("Wings", "Halo", "Gyroscope", "Crystals", "Crown", "Orbiting blades", "Shield shell", "Horns",
					"Ears", "Rune circle", "Spirits", "Ribbons", "China hat");
			String[][] looks = {{"Wings", "Wing style", "Feathered"}, {"Wings", "Wing style", "Bat"}, {"Wings", "Wing style", "Butterfly"},
					{"Halo", null, null}, {"Gyroscope", null, null}, {"Crystals", null, null}, {"Crown", "Crown style", "Royal"},
					{"Crown", "Crown style", "Spiked"}, {"Crown", "Crown style", "Circlet"}, {"Orbiting blades", "Blade style", "Sword"},
					{"Orbiting blades", "Blade style", "Energy"}, {"Shield shell", null, null}, {"Horns", "Horn style", "Devil"},
					{"Horns", "Horn style", "Ram"}, {"Horns", "Horn style", "Dragon"}, {"Ears", "Ear style", "Cat"}, {"Ears", "Ear style", "Fox"},
					{"Ears", "Ear style", "Bunny"}, {"Rune circle", null, null}, {"Spirits", null, null}, {"Ribbons", null, null},
					{"China hat", null, null}};
			for (String[] lk : looks) {
				add(1, () -> {
					for (String other : pieces) {
						call("showcaseSet", "Player Aura", other, String.valueOf(other.equals(lk[0])));
					}
					if (lk[1] != null) {
						call("showcaseSet", "Player Aura", lk[1], lk[2]);
					}
					call("showcaseSet", "Player Aura", "Who", "Self");
					call("showcaseSet", "Player Aura", "Size", "1");
					call("showcaseEnable", "Player Aura", true);
				});
				String slug = "look51-aura-" + lk[0].toLowerCase().replace(' ', '-') + (lk[2] == null ? "" : "-" + lk[2].toLowerCase());
				add(2, () -> call("showcaseOrbit", 150f));
				add(8, () -> { });
				shot(mc, slug + "-front");
				add(2, () -> call("showcaseOrbit", 70f));
				add(4, () -> { });
				shot(mc, slug + "-back");
				add(2, () -> call("showcaseOrbit", 140f));
			}
			add(1, () -> call("showcaseEnable", "Player Aura", false));
			for (String pet : (List<String>) call("showcaseOptions", "Pets", "Pet")) {
				add(1, () -> {
					call("showcaseSet", "Pets", "Pet", pet);
					call("showcaseSet", "Pets", "Size", "1.3");
					call("showcaseSet", "Pets", "Color", "Natural");
					call("showcaseSet", "Pets", "Position", "Shoulder");
					call("showcaseEnable", "Pets", true);
				});
				add(40, () -> { });
				for (int view = 0; view < 4; view++) {
					int v = view;
					add(2, () -> call("showcaseOrbit", 90f));
					add(4, () -> { });
					shot(mc, "look51-pet-" + pet.toLowerCase().replace(' ', '-') + "-" + v);
				}
				add(1, () -> call("showcaseEnable", "Pets", false));
			}
			add(1, () -> {
				call("showcaseSet", "Freelook", "Distance", "5.5");
				call("showcaseOrbit", 110f);
			});
			List<String[]> runs = new java.util.ArrayList<>();
			for (String style : (List<String>) call("showcaseOptions", "Trails", "Style")) {
				runs.add(new String[] {"Trails", style});
			}
			for (String style : (List<String>) call("showcaseOptions", "Contrails", "Style")) {
				runs.add(new String[] {"Contrails", style});
			}
			for (String style : (List<String>) call("showcaseOptions", "Footsteps", "Style")) {
				runs.add(new String[] {"Footsteps", style});
			}
			runs.add(new String[] {"Aurora", null});
			for (String[] run : runs) {
				add(1, () -> {
					mc.player.setPos(home[0], home[1], home[2] - 14);
					mc.player.setYRot(0);
					call("showcaseSet", run[0], "Who", "Self");
					if (run[1] != null) {
						call("showcaseSet", run[0], "Style", run[1]);
					}
					call("showcaseEnable", run[0], true);
				});
				for (int i = 0; i < 34; i++) {
					add(1, () -> {
						mc.player.setYRot(0);
						mc.player.setSprinting(true);
						mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0.32);
					});
				}
				String slug = "look51-run-" + run[0].toLowerCase() + (run[1] == null ? "" : "-" + run[1].toLowerCase().replace(' ', '-'));
				shot(mc, slug + "-side");
				add(1, () -> call("showcaseOrbit", 60f));
				add(1, () -> mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0.32));
				shot(mc, slug + "-front");
				add(1, () -> {
					call("showcaseOrbit", -60f);
					call("showcaseEnable", run[0], false);
				});
				add(10, () -> { });
			}
			add(1, () -> {
				call("showcaseEnable", "Freelook", false);
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
		}
		if (only.contains("Audit50")) {
			command(mc, "time set 6000");
			command(mc, "effect clear @s");
			add(1, () -> {
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				call("showcaseEnable", "Skybox", false);
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			double[] home = new double[3];
			add(1, () -> {
				home[0] = mc.player.getX();
				home[1] = mc.player.getY();
				home[2] = mc.player.getZ();
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				mc.player.setYRot(0);
				mc.player.setXRot(10);
				call("showcaseEnable", "Walk Style", true);
			});
			for (String style : (List<String>) call("showcaseOptions", "Walk Style", "Style")) {
				add(1, () -> {
					mc.player.setPos(home[0], home[1], home[2]);
					call("showcaseSet", "Walk Style", "Style", style);
				});
				for (int i = 0; i < 12; i++) {
					add(1, () -> {
						mc.player.setYRot(0);
						mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0.13);
					});
				}
				shot(mc, "audit50-walk-" + style.toLowerCase().replace(' ', '-'));
			}
			add(1, () -> {
				call("showcaseEnable", "Walk Style", false);
				mc.player.setPos(home[0], home[1], home[2]);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
			command(mc, "gamemode survival");
			command(mc, "item replace entity @s weapon.mainhand with minecraft:wooden_pickaxe");
			for (String style : List.of("Nuker", "Liquid", "Brackets", "Chip")) {
				command(mc, "setblock ~ ~1 ~2 minecraft:stone");
				add(1, () -> {
					mc.player.setYRot(0);
					mc.player.setXRot(20);
					call("showcaseSet", "Break Animation", "Style", style);
					call("showcaseEnable", "Break Animation", true);
					mc.options.keyAttack.setDown(true);
				});
				add(12, () -> { });
				shot(mc, "audit50-break-" + style.toLowerCase());
				add(30, () -> { });
				shot(mc, "audit50-break-" + style.toLowerCase() + "-done");
				add(1, () -> mc.options.keyAttack.setDown(false));
			}
			add(1, () -> call("showcaseEnable", "Break Animation", false));
			command(mc, "gamemode creative");
			command(mc, "item replace entity @s weapon.mainhand with minecraft:diamond_sword");
			for (String look : List.of("Chrome", "Galaxy", "Fire", "Crystal")) {
				add(1, () -> {
					mc.player.setXRot(10);
					call("showcaseSet", "Hand Chams", "Weapon style", look);
					call("showcaseSet", "Hand Chams", "Hand style", look);
					call("showcaseEnable", "Hand Chams", true);
				});
				add(6, () -> { });
				shot(mc, "audit50-hand-" + look.toLowerCase());
			}
			add(1, () -> call("showcaseEnable", "Hand Chams", false));
			add(1, () -> {
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.player.setXRot(15);
				call("showcaseSet", "Teleport Effects", "Who", "Both");
				call("showcaseEnable", "Teleport Effects", true);
			});
			for (String style : List.of("Vortex", "Rings", "Portal")) {
				add(1, () -> call("showcaseSet", "Teleport Effects", "Style", style));
				command(mc, "tp @s ~ ~ ~12");
				add(4, () -> { });
				shot(mc, "audit50-tp-" + style.toLowerCase() + "-a");
				add(10, () -> { });
				shot(mc, "audit50-tp-" + style.toLowerCase() + "-b");
				add(20, () -> { });
			}
			add(1, () -> {
				call("showcaseEnable", "Teleport Effects", false);
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
		}
		if (only.contains("Fix53")) {
			command(mc, "time set 6000");
			command(mc, "effect clear @s");
			command(mc, "kill @e[type=husk]");
			add(1, () -> {
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setYRot(0);
				mc.player.setXRot(8);
				call("showcaseSet", "Hit Particles", "Style", "Impact");
				call("showcaseEnable", "Hit Particles", true);
			});
			command(mc, "summon minecraft:husk ~ ~ ~2.6 {NoAI:1b,Silent:1b,Health:1000f,Attributes:[{id:\"minecraft:max_health\",base:1000}]}");
			for (int hit = 0; hit < 3; hit++) {
				int h = hit;
				add(14, () -> {
					var husk = mc.level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Husk.class, mc.player.getBoundingBox().inflate(6)).stream()
							.findFirst().orElse(null);
					if (husk != null) {
						mc.gameMode.attack(mc.player, husk);
						mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
					}
					System.out.println("TEMPLE_FIX53 hit " + h + " husk=" + (husk == null ? "none" : husk.distanceTo(mc.player) + " hp " + husk.getHealth())
							+ " fx=" + call("showcaseMeshQuads"));
				});
				int[] waits = h == 0 ? new int[] {1, 1, 2, 4, 6} : new int[] {1};
				for (int w = 0; w < waits.length; w++) {
					int k = w;
					add(waits[w], () -> { });
					shot(mc, "fix53-impact-" + h + "-" + k);
				}
			}
			command(mc, "kill @e[type=husk]");
			add(1, () -> call("showcaseEnable", "Hit Particles", false));
			add(1, () -> {
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.player.setXRot(20);
				call("showcaseSet", "Trails", "Who", "Self");
				call("showcaseEnable", "Trails", true);
			});
			String[][] trails = {{"Elemental", "Fire"}, {"Elemental", "Frost"}, {"Elemental", "Stardust"}, {"Elemental", "Smoke"},
					{"Elemental", "Lightning"}, {"Energy", "Hands"}, {"Energy", "All"}, {"Afterimages", "real"}, {"Tube", ""}};
			for (String[] tr : trails) {
				add(1, () -> {
					call("showcaseSet", "Trails", "Type", tr[0]);
					if (tr[0].equals("Elemental")) {
						call("showcaseSet", "Trails", "Element", tr[1]);
					} else if (tr[0].equals("Energy")) {
						call("showcaseSet", "Trails", "Ribbons from", tr[1]);
					} else if (tr[0].equals("Afterimages")) {
						call("showcaseRealism", "Trails", true);
					}
				});
				add(2, () -> call("showcasePreview", "Trails"));
				add(18, () -> { });
				shot(mc, "fix53-trail-" + (tr[0] + "-" + tr[1]).toLowerCase());
				add(40, () -> { });
			}
			add(1, () -> call("showcaseEnable", "Trails", false));
			add(1, () -> call("showcaseEnable", "Hit Sounds", true));
			for (String sound : List.of("Pop", "Punch", "Crit", "Tick", "Thud", "Snap", "Crunch", "Coin", "Ding", "Bubble")) {
				add(6, () -> {
					call("showcaseSet", "Hit Sounds", "Sound", sound);
					call("showcasePress", "Hit Sounds", "Preview");
					System.out.println("TEMPLE_FIX53 sound " + sound + " = " + call("showcaseSettingValue", "Hit Sounds", "Sound"));
				});
			}
			add(1, () -> call("showcaseEnable", "Hit Sounds", false));
			add(1, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
		}
		if (only.contains("Death49")) {
			command(mc, "time set 6000");
			command(mc, "effect clear @s");
			command(mc, "kill @e[type=husk]");
			add(1, () -> {
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				call("showcaseEnable", "Skybox", false);
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setYRot(0);
				mc.player.setXRot(6);
				call("showcaseSet", "Death Effects", "Who", "Everything");
				call("showcaseEnable", "Death Effects", true);
			});
			for (String style : List.of("Coins", "Soul", "Shockwave", "Ghost", "Crumble", "Ashes")) {
				add(1, () -> call("showcaseSet", "Death Effects", "Style", style));
				command(mc, "summon minecraft:husk ~ ~ ~4 {NoAI:1b,Silent:1b}");
				add(6, () -> { });
				command(mc, "damage @e[type=husk,limit=1] 100 minecraft:player_attack by @s");
				int[] waits = {3, 8, 14, 24};
				for (int w = 0; w < waits.length; w++) {
					int k = w;
					add(waits[w], () -> { });
					shot(mc, "death49-" + style.toLowerCase() + "-" + k);
				}
				add(30, () -> { });
			}
			add(1, () -> call("showcaseEnable", "Death Effects", false));
			command(mc, "summon minecraft:husk ~ ~ ~3 {NoAI:1b,Silent:1b}");
			add(1, () -> {
				call("showcaseSet", "Hit Markers", "Placement", "On player");
				call("showcaseEnable", "Hit Markers", true);
			});
			for (int hit = 0; hit < 2; hit++) {
				int h = hit;
				add(12, () -> {
					var husk = mc.level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Husk.class, mc.player.getBoundingBox().inflate(6)).stream()
							.findFirst().orElse(null);
					if (husk != null) {
						mc.gameMode.attack(mc.player, husk);
						mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
					}
				});
				add(2, () -> { });
				shot(mc, "death49-hitmarker-" + h);
			}
			command(mc, "kill @e[type=husk]");
			add(1, () -> call("showcaseEnable", "Hit Markers", false));
			add(1, () -> {
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				call("showcaseSet", "Trails", "Style", "Afterimages");
				call("showcaseSet", "Trails", "Who", "Self");
				call("showcaseEnable", "Trails", true);
			});
			for (int i = 0; i < 16; i++) {
				add(1, () -> {
					mc.player.setYRot(0);
					mc.player.setDeltaMovement(0.18, mc.player.getDeltaMovement().y, 0.12);
				});
			}
			shot(mc, "death49-afterimages");
			add(1, () -> {
				call("showcaseEnable", "Trails", false);
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
		}
		if (only.contains("Rest49")) {
			command(mc, "time set 18000");
			command(mc, "effect clear @s");
			add(1, () -> {
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				call("showcaseEnable", "Skybox", false);
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.player.setYRot(0);
				mc.player.setXRot(8);
			});
			add(1, () -> {
				call("showcaseSet", "Aurora", "Veil", "true");
				call("showcaseSet", "Aurora", "Crown", "true");
				call("showcaseSet", "Aurora", "Palette", "Natural");
				call("showcaseSet", "Aurora", "Who", "Self");
				call("showcaseEnable", "Aurora", true);
			});
			for (int i = 0; i < 3; i++) {
				add(12, () -> { });
				shot(mc, "rest49-aurora-" + i);
			}
			add(1, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			add(6, () -> { });
			shot(mc, "rest49-aurora-front");
			add(1, () -> {
				call("showcaseEnable", "Aurora", false);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			});
			command(mc, "time set 6000");
			add(1, () -> {
				call("showcaseSet", "Contrails", "Style", "Vapour");
				call("showcaseEnable", "Contrails", true);
				call("showcasePreview", "Contrails");
			});
			for (int i = 0; i < 3; i++) {
				add(10, () -> { });
				shot(mc, "rest49-contrails-" + i);
			}
			add(1, () -> call("showcaseEnable", "Contrails", false));
			double[] home = new double[3];
			add(1, () -> {
				home[0] = mc.player.getX();
				home[1] = mc.player.getY();
				home[2] = mc.player.getZ();
			});
			for (String style : List.of("Tube", "Ribbon", "Afterimages", "Wisps")) {
				add(1, () -> {
					mc.player.setPos(home[0], home[1], home[2]);
					call("showcaseSet", "Trails", "Style", style);
					call("showcaseSet", "Trails", "Who", "Self");
					call("showcaseEnable", "Trails", true);
				});
				for (int i = 0; i < 16; i++) {
					add(1, () -> {
						mc.player.setYRot(0);
						mc.player.setDeltaMovement(0.18, mc.player.getDeltaMovement().y, 0.12);
					});
				}
				shot(mc, "rest49-trails-" + style.toLowerCase());
				add(1, () -> call("showcaseEnable", "Trails", false));
			}
			command(mc, "kill @e[type=husk]");
			command(mc, "summon minecraft:husk ~ ~ ~4 {NoAI:1b,Silent:1b,PersistenceRequired:1b}");
			add(1, () -> {
				mc.player.setPos(home[0], home[1], home[2]);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setYRot(0);
				mc.player.setXRot(10);
				call("showcaseSet", "Damage Effects", "Who", "Everything");
				call("showcaseEnable", "Damage Effects", true);
			});
			for (String style : List.of("Shockwave", "Shatter", "Bleed")) {
				add(10, () -> call("showcaseSet", "Damage Effects", "Style", style));
				command(mc, "damage @e[type=husk,limit=1] 1 minecraft:player_attack by @s");
				add(2, () -> { });
				shot(mc, "rest49-damage-" + style.toLowerCase() + "-a");
				add(4, () -> { });
				shot(mc, "rest49-damage-" + style.toLowerCase() + "-b");
			}
			command(mc, "kill @e[type=husk]");
			add(1, () -> call("showcaseEnable", "Damage Effects", false));
			add(1, () -> {
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				call("showcaseSet", "Teleport Effects", "Style", "Portal");
				call("showcaseEnable", "Teleport Effects", true);
				call("showcasePreview", "Teleport Effects");
			});
			add(6, () -> { });
			shot(mc, "rest49-teleport-a");
			add(12, () -> { });
			shot(mc, "rest49-teleport-b");
			add(1, () -> {
				call("showcaseEnable", "Teleport Effects", false);
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
		}
		if (only.contains("BoomDbg")) {
			add(1, () -> call("showcaseEnable", "Explosion Effects", true));
			command(mc, "summon minecraft:tnt ~ ~ ~8 {fuse:10}");
			add(30, () -> { });
			command(mc, "summon minecraft:tnt ~3 ~ ~8 {fuse:10}");
			add(5, () -> System.out.println("TEMPLE_DBG particles " + mc.particleEngine.countParticles()));
			add(5, () -> System.out.println("TEMPLE_DBG particles " + mc.particleEngine.countParticles()));
			add(30, () -> call("showcaseEnable", "Explosion Effects", false));
		}
		if (only.contains("Big48")) {
			command(mc, "time set 6000");
			command(mc, "effect clear @s");
			add(1, () -> {
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				call("showcaseEnable", "Skybox", false);
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			add(1, () -> {
				mc.player.setYRot(0);
				mc.player.setXRot(5);
				call("showcaseSet", "Freelook", "Cinematic", "false");
				call("showcaseSet", "Freelook", "Distance", "3.4");
				call("showcaseEnable", "Freelook", true);
				for (String other : List.of("Wings", "Halo", "Gyroscope", "Crystals", "Crown", "Orbiting blades", "Shield shell", "Horns",
						"Ears", "Rune circle", "Spirits", "Ribbons", "China hat")) {
					call("showcaseSet", "Player Aura", other, String.valueOf(other.equals("Wings")));
				}
				call("showcaseSet", "Player Aura", "Who", "Self");
				call("showcaseSet", "Player Aura", "Size", "1");
			});
			add(2, () -> call("showcaseOrbit", 25f));
			for (String wing : List.of("Feathered", "Bat", "Butterfly")) {
				for (String material : List.of("Gold", "Matte")) {
					add(1, () -> {
						call("showcaseSet", "Player Aura", "Wing style", wing);
						call("showcaseSet", "Player Aura", "Material", material);
						call("showcaseEnable", "Player Aura", true);
					});
					add(14, () -> { });
					shot(mc, "big48-wing-" + wing.toLowerCase() + "-" + material.toLowerCase() + "-back");
				}
				add(2, () -> call("showcaseOrbit", 155f));
				add(6, () -> { });
				shot(mc, "big48-wing-" + wing.toLowerCase() + "-front");
				add(2, () -> call("showcaseOrbit", -155f));
				add(1, () -> {
					mc.player.getAbilities().flying = true;
					mc.player.setPos(mc.player.getX(), mc.player.getY() + 2, mc.player.getZ());
				});
				add(16, () -> { });
				shot(mc, "big48-wing-" + wing.toLowerCase() + "-fly-a");
				add(3, () -> { });
				shot(mc, "big48-wing-" + wing.toLowerCase() + "-fly-b");
				add(1, () -> {
					mc.player.getAbilities().flying = false;
				});
				add(20, () -> { });
			}
			add(1, () -> {
				call("showcaseEnable", "Player Aura", false);
				call("showcaseEnable", "Freelook", false);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.player.setYRot(0);
				mc.player.setXRot(12);
			});
			add(1, () -> {
				call("showcaseSet", "Explosion Effects", "Style", "Realistic");
				call("showcaseSet", "Explosion Effects", "Duration", "1.2");
				call("showcaseSet", "Explosion Effects", "Impact frames", "false");
				call("showcaseSet", "Explosion Effects", "Shockwave", "false");
				call("showcaseEnable", "Explosion Effects", true);
			});
			for (int pass = 0; pass < 2; pass++) {
				int p = pass;
				add(1, () -> call("showcasePreview", "Explosion Effects"));
				for (int i = 0; i < 12; i++) {
					int frame = i;
					add(1, () -> Screenshot.grab(mc.gameDirectory, "showcase-big48-boom" + p + "-" + String.format("%02d", frame) + ".png",
							mc.gameRenderer.mainRenderTarget(), 1, c -> { }));
				}
				add(30, () -> { });
				shot(mc, "big48-boom" + p + "-smoke");
				add(40, () -> { });
			}
			add(1, () -> call("showcaseEnable", "Explosion Effects", false));
			command(mc, "kill @e[type=husk]");
			command(mc, "summon minecraft:husk ~ ~ ~5 {NoAI:1b,Silent:1b,PersistenceRequired:1b}");
			add(1, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setYRot(0);
				mc.player.setXRot(14);
				call("showcaseSet", "Damage Effects", "Style", "Bleed");
				call("showcaseSet", "Damage Effects", "Who", "Everything");
				call("showcaseEnable", "Damage Effects", true);
			});
			for (int hit = 0; hit < 3; hit++) {
				int h = hit;
				command(mc, "damage @e[type=husk,limit=1] 1 minecraft:player_attack by @s");
				add(3, () -> { });
				shot(mc, "big48-blood-" + h + "-a");
				add(10, () -> { });
				shot(mc, "big48-blood-" + h + "-b");
			}
			add(40, () -> { });
			shot(mc, "big48-blood-later");
			command(mc, "kill @e[type=husk]");
			add(1, () -> call("showcaseEnable", "Damage Effects", false));
			add(1, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setYRot(0);
				mc.player.setXRot(4);
				call("showcaseSet", "Explosion Effects", "Style", "Realistic");
				call("showcaseSet", "Explosion Effects", "TNT", "true");
				call("showcaseEnable", "Explosion Effects", true);
			});
			command(mc, "summon minecraft:tnt ~ ~ ~10 {fuse:20}");
			add(17, () -> { });
			for (int i = 0; i < 8; i++) {
				int frame = i;
				add(1, () -> Screenshot.grab(mc.gameDirectory, "showcase-big48-tnt-" + String.format("%02d", frame) + ".png",
						mc.gameRenderer.mainRenderTarget(), 1, c -> { }));
			}
			add(30, () -> call("showcaseEnable", "Explosion Effects", false));
			command(mc, "gamemode survival");
			command(mc, "setblock ~ ~1 ~2 minecraft:stone");
			command(mc, "item replace entity @s weapon.mainhand with minecraft:wooden_pickaxe");
			add(1, () -> {
				mc.player.setYRot(0);
				mc.player.setXRot(20);
				call("showcaseSet", "Break Animation", "Style", "Nuker");
				call("showcaseEnable", "Break Animation", true);
				mc.options.keyAttack.setDown(true);
			});
			add(10, () -> { });
			shot(mc, "big48-mine-a");
			add(4, () -> { });
			shot(mc, "big48-mine-b");
			add(1, () -> mc.options.keyAttack.setDown(false));
			add(20, () -> call("showcaseEnable", "Break Animation", false));
			command(mc, "gamemode creative");
			command(mc, "setblock ~ ~1 ~3 minecraft:gold_block");
			add(1, () -> {
				call("showcaseSet", "Block Effects", "Style", "Crumble");
				call("showcaseEnable", "Block Effects", true);
				mc.player.setXRot(12);
			});
			command(mc, "setblock ~ ~1 ~3 minecraft:air destroy");
			add(1, () -> { });
			shot(mc, "big48-blockbreak-a");
			add(3, () -> { });
			shot(mc, "big48-blockbreak-b");
			add(20, () -> call("showcaseEnable", "Block Effects", false));
			add(1, () -> {
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.player.setXRot(30);
				call("showcaseSet", "Jump Circles", "Material", "Light");
				call("showcaseEnable", "Jump Circles", true);
			});
			for (String style : List.of("Ripple", "Shockwave", "Crown", "Block highlight")) {
				add(1, () -> {
					call("showcaseSet", "Jump Circles", "Style", style);
					call("showcasePreview", "Jump Circles");
				});
				add(4, () -> { });
				shot(mc, "big48-jump-" + style.toLowerCase().replace(' ', '-'));
			}
			add(1, () -> call("showcaseEnable", "Jump Circles", false));
			add(1, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setYRot(0);
				mc.player.setXRot(8);
				call("showcaseEnable", "Storm Effects", true);
			});
			command(mc, "summon minecraft:lightning_bolt ~2 ~ ~12");
			for (int i = 0; i < 10; i++) {
				int frame = i;
				add(1, () -> Screenshot.grab(mc.gameDirectory, "showcase-big48-boltnear-" + String.format("%02d", frame) + ".png",
						mc.gameRenderer.mainRenderTarget(), 1, c -> { }));
			}
			add(20, () -> { });
			shot(mc, "big48-boltnear-scorch");
			add(1, () -> {
				call("showcaseEnable", "Storm Effects", false);
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
		}
		if (only.contains("Bolt48")) {
			add(1, () -> {
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
				call("showcaseEnable", "Storm Effects", true);
				call("showcaseSet", "Storm Effects", "Style", "Realistic");
				call("showcaseEnable", "Skybox", false);
				mc.player.setYRot(0);
				mc.player.setXRot(-24);
			});
			for (String time : List.of("18000", "6000")) {
				command(mc, "time set " + time);
				add(10, () -> { });
				command(mc, "summon minecraft:lightning_bolt ~4 ~ ~48");
				for (int i = 0; i < 16; i++) {
					int frame = i;
					add(0, () -> Screenshot.grab(mc.gameDirectory, "showcase-bolt48-" + time + "-" + String.format("%02d", frame) + ".png",
							mc.gameRenderer.mainRenderTarget(), 1, c -> { }));
				}
				add(40, () -> { });
				shot(mc, "bolt48-" + time + "-scorch");
			}
			add(1, () -> {
				call("showcaseEnable", "Storm Effects", false);
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
				mc.player.setXRot(0);
			});
		}
		if (only.contains("Cos47")) {
			command(mc, "time set 6000");
			command(mc, "effect clear @s");
			command(mc, "fill ~-3 ~ ~5 ~3 ~3 ~5 minecraft:stone_bricks");
			add(1, () -> {
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setYRot(0);
				mc.player.setXRot(10);
				call("showcaseSet", "Crosshair", "Outline", "true");
				call("showcaseSet", "Crosshair", "Charge ring", "false");
				call("showcaseSet", "Crosshair", "Dynamic gap", "Off");
				call("showcaseEnable", "Crosshair", true);
			});
			for (String shape : List.of("Cross", "Cross + dot", "Dot", "Circle", "Circle + dot", "T", "Chevron")) {
				add(1, () -> call("showcaseSet", "Crosshair", "Style", shape));
				add(3, () -> { });
				shot(mc, "cos47-crosshair-" + shape.toLowerCase().replace(" + ", "-").replace(' ', '-'));
			}
			add(1, () -> {
				call("showcaseSet", "Crosshair", "Style", "Cross + dot");
				call("showcaseSet", "Crosshair", "Colour", "#FF4AE0FF");
				call("showcaseSet", "Crosshair", "Charge ring", "true");
			});
			command(mc, "item replace entity @s weapon.mainhand with minecraft:netherite_axe");
			add(2, () -> {
				mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
				mc.player.resetAttackStrengthTicker();
			});
			shot(mc, "cos47-crosshair-ring");
			add(1, () -> {
				call("showcaseSet", "Crosshair", "Dynamic gap", "Movement");
				call("showcaseSet", "Crosshair", "Charge ring", "false");
			});
			for (int i = 0; i < 6; i++) {
				add(1, () -> mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0.0));
			}
			add(1, () -> mc.player.setDeltaMovement(0.25, mc.player.getDeltaMovement().y, 0));
			add(1, () -> mc.player.setDeltaMovement(0.25, mc.player.getDeltaMovement().y, 0));
			shot(mc, "cos47-crosshair-moving");
			add(1, () -> {
				call("showcaseSet", "Crosshair", "Dynamic gap", "Off");
				call("showcaseSet", "Crosshair", "Colour", "#FFFFFFFF");
				call("showcaseEnable", "Crosshair", false);
			});
			for (String item : List.of("oak_stairs", "oak_log", "grass_block", "glass", "chest")) {
				command(mc, "item replace entity @s weapon.mainhand with minecraft:" + item);
				add(1, () -> {
					call("showcaseSet", "Place Preview", "Preview", "Real block");
					call("showcaseEnable", "Place Preview", true);
					mc.player.setYRot(20);
					mc.player.setXRot(8);
				});
				add(8, () -> { });
				shot(mc, "cos47-place-" + item);
			}
			add(1, () -> call("showcaseEnable", "Place Preview", false));
			add(1, () -> {
				mc.player.setYRot(0);
				mc.player.setXRot(10);
				call("showcaseSet", "Pets", "Size", "1.6");
				call("showcaseSet", "Pets", "Color", "Natural");
				call("showcaseSet", "Pets", "Position", "Shoulder");
				call("showcaseSet", "Freelook", "Cinematic", "false");
				call("showcaseSet", "Freelook", "Distance", "2.4");
				call("showcaseEnable", "Freelook", true);
			});
			for (String pet : List.of("Bee", "Ghost")) {
				add(1, () -> {
					call("showcaseSet", "Pets", "Pet", pet);
					call("showcaseEnable", "Pets", true);
				});
				add(40, () -> { });
				for (int view = 0; view < 4; view++) {
					add(2, () -> call("showcaseOrbit", 90f));
					add(4, () -> { });
					shot(mc, "cos47-pet-" + pet.toLowerCase() + "-" + view);
				}
				add(1, () -> call("showcaseEnable", "Pets", false));
			}
			add(1, () -> call("showcaseEnable", "Freelook", false));
		}
		if (only.contains("Cos46Crystal")) {
			command(mc, "time set 6000");
			command(mc, "kill @e[type=end_crystal]");
			add(1, () -> {
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
				mc.player.setYRot(0);
				mc.player.setXRot(0);
			});
			command(mc, "summon minecraft:end_crystal ~ ~ ~3.5");
			add(1, () -> {
				mc.player.setXRot(5);
				mc.player.xRotO = 5;
			});
			for (String model : List.of("Vanilla", "Gem", "Orb", "Prism")) {
				add(1, () -> {
					call("showcaseSet", "Crystal Chams", "Model", model);
					call("showcaseEnable", "Crystal Chams", true);
				});
				add(10, () -> { });
				shot(mc, "cos46-crystal-" + model.toLowerCase() + "-a");
				add(7, () -> { });
				shot(mc, "cos46-crystal-" + model.toLowerCase() + "-b");
			}
			command(mc, "kill @e[type=end_crystal]");
			add(1, () -> {
				call("showcaseEnable", "Crystal Chams", false);
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
		}
		if (only.contains("SugAura")) {
			command(mc, "time set 6000");
			add(1, () -> {
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				call("showcaseSet", "Freelook", "Cinematic", "false");
				call("showcaseSet", "Freelook", "Distance", "2.6");
				mc.player.setYRot(0);
				mc.player.setXRot(0);
				call("showcaseEnable", "Freelook", true);
			});
			String[][] looks = {{"Crown", "Royal", "Gold"}, {"Crown", "Spiked", "Gold"}, {"Crown", "Circlet", "Gold"}, {"Crown", "Royal", "Glass"},
					{"Orbiting blades", "Sword", "Gold"}, {"Orbiting blades", "Energy", "Glow"}, {"Orbiting blades", "Sword", "Matte"}};
			for (String[] lk : looks) {
				for (float orbit : new float[] {180f, 90f, 135f}) {
					add(1, () -> {
						for (String other : List.of("Wings", "Halo", "Gyroscope", "Crystals", "Crown", "Orbiting blades", "Shield shell", "Horns",
								"Ears", "Rune circle", "Spirits", "Ribbons", "China hat")) {
							call("showcaseSet", "Player Aura", other, String.valueOf(other.equals(lk[0])));
						}
						call("showcaseSet", "Player Aura", lk[0].equals("Crown") ? "Crown style" : "Blade style", lk[1]);
						call("showcaseSet", "Player Aura", "Material", lk[2]);
						call("showcaseSet", "Player Aura", "Who", "Self");
						call("showcaseSet", "Player Aura", "Size", "1");
						call("showcaseSet", "Player Aura", "Speed", "1");
						call("showcaseEnable", "Player Aura", true);
					});
					add(2, () -> call("showcaseOrbit", orbit));
					add(10, () -> { });
					shot(mc, "sug-" + lk[0].toLowerCase().replace(' ', '-') + "-" + lk[1].toLowerCase() + "-" + lk[2].toLowerCase() + "-" + (int) orbit);
				}
			}
			add(1, () -> {
				call("showcaseSet", "Player Aura", "Crown", "true");
				call("showcaseSet", "Player Aura", "Orbiting blades", "false");
				call("showcaseSet", "Player Aura", "Crown style", "Royal");
				call("showcaseSet", "Player Aura", "Material", "Gold");
				mc.player.setXRot(50);
				mc.player.xRotO = 50;
			});
			add(10, () -> { });
			shot(mc, "sug-crown-royal-gold-lookdown");
			add(1, () -> {
				mc.player.setXRot(0);
				mc.player.xRotO = 0;
				call("showcaseEnable", "Player Aura", false);
				call("showcaseEnable", "Freelook", false);
			});
		}
		if (only.contains("SugEmotes")) {
			command(mc, "time set 6000");
			add(1, () -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
			for (String emote : (List<String>) call("showcaseOptions", "Emotes", "Emote")) {
				add(1, () -> {
					call("showcaseSet", "Emotes", "Emote", emote);
					call("showcaseSet", "Emotes", "Stop when moving", "false");
					call("showcaseSet", "Emotes", "Loop", "true");
					call("showcaseSet", "Emotes", "Face the camera", "true");
					call("showcaseEnable", "Emotes", true);
				});
				add(10, () -> { });
				for (int frame = 0; frame < 4; frame++) {
					shot(mc, "sug-emote-" + emote.toLowerCase().replace(" ", "-").replace("'", "") + "-" + frame);
					add(5, () -> { });
				}
				add(1, () -> call("showcaseEnable", "Emotes", false));
				add(6, () -> { });
			}
			add(1, () -> call("showcaseWheel", "press", 0f));
			add(10, () -> call("showcaseWheel", "point", 30f));
			add(3, () -> { });
			shot(mc, "sug-emote-wheel");
			add(1, () -> call("showcaseWheel", "release", 0f));
			add(10, () -> call("showcaseEnable", "Emotes", false));
		}
		if (only.contains("SugPets")) {
			command(mc, "time set 6000");
			add(1, () -> {
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				mc.player.setYRot(0);
				mc.player.setXRot(10);
				call("showcaseSet", "Pets", "Size", "1.6");
				call("showcaseSet", "Pets", "Color", "Natural");
				call("showcaseSet", "Pets", "Position", "Shoulder");
				call("showcaseSet", "Freelook", "Cinematic", "false");
				call("showcaseSet", "Freelook", "Distance", "2.4");
				call("showcaseEnable", "Freelook", true);
			});
			for (String pet : (List<String>) call("showcaseOptions", "Pets", "Pet")) {
				add(1, () -> {
					call("showcaseSet", "Pets", "Pet", pet);
					call("showcaseEnable", "Pets", true);
				});
				add(50, () -> { });
				for (int view = 0; view < 4; view++) {
					add(2, () -> call("showcaseOrbit", 90f));
					add(4, () -> { });
					shot(mc, "sug-pet-" + pet.toLowerCase().replace(' ', '-') + "-" + view);
				}
				add(1, () -> call("showcaseEnable", "Pets", false));
			}
			add(1, () -> call("showcaseEnable", "Freelook", false));
		}
		if (only.contains("BlurProbe")) {
			command(mc, "fill ~-6 ~ ~6 ~6 ~3 ~6 minecraft:glowstone");
			command(mc, "fill ~-4 ~ ~6 ~-4 ~3 ~6 minecraft:obsidian");
			command(mc, "fill ~0 ~ ~6 ~0 ~3 ~6 minecraft:obsidian");
			command(mc, "fill ~4 ~ ~6 ~4 ~3 ~6 minecraft:obsidian");
			for (String state : List.of("off", "on")) {
				add(1, () -> {
					mc.options.setCameraType(CameraType.FIRST_PERSON);
					mc.player.setYRot(-30);
					mc.player.setXRot(0);
					call("showcaseEnable", "Motion Blur", state.equals("on"));
				});
				add(10, () -> { });
				for (int i = 0; i < 6; i++) {
					add(1, () -> mc.player.setYRot(mc.player.getYRot() + 10));
					if (i == 4) {
						add(0, () -> Screenshot.grab(mc.gameDirectory, "showcase-blur-" + state + ".png", mc.gameRenderer.mainRenderTarget(), 1, c -> { }));
					}
				}
			}
			add(1, () -> call("showcaseEnable", "Motion Blur", false));
		}
		if (only.contains("FakeLag")) {
			command(mc, "kill @e[type=husk]");
			add(10, () -> {
				call("showcaseSet", "Fake Lag", "Only near players", "false");
				call("showcaseSet", "Fake Lag", "Mode", "Pulse");
				call("showcaseSet", "Fake Lag", "Lag", "600");
				call("showcaseSet", "Fake Lag", "Jitter", "0");
				call("showcaseEnable", "Fake Lag", true);
			});
			for (int i = 0; i < 40; i++) {
				int tick = i;
				add(1, () -> {
					mc.player.setPos(mc.player.getX() + 0.15, mc.player.getY(), mc.player.getZ());
					var sp = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
					System.out.printf("TEMPLE_FAKELAG pulse t%02d client %.2f server %.2f%n", tick, mc.player.getX(), sp.getX());
				});
			}
			add(1, () -> call("showcaseSet", "Fake Lag", "Mode", "Constant"));
			for (int i = 0; i < 30; i++) {
				int tick = i;
				add(1, () -> {
					mc.player.setPos(mc.player.getX() + 0.15, mc.player.getY(), mc.player.getZ());
					var sp = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
					System.out.printf("TEMPLE_FAKELAG constant t%02d client %.2f server %.2f%n", tick, mc.player.getX(), sp.getX());
				});
			}
			add(1, () -> call("showcaseEnable", "Fake Lag", false));
			command(mc, "summon husk ~ ~ ~3 {NoAI:1b,Rotation:[180f,0f]}");
			add(10, () -> {
				call("showcaseSet", "Fake Lag", "Mode", "Pulse");
				call("showcaseSet", "Fake Lag", "Lag", "1500");
				call("showcaseEnable", "Fake Lag", true);
				mc.player.setYRot(0);
				mc.player.setXRot(18);
			});
			add(4, () -> mc.player.setPos(mc.player.getX(), mc.player.getY(), mc.player.getZ() + 0.5));
			add(1, () -> {
				var sp = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
				System.out.printf("TEMPLE_FAKELAG before hit client z %.2f server z %.2f%n", mc.player.getZ(), sp.getZ());
				attackNearest(mc);
			});
			add(5, () -> {
				var sp = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
				var husk = sp.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, sp.getBoundingBox().inflate(8));
				System.out.printf("TEMPLE_FAKELAG after hit client z %.2f server z %.2f husk health %s%n", mc.player.getZ(), sp.getZ(),
						husk.isEmpty() ? "none" : String.valueOf(husk.get(0).getHealth()));
				call("showcaseEnable", "Fake Lag", false);
			});
			command(mc, "kill @e[type=husk]");
		}
		if (only.contains("Water46")) {
			net.minecraft.core.BlockPos[] o = new net.minecraft.core.BlockPos[1];
			command(mc, "gamemode creative");
			add(1, () -> {
				mc.player.getAbilities().flying = true;
				mc.player.setDeltaMovement(0, 0, 0);
				o[0] = mc.player.blockPosition();
			});
			add(1, () -> {
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				mc.options.enableVsync().set(false);
				mc.options.framerateLimit().set(260);
			});
			command(mc, "time set 5000");
			command(mc, "weather clear");
			command(mc, "fill ~-16 ~ ~-16 ~16 ~12 ~16 minecraft:air");
			command(mc, "fill ~-16 ~-9 ~-16 ~16 ~-1 ~16 minecraft:stone");
			command(mc, "fill ~-16 ~-1 ~-16 ~16 ~-1 ~16 minecraft:grass_block");
			command(mc, "fill ~-12 ~-6 ~-12 ~12 ~-1 ~12 minecraft:water");
			command(mc, "fill ~-12 ~-6 ~3 ~12 ~-4 ~12 minecraft:sand");
			command(mc, "fill ~-12 ~-3 ~7 ~12 ~-3 ~12 minecraft:sand");
			command(mc, "fill ~-12 ~-2 ~10 ~12 ~-2 ~12 minecraft:sand");
			command(mc, "fill ~-6 ~-6 ~-5 ~-6 ~1 ~-5 minecraft:glass");
			command(mc, "fill ~-4 ~-6 ~-5 ~-4 ~1 ~-5 minecraft:light_blue_stained_glass");
			command(mc, "setblock ~-8 ~-1 ~-2 minecraft:ice");
			command(mc, "fill ~13 ~-1 ~-12 ~16 ~3 ~12 minecraft:stone");
			command(mc, "fill ~13 ~4 ~-1 ~16 ~4 ~-1 minecraft:stone");
			command(mc, "fill ~13 ~4 ~1 ~16 ~4 ~1 minecraft:stone");
			command(mc, "setblock ~16 ~4 ~0 minecraft:water");
			command(mc, "setblock ~15 ~4 ~0 minecraft:water[level=1]");
			command(mc, "setblock ~14 ~4 ~0 minecraft:water[level=2]");
			command(mc, "setblock ~13 ~4 ~0 minecraft:water[level=3]");
			command(mc, "fill ~12 ~0 ~0 ~12 ~4 ~0 minecraft:water[level=8]");
			command(mc, "summon minecraft:oak_boat ~2 ~-0.5 ~-3");
			command(mc, "summon minecraft:cow ~-2 ~-1 ~10 {NoAI:1b}");
			java.util.function.BiConsumer<double[], String> view = (at, name) -> {
				add(1, () -> {
					mc.player.getAbilities().flying = true;
					mc.player.setDeltaMovement(0, 0, 0);
					mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f",
							o[0].getX() + at[0], o[0].getY() + at[1], o[0].getZ() + at[2], at[3], at[4]));
				});
				add(20, () -> mc.player.setDeltaMovement(0, 0, 0));
				shot(mc, name);
			};
			double[] overview = {0.5, 4, 15.5, 180, 22};
			java.util.function.Consumer<String> frameTime = label -> {
				int[] fps = new int[5];
				for (int i = 0; i < 5; i++) {
					int k = i;
					add(20, () -> fps[k] = mc.getFps());
				}
				add(1, () -> {
					int[] sorted = fps.clone();
					java.util.Arrays.sort(sorted);
					System.out.printf("TEMPLE_WATER46 %s fps %d (%.2f ms) samples %s%n", label, sorted[2], 1000.0 / Math.max(1, sorted[2]),
							java.util.Arrays.toString(fps));
				});
			};
			add(40, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				call("showcaseEnable", "Realistic Water", false);
			});
			view.accept(overview, "water46-vanilla");
			frameTime.accept("vanilla");
			add(1, () -> {
				call("showcaseSet", "Realistic Water", "Intensity", "100");
				call("showcaseSet", "Realistic Water", "Waves", "60");
				call("showcaseSet", "Realistic Water", "World reflections", "true");
				call("showcaseSet", "Realistic Water", "Foam", "true");
				call("showcaseSet", "Realistic Water", "Caustics", "true");
				call("showcaseSet", "Realistic Water", "Underwater", "true");
				call("showcaseSet", "Realistic Water", "Volumetric", "false");
				call("showcaseEnable", "Realistic Water", true);
			});
			add(80, () -> { });
			view.accept(overview, "water46-overview");
			frameTime.accept("water");
			view.accept(new double[] {0.5, 0.4, 13.5, 180, 4}, "water46-grazing");
			view.accept(new double[] {0.5, 2.5, 9.5, 180, 70}, "water46-shallows");
			view.accept(new double[] {5.5, 3, 5.5, -125, 12}, "water46-waterfall");
			add(1, () -> call("showcaseEnable", "Realistic Water", false));
			view.accept(new double[] {5.5, 3, 5.5, -125, 12}, "water46-waterfall-vanilla");
			add(1, () -> call("showcaseEnable", "Realistic Water", true));
			view.accept(new double[] {-3.5, 0.5, 2.5, 160, 20}, "water46-glass");
			view.accept(new double[] {0.5, -4, -2.5, 0, -10}, "water46-underwater");
			view.accept(new double[] {0.5, -4, -2.5, 0, -70}, "water46-under-up");
			add(1, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			view.accept(new double[] {0.5, -2.2, 0.5, 0, 35}, "water46-f5");
			add(1, () -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			frameTime.accept("underwater");
			add(1, () -> call("showcaseSet", "Realistic Water", "World reflections", "false"));
			view.accept(overview, "water46-no-reflections");
			frameTime.accept("no-reflections");
			add(1, () -> call("showcaseSet", "Realistic Water", "World reflections", "true"));
			command(mc, "time set 12600");
			view.accept(new double[] {0.5, 1.5, 14.5, 200, 10}, "water46-sunset");
			command(mc, "time set 18000");
			view.accept(overview, "water46-night");
			command(mc, "time set 5000");
			command(mc, "weather rain");
			add(60, () -> { });
			view.accept(overview, "water46-rain");
			command(mc, "weather clear");
			add(1, () -> call("showcaseEnable", "Realistic Water", false));
		}
		if (only.contains("World47")) {
			net.minecraft.core.BlockPos[] o = new net.minecraft.core.BlockPos[1];
			command(mc, "gamemode creative");
			add(1, () -> {
				mc.player.getAbilities().flying = true;
				mc.player.setDeltaMovement(0, 0, 0);
				o[0] = mc.player.blockPosition();
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				mc.options.enableVsync().set(false);
				mc.options.framerateLimit().set(260);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
			command(mc, "time set 3000");
			command(mc, "weather clear");
			command(mc, "fill ~-24 ~ ~-24 ~24 ~14 ~24 minecraft:air");
			command(mc, "fill ~-24 ~-1 ~-24 ~24 ~-1 ~24 minecraft:grass_block");
			command(mc, "fill ~-20 ~ ~-20 ~-8 ~ ~-12 minecraft:grass_block");
			command(mc, "fill ~-18 ~1 ~-19 ~-11 ~1 ~-14 minecraft:grass_block");
			command(mc, "fill ~6 ~ ~-6 ~6 ~5 ~-6 minecraft:oak_log");
			command(mc, "fill ~4 ~4 ~-8 ~8 ~6 ~-4 minecraft:oak_leaves keep");
			command(mc, "fill ~5 ~7 ~-7 ~7 ~7 ~-5 minecraft:oak_leaves keep");
			command(mc, "fill ~-6 ~ ~-8 ~0 ~3 ~-8 minecraft:stone_bricks");
			command(mc, "fill ~-3 ~ ~-8 ~-3 ~1 ~-8 minecraft:air");
			command(mc, "fill ~12 ~ ~-12 ~13 ~8 ~-11 minecraft:smooth_stone");
			command(mc, "fill ~-12 ~3 ~-2 ~-8 ~3 ~2 minecraft:oak_slab");
			command(mc, "fill ~-12 ~ ~-2 ~-12 ~2 ~-2 minecraft:oak_fence");
			command(mc, "fill ~-8 ~ ~2 ~-8 ~2 ~2 minecraft:oak_fence");
			command(mc, "fill ~-12 ~ ~2 ~-12 ~2 ~2 minecraft:oak_fence");
			command(mc, "fill ~-8 ~ ~-2 ~-8 ~2 ~-2 minecraft:oak_fence");
			command(mc, "setblock ~-10 ~2 ~0 minecraft:lantern[hanging=true]");
			for (int[] p : new int[][] {{2, 3}, {3, 5}, {-2, 4}, {-4, 2}, {1, -2}, {4, -1}, {-5, -3}, {8, 2}, {9, 5}, {-7, 6}}) {
				command(mc, "setblock ~" + p[0] + " ~ ~" + p[1] + " minecraft:short_grass");
			}
			for (int[] p : new int[][] {{3, 2}, {-3, 6}, {7, -1}, {-6, -5}}) {
				command(mc, "setblock ~" + p[0] + " ~ ~" + p[1] + " minecraft:tall_grass[half=lower]");
				command(mc, "setblock ~" + p[0] + " ~1 ~" + p[1] + " minecraft:tall_grass[half=upper]");
			}
			command(mc, "setblock ~0 ~ ~2 minecraft:poppy");
			command(mc, "fill ~-1 ~-1 ~8 ~-1 ~-1 ~20 minecraft:dirt_path");
			command(mc, "summon minecraft:cow ~3 ~ ~1 {NoAI:1b}");
			java.util.function.BiConsumer<double[], String> view = (at, name) -> {
				add(1, () -> {
					mc.player.getAbilities().flying = true;
					mc.player.setDeltaMovement(0, 0, 0);
					mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f",
							o[0].getX() + at[0], o[0].getY() + at[1], o[0].getZ() + at[2], at[3], at[4]));
				});
				add(30, () -> mc.player.setDeltaMovement(0, 0, 0));
				shot(mc, name);
			};
			java.util.function.Consumer<String> frameTime = label -> {
				int[] fps = new int[5];
				for (int i = 0; i < 5; i++) {
					int k = i;
					add(20, () -> {
						fps[k] = mc.getFps();
						mc.getFramerateLimitTracker().onInputReceived();
					});
				}
				add(1, () -> {
					int[] sorted = fps.clone();
					java.util.Arrays.sort(sorted);
					System.out.printf("TEMPLE_WORLD47 %s fps %d (%.2f ms) samples %s%n", label, sorted[2], 1000.0 / Math.max(1, sorted[2]),
							java.util.Arrays.toString(fps));
				});
			};
			double[] overview = {0.5, 3.2, 12.5, 180, 16};
			double[] low = {0.5, 0.2, 7.5, 180, 4};
			add(1, () -> {
				for (String part : List.of("Realistic Water", "Wet Ground", "Waving Foliage", "Realistic Grass", "Realistic Lighting")) {
					call("showcaseEnable", part, false);
				}
				call("showcaseEnable", "Realistic World", true);
				call("showcaseEnable", "Skybox", false);
			});
			add(1, () -> mc.getFramerateLimitTracker().onInputReceived());
			view.accept(overview, "world47-vanilla");
			frameTime.accept("vanilla");
			add(1, () -> call("showcaseEnable", "Realistic Grass", true));
			add(120, () -> { });
			view.accept(overview, "world47-grass");
			frameTime.accept("grass");
			view.accept(low, "world47-grass-low");
			view.accept(new double[] {2.5, 0.6, 4.5, 150, 30}, "world47-grass-close");
			add(1, () -> call("showcaseEnable", "Realistic Lighting", true));
			add(160, () -> { });
			view.accept(overview, "world47-lit");
			frameTime.accept("grass+lighting");
			view.accept(low, "world47-lit-low");
			view.accept(new double[] {-3.5, 1.2, 4.5, -120, 14}, "world47-lit-roof");
			view.accept(new double[] {10.5, 2.2, 2.5, 150, 18}, "world47-lit-tree");
			view.accept(new double[] {-1.5, 0.3, -5.5, -90, -32}, "world47-shafts");
			view.accept(new double[] {-14.5, 0.3, 6.5, -90, -40}, "world47-sun");
			view.accept(new double[] {-14.5, 0.3, 6.5, -60, -30}, "world47-flare");
			view.accept(new double[] {-1.5, 0.3, -3.5, -100, -40}, "world47-beams");
			view.accept(new double[] {8.5, 2.5, 14.5, 90, 2}, "world47-shafts-away");
			view.accept(new double[] {0.5, 2.5, 18.5, 180, 0}, "world47-shafts-side");
			view.accept(new double[] {0.5, 6.5, 18.5, 180, 30}, "world47-rows");
			command(mc, "time set 500");
			add(60, () -> { });
			view.accept(new double[] {-1.5, 0.3, -5.5, -90, -8}, "world47-shafts-low");
			view.accept(new double[] {-14.5, 0.6, 12.5, -100, -4}, "world47-sun-low");
			command(mc, "time set 3000");
			add(40, () -> { });
			add(1, () -> call("showcaseEnable", "Realistic Grass", false));
			add(40, () -> { });
			view.accept(overview, "world47-lit-only");
			frameTime.accept("lighting");
			add(1, () -> call("showcaseSet", "Realistic Lighting", "Quality", "Low"));
			frameTime.accept("lighting low");
			add(1, () -> call("showcaseSet", "Realistic Lighting", "Quality", "High"));
			frameTime.accept("lighting high");
			add(1, () -> {
				call("showcaseSet", "Realistic Lighting", "Quality", "Medium");
				call("showcaseSet", "Realistic Lighting", "Ambient occlusion", "0");
			});
			frameTime.accept("lighting no ao");
			add(1, () -> call("showcaseSet", "Realistic Lighting", "Ambient occlusion", "50"));
			add(1, () -> call("showcaseEnable", "Realistic Grass", true));
			view.accept(new double[] {-5.5, 5, 13.5, 180, 55}, "world47-before");
			command(mc, "summon minecraft:iron_golem ~ ~-5 ~-3.5 {NoAI:1b,Silent:1b,Tags:[\"walker\"]}");
			add(30, () -> { });
			shot(mc, "world47-standing");
			for (int i = 0; i < 12; i++) {
				command(mc, "execute as @e[tag=walker] at @s run tp @s ~0.5 ~ ~");
			}
			view.accept(new double[] {-1.5, 5, 13.5, 180, 55}, "world47-walked");
			command(mc, "kill @e[tag=walker]");
			add(1, () -> {
				call("showcaseSet", "Wet Ground", "Style", "Puddles");
				call("showcaseSet", "Wet Ground", "Puddles", "60");
				call("showcaseEnable", "Wet Ground", true);
			});
			add(60, () -> { });
			view.accept(overview, "world47-wet");
			view.accept(new double[] {2.5, 0.8, 5.5, 160, 26}, "world47-wet-close");
			add(1, () -> call("showcaseEnable", "Wet Ground", false));
			command(mc, "time set 11800");
			add(80, () -> { });
			view.accept(overview, "world47-sunset");
			view.accept(new double[] {-9.5, 2.2, 8.5, -150, 12}, "world47-sunset-long");
			command(mc, "time set 18000");
			add(80, () -> { });
			view.accept(overview, "world47-night");
			view.accept(new double[] {-3.5, 1.2, 4.5, -120, 14}, "world47-night-lantern");
			command(mc, "time set 6000");
			add(1, () -> call("showcaseEnable", "Realistic World", false));
		}
		if (only.contains("Drop46")) {
			net.minecraft.core.BlockPos[] o = new net.minecraft.core.BlockPos[1];
			command(mc, "gamemode creative");
			add(1, () -> {
				mc.player.getAbilities().flying = true;
				mc.player.setDeltaMovement(0, 0, 0);
				o[0] = mc.player.blockPosition();
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
				mc.options.enableVsync().set(false);
				mc.options.framerateLimit().set(260);
			});
			command(mc, "time set 5000");
			command(mc, "weather clear");
			command(mc, "fill ~-12 ~ ~-14 ~12 ~10 ~6 minecraft:air");
			command(mc, "fill ~-12 ~-4 ~-14 ~12 ~-1 ~6 minecraft:smooth_stone");
			command(mc, "setblock ~4 ~-1 ~-4 minecraft:air");
			command(mc, "fill ~-6 ~ ~-6 ~-3 ~1 ~-3 minecraft:stone_bricks");
			command(mc, "fill ~-2 ~-1 ~-12 ~2 ~-1 ~-9 minecraft:water");
			command(mc, "fill ~5 ~ ~-12 ~9 ~2 ~-8 minecraft:stone_bricks");
			command(mc, "fill ~6 ~1 ~-11 ~8 ~2 ~-9 minecraft:water");
			command(mc, "fill ~-11 ~ ~-2 ~-7 ~ ~2 minecraft:smooth_stone_slab");
			command(mc, "setblock ~-9 ~1 ~0 minecraft:oak_fence");
			command(mc, "setblock ~-8 ~ ~1 minecraft:oak_trapdoor[half=bottom,open=false]");
			java.util.function.BiConsumer<int[], String> place = (at, block) -> add(1, () -> mc.player.connection.sendCommand(String.format(
					java.util.Locale.ROOT, "setblock %d %d %d %s", o[0].getX() + at[0], o[0].getY() + at[1], o[0].getZ() + at[2], block)));
			java.util.function.BiConsumer<double[], String> camera = (at, name) -> {
				add(1, () -> {
					mc.player.getAbilities().flying = true;
					mc.player.setDeltaMovement(0, 0, 0);
					mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f",
							o[0].getX() + at[0], o[0].getY() + at[1], o[0].getZ() + at[2], at[3], at[4]));
				});
				add(10, () -> mc.player.setDeltaMovement(0, 0, 0));
			};
			java.util.function.BiConsumer<Object[], String> pour = (args, name) -> {
				camera.accept((double[]) args[0], name);
				int[] block = (int[]) args[1];
				if (args.length > 2) {
					add(1, () -> call("showcaseBreak"));
					place.accept((int[]) args[2], "minecraft:air");
				} else {
					add(1, () -> call("showcasePour", o[0].getX() + block[0], o[0].getY() + block[1], o[0].getZ() + block[2]));
				}
				place.accept(block, "minecraft:water");
				add(2, () -> { });
				shot(mc, name + "-0");
				add(8, () -> { });
				shot(mc, name + "-1");
				add(20, () -> { });
				shot(mc, name + "-2");
				add(60, () -> { });
				shot(mc, name + "-3");
				add(1, () -> call("showcaseSet", "Realistic Water", "Volumetric", "false"));
				add(2, () -> call("showcaseSet", "Realistic Water", "Volumetric", "true"));
				add(1, () -> mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT,
						"fill %d %d %d %d %d %d minecraft:air replace minecraft:water", o[0].getX() - 12, o[0].getY(), o[0].getZ() - 7,
						o[0].getX() + 4, o[0].getY() + 3, o[0].getZ() + 6)));
				add(1, () -> mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT,
						"setblock %d %d %d minecraft:air", o[0].getX() + 4, o[0].getY() - 1, o[0].getZ() - 4)));
				add(20, () -> { });
			};
			add(20, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				call("showcaseSet", "Realistic Water", "Intensity", "100");
				call("showcaseSet", "Realistic Water", "Volumetric", "true");
				call("showcaseEnable", "Realistic Water", true);
			});
			add(40, () -> { });
			pour.accept(new Object[] {new double[] {0.5, 2.5, 0.5, 180, 40}, new int[] {0, 0, -4}}, "drop46-flat");
			pour.accept(new Object[] {new double[] {4.5, 2.5, -0.5, 180, 50}, new int[] {4, 0, -4}}, "drop46-hole");
			pour.accept(new Object[] {new double[] {-1.5, 3.5, -1.5, 225, 30}, new int[] {-4, 2, -4}}, "drop46-ledge");
			pour.accept(new Object[] {new double[] {0.5, 2.5, -4.5, 180, 40}, new int[] {0, 0, -8}}, "drop46-pool");
			pour.accept(new Object[] {new double[] {4.5, 3, -5.5, 200, 35}, new int[] {7, 1, -8}, new int[] {7, 1, -8}}, "drop46-break");
			pour.accept(new Object[] {new double[] {-8.5, 2.5, 4.5, 180, 40}, new int[] {-9, 2, 0}}, "drop46-shapes");
			camera.accept(new double[] {0.5, 2.5, 0.5, 180, 40}, "drop46-stack");
			for (int i = 0; i < 3; i++) {
				add(1, () -> call("showcasePour", o[0].getX(), o[0].getY(), o[0].getZ() - 4));
				add(12, () -> { });
			}
			shot(mc, "drop46-stack-0");
			add(40, () -> { });
			shot(mc, "drop46-stack-1");
			add(200, () -> { });
			shot(mc, "drop46-stack-2");
			add(1, () -> call("showcaseSet", "Realistic Water", "Volumetric", "false"));
		}
		if (only.contains("Drift46")) {
			net.minecraft.core.BlockPos[] o = new net.minecraft.core.BlockPos[1];
			add(1, () -> o[0] = mc.player.blockPosition());
			add(1, () -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
			command(mc, "gamemode creative");
			command(mc, "time set 6000");
			command(mc, "weather clear");
			command(mc, "fill ~-14 ~ ~-14 ~14 ~12 ~14 minecraft:air");
			command(mc, "fill ~-14 ~-4 ~-14 ~14 ~-2 ~14 minecraft:stone");
			command(mc, "fill ~-14 ~-1 ~-14 ~14 ~-1 ~14 minecraft:grass_block");
			command(mc, "fill ~5 ~ ~1 ~5 ~1 ~9 minecraft:stone_bricks");
			command(mc, "fill ~-5 ~ ~6 ~-5 ~3 ~6 minecraft:oak_log");
			command(mc, "fill ~-7 ~3 ~4 ~-3 ~4 ~8 minecraft:oak_leaves[persistent=true]");
			command(mc, "setblock ~-5 ~3 ~6 minecraft:oak_log");
			command(mc, "setblock ~-1 ~ ~11 minecraft:torch");
			command(mc, "fill ~1 ~ ~11 ~3 ~ ~13 minecraft:stone");
			java.util.function.BiConsumer<double[], String> view = (at, name) -> {
				add(1, () -> {
					mc.player.setDeltaMovement(0, 0, 0);
					mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f",
							o[0].getX() + at[0], o[0].getY() + at[1], o[0].getZ() + at[2], at[3], at[4]));
				});
				add(20, () -> { });
				shot(mc, name);
			};
			add(20, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				airOnly("Snow");
				call("showcaseSet", "Air Effects", "Volumetric snow", "true");
				call("showcaseSet", "Air Effects", "Snow drifts", "false");
				call("showcaseEnable", "Air Effects", true);
			});
			add(60, () -> System.out.println("TEMPLE_DRIFT46 fps before drifts " + mc.getFps()));
			add(1, () -> {
				call("showcaseSet", "Air Effects", "Snow drifts", "true");
				call("showcaseSet", "Air Effects", "Drift speed", "400");
				call("showcaseSet", "Air Effects", "Wind", "1.5");
				call("showcaseSet", "Air Effects", "Color", "Natural");
				call("showcaseEnable", "Air Effects", true);
			});
			add(600, () -> System.out.println("TEMPLE_DRIFT46 fps at 30s " + mc.getFps()));
			view.accept(new double[] {0.5, 0, 0.5, 0, 22}, "drift46-30s");
			add(1200, () -> System.out.println("TEMPLE_DRIFT46 fps at 90s " + mc.getFps()));
			view.accept(new double[] {0.5, 0, 0.5, 0, 22}, "drift46-90s");
			view.accept(new double[] {8.5, 0, 5.5, 90, 22}, "drift46-lee-side");
			view.accept(new double[] {-1.5, 0, 2.5, 45, 22}, "drift46-tree");
			view.accept(new double[] {0.5, 0, 5.5, 0, 32}, "drift46-torch-ledge");
			view.accept(new double[] {2.5, 0.2, 0.2, 180, 62}, "drift46-before-hole");
			add(1, () -> mc.player.connection.sendCommand("setblock " + (o[0].getX() + 2) + " " + (o[0].getY() - 1) + " "
					+ (o[0].getZ() - 2) + " minecraft:air"));
			add(1, () -> mc.player.connection.sendCommand("setblock " + (o[0].getX() + 3) + " " + (o[0].getY() - 1) + " "
					+ (o[0].getZ() - 2) + " minecraft:air"));
			add(20, () -> { });
			view.accept(new double[] {2.5, 0.2, 0.2, 180, 62}, "drift46-hole");
			add(400, () -> { });
			view.accept(new double[] {2.5, 0.2, 0.2, 180, 62}, "drift46-hole-later");
			add(1, () -> mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.2f %d %.2f 180 10",
					o[0].getX() - 3.5, o[0].getY(), o[0].getZ() - 4.5)));
			add(10, () -> { });
			for (int i = 0; i < 40; i++) {
				add(1, () -> mc.player.setDeltaMovement(0.02, mc.player.getDeltaMovement().y, -0.2));
			}
			add(10, () -> {
				mc.player.setYRot(0);
				mc.player.setXRot(55);
			});
			shot(mc, "drift46-footprints");
			add(1, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			add(10, () -> { });
			shot(mc, "drift46-standing");
			add(1, () -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			view.accept(new double[] {0.5, 14, 0.5, 30, 18}, "drift46-far");
			command(mc, "time set 14000");
			add(40, () -> System.out.println("TEMPLE_DRIFT46 fps at night " + mc.getFps()));
			view.accept(new double[] {0.5, 0, -6.5, 0, 25}, "drift46-night");
			add(1, () -> {
				call("showcaseSet", "Air Effects", "Snow drifts", "false");
				call("showcaseEnable", "Air Effects", false);
			});
		}
		if (only.contains("Air46")) {
			add(1, () -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
			command(mc, "time set 18000");
			command(mc, "fill ~-14 ~ ~-14 ~14 ~8 ~14 minecraft:air");
			command(mc, "fill ~-14 ~-1 ~-14 ~14 ~-1 ~14 minecraft:grass_block");
			command(mc, "tp @s ~ ~ ~ 0 5");
			add(20, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				call("showcaseSet", "Air Effects", "Clear radius", "1.5");
				call("showcaseSet", "Air Effects", "Density", "160");
				call("showcaseSet", "Air Effects", "Radius", "10");
				call("showcaseSet", "Air Effects", "Size", "1");
				call("showcaseSet", "Air Effects", "Color", "Natural");
				call("showcaseSet", "Air Effects", "Brightness", "80");
				call("showcaseSet", "Air Effects", "Bloom", "true");
				call("showcaseSet", "Air Effects", "Volumetric snow", "false");
				call("showcaseSet", "Air Effects", "Cube material", "Glow");
				call("showcaseEnable", "Air Effects", true);
			});
			add(1, () -> airOnly());
			add(60, () -> System.out.println("TEMPLE_AIR46 fps none " + mc.getFps()));
			List<String> all = List.of("Fireflies", "Embers", "Snow", "Stardust", "Matrix", "Light lines", "Cubes", "Rings", "Prisms", "Network", "Pillars");
			for (String effect : all) {
				add(1, () -> airOnly(effect));
				add(70, () -> { });
				shot(mc, "air46-" + effect.toLowerCase().replace(' ', '-'));
				add(1, () -> System.out.println("TEMPLE_AIR46 fps " + effect + " " + mc.getFps()));
			}
			add(1, () -> {
				call("showcaseSet", "Air Effects", "Size", "2.5");
				call("showcaseSet", "Air Effects", "Clear radius", "0.5");
				call("showcaseSet", "Air Effects", "Radius", "5");
				call("showcaseSet", "Air Effects", "Density", "60");
			});
			for (String effect : List.of("Fireflies", "Embers", "Snow", "Stardust", "Cubes", "Prisms", "Matrix")) {
				add(1, () -> airOnly(effect));
				add(70, () -> { });
				shot(mc, "air46-close-" + effect.toLowerCase());
			}
			add(1, () -> {
				call("showcaseSet", "Air Effects", "Size", "1");
				call("showcaseSet", "Air Effects", "Clear radius", "1.5");
				call("showcaseSet", "Air Effects", "Radius", "10");
				call("showcaseSet", "Air Effects", "Density", "160");
				call("showcaseSet", "Air Effects", "Color", "Theme");
				airOnly("Light lines", "Rings", "Network");
			});
			add(70, () -> { });
			shot(mc, "air46-theme");
			command(mc, "time set 6000");
			add(1, () -> {
				call("showcaseSet", "Air Effects", "Color", "Natural");
				call("showcaseSet", "Air Effects", "Cube material", "Glass");
				airOnly("Cubes", "Prisms", "Snow");
			});
			add(70, () -> { });
			shot(mc, "air46-day-glass");
			add(1, () -> {
				call("showcaseSet", "Air Effects", "Cube material", "Glow");
				airOnly("Fireflies", "Pillars", "Embers");
			});
			add(70, () -> { });
			shot(mc, "air46-day-glow");
			add(1, () -> {
				System.out.println("TEMPLE_AIR46 fps " + mc.getFps());
				call("showcaseEnable", "Air Effects", false);
				airOnly("Fireflies");
			});
		}
		if (only.contains("AirFx")) {
			add(1, () -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
			command(mc, "time set 18000");
			command(mc, "fill ~-12 ~ ~-12 ~12 ~6 ~12 minecraft:air");
			command(mc, "fill ~-12 ~-1 ~-12 ~12 ~-1 ~12 minecraft:grass_block");
			command(mc, "tp @s ~ ~ ~ 0 5");
			add(20, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				call("showcaseSet", "Air Effects", "Clear radius", "2");
				call("showcaseSet", "Air Effects", "Density", "200");
				call("showcaseSet", "Air Effects", "Color", "Natural");
				call("showcaseEnable", "Air Effects", true);
			});
			for (String effect : List.of("Rings", "Prisms", "Network", "Pillars")) {
				add(1, () -> airOnly(effect));
				add(60, () -> { });
				shot(mc, "airfx-" + effect.toLowerCase());
			}
			add(1, () -> airOnly("Cubes", "Light lines", "Network", "Fireflies"));
			add(60, () -> { });
			shot(mc, "airfx-stacked");
			command(mc, "time set 6000");
			add(1, () -> airOnly("Cubes", "Prisms"));
			add(60, () -> { });
			shot(mc, "airfx-day-glow");
			add(1, () -> {
				call("showcaseEnable", "Air Effects", false);
				airOnly("Fireflies");
			});
			command(mc, "time set 6000");
			command(mc, "gamemode survival");
			net.minecraft.core.BlockPos[] nukerBlock = new net.minecraft.core.BlockPos[1];
			add(1, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setYRot(0);
				mc.player.setXRot(10);
				call("showcaseSet", "Break Animation", "Style", "Nuker");
				call("showcaseEnable", "Break Animation", true);
				nukerBlock[0] = mc.player.blockPosition().offset(0, 1, 2);
				mc.player.connection.sendCommand("setblock " + nukerBlock[0].getX() + " " + nukerBlock[0].getY() + " " + nukerBlock[0].getZ()
						+ " minecraft:stone");
			});
			add(3, () -> mc.gameMode.startDestroyBlock(nukerBlock[0], net.minecraft.core.Direction.NORTH));
			for (int i = 0; i < 60; i++) {
				add(1, () -> mc.gameMode.continueDestroyBlock(nukerBlock[0], net.minecraft.core.Direction.NORTH));
			}
			shot(mc, "airfx-nuker");
			add(1, () -> {
				mc.player.setYRot(38);
				mc.player.setXRot(28);
				mc.gameMode.continueDestroyBlock(nukerBlock[0], net.minecraft.core.Direction.NORTH);
			});
			add(1, () -> mc.gameMode.continueDestroyBlock(nukerBlock[0], net.minecraft.core.Direction.NORTH));
			shot(mc, "airfx-nuker-corner");
			add(1, () -> {
				mc.gameMode.stopDestroyBlock();
				call("showcaseEnable", "Break Animation", false);
				mc.player.connection.sendCommand("setblock " + nukerBlock[0].getX() + " " + nukerBlock[0].getY() + " " + nukerBlock[0].getZ() + " air");
			});
			command(mc, "gamemode creative");
			add(1, () -> {
				for (String other : List.of("Wings", "Halo", "Gyroscope", "Crystals", "Crown", "Orbiting blades", "Shield shell", "Horns",
						"Ears", "Rune circle", "Spirits", "Ribbons", "China hat")) {
					call("showcaseSet", "Player Aura", other, String.valueOf(other.equals("Wings") || other.equals("Halo")));
				}
				call("showcaseSet", "Player Aura", "Material", "Glow");
				call("showcaseEnable", "Player Aura", true);
				mc.player.setYRot(0);
				mc.player.setXRot(10);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			});
			add(20, () -> { });
			shot(mc, "airfx-neon-wings");
			add(1, () -> {
				call("showcaseEnable", "Player Aura", false);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
		}
		if (only.contains("Crosshair")) {
			command(mc, "item replace entity @s hotbar.0 with minecraft:wooden_sword");
			command(mc, "kill @e[type=husk]");
			command(mc, "summon husk ~ ~ ~2.6 {NoAI:1b,Rotation:[180f,0f]}");
			add(20, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.getInventory().setSelectedSlot(0);
				mc.player.setYRot(0);
				mc.player.setXRot(18);
				call("showcaseEnable", "Hit Markers", false);
				call("showcaseSet", "Crosshair", "Style", "Default");
				call("showcaseSet", "Crosshair", "Colour", "FFFFFF");
				call("showcaseSet", "Crosshair", "On hit", "Off");
				call("showcaseEnable", "Crosshair", true);
			});
			shot(mc, "xh-default");
			add(1, () -> call("showcaseSet", "Crosshair", "Colour", "E9B949"));
			add(2, () -> { });
			shot(mc, "xh-gold");
			add(1, () -> {
				call("showcaseSet", "Crosshair", "On hit", "Flash");
				call("showcaseSet", "Crosshair", "Reaction time", "600");
			});
			add(16, () -> { });
			add(2, () -> attackNearest(mc));
			shot(mc, "xh-flash");
			add(1, () -> call("showcaseSet", "Crosshair", "On hit", "Pulse"));
			add(16, () -> { });
			add(2, () -> attackNearest(mc));
			shot(mc, "xh-pulse");
			add(1, () -> {
				try {
					var img = new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB);
					var g2 = img.createGraphics();
					g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
					g2.setColor(java.awt.Color.WHITE);
					g2.setStroke(new java.awt.BasicStroke(5));
					g2.drawOval(8, 8, 48, 48);
					g2.fillOval(27, 27, 10, 10);
					g2.dispose();
					java.nio.file.Path file = mc.gameDirectory.toPath().resolve("crosshair-test.png").toAbsolutePath();
					javax.imageio.ImageIO.write(img, "png", file.toFile());
					call("showcaseSet", "Crosshair", "On hit", "Off");
					call("showcaseSet", "Crosshair", "Style", "Custom image");
					call("showcaseSet", "Crosshair", "Image", file.toString());
					call("showcaseSet", "Crosshair", "Size", "20");
					call("showcaseSet", "Crosshair", "Colour", "5AC8FA");
				} catch (java.io.IOException e) {
					throw new IllegalStateException(e);
				}
			});
			add(4, () -> { });
			shot(mc, "xh-image");
			add(1, () -> {
				call("showcaseSet", "Crosshair", "Style", "Default");
				call("showcaseSet", "Crosshair", "Colour", "FFFFFF");
				call("showcaseSet", "Crosshair", "Spin", "Always");
				call("showcaseSet", "Crosshair", "Spin speed", "120");
			});
			add(13, () -> { });
			shot(mc, "xh-spin");
			add(1, () -> call("showcaseSet", "Crosshair", "Spin", "On hit"));
			add(20, () -> { });
			add(1, () -> attackNearest(mc));
			shot(mc, "xh-spin-hit");
			add(1, () -> {
				call("showcaseSet", "Crosshair", "Spin", "Off");
				call("showcaseEnable", "Crosshair", false);
				call("showcaseSet", "Crosshair", "Style", "Default");
			});
			command(mc, "kill @e[type=husk]");
		}
		if (only.contains("Clean")) {
			command(mc, "effect give @s fire_resistance 99999 0 true");
			add(1, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setXRot(20);
				mc.player.xRotO = 20;
				call("showcaseEnable", "Crosshair", true);
				call("showcaseSet", "Smooth Hotbar", "Glide speed", "6");
				call("showcaseEnable", "Smooth Hotbar", true);
				call("showcaseEnable", "Chat Animations", true);
				call("showcaseEnable", "Clean View", true);
				mc.player.getInventory().setSelectedSlot(0);
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.FULL);
			});
			add(20, () -> { });
			add(3, () -> mc.player.getInventory().setSelectedSlot(8));
			shot(mc, "clean-hotbar");
			add(1, () -> {
				mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.FULL);
				mc.gui.hud.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal("An earlier line"));
			});
			add(20, () -> mc.gui.hud.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal("Chat slides in now")));
			shot(mc, "clean-chat");
			command(mc, "gamemode survival");
			add(1, () -> mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().getPlayerList().getPlayers().forEach(p -> p.igniteForSeconds(30))));
			add(20, () -> { });
			shot(mc, "clean-fire");
			add(1, () -> call("showcaseEnable", "Clean View", false));
			add(4, () -> { });
			shot(mc, "clean-fire-vanilla");
			add(1, () -> mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().getPlayerList().getPlayers().forEach(p -> p.clearFire())));
			command(mc, "gamemode creative");
			add(10, () -> {
				call("showcaseEnable", "Crosshair", false);
				call("showcaseEnable", "Smooth Hotbar", false);
				call("showcaseEnable", "Chat Animations", false);
				call("showcaseEnable", "Clean View", false);
			});
		}
		if (only.contains("Afterimage")) {
			add(1, () -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
			command(mc, "item replace entity @s weapon.mainhand with diamond_sword");
			add(1, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setXRot(10);
				mc.player.xRotO = 10;
				call("showcaseSet", "Hand Tweaks", "Swing", "Vanilla");
				call("showcaseSet", "Hand Tweaks", "Custom swing speed", "true");
				call("showcaseSet", "Hand Tweaks", "Swing duration", "14");
				call("showcaseSet", "Hand Tweaks", "Afterimages", "true");
				call("showcaseSet", "Hand Tweaks", "Trail length", "0.5");
				call("showcaseSet", "Hand Tweaks", "Frame gap", "35");
				call("showcaseEnable", "Hand Tweaks", true);
				call("showcaseEnable", "Hand Chams", false);
			});
			add(10, () -> mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND));
			add(6, () -> { });
			shot(mc, "afterimage-plain");
			add(20, () -> {
				call("showcaseSet", "Hand Chams", "Hand style", "Galaxy");
				call("showcaseSet", "Hand Chams", "Weapon style", "Chrome");
				call("showcaseEnable", "Hand Chams", true);
			});
			add(10, () -> mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND));
			add(6, () -> { });
			shot(mc, "afterimage-chams");
			add(20, () -> {
				call("showcaseEnable", "Hand Chams", false);
				call("showcaseSet", "Hand Tweaks", "Afterimages", "false");
				call("showcaseSet", "Hand Tweaks", "Custom swing speed", "false");
			});
		}
		if (only.contains("HeadSide")) {
			add(1, () -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
			add(1, () -> {
				call("showcaseSet", "Freelook", "Cinematic", "false");
				call("showcaseSet", "Freelook", "Distance", "2.5");
				mc.player.setYRot(0);
				mc.player.setXRot(0);
				call("showcaseEnable", "Freelook", true);
			});
			add(2, () -> call("showcaseOrbit", 90f));
			for (String piece : List.of("Halo", "Crown", "Horns", "Ears", "China hat")) {
				for (int pitch : new int[] {0, 60}) {
					add(1, () -> {
						for (String other : List.of("Wings", "Halo", "Gyroscope", "Crystals", "Crown", "Orbiting blades", "Shield shell", "Horns",
								"Ears", "Rune circle", "Spirits", "Ribbons", "China hat")) {
							call("showcaseSet", "Player Aura", other, String.valueOf(other.equals(piece)));
						}
						call("showcaseSet", "Player Aura", "Material", "Gold");
						call("showcaseEnable", "Player Aura", true);
						mc.player.setXRot(pitch);
						mc.player.xRotO = pitch;
					});
					add(8, () -> { });
					shot(mc, "hs-" + piece.toLowerCase().replace(' ', '-') + "-" + pitch);
				}
			}
			command(mc, "item replace entity @s armor.head with iron_helmet");
			add(1, () -> {
				mc.player.setXRot(0);
				mc.player.xRotO = 0;
			});
			add(8, () -> { });
			shot(mc, "hs-china-hat-helmet");
			command(mc, "item replace entity @s armor.head with air");
		}
		if (only.contains("Round8")) {
			add(1, () -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
			command(mc, "time set 6000");
			command(mc, "weather clear");
			command(mc, "effect give @s resistance 99999 3 true");
			add(1, () -> {
				call("showcaseBind", "Emotes", 66);
				call("showcaseBind", "Zoom", 67);
				call("showcaseBind", "Freelook", 342);
				call("showcaseBind", "Kill Aura", 82);
				call("showcaseSet", "HUD", "Speed graph", "true");
				call("showcaseSet", "HUD", "Keybinds", "true");
				call("showcaseSet", "HUD", "Enemy armor", "false");
				call("showcaseEnable", "Wet Ground", true);
				call("showcaseEnable", "Waving Foliage", true);
			});
			for (int i = 0; i < 50; i++) {
				int tick = i;
				add(1, () -> {
					mc.player.setYRot(0);
					mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, tick < 25 ? 0.25 : 0.1);
				});
			}
			command(mc, "summon zombie ~ ~ ~3 {NoAI:1b,Tags:[\"r8\"],Health:40f,equipment:{head:{id:\"minecraft:diamond_helmet\"},"
					+ "chest:{id:\"minecraft:netherite_chestplate\"},mainhand:{id:\"minecraft:iron_sword\"},offhand:{id:\"minecraft:totem_of_undying\"}},"
					+ "active_effects:[{id:\"minecraft:speed\",duration:6000,amplifier:1},{id:\"minecraft:strength\",duration:1200,amplifier:0}]}");
			add(10, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setYRot(0);
				mc.player.setXRot(10);
				var z = entity(mc, "r8");
				if (z != null) {
					mc.gameMode.attack(mc.player, z);
				}
			});
			add(8, () -> { });
			shot(mc, "r8-hud");
			command(mc, "kill @e[type=zombie]");
			add(1, () -> call("showcaseEnable", "Zoom", true));
			add(1, () -> call("showcaseZoom", 14));
			add(30, () -> { });
			shot(mc, "r8-zoom");
			add(1, () -> call("showcaseEnable", "Zoom", false));
			add(20, () -> { });
			add(1, () -> call("showcaseWheel", "press", 0f));
			add(10, () -> call("showcaseWheel", "point", 30f));
			add(3, () -> { });
			shot(mc, "r8-wheel");
			add(1, () -> call("showcaseWheel", "release", 0f));
			add(10, () -> call("showcaseEnable", "Emotes", false));
			for (String piece : List.of("Halo", "Crown", "Horns", "Ears")) {
				add(1, () -> {
					for (String other : List.of("Wings", "Halo", "Gyroscope", "Crystals", "Crown", "Orbiting blades", "Shield shell", "Horns",
							"Ears", "Rune circle", "Spirits", "Ribbons")) {
						call("showcaseSet", "Player Aura", other, String.valueOf(other.equals(piece)));
					}
					call("showcaseSet", "Player Aura", "Material", "Gold");
					call("showcaseEnable", "Player Aura", true);
					mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
					mc.player.setXRot(55);
					mc.player.xRotO = 55;
				});
				add(10, () -> { });
				shot(mc, "r8-head-" + piece.toLowerCase());
			}
			add(1, () -> {
				call("showcaseSet", "Player Aura", "Horns", "false");
				call("showcaseSet", "Player Aura", "Ears", "false");
				call("showcaseSet", "Player Aura", "Wings", "true");
				call("showcaseSet", "Player Aura", "Material", "Glow");
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.player.setXRot(10);
			});
			add(12, () -> { });
			shot(mc, "r8-glow");
			add(1, () -> call("showcaseEnable", "Player Aura", false));
			add(1, () -> {
				call("showcaseEnable", "Cape", true);
				call("showcaseEnable", "Freelook", true);
			});
			add(2, () -> call("showcaseOrbit", 20f));
			for (int i = 0; i < 16; i++) {
				add(1, () -> {
					mc.player.setYRot(0);
					mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0.25);
				});
			}
			shot(mc, "r8-cape");
			add(1, () -> {
				call("showcaseSet", "Cape", "Cape", "Image");
				call("showcaseSet", "Cape", "Cape image", "build/test-cape.png");
			});
			add(30, () -> { });
			shot(mc, "r8-cape-image");
			add(1, () -> {
				call("showcaseEnable", "Freelook", false);
				call("showcaseCapeEditor");
			});
			add(30, () -> { });
			shot(mc, "r8-cape-editor");
			add(1, () -> {
				mc.gui.setScreen(null);
				call("showcaseEnable", "Cape", false);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				call("showcaseSet", "Menu", "Click GUI", "Simple");
				call("openMenu", (Object) null);
			});
			add(15, () -> { });
			shot(mc, "r8-simple");
			add(1, () -> call("showcaseSettings", "Chams"));
			add(15, () -> { });
			shot(mc, "r8-simple-chams");
			add(1, () -> {
				mc.gui.setScreen(null);
				call("showcaseSet", "Menu", "Click GUI", "Glass");
			});
		}
		if (only.contains("R40")) {
			add(1, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				for (String m : List.of("Step", "Speed", "Kill Aura", "Criticals", "Velocity", "ESP", "Tracers", "Nametags",
						"Chams", "Storage ESP", "Block ESP", "Block Highlight", "Trails", "Jump Circles", "Hit Markers", "Damage Numbers",
						"Totem Pops", "Death Effects", "Air Effects", "Wet Ground", "Waving Foliage", "Auto Totem", "Auto Eat", "Notifier",
						"Donut Region", "Staff Display")) {
					call("showcaseEnable", m, true);
				}
				call("showcaseSet", "Staff Display", "Show when none", "true");
				call("showcaseSet", "HUD", "Speed graph", "true");
			});
			add(40, () -> { });
			shot(mc, "r40-hud");
			add(1, () -> {
				for (String m : List.of("Step", "Speed", "Kill Aura", "Criticals", "Velocity", "ESP", "Tracers", "Nametags",
						"Chams", "Storage ESP", "Block ESP", "Block Highlight", "Trails", "Jump Circles", "Hit Markers", "Damage Numbers",
						"Totem Pops", "Death Effects", "Air Effects", "Auto Totem", "Auto Eat", "Donut Region", "Staff Display")) {
					call("showcaseEnable", m, false);
				}
			});
			add(1, () -> {
				call("showcaseEnable", "Streamer Mode", true);
				call("showcaseEnable", "Coord Snapper", true);
			});
			add(5, () -> call("showcaseEnable", "Streamer Mode", false));
			add(1, () -> {
				call("showcaseSet", "Spoof", "Fake payment", "true");
				call("showcaseSet", "Spoof", "Fake money", "1.5M");
				call("showcaseEnable", "Spoof", true);
			});
			add(3, () -> mc.player.connection.sendCommand("pay Steve 12500"));
			add(5, () -> System.out.println("TEMPLE_R40 fake money now " + call("showcaseSettingValue", "Spoof", "Fake money")));
			for (String state : List.of("off", "on")) {
				add(1, () -> {
					call("showcaseEnable", "Motion Blur", state.equals("on"));
					mc.player.setYRot(0);
					mc.player.setXRot(5);
				});
				for (int i = 0; i < 10; i++) {
					add(1, () -> {
						mc.player.setYRot(mc.player.getYRot() + 14);
						mc.player.yRotO = mc.player.getYRot() - 14;
					});
					if (i == 5) {
						add(0, () -> Screenshot.grab(mc.gameDirectory, "showcase-r40-blur-" + state + ".png", mc.gameRenderer.mainRenderTarget(), 1, c -> { }));
					}
				}
			}
			add(1, () -> call("showcaseEnable", "Motion Blur", false));
			command(mc, "weather rain");
			command(mc, "time set 3000");
			add(1, () -> {
				call("showcaseEnable", "Wet Ground", true);
				mc.player.setYRot(0);
				mc.player.setXRot(35);
			});
			add(80, () -> { });
			shot(mc, "r40-wet-rain");
			command(mc, "weather clear");
			add(60, () -> { });
			shot(mc, "r40-wet-clear");
		}
		if (only.contains("Nav")) {
			navigation(mc);
		}
		if (only.contains("Tunnel")) {
			command(mc, "gamemode survival");
			command(mc, "effect give @s resistance 99999 4 true");
			command(mc, "effect give @s fire_resistance 99999 0 true");
			command(mc, "tp @s ~ ~ ~ 0 20");
			command(mc, "fill ~-12 ~ ~1 ~12 ~5 ~44 minecraft:stone");
			command(mc, "setblock ~ ~2 ~9 minecraft:lava");
			command(mc, "setblock ~4 ~1 ~22 minecraft:chest");
			command(mc, "item replace entity @s weapon.mainhand with minecraft:netherite_pickaxe[minecraft:enchantments={\"minecraft:efficiency\":5}]");
			double[] origin = new double[3];
			add(5, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				origin[0] = mc.player.getX();
				origin[1] = mc.player.getY();
				origin[2] = mc.player.getZ();
				call("showcaseSet", "Auto Tunnel", "Base finder", "true");
				call("showcaseSet", "Auto Tunnel", "Ignore near start", "16");
				call("showcaseSet", "Auto Tunnel", "On find", "Notify & stop");
				call("showcaseEnable", "Auto Tunnel", true);
			});
			for (int i = 0; i < 60; i++) {
				int n = i;
				add(10, () -> System.out.println(String.format(java.util.Locale.ROOT, "TEMPLE_TUNNEL t=%d dx=%.2f dy=%.2f dz=%.2f yaw=%.0f lava=%s",
						n * 10, mc.player.getX() - origin[0], mc.player.getY() - origin[1], mc.player.getZ() - origin[2], mc.player.getYRot(),
						mc.level.getBlockState(net.minecraft.core.BlockPos.containing(origin[0], origin[1] + 2, origin[2] + 9)).getBlock())));
				if (i == 12 || i == 30) {
					shot(mc, "tunnel-" + i);
				}
				if (i == 35) {
					add(1, () -> {
						int x = (int) Math.floor(origin[0]) + 9;
						int z = (int) Math.floor(origin[2]) + 34;
						int border = Math.floorDiv(z, 16) * 16;
						mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "fill %d %d %d %d %d %d minecraft:chest",
								x, (int) origin[1], border - 1, x + 2, (int) origin[1], border));
					});
				}
			}
			add(1, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			shot(mc, "tunnel-end");
			add(1, () -> call("showcaseEnable", "Auto Tunnel", false));
		}
		if (only.contains("SkyProbe")) {
			add(1, () -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
			command(mc, "time set 18000");
			add(1, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setXRot(-35);
				mc.player.setYRot(30);
			});
			add(60, () -> System.out.println("TEMPLE_SKYFPS vanilla " + mc.getFps()));
			for (String aurora : List.of("false", "true")) {
				for (String style : List.of("Painted", "Tendrils", "Silk", "Void", "Plasma", "Storm", "Nebula")) {
					add(1, () -> {
						call("showcaseSet", "Skybox", "Style", style);
						call("showcaseSet", "Skybox", "Aurora", aurora);
						call("showcaseEnable", "Skybox", true);
					});
					add(60, () -> System.out.println("TEMPLE_SKYFPS " + style + " aurora=" + aurora + " " + mc.getFps()));
				}
			}
			add(1, () -> call("showcaseEnable", "Skybox", false));
		}
		if (only.contains("Gallery")) {
			add(1, () -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
			command(mc, "gamemode survival");
			command(mc, "time set 6000");
			command(mc, "weather clear");
			command(mc, "gamerule doDaylightCycle false");
			command(mc, "gamerule do_daylight_cycle false");
			command(mc, "effect give @s resistance 99999 3 true");
			command(mc, "effect give @s saturation 99999 1 true");
			command(mc, "effect give @s regeneration 99999 2 true");
			command(mc, "kill @e[type=!player]");
			command(mc, "fill ~-12 ~ ~-6 ~12 ~8 ~26 minecraft:air");
			command(mc, "summon zombie ~0 ~ ~5 {NoAI:1b,PersistenceRequired:1b,Health:1000f,attributes:[{id:\"minecraft:max_health\",base:1000}],"
					+ "equipment:{head:{id:\"minecraft:iron_helmet\"},chest:{id:\"minecraft:diamond_chestplate\"},mainhand:{id:\"minecraft:iron_sword\"}}}");
			command(mc, "summon villager ~-3 ~ ~6 {NoAI:1b,PersistenceRequired:1b}");
			command(mc, "summon pig ~3 ~ ~6 {NoAI:1b,PersistenceRequired:1b}");
			command(mc, "setblock ~3 ~ ~3 minecraft:chest");
			command(mc, "setblock ~4 ~ ~3 minecraft:red_shulker_box");
			command(mc, "setblock ~-4 ~ ~3 minecraft:barrel");
			command(mc, "setblock ~-5 ~ ~3 minecraft:spawner");
			command(mc, "fill ~-9 ~ ~12 ~-6 ~2 ~14 minecraft:stone");
			command(mc, "setblock ~-8 ~1 ~13 minecraft:diamond_ore");
			command(mc, "setblock ~-7 ~1 ~13 minecraft:ancient_debris");
			command(mc, "setblock ~-7 ~ ~13 minecraft:gold_ore");
			command(mc, "setblock ~-4 ~-1 ~8 minecraft:obsidian");
			command(mc, "summon end_crystal ~-4 ~ ~8 {ShowBottom:0b}");
			command(mc, "setblock ~5 ~-1 ~9 minecraft:air");
			command(mc, "setblock ~5 ~-2 ~9 minecraft:obsidian");
			command(mc, "setblock ~4 ~-1 ~9 minecraft:obsidian");
			command(mc, "setblock ~6 ~-1 ~9 minecraft:obsidian");
			command(mc, "setblock ~5 ~-1 ~8 minecraft:obsidian");
			command(mc, "setblock ~5 ~-1 ~10 minecraft:obsidian");
			command(mc, "place feature minecraft:oak ~8 ~ ~15");
			command(mc, "place feature minecraft:birch ~-2 ~ ~19");
			command(mc, "fill ~-2 ~-1 ~21 ~2 ~-1 ~23 minecraft:water");
			command(mc, "fill ~-10 ~ ~10 ~10 ~ ~20 minecraft:short_grass replace air");
			command(mc, "summon item ~1 ~ ~3 {Item:{id:\"minecraft:diamond\",count:3},PickupDelay:32767}");
			command(mc, "summon item ~-1 ~ ~3 {Item:{id:\"minecraft:totem_of_undying\",count:1},PickupDelay:32767}");
			command(mc, "item replace entity @s weapon.mainhand with minecraft:diamond_sword");
			command(mc, "item replace entity @s weapon.offhand with minecraft:totem_of_undying");
			double[] home = new double[3];
			add(30, () -> {
				home[0] = mc.player.getX();
				home[1] = mc.player.getY();
				home[2] = mc.player.getZ();
			});
			Runnable goHome = () -> {
				mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f 0 0", home[0], home[1], home[2]));
				mc.player.setYRot(0);
				mc.player.setXRot(8);
				mc.player.setDeltaMovement(0, 0, 0);
			};
			java.util.Set<String> firstPerson = java.util.Set.of("Hand Chams", "Hand Tweaks", "Block Highlight", "Place Preview", "Wet Ground", "Waving Foliage",
					"Fullbright", "Motion Blur", "Skybox", "Ambience", "Zoom", "Damage Effects", "Vitals", "Break Animation",
					"Place Animation", "Block Effects", "Trajectories", "Hit Markers", "Damage Numbers");
			java.util.Set<String> night = java.util.Set.of("Aurora", "Fireflies", "Fullbright", "Ambience");
			java.util.Set<String> special = java.util.Set.of("Better Tooltips", "Freecam", "Spoof", "No Block Rotation", "Emotes", "Walk Style", "Pets",
					"Player Aura", "Logout Spots");
			boolean cosmeticsOnly = only.contains("GalleryCosmetics");
			if (cosmeticsOnly) {
				siteWindow(mc);
			}
			List<String> visual = new java.util.ArrayList<>();
			if (!cosmeticsOnly) {
				visual.addAll((List<String>) call("showcaseCategory", "RENDER"));
			}
			visual.addAll((List<String>) call("showcaseCategory", "COSMETICS"));
			for (String name : visual) {
				if (special.contains(name)) {
					continue;
				}
				List<String> styles = new java.util.ArrayList<>((List<String>) call("showcaseStyles", name));
				if (styles.isEmpty()) {
					styles.add(null);
				}
				String slug = name.toLowerCase(java.util.Locale.ROOT).replace(" ", "-");
				for (String style : styles.subList(0, Math.min(cosmeticsOnly ? 1 : 6, styles.size()))) {
					add(1, () -> {
						goHome.run();
						mc.options.setCameraType(firstPerson.contains(name) ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK);
						if (night.contains(name)) {
							mc.player.connection.sendCommand("time set 18000");
						}
						if (name.equals("Storm Effects")) {
							mc.player.connection.sendCommand("weather thunder");
						}
						if (style != null) {
							call("showcaseSet", name, "Style", style);
						}
						call("showcaseEnable", name, true);
					});
					add(8, () -> { });
					switch (name) {
						case "Hit Particles", "Hit Markers", "Damage Numbers", "Target ESP" -> {
							for (int i = 0; i < 3; i++) {
								add(5, () -> {
									var z = entity(mc, "");
									if (z != null) {
										mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
										mc.gameMode.attack(mc.player, z);
										mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
									}
								});
							}
						}
						case "Death Effects", "Totem Pops" -> {
							command(mc, "summon zombie ~0 ~ ~3 {NoAI:1b,Health:4f,equipment:{offhand:{id:\"minecraft:totem_of_undying\"}}}");
							add(4, () -> {
								var z = entity(mc, "");
								if (z != null) {
									mc.gameMode.attack(mc.player, z);
								}
							});
							command(mc, "damage @e[type=zombie,distance=..3.5,limit=1,sort=nearest] 100 minecraft:generic");
							add(6, () -> { });
							command(mc, "kill @e[type=zombie,distance=..3.5]");
							add(6, () -> { });
						}
						case "Damage Effects", "Vitals" -> {
							command(mc, "damage @s 6 minecraft:generic");
							add(4, () -> { });
						}
						case "Jump Circles" -> {
							add(4, () -> mc.player.jumpFromGround());
							add(12, () -> { });
						}
						case "Footsteps", "Trails", "Contrails", "Spinbot" -> {
							add(1, () -> mc.player.setYRot(-90));
							for (int i = 0; i < 18; i++) {
								add(1, () -> {
									mc.player.setYRot(-90);
									mc.player.setDeltaMovement(0.28, mc.player.getDeltaMovement().y, 0);
								});
							}
						}
						case "Teleport Effects" -> {
							add(1, () -> mc.player.connection.sendCommand("tp @s ~ ~ ~4"));
							add(6, () -> { });
						}
						case "Explosion Effects" -> {
							command(mc, "summon creeper ~3 ~ ~7 {Fuse:0,ignited:1b,ExplosionRadius:0b}");
							add(8, () -> { });
						}
						case "Pickup Streaks" -> {
							for (int i = 0; i < 4; i++) {
								command(mc, "summon item ~ ~1 ~ {Item:{id:\"minecraft:gold_ingot\",count:" + (i + 1) + "}}");
							}
							add(6, () -> { });
						}
						case "Projectile Trails" -> {
							for (int i = 0; i < 3; i++) {
								command(mc, "summon arrow ~" + (i - 1) + " ~2 ~1 {Motion:[0.0,0.35,1.1]}");
							}
							command(mc, "summon snowball ~ ~2 ~1 {Motion:[0.2,0.4,0.9]}");
							add(4, () -> { });
						}
						case "Trajectories" -> {
							command(mc, "item replace entity @s weapon.mainhand with minecraft:ender_pearl 16");
							add(4, () -> mc.player.setXRot(-12));
						}
						case "Place Preview", "Place Animation", "Block Effects" -> {
							command(mc, "item replace entity @s weapon.mainhand with minecraft:obsidian 64");
							add(3, () -> mc.player.setXRot(55));
							if (!name.equals("Place Preview")) {
								add(4, () -> {
									if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit) {
										mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
										mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
									}
								});
							}
						}
						case "Break Animation" -> {
							command(mc, "item replace entity @s weapon.mainhand with minecraft:wooden_shovel");
							add(3, () -> mc.player.setXRot(60));
							for (int i = 0; i < 14; i++) {
								add(1, () -> {
									if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit) {
										mc.gameMode.continueDestroyBlock(hit.getBlockPos(), hit.getDirection());
										mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
									}
								});
							}
						}
						case "Motion Blur" -> {
							for (int i = 0; i < 5; i++) {
								add(1, () -> mc.player.setYRot(mc.player.getYRot() + 12));
							}
						}
						case "Hand Tweaks" -> add(2, () -> mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND));
						case "Block Highlight" -> add(3, () -> mc.player.setXRot(40));
						case "Chunk Finder", "Sus Chunk Finder", "OreSim", "Ore ESP", "Storage ESP", "Block ESP" -> add(20, () -> { });
						default -> add(12, () -> { });
					}
					shot(mc, "g-" + slug + (style == null ? "" : "-" + style.toLowerCase(java.util.Locale.ROOT).replace(" ", "-")));
					add(2, () -> {
						call("showcaseEnable", name, false);
						if (night.contains(name) || name.equals("Storm Effects")) {
							mc.player.connection.sendCommand("time set 6000");
							mc.player.connection.sendCommand("weather clear");
						}
						mc.player.connection.sendCommand("item replace entity @s weapon.mainhand with minecraft:diamond_sword");
						mc.player.connection.sendCommand("kill @e[type=item,distance=..2]");
						mc.player.connection.sendCommand("kill @e[type=arrow]");
					});
				}
			}
			add(1, () -> {
				goHome.run();
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				call("showcaseSet", "Freelook", "Cinematic", "false");
				call("showcaseSet", "Freelook", "Distance", "3.4");
			});
			List<String> worn = List.of("Wings", "Halo", "Gyroscope", "Crystals", "Crown", "Orbiting blades", "Shield shell", "Cape", "Horns",
					"Ears", "Rune circle", "Spirits", "Ribbons");
			for (String piece : worn) {
				boolean back = piece.equals("Cape") || piece.equals("Wings");
				add(1, () -> {
					for (String other : worn) {
						call("showcaseSet", "Player Aura", other, String.valueOf(other.equals(piece)));
					}
					call("showcaseSet", "Player Aura", "Material", "Gold");
					call("showcaseEnable", "Player Aura", true);
					call("showcaseEnable", "Freelook", true);
				});
				add(3, () -> call("showcaseOrbit", back ? 25f : 200f));
				add(14, () -> { });
				shot(mc, "g-aura-" + piece.toLowerCase(java.util.Locale.ROOT).replace(" ", "-"));
				add(1, () -> call("showcaseEnable", "Freelook", false));
			}
			for (String material : List.of("Glass", "Light", "Matte", "Glow")) {
				add(1, () -> {
					for (String other : worn) {
						call("showcaseSet", "Player Aura", other, String.valueOf(other.equals("Wings") || other.equals("Halo")));
					}
					call("showcaseSet", "Player Aura", "Material", material);
					call("showcaseEnable", "Freelook", true);
				});
				add(3, () -> call("showcaseOrbit", 25f));
				add(14, () -> { });
				shot(mc, "g-aura-material-" + material.toLowerCase(java.util.Locale.ROOT));
				add(1, () -> call("showcaseEnable", "Freelook", false));
			}
			add(1, () -> call("showcaseEnable", "Player Aura", false));
			for (String kind : (List<String>) call("showcaseOptions", "Pets", "Pet")) {
				add(1, () -> {
					goHome.run();
					mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
					call("showcaseSet", "Pets", "Pet", kind);
					call("showcaseEnable", "Pets", true);
				});
				add(40, () -> { });
				shot(mc, "g-pet-" + kind.toLowerCase(java.util.Locale.ROOT).replace(" ", "-"));
				add(1, () -> call("showcaseEnable", "Pets", false));
			}
			for (String emote : (List<String>) call("showcaseOptions", "Emotes", "Emote")) {
				add(1, () -> {
					goHome.run();
					call("showcaseSet", "Emotes", "Emote", emote);
					call("showcaseSet", "Emotes", "Stop when moving", "false");
					call("showcaseSet", "Emotes", "Loop", "true");
					call("showcaseEnable", "Emotes", true);
				});
				add(16, () -> { });
				shot(mc, "g-emote-" + emote.toLowerCase(java.util.Locale.ROOT).replace(" ", "-"));
				add(2, () -> call("showcaseEnable", "Emotes", false));
			}
			for (String walk : (List<String>) call("showcaseOptions", "Walk Style", "Style")) {
				add(1, () -> {
					goHome.run();
					mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
					call("showcaseSet", "Walk Style", "Style", walk);
					call("showcaseEnable", "Walk Style", true);
					call("showcaseEnable", "Freelook", true);
				});
				add(2, () -> call("showcaseOrbit", 95f));
				for (int i = 0; i < 12; i++) {
					add(1, () -> {
						mc.player.setYRot(-90);
						mc.player.setDeltaMovement(0.2, mc.player.getDeltaMovement().y, 0);
					});
				}
				shot(mc, "g-walk-" + walk.toLowerCase(java.util.Locale.ROOT).replace(" ", "-"));
				add(1, () -> {
					call("showcaseEnable", "Walk Style", false);
					call("showcaseEnable", "Freelook", false);
				});
			}
			for (String look : List.of("Glass", "Dark")) {
				add(1, () -> {
					goHome.run();
					mc.options.setCameraType(CameraType.FIRST_PERSON);
					call("showcaseSet", "HUD", "HUD look", look);
					for (String widget : List.of("Keybinds", "Potions", "Armor", "Coordinates", "FPS", "Speed", "Speed graph", "Enemy armor")) {
						call("showcaseSet", "HUD", widget, "true");
					}
					call("showcaseToast", "Kill Aura", "enabled");
					var z = entity(mc, "");
					if (z != null) {
						mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
						mc.gameMode.attack(mc.player, z);
					}
				});
				add(20, () -> { });
				shot(mc, "g-hud-" + look.toLowerCase(java.util.Locale.ROOT));
			}
			add(1, () -> call("showcaseSet", "HUD", "HUD look", "Glass"));
			add(1, () -> call("openHudEditor", (Object) null));
			add(20, () -> { });
			shot(mc, "g-hud-editor");
			add(1, () -> mc.gui.setScreen(null));
			add(1, () -> call("openMenu", (Object) null));
			add(20, () -> { });
			shot(mc, "g-menu-glass");
			for (String page : List.of("Kill Aura", "Chams", "Player Aura", "HUD", "Item ESP", "Wet Ground")) {
				add(1, () -> call("showcaseSettings", page));
				add(14, () -> { });
				shot(mc, "g-menu-settings-" + page.toLowerCase(java.util.Locale.ROOT).replace(" ", "-"));
			}
			add(1, () -> {
				mc.gui.setScreen(null);
				call("showcaseSet", "Menu", "Click GUI", "Simple");
				call("openMenu", (Object) null);
			});
			add(15, () -> { });
			shot(mc, "g-menu-simple");
			add(1, () -> call("showcaseSettings", "Chams"));
			add(10, () -> { });
			shot(mc, "g-menu-simple-chams");
			add(1, () -> call("showcasePopup", "themes"));
			add(12, () -> { });
			shot(mc, "g-menu-themes");
			add(1, () -> {
				mc.gui.setScreen(null);
				call("showcaseSet", "Menu", "Click GUI", "Glass");
				mc.pauseGame(false);
			});
			add(20, () -> { });
			shot(mc, "g-menu-pause");
			add(1, () -> mc.gui.setScreen(null));
			command(mc, "give @s minecraft:red_shulker_box[container=[{slot:0,item:{id:\"minecraft:diamond\",count:64}},"
					+ "{slot:1,item:{id:\"minecraft:totem_of_undying\",count:1}},{slot:4,item:{id:\"minecraft:netherite_sword\",count:1}}]]");
			add(3, () -> {
				call("showcaseEnable", "Better Tooltips", true);
				mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
			});
			add(4, () -> {
				int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
				int top = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
				double scale = mc.getWindow().getGuiScale();
				org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().handle(), (left + 34) * scale, (top + 150) * scale);
			});
			add(6, () -> { });
			shot(mc, "g-tooltip");
			add(1, () -> mc.gui.setScreen(null));
		}
		if (only.contains("SiteMenus")) {
			List<String> on = List.of("Kill Aura", "Velocity", "Crystal Aura", "No Fall", "Step", "Auto Eat",
					"Waving Foliage", "Hand Chams");
			siteWindow(mc);
			command(mc, "time set 18000");
			command(mc, "gamemode spectator");
			command(mc, "tp @s ~ 190 ~");
			add(60, () -> { });
			add(1, () -> TrailerShots.hud(mc, false));
			add(1, () -> {
				call("showcaseSet", "Skybox", "Style", "Nebula");
				call("showcaseEnable", "Skybox", true);
				mc.player.setXRot(6);
				for (String m : on) {
					call("showcaseEnable", m, true);
				}
				call("showcaseSet", "Menu", "Click GUI", "Glass");
				call("openMenu", (Object) null);
			});
			add(30, () -> parkCursor(mc));
			add(10, () -> { });
			shot(mc, "site-glass");
			add(1, () -> {
				mc.gui.setScreen(null);
				call("showcaseSet", "Menu", "Click GUI", "Simple");
				call("openMenu", (Object) null);
			});
			add(20, () -> parkCursor(mc));
			add(10, () -> { });
			shot(mc, "site-simple");
			add(1, () -> {
				TrailerShots.hud(mc, true);
				mc.gui.setScreen(null);
				for (String m : on) {
					call("showcaseEnable", m, false);
				}
				call("showcaseSet", "Menu", "Click GUI", "Glass");
			});
		}
		if (only.contains("SimpleOnly")) {
			add(1, () -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
			add(1, () -> {
				call("showcaseSet", "Menu", "Click GUI", "Simple");
				call("openMenu", (Object) null);
			});
			add(15, () -> call("showcaseSettings", "Kill Aura"));
			add(12, () -> { });
			shot(mc, "simple-only");
			add(1, () -> {
				mc.gui.setScreen(null);
				call("showcaseSet", "Menu", "Click GUI", "Glass");
			});
		}
		if (only.contains("Round7")) {
			add(1, () -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
			command(mc, "time set 1000");
			command(mc, "weather clear");
			command(mc, "clear @s");
			command(mc, "gamemode survival");
			command(mc, "give @s minecraft:red_shulker_box[container=[{slot:0,item:{id:\"minecraft:diamond\",count:64}},"
					+ "{slot:1,item:{id:\"minecraft:totem_of_undying\",count:1}},{slot:4,item:{id:\"minecraft:netherite_sword\",count:1}},"
					+ "{slot:13,item:{id:\"minecraft:golden_apple\",count:12}},{slot:26,item:{id:\"minecraft:ender_pearl\",count:16}}]]");
			add(2, () -> {
				call("showcaseEnable", "Better Tooltips", true);
				mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
			});
			add(4, () -> {
				int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
				int top = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
				double scale = mc.getWindow().getGuiScale();
				org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().handle(), (left + 16) * scale, (top + 150) * scale);
			});
			add(6, () -> { });
			shot(mc, "r7-tooltip-shulker");
			add(1, () -> mc.gui.setScreen(null));
			add(1, () -> {
				call("showcaseSet", "Menu", "Click GUI", "Simple");
				call("openMenu", (Object) null);
			});
			add(15, () -> { });
			shot(mc, "r7-simple");
			add(1, () -> call("showcaseSettings", "Player Aura"));
			add(10, () -> { });
			shot(mc, "r7-simple-aura");
			add(1, () -> call("showcasePopup", "themes"));
			add(12, () -> { });
			shot(mc, "r7-themes");
			add(1, () -> call("showcaseTheme", 2));
			add(6, () -> { });
			shot(mc, "r7-themes-ocean");
			add(1, () -> mc.gui.screen().onClose());
			add(8, () -> { });
			shot(mc, "r7-simple-ocean");
			add(1, () -> {
				call("showcaseTheme", 0);
				mc.gui.setScreen(null);
				call("showcaseSet", "Menu", "Click GUI", "Glass");
				call("openMenu", (Object) null);
			});
			add(15, () -> { });
			shot(mc, "r7-glass");
			add(1, () -> mc.gui.setScreen(null));
			add(12, () -> call("showcaseWheel", "press", 0f));
			add(4, () -> call("showcaseWheel", "point", -50f));
			add(4, () -> { });
			shot(mc, "r7-wheel");
			add(1, () -> call("showcaseWheel", "release", 0f));
			add(14, () -> { });
			shot(mc, "r7-wheel-played");
			add(1, () -> {
				call("showcaseEnable", "Emotes", false);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
			command(mc, "place feature minecraft:oak ~4 ~ ~6");
			command(mc, "place feature minecraft:birch ~-3 ~ ~8");
			add(4, () -> {
				mc.player.setYRot(10);
				mc.player.setXRot(28);
				call("showcaseEnable", "Wet Ground", true);
				call("showcaseEnable", "Waving Foliage", true);
			});
			add(40, () -> { });
			shot(mc, "r7-wet");
			add(12, () -> { });
			shot(mc, "r7-wet-b");
			command(mc, "weather rain");
			add(80, () -> { });
			shot(mc, "r7-wet-rain");
			command(mc, "weather clear");
			add(1, () -> {
				call("showcaseEnable", "Wet Ground", false);
				call("showcaseEnable", "Waving Foliage", false);
			});
			command(mc, "summon item ~1 ~1 ~4 {Item:{id:\"minecraft:diamond\",count:3}}");
			command(mc, "summon item ~-1 ~1 ~5 {Item:{id:\"minecraft:totem_of_undying\",count:1}}");
			command(mc, "summon item ~0 ~1 ~7 {Item:{id:\"minecraft:cobblestone\",count:12}}");
			add(10, () -> {
				mc.player.setYRot(0);
				mc.player.setXRot(20);
				call("showcaseEnable", "Item ESP", true);
			});
			add(10, () -> { });
			shot(mc, "r7-itemesp");
			add(1, () -> call("showcaseSet", "Item ESP", "Beams", "true"));
			add(6, () -> { });
			shot(mc, "r7-itemesp-beams");
			add(1, () -> {
				call("showcaseSet", "Item ESP", "Beams", "false");
				call("showcaseEnable", "Item ESP", false);
			});
			command(mc, "kill @e[type=item]");
			command(mc, "summon zombie ~ ~ ~3 {NoAI:1b,Tags:[\"r7t\"],equipment:{head:{id:\"minecraft:diamond_helmet\"},"
					+ "chest:{id:\"minecraft:netherite_chestplate\"},mainhand:{id:\"minecraft:iron_sword\"},offhand:{id:\"minecraft:totem_of_undying\"}},"
					+ "active_effects:[{id:\"minecraft:speed\",duration:6000,amplifier:1},{id:\"minecraft:strength\",duration:1200,amplifier:0}]}");
			add(10, () -> {
				mc.player.setYRot(0);
				mc.player.setXRot(5);
				var z = entity(mc, "r7t");
				if (z != null) {
					mc.gameMode.attack(mc.player, z);
				}
			});
			add(6, () -> { });
			shot(mc, "r7-target");
			command(mc, "kill @e[tag=r7t]");
			command(mc, "gamemode creative");
			add(1, () -> {
				mc.options.guiScale().set(1);
				mc.resizeGui();
			});
			add(10, () -> { });
			shot(mc, "r7-hud-scale1");
			add(1, () -> {
				mc.options.guiScale().set(0);
				mc.resizeGui();
			});
		}
		if (only.contains("Round6")) {
			add(1, () -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
			add(1, () -> {
				call("showcaseSet", "Menu", "Click GUI", "Simple");
				call("openMenu", (Object) null);
			});
			add(20, () -> { });
			shot(mc, "r6-simple-menu");
			add(1, () -> call("showcaseSettings", "Kill Aura"));
			add(20, () -> { });
			shot(mc, "r6-simple-settings");
			add(1, () -> {
				mc.gui.setScreen(null);
				call("showcaseSet", "Menu", "Click GUI", "Glass");
				call("openMenu", (Object) null);
			});
			add(20, () -> { });
			shot(mc, "r6-glass-menu");
			add(1, () -> call("showcaseSettings", "Player Aura"));
			add(20, () -> { });
			shot(mc, "r6-aura-settings");
			add(1, () -> mc.gui.setScreen(null));
			add(1, () -> {
				call("showcaseSet", "HUD", "Speed graph", "true");
				call("showcaseSet", "HUD", "Armor", "true");
				call("showcaseSet", "HUD", "Keybinds", "true");
			});
			for (int scale : new int[] {2, 3, 4}) {
				add(1, () -> {
					mc.options.guiScale().set(scale);
					mc.resizeGui();
				});
				add(10, () -> { });
				shot(mc, "r6-hud-scale" + scale);
			}
			add(1, () -> {
				mc.options.guiScale().set(0);
				mc.resizeGui();
			});
		}
		if (only.contains("Round5")) {
			add(1, () -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
			List<String> worn = List.of("Wings", "Halo", "Gyroscope", "Crystals", "Crown", "Orbiting blades", "Shield shell", "Cape", "Horns",
					"Ears", "Rune circle", "Spirits", "Ribbons");
			record Piece(String piece, String setting, String value, float orbit, boolean walk) {
			}
			List<Piece> looks = List.of(new Piece("Cape", "Cape style", "Trimmed", 25, false), new Piece("Cape", "Cape style", "Trimmed", 25, true),
					new Piece("Cape", "Cape style", "Emblem", 0, false), new Piece("Horns", "Horn style", "Devil", 200, false),
					new Piece("Horns", "Horn style", "Ram", 200, false), new Piece("Horns", "Horn style", "Dragon", 110, false),
					new Piece("Ears", "Ear style", "Cat", 200, false), new Piece("Ears", "Ear style", "Fox", 200, false),
					new Piece("Ears", "Ear style", "Bunny", 200, false), new Piece("Rune circle", null, null, 20, false),
					new Piece("Spirits", null, null, 20, false), new Piece("Ribbons", null, null, 20, false));
			add(1, () -> {
				mc.player.setYRot(0);
				mc.player.setXRot(0);
				call("showcaseSet", "Freelook", "Cinematic", "false");
				call("showcaseSet", "Freelook", "Distance", "3.2");
				call("showcaseSet", "Player Aura", "Material", "Gold");
			});
			for (Piece look : looks) {
				add(1, () -> {
					for (String other : worn) {
						call("showcaseSet", "Player Aura", other, String.valueOf(other.equals(look.piece())));
					}
					if (look.setting() != null) {
						call("showcaseSet", "Player Aura", look.setting(), look.value());
					}
					mc.player.setYRot(0);
					mc.player.setXRot(0);
					call("showcaseEnable", "Player Aura", true);
					call("showcaseEnable", "Freelook", true);
				});
				add(2, () -> call("showcaseOrbit", look.orbit()));
				for (int i = 0; i < 14; i++) {
					add(1, () -> {
						if (look.walk()) {
							mc.player.setYRot(0);
							mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0.26);
						}
					});
				}
				String name = look.piece().toLowerCase().replace(" ", "-") + (look.value() == null ? "" : "-" + look.value().toLowerCase())
						+ (look.walk() ? "-walk" : "");
				shot(mc, "r5-" + name);
				add(1, () -> {
					call("showcaseEnable", "Freelook", false);
					call("showcaseEnable", "Player Aura", false);
				});
			}
			add(1, () -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			add(1, () -> {
				mc.player.setYRot(0);
				mc.player.setXRot(15);
			});
			command(mc, "summon zombie ~ ~ ~3 {NoAI:1b,Health:1f,Tags:[\"r5kill\"]}");
			add(10, () -> {
				call("showcaseSet", "Death Effects", "Darken after kill", "true");
				call("showcaseSet", "Death Effects", "Fog time", "4");
				call("showcaseEnable", "Death Effects", true);
				var z = entity(mc, "r5kill");
				if (z != null) {
					mc.gameMode.attack(mc.player, z);
				}
			});
			command(mc, "kill @e[tag=r5kill]");
			add(6, () -> { });
			shot(mc, "r5-fog-early");
			add(20, () -> { });
			shot(mc, "r5-fog-thick");
			add(35, () -> { });
			shot(mc, "r5-fog-clearing");
			add(1, () -> call("showcaseEnable", "Death Effects", false));
			add(1, () -> {
				call("showcaseSet", "HUD", "Speed graph", "true");
				call("showcaseSet", "HUD", "Enemy armor", "true");
				call("showcaseSet", "HUD", "Elytra HUD", "true");
			});
			for (int i = 0; i < 70; i++) {
				int tick = i;
				add(1, () -> {
					mc.player.setYRot(0);
					double v = tick < 20 ? 0.1 : tick < 45 ? 0.28 : 0.05;
					mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, v);
				});
			}
			shot(mc, "r5-speedgraph");
			command(mc, "item replace entity @s armor.head with minecraft:netherite_helmet[damage=120]");
			command(mc, "item replace entity @s armor.chest with minecraft:diamond_chestplate[damage=400]");
			command(mc, "item replace entity @s armor.legs with minecraft:netherite_leggings");
			command(mc, "item replace entity @s armor.feet with minecraft:diamond_boots[damage=380]");
			command(mc, "item replace entity @s weapon.mainhand with minecraft:netherite_sword[damage=900]");
			command(mc, "item replace entity @s weapon.offhand with minecraft:totem_of_undying");
			add(1, () -> call("openHudEditor", (Object) null));
			add(20, () -> { });
			shot(mc, "r5-hud-editor");
			add(1, () -> mc.gui.setScreen(null));
			for (String slot : List.of("armor.head", "armor.legs", "armor.feet", "weapon.mainhand", "weapon.offhand")) {
				command(mc, "item replace entity @s " + slot + " with minecraft:air");
			}
			command(mc, "item replace entity @s armor.chest with minecraft:elytra[damage=300]");
			command(mc, "give @s minecraft:firework_rocket 23");
			command(mc, "tp @s ~ ~60 ~");
			add(6, () -> {
				mc.player.setXRot(20);
				mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,
						net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
			});
			add(30, () -> { });
			shot(mc, "r5-elytra");
			add(1, () -> mc.player.setXRot(70));
			add(16, () -> { });
			shot(mc, "r5-elytra-pullup");
			command(mc, "item replace entity @s armor.chest with minecraft:air");
		}
		if (only.contains("Round4")) {
			add(1, () -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
			for (String emote : List.of("Take the L", "Dance", "Dab", "Headbang")) {
				add(1, () -> {
					call("showcaseSet", "Emotes", "Emote", emote);
					call("showcaseSet", "Emotes", "Stop when moving", "false");
					call("showcaseSet", "Emotes", "Loop", "true");
					call("showcaseEnable", "Emotes", true);
				});
				add(14, () -> { });
				shot(mc, "r4-emote-" + emote.toLowerCase().replace(" ", "-"));
				add(1, () -> call("showcaseEnable", "Emotes", false));
				add(2, () -> { });
			}
			add(1, () -> {
				mc.player.setYRot(-90);
				mc.player.setXRot(10);
				call("showcaseSet", "Freelook", "Cinematic", "false");
				call("showcaseSet", "Freelook", "Distance", "3.5");
				call("showcaseEnable", "Freelook", true);
				call("showcaseEnable", "Walk Style", true);
			});
			add(2, () -> call("showcaseOrbit", 100f));
			for (String walk : List.of("Natural", "Robot", "Ninja run", "Zombie", "Bouncy")) {
				add(1, () -> call("showcaseSet", "Walk Style", "Style", walk));
				for (int i = 0; i < 12; i++) {
					add(1, () -> {
						mc.player.setYRot(-90);
						mc.player.setDeltaMovement(0.21, mc.player.getDeltaMovement().y, 0);
					});
				}
				shot(mc, "r4-walk-" + walk.toLowerCase().replace(" ", "-"));
			}
			add(1, () -> {
				call("showcaseEnable", "Walk Style", false);
				call("showcaseEnable", "Freelook", false);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
			add(1, () -> call("openMenu", (Object) null));
			add(10, () -> call("showcaseSettings", "Kill Aura"));
			add(15, () -> { });
			shot(mc, "r4-settings-killaura");
			add(1, () -> call("showcaseSettings", "Chams"));
			add(15, () -> { });
			shot(mc, "r4-settings-chams");
			add(1, () -> mc.gui.setScreen(null));
			add(1, () -> call("showcaseEnable", "Motion Blur", true));
			for (int i = 0; i < 8; i++) {
				add(1, () -> mc.player.setYRot(mc.player.getYRot() + 14));
				if (i == 6) {
					add(0, () -> Screenshot.grab(mc.gameDirectory, "showcase-r4-motionblur.png", mc.gameRenderer.mainRenderTarget(), 1, c -> { }));
				}
			}
			add(1, () -> call("showcaseEnable", "Motion Blur", false));
			add(1, () -> {
				call("showcaseSet", "Freelook", "Cinematic", "true");
				call("showcaseEnable", "Freelook", true);
			});
			add(30, () -> { });
			shot(mc, "r4-cinematic");
			add(1, () -> call("showcaseEnable", "Freelook", false));
			command(mc, "summon zombie ~ ~ ~2 {NoAI:1b,Health:1f,Tags:[\"r4kill\"]}");
			add(10, () -> {
				call("showcaseSet", "Death Effects", "Darken after kill", "true");
				call("showcaseEnable", "Death Effects", true);
				var z = entity(mc, "r4kill");
				if (z != null) {
					mc.gameMode.attack(mc.player, z);
				}
			});
			command(mc, "kill @e[tag=r4kill]");
			add(12, () -> { });
			shot(mc, "r4-killdark");
			add(1, () -> call("showcaseEnable", "Death Effects", false));
		}
		if (only.contains("Round3")) {
			add(1, () -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
			add(1, () -> {
				for (String other : List.of("Halo", "Gyroscope", "Crystals")) {
					call("showcaseSet", "Player Aura", other, "false");
				}
				call("showcaseSet", "Player Aura", "Wings", "true");
				call("showcaseSet", "Player Aura", "Material", "Gold");
				call("showcaseEnable", "Player Aura", true);
			});
			for (String emote : List.of("Take the L", "The Worm", "Dance", "Floss", "Dab", "Headbang", "Zombie")) {
				add(1, () -> {
					call("showcaseSet", "Emotes", "Emote", emote);
					call("showcaseSet", "Emotes", "Stop when moving", "false");
					call("showcaseSet", "Emotes", "Loop", "true");
					call("showcaseEnable", "Emotes", true);
				});
				add(14, () -> { });
				shot(mc, "r3-emote-" + emote.toLowerCase().replace(" ", "-"));
				add(1, () -> call("showcaseEnable", "Emotes", false));
				add(2, () -> { });
			}
			add(1, () -> call("showcaseEnable", "Player Aura", false));
			add(1, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				call("openMenu", (Object) null);
			});
			add(20, () -> { });
			shot(mc, "r3-menu");
			add(1, () -> mc.gui.setScreen(null));
			add(1, () -> call("showcaseEnable", "Death Recovery", true));
			command(mc, "gamemode survival");
			command(mc, "kill @s");
			add(20, () -> {
				if (mc.player != null) {
					mc.player.respawn();
				}
			});
			add(40, () -> { });
			add(1, () -> mc.gui.setScreen(null));
			command(mc, "tp @s ~25 ~ ~12 40 0");
			add(20, () -> { });
			shot(mc, "r3-death");
			command(mc, "gamemode creative");
		}
		if (only.contains("PerfProbe")) {
			perfProbe(mc, only);
		}
		if (only.contains("PerfShots")) {
			perfShots(mc);
		}
		if (only.contains("FpsProbe")) {
			add(1, () -> {
				mc.options.enableVsync().set(false);
				mc.options.framerateLimit().set(260);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
			add(60, () -> { mc.getFramerateLimitTracker().onInputReceived(); System.out.println("TEMPLE_FPS world " + mc.getFps()); });
			add(40, () -> { mc.getFramerateLimitTracker().onInputReceived(); System.out.println("TEMPLE_FPS world " + mc.getFps()); });
			add(1, () -> call("openMenu", (Object) null));
			add(60, () -> { mc.getFramerateLimitTracker().onInputReceived(); System.out.println("TEMPLE_FPS menu " + mc.getFps() + " " + mc.gui.screen()); });
			add(40, () -> { mc.getFramerateLimitTracker().onInputReceived(); System.out.println("TEMPLE_FPS menu " + mc.getFps()); });
			for (int round = 0; round < 3; round++) {
				for (String soft : List.of("8", "0")) {
					add(1, () -> call("showcaseSet", "Glass", "Softness", soft));
					for (int k = 0; k < 3; k++) {
						add(25, () -> mc.getFramerateLimitTracker().onInputReceived());
					}
					add(10, () -> { mc.getFramerateLimitTracker().onInputReceived(); System.out.println("TEMPLE_FPS menu softness " + soft + " " + mc.getFps()); });
				}
			}
			add(1, () -> call("showcaseSet", "Glass", "Softness", "8"));
			add(1, () -> mc.gui.setScreen(null));
			for (String sky : (List<String>) call("showcaseStyles", "Skybox")) {
				add(1, () -> {
					call("showcaseSet", "Skybox", "Style", sky);
					call("showcaseEnable", "Skybox", true);
				});
				for (int k = 0; k < 3; k++) {
					add(25, () -> mc.getFramerateLimitTracker().onInputReceived());
				}
				add(60, () -> { mc.getFramerateLimitTracker().onInputReceived(); System.out.println("TEMPLE_FPS skybox " + sky + " " + mc.getFps()); });
				add(1, () -> call("showcaseEnable", "Skybox", false));
				for (int k = 0; k < 3; k++) {
					add(25, () -> mc.getFramerateLimitTracker().onInputReceived());
				}
				add(60, () -> { mc.getFramerateLimitTracker().onInputReceived(); System.out.println("TEMPLE_FPS off " + mc.getFps()); });
			}
			add(1, () -> call("showcaseEnable", "Skybox", false));
			add(60, () -> { mc.getFramerateLimitTracker().onInputReceived(); System.out.println("TEMPLE_FPS noskybox " + mc.getFps()); });
		}
		if (only.contains("Polish")) {
			command(mc, "time set 6000");
			add(1, () -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
			add(1, () -> {
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				call("showcaseEnable", "Fullbright", true);
				call("showcaseEnable", "Step", true);
			});
			add(4, () -> call("showcaseToast", "Probe blocked", "c:register"));
			add(4, () -> call("showcaseToast", "Probe blocked", "fabric:recipe_sync"));
			add(4, () -> call("showcaseToast", "Stash found", "14 chests at 1200, -340"));
			add(10, () -> { });
			shot(mc, "polish-hud");
			command(mc, "summon zombie ~-1.5 ~ ~5 {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f],equipment:{mainhand:{id:\"minecraft:diamond_sword\",count:1},head:{id:\"minecraft:leather_helmet\",count:1}}}");
			command(mc, "summon zombie ~1.8 ~ ~6 {NoAI:1b,PersistenceRequired:1b,Rotation:[160f,0f],equipment:{mainhand:{id:\"minecraft:iron_pickaxe\",count:1},head:{id:\"minecraft:leather_helmet\",count:1}}}");
			command(mc, "summon pig ~0 ~ ~8 {NoAI:1b}");
			add(10, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setXRot(8);
				mc.player.setYRot(0);
				call("showcaseSet", "ESP", "Entities", "minecraft:zombie,minecraft:pig,minecraft:player");
				call("showcaseSet", "ESP", "Mode", "Box");
				call("showcaseSet", "ESP", "Names", "true");
				call("showcaseSet", "ESP", "Distance", "true");
				call("showcaseSet", "ESP", "Health bar", "true");
				call("showcaseEnable", "ESP", true);
			});
			add(10, () -> { });
			shot(mc, "polish-esp-box");
			add(1, () -> call("showcaseSet", "ESP", "Mode", "Corners"));
			add(6, () -> { });
			shot(mc, "polish-esp-corners");
			add(1, () -> {
				call("showcaseEnable", "ESP", false);
				call("showcaseSet", "Chams", "Entities", "minecraft:zombie");
				call("showcaseSet", "Chams", "Style", "Astral");
				call("showcaseSet", "Chams", "Outline", "false");
				call("showcaseEnable", "Chams", true);
			});
			add(10, () -> { });
			shot(mc, "polish-chams-held");
			add(1, () -> call("showcaseEnable", "Chams", false));
			command(mc, "kill @e[type=zombie]");
			command(mc, "kill @e[type=pig]");
			add(1, () -> {
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				mc.player.setXRot(20);
				call("showcaseSet", "Pets", "Pet", "Dragon");
				call("showcaseSet", "Pets", "Size", "1.4");
				call("showcaseEnable", "Pets", true);
			});
			add(30, () -> { });
			for (int i = 0; i < 6; i++) {
				add(2, () -> mc.player.setPos(mc.player.getX(), mc.player.getY(), mc.player.getZ() + 0.35));
			}
			shot(mc, "polish-fox-walk");
			add(50, () -> { });
			shot(mc, "polish-fox-sit");
			add(1, () -> call("showcaseToast", "Stash found", "14 chests at 1200, -340"));
			add(8, () -> { });
			shot(mc, "polish-fox-bubble");
			add(1, () -> {
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				mc.player.setXRot(10);
			});
			add(10, () -> { });
			shot(mc, "polish-fox-front");
			add(1, () -> call("showcaseSet", "Pets", "Pet", "Dragon"));
			add(30, () -> { });
			shot(mc, "polish-dragon");
			add(1, () -> call("showcaseEnable", "Pets", false));
			add(1, () -> {
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.player.setXRot(12);
				for (String other : List.of("Halo", "Gyroscope", "Crystals")) {
					call("showcaseSet", "Player Aura", other, "false");
				}
				call("showcaseSet", "Player Aura", "Wings", "true");
				call("showcaseEnable", "Player Aura", true);
			});
			for (String material : List.of("Matte", "Light", "Gold", "Glass")) {
				add(1, () -> call("showcaseSet", "Player Aura", "Material", material));
				add(15, () -> { });
				shot(mc, "polish-wings-" + material.toLowerCase());
			}
			add(1, () -> {
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				call("showcaseSet", "Player Aura", "Material", "Matte");
			});
			add(15, () -> { });
			shot(mc, "polish-wings-front");
			add(1, () -> {
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				call("showcaseSet", "Player Aura", "Material", "Gold");
				mc.player.getAbilities().flying = true;
				mc.player.setPos(mc.player.getX(), mc.player.getY() + 2.5, mc.player.getZ());
			});
			add(40, () -> { });
			shot(mc, "polish-wings-flying");
			add(1, () -> call("showcaseEnable", "Player Aura", false));
		}
		if (only.contains("Audio")) {
			add(1, () -> {
				call("showcaseSet", "Hit Sounds", "Sound", "Bell");
				call("showcaseEnable", "Hit Sounds", true);
				call("showcasePress", "Hit Sounds", "Preview");
			});
			for (int i = 0; i < 4; i++) {
				add(1, () -> System.out.println("TEMPLE_AUDIO bell " + call("showcaseHitSound")));
			}
			add(1, () -> {
				call("showcaseSet", "Hit Sounds", "Sound", "Custom");
				call("showcaseSet", "Hit Sounds", "Sound file", System.getProperty("temple.showcase.mp3", ""));
			});
			add(60, () -> { });
			add(1, () -> call("showcasePress", "Hit Sounds", "Preview"));
			for (int i = 0; i < 4; i++) {
				add(1, () -> System.out.println("TEMPLE_AUDIO custom " + call("showcaseHitSound")));
			}
			add(1, () -> call("showcaseEnable", "Hit Sounds", false));
		}
		if (only.contains("SkyPick")) {
			command(mc, "time set 18000");
			add(1, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				call("showcaseSet", "Skybox", "Rotation speed", "0");
				call("showcaseSet", "Skybox", "Use sky colors", "false");
				call("showcaseSet", "Skybox", "Style", "Tendrils");
				call("showcaseEnable", "Skybox", true);
				mc.player.setYRot(30);
				mc.player.setXRot(-35);
			});
			add(40, () -> { });
			shot(mc, "r42x-tendrils");
			add(1, () -> mc.player.setXRot(-80));
			add(20, () -> { });
			shot(mc, "r42x-tendrils-up");
			add(1, () -> call("showcaseEnable", "Skybox", false));
			command(mc, "time set 6000");
			add(1, () -> {
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.player.setXRot(15);
				call("showcaseEnable", "Pickup Streaks", true);
				call("showcasePreview", "Pickup Streaks");
			});
			add(4, () -> { });
			shot(mc, "r42x-pickup-a");
			add(3, () -> { });
			shot(mc, "r42x-pickup-b");
			add(20, () -> call("showcaseEnable", "Pickup Streaks", false));
		}
		if (only.contains("Smooth")) {
			command(mc, "gamemode creative");
			command(mc, "time set 6000");
			add(1, () -> call("showcaseEnable", "Skybox", false));
			command(mc, "fill ~-12 ~ ~-12 ~12 ~6 ~12 minecraft:air");
			command(mc, "fill ~-12 ~-1 ~-12 ~12 ~-1 ~12 minecraft:grass_block");
			command(mc, "tp @s ~ ~ ~ 0 20");
			add(10, () -> {
				for (String part : List.of("Wings", "Halo", "Gyroscope", "Crystals", "Crown", "Orbiting blades", "Shield shell", "Horns",
						"Ears", "Rune circle", "Spirits", "Ribbons")) {
					call("showcaseSet", "Player Aura", part, "false");
				}
				call("showcaseSet", "Player Aura", "China hat", "true");
				call("showcaseEnable", "Player Aura", true);
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			});
			for (String material : List.of("Gold", "Glass", "Glow", "Matte")) {
				add(1, () -> call("showcaseSet", "Player Aura", "Material", material));
				add(15, () -> { });
				shot(mc, "r42-hat-" + material.toLowerCase());
			}
			add(1, () -> {
				mc.player.setXRot(-35);
			});
			add(10, () -> { });
			shot(mc, "r42-hat-lookup");
			add(1, () -> {
				call("showcaseEnable", "Player Aura", false);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setXRot(40);
			});
			command(mc, "item replace entity @s hotbar.0 with minecraft:diamond_sword[enchantments={sharpness:5}]");
			command(mc, "item replace entity @s armor.chest with minecraft:diamond_chestplate[enchantments={protection:4}]");
			add(10, () -> {
				mc.player.getInventory().setSelectedSlot(0);
				mc.player.setXRot(10);
			});
			shot(mc, "r42-glint-vanilla");
			add(1, () -> {
				call("showcaseSet", "Glint Color", "Color", "Custom");
				call("showcaseSet", "Glint Color", "Custom color", "#FF3B30");
				call("showcaseEnable", "Glint Color", true);
			});
			add(15, () -> { });
			shot(mc, "r42-glint-red");
			add(1, () -> call("showcaseSet", "Glint Color", "Color", "Theme"));
			add(15, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			add(10, () -> { });
			shot(mc, "r42-glint-theme-armor");
			add(1, () -> {
				call("showcaseEnable", "Glint Color", false);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setXRot(35);
			});
			add(10, () -> { });
			shot(mc, "r42-glint-off-again");
			add(1, () -> call("showcaseEnable", "Item Physics", true));
			for (String item : List.of("diamond_sword", "golden_apple", "stone", "totem_of_undying", "oak_log", "ender_pearl", "netherite_pickaxe")) {
				double vx = (item.hashCode() % 10) / 60.0;
				command(mc, String.format(java.util.Locale.ROOT, "summon item ~%.1f ~1 ~3 {Item:{id:\"minecraft:%s\",count:3},Motion:[%.2f,0.35,0.0],PickupDelay:32767}",
						(item.length() % 5) - 2.0, item, vx));
			}
			add(4, () -> { });
			shot(mc, "r42-items-air");
			add(60, () -> { });
			shot(mc, "r42-items-landed");
			add(1, () -> call("showcaseEnable", "Item Physics", false));
			add(5, () -> { });
			shot(mc, "r42-items-vanilla");
			command(mc, "kill @e[type=item]");
			add(1, () -> {
				call("showcaseSet", "Trails", "Only while moving", "true");
				call("showcaseSet", "Trails", "Length", "2");
				call("showcaseEnable", "Trails", true);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.player.setXRot(20);
				mc.options.keyUp.setDown(true);
			});
			add(25, () -> { });
			shot(mc, "r42-trail-moving");
			add(1, () -> mc.options.keyUp.setDown(false));
			add(12, () -> { });
			shot(mc, "r42-trail-stopped");
			add(1, () -> {
				call("showcaseEnable", "Trails", false);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
		}
		if (only.contains("Audio")) {
			add(1, () -> {
				call("showcaseSet", "Hit Sounds", "Sound", "Bell");
				call("showcaseEnable", "Hit Sounds", true);
				call("showcasePress", "Hit Sounds", "Preview");
			});
			for (int i = 0; i < 4; i++) {
				add(1, () -> System.out.println("TEMPLE_AUDIO bell " + call("showcaseHitSound")));
			}
			add(1, () -> {
				call("showcaseSet", "Hit Sounds", "Sound", "Custom");
				call("showcaseSet", "Hit Sounds", "Sound file", System.getProperty("temple.showcase.mp3", ""));
			});
			add(60, () -> { });
			add(1, () -> call("showcasePress", "Hit Sounds", "Preview"));
			for (int i = 0; i < 4; i++) {
				add(1, () -> System.out.println("TEMPLE_AUDIO custom " + call("showcaseHitSound")));
			}
			add(1, () -> call("showcaseEnable", "Hit Sounds", false));
		}
		if (only.contains("R42")) {
			command(mc, "gamemode creative");
			command(mc, "weather clear");
			command(mc, "fill ~-12 ~ ~-12 ~12 ~6 ~12 minecraft:air");
			command(mc, "fill ~-12 ~-1 ~-12 ~12 ~-1 ~12 minecraft:grass_block");
			command(mc, "kill @e[type=!player]");
			add(20, () -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			command(mc, "time set 18000");
			add(1, () -> {
				call("showcaseSet", "Skybox", "Rotation speed", "0");
				call("showcaseSet", "Skybox", "Tilt", "0");
				call("showcaseSet", "Skybox", "Use sky colors", "false");
				call("showcaseSet", "Skybox", "Aurora", "true");
				call("showcaseEnable", "Skybox", true);
			});
			for (String style : List.of("Tendrils", "Silk", "Void", "Plasma", "Painted", "Storm", "Nebula")) {
				add(1, () -> {
					call("showcaseSet", "Skybox", "Style", style);
					mc.player.setYRot(style.equals("Void") ? 202 : 30);
					mc.player.setXRot(style.equals("Void") ? -20 : -35);
				});
				add(40, () -> System.out.println("TEMPLE_R42 sky " + style + " fps " + mc.getFps()));
				shot(mc, "r42-sky-" + style.toLowerCase());
			}
			add(1, () -> {
				call("showcaseSet", "Skybox", "Style", "Tendrils");
				call("showcaseSet", "Skybox", "Use sky colors", "true");
			});
			add(30, () -> { });
			shot(mc, "r42-sky-tendrils-themed");
			add(1, () -> call("showcaseEnable", "Skybox", false));
			command(mc, "time set 6000");
			command(mc, "item replace entity @s hotbar.0 with minecraft:wooden_sword");
			add(20, () -> {
				mc.player.getInventory().setSelectedSlot(0);
				mc.player.setYRot(0);
				mc.player.setXRot(18);
				call("showcaseSet", "Hit Markers", "Placement", "On player");
				call("showcaseSet", "Hit Markers", "Duration", "1.2");
				call("showcaseSet", "Hit Markers", "Kill marker", "true");
				call("showcaseEnable", "Hit Markers", true);
				call("showcaseSet", "Hit Sounds", "Sound", "Pop");
				call("showcaseEnable", "Hit Sounds", true);
			});
			String[][] markers = {{"Ring", "Neon"}, {"Ring", "Glass"}, {"Ring", "Matte"}, {"Ring", "Frosted"}, {"Ring", "Metal"},
					{"Vortex", "Neon"}, {"Vortex", "Frosted"}, {"Glow", "Neon"}, {"Glow", "Glass"}, {"Box", "Neon"}};
			for (String[] marker : markers) {
				command(mc, "tp @e[type=husk] ~ -300 ~");
				command(mc, "summon husk ~ ~ ~2.6 {NoAI:1b,Rotation:[180f,0f]}");
				add(1, () -> {
					call("showcaseSet", "Hit Markers", "Marker", marker[0]);
					call("showcaseSet", "Hit Markers", "Finish", marker[1]);
				});
				add(14, () -> { });
				add(3, () -> attackNearest(mc));
				shot(mc, "r42-hit-" + marker[0].toLowerCase() + "-" + marker[1].toLowerCase() + "-a");
				add(5, () -> { });
				shot(mc, "r42-hit-" + marker[0].toLowerCase() + "-" + marker[1].toLowerCase() + "-b");
			}
			command(mc, "tp @e[type=husk] ~ -300 ~");
			command(mc, "summon husk ~ ~ ~2.6 {NoAI:1b,Rotation:[180f,0f],Health:1f}");
			add(1, () -> {
				call("showcaseSet", "Hit Markers", "Marker", "Ring");
				call("showcaseSet", "Hit Markers", "Finish", "Neon");
			});
			add(14, () -> { });
			add(3, () -> attackNearest(mc));
			add(1, () -> System.out.println("TEMPLE_R42 hitsound " + call("showcaseHitSound")));
			shot(mc, "r42-hit-ring-kill-a");
			add(6, () -> { });
			shot(mc, "r42-hit-ring-kill-b");
			add(1, () -> call("showcaseEnable", "Hit Markers", false));
			command(mc, "kill @e[type=husk]");
			add(1, () -> {
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				mc.player.setXRot(20);
				call("showcaseEnable", "Pickup Streaks", true);
			});
			for (int i = 0; i < 5; i++) {
				int k = i;
				command(mc, "summon item ~" + (k - 2) * 0.3 + " ~1.2 ~0.4 {Item:{id:\"minecraft:diamond\",count:1},PickupDelay:8,Motion:[0.0,0.2,0.0]}");
			}
			command(mc, "summon experience_orb ~0.6 ~0.6 ~0.3 {Value:3}");
			command(mc, "summon experience_orb ~-0.6 ~0.6 ~0.3 {Value:3}");
			for (int i = 0; i < 5; i++) {
				shot(mc, "r42-pickup-" + i);
				add(2, () -> { });
			}
			add(20, () -> call("showcaseEnable", "Pickup Streaks", false));
			for (String style : List.of("Vapour", "Streamers", "Embers")) {
				add(1, () -> {
					call("showcaseSet", "Contrails", "Style", style);
					call("showcaseEnable", "Contrails", true);
					call("showcasePreview", "Contrails");
				});
				add(40, () -> { });
				shot(mc, "r42-contrails-" + style.toLowerCase());
			}
			add(1, () -> call("showcaseEnable", "Contrails", false));
			for (String style : List.of("Tube", "Beads", "Sparkles", "Wisps")) {
				for (String material : List.of("Light", "Glow", "Glass")) {
					add(1, () -> {
						call("showcaseSet", "Trails", "Style", style);
						call("showcaseSet", "Trails", "Material", material);
						call("showcaseEnable", "Trails", true);
						call("showcasePreview", "Trails");
					});
					add(40, () -> { });
					shot(mc, "r42-trails-" + style.toLowerCase() + "-" + material.toLowerCase());
				}
			}
			add(1, () -> call("showcaseEnable", "Trails", false));
			for (String style : List.of("Comet", "Tube", "Spiral")) {
				add(1, () -> {
					call("showcaseSet", "Projectile Trails", "Style", style);
					call("showcaseEnable", "Projectile Trails", true);
					call("showcasePreview", "Projectile Trails");
				});
				add(28, () -> { });
				shot(mc, "r42-projectile-" + style.toLowerCase());
			}
			add(1, () -> call("showcaseEnable", "Projectile Trails", false));
			add(1, () -> {
				call("showcaseSet", "Fake Lag", "Only near players", "false");
				call("showcaseSet", "Fake Lag", "Lag", "1000");
				call("showcaseSet", "Fake Lag", "Jitter", "0");
				call("showcaseEnable", "Fake Lag", true);
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				mc.options.keyUp.setDown(true);
			});
			add(12, () -> mc.options.keyUp.setDown(false));
			add(2, () -> { });
			shot(mc, "r42-fakelag-ghost");
			add(30, () -> {
				call("showcaseEnable", "Fake Lag", false);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
			add(1, () -> {
				call("showcaseSet", "Hit Sounds", "Sound", "Custom");
				call("showcaseSet", "Hit Sounds", "Sound file", System.getProperty("temple.showcase.mp3", ""));
			});
			add(80, () -> System.out.println("TEMPLE_R42 hitsound " + call("showcaseHitSound")));
			add(1, () -> call("showcasePress", "Hit Sounds", "Trim"));
			add(20, () -> System.out.println("TEMPLE_R42 editor " + (mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getName())));
			shot(mc, "r42-hitsound-editor");
			add(1, () -> mc.gui.setScreen(null));
			command(mc, "summon husk ~ ~ ~2.6 {NoAI:1b,Rotation:[180f,0f]}");
			add(14, () -> attackNearest(mc));
			add(3, () -> System.out.println("TEMPLE_R42 custom hit " + call("showcaseHitSound")));
			command(mc, "kill @e[type=husk]");
			add(1, () -> {
				call("showcaseEnable", "Hit Sounds", false);
				call("showcaseSet", "Hit Sounds", "Sound", "Pop");
			});
		}
		if (only.contains("R41")) {
			command(mc, "gamemode creative");
			command(mc, "time set 6000");
			add(1, () -> call("showcaseEnable", "Skybox", false));
			command(mc, "fill ~-12 ~ ~-12 ~12 ~6 ~12 minecraft:air");
			command(mc, "fill ~-12 ~-1 ~-12 ~12 ~-1 ~12 minecraft:grass_block");
			command(mc, "fill ~-2 ~-1 ~6 ~2 ~-1 ~8 minecraft:stone");
			command(mc, "tp @s ~ ~ ~ 0 5");
			add(20, () -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			add(1, () -> {
				airOnly("Light lines");
				call("showcaseSet", "Air Effects", "Density", "260");
				call("showcaseEnable", "Air Effects", true);
			});
			add(60, () -> { });
			shot(mc, "r41-airfx-lines");
			add(1, () -> airOnly("Cubes"));
			add(60, () -> { });
			shot(mc, "r41-airfx-cubes");
			add(4, () -> { });
			shot(mc, "r41-airfx-cubes2");
			add(1, () -> call("showcaseSet", "Air Effects", "Cube material", "Glass"));
			add(20, () -> { });
			shot(mc, "r41-airfx-cubes-glass");
			add(1, () -> call("showcaseEnable", "Air Effects", false));
			command(mc, "item replace entity @s hotbar.0 with minecraft:wooden_sword");
			add(20, () -> {
				mc.player.getInventory().setSelectedSlot(0);
				mc.player.setXRot(18);
				call("showcaseSet", "Hit Markers", "Duration", "2.5");
				call("showcaseSet", "Hit Markers", "Kill marker", "false");
				call("showcaseEnable", "Hit Markers", true);
			});
			for (String style : List.of("Box", "Vortex", "Glow")) {
				for (String finish : List.of("Neon", "Glass", "Matte", "Frosted")) {
					command(mc, "tp @e[type=husk] ~ -300 ~");
					command(mc, "summon husk ~ ~ ~2.6 {NoAI:1b,Rotation:[180f,0f]}");
					add(1, () -> {
						call("showcaseSet", "Hit Markers", "Marker", style);
						call("showcaseSet", "Hit Markers", "Finish", finish);
					});
					add(14, () -> {
						var target = mc.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
								mc.player.getBoundingBox().inflate(6), e -> e != mc.player && e.isAlive()).stream().findFirst().orElse(null);
						if (target != null) {
							mc.gameMode.attack(mc.player, target);
							mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
						}
					});
					add(4, () -> { });
					shot(mc, "r41-hit-" + style.toLowerCase() + "-" + finish.toLowerCase());
				}
			}
			add(1, () -> call("showcaseEnable", "Hit Markers", false));
			command(mc, "kill @e[type=husk]");
			add(1, () -> {
				mc.player.setXRot(55);
				call("showcaseSet", "Jump Circles", "Style", "Block highlight");
				call("showcaseSet", "Jump Circles", "Duration", "1.6");
				call("showcaseEnable", "Jump Circles", true);
			});
			add(10, () -> mc.options.keyJump.setDown(true));
			add(3, () -> mc.options.keyJump.setDown(false));
			add(6, () -> { });
			shot(mc, "r41-jump-highlight-a");
			add(8, () -> { });
			shot(mc, "r41-jump-highlight-b");
			add(30, () -> call("showcaseEnable", "Jump Circles", false));
			command(mc, "item replace entity @s hotbar.0 with minecraft:diamond_pickaxe");
			add(10, () -> {
				mc.player.setXRot(10);
				call("showcaseSet", "Hand Chams", "Hand style", "Galaxy");
				call("showcaseSet", "Hand Chams", "Weapon style", "Chrome");
				call("showcaseEnable", "Hand Chams", true);
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			});
			add(20, () -> { });
			shot(mc, "r41-handchams-front");
			add(1, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			add(10, () -> { });
			shot(mc, "r41-handchams-back");
			add(1, () -> {
				call("showcaseEnable", "Hand Chams", false);
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setXRot(5);
			});
			add(1, () -> {
				call("showcaseSet", "Music", "Hide when paused", "false");
				call("showcaseSet", "Music", "Lyric style", "Top bar");
				System.setProperty("temple.music.fake", "Calcium|Ecco2k|E|225|40");
				call("showcaseEnable", "Music", true);
			});
			add(160, () -> { });
			shot(mc, "r41-music-topbar");
			add(60, () -> { });
			shot(mc, "r41-music-topbar2");
			add(1, () -> call("showcaseSet", "Music", "Lyric style", "Crosshair"));
			add(40, () -> { });
			shot(mc, "r41-music-crosshair");
			add(1, () -> call("showcaseSet", "Music", "Lyric style", "In world"));
			add(120, () -> { });
			shot(mc, "r41-music-world");
			add(80, () -> { });
			shot(mc, "r41-music-world2");
			add(1, () -> call("showcaseEnable", "Music", false));
			add(1, () -> call("showcasePopup", "intro"));
			add(40, () -> { });
			shot(mc, "r41-suggest-intro");
			add(1, () -> mc.gui.setScreen(null));
			add(5, () -> mc.disconnectFromWorld(net.minecraft.network.chat.Component.literal("Showcase")));
			add(120, () -> {
				System.out.println("TEMPLE_TITLE after world: " + (mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getName()));
				if (!(mc.gui.screen() instanceof TitleScreen)) {
					mc.gui.setScreen(new TitleScreen());
				}
			});
			add(40, () -> call("showcaseTitle", "worlds"));
			add(60, () -> call("showcaseTitle", "select"));
			add(10, () -> { });
			add(2, () -> Screenshot.grab(mc.gameDirectory, "showcase-r41-title-worlds.png", mc.gameRenderer.mainRenderTarget(), 1, c -> { }));
		}
		if (only.contains("HandLook")) {
			command(mc, "time set 13000");
			command(mc, "item replace entity @s hotbar.0 with minecraft:diamond_sword");
			add(10, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.getInventory().setSelectedSlot(0);
				mc.player.setXRot(10);
				call("showcaseSet", "Hand Chams", "Items", "Weapons");
				call("showcaseEnable", "Hand Chams", true);
			});
			List<String> looks = List.of("Flat", "Tinted", "Gradient", "Rainbow", "Glass", "Chrome", "Liquid", "Galaxy", "Void",
					"Plasma", "Hologram", "Fire", "Electric", "Crystal", "Matrix", "Wireframe", "Ghost", "Toon");
			for (String look : looks) {
				add(1, () -> {
					call("showcaseSet", "Hand Chams", "Hand style", look);
					call("showcaseSet", "Hand Chams", "Weapon style", look);
				});
				add(6, () -> { });
				shot(mc, "handchams-" + look.toLowerCase());
			}
			add(1, () -> {
				call("showcaseSet", "Hand Chams", "Hand blend", "Overlay");
				call("showcaseSet", "Hand Chams", "Weapon blend", "Overlay");
				call("showcaseSet", "Hand Chams", "Opacity", "45");
				call("showcaseSet", "Hand Chams", "Hand style", "Chrome");
				call("showcaseSet", "Hand Chams", "Weapon style", "Fire");
			});
			add(6, () -> { });
			shot(mc, "handchams-overlay");
			add(1, () -> {
				mc.player.getInventory().setSelectedSlot(1);
				call("showcaseSet", "Hand Chams", "Hand blend", "Replace");
				call("showcaseSet", "Hand Chams", "Opacity", "100");
			});
			for (String look : List.of("Galaxy", "Chrome", "Crystal", "Fire", "Wireframe", "Glass")) {
				add(1, () -> call("showcaseSet", "Hand Chams", "Hand style", look));
				add(8, () -> { });
				shot(mc, "handchams-arm-" + look.toLowerCase());
			}
			add(1, () -> call("showcaseEnable", "Hand Chams", false));
		}
		if (only.contains("Energy")) {
			command(mc, "time set 18000");
			command(mc, "summon zombie ~1.8 ~ ~2.5 {NoAI:1b,Rotation:[200f,0f]}");
			add(10, () -> { });
			for (String style : List.of("Astral", "Void", "Plasma", "Hologram")) {
				add(1, () -> {
					call("showcaseSet", "Chams", "Self", "true");
					call("showcaseSet", "Chams", "Entities", "minecraft:player,minecraft:zombie");
					call("showcaseSet", "Chams", "Style", style);
					call("showcaseSet", "Chams", "Outline", "false");
					call("showcaseEnable", "Chams", true);
				});
				add(20, () -> { });
				shot(mc, "energy-" + style.toLowerCase());
			}
			add(1, () -> call("showcaseEnable", "Chams", false));
		}
		if (only.contains("Visuals")) {
			command(mc, "fill ~-16 ~ ~2 ~16 ~ ~24 minecraft:short_grass replace air");
			command(mc, "place feature minecraft:oak ~5 ~ ~9");
			command(mc, "place feature minecraft:birch ~-5 ~ ~12");
			command(mc, "place feature minecraft:fancy_oak ~1 ~ ~18");
			command(mc, "setblock ~2 ~ ~4 minecraft:poppy");
			command(mc, "setblock ~-2 ~ ~5 minecraft:sunflower");
			command(mc, "setblock ~-2 ~1 ~5 minecraft:sunflower[half=upper]");
			command(mc, "fill ~-8 ~5 ~3 ~-3 ~5 ~8 minecraft:oak_planks");
			add(1, () -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
				mc.player.setXRot(4);
				mc.player.setYRot(0);
				call("showcaseEnable", "Waving Foliage", true);
				call("showcaseSet", "Waving Foliage", "Strength", "2.5");
			});
			add(80, () -> { });
			shot(mc, "visuals-foliage-a");
			add(6, () -> { });
			shot(mc, "visuals-foliage-b");
			add(1, () -> {
				airOnly("Snow");
				call("showcaseSet", "Air Effects", "Color", "Natural");
				call("showcaseSet", "Air Effects", "Snowfall", "60");
				call("showcaseEnable", "Air Effects", true);
			});
			add(40, () -> { });
			shot(mc, "visuals-snow");
			command(mc, "time set 14000");
			add(40, () -> { });
			shot(mc, "visuals-snow-dusk");
			add(1, () -> {
				call("showcaseEnable", "Air Effects", false);
				call("showcaseEnable", "Waving Foliage", false);
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			});
		}
		if (only.contains("TabOut")) {
			add(40, () -> { });
			shot(mc, "tabout-before");
			add(1, () -> org.lwjgl.glfw.GLFW.glfwIconifyWindow(mc.getWindow().handle()));
			add(60, () -> {
				org.lwjgl.glfw.GLFW.glfwRestoreWindow(mc.getWindow().handle());
				org.lwjgl.glfw.GLFW.glfwFocusWindow(mc.getWindow().handle());
			});
			add(60, () -> System.out.println("TEMPLE_TABOUT window " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight()
					+ " target " + mc.gameRenderer.mainRenderTarget().width + "x" + mc.gameRenderer.mainRenderTarget().height
					+ " minimized=" + mc.getWindow().isMinimized() + " iconified=" + mc.getWindow().isIconified()
					+ " screen=" + mc.gui.screen()));
			add(1, () -> mc.gui.setScreen(null));
			add(20, () -> { });
			shot(mc, "tabout-after");
			add(5, () -> desktopShot(mc, "tabout-desktop"));
		}
		if (only.contains("SimpleMenu")) {
			add(1, () -> {
				call("showcaseSet", "Menu", "Click GUI", "Simple");
				call("showcaseSet", "Kill Aura", "Legit", "true");
				call("showcaseEnable", "Kill Aura", true);
				call("showcaseEnable", "No Fall", true);
				call("openMenu", (Object) null);
			});
			add(20, () -> { });
			shot(mc, "simple-menu-list");
			add(1, () -> call("showcaseSettings", "Kill Aura"));
			add(20, () -> { });
			shot(mc, "simple-menu");
			add(1, () -> {
				mc.gui.setScreen(null);
				call("showcaseEnable", "Kill Aura", false);
				call("showcaseEnable", "No Fall", false);
				call("showcaseSet", "Kill Aura", "Legit", "false");
				call("showcaseSet", "Menu", "Click GUI", "Glass");
			});
			add(10, () -> { });
		}
		if (only.contains("NoFall")) {
			float[] health = new float[1];
			command(mc, "gamerule fall_damage true");
			command(mc, "gamerule fallDamage true");
			command(mc, "gamemode survival");
			command(mc, "effect clear @s");
			command(mc, "tp @s ~ ~12 ~");
			add(60, () -> { });
			add(1, () -> System.out.println("TEMPLE_NOFALL control health " + mc.player.getHealth()));
			command(mc, "effect give @s instant_health 1 5 true");
			add(20, () -> {
				call("showcaseSet", "No Fall", "Mode", "Packet");
				call("showcaseEnable", "No Fall", true);
				health[0] = mc.player.getHealth();
			});
			command(mc, "tp @s ~ ~30 ~");
			add(80, () -> { });
			add(1, () -> System.out.println("TEMPLE_NOFALL packet health " + health[0] + " -> " + mc.player.getHealth()));
			command(mc, "item replace entity @s hotbar.0 with minecraft:iron_sword");
			command(mc, "item replace entity @s hotbar.3 with minecraft:water_bucket");
			add(5, () -> {
				mc.player.getInventory().setSelectedSlot(0);
				call("showcaseSet", "No Fall", "Mode", "Water bucket");
				health[0] = mc.player.getHealth();
			});
			command(mc, "tp @s ~ ~24 ~ ~ 0");
			add(26, () -> { });
			shot(mc, "nofall-bucket-fall");
			add(70, () -> { });
			add(1, () -> System.out.println("TEMPLE_NOFALL bucket health " + health[0] + " -> " + mc.player.getHealth()
					+ " hotbar3 " + mc.player.getInventory().getItem(3).getItem() + " selected " + mc.player.getInventory().getSelectedSlot()
					+ " pitch " + mc.player.getXRot()));
			add(1, () -> call("showcaseEnable", "No Fall", false));
			command(mc, "effect give @s instant_health 1 5 true");
			command(mc, "gamemode creative");
			add(20, () -> { });
		}
		if (only.contains("TPAura")) {
			command(mc, "gamemode survival");
			command(mc, "effect give @s resistance 600 255 true");
			command(mc, "item replace entity @s hotbar.0 with minecraft:iron_sword");
			add(1, () -> mc.player.getInventory().setSelectedSlot(0));
			command(mc, "fill ~5 ~ ~-3 ~5 ~2 ~3 minecraft:stone");
			for (String run : List.of("false 2.4 off CPS", "true 2.4 off Cooldown", "true 3 on CPS", "false 3 on CPS")) {
				String[] cfg = run.split(" ");
				String quick = cfg[0];
				command(mc, "summon zombie ~11 ~ ~ {NoAI:1b,PersistenceRequired:1b,Tags:[\"tptarget\"],Health:300f,"
						+ "attributes:[{id:\"minecraft:max_health\",base:300}]}");
				float[] start = new float[1];
				int[] hurts = new int[1];
				int[] lastHurt = new int[1];
				net.minecraft.world.phys.Vec3[] home = new net.minecraft.world.phys.Vec3[1];
				add(20, () -> {
					home[0] = mc.player.position();
					start[0] = zombieHealth(mc, "tptarget");
					call("showcaseSet", "TP Aura", "Hostiles", "true");
					call("showcaseSet", "TP Aura", "Travel", "Return");
					call("showcaseSet", "TP Aura", "Quick hit", quick);
					call("showcaseSet", "TP Aura", "Distance", cfg[1]);
					call("showcaseSet", "TP Aura", "Timing", cfg[3]);
					call("showcaseSet", "TP Aura", "CPS", "16");
					call("showcaseSet", "Criticals", "Mode", "Packet");
					call("showcaseEnable", "Criticals", cfg[2].equals("on"));
					call("showcaseEnable", "Kill Aura", true);
					call("showcaseEnable", "TP Aura", true);
					System.out.println("TEMPLE_TPAURA exclusive: kill aura on = " + call("showcaseEnabled", "Kill Aura"));
				});
				for (int t = 0; t < 100; t++) {
					add(1, () -> {
						var z = entity(mc, "tptarget");
						int h = z != null ? z.hurtTime : 0;
						if (h > lastHurt[0]) {
							hurts[0]++;
						}
						lastHurt[0] = h;
					});
				}
				add(1, () -> System.out.println("TEMPLE_TPAURA " + run + " zombie health " + start[0] + " -> " + zombieHealth(mc, "tptarget")
						+ ", hits landed " + hurts[0] + ", moved " + mc.player.position().distanceTo(home[0])));
				add(1, () -> {
					call("showcaseEnable", "TP Aura", false);
					call("showcaseEnable", "Criticals", false);
				});
				command(mc, "kill @e[tag=tptarget]");
			}
			command(mc, "fill ~5 ~ ~-3 ~5 ~2 ~3 minecraft:air");
			command(mc, "gamemode creative");
			add(10, () -> { });
		}
		if (only.contains("AuraCps")) {
			command(mc, "gamemode survival");
			command(mc, "effect give @s resistance 600 255 true");
			command(mc, "item replace entity @s hotbar.0 with minecraft:iron_sword");
			for (String run : List.of("off still", "on still", "on jump")) {
				String[] cfg = run.split(" ");
				command(mc, "summon zombie ~2.5 ~ ~ {NoAI:1b,PersistenceRequired:1b,Tags:[\"cpstarget\"],Health:1000f,"
						+ "attributes:[{id:\"minecraft:max_health\",base:1000}]}");
				float[] start = new float[2];
				int[] hurts = new int[1];
				int[] lastHurt = new int[1];
				int[] swings = new int[1];
				add(20, () -> {
					mc.player.getInventory().setSelectedSlot(0);
					start[0] = zombieHealth(mc, "cpstarget");
					start[1] = start[0];
					call("showcaseSet", "Kill Aura", "Hostiles", "true");
					call("showcaseSet", "Kill Aura", "Timing", "CPS");
					call("showcaseSet", "Kill Aura", "Min CPS", "16");
					call("showcaseSet", "Kill Aura", "Max CPS", "16");
					call("showcaseSet", "Kill Aura", "Wait for crit", "true");
					call("showcaseSet", "Criticals", "Mode", "Packet");
					call("showcaseSet", "Criticals", "Only Kill Aura", "false");
					call("showcaseEnable", "Criticals", cfg[0].equals("on"));
					call("showcaseEnable", "TP Aura", true);
					call("showcaseEnable", "Kill Aura", true);
					System.out.println("TEMPLE_AURACPS exclusive: tp aura on = " + call("showcaseEnabled", "TP Aura"));
				});
				for (int t = 0; t < 100; t++) {
					add(1, () -> {
						if (cfg[1].equals("jump") && mc.player.onGround()) {
							mc.player.jumpFromGround();
						}
						if (mc.player.swinging && mc.player.swingTime == 0) {
							swings[0]++;
						}
						var z = entity(mc, "cpstarget");
						int h = z != null ? z.hurtTime : 0;
						if (z != null && z.getHealth() != start[1]) {
							System.out.printf("TEMPLE_AURACPS_TICK %d health %.2f (%.2f) hurt %d charge %.2f ground %s%n", mc.player.tickCount,
									z.getHealth(), z.getHealth() - start[1], h, mc.player.getAttackStrengthScale(0), mc.player.onGround());
							start[1] = z.getHealth();
						}
						if (h > lastHurt[0]) {
							hurts[0]++;
						}
						lastHurt[0] = h;
					});
				}
				add(1, () -> System.out.println("TEMPLE_AURACPS " + run + " damage " + (start[0] - zombieHealth(mc, "cpstarget"))
						+ ", hits landed " + hurts[0] + ", swings " + swings[0]));
				add(1, () -> {
					call("showcaseEnable", "Kill Aura", false);
					call("showcaseEnable", "Criticals", false);
				});
				command(mc, "kill @e[tag=cpstarget]");
				add(10, () -> { });
			}
			command(mc, "gamemode creative");
			add(10, () -> { });
		}
		if (only.contains("Strafe")) {
			command(mc, "gamemode survival");
			command(mc, "effect give @s resistance 600 255 true");
			command(mc, "item replace entity @s hotbar.0 with minecraft:iron_sword");
			command(mc, "summon zombie ~3 ~ ~ {NoAI:1b,PersistenceRequired:1b,Tags:[\"strafetarget\"],Health:1000f,"
					+ "attributes:[{id:\"minecraft:max_health\",base:1000}]}");
			add(20, () -> {
				mc.player.getInventory().setSelectedSlot(0);
				call("showcaseSet", "Kill Aura", "Hostiles", "true");
				call("showcaseSet", "Kill Aura", "Range", "6");
				call("showcaseSet", "Speed", "Profile", "Sprint hop");
				call("showcaseEnable", "Kill Aura", true);
				call("showcaseEnable", "Target Strafe", true);
				call("showcaseEnable", "Speed", true);
			});
			for (int i = 0; i < 8; i++) {
				add(10, () -> {
					var zombie = entity(mc, "strafetarget");
					if (zombie != null) {
						double dx = mc.player.getX() - zombie.getX();
						double dz = mc.player.getZ() - zombie.getZ();
						System.out.printf("TEMPLE_STRAFE angle %.0f distance %.2f ground %s%n",
								Math.toDegrees(Math.atan2(dz, dx)), Math.sqrt(dx * dx + dz * dz), mc.player.onGround());
					}
				});
			}
			add(1, () -> {
				call("showcaseEnable", "Speed", false);
				call("showcaseEnable", "Target Strafe", false);
				call("showcaseEnable", "Kill Aura", false);
			});
			command(mc, "kill @e[tag=strafetarget]");
			command(mc, "gamemode creative");
			add(10, () -> { });
		}
		if (only.contains("BowAim")) {
			command(mc, "gamemode survival");
			command(mc, "effect give @s resistance 600 255 true");
			command(mc, "item replace entity @s hotbar.0 with minecraft:bow");
			command(mc, "item replace entity @s hotbar.1 with minecraft:arrow 64");
			command(mc, "fill ~-2 ~-1 ~-2 ~27 ~-1 ~2 minecraft:stone");
			command(mc, "fill ~-2 ~ ~-2 ~27 ~6 ~2 minecraft:air");
			command(mc, "summon zombie ~25 ~ ~ {NoAI:1b,PersistenceRequired:1b,Tags:[\"bowtarget\"],Health:300f,"
					+ "attributes:[{id:\"minecraft:max_health\",base:300}]}");
			command(mc, "tp @s ~ ~ ~ 0 0");
			float[] start = new float[1];
			add(20, () -> {
				mc.player.getInventory().setSelectedSlot(0);
				start[0] = zombieHealth(mc, "bowtarget");
				call("showcaseSet", "Bow Aimbot", "Mode", "Silent");
				call("showcaseSet", "Bow Aimbot", "FOV", "360");
				call("showcaseEnable", "Bow Aimbot", true);
			});
			for (int shot = 0; shot < 3; shot++) {
				add(1, () -> mc.options.keyUse.setDown(true));
				add(25, () -> mc.options.keyUse.setDown(false));
				add(40, () -> System.out.println("TEMPLE_BOWAIM health " + zombieHealth(mc, "bowtarget") + " yaw " + mc.player.getYRot()));
			}
			add(1, () -> {
				System.out.println("TEMPLE_BOWAIM zombie health " + start[0] + " -> " + zombieHealth(mc, "bowtarget"));
				call("showcaseEnable", "Bow Aimbot", false);
			});
			command(mc, "kill @e[tag=bowtarget]");
			command(mc, "gamemode creative");
			add(10, () -> { });
		}
		if (only.contains("WatermarkFps")) {
			add(20, () -> call("showcaseSet", "HUD", "FPS", "false"));
			add(10, () -> { });
			shot(mc, "watermark-fps-off");
			add(1, () -> call("showcaseSet", "HUD", "FPS", "true"));
			add(10, () -> { });
			shot(mc, "watermark-fps-on");
		}
		if (only.contains("NoSlowBlocks")) {
			command(mc, "gamemode survival");
			command(mc, "effect give @s resistance 600 255 true");
			command(mc, "effect give @s fire_resistance 600 0 true");
			for (String block : List.of("air", "water", "lava", "cobweb")) {
				for (String on : List.of("false", "true")) {
					command(mc, "fill ~-1 ~ ~1 ~1 ~1 ~30 minecraft:" + block);
					command(mc, "tp @s ~ ~ ~ 0 0");
					double[] start = new double[1];
					add(20, () -> {
						call("showcaseEnable", "No Slow", Boolean.parseBoolean(on));
						start[0] = mc.player.getZ();
						mc.options.keyUp.setDown(true);
					});
					add(40, () -> {
						mc.options.keyUp.setDown(false);
						System.out.printf("TEMPLE_NOSLOW %s noSlow %s speed %.3f b/t%n", block, on, (mc.player.getZ() - start[0]) / 40);
						call("showcaseEnable", "No Slow", false);
					});
					command(mc, "fill ~-1 ~ ~-30 ~1 ~2 ~30 minecraft:air");
					add(10, () -> mc.player.setPos(mc.player.getX(), mc.player.getY(), start[0]));
					add(10, () -> { });
				}
			}
			command(mc, "gamemode creative");
			add(10, () -> { });
		}
		if (only.contains("MusicMenus")) {
			add(1, () -> call("showcaseEnable", "Music", true));
			add(80, () -> mc.gui.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true)));
			add(30, () -> System.out.println("TEMPLE_MUSICMENU screen " + mc.gui.screen()));
			shot(mc, "music-pause");
			add(1, () -> mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
			add(30, () -> { });
			shot(mc, "music-inventory");
			add(1, () -> {
				mc.gui.setScreen(null);
				call("showcaseEnable", "Music", false);
			});
			add(10, () -> { });
		}
		if (only.contains("LagAura")) {
			command(mc, "gamemode survival");
			command(mc, "effect give @s resistance 600 255 true");
			command(mc, "item replace entity @s hotbar.0 with minecraft:iron_sword");
			for (String lag : List.of("off", "400", "2000")) {
				command(mc, "summon zombie ~3 ~ ~ {NoAI:1b,PersistenceRequired:1b,Tags:[\"lagtarget\"],Health:1000f,"
						+ "attributes:[{id:\"minecraft:max_health\",base:1000}]}");
				float[] health = new float[1];
				int[] setbacks = new int[2];
				float[] hp = {-1};
				StringBuilder hits = new StringBuilder();
				net.minecraft.world.phys.Vec3[] last = new net.minecraft.world.phys.Vec3[1];
				add(20, () -> {
					mc.player.getInventory().setSelectedSlot(0);
					health[0] = zombieHealth(mc, "lagtarget");
					call("showcaseSet", "Kill Aura", "Hostiles", "true");
					call("showcaseSet", "Kill Aura", "Legit", "false");
					call("showcaseSet", "Target Strafe", "Respect WASD", "false");
					call("showcaseSet", "Speed", "Profile", "Sprint hop");
					call("showcaseSet", "Fake Lag", "Only near players", "false");
					if (!lag.equals("off")) {
						call("showcaseSet", "Fake Lag", "Lag", lag);
						call("showcaseEnable", "Fake Lag", true);
					}
					call("showcaseEnable", "Kill Aura", true);
					call("showcaseEnable", "Target Strafe", true);
					call("showcaseEnable", "Speed", true);
				});
				for (int i = 0; i < 200; i++) {
					add(1, () -> {
						var now = mc.player.position();
						if (last[0] != null && now.distanceTo(last[0]) > 3) {
							setbacks[0]++;
						}
						last[0] = now;
						float h = zombieHealth(mc, "lagtarget");
						if (hp[0] >= 0 && h < hp[0]) {
							hits.append(setbacks[1]).append(' ');
						}
						hp[0] = h;
						setbacks[1]++;
					});
				}
				add(1, () -> {
					System.out.printf("TEMPLE_LAGAURA lag %s damage %.0f setbacks %d hit ticks %s%n", lag,
							health[0] - zombieHealth(mc, "lagtarget"), setbacks[0], hits);
					call("showcaseEnable", "Fake Lag", false);
					call("showcaseEnable", "Speed", false);
					call("showcaseEnable", "Target Strafe", false);
					call("showcaseEnable", "Kill Aura", false);
				});
				command(mc, "kill @e[tag=lagtarget]");
				add(20, () -> { });
			}
			command(mc, "gamemode creative");
			add(10, () -> { });
		}
		if (only.contains("HugAura")) {
			command(mc, "gamemode survival");
			command(mc, "effect give @s resistance 600 255 true");
			command(mc, "item replace entity @s hotbar.0 with minecraft:iron_sword");
			for (String legitMode : List.of("false", "true")) {
				command(mc, "summon zombie ~2 ~ ~ {NoAI:1b,PersistenceRequired:1b,Tags:[\"hugtarget\"],Health:1000f,"
						+ "attributes:[{id:\"minecraft:max_health\",base:1000}]}");
				command(mc, "tp @s ~ ~ ~ facing entity @e[tag=hugtarget,limit=1] eyes");
				int[] count = new int[4];
				add(20, () -> {
					mc.player.getInventory().setSelectedSlot(0);
					call("showcaseSet", "Kill Aura", "Hostiles", "true");
					call("showcaseSet", "Kill Aura", "Legit", legitMode);
					call("showcaseSet", "Kill Aura", "Rotation", "Silent");
					call("showcaseSet", "Target Strafe", "Style", "Hug");
					call("showcaseSet", "Target Strafe", "Respect WASD", "false");
					call("showcaseSet", "Speed", "Profile", "Sprint hop");
					call("showcaseEnable", "Kill Aura", true);
					call("showcaseEnable", "Target Strafe", true);
					call("showcaseEnable", "Speed", true);
				});
				add(20, () -> { });
				for (int i = 0; i < 200; i++) {
					add(1, () -> {
						var zombie = entity(mc, "hugtarget");
						if (zombie == null) {
							return;
						}
						float yaw = mc.player.getYRot();
						float pitch = mc.player.getXRot();
						if (legitMode.equals("false")) {
							try {
								Class<?> r = Class.forName("nz.temple.client.util.Rotations");
								yaw = (float) r.getMethod("serverYaw", net.minecraft.client.player.LocalPlayer.class).invoke(null, mc.player);
								pitch = (float) r.getMethod("serverPitch", net.minecraft.client.player.LocalPlayer.class).invoke(null, mc.player);
							} catch (ReflectiveOperationException e) {
								throw new RuntimeException(e);
							}
						}
						var eye = mc.player.getEyePosition();
						var box = zombie.getBoundingBox();
						boolean on = box.contains(eye) || box.clip(eye, eye.add(net.minecraft.world.phys.Vec3.directionFromRotation(pitch, yaw)
								.scale(4))).isPresent();
						count[0] += on ? 1 : 0;
						count[1]++;
						count[2] += zombie.hurtTime == 10 ? 1 : 0;
						count[3] += Math.sqrt(box.distanceToSqr(eye)) < 1.0 ? 1 : 0;
					});
				}
				add(1, () -> {
					System.out.printf("TEMPLE_HUGAURA legit %s onHitbox %d/%d hits %d withinOneMetre %d health %.0f%n", legitMode,
							count[0], count[1], count[2], count[3], zombieHealth(mc, "hugtarget"));
					call("showcaseEnable", "Speed", false);
					call("showcaseEnable", "Target Strafe", false);
					call("showcaseEnable", "Kill Aura", false);
				});
				command(mc, "kill @e[tag=hugtarget]");
				add(10, () -> { });
			}
			command(mc, "gamemode creative");
			add(10, () -> { });
		}
		if (only.contains("LegitOrbit")) {
			command(mc, "gamemode survival");
			command(mc, "effect give @s resistance 600 255 true");
			command(mc, "effect give @s saturation 600 10 true");
			command(mc, "item replace entity @s hotbar.0 with minecraft:iron_sword");
			command(mc, "fill ~-3 ~ ~4 ~-2 ~ ~6 minecraft:stone");
			command(mc, "fill ~2 ~ ~1 ~3 ~ ~2 minecraft:stone");
			command(mc, "summon zombie ~0.5 ~ ~3 {NoAI:1b,PersistenceRequired:1b,Tags:[\"legitstrafe\"],Health:1000f,"
					+ "attributes:[{id:\"minecraft:max_health\",base:1000}]}");
			float[] health = new float[1];
			int[] sprintTicks = new int[2];
			add(20, () -> {
				mc.player.getInventory().setSelectedSlot(0);
				health[0] = zombieHealth(mc, "legitstrafe");
				call("showcaseSet", "Kill Aura", "Hostiles", "true");
				call("showcaseSet", "Kill Aura", "Legit", "true");
				call("showcaseSet", "Target Strafe", "Legit", "true");
				call("showcaseEnable", "Kill Aura", true);
				call("showcaseEnable", "Target Strafe", true);
			});
			for (int i = 0; i < 16; i++) {
				add(5, () -> {
					var zombie = entity(mc, "legitstrafe");
					if (zombie != null) {
						double dx = mc.player.getX() - zombie.getX();
						double dz = mc.player.getZ() - zombie.getZ();
						float want = (float) Math.toDegrees(Math.atan2(-(zombie.getX() - mc.player.getX()), zombie.getZ() - mc.player.getZ()));
						sprintTicks[0] += mc.player.isSprinting() ? 1 : 0;
						sprintTicks[1]++;
						System.out.printf("TEMPLE_LEGITSTRAFE angle %.0f distance %.2f sprint %s viewOffTarget %.0f%n",
								Math.toDegrees(Math.atan2(dz, dx)), Math.sqrt(dx * dx + dz * dz), mc.player.isSprinting(),
								net.minecraft.util.Mth.wrapDegrees(mc.player.getYRot() - want));
					}
				});
			}
			add(1, () -> System.out.printf("TEMPLE_LEGITSTRAFE health %.0f -> %.0f, sprinting %d/%d samples%n", health[0],
					zombieHealth(mc, "legitstrafe"), sprintTicks[0], sprintTicks[1]));
			add(1, () -> {
				call("showcaseEnable", "Target Strafe", false);
				call("showcaseEnable", "Kill Aura", false);
				call("showcaseSet", "Kill Aura", "Legit", "false");
				call("showcaseSet", "Target Strafe", "Legit", "false");
			});
			command(mc, "kill @e[tag=legitstrafe]");
			command(mc, "gamemode creative");
			add(10, () -> { });
		}
		if (only.contains("LegitCombat")) {
			command(mc, "gamemode survival");
			command(mc, "effect give @s resistance 600 255 true");
			command(mc, "item replace entity @s hotbar.0 with minecraft:iron_sword");
			command(mc, "item replace entity @s hotbar.1 with minecraft:iron_pickaxe");
			command(mc, "tp @s ~ ~ ~ 20 0");
			command(mc, "summon zombie ~0.6 ~ ~3 {NoAI:1b,PersistenceRequired:1b,Health:300f,attributes:[{id:\"minecraft:max_health\",base:300}]}");
			float[] start = new float[3];
			add(20, () -> {
				mc.player.getInventory().setSelectedSlot(0);
				start[0] = zombieHealth(mc, "");
				start[1] = mc.player.getYRot();
				start[2] = mc.player.getXRot();
				call("showcaseSet", "Kill Aura", "Hostiles", "true");
				call("showcaseSet", "Kill Aura", "Legit", "true");
				call("showcaseEnable", "Kill Aura", true);
			});
			add(80, () -> { });
			add(1, () -> System.out.printf("TEMPLE_LEGIT killaura health %.1f -> %.1f, view %.0f/%.0f -> %.0f/%.0f%n", start[0],
					zombieHealth(mc, ""), start[1], start[2], mc.player.getYRot(), mc.player.getXRot()));
			add(1, () -> {
				call("showcaseEnable", "Kill Aura", false);
				call("showcaseSet", "Kill Aura", "Legit", "false");
			});
			command(mc, "kill @e[type=zombie]");
			command(mc, "fill ~-5 ~-1 ~-5 ~5 ~-1 ~5 minecraft:bedrock");
			command(mc, "fill ~-2 ~ ~2 ~2 ~1 ~2 minecraft:stone");
			int[] stone = new int[1];
			add(10, () -> {
				mc.player.getInventory().setSelectedSlot(1);
				stone[0] = countStone(mc);
				call("showcaseSet", "Nuker", "Legit", "true");
				call("showcaseEnable", "Nuker", true);
			});
			add(160, () -> { });
			add(1, () -> System.out.println("TEMPLE_LEGIT nuker stone " + stone[0] + " -> " + countStone(mc)
					+ ", pitch " + mc.player.getXRot()));
			add(1, () -> {
				call("showcaseEnable", "Nuker", false);
				call("showcaseSet", "Nuker", "Legit", "false");
			});
			command(mc, "gamemode creative");
			add(10, () -> { });
		}
		if (only.contains("Modules")) {
			command(mc, "gamemode survival");
			command(mc, "effect give @s resistance 99999 255 true");
			command(mc, "effect give @s saturation 99999 1 true");
			command(mc, "item replace entity @s hotbar.0 with minecraft:iron_sword");
			command(mc, "item replace entity @s hotbar.1 with minecraft:end_crystal 64");
			command(mc, "item replace entity @s hotbar.2 with minecraft:obsidian 64");
			command(mc, "item replace entity @s hotbar.3 with minecraft:respawn_anchor 64");
			command(mc, "item replace entity @s hotbar.4 with minecraft:glowstone 64");
			command(mc, "item replace entity @s hotbar.5 with minecraft:water_bucket");
			command(mc, "item replace entity @s hotbar.6 with minecraft:ender_pearl 16");
			command(mc, "item replace entity @s hotbar.7 with minecraft:cobblestone 64");
			command(mc, "item replace entity @s hotbar.8 with minecraft:golden_apple 16");
			command(mc, "item replace entity @s weapon.offhand with minecraft:totem_of_undying");
			command(mc, "summon zombie ~4 ~ ~2 {NoAI:1b,PersistenceRequired:1b,Health:1000f,attributes:[{id:\"minecraft:max_health\",base:1000}]}");
			command(mc, "summon villager ~-4 ~ ~3 {NoAI:1b,PersistenceRequired:1b}");
			command(mc, "summon cow ~2 ~ ~-4 {NoAI:1b,PersistenceRequired:1b}");
			command(mc, "setblock ~3 ~ ~-2 minecraft:chest");
			net.minecraft.world.phys.Vec3[] home = new net.minecraft.world.phys.Vec3[1];
			add(20, () -> {
				home[0] = mc.player.position();
				call("showcaseErrors");
			});
			for (String name : (List<String>) call("showcaseModules")) {
				if (SWEEP_SKIP.contains(name)) {
					continue;
				}
				add(25, () -> {
					if (mc.gui.screen() != null) {
						mc.gui.setScreen(null);
					}
					if (only.contains("LegitSweep")) {
						call("showcaseSet", name, "Legit", "true");
						call("showcaseSet", name, "Mode", "Legit");
					}
					call("showcaseEnable", name, true);
				});
				add(3, () -> call("showcaseEnable", name, false));
				add(1, () -> {
					if (mc.player != null && home[0] != null && mc.player.position().distanceTo(home[0]) > 1) {
						mc.player.connection.sendCommand("tp @s " + home[0].x + " " + home[0].y + " " + home[0].z);
					}
				});
			}
			add(10, () -> {
				List<String> errors = (List<String>) call("showcaseErrors");
				System.out.println("TEMPLE_SWEEP errors " + errors.size());
				errors.forEach(e -> System.out.println("TEMPLE_SWEEP_ERROR " + e));
			});
			command(mc, "gamemode creative");
		}
		List<String> names = (List<String>) call("cosmeticNames");
		for (String name : names) {
			if (!only.isEmpty() && !only.contains(name)) {
				continue;
			}
			String file = name.toLowerCase().replace(' ', '-');
			if (name.equals("Place Animation")) {
				for (String style : List.of("Grow", "Drop")) {
					net.minecraft.core.BlockPos[] at = new net.minecraft.core.BlockPos[1];
					add(1, () -> {
						call("showcaseSet", name, "Style", style);
						call("showcaseSet", name, "Duration", "1.2");
						call("showcaseSet", name, "Break duration", "1");
						call("showcaseEnable", name, true);
						net.minecraft.world.phys.Vec3 ahead = net.minecraft.world.phys.Vec3.directionFromRotation(0, mc.player.getYRot());
						at[0] = net.minecraft.core.BlockPos.containing(mc.player.position().add(ahead.scale(3.5)).add(0, 1, 0));
						mc.level.setBlock(at[0], net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState(), 3);
					});
					add(2, () -> { });
					shot(mc, file + "-" + style.toLowerCase() + "-a");
					add(40, () -> { });
					shot(mc, file + "-" + style.toLowerCase() + "-done");
					add(1, () -> mc.level.setBlock(at[0], net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3));
					add(1, () -> { });
					shot(mc, file + "-" + style.toLowerCase() + "-break");
				}
				add(20, () -> call("showcaseEnable", name, false));
				continue;
			}
			if (name.equals("Player Aura")) {
				add(1, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
				for (String part : List.of("Wings", "Halo", "Gyroscope", "Crystals")) {
					for (String material : List.of("Gold", "Glass", "Light")) {
						add(1, () -> {
							for (String other : List.of("Wings", "Halo", "Gyroscope", "Crystals")) {
								call("showcaseSet", name, other, String.valueOf(other.equals(part)));
							}
							call("showcaseSet", name, "Material", material);
							call("showcaseEnable", name, true);
						});
						add(15, () -> { });
						shot(mc, file + "-" + part.toLowerCase() + "-" + material.toLowerCase());
					}
				}
				add(1, () -> call("showcaseEnable", name, false));
				add(1, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
				continue;
			}
			List<String> styles = (List<String>) call("showcaseStyles", name);
			for (String style : styles.isEmpty() ? List.of("") : styles) {
				String tag = style.isEmpty() ? file : file + "-" + style.toLowerCase().replace(' ', '-');
				add(1, () -> {
					if (!style.isEmpty()) {
						call("showcaseSet", name, "Style", style);
					}
					call("showcaseSet", name, "Duration", "2.5");
					String material = System.getProperty("temple.showcase.material", "");
					if (!material.isEmpty()) {
						call("showcaseSet", name, "Material", material);
					}
					call("showcaseEnable", name, true);
					call("showcasePreview", name);
				});
				add(4, () -> { });
				shot(mc, tag + "-a");
				add(12, () -> { });
				shot(mc, tag + "-b");
				add(20, () -> call("showcaseEnable", name, false));
			}
		}
	}
}
