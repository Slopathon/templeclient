package nz.temple.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import nz.temple.TempleNz;
import nz.temple.client.gui.TempleScreen;
import nz.temple.client.gui.SuggestionReminder;
import nz.temple.client.hud.HudOverlay;
import nz.temple.client.hud.Notifications;
import nz.temple.client.hud.TargetInfo;
import nz.temple.client.input.KeybindPoller;
import nz.temple.client.modules.client.*;
import nz.temple.client.modules.combat.*;
import nz.temple.client.modules.cosmetic.Spinbot;
import nz.temple.client.modules.movement.*;
import nz.temple.client.modules.player.*;
import nz.temple.client.modules.render.*;
import nz.temple.client.modules.world.*;
import nz.temple.client.render.ChamsLayer;
import nz.temple.client.render.GlassRenderer;
import nz.temple.client.render.Projection;
import nz.temple.client.render.Ui;
import nz.temple.client.render.WorldEsp;
import nz.temple.client.render.streamproof.StreamproofRenderer;
import nz.temple.command.CommandManager;
import nz.temple.config.ConfigManager;
import nz.temple.event.EventBus;
import nz.temple.event.TickEvent;
import nz.temple.module.ModuleManager;
import nz.temple.module.Category;
import nz.temple.social.FriendManager;

