package nz.temple.client.modules.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import nz.temple.TempleNz;
import nz.temple.client.TempleNzClient;
import nz.temple.client.cosmetic.ModelCatalog;
import nz.temple.client.render.EspStateAccess;
import nz.temple.client.render.vrm.JigglePaint;
import nz.temple.client.render.vrm.VrmAnimation;
import nz.temple.client.render.vrm.VrmAvatar;
import nz.temple.client.render.vrm.VrmModel;
import nz.temple.client.render.vrm.VrmPreview;
import nz.temple.client.render.vrm.VrmRig;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.ActionSetting;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Replaces the player model with a full 3D avatar (VRM, the format VRoid Studio and most anime avatar sites use). The
 * avatar moves on its own from how the player moves: it walks, runs, sneaks, steps sideways and back, turns on the
 * spot, jumps, swims and flies with planted feet, and looks where the player looks, with hair and clothes that swing,
 * painted jiggle, and eyes that blink. Avatars come from the published list (downloaded the first time one is
 * picked, so nothing is in the jar) or from the player's own models folder (see {@link #README}).
 */
public final class CustomModels extends Module {
	private static CustomModels instance;

	/** The full guide on the site. Unlisted: only this button links to it. */
	static final String GUIDE = "https://temple.nz/avatars-na45ymbngl";

	static final String README = """
			Your own avatars
			================

			The full guide, with pictures: %s

			Custom Models replaces your player with a 3D avatar in VRM format, the format VRoid Studio exports and
			that most anime avatar sites (VRoid Hub, Booth) offer. Only you see it.

			Adding one
			----------
			Put a .vrm file in this folder, or a folder holding one:

			  My Avatar.vrm                 shows as "My Avatar"
			  My Avatar/
			    model.vrm                   (any name ending in .vrm)
			    idle.vrma                   optional custom animations, see below
			    walk.vrma

			Then press Reload in the module (or restart) and pick it in the Model list.

			Making one
			----------
			1. Download VRoid Studio (free, vroid.com/en/studio), make or open a character, and use
			   Export > Export as VRM. Keep the polygon count under about 40,000 for a smooth frame rate.
			2. Or download a .vrm from VRoid Hub or Booth. Check the model's conditions of use first: many
			   allow avatar use but not redistribution, and that is the author's call.
			3. Avatars made in Blender (with the VRM add-on) or Unity (with UniVRM) work too, as long as they
			   export as VRM 0.x or 1.0 with a humanoid skeleton.

			Custom animations
			-----------------
			Without any, the avatar moves on its own: it walks, runs, sneaks, steps sideways and back, turns on
			the spot and jumps with its feet planted on the ground, and looks where you look. To give it your own
			moves instead, add VRM Animation files (.vrma) named after when they play:

			  idle.vrma     standing still, looping
			  walk.vrma     walking, looping (blended in by how fast you move)
			  sprint.vrma   sprinting, used instead of walk
			  sneak.vrma    sneaking
			  fly.vrma      flying with an elytra or in creative
			  swim.vrma     swimming

			Make .vrma files in VRoid Studio's photo booth, with the free "VRM Animation" exporter for Blender, or
			convert Mixamo animations with a tool such as vrma-converter. The head always follows where you look,
			and attacks and mining always use the avatar's own swing.

			Size and look
			-------------
			Each avatar is sized from its own skeleton so its eyes are at a player's eye height: short and tall
			avatars both come out player-sized. Height changes that (in blocks; a player is 1.8). Held item size
			sets how big items and blocks are in its hands. Anime shading
			lights it flat like in VRoid; turn it off for Minecraft's lighting. Outline draws the thin dark edge anime avatars have.
			Physics lets hair, ears and skirts swing.

			Jiggle
			------
			Paint regions opens a preview of the avatar. Paint where it should bounce and sway (hair, clothes,
			accessories, anything): left drag paints, right drag turns the avatar, scroll zooms. The Mass brush
			pushes the surface out to add volume (Deflate takes it back); the jiggle moves the fuller shape. The spring
			settings set how firm, how quickly settled and how strong the motion is. Painting is kept for each
			avatar separately.
			""".formatted(GUIDE);

