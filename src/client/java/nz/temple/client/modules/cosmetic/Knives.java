package nz.temple.client.modules.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import nz.temple.TempleNz;
import nz.temple.client.TempleNzClient;
import nz.temple.client.render.ChamsRenderTypes;
import nz.temple.client.render.bedrock.BedrockAnimation;
import nz.temple.client.render.bedrock.BedrockModel;
import nz.temple.client.util.Inv;
import nz.temple.client.util.KeyEdge;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.ActionSetting;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.KeybindSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class Knives extends Module {
	private static Knives instance;
	private static final String[][] BUILT_IN = {
			{"Karambit", "karambit", "1"}, {"Karambit | Obsidian", "karobsidian", "3"}, {"Butterfly", "butterfly", "1"},
			{"Butterfly | Emerald", "buemerald", "2"}, {"Butterfly | Snowfall", "busnowfall", "1"}, {"M9 Bayonet", "m9", "1"},
			{"M9 Bayonet | Amethyst", "m9amethyst", "2"}, {"M9 Bayonet | Autotronic", "m9auto", "4"},
			{"M9 Bayonet | Emerald", "m9emerald", "2"}, {"M9 Bayonet | Sapphire", "m9sapphire", "2"}, {"Bayonet", "bayonet", "1"},
			{"Bayonet | Autotronic", "bayoauto", "4"}, {"Talon", "talon", "1"}, {"Stiletto", "stiletto", "1"},
			{"Stiletto | Sapphire", "stilettosapphire", "2"}, {"Stiletto | Tiger tooth", "stilettott", "4"}, {"Skeleton", "skeleton", "1"},
			{"Huntsman", "tactical", "1"}, {"Classic Knife", "css", "1"}, {"Push", "push", "1"},
	};
	static final String README = """
			Your own knives
			===============
			Each knife is a folder in this one. Its name is the knife's name in the Knives module.
			  My Knife/
			    model.json      Bedrock geometry, exported from Blockbench (File > Export > Bedrock Geometry)
			    animation.json  Bedrock animations for it (File > Export > Animations)
			    texture.png     its texture

			The model is drawn in first person the way TaCZ and LR Tactical draw theirs:
			  - a bone named idle_view marks where your eyes are; the model is placed so that bone sits on the camera
			  - bones named righthand_pos and lefthand_pos are where your arms are drawn (their cubes are replaced by
			    your skin's arms)
			  - a bone named camera, if animated, moves the view with it
			Animations it uses, by name:
			  draw, draw_1, draw_2 ...      when you switch to the knife (one is picked at random)
			  static_idle                   held still, looping
			  idle                          a gentle sway on top, looping
			  melee_1, melee_2              swings, taking turns
			  melee_3                       a critical hit (swinging while falling)
			  inspect, inspect_1 ...        the inspect key (one is picked at random)
			Sounds named in an animation's sound_effects play if they are one of the built-in knife sounds
			(knife_slash1, knife_deploy1 and so on).
			Turn the Knives module off and on again, or restart, after adding a knife.
			""";
	private final ModeSetting knife;
	private final ModeSetting useOn;
	private final ModeSetting finish;
	private final ModeSetting material;
	private final BoolSetting sounds;
	private final NumberSetting volume;
	private final KeybindSetting inspectKey;
	private final NumberSetting armLength;
	private final NumberSetting height;
	private final NumberSetting forward;
	private final KeyEdge inspectEdge;
	private final Map<String, Path> custom = new LinkedHashMap<>();
	private record Skin(BedrockModel model, Map<String, BedrockAnimation> anims, Identifier texture, int finish) {
	}

	private final Map<String, Skin> loaded = new HashMap<>();
	private Skin skin;
	private String skinName;
	private BedrockAnimation main;
	private long mainStart;
	private float mainBlend;
	private int nextSound;
	private BedrockAnimation fading;
	private long fadeStart;
	private float fadeAt;
	private long baseStart;
	private int swingIndex;
	private boolean holding;
	private int lastSlot = -1;
	public Knives() {
		super("Knives", Category.COSMETICS, "Replaces your sword with a knife model that has its own first-person animations and sounds, "
				+ "and shows it in your hand in third person.");
		instance = this;
		scanCustom();
		List<String> names = new ArrayList<>();
		for (String[] k : BUILT_IN) {
			names.add(k[0]);
		}
		names.addAll(custom.keySet());
		knife = add(new ModeSetting("Knife", names, "Karambit").describe("The knife to hold. Your own knives from the knives folder are "
				+ "listed after the built-in ones."));
		useOn = add(new ModeSetting("Use on", List.of("Swords", "Swords and axes", "Any weapon"), "Swords")
				.describe("Which held items become the knife."));
		finish = add(new ModeSetting("Finish", List.of("Polished", "Flat"), "Polished")
				.describe("Polished draws gem blades as cut stones and steel as a polished mirror that catch the light. "
						+ "Flat shows the plain texture."));
		material = add(new ModeSetting("Material", List.of("Knife's own", "Match sword"), "Knife's own")
				.describe("Match sword makes the blade the material of the sword you hold: wood, stone, iron, gold, diamond, "
						+ "netherite or copper.").visibleWhen(() -> finish.is("Polished")));
		sounds = add(new BoolSetting("Sounds", true).describe("Plays the knife's draw, swing and inspect sounds."));
		volume = add(new NumberSetting("Volume", 60, 0, 100, 5, "%").describe("Volume of the knife sounds.").visibleWhen(sounds::get));
		inspectKey = add(new KeybindSetting("Inspect key", 89).describe("Plays the knife's inspect animation."));
		height = add(new NumberSetting("Height", 0.2, -0.5, 0.5, 0.01, " b")
				.describe("Moves the knife and hands up or down in your view."));
		forward = add(new NumberSetting("Distance", 0, -0.5, 0.5, 0.01, " b")
				.describe("Moves the knife and hands nearer or further away."));
		armLength = add(new NumberSetting("Arm length", 60, 25, 100, 5, "%").describe("How much of your forearms shows behind the "
				+ "hands. Shorter arms cover less of the knife."));
		add(new ActionSetting("Your knives", "Open folder", () -> nz.temple.client.compat.Platform.openPath(folder())));
		inspectEdge = new KeyEdge(inspectKey);
		knife.addListener(() -> skin = null);
		on(TickEvent.class, e -> tick());
	}
	public static Knives instance() {
		return instance;
	}
	private static Path folder() {
		Path dir = FabricLoader.getInstance().getConfigDir().resolve(TempleNz.MOD_ID).resolve("knives");
		try {
			Files.createDirectories(dir);
			Path readme = dir.resolve("README.txt");
			if (!Files.exists(readme)) {
				Files.writeString(readme, README, StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			TempleNzClient.LOGGER.warn("Could not make the knives folder", e);
		}
		return dir;
	}
	private void scanCustom() {
		try (var list = Files.list(folder())) {
			list.filter(Files::isDirectory)
					.filter(d -> Files.exists(d.resolve("model.json")) && Files.exists(d.resolve("texture.png")))
					.sorted()
					.forEach(d -> custom.put(d.getFileName().toString(), d));
		} catch (IOException e) {
			TempleNzClient.LOGGER.warn("Could not list your knives", e);
		}
	}
	@Override
	protected void onEnable() {
		holding = false;
		main = null;
		fading = null;
	}

	private Skin skin() {
		if (skin != null && knife.get().equals(skinName)) {
			return skin;
		}
		skinName = knife.get();
		skin = loaded.computeIfAbsent(skinName, this::load);
		return skin;
	}
	private Skin load(String name) {
		try {
			Path dir = custom.get(name);
			if (dir != null) {
				return loadCustom(name, dir);
			}
			for (String[] k : BUILT_IN) {
				if (k[0].equals(name)) {
					return loadBuiltIn(k[1], Integer.parseInt(k[2]));
				}
			}
		} catch (IOException | RuntimeException e) {
			TempleNzClient.LOGGER.warn("Could not load knife {}", name, e);
		}
		return null;
	}
	private static Skin loadBuiltIn(String id, int finish) throws IOException {
		JsonObject display = json(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "knives/display/" + id + ".json"));
		String model = after(display.get("model").getAsString());
		String anim = after(display.get("animation").getAsString());
		String texture = after(display.get("texture").getAsString());
		BedrockModel m = BedrockModel.parse(json(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "knives/models/" + model + ".json")));
		Map<String, BedrockAnimation> a = BedrockAnimation.parseAll(json(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID,
				"knives/animations/" + anim + ".animation.json")));
		return new Skin(m, a, Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "textures/knives/" + texture + ".png"), finish);
	}
	private static Skin loadCustom(String name, Path dir) throws IOException {
		BedrockModel m = BedrockModel.parse(JsonParser.parseString(Files.readString(dir.resolve("model.json"))).getAsJsonObject());
		Map<String, BedrockAnimation> a = Files.exists(dir.resolve("animation.json"))
				? BedrockAnimation.parseAll(JsonParser.parseString(Files.readString(dir.resolve("animation.json"))).getAsJsonObject())
				: Map.of();
		Identifier tex = Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "knife_custom/"
				+ Integer.toHexString(name.hashCode()));
		try (InputStream in = Files.newInputStream(dir.resolve("texture.png"))) {
			NativeImage image = NativeImage.read(in);
			Minecraft.getInstance().getTextureManager().register(tex, new DynamicTexture(() -> "Temple knife " + name, image));
		}
		return new Skin(m, a, tex, 2);
	}
	private static String after(String packId) {
		return packId.substring(packId.lastIndexOf('/') + 1);
	}
	private static JsonObject json(Identifier id) throws IOException {
		var res = Minecraft.getInstance().getResourceManager().getResource(id).orElseThrow(() -> new IOException("missing " + id));
		try (Reader r = res.openAsReader()) {
			return JsonParser.parseReader(r).getAsJsonObject();
		}
	}

	private boolean applies(ItemStack stack) {
		String p = Inv.path(stack);
		boolean sword = p.endsWith("_sword");
		return switch (useOn.get()) {
			case "Swords and axes" -> sword || p.endsWith("_axe");
			case "Any weapon" -> sword || p.endsWith("_axe") || p.equals("mace") || p.equals("trident") || p.endsWith("_spear");
			default -> sword;
		};
	}
	private void tick() {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) {
			return;
		}
		skin();
		if (!equip(p)) {
			return;
		}
		if (inspectEdge.pressed() && (main == null || !main.name.startsWith("melee"))) {
			play(pick("inspect"), 0.2f);
		}
	}
	private boolean equip(LocalPlayer p) {
		boolean now = applies(p.getMainHandItem());
		int slot = p.getInventory().getSelectedSlot();
		if (now && (!holding || slot != lastSlot)) {
			play(pick("draw"), 0);

			fading = null;
			baseStart = System.nanoTime();
		}
		holding = now;
		lastSlot = slot;
		return now;
	}
	public static void clicked() {
		Knives k = instance;
		LocalPlayer p = Minecraft.getInstance().player;
		if (k == null || !k.isEnabled() || p == null || !k.holding || !nz.temple.client.compat.Players.isSwinging(p)) {
			return;
		}
		boolean crit = !p.onGround() && p.fallDistance > 0 && !p.isInWater();
		String name = crit && k.has("melee_3") ? "melee_3" : "melee_" + (k.swingIndex + 1);
		k.swingIndex = (k.swingIndex + 1) % 2;
		k.play(name, 0.12f);
	}
	public static void showcase(String action) {
		Knives k = instance;
		if (k == null) {
			return;
		}
		if (action.equals("inspect")) {
			k.play(k.pick("inspect"), 0.2f);
		} else {
			k.play("melee_" + (k.swingIndex + 1), 0.12f);
			k.swingIndex = (k.swingIndex + 1) % 2;
		}
	}
	private boolean has(String name) {
		Skin s = skin();
		return s != null && s.anims.containsKey(name);
	}
	private String pick(String base) {
		Skin s = skin();
		if (s == null) {
			return base;
		}
		List<String> options = new ArrayList<>();
		if (s.anims.containsKey(base)) {
			options.add(base);
		}
		for (int i = 1; s.anims.containsKey(base + "_" + i); i++) {
			options.add(base + "_" + i);
		}
		return options.isEmpty() ? base : options.get(ThreadLocalRandom.current().nextInt(options.size()));
	}
	private void play(String name, float blend) {
		Skin s = skin();
		if (s == null) {
			return;
		}
		BedrockAnimation a = s.anims.get(name);
		if (a == null) {
			return;
		}
		if (main != null) {
			fading = main;
			fadeStart = System.nanoTime();
			fadeAt = Math.min(main.length, (fadeStart - mainStart) / 1e9f);
		}
		main = a;
		mainStart = System.nanoTime();
		mainBlend = blend;
		nextSound = 0;
	}
	public static boolean firstPerson(PoseStack pose, SubmitNodeCollector collector, int light, InteractionHand hand) {
		Knives k = instance;
		LocalPlayer p = Minecraft.getInstance().player;
		if (k == null || !k.isEnabled() || p == null || !k.applies(p.getMainHandItem())) {
			return false;
		}
		Skin s = k.skin();
		if (s == null) {
			return false;
		}
		if (hand == InteractionHand.OFF_HAND) {
			return p.getOffhandItem().isEmpty();
		}
		k.equip(p);
		k.draw(pose, collector, light, p, s);
		return true;
	}
	public static boolean thirdPerson(PoseStack pose, SubmitNodeCollector collector, int light, ItemStack stack) {
		Knives k = instance;
		if (k == null || !k.isEnabled() || !k.applies(stack)) {
			return false;
		}
		Skin s = k.skin();
		if (s == null) {
			return false;
		}
		BedrockModel m = s.model;
		int right = m.bone("righthand_pos");
		if (right < 0) {
			return false;
		}
		BedrockModel.Pose[] anim = poses(m.boneCount());
		BedrockAnimation still = s.anims.get("static_idle");
		if (still != null) {
			still.apply(m, 0, 1, anim);
		}
		BedrockModel.Posed posed = m.pose(anim);
		pose.pushPose();
		pose.translate(-1 / 16f, -0.125f, 0.625f);
		pose.mulPose(new Quaternionf().rotationY((float) Math.PI));
		pose.mulPose(new Quaternionf().rotationX((float) (Math.PI / 2)));
		pose.translate(5 / 16f, -10 / 16f, 0);
		pose.scale(-1, 1, 1);
		pose.mulPose(new Matrix4f(posed.pose()[right]).invert());
		drawKnife(pose, collector, light, s, posed, stack);
		pose.popPose();
		return true;
	}
	private static void drawKnife(PoseStack pose, SubmitNodeCollector collector, int light, Skin s, BedrockModel.Posed posed,
			ItemStack held) {
		BedrockModel m = s.model;
		if (instance.finish.is("Flat")) {
			collector.submitCustomGeometry(pose, RenderTypes.entityCutout(s.texture), (pp, consumer) ->
					m.draw(pp, posed, consumer, light, OverlayTexture.NO_OVERLAY, n -> n.endsWith("hand_pos")));
			return;
		}
		collector.submitCustomGeometry(pose, ChamsRenderTypes.knife(s.texture), (pp, consumer) ->
				m.draw(pp, posed, consumer, light, OverlayTexture.pack(s.finish, instance.materialOf(held)), n -> n.endsWith("hand_pos"),
						true));
	}
	private int materialOf(ItemStack held) {
		if (!material.is("Match sword")) {
			return 0;
		}
		String p = Inv.path(held);
		String kind = p.contains("_") ? p.substring(0, p.indexOf('_')) : p;
		return switch (kind) {
			case "wooden" -> 1;
			case "stone" -> 2;
			case "iron" -> 3;
			case "golden" -> 4;
			case "diamond" -> 5;
			case "netherite" -> 6;
			case "copper" -> 7;
			default -> 0;
		};
	}
	private void draw(PoseStack pose, SubmitNodeCollector collector, int light, LocalPlayer p, Skin s) {
		BedrockModel m = s.model;
		BedrockModel.Pose[] anim = animate(m, s);
		BedrockModel.Posed posed = m.pose(anim);
		pose.pushPose();
		pose.translate(0, height.getFloat(), -forward.getFloat());
		pose.scale(-1, 1, 1);
		int cam = m.bone("camera");
		if (cam >= 0) {
			BedrockModel.Pose c = anim[cam];
			pose.mulPose(new Quaternionf().rotationX((float) Math.toRadians(c.rx)));
			pose.mulPose(new Quaternionf().rotationY((float) Math.toRadians(-c.ry)));
			pose.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(c.rz)));
		}

		int view = m.bone("idle_view");
		if (view >= 0) {
			float[] pv = m.pivot(view);
			Matrix4f at = new Matrix4f(m.pose(null).pose()[view]).translate(pv[0] / 16f, pv[1] / 16f, pv[2] / 16f);
			pose.mulPose(at.invert());
		}
		boolean leftArm = p.getOffhandItem().isEmpty();
		drawKnife(pose, collector, light, s, posed, p.getMainHandItem());
		Identifier skinTex = p.getSkin().body().texturePath();
		boolean slim = p.getSkin().model() == PlayerModelType.SLIM;
		int right = m.bone("righthand_pos");
		int left = m.bone("lefthand_pos");
		float armLength = this.armLength.getFloat() / 100f;
		collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(skinTex), (pp, consumer) -> {
			PoseStack ps = new PoseStack();
			if (right >= 0) {
				BedrockModel.at(ps, pp, posed, right);
				arm(ps, consumer, true, slim, armLength, light);
			}
			if (left >= 0 && leftArm) {
				BedrockModel.at(ps, pp, posed, left);
				arm(ps, consumer, false, slim, armLength, light);
			}
		});
		pose.popPose();
		sounds();
	}
	private static void arm(PoseStack ps, com.mojang.blaze3d.vertex.VertexConsumer out, boolean right, boolean slim, float length, int light) {
		float w = slim ? 3 : 4;
		float x0 = right ? 4 : -4 - w;
		int[] base = right ? new int[] {40, 16} : new int[] {32, 48};
		int[] sleeve = right ? new int[] {40, 32} : new int[] {48, 48};
		int h = Math.max(3, Math.min(12, Math.round(12 * length)));
		BedrockModel.drawCube(ps, out, armCube(x0, w, h, 0, base[0], base[1]), 64, 64, light, OverlayTexture.NO_OVERLAY);
		BedrockModel.drawCube(ps, out, armCube(x0, w, h, 0.25f, sleeve[0], sleeve[1]), 64, 64, light, OverlayTexture.NO_OVERLAY);
	}
	private static JsonObject armCube(float x0, float w, int h, float grow, int u, int v) {
		JsonObject c = new JsonObject();
		c.add("origin", nums(x0 - grow, 20 - h - grow, -2 - grow));
		c.add("size", nums(w + 2 * grow, h + 2 * grow, 4 + 2 * grow));
		int iw = Math.round(w);

		int hand = v + 16;
		JsonObject f = new JsonObject();
		f.add("east", face(u, hand, 4, -h));
		f.add("north", face(u + 4, hand, iw, -h));
		f.add("west", face(u + 4 + iw, hand, 4, -h));
		f.add("south", face(u + 8 + iw, hand, iw, -h));
		f.add("up", face(u + 4 + 2 * iw, v, -iw, 4));
		f.add("down", face(u + 4 + iw, v + 4, -iw, -4));
		c.add("uv", f);
		return c;
	}
	private static JsonObject face(float u, float v, float w, float h) {
		JsonObject o = new JsonObject();
		o.add("uv", nums(u, v));
		o.add("uv_size", nums(w, h));
		return o;
	}
	private static JsonArray nums(float... values) {
		JsonArray a = new JsonArray();
		for (float x : values) {
			a.add(x);
		}
		return a;
	}
	private BedrockModel.Pose[] animate(BedrockModel m, Skin s) {
		int n = m.boneCount();
		BedrockModel.Pose[] out = poses(n);
		long now = System.nanoTime();
		BedrockAnimation still = s.anims.get("static_idle");
		if (still != null) {
			still.apply(m, loopTime(still, now - baseStart), 1, out);
		}
		if (main != null && (now - mainStart) / 1e9f > main.length) {
			fading = main;
			fadeStart = now;
			fadeAt = main.length;
			main = null;
		}
		if (fading != null) {
			float w = 1 - (now - fadeStart) / 1e9f / 0.15f;
			if (w <= 0) {
				fading = null;
			} else {
				over(m, out, fading, fadeAt, w);
			}
		}
		if (main != null) {
			float t = (now - mainStart) / 1e9f;
			float w = mainBlend <= 0 ? 1 : Math.min(1, t / mainBlend);
			over(m, out, main, t, w);
		}
		BedrockAnimation sway = s.anims.get("idle");
		if (sway != null) {
			sway.apply(m, loopTime(sway, now - baseStart), 1, out);
		}
		return out;
	}
	private static void over(BedrockModel m, BedrockModel.Pose[] out, BedrockAnimation a, float t, float w) {
		BedrockModel.Pose[] top = poses(out.length);
		a.apply(m, Math.min(t, a.length), 1, top);
		for (int i = 0; i < out.length; i++) {
			if (!a.moves(m.boneName(i))) {
				continue;
			}
			BedrockModel.Pose o = out[i];
			BedrockModel.Pose tp = top[i];
			if (w >= 1) {
				o.rx = tp.rx;
				o.ry = tp.ry;
				o.rz = tp.rz;
			} else if (w > 0) {
				Quaternionf q = euler(o.rx, o.ry, o.rz);
				q.slerp(euler(tp.rx, tp.ry, tp.rz), w);
				org.joml.Vector3f e = q.getEulerAnglesZYX(new org.joml.Vector3f());
				o.rx = (float) -Math.toDegrees(e.x);
				o.ry = (float) Math.toDegrees(e.y);
				o.rz = (float) -Math.toDegrees(e.z);
			}
			o.x += (tp.x - o.x) * w;
			o.y += (tp.y - o.y) * w;
			o.z += (tp.z - o.z) * w;
			o.sx += (tp.sx - o.sx) * w;
			o.sy += (tp.sy - o.sy) * w;
			o.sz += (tp.sz - o.sz) * w;
		}
	}
	private static Quaternionf euler(float rx, float ry, float rz) {
		return new Quaternionf().rotationZYX((float) Math.toRadians(-rz), (float) Math.toRadians(ry), (float) Math.toRadians(-rx));
	}
	private static BedrockModel.Pose[] poses(int n) {
		BedrockModel.Pose[] out = new BedrockModel.Pose[n];
		for (int i = 0; i < n; i++) {
			out[i] = new BedrockModel.Pose();
		}
		return out;
	}
	private static float loopTime(BedrockAnimation a, long elapsedNs) {
		float t = elapsedNs / 1e9f;
		return a.length > 0 ? t % a.length : 0;
	}
	private void sounds() {
		if (main == null || !sounds.get() || volume.get() <= 0) {
			return;
		}
		float t = (System.nanoTime() - mainStart) / 1e9f;
		List<BedrockAnimation.Sound> cues = main.sounds;
		while (nextSound < cues.size() && cues.get(nextSound).time() <= t) {
			String effect = cues.get(nextSound++).effect();
			String file = effect.substring(effect.lastIndexOf('/') + 1);
			Identifier id = Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "knife." + file);
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(id), 1f,
					volume.getFloat() / 100f));
		}
	}
}