public final class TempleNzClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME);
	private static ModuleManager modules;
	private static nz.temple.module.Module customAnimations;
	private static GlassRenderer glass;
	private static HudOverlay hud;
	private static Notifications notifications;
	private static ConfigManager config;
	private static Ui ui;
	private static MenuModule menuModule;
	private static SuggestionReminder suggestions;
	private static KeyMapping menuKey;

	public static boolean isMenuKey(net.minecraft.client.input.KeyEvent event) {
		return menuKey != null && menuKey.matches(event);
	}

	public static ConfigManager config() {
		return config;
	}

	public static Notifications notifications() {
		return notifications;
	}

	public static boolean openMenu(Screen returnScreen) {
		if (modules == null || config == null || ui == null || menuModule == null || suggestions == null) {
			return false;
		}
		if (menuModule.modernGui()) {
			Minecraft.getInstance().gui.setScreen(new nz.temple.client.gui.ModernScreen(modules, ui, returnScreen));
			return true;
		}
		Minecraft.getInstance().gui.setScreen(new TempleScreen(modules, config, ui, menuModule::scale,
				returnScreen, suggestions.menuOpened()));
		return true;
	}

	public static void openGlassMenu(Screen returnScreen, nz.temple.module.Module module) {
		if (modules == null || config == null || ui == null || menuModule == null || suggestions == null) {
			return;
		}
		TempleScreen screen = new TempleScreen(modules, config, ui, menuModule::scale, returnScreen, false);
		Minecraft.getInstance().gui.setScreen(screen);
		if (module != null) {
			screen.showSettings(module);
		}
	}

	public static void openPopup(Screen returnScreen, String kind) {
		if (modules == null || config == null || ui == null || menuModule == null || suggestions == null) {
			return;
		}
		TempleScreen screen = new TempleScreen(modules, config, ui, menuModule::scale, returnScreen, false);
		Minecraft.getInstance().gui.setScreen(screen);
		screen.popupOnly(kind);
	}
	public static void openHudEditor(Screen returnScreen) {
		openGlassMenu(returnScreen, null);
		if (Minecraft.getInstance().gui.screen() instanceof TempleScreen screen) {
			screen.editHud();
		}
	}
	private static nz.temple.client.render.Fx effects;

	public static String oreSimVerify() {
		return modules.get(nz.temple.client.modules.render.OreSim.class).verify();
	}

	public static java.util.List<String> cosmeticNames() {
		java.util.List<String> out = new java.util.ArrayList<>();
		for (nz.temple.module.Module m : modules.all()) {
			if (m.category() == nz.temple.module.Category.COSMETICS) {
				out.add(m.name());
			}
		}
		return out;
	}
	public static java.util.List<String> showcaseStyles(String name) {
		nz.temple.module.Module m = modules.byName(name);
		var s = m == null ? null : m.setting("Style");
		return s instanceof nz.temple.setting.ModeSetting mode ? mode.options() : java.util.List.of();
	}
	public static String showcaseStreamproof(String dumpFile) {
		if (dumpFile != null && !dumpFile.isEmpty()) {
			StreamproofRenderer.dumpNextFrame(dumpFile);
		}
		return StreamproofRenderer.state();
	}
	public static void showcasePour(int x, int y, int z) {
		nz.temple.client.render.water.DropletWater.pretendPour(x, y, z);
	}

	public static void showcaseBreak() {
		nz.temple.client.render.water.DropletWater.pretendBreak();
	}

	private static nz.temple.module.Module showcaseModule(String name) {
		nz.temple.module.Module m = modules.byName(name);
		return m != null ? m : nz.temple.client.modules.render.RealisticWorld.feature(name);
	}

	public static boolean showcaseEnable(String name, boolean on) {
		nz.temple.module.Module m = modules.byName(name);
		if (m == null) {
			return nz.temple.client.modules.render.RealisticWorld.setFeature(name, on);
		}
		m.setEnabled(on);
		return true;
	}
	private static String versionLabel;
	public static String versionLabel() {
		if (versionLabel == null) {
			versionLabel = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("templenz")
					.map(c -> "Temple.nz " + c.getMetadata().getVersion().getFriendlyString()).orElse("Temple.nz");
		}
		return versionLabel;
	}
	public static String showcaseServerKey() {
		return nz.temple.client.util.ServerKey.current();
	}
	public static void showcaseKnife(String action) {
		nz.temple.client.modules.cosmetic.Knives.showcase(action);
	}
	public static void showcaseOpenMap() {
		nz.temple.client.modules.render.WorldMap.open();
	}
	public static void showcaseReloadMap() {
		nz.temple.client.map.MapStore.get().leave();
	}
	public static boolean showcaseEnabled(String name) {
		nz.temple.module.Module m = showcaseModule(name);
		return m != null && m.isEnabled();
	}
	public static boolean showcaseSet(String module, String setting, String value) {
		nz.temple.module.Module m = showcaseModule(module);
		var s = m == null ? null : m.setting(setting);
		return s != null && s.trySet(value);
	}

	public static String showcaseSettingValue(String module, String setting) {
		nz.temple.module.Module m = showcaseModule(module);
		var s = m == null ? null : m.setting(setting);
		return s == null ? null : String.valueOf(s.get());
	}
	public static boolean showcaseSettings(String name) {
		nz.temple.module.Module m = modules.byName(name);
		if (m == null && nz.temple.client.modules.render.RealisticWorld.feature(name) != null) {
			m = modules.byName("Realistic World");
		}
		var open = net.minecraft.client.Minecraft.getInstance().gui.screen();
		if (m != null && open instanceof TempleScreen screen) {
			screen.showSettings(m);
			return true;
		}
		return false;
	}
	public static boolean showcaseMenu(String action, String arg) {
		if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof TempleScreen screen) {
			screen.showcase(action, arg);
			return true;
		}
		return false;
	}
	public static boolean showcaseBind(String name, Integer key) {
		nz.temple.module.Module m = modules.byName(name);
		if (m != null) {
			m.keybind().set(key);
		}
		return m != null;
	}
	public static boolean showcaseZoom(Integer notches) {
		for (int i = 0; i < Math.abs(notches); i++) {
			nz.temple.client.modules.render.Zoom.scroll(Math.signum(notches));
		}
		return true;
	}
	public static boolean showcasePress(String module, String setting) {
		nz.temple.module.Module m = modules.byName(module);
		if (m != null && m.setting(setting) instanceof nz.temple.setting.ActionSetting action) {
			action.press();
			return true;
		}
		return false;
	}
	public static String showcaseHitSound() {
		var hs = modules.get(nz.temple.client.modules.render.HitSounds.class);
		var pcm = hs == null ? null : hs.customSound();
		return "custom=" + (pcm == null ? "none" : String.format(java.util.Locale.ROOT, "%.2fs@%d", pcm.seconds(), pcm.rate()))
				+ " error=" + (hs == null ? null : hs.customError()) + " output=" + nz.temple.client.audio.SoundOut.status();
	}
	public static boolean showcaseCapeEditor() {
		var mc = net.minecraft.client.Minecraft.getInstance();
		mc.gui.setScreen(new nz.temple.client.gui.CapeEditorScreen(null, modules.get(nz.temple.client.modules.cosmetic.Cape.class)));
		return true;
	}
	public static java.util.List<String> showcaseCategory(String name) {
		java.util.List<String> out = new java.util.ArrayList<>();
		for (nz.temple.module.Module m : modules.all()) {
			if (m.category().name().equals(name)) {
				out.add(m.name());
			}
		}
		return out;
	}
	public static java.util.List<String> showcaseOptions(String module, String setting) {
		nz.temple.module.Module m = modules.byName(module);
		var s = m == null ? null : m.setting(setting);
		return s instanceof nz.temple.setting.ModeSetting mode ? mode.options() : java.util.List.of();
	}
	public static boolean showcasePopup(String kind) {
		openPopup(net.minecraft.client.Minecraft.getInstance().gui.screen(), kind);
		return true;
	}

	public static boolean showcaseTitle(String action) {
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.gui.screen() instanceof nz.temple.client.gui.TempleTitleScreen title) {
			title.showcase(action);
			return true;
		}
		return false;
	}
	public static String showcaseUiSounds() {
		var sm = net.minecraft.client.Minecraft.getInstance().getSoundManager();
		StringBuilder out = new StringBuilder();
		for (String n : new String[] {"tap", "soft", "hover", "tick", "on", "off", "open", "close", "pluck"}) {
			var id = net.minecraft.resources.Identifier.fromNamespaceAndPath(nz.temple.TempleNz.MOD_ID, "ui." + n);
			var events = sm.getSoundEvent(id);
			var instance = net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
					net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id), 1f, 0.3f);
			var result = sm.play(instance);
			out.append(n).append(events == null ? " missing" : " loaded").append(' ').append(result)
					.append(sm.isActive(instance) ? " active" : "").append("; ");
		}
		return out.toString();
	}
	public static boolean showcaseTheme(Integer index) {
		modules.get(nz.temple.client.modules.client.HudModule.class).applyTheme(index);
		return true;
	}

	public static boolean showcaseWheel(String action, Float degrees) {
		var emotes = modules.get(nz.temple.client.modules.cosmetic.Emotes.class);
		switch (action) {
			case "press" -> emotes.keyPressed();
			case "point" -> nz.temple.client.modules.cosmetic.Emotes.wheelTurn(Math.cos(Math.toRadians(degrees)) * 200,
					Math.sin(Math.toRadians(degrees)) * 200);
			case "scroll" -> nz.temple.client.modules.cosmetic.Emotes.wheelScroll(degrees);
			default -> emotes.keyReleased();
		}
		return true;
	}
	public static java.util.List<String> showcaseModules() {
		java.util.List<String> out = new java.util.ArrayList<>();
		for (nz.temple.module.Module m : modules.all()) {
			if (m.category() != nz.temple.module.Category.COSMETICS && m.category() != nz.temple.module.Category.CLIENT) {
				out.add(m.name());
			}
		}
		return out;
	}
	private static final java.util.List<String> showcaseErrors = new java.util.concurrent.CopyOnWriteArrayList<>();
	private static boolean watchingErrors;
	public static java.util.List<String> showcaseErrors() {
		if (!watchingErrors) {
			watchingErrors = true;
			modules.addErrorListener((module, error) -> {
				showcaseErrors.add(module.name() + ": " + error);
				error.printStackTrace();
			});
		}
		return java.util.List.copyOf(showcaseErrors);
	}
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void registerJointed(net.minecraft.client.renderer.entity.player.AvatarRenderer<?> avatar,
			LivingEntityRenderLayerRegistrationCallback.RegistrationHelper helper) {
		net.minecraft.client.renderer.entity.RenderLayerParent raw = avatar;
		helper.register(new nz.temple.client.render.JointedLimbsLayer(raw));
		helper.register(new nz.temple.client.cosmetic.AnchorLayer(raw));
		helper.register(new nz.temple.client.cosmetic.CustomModelsLayer(raw));
	}
	public static String showcaseServerRotation() {
		var p = net.minecraft.client.Minecraft.getInstance().player;
		return p == null ? "" : nz.temple.client.util.Rotations.serverYaw(p) + " " + nz.temple.client.util.Rotations.serverPitch(p);
	}

	public static boolean showcaseTilt(float degrees) {
		nz.temple.client.util.DetachedCamera.turn(0, degrees / 0.15);
		return true;
	}
	public static java.util.List<String> showcaseAuraPieces() {
		java.util.List<String> out = new java.util.ArrayList<>();
		for (nz.temple.setting.Setting<?> s : modules.byName("Player Aura").allSettings()) {
			if (s.key().endsWith(" first person") && !s.key().contains("Show in")) {
				out.add(s.key().substring(0, s.key().length() - " first person".length()));
			}
		}
		return out;
	}
	public static String showcaseMeshQuads() {
		return nz.temple.client.render.Meshes.lastFrameQuads + String.format(" build %.2fms submit %.2fms",
				nz.temple.client.render.Fx.buildNanos / 1e6, nz.temple.client.render.Fx.submitNanos / 1e6);
	}

	public static boolean showcaseRealism(String name, boolean on) {
		nz.temple.module.Module m = modules.byName(name);
		if (m == null) {
			return false;
		}
		for (nz.temple.setting.Setting<?> s : m.allSettings()) {
			if (s.name().equals("Realism") && s instanceof nz.temple.setting.BoolSetting b) {
				b.set(on);
			}
		}
		return true;
	}
	public static boolean showcasePieceRealism(String piece, boolean on) {
		nz.temple.module.Module m = modules.byName("Player Aura");
		for (nz.temple.setting.Setting<?> s : m.allSettings()) {
			if (s.key().equals(piece + " realism") && s instanceof nz.temple.setting.BoolSetting b) {
				b.set(on);
				return true;
			}
		}
		return false;
	}
	public static boolean showcaseOrbit(float degrees) {
		nz.temple.client.util.DetachedCamera.turn(degrees / 0.15, 0);
		return true;
	}
	public static boolean showcaseToast(String title, String detail) {
		if (notifications == null) {
			return false;
		}
		notifications.info(title, detail);
		return true;
	}
	public static boolean showcasePreview(String name) {
		nz.temple.module.Module m = modules.byName(name);
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.player == null) {
			return false;
		}
		net.minecraft.world.phys.Vec3 ahead = net.minecraft.world.phys.Vec3.directionFromRotation(0, mc.player.getYRot());
		net.minecraft.world.phys.Vec3 side = new net.minecraft.world.phys.Vec3(-ahead.z, 0, ahead.x);
		return nz.temple.client.security.LazyPayloadBridges.preview(m, mc.player.position().add(ahead.scale(3.2)).add(side.scale(2.2)));
	}
	public static boolean titleSceneAvailable() {
		return nz.temple.client.render.title.TitleScene.get().available();
	}
	public static java.util.List<String> shaderProblems() {
		java.util.List<String> problems = new java.util.ArrayList<>();
		java.util.List<com.mojang.blaze3d.pipeline.RenderPipeline> all = new java.util.ArrayList<>();
		try {
			all.add(nz.temple.client.render.SkyboxRenderer.pipeline());
			all.addAll(nz.temple.client.render.ChamsRenderTypes.pipelines());
			all.add(nz.temple.client.render.Meshes.pipeline());
			all.addAll(nz.temple.client.modules.render.WetGround.pipelines());
			all.addAll(nz.temple.client.modules.render.RealisticWater.pipelines());
			all.addAll(nz.temple.client.modules.render.WavingFoliage.pipelines());
			all.add(nz.temple.client.render.grass.GrassRenderer.pipeline());
			all.addAll(nz.temple.client.render.light.LightingPass.pipelines());
			all.add(nz.temple.client.render.SnowVolume.pipeline());
			all.addAll(nz.temple.client.render.snow.SnowDrifts.pipelines());
			all.addAll(nz.temple.client.render.GlowBuffer.pipelines());
			all.add(nz.temple.client.render.MotionBlur.pipeline());
			all.add(nz.temple.client.render.WorldPost.pipeline());
			all.add(nz.temple.client.render.ScreenFilter.pipeline());
			all.add(nz.temple.client.render.title.SongBackdrop.pipeline());
			if (effects != null) {
				all.addAll(effects.pipelines());
			}
		} catch (RuntimeException e) {
			problems.add("building pipelines: " + e + java.util.Arrays.stream(e.getStackTrace())
					.filter(f -> f.getClassName().startsWith("nz.temple")).findFirst().map(f -> " at " + f).orElse(""));
			return problems;
		}
		for (var pipeline : all) {
			try {
				if (!nz.temple.client.compat.Gpu.precompile(pipeline)) {
					problems.add(pipeline.getLocation().toString());
				}
			} catch (RuntimeException e) {
				problems.add(pipeline.getLocation() + ": " + e);
			}
		}
		if (effects != null) {
			String copy = effects.mesh().selfTest();
			if (copy != null) {
				problems.add(copy);
			}
		}
		String title = nz.temple.client.render.title.TitleScene.get().selfTest();
		if (title != null) {
			problems.add(title);
		}
		String hole = nz.temple.client.render.title.BlackHoleScene.get().selfTest();
		if (hole != null) {
			problems.add(hole);
		}
		return problems;
	}
	public static ModuleManager modules() {
		return modules;
	}
	public static nz.temple.module.Module customAnimations() {
		return customAnimations;
	}

	private static HudModule hudSettings;

	/** Each HUD element as a module in the HUD category, with its own options. */
	private static void registerHudElements(HudModule h) {
		modules.register(new nz.temple.client.modules.hud.HudElementModule("Info Bar", "The bar at the top. In Edit HUD, drag its items to reorder them, drag them off to hide them, and drag them back from the tray.",
				h.watermark, h.infoLogo, h.infoUser, h.infoPing, h.fps, h.speed, h.infoServer, h.infoTps, h.infoCoords, h.infoDate, h.infoTime,
				h.twentyFourHour));
		modules.register(new nz.temple.client.modules.hud.HudElementModule("Lyrics", "The current line of the song playing, under the info bar. "
				+ "Needs the Music module.", h.lyricsBar));
		modules.register(new nz.temple.client.modules.hud.HudElementModule("Keystrokes", "W, A, S, D, the mouse buttons and space, lighting up as you press them",
				h.keystrokes, h.keysMouse, h.keysSpace));
		modules.register(new nz.temple.client.modules.hud.HudElementModule("Coordinates", "Shows your current position", h.coordinates));
		modules.register(new nz.temple.client.modules.hud.HudElementModule("ArrayList", "Lists the modules you have turned on",
				h.moduleList, h.moduleInfo, h.listSort, h.listHideVisuals));
		modules.register(new nz.temple.client.modules.hud.HudElementModule("Keybinds", "Your bound modules and their keys", h.keybinds,
				h.keybindsOnlyOn, h.keybindsMax));
		modules.register(new nz.temple.client.modules.hud.HudElementModule("Effects List", "Shows your active potion effects", h.potions));
		modules.register(new nz.temple.client.modules.hud.HudElementModule("Speed Graph", "Graphs how fast you are moving", h.speedGraph,
				h.graphLength));
		modules.register(new nz.temple.client.modules.hud.HudElementModule("Target HUD", "Shows the health of who you are fighting",
				h.targetHud, h.targetPosition, h.targetArmor, h.targetEffects));
		modules.register(new nz.temple.client.modules.hud.HudElementModule("Armor HUD", "Your armour with durability bars, next to the hotbar",
				h.armor));
		modules.register(new nz.temple.client.modules.hud.HudElementModule("Elytra HUD",
				"Speed, altitude, climb and fireworks while gliding", h.elytraHud));
		modules.register(new nz.temple.client.modules.hud.HudElementModule("Enemy Armor", "Nearby players' armour, health and pops",
				h.enemyArmor, h.enemyCount, h.enemyRange, h.enemyFriends));
		modules.register(new nz.temple.client.modules.hud.HudElementModule("Notifications", "A notice when a module turns on or off",
				h.notifications, h.notificationScale));
	}

	/** The HUD module's settings (theme and colours), or null before the client has started. */
	public static HudModule hudSettings() {
		return hudSettings;
	}

	public static Ui ui() {
		return ui;
	}
	public static GlassRenderer glass() {
		return glass;
	}
	public static boolean glassAvailable() {
		return glass != null && glass.available();
	}
	public static int moduleCount() {
		return modules == null ? 0 : modules.all().size();
	}
	public static HudOverlay hud() {
		return hud;
	}

	@Override
	public void onInitializeClient() {

		if (Boolean.getBoolean("temple.mixinAudit") && net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()) {
			org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
			System.out.println("[temple] mixin audit complete");
			Runtime.getRuntime().halt(0);
		}
		if (Boolean.getBoolean("temple.worldTest") && net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()) {
			DevWorldTest.start();
		}
		if (Boolean.getBoolean("temple.titleTest") && net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()) {
			DevTitleTest.start();
		}
		if (Boolean.getBoolean("temple.shaderAudit") && net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()) {
			int[] ticks = {0};
			ClientTickEvents.END_CLIENT_TICK.register(mc -> {
				if (mc.gui.screen() != null && ++ticks[0] == 100) {
					java.util.List<String> problems = shaderProblems();
					System.out.println("[temple] shader audit: " + (problems.isEmpty() ? "all pipelines compiled" : problems));
					Runtime.getRuntime().halt(problems.isEmpty() ? 0 : 1);
				}
			});
		}
		modules = new ModuleManager(new EventBus());
		GlassModule glassModule = modules.register(new GlassModule());
		HudModule hudModule = modules.register(new HudModule());
		hudSettings = hudModule;
		modules.register(new nz.temple.client.modules.client.ThemesModule(hudModule));
		registerHudElements(hudModule);
		nz.temple.client.util.ServerTps.register();
		menuModule = modules.register(new MenuModule());
		modules.register(new Privacy()).setEnabled(true);
		modules.register(new nz.temple.client.modules.client.DiscordStatus()).setEnabled(true);
		FriendManager friends = new FriendManager();
		nz.temple.client.util.Friends.attach(friends);
		modules.register(new Fullbright());
		modules.register(new NoBlockRotation());
		modules.register(new Spoof());
		MotionBlurModule motionBlur = modules.register(new MotionBlurModule());
		nz.temple.client.render.light.LightingPass.register();
		nz.temple.client.render.WorldPost.register();
		nz.temple.client.render.ScreenFilter.register();
		nz.temple.client.render.MotionBlur.register(motionBlur::strength);
		modules.register(new nz.temple.client.modules.render.RealisticWorld());
		nz.temple.client.modules.render.RealisticWater.register();
		nz.temple.client.render.grass.GrassRenderer.register();
		modules.register(new Ambience());
		modules.register(new nz.temple.client.modules.render.Atmosphere());
		Chams chams = modules.register(new Chams());
		CrystalChams crystalChams = modules.register(new CrystalChams());
		modules.register(new nz.temple.client.modules.render.HandChams());
		modules.register(new nz.temple.client.modules.render.Skybox());
		modules.register(new nz.temple.client.modules.render.SmoothHotbar());
		modules.register(new nz.temple.client.modules.render.ChatAnimations());
		modules.register(new nz.temple.client.modules.render.Crosshair());
		modules.register(new nz.temple.client.modules.render.CleanView());
		var deathRecovery = modules.register(new nz.temple.client.modules.player.DeathRecovery());
		nz.temple.client.util.DeathLog.register();
		Esp esp = modules.register(new Esp());
		Tracers tracers = modules.register(new Tracers(friends));
		Nametags nametags = modules.register(new Nametags());
		LogoutSpots logoutSpots = modules.register(new LogoutSpots(friends));
		StorageESP storageEsp = modules.register(new StorageESP());
		OreESP oreEsp = modules.register(new OreESP());
		OreSim oreSim = modules.register(new OreSim());
		ChunkFinder chunkFinder = modules.register(new ChunkFinder());
		SusChunkFinder susChunkFinder = modules.register(new SusChunkFinder());
		ItemESP itemEsp = modules.register(new ItemESP());
		modules.register(new nz.temple.client.modules.render.BetterTooltips());
		BlockHighlight blockHighlight = modules.register(new BlockHighlight());
		BlockESP blockEsp = modules.register(new BlockESP());
		modules.register(new Xray());
		modules.register(new Zoom());
		modules.register(new Freecam());
		Freelook freelook = modules.register(new Freelook());
		nz.temple.client.modules.render.CinematicCamera cinematicCamera = modules.register(new nz.temple.client.modules.render.CinematicCamera());
		nz.temple.module.Module attackEffects = modules.register(nz.temple.client.security.PayloadModules.lazy("Hit Particles", Category.COSMETICS, "A burst of light where your hits land: a starburst and sparks, a slash, lightning or shattering glass. Visual only.", "nz.temple.client.modules.render.AttackEffects"));
		nz.temple.module.Module targetEsp = modules.register(nz.temple.client.security.PayloadModules.lazy("Target ESP", Category.RENDER, "Marks your current target with streams of light, glowing crystals, a light ring or curtain, a moving ring, orbiting trails or a halo.", "nz.temple.client.modules.render.TargetEsp"));
		nz.temple.module.Module jumpCircles = modules.register(nz.temple.client.security.PayloadModules.lazy("Jump Circles", Category.COSMETICS, "An effect on the ground when you jump or land: a ripple, rings, a shockwave, a crown, lit blocks or an earthquake.", "nz.temple.client.modules.render.JumpCircles"));
		nz.temple.module.Module trails = modules.register(nz.temple.client.security.PayloadModules.lazy("Trails", Category.COSMETICS, "Trails behind players: fire, frost, stardust, smoke or lightning left in the world, ribbons of light from the hands, feet or back, or afterimages of the body.", "nz.temple.client.modules.render.Trails"));
		nz.temple.module.Module deathEffects = modules.register(nz.temple.client.security.PayloadModules.lazy("Death Effects", Category.COSMETICS, "Effects where players and mobs die: coins, souls, shockwaves, ghosts and more. Coins fly to you like experience.", "nz.temple.client.modules.render.DeathEffects"));
		nz.temple.module.Module airEffects = modules.register(nz.temple.client.security.PayloadModules.lazy("Air Effects", Category.COSMETICS, "Particles and shapes floating around you. Any combination can be on at once: fireflies, embers, snow, stardust, falling code, light lines, dashing cubes, rings, prisms, a light network and light pillars.", "nz.temple.client.modules.render.AirEffects"));
		nz.temple.module.Module blockEffects = modules.register(nz.temple.client.security.PayloadModules.lazy("Block Effects", Category.COSMETICS, "Effects when blocks are broken or placed, including by other players.", "nz.temple.client.modules.render.BlockEffects"));
		modules.register(new PlaceAnimation());
		modules.register(new nz.temple.client.modules.player.FakePlayer());
		modules.register(new nz.temple.client.modules.cosmetic.Knives());
		modules.register(new nz.temple.client.modules.cosmetic.CustomModels());
		modules.register(new nz.temple.client.modules.world.BaritoneModule());
		nz.temple.module.Module placePreview = modules.register(nz.temple.client.security.PayloadModules.lazy("Place Preview", Category.COSMETICS, "Shows a see-through preview of the block you are holding where it will be placed.", "nz.temple.client.modules.render.PlacePreview"));
		nz.temple.module.Module playerAura = modules.register(nz.temple.client.security.PayloadModules.lazy("Player Aura", Category.COSMETICS, "Wings, halos, crowns, horns, ears and other 3D pieces worn by players. Each piece has its own look, and any combination can be worn.", "nz.temple.client.modules.render.PlayerAura"));
		var cape = modules.register(new nz.temple.client.modules.cosmetic.Cape());
		nz.temple.module.Module damageEffects = modules.register(nz.temple.client.security.PayloadModules.lazy("Damage Effects", Category.COSMETICS, "Shows an effect where damage is dealt, and a warning pulse around you when your health is low.", "nz.temple.client.modules.render.DamageEffects"));
		nz.temple.module.Module footsteps = modules.register(nz.temple.client.security.PayloadModules.lazy("Footsteps", Category.COSMETICS, "Leaves marks where players walk: prints, puddles, petals, flames, frost or flowers.", "nz.temple.client.modules.render.Footsteps"));
		nz.temple.module.Module projectileTrails = modules.register(nz.temple.client.security.PayloadModules.lazy("Projectile Trails", Category.COSMETICS, "Trails behind arrows, pearls, tridents and fireballs, with an effect where they land.", "nz.temple.client.modules.render.ProjectileTrails"));
		nz.temple.module.Module pickupStreaks = modules.register(nz.temple.client.security.PayloadModules.lazy("Pickup Streaks", Category.COSMETICS, "A streak of light flies to whoever picks up an item or experience orb.", "nz.temple.client.modules.render.PickupStreaks"));
		nz.temple.module.Module teleportEffects = modules.register(nz.temple.client.security.PayloadModules.lazy("Teleport Effects", Category.COSMETICS, "Marks where a player teleported from and to, joined by a line. Useful for seeing where someone pearled.", "nz.temple.client.modules.render.TeleportEffects"));
		nz.temple.module.Module damageNumbers = modules.register(nz.temple.client.security.PayloadModules.lazy("Damage Numbers", Category.RENDER, "Shows damage numbers where hits land, sized and coloured by how much damage was dealt.", "nz.temple.client.modules.render.DamageNumbers"));
		nz.temple.module.Module hitMarkers = modules.register(nz.temple.client.security.PayloadModules.lazy("Hit Markers", Category.COSMETICS, "Shows a marker when a hit lands, either on the spot you hit or at your crosshair, with a larger marker for kills.", "nz.temple.client.modules.render.HitMarkers"));
		modules.register(new nz.temple.client.modules.render.HitSounds());
		nz.temple.client.modules.render.Music music = modules.register(new nz.temple.client.modules.render.Music());
		modules.register(new nz.temple.client.modules.render.ItemPhysics());
		modules.register(new nz.temple.client.modules.render.GlintColor());
		nz.temple.module.Module totemPops = modules.register(nz.temple.client.security.PayloadModules.lazy("Totem Pops", Category.RENDER, "Counts how many totems each player has used and shows the count on their nametag. Replaces Minecraft's totem particles with a 3D effect, and can shrink or hide the totem animation on your screen.", "nz.temple.client.modules.render.TotemPops"));
		nz.temple.module.Module contrails = modules.register(nz.temple.client.security.PayloadModules.lazy("Contrails", Category.COSMETICS, "Vapour trails from both shoulders while flying, thicker at higher speed. Made for elytra flight.", "nz.temple.client.modules.render.Contrails"));
		nz.temple.module.Module aurora = modules.register(nz.temple.client.security.PayloadModules.lazy("Aurora", Category.COSMETICS, "Aurora curtains around you, above your head, and along the path you walk.", "nz.temple.client.modules.render.Aurora"));
		nz.temple.module.Module pets = modules.register(nz.temple.client.security.PayloadModules.lazy("Pets", Category.COSMETICS, "A pet that follows you and can show your notifications. Only visible to you.", "nz.temple.client.modules.cosmetic.Pets"));
		modules.register(new nz.temple.client.modules.cosmetic.WalkStyle());
		nz.temple.client.modules.cosmetic.Emotes emotes = modules.register(new nz.temple.client.modules.cosmetic.Emotes());
		nz.temple.module.Module breakAnimation = modules.register(nz.temple.client.security.PayloadModules.lazy("Break Animation", Category.COSMETICS, "Shows your progress mining a block, and breaks it apart when it breaks.", "nz.temple.client.modules.cosmetic.BreakAnimation"));
		nz.temple.module.Module explosionEffects = modules.register(nz.temple.client.security.PayloadModules.lazy("Explosion Effects", Category.COSMETICS, "Replaces crystal and TNT explosions with a fireball, implosion or nova effect.", "nz.temple.client.modules.render.ExplosionEffects"));
		modules.register(new nz.temple.client.modules.player.InventoryTweaks());
		nz.temple.module.Module stormEffects = modules.register(nz.temple.client.security.PayloadModules.lazy("Storm Effects", Category.COSMETICS, "Replaces lightning with a realistic strike: a branching bolt that builds down from the sky, flashes, re-strikes and fades, lighting up the sky and scorching the ground.", "nz.temple.client.modules.render.StormEffects"));
		nz.temple.module.Module vitals = modules.register(nz.temple.client.security.PayloadModules.lazy("Vitals", Category.COSMETICS, "Darkens the edges of the screen with a pulse when your health is low.", "nz.temple.client.modules.render.Vitals"));
		customAnimations = modules.register(nz.temple.client.security.PayloadModules.lazy("Custom Animations", Category.COSMETICS, "Custom first-person swing animations, with separate sets for swords and general items, pickaxes and axes. The set is chosen from the item in your hand.", "nz.temple.client.modules.render.CustomAnimations"));
		modules.register(new Animations());
		SpawnerNotifier spawnerNotifier = modules.register(new SpawnerNotifier());
		StashFinder stashFinder = modules.register(new StashFinder());
		Trajectories trajectories = modules.register(new Trajectories());
		modules.register(new Spinbot());
		modules.register(new NoFall());
		modules.register(new Fly());
		modules.register(new Step());
		modules.register(new Jesus());
		modules.register(new Spider());
		modules.register(new BoatFly());
		modules.register(nz.temple.client.security.PayloadModules.lazy("Elytra Boost", Category.MOVEMENT, "Boosts elytra flight without rockets. Steer with your mouse; Space climbs.", "nz.temple.client.modules.movement.ElytraBoost"));
		modules.register(nz.temple.client.security.PayloadModules.lazy("Entity Speed", Category.MOVEMENT, "Makes the mount you are riding move faster.", "nz.temple.client.modules.movement.EntitySpeed"));
		modules.register(nz.temple.client.security.PayloadModules.lazy("Fast Fall", Category.MOVEMENT, "Drops you quickly after stepping off a ledge.", "nz.temple.client.modules.movement.FastFall"));
		modules.register(nz.temple.client.security.PayloadModules.lazy("Fast Ladder", Category.MOVEMENT, "Climbs ladders and vines much faster than vanilla. Hold Space to climb faster still.", "nz.temple.client.modules.movement.FastLadder"));
		modules.register(new Glide());
		modules.register(nz.temple.client.security.PayloadModules.lazy("Ground Strafe", Category.MOVEMENT, "Keeps a steady strafe speed on the ground without jumping.", "nz.temple.client.modules.movement.GroundStrafe"));
		modules.register(new HighJump());
		modules.register(nz.temple.client.security.PayloadModules.lazy("Long Jump", Category.MOVEMENT, "Launches you further when you jump. Best for short use on servers without an anticheat.", "nz.temple.client.modules.movement.LongJump"));
		modules.register(new Noclip());
		modules.register(nz.temple.client.security.PayloadModules.lazy("No Slow", Category.MOVEMENT, "Removes the slowdown from using items, water and lava, and cobwebs.", "nz.temple.client.modules.movement.NoSlow"));
		modules.register(new Parkour());
		modules.register(nz.temple.client.security.PayloadModules.lazy("Reverse Step", Category.MOVEMENT, "Pulls you down quickly when you walk off a ledge so you land sooner.", "nz.temple.client.modules.movement.ReverseStep"));
		modules.register(nz.temple.client.security.PayloadModules.lazy("Safe Walk", Category.MOVEMENT, "Stops you at block edges so you do not walk off.", "nz.temple.client.modules.movement.SafeWalk"));
		modules.register(new nz.temple.client.modules.movement.Timer());
		Blink blink = modules.register(new Blink());
		// Combat modules with silent rotations. Registered in a fixed order: they share per-frame, packet and movement
		// listeners that must run in this sequence (for example Aim Assist resets the silent aim before Kill Aura sets it).
		modules.register(new nz.temple.client.modules.combat.TimeFreeze());
		modules.register(new nz.temple.client.modules.combat.Triggerbot());
		modules.register(new nz.temple.client.modules.combat.AimAssist());
		nz.temple.client.modules.combat.KillAura killAura = modules.register(new nz.temple.client.modules.combat.KillAura());
		modules.register(new nz.temple.client.modules.combat.ShieldBreaker());
		modules.register(new nz.temple.client.modules.combat.SprintReset());
		modules.register(new nz.temple.client.modules.combat.Criticals());
		modules.register(new nz.temple.client.modules.combat.Piercing());
		modules.register(new nz.temple.client.modules.combat.Reach());
		modules.register(new nz.temple.client.modules.combat.Backtrack());
		modules.register(new nz.temple.client.modules.combat.AntiBot());
		modules.register(new nz.temple.client.modules.combat.HitSwap());
		modules.register(new nz.temple.client.modules.combat.AutoCrystal());
		modules.register(new nz.temple.client.modules.combat.CrystalAura());
		modules.register(new nz.temple.client.modules.combat.AutoHitCrystal());
		modules.register(new nz.temple.client.modules.combat.AutoAnchor());
		modules.register(new nz.temple.client.modules.combat.AutoLunge());
		modules.register(new nz.temple.client.modules.combat.AutoSpearDtap());
		modules.register(new nz.temple.client.modules.combat.AutoTrap());
		modules.register(new nz.temple.client.modules.combat.TargetStrafe());
		modules.register(new nz.temple.client.modules.combat.AutoMace());
		modules.register(new nz.temple.client.modules.combat.CartAssist());
		modules.register(new nz.temple.client.modules.combat.AutoXbow());
		modules.register(new nz.temple.client.modules.combat.AutoJumpReset());
		modules.register(nz.temple.client.security.PayloadModules.lazy("Auto Clicker", Category.COMBAT, "Clicks for you while you hold attack, at a randomised rate.", "nz.temple.client.modules.combat.AutoClicker"));
		modules.register(new nz.temple.client.modules.client.CustomRotations());
		modules.register(new nz.temple.client.modules.movement.MoveFix()).setEnabled(true);
		modules.register(new nz.temple.client.modules.movement.Speed());
		modules.register(new nz.temple.client.modules.player.Targets());
		modules.register(new nz.temple.client.modules.combat.FakeLag());
		modules.register(new nz.temple.client.modules.combat.PearlClutch());
		nz.temple.client.modules.SchemaModules.registerTickEvent();
		AnchorAura anchorAura = modules.register(new AnchorAura(friends));
		modules.register(new nz.temple.client.modules.combat.Macro());
		Surround surround = modules.register(new Surround());
		AutoWeb autoWeb = modules.register(new AutoWeb(friends));
		HoleFiller holeFiller = modules.register(new HoleFiller(friends));
		CoverPlanner coverPlanner = modules.register(new CoverPlanner(friends));
		TrapEarlyWarning trapEarlyWarning = modules.register(new TrapEarlyWarning(friends));
		TPAura tpAura = modules.register(new TPAura(friends));
		killAura.addToggleListener(m -> {
			if (m.isEnabled()) {
				tpAura.setEnabled(false);
			}
		});
		tpAura.addToggleListener(m -> {
			if (m.isEnabled()) {
				killAura.setEnabled(false);
			}
		});
		modules.register(new PearlTrap(friends));
		modules.register(new BowAimbot(friends));
		modules.register(new AutoBlock(friends));
		modules.register(nz.temple.client.security.PayloadModules.lazy("Auto Weapon", Category.COMBAT, "Switches to your best sword or axe when you start fighting, and back afterwards.", "nz.temple.client.modules.combat.AutoWeapon"));
		modules.register(nz.temple.client.security.PayloadModules.lazy("Keep Sprint", Category.MOVEMENT, "Keeps you sprinting when you attack, so every hit deals full knockback. Does not change your motion.", "nz.temple.client.modules.combat.KeepSprint"));
		modules.register(new nz.temple.client.modules.combat.AttributeSwap());
		modules.register(new Velocity());
		modules.register(new AutoEat());
		modules.register(nz.temple.client.security.PayloadModules.lazy("Anti AFK", Category.PLAYER, "Prevents AFK kicks with randomised actions, including on servers with macro-check plugins.", "nz.temple.client.modules.player.AntiAFK"));
		modules.register(nz.temple.client.security.PayloadModules.lazy("Auto Armor", Category.PLAYER, "Equips armour from your inventory into empty slots as soon as you pick it up.", "nz.temple.client.modules.player.AutoArmor"));
		modules.register(nz.temple.client.security.PayloadModules.lazy("Auto Fish", Category.PLAYER, "Fishes automatically: casts, detects bites, reels in and recasts.", "nz.temple.client.modules.player.AutoFish"));
		modules.register(nz.temple.client.security.PayloadModules.lazy("Auto Gapple", Category.PLAYER, "Eats a golden apple when your health is low.", "nz.temple.client.modules.player.AutoGapple"));
		modules.register(nz.temple.client.security.PayloadModules.lazy("Auto Leave", Category.PLAYER, "Disconnects when your health is critically low.", "nz.temple.client.modules.player.AutoLeave"));
		modules.register(new nz.temple.client.modules.player.AutoReconnect());
		modules.register(nz.temple.client.security.PayloadModules.lazy("Auto Mend", Category.PLAYER, "Throws experience bottles while you sneak to repair Mending gear.", "nz.temple.client.modules.player.AutoMend"));
		modules.register(nz.temple.client.security.PayloadModules.lazy("Auto Respawn", Category.PLAYER, "Respawns automatically when you die.", "nz.temple.client.modules.player.AutoRespawn"));
		modules.register(nz.temple.client.security.PayloadModules.lazy("Auto Tool", Category.PLAYER, "Switches to the right tool for the block you are mining.", "nz.temple.client.modules.player.AutoTool"));
		modules.register(nz.temple.client.security.PayloadModules.lazy("Auto Totem", Category.PLAYER, "Moves a totem into your offhand when the current one pops.", "nz.temple.client.modules.player.AutoTotem"));
		modules.register(nz.temple.client.security.PayloadModules.lazy("Auto Walk", Category.PLAYER, "Walks forward automatically.", "nz.temple.client.modules.player.AutoWalk"));
		modules.register(nz.temple.client.security.PayloadModules.lazy("Chest Stealer", Category.PLAYER, "Takes everything out of chests when you open them.", "nz.temple.client.modules.player.ChestStealer"));
		modules.register(nz.temple.client.security.PayloadModules.lazy("Inventory Manager", Category.PLAYER, "Drops selected items from your inventory automatically.", "nz.temple.client.modules.player.InventoryManager"));
		modules.register(new nz.temple.client.modules.player.MiddleClickFriend()).setEnabled(true);
		modules.register(new nz.temple.client.modules.player.Notifier());
		modules.register(nz.temple.client.security.PayloadModules.lazy("Refill", Category.PLAYER, "Refills pearls, potions and food from your inventory into empty hotbar slots.", "nz.temple.client.modules.player.Refill"));
		modules.register(nz.temple.client.security.PayloadModules.lazy("Coord Snapper", Category.PLAYER, "Copies your block coordinates and dimension to the clipboard. Bind it to a key for quick use.", "nz.temple.client.modules.player.CoordSnapper"));
		modules.register(nz.temple.client.security.PayloadModules.lazy("Key Pearl", Category.PLAYER, "Throws an ender pearl from your hotbar in your look direction and switches back. Bind it to a key.", "nz.temple.client.modules.player.KeyPearl"));
		PearlFollow pearlFollow = modules.register(new PearlFollow());
		modules.register(nz.temple.client.security.PayloadModules.lazy("Player Detection", Category.PLAYER, "Sends a local chat alert when a loaded player enters your detection radius. No webhooks or server commands.", "nz.temple.client.modules.player.PlayerDetection"));
		modules.register(nz.temple.client.security.PayloadModules.lazy("Durability Alert", Category.PLAYER, "Warns when armour or a weapon is about to break.", "nz.temple.client.modules.player.DurabilityAlert"));
		modules.register(new DonutRegion());
		modules.register(nz.temple.client.security.PayloadModules.lazy("Stash Disconnect", Category.PLAYER, "Disconnects when a shulker box, spawner, amethyst or candle appears in a chunk you already had loaded, which usually means someone is building nearby.", "nz.temple.client.modules.player.StashDisconnect"));
		StaffDisplay staffDisplay = modules.register(new StaffDisplay());
		modules.register(new Nuker());
		modules.register(nz.temple.client.security.PayloadModules.lazy("Air Place", Category.WORLD, "Places blocks in the air along your crosshair, up to your reach.", "nz.temple.client.modules.world.AirPlace"));
		modules.register(nz.temple.client.security.PayloadModules.lazy("Auto Farm", Category.WORLD, "Harvests mature crops around you while you walk through a farm.", "nz.temple.client.modules.world.AutoFarm"));
		modules.register(nz.temple.client.security.PayloadModules.lazy("Auto Tunnel", Category.WORLD, "Digs a tunnel forward as you walk. Turn on Base finder to let it steer, avoid hazards and search for bases.", "nz.temple.client.modules.world.AutoTunnel"));
		modules.register(new nz.temple.client.modules.movement.ElytraAutopilot());
		modules.register(new nz.temple.client.modules.player.AutoRelog());
		modules.register(new nz.temple.client.modules.client.StreamerMode());
		modules.register(new nz.temple.client.modules.client.Streamproof());
		nz.temple.client.modules.render.WaypointsModule waypoints = modules.register(new nz.temple.client.modules.render.WaypointsModule());
		waypoints.setEnabled(true);
		modules.register(new nz.temple.client.modules.render.WorldMap()).setEnabled(true);
		modules.register(nz.temple.client.security.PayloadModules.lazy("Bed Breaker", Category.WORLD, "Breaks beds and dragon eggs around you.", "nz.temple.client.modules.world.BedBreaker"));
		modules.register(new Flatten());
		modules.register(new Scaffold());
		modules.register(new SpeedMine());
		modules.register(new Tower());
		modules.register(nz.temple.client.security.PayloadModules.lazy("Vein Miner", Category.WORLD, "Mines the whole ore vein when you break one ore. Aim at an ore and hold attack.", "nz.temple.client.modules.world.VeinMiner"));
		modules.register(new NoBlockInteract());
		nz.temple.client.util.MovementProfiles.register(modules);
		Projection projection = new Projection();
		projection.register();
		WorldEsp worldEsp = new WorldEsp();
		worldEsp.addSource(esp::collect);
		worldEsp.addSource(storageEsp::collect);
		worldEsp.addSource(storageEsp::collectCarts);
		worldEsp.addSource(oreEsp::collect);
		worldEsp.addSource(oreSim::collect);
		worldEsp.addSource(chunkFinder::collect);
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> chunkFinder.chunkLoaded(chunk));
		nz.temple.client.util.WorldEvents.onBlockChanged((pos, oldState, newState) -> chunkFinder.blockChanged(pos));
		worldEsp.addSource(susChunkFinder::collect);
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> susChunkFinder.chunkLoaded(chunk));
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> susChunkFinder.chunkUnloaded(chunk));
		nz.temple.client.util.WorldEvents.onBlockChanged((pos, oldState, newState) -> susChunkFinder.blockChanged(pos));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(susChunkFinder::disconnected));
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "chunk_finder"), StreamproofRenderer.hud("chunk_finder", (g, delta) -> chunkFinder.render(g, delta, projection)));
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "damage_numbers"), StreamproofRenderer.hud("damage_numbers", (g, delta) -> nz.temple.client.security.LazyPayloadBridges.renderHud(damageNumbers, g, delta, projection)));
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "hit_markers"), StreamproofRenderer.hud("hit_markers", (g, delta) -> nz.temple.client.security.LazyPayloadBridges.renderHud(hitMarkers, g, delta, projection)));
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "music_word"), StreamproofRenderer.hud("music_word", music::renderCrosshair));
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "vitals"), StreamproofRenderer.hud("vitals", (g, delta) -> nz.temple.client.security.LazyPayloadBridges.renderHud(vitals, g, delta, projection)));
		HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "letterbox"), StreamproofRenderer.hud("letterbox", freelook::renderBars));
		HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "cinematic_letterbox"), StreamproofRenderer.hud("cinematic_letterbox", cinematicCamera::renderBars));
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "emote_wheel"), StreamproofRenderer.hud("emote_wheel", (g, delta) -> emotes.renderWheel(g, ui)));
		worldEsp.addSource(itemEsp::collect);
		worldEsp.addSource(blockHighlight::collect);
		worldEsp.addSource(blockEsp::collect);
		worldEsp.addSource(anchorAura::collect);
		worldEsp.addSource(surround::collect);
		worldEsp.addSource(autoWeb::collect);
		worldEsp.addSource(holeFiller::collect);
		worldEsp.addSource(coverPlanner::collect);
		worldEsp.addSource(trapEarlyWarning::collect);
		worldEsp.addSource(blink::collect);
		nz.temple.client.render.WorldDraw.attach(worldEsp);
		worldEsp.addSource(trajectories::collect);
		worldEsp.addSource(pearlFollow::collect);
		worldEsp.addSource(spawnerNotifier::collect);
		worldEsp.addSource(stashFinder::collect);
		worldEsp.register();
		nz.temple.client.render.Fx fx = new nz.temple.client.render.Fx();
		effects = fx;
		nz.temple.client.modules.render.ChinaHat chinaHat = modules.register(new nz.temple.client.modules.render.ChinaHat());
		fx.addSource("chinaHat", chinaHat::emit);
		fx.addSource("targetEsp", (f, partial) -> nz.temple.client.security.LazyPayloadBridges.emit(targetEsp, f, partial));
		fx.addSource("jumpCircles", (f, partial) -> nz.temple.client.security.LazyPayloadBridges.emit(jumpCircles, f, partial));
		fx.addSource("trails", (f, partial) -> nz.temple.client.security.LazyPayloadBridges.emit(trails, f, partial));
		fx.addSource("attackEffects", (f, partial) -> nz.temple.client.security.LazyPayloadBridges.emit(attackEffects, f, partial));
		fx.addSource("chunkFinder", chunkFinder::emit);
		fx.addSource("deathEffects", (f, partial) -> nz.temple.client.security.LazyPayloadBridges.emit(deathEffects, f, partial));
		fx.addSource("airEffects", (f, partial) -> nz.temple.client.security.LazyPayloadBridges.emit(airEffects, f, partial));
		fx.addSource("hitMarkers", (f, partial) -> nz.temple.client.security.LazyPayloadBridges.emit(hitMarkers, f, partial));
		fx.addSource("blockEffects", (f, partial) -> nz.temple.client.security.LazyPayloadBridges.emit(blockEffects, f, partial));
		fx.addSource("placePreview", (f, partial) -> nz.temple.client.security.LazyPayloadBridges.emit(placePreview, f, partial));
		fx.addSource("logoutSpots", logoutSpots::emit);
		fx.addSource("playerAura", (f, partial) -> nz.temple.client.security.LazyPayloadBridges.emit(playerAura, f, partial));
		fx.addSource("damageEffects", (f, partial) -> nz.temple.client.security.LazyPayloadBridges.emit(damageEffects, f, partial));
		fx.addSource("footsteps", (f, partial) -> nz.temple.client.security.LazyPayloadBridges.emit(footsteps, f, partial));
		fx.addSource("projectileTrails", (f, partial) -> nz.temple.client.security.LazyPayloadBridges.emit(projectileTrails, f, partial));
		fx.addSource("pickupStreaks", (f, partial) -> nz.temple.client.security.LazyPayloadBridges.emit(pickupStreaks, f, partial));
		fx.addSource("teleportEffects", (f, partial) -> nz.temple.client.security.LazyPayloadBridges.emit(teleportEffects, f, partial));
		fx.addSource("totemPops", (f, partial) -> nz.temple.client.security.LazyPayloadBridges.emit(totemPops, f, partial));
		fx.addSource("crystalChams", crystalChams::emit);
		fx.addSource("contrails", (f, partial) -> nz.temple.client.security.LazyPayloadBridges.emit(contrails, f, partial));
		fx.addSource("aurora", (f, partial) -> nz.temple.client.security.LazyPayloadBridges.emit(aurora, f, partial));
		fx.addSource("WorldEffects", nz.temple.client.render.WorldEffects::emit);
		fx.addSource("pets", (f, partial) -> nz.temple.client.security.LazyPayloadBridges.emit(pets, f, partial));
		fx.addSource("breakAnimation", (f, partial) -> nz.temple.client.security.LazyPayloadBridges.emit(breakAnimation, f, partial));
		fx.addSource("explosionEffects", (f, partial) -> nz.temple.client.security.LazyPayloadBridges.emit(explosionEffects, f, partial));
		fx.addSource("stormEffects", (f, partial) -> nz.temple.client.security.LazyPayloadBridges.emit(stormEffects, f, partial));
		fx.addSource("deathRecovery", deathRecovery::emit);
		fx.addSource("itemEsp", itemEsp::emit);
		fx.addSource("cape", cape::emit);
		nz.temple.client.render.GlowBuffer.register();
		nz.temple.client.render.GlowBuffer.addUser(() -> pets != null && pets.isEnabled());
		nz.temple.client.render.GlowBuffer.addUser(() -> nz.temple.client.security.LazyPayloadBridges.glowing(attackEffects));
		nz.temple.client.render.GlowBuffer.addUser(() -> nz.temple.client.security.LazyPayloadBridges.glowing(damageEffects));
		nz.temple.client.render.GlowBuffer.addUser(() -> nz.temple.client.security.LazyPayloadBridges.glowing(trails));
		nz.temple.client.render.GlowBuffer.addUser(nz.temple.client.modules.cosmetic.Look::glowingRecently);
		nz.temple.client.render.GlowBuffer.addUser(() -> {
			Chams c = modules.get(Chams.class);
			if (c != null && c.glowOn()) {
				nz.temple.client.render.GlowBuffer.style(c.glowSpread(), 1f);
				return true;
			}
			return false;
		});
		fx.register();
		new nz.temple.client.render.SnowVolume(() -> nz.temple.client.security.LazyPayloadBridges.snowVolume(airEffects)).register();
		new nz.temple.client.render.snow.SnowDrifts(() -> nz.temple.client.security.LazyPayloadBridges.driftSettings(airEffects)).register();
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> ChamsLayer.register(renderer, helper));
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof net.minecraft.client.renderer.entity.player.AvatarRenderer<?> avatar) {
				registerJointed(avatar, helper);
			}
		});
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "esp"), StreamproofRenderer.hud("esp", (g, delta) -> esp.render(g, delta, projection)));
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "petbubble"), StreamproofRenderer.hud("petbubble", (g, delta) -> nz.temple.client.security.LazyPayloadBridges.renderPetBubble(pets, g, delta, projection)));
		worldEsp.addLineSource(tracers::collect);
		worldEsp.addLineSource(storageEsp::lines);
		worldEsp.addLineSource(trajectories::lines);
		worldEsp.addLineSource(coverPlanner::lines);
		worldEsp.addLineSource(trapEarlyWarning::lines);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "nametags"), StreamproofRenderer.hud("nametags", (g, delta) -> nametags.render(g, delta, projection)));
		notifications = new Notifications(Util::getMillis, hudModule.notifications::get);
		notifications.setRedirect((title, detail, kind) -> nz.temple.client.security.LazyPayloadBridges.say(pets, title, detail, kind));
		glass = new GlassRenderer(glassModule::params, () -> notifications.error("Glass", "shader failed, using plain panels"));
		ui = new Ui(glass);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "death_recovery"), StreamproofRenderer.hud("death_recovery", (g, delta) -> deathRecovery.render(g, delta, ui)));
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "logout_spots"), StreamproofRenderer.hud("logout_spots", (g, delta) -> logoutSpots.render(g, delta, projection, ui)));
		worldEsp.addLineSource(waypoints::lines);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "waypoints"), StreamproofRenderer.hud("waypoints", (g, delta) -> waypoints.render(g, delta, projection, ui)));
		nz.temple.client.util.PlayerHistory.register();
		nz.temple.module.Module.deferHooks();
		ClientLifecycleEvents.CLIENT_STARTED.register(mc -> nz.temple.module.Module.runDeferredHooks(modules.all()));
		nz.temple.module.LazyPayloadModule.onLoadFailure((module, error) -> {
			LOGGER.error("Module {} could not be loaded", module.name());
			notifications.error(module.name(), "could not be loaded, try again");
		});
		java.util.concurrent.Executor payloadFetch = java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "temple-payload");
			thread.setDaemon(true);
			return thread;
		});
		nz.temple.module.LazyPayloadModule.asyncLoader(payloadFetch, runnable -> Minecraft.getInstance().execute(runnable));
		config = new ConfigManager(FabricLoader.getInstance().getConfigDir().resolve(TempleNz.MOD_ID), modules, Util::getMillis);
		if (config.load() == ConfigManager.LoadResult.CORRUPT) {
			notifications.error("Config", "was corrupt, defaults loaded (backup kept)");
		}
		nz.temple.client.gui.HvhTab.apply(modules);
		suggestions = new SuggestionReminder(config, Util::getMillis);
		nz.temple.setting.ColorSetting.palette(role -> role == 2
				? nz.temple.client.render.Theme.secondary() : nz.temple.client.render.Theme.primary());
		modules.addToggleListener(notifications::toggled);
		modules.addToggleListener(module -> {
			if (!module.isEnabled() && module.disableWarning() != null) {
				notifications.error(module.name() + " off", "servers can detect this client");
			}
		});
		modules.addErrorListener((module, error) -> {
			LOGGER.error("Module {} crashed and was disabled", module.name(), error);
			notifications.error(module.name(), "crashed and was disabled");
		});
		hud = new HudOverlay(ui, modules, hudModule, notifications);
		hud.setTargetSource(TempleNzClient::targetInfo);
		hud.setEntitySource(TempleNzClient::combatTarget);
		hud.setBlockSource(blockHighlight::info);
		hud.setStaffSource(staffDisplay::online, staffDisplay::showWhenEmpty, staffDisplay::isEnabled);
		hud.setFriends(friends);
		hud.setConfig(config);
		hud.setProjection(projection);
		notifications.setDarkStyle(() -> hudModule.style.is("Dark"));
		notifications.setSize(() -> hudModule.notificationScale.get() / 100.0);
		hud.register();
		CommandManager commands = new CommandManager(modules, friends, line -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) {
				nz.temple.client.compat.Players.message(mc.player, Component.literal("§8[§bTemple§8] §r" + line));
			}
		});
		nz.temple.client.util.ClientCommands.register(commands);
		ClientSendMessageEvents.ALLOW_CHAT.register(message -> !commands.handle(message));
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "main"));
		menuKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.templenz.menu", nz.temple.client.compat.Input.KEYBOARD, InputConstants.KEY_RSHIFT, category));
		KeybindPoller poller = new KeybindPoller(modules);
		nz.temple.client.gui.PauseMenuButtons.register();

		net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (screen instanceof TempleScreen) {
				return;
			}
			net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
				boolean menuScreen = mc.level == null || s instanceof net.minecraft.client.gui.screens.PauseScreen;
				if (!menuScreen || !isMenuKey(event) || mc.gui.screen() != s
						|| s.getFocused() instanceof net.minecraft.client.gui.components.EditBox
						|| s instanceof net.minecraft.client.gui.screens.options.controls.KeyBindsScreen) {
					return true;
				}
				openMenu(s);
				return false;
			});
		});
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (menuKey.consumeClick()) {
				if (mc.gui.screen() == null) {
					openMenu(null);
				}
			}
			suggestions.tick();
			poller.tick(mc);
			nz.temple.client.util.Keys.tick();
			nz.temple.client.util.HitConfirm.tick(mc);
			modules.bus().post(TickEvent.INSTANCE, owner -> !nz.temple.client.util.DetachedCamera.freecam()
					|| !(owner instanceof nz.temple.module.Module m)
					|| m.category() == nz.temple.module.Category.RENDER || m.category() == nz.temple.module.Category.COSMETICS
					|| m.category() == nz.temple.module.Category.CLIENT);
			try {
				config.tick();
			} catch (RuntimeException e) {
				LOGGER.error("Could not save config", e);
			}
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			suggestions.flush();
			if (config.isDirty()) {
				config.save();
			}
		});
		LOGGER.info("{} loaded {} modules", TempleNz.NAME, modules.all().size());
	}
	private static LivingEntity combatTarget() {
		LivingEntity target = nz.temple.client.util.CombatTarget.current();
		if (target != null) {
			return target;
		}
		BowAimbot bow = modules.get(BowAimbot.class);
		target = bow == null ? null : bow.target();
		return target != null ? target : nz.temple.client.util.CombatTracker.recent(8000);
	}
	private static TargetInfo targetInfo() {
		LivingEntity target = combatTarget();
		Minecraft mc = Minecraft.getInstance();
		if (target == null || mc.player == null) {
			return null;
		}
		PlayerSkin skin = null;
		int ping = 0;
		if (target instanceof AbstractClientPlayer p) {
			skin = p.getSkin();
			if (mc.getConnection() != null) {
				PlayerInfo info = mc.getConnection().getPlayerInfo(p.getUUID());
				if (info != null) {
					ping = info.getLatency();
				}
			}
		}
		return new TargetInfo(target.getName().getString(), skin, target.getHealth(), target.getMaxHealth(),
				(float) mc.player.distanceTo(target), ping);
	}
}