	/** One wearer's smoothed movement state and rig. */
	private static final class Wearer {
		VrmRig rig;
		float sneak;
		float fly;
		float swim;
		float walk;
		float sprint;
		float time;
		long seen;
		/** Where it was drawn last frame and how fast it moves (blocks a second), worked out frame to frame. */
		boolean placed;
		double px;
		double py;
		double pz;
		float vx;
		float vy;
		float vz;
		/** Whether the rig was posed yet, and the turn it was drawn with. */
		boolean updated;
		float yawOut;
		/** Where each hand (0 right, 1 left) holds things in the player model's space, as last drawn. */
		final boolean[] handOk = new boolean[2];
		final Vector3f[] palm = {new Vector3f(), new Vector3f()};
		final Quaternionf[] handTurn = {new Quaternionf(), new Quaternionf()};
		float itemScale = 1;
	}

	private final ModeSetting model;
	private final ModeSetting wornBy;
	private final NumberSetting height;
	private final NumberSetting itemSize;
	private final BoolSetting anime;
	private final BoolSetting outline;
	private final BoolSetting physics;
	private final BoolSetting hideArmor;
	private final BoolSetting hands;
	private final ModeSetting quality;
	private final BoolSetting jiggle;
	private final NumberSetting stiffness;
	private final NumberSetting damping;
	private final NumberSetting amount;
	private final NumberSetting brushSize;
	private final NumberSetting brushStrength;
	private final ModeSetting brush;
	private final BoolSetting mirror;

	/** The shown avatar's jiggle painting. */
	private JigglePaint jigglePaint;
	private String jiggleFor;

	/** Your own avatars: shown name to file, and their animation files by state. */
	private final Map<String, Path> local = new LinkedHashMap<>();
	private final Map<String, Map<String, Path>> localAnims = new HashMap<>();

	/** The avatar being shown, which choice it is for, and the one being loaded. */
	private VrmAvatar avatar;
	private Map<String, VrmAnimation> clips = Map.of();
	private String shown;
	private String loading;
	private int loadToken;
	private final Map<Integer, Wearer> wearers = new HashMap<>();
	/** The avatar posed for the first-person arms, apart from every wearer's own. */
	private VrmRig handRig;
	private static final nz.temple.util.Perf.Section TIMER = nz.temple.util.Perf.section("avatar");

	public CustomModels() {
		super("Custom Models", Category.COSMETICS, "Replaces your player with a 3D avatar (VRM, as made in VRoid Studio). Pick one from "
				+ "the list or add your own to the models folder.");
		instance = this;
		scanLocal();
		model = add(ModeSetting.open("Model", names(), "Sendagaya Shino")
				.describe("The avatar to wear. Listed avatars download the first time you pick one. Your own .vrm files from the "
						+ "models folder are listed after them."));
		wornBy = add(new ModeSetting("Worn by", List.of("You", "Everyone"), "You")
				.describe("Who wears the avatar on your screen. Only you see it either way."));
		height = add(new NumberSetting("Height", 1.8, 0.8, 3, 0.05, " b").describe("How tall the avatar stands, in blocks. 1.8 matches the "
				+ "player: the avatar's eyes sit where the player's are, whatever size it was made."));
		itemSize = add(new NumberSetting("Held item size", 60, 25, 100, 5, "%").describe("How big items and blocks are in the avatar's "
				+ "hands, compared with the normal player's. Scales with Height."));
		anime = add(new BoolSetting("Anime shading", true).describe("Lights the avatar evenly, the way it looks in VRoid. Off uses "
				+ "Minecraft's lighting."));
		outline = add(new BoolSetting("Outline", true).describe("Draws the thin dark edge anime avatars have, where the avatar asks for one."));
		physics = add(new BoolSetting("Physics", true).describe("Lets hair, ears, tails and skirts swing as you move."));
		hideArmor = add(new BoolSetting("Hide armor and cape", true).describe("Hides armor and capes, which are shaped for the normal "
				+ "player and float around an avatar."));
		hands = add(new BoolSetting("First-person hands", false).describe("Shows the avatar's own arms and hands in first person "
				+ "instead of the normal ones."));
		quality = add(new ModeSetting("Texture quality", List.of("Low", "Medium", "High"), "Medium")
				.describe("Texture sharpness. Low saves video memory on weaker computers; High is sharpest."));
		add(new ActionSetting("Your avatars", "Open folder", () -> nz.temple.client.compat.Platform.openPath(folder())));
		add(new ActionSetting("How to add your own", "Open guide", () -> nz.temple.client.compat.Platform.openUri(GUIDE))
				.describe("Opens the guide in your browser: making an avatar in VRoid Studio, adding it here, and giving it custom "
						+ "animations."));
		add(new ActionSetting("Reload", "Reload", this::reload).describe("Reads the models folder and the published list again, "
				+ "for an avatar you just added or changed."));
		group("Jiggle");
		jiggle = add(new BoolSetting("Jiggle", true).describe("Lets the parts you painted bounce and sway as you move, on top of the "
				+ "avatar's own hair and skirt physics."));
		add(new ActionSetting("Jiggle regions", "Paint", this::openPainter).describe("Opens a preview of the avatar to paint where it "
				+ "jiggles: hair, clothes, accessories or the body. Left drag paints, right drag turns the avatar, scroll zooms."));
		stiffness = add(new NumberSetting("Stiffness", 45, 0, 100, 1, "%").describe("How firmly painted parts spring back. Low is loose "
				+ "and slow, high is tight and quick."));
		damping = add(new NumberSetting("Damping", 35, 0, 100, 1, "%").describe("How quickly the motion settles. Low keeps bouncing, "
				+ "high stops after one swing."));
		amount = add(new NumberSetting("Amount", 100, 0, 200, 5, "%").describe("How far painted parts move."));
		brush = add(new ModeSetting("Brush", List.of("Jiggle", "Erase", "Mass", "Deflate"), "Jiggle").describe("Jiggle paints where the "
				+ "avatar jiggles and Erase takes it away. Mass pushes the surface out to add volume, and Deflate takes it back."));
		brushSize = add(new NumberSetting("Brush size", 18, 4, 80, 1, " px").describe("The brush's radius in the preview."));
		brushStrength = add(new NumberSetting("Brush strength", 50, 5, 100, 5, "%").describe("How much each stroke adds or removes."));
		mirror = add(new BoolSetting("Mirror", true).describe("Paints both sides of the avatar at once."));
		add(new ActionSetting("Clear jiggle", "Clear", () -> {
			if (jigglePaint != null) {
				jigglePaint.clear();
				saveJiggle();
			}
		}).describe("Removes all jiggle painting from the shown avatar."));
		add(new ActionSetting("Clear mass", "Clear", () -> {
			if (jigglePaint != null) {
				jigglePaint.clearMass();
				saveJiggle();
			}
		}).describe("Removes all added mass from the shown avatar."));
		stiffness.addListener(this::springFromSettings);
		damping.addListener(this::springFromSettings);
		amount.addListener(this::springFromSettings);
		model.addListener(() -> shown = null);
		quality.addListener(() -> shown = null);
		ModelCatalog.refresh().thenAccept(list -> Minecraft.getInstance().execute(() -> model.setOptions(names())));
	}

	public static CustomModels instance() {
		return instance;
	}

	private static Path folder() {
		Path dir = FabricLoader.getInstance().getConfigDir().resolve(TempleNz.MOD_ID).resolve("models");
		try {
			Files.createDirectories(dir);
			Path readme = dir.resolve("README.txt");
			if (!Files.exists(readme) || !Files.readString(readme, StandardCharsets.UTF_8).equals(README)) {
				Files.writeString(readme, README, StandardCharsets.UTF_8);
			}
		} catch (IOException e) {
			TempleNzClient.LOGGER.warn("Could not make the models folder", e);
		}
		return dir;
	}

	private List<String> names() {
		List<String> out = new ArrayList<>();
		for (ModelCatalog.Entry e : ModelCatalog.entries()) {
			out.add(e.name());
		}
		for (String n : local.keySet()) {
			if (!out.contains(n)) {
				out.add(n);
			}
		}
		return out;
	}

	private void scanLocal() {
		local.clear();
		localAnims.clear();
		try (var list = Files.list(folder())) {
			for (Path p : list.sorted().toList()) {
				String file = p.getFileName().toString();
				if (file.startsWith(".")) {
					continue;
				}
				if (Files.isRegularFile(p) && file.toLowerCase(Locale.ROOT).endsWith(".vrm")) {
					local.put(file.substring(0, file.length() - 4), p);
				} else if (Files.isDirectory(p)) {
					Path vrm = null;
					Map<String, Path> anims = new HashMap<>();
					try (var inner = Files.list(p)) {
						for (Path f : inner.sorted().toList()) {
							String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
							if (n.endsWith(".vrm") && vrm == null) {
								vrm = f;
							} else if (n.endsWith(".vrma")) {
								anims.put(n.substring(0, n.length() - 5), f);
							}
						}
					}
					if (vrm != null) {
						local.put(file, vrm);
						localAnims.put(file, anims);
					}
				}
			}
		} catch (IOException e) {
			TempleNzClient.LOGGER.warn("Could not list your avatars", e);
		}
	}

	private void reload() {
		scanLocal();
		model.setOptions(names());
		shown = null;
		ModelCatalog.refresh().thenAccept(list -> Minecraft.getInstance().execute(() -> model.setOptions(names())));
	}

	@Override
	protected void onDisable() {
		wearers.clear();
	}

	// ---- loading ------------------------------------------------------------------------------------------------

	private int maxTexture() {
		return quality.is("Low") ? 512 : quality.is("High") ? 2048 : 1024;
	}

	/** Starts loading the chosen avatar when it is not the one shown; the old one stays until the new one is ready. */
	private void ensureLoaded() {
		String want = model.get();
		if (want.equals(shown) || want.equals(loading)) {
			return;
		}
		loading = want;
		int token = ++loadToken;
		int max = maxTexture();
		CompletableFuture<Path> file;
		Map<String, CompletableFuture<Path>> animFiles = new HashMap<>();
		Path own = local.get(want);
		ModelCatalog.Entry entry = own == null ? ModelCatalog.byName(want) : null;
		if (own != null) {
			file = CompletableFuture.completedFuture(own);
			for (Map.Entry<String, Path> a : localAnims.getOrDefault(want, Map.of()).entrySet()) {
				animFiles.put(a.getKey(), CompletableFuture.completedFuture(a.getValue()));
			}
		} else if (entry != null) {
			boolean have;
			try {
				have = Files.exists(ModelCatalog.file(entry.model(), ".vrm"));
			} catch (IOException e) {
				have = false;
			}
			if (!have) {
				notify("Downloading avatar", want + " is downloading. It shows when it is ready.");
			}
			file = ModelCatalog.fetch(entry.model(), ".vrm", p -> { });
			for (Map.Entry<String, ModelCatalog.Asset> a : entry.animations().entrySet()) {
				animFiles.put(a.getKey(), ModelCatalog.fetch(a.getValue(), ".vrma", p -> { }));
			}
		} else {
			loading = null;
			shown = want;
			return;
		}
		file.thenApplyAsync(path -> {
			try {
				VrmModel m = VrmModel.read(Files.readAllBytes(path), max);
				Map<String, VrmAnimation> anims = new HashMap<>();
				for (Map.Entry<String, CompletableFuture<Path>> a : animFiles.entrySet()) {
					try {
						anims.put(a.getKey(), VrmAnimation.read(Files.readAllBytes(a.getValue().join())));
					} catch (IOException | RuntimeException e) {
						TempleNzClient.LOGGER.warn("Could not read the {} animation of {}", a.getKey(), want, e);
					}
				}
				return Map.entry(m, anims);
			} catch (IOException e) {
				throw new java.util.concurrent.CompletionException(e);
			}
		}).whenComplete((loaded, error) -> Minecraft.getInstance().execute(() -> {
			if (token != loadToken) {
				if (loaded != null) {
					loaded.getKey().closeTextures();
				}
				return;
			}
			loading = null;
			shown = want;
			if (error != null) {
				TempleNzClient.LOGGER.warn("Could not load avatar {}", want, error);
				Throwable cause = error.getCause() != null ? error.getCause() : error;
				notify("Avatar not loaded", want + ": " + cause.getMessage());
				return;
			}
			if (avatar != null) {
				avatar.close();
			}
			avatar = VrmAvatar.upload(loaded.getKey());
			clips = loaded.getValue();
			wearers.clear();
			loadJiggle(want);
		}));
	}

	private static void notify(String title, String text) {
		if (TempleNzClient.notifications() != null) {
			TempleNzClient.notifications().info(title, text);
		}
	}

	// ---- drawing ------------------------------------------------------------------------------------------------

	/** Whether {@code state}'s player is drawn as an avatar instead of the normal model. */
	public static boolean replaces(AvatarRenderState state) {
		CustomModels m = instance;
		if (m == null || !m.isEnabled()) {
			return false;
		}
		m.ensureLoaded();
		return m.avatar != null && (m.wornBy.is("Everyone") || ((EspStateAccess) state).temple$mine());
	}

	/** Whether armor and capes are hidden on {@code state}'s player. */
	public static boolean hidesArmor(AvatarRenderState state) {
		return replaces(state) && instance.hideArmor.get();
	}

	/** Called by the player render layer for each player as it is drawn. */
	public static void submit(PlayerModel parent, AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector, int light) {
		CustomModels m = instance;
		if (state.isInvisible || !replaces(state)) {
			return;
		}
		m.draw(parent, state, pose, collector, light);
	}

	private void draw(PlayerModel parent, AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector, int light) {
		long now = System.nanoTime();
		Wearer w = wearers.get(state.id);
		if (w == null || w.rig == null || w.rig.avatar() != avatar) {
			w = new Wearer();
			w.rig = new VrmRig(avatar);
			w.seen = now;
			wearers.put(state.id, w);
		}
		float dt = Math.min(0.1f, (now - w.seen) / 1e9f);
		// Drawn again in the same frame (another render pass): reuse this frame's pose rather than stepping the
		// animation and physics by almost no time.
		boolean again = w.rig != null && dt < 0.0015f && w.updated;
		if (!again) {
			w.seen = now;
		}
		if (wearers.size() > 32) {
			wearers.values().removeIf(x -> now - x.seen > 5_000_000_000L);
		}
		float ease = 1 - (float) Math.exp(-5 * dt);
		w.walk += (Math.min(1, state.walkAnimationSpeed) - w.walk) * ease;
		w.sprint += ((state.walkAnimationSpeed > 0.9f ? 1 : 0) - w.sprint) * ease;
		w.sneak += ((state.isCrouching ? 1 : 0) - w.sneak) * ease;
		w.fly += ((state.isFallFlying ? 1 : 0) - w.fly) * ease;
		w.swim += ((state.isVisuallySwimming ? 1 : 0) - w.swim) * ease;
		if (!again) {
			w.time += dt;
		}
		// How fast the wearer moves, from where it is drawn frame to frame (drawn positions are smooth between ticks).
		if (dt > 1e-4f && !again) {
			double dx = state.x - w.px;
			double dy = state.y - w.py;
			double dz = state.z - w.pz;
			if (!w.placed || dx * dx + dy * dy + dz * dz > 16) {
				w.vx = w.vy = w.vz = 0;
			} else {
				float v = 1 - (float) Math.exp(-14 * dt);
				w.vx += ((float) (dx / dt) - w.vx) * v;
				w.vy += ((float) (dy / dt) - w.vy) * v;
				w.vz += ((float) (dz / dt) - w.vz) * v;
			}
			w.placed = true;
			w.px = state.x;
			w.py = state.y;
			w.pz = state.z;
		}

		Minecraft mc = Minecraft.getInstance();
		net.minecraft.world.entity.Entity entity = mc.level != null ? mc.level.getEntity(state.id) : null;
		VrmRig.Drive d = new VrmRig.Drive();
		ModelPart[] parts = {parent.head, parent.body, parent.rightArm, parent.leftArm, parent.rightLeg, parent.leftLeg};
		for (int i = 0; i < parts.length; i++) {
			d.parts[i][0] = parts[i].xRot;
			d.parts[i][1] = parts[i].yRot;
			d.parts[i][2] = parts[i].zRot;
		}
		//? if >=26.3 {
		/*d.attacking = state.swingAnimation > 0;
		d.swing = state.swingAnimation;
		boolean offSwing = state.currentSwing != null && state.currentSwing.hand() == net.minecraft.world.InteractionHand.OFF_HAND;
		d.swingSide = (state.mainArm == net.minecraft.world.entity.HumanoidArm.LEFT) != offSwing ? 1 : 0;
		*///?} else {
		d.attacking = state.attackTime > 0;
		d.swing = state.attackTime;
		d.swingSide = state.attackArm == net.minecraft.world.entity.HumanoidArm.LEFT ? 1 : 0;
		//?}
		d.busy[0] = busy(state.rightArmPose);
		d.busy[1] = busy(state.leftArmPose);
		d.holding[0] = !state.rightHandItemStack.isEmpty();
		d.holding[1] = !state.leftHandItemStack.isEmpty();
		d.time = w.time;
		d.dt = dt;
		d.x = state.x;
		d.y = state.y;
		d.z = state.z;
		d.vx = w.vx;
		d.vy = w.vy;
		d.vz = w.vz;
		d.bodyYaw = state.bodyRot;
		d.headYaw = state.bodyRot + state.yRot;
		d.pitch = state.xRot;
		d.grounded = entity == null || entity.onGround();
		d.flying = entity instanceof net.minecraft.world.entity.player.Player p && p.getAbilities().flying;
		d.sneaking = state.isCrouching;
		d.swimming = state.isVisuallySwimming;
		d.fallFlying = state.isFallFlying;
		d.passenger = state.isPassenger;
		d.sleeping = state.bedOrientation != null;
		d.inWater = state.isInWater;
		if (mc.level != null && mc.player != null && !d.swimming) {
			var level = mc.level;
			var viewer = mc.player;
			d.ground = (gx, gy, gz) -> ground(level, viewer, gx, gy, gz);
		}
		// Sized from its own skeleton so its eyes sit where a player's are (1.62 of 1.8 blocks, scaled to Height),
		// whether it was made short or tall. The player model is drawn at 15/16 scale; the avatar is sized in blocks.
		float k = height.getFloat() * (1.62f / 1.8f) / Math.max(0.2f, w.rig.eyeHeight()) / 0.9375f;
		d.scale = k * 0.9375f;
		d.anime = anime.get();
		d.outline = outline.get();
		d.physics = physics.get();
		d.jiggle = jigglePaint;
		d.jiggleMotion = jiggle.get();
		// Custom animations, if the avatar has them, by how much each state applies now.
		float special = Math.max(w.sneak * has("sneak"), Math.max(w.fly * has("fly"), w.swim * has("swim")));
		// Walk and sprint cross-fade; with only one of them, it covers both.
		float run = clips.containsKey("sprint") ? clips.containsKey("walk") ? w.sprint : 1 : 0;
		float moving = Math.max(has("walk"), has("sprint")) * w.walk * (1 - special);
		put(d, "idle", has("idle") * Math.max(0, 1 - moving - special));
		put(d, "walk", has("walk") * moving * (1 - run));
		put(d, "sprint", has("sprint") * moving * run);
		put(d, "sneak", has("sneak") * w.sneak);
		put(d, "fly", has("fly") * w.fly);
		put(d, "swim", has("swim") * w.swim);

		long began = TIMER.begin();
		if (again) {
			d.yawOut = w.yawOut;
		} else {
			w.rig.update(d);
			w.yawOut = d.yawOut;
			w.updated = true;
		}
		// From the player model's space (y down, at the neck) to the avatar's (y up, feet at the origin). A sneaking
		// player is drawn 2/16 lower; the avatar crouches on its own legs instead, so its feet stay on the ground. Then
		// soles on the ground, and the avatar's own turn (standing, it only turns once the head is turned far). VRM 1.0
		// avatars face the other way from VRM 0.x and from the player.
		Matrix4f toModel = new Matrix4f().translate(0, 1.501f - (state.isCrouching ? 0.125f / 0.9375f : 0), 0).scale(-k, -k, k)
				.translate(0, -w.rig.soleY(), 0).rotateY(d.yawOut * Mth.DEG_TO_RAD);
		if (avatar.vrm1()) {
			toModel.rotateY(Mth.PI);
		}
		hands(w, toModel);
		pose.pushPose();
		parent.root().translateAndRotate(pose);
		pose.mulPose(toModel);
		w.rig.submit(d, pose, collector, light, LivingEntityRenderer.getOverlayCoords(state, 0f));
		pose.popPose();
		TIMER.end(began);
		if (((EspStateAccess) state).temple$self()) {
			lastJiggleReach = w.rig.jiggleReach();
			lastSeamGap = w.rig.seamGap(jigglePaint);
		}
	}

	private float lastJiggleReach;
	private float lastSeamGap;

	/** Works out where each hand holds things, in the player model's space, for {@link #translateToHand}. */
	private void hands(Wearer w, Matrix4f toModel) {
		Vector3f along = new Vector3f();
		Vector3f thumb = new Vector3f();
		for (int i = 0; i < 2; i++) {
			Vector3f palm = w.palm[i];
			w.handOk[i] = w.rig.handFrame(i, palm, along, thumb);
			if (!w.handOk[i]) {
				continue;
			}
			toModel.transformPosition(palm);
			toModel.transformDirection(along).normalize();
			toModel.transformDirection(thumb).normalize();
			// The player's arm, hanging, runs down its +y to the hand with the thumb toward -z (forward).
			Vector3f back = new Vector3f(thumb).negate();
			Vector3f x = new Vector3f(along).cross(back);
			new Matrix3f().setColumn(0, x).setColumn(1, along).setColumn(2, back).getNormalizedRotation(w.handTurn[i]);
		}
		w.itemScale = itemSize.getFloat() / 100f * height.getFloat() / 1.8f;
	}

	/**
	 * Called in place of the player model's hand placement for held items: moves to the avatar's palm, turned the way
	 * its hand is, and scales the item to it. Returns false to leave it to the player model (no avatar, or no hands).
	 */
	public static boolean translateToHand(PlayerModel parent, AvatarRenderState state, net.minecraft.world.entity.HumanoidArm arm, PoseStack pose) {
		CustomModels m = instance;
		if (state.isInvisible || !replaces(state)) {
			return false;
		}
		Wearer w = m.wearers.get(state.id);
		int i = arm == net.minecraft.world.entity.HumanoidArm.LEFT ? 1 : 0;
		if (w == null || !w.handOk[i]) {
			return false;
		}
		parent.root().translateAndRotate(pose);
		Vector3f palm = w.palm[i];
		pose.translate(palm.x, palm.y, palm.z);
		pose.mulPose(new Matrix4f().rotation(w.handTurn[i]));
		pose.scale(w.itemScale, w.itemScale, w.itemScale);
		// The item layer then moves to where the player's grip is, 10/16 down the arm, 2/16 forward and 1/16 in.
		pose.translate(i == 0 ? 1 / 16f : -1 / 16f, -10 / 16f, 2 / 16f);
		return true;
	}

	/**
	 * Called in place of the first-person arm: draws the avatar's own arm and hand where the normal arm is. Sized as a
	 * 1.8 block avatar whatever its Height, so the arm sits where the normal one does. False leaves the normal arm.
	 */
	public static boolean drawFirstPersonArm(boolean right, ModelPart arm, PoseStack pose, SubmitNodeCollector collector, int light) {
		CustomModels m = instance;
		if (m == null || !m.isEnabled() || !m.hands.get()) {
			return false;
		}
		m.ensureLoaded();
		if (m.avatar == null) {
			return false;
		}
		if (m.handRig == null || m.handRig.avatar() != m.avatar) {
			m.handRig = new VrmRig(m.avatar);
		}
		// A little larger than life, as the normal first-person arm is.
		float scale = 1.35f * 1.62f / Math.max(0.2f, m.handRig.eyeHeight());
		pose.pushPose();
		arm.resetPose();
		arm.zRot = right ? 0.1f : -0.1f;
		arm.translateAndRotate(pose);
		// The wrist goes where the normal arm's hand starts, 6.5/16 down from the shoulder, in the middle of the arm.
		boolean drawn = m.handRig.submitArm(right ? 0 : 1, pose, collector, light,
				net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, m.anime.get(), m.outline.get(), scale,
				right ? -1 / 16f : 1 / 16f, 6.5f / 16f);
		pose.popPose();
		return drawn;
	}

	/** Whether an arm pose holds something up (a bow, shield, crossbow, spyglass or food), not just carries it. */
	private static boolean busy(net.minecraft.client.model.HumanoidModel.ArmPose arm) {
		return arm != null && arm != net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY
				&& arm != net.minecraft.client.model.HumanoidModel.ArmPose.ITEM;
	}

	/** The ground's height under (x, z) relative to {@code y}, looking from a little above to a little below. */
	private static float ground(net.minecraft.world.level.Level level, net.minecraft.world.entity.Entity viewer, double x, double y, double z) {
		var from = new net.minecraft.world.phys.Vec3(x, y + 0.6, z);
		var to = new net.minecraft.world.phys.Vec3(x, y - 0.6, z);
		var hit = level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, viewer));
		return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? Float.NaN : (float) (hit.getLocation().y - y);
	}

	// ---- jiggle -------------------------------------------------------------------------------------------------

	private static Path jiggleFile(String avatarName) {
		String safe = avatarName.replaceAll("[^A-Za-z0-9 ._-]", "_");
		return folder().resolve(".jiggle").resolve(safe + ".json");
	}

	private boolean loadingSprings;

	private void loadJiggle(String name) {
		jigglePaint = new JigglePaint(avatar);
		jiggleFor = name;
		Path f = jiggleFile(name);
		if (Files.exists(f)) {
			try {
				jigglePaint.fromJson(com.google.gson.JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject());
			} catch (IOException | RuntimeException e) {
				TempleNzClient.LOGGER.warn("Could not read the jiggle painting of {}", name, e);
			}
		}
		loadingSprings = true;
		stiffness.setQuietly((double) Math.round(jigglePaint.stiffness * 100));
		damping.setQuietly((double) Math.round(jigglePaint.damping * 100));
		amount.setQuietly((double) Math.round(jigglePaint.amount * 100));
		loadingSprings = false;
	}

	private void springFromSettings() {
		if (loadingSprings || jigglePaint == null) {
			return;
		}
		jigglePaint.stiffness = stiffness.getFloat() / 100f;
		jigglePaint.damping = damping.getFloat() / 100f;
		jigglePaint.amount = amount.getFloat() / 100f;
		saveJiggle();
	}

	/** The painter changed the spring: shows it in the settings and saves it. */
	public void jiggleSpringChanged() {
		if (jigglePaint == null) {
			return;
		}
		loadingSprings = true;
		stiffness.setQuietly((double) Math.round(jigglePaint.stiffness * 100));
		damping.setQuietly((double) Math.round(jigglePaint.damping * 100));
		amount.setQuietly((double) Math.round(jigglePaint.amount * 100));
		loadingSprings = false;
		saveJiggle();
	}

	/** Saves the shown avatar's jiggle painting. */
	public void saveJiggle() {
		if (jigglePaint == null || jiggleFor == null) {
			return;
		}
		try {
			Path f = jiggleFile(jiggleFor);
			Files.createDirectories(f.getParent());
			Files.writeString(f, jigglePaint.toJson().toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			TempleNzClient.LOGGER.warn("Could not save the jiggle painting of {}", jiggleFor, e);
		}
	}

	private void openPainter() {
		Minecraft mc = Minecraft.getInstance();
		if (avatar == null || jigglePaint == null) {
			notify("No avatar yet", "Turn on Custom Models and wait for the avatar to load, then paint it.");
			return;
		}
		mc.gui.setScreen(new nz.temple.client.gui.JiggleEditorScreen(mc.gui.screen(), this, jigglePaint, new VrmPreview(avatar, jigglePaint)));
	}

	public VrmPreview.Tool jiggleTool() {
		return brush.is("Erase") ? VrmPreview.Tool.ERASE : brush.is("Mass") ? VrmPreview.Tool.MASS
				: brush.is("Deflate") ? VrmPreview.Tool.DEFLATE : VrmPreview.Tool.JIGGLE;
	}

	public void setJiggleTool(VrmPreview.Tool tool) {
		brush.set(switch (tool) {
			case ERASE -> "Erase";
			case MASS -> "Mass";
			case DEFLATE -> "Deflate";
			default -> "Jiggle";
		});
	}

	public boolean jiggleMirror() {
		return mirror.get();
	}

	public void setJiggleMirror(boolean on) {
		mirror.set(on);
	}

	public float jiggleBrushSize() {
		return brushSize.getFloat();
	}

	public float jiggleBrushStrength() {
		return brushStrength.getFloat() / 100f;
	}

	public void nudgeJiggleBrush(float size, float strength) {
		brushSize.set(Math.max(brushSize.min(), Math.min(brushSize.max(), brushSize.get() + size)));
		brushStrength.set(Math.max(brushStrength.min(), Math.min(brushStrength.max(), brushStrength.get() + strength * 100)));
	}

	/** For the showcase: the widest gap between copies of one painted point on your own avatar (0 is sealed). */
	public static float showcaseSeamGap() {
		return instance == null ? 0 : instance.lastSeamGap;
	}

	/** For the showcase: how far your own avatar's painted jiggle is pushed right now, in avatar units. */
	public static float showcaseJiggleReach() {
		return instance == null ? 0 : instance.lastJiggleReach;
	}

	private float has(String clip) {
		return clips.containsKey(clip) ? 1 : 0;
	}

	private void put(VrmRig.Drive d, String clip, float weight) {
		VrmAnimation a = clips.get(clip);
		if (a != null && weight > 0.001f) {
			d.clips.put(clip, a);
			d.weights.put(clip, weight);
		}
	}
}
