package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import nz.temple.TempleNz;
import nz.temple.client.TempleNzClient;
import nz.temple.client.audio.AudioFiles;
import nz.temple.client.audio.Pcm;
import nz.temple.client.audio.SoundOut;
import nz.temple.client.util.FilePicker;
import nz.temple.client.util.HitConfirm;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.ActionSetting;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.FileSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class HitSounds extends Module {
	private static final String CUSTOM = "Custom";
	private static final String ORB = "Orb";
	private static final List<String> PRESETS = List.of("Pop", "Punch", "Crit", "Tick", "Thud", "Snap", "Crunch", "Coin", "Ding", "Bubble");
	private final ModeSetting sound;
	private final FileSetting file;
	private final NumberSetting trimStart;
	private final NumberSetting trimLength;
	private final NumberSetting volume;
	private final NumberSetting pitch;
	private final NumberSetting variation;
	private final BoolSetting combo;
	private final ModeSetting killSound;
	private volatile Pcm custom;
	private volatile String customError;
	private volatile boolean loading;
	private Path loadedPath;
	private int comboCount;
	private long lastHit;

	public HitSounds() {
		super("Hit Sounds", Category.COSMETICS, "Plays a sound when a hit lands and when the target dies. Use a built-in sound or your own "
				+ "MP3, trimmed to the part you want.");
		List<String> sounds = new ArrayList<>(PRESETS);
		sounds.add(ORB);
		sounds.add(CUSTOM);
		sound = add(new ModeSetting("Sound", sounds, "Pop")
				.describe("Sound for a landed hit. Orb is Minecraft's experience pickup sound. Custom plays your own file."));
		file = add(new FileSetting("Sound file", List.of("mp3", "ogg", "wav"))
				.describe("Your own MP3, OGG or WAV file. Only the first 30 seconds are loaded; trim it to the part you want.")
				.visibleWhen(() -> sound.is(CUSTOM)));
		add(new ActionSetting("Trim", () -> custom == null ? (loading ? "Loading…" : "Choose a file first") : "Open editor", () -> {
			Minecraft mc = Minecraft.getInstance();
			mc.execute(() -> {
				if (custom != null) {
					mc.gui.setScreen(new nz.temple.client.gui.HitSoundEditor(mc.gui.screen(), this));
				} else if (file.isDefault()) {
					FilePicker.choose(file);
				}
			});
		}).describe("Opens the trim editor. Drag the handles on the waveform to choose which part plays.")
				.visibleWhen(() -> sound.is(CUSTOM)));
		trimStart = add(new NumberSetting("Trim start", 0, 0, AudioFiles.MAX_SECONDS, 0.01, " s")
				.describe("Start point in your file.").visibleWhen(() -> sound.is(CUSTOM)));
		trimLength = add(new NumberSetting("Trim length", 0.4, 0.03, 5, 0.01, " s")
				.describe("How much of the file plays from the start point.").visibleWhen(() -> sound.is(CUSTOM)));
		group();
		volume = add(new NumberSetting("Volume", 70, 5, 150, 5, "%").describe("Volume, relative to Minecraft's master volume."));
		pitch = add(new NumberSetting("Pitch", 100, 50, 200, 5, "%").describe("Raises or lowers the pitch."));
		variation = add(new NumberSetting("Pitch variation", 6, 0, 25, 1, "%")
				.describe("Varies the pitch slightly each hit so rapid hits sound less repetitive."));
		combo = add(new BoolSetting("Combo pitch", false)
				.describe("Each hit in a quick combo plays slightly higher than the last. Resets after 1.5 seconds."));
		List<String> kills = new ArrayList<>(List.of("Same, lower", "Same", "Off"));
		kills.addAll(PRESETS);
		kills.add(ORB);
		killSound = add(new ModeSetting("Kill sound", kills, "Same, lower")
				.describe("Sound when your target dies. Same, lower plays your hit sound at a lower pitch."));
		add(new ActionSetting("Preview", "Play", () -> play(false)).describe("Plays the current hit sound."));
		file.addListener(this::reload);
		HitConfirm.listen(new HitConfirm.Listener() {
			@Override
			public void hit(LivingEntity target, Vec3 offset) {
				if (isEnabled()) {
					long now = System.currentTimeMillis();
					comboCount = now - lastHit < 1500 ? Math.min(comboCount + 1, 8) : 0;
					lastHit = now;
					play(false);
				}
			}
			@Override
			public void kill(Entity target, int entityId, Vec3 offset) {
				if (isEnabled()) {
					comboCount = 0;
					play(true);
				}
			}
		});
	}
	@Override
	protected void onEnable() {
		reload();
	}
	private void reload() {
		Path path = FilePicker.path(file);
		if (path == null) {
			custom = null;
			customError = null;
			loadedPath = null;
			return;
		}
		if (path.equals(loadedPath) && custom != null) {
			return;
		}
		loadedPath = path;
		loading = true;
		Thread worker = new Thread(() -> {
			try {
				Pcm pcm = AudioFiles.load(path);
				custom = pcm;
				customError = null;
				Minecraft.getInstance().execute(() -> {
					if (trimStart.get() >= pcm.seconds()) {
						trimStart.set(0.0);
					}
				});
			} catch (Exception e) {
				custom = null;
				customError = e.getMessage();
				Minecraft.getInstance().execute(() -> {
					if (TempleNzClient.notifications() != null) {
						TempleNzClient.notifications().info("Hit sound", "Could not read " + path.getFileName() + ": " + e.getMessage());
					}
				});
			} finally {
				loading = false;
			}
		}, "Temple hit sound loader");
		worker.setDaemon(true);
		worker.start();
	}
	public Pcm customSound() {
		return custom;
	}
	public String customError() {
		return customError;
	}
	public FileSetting fileSetting() {
		return file;
	}
	public String fileName() {
		return file.fileName();
	}
	public double trimStart() {
		return trimStart.get();
	}
	public double trimLength() {
		return trimLength.get();
	}
	public double maxTrimLength() {
		return trimLength.max();
	}
	public void setTrim(double start, double length) {
		trimStart.set(Math.max(0, start));
		trimLength.set(Math.max(trimLength.min(), Math.min(trimLength.max(), length)));
	}
	public SoundOut.Voice previewTrim() {
		Pcm pcm = custom;
		return pcm == null ? null : SoundOut.play(pcm, trimStart.get(), trimStart.get() + trimLength.get(),
				volume.getFloat() / 100f, pitch.getFloat() / 100f);
	}

	private void play(boolean kill) {
		String name = sound.get();
		float p = pitch.getFloat() / 100f;
		if (kill) {
			switch (killSound.get()) {
				case "Off" -> {
					return;
				}
				case "Same" -> {
				}
				case "Same, lower" -> p *= 0.72f;
				default -> name = killSound.get();
			}
		}
		float spread = variation.getFloat() / 100f;
		p *= 1 + (ThreadLocalRandom.current().nextFloat() * 2 - 1) * spread;
		if (combo.get() && !kill) {
			p *= 1 + comboCount * 0.05f;
		}
		float v = volume.getFloat() / 100f;
		if (ORB.equals(name)) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP,
					Math.max(0.5f, Math.min(2f, 1.6f * p)), Math.min(1f, v * 0.6f)));
		} else if (CUSTOM.equals(name)) {
			Pcm pcm = custom;
			if (pcm != null) {
				SoundOut.play(pcm, trimStart.get(), trimStart.get() + trimLength.get(), v, p);
			}
		} else if (PRESETS.contains(name)) {
			SoundEvent event = SoundEvent.createVariableRangeEvent(
					Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "hit." + name.toLowerCase(java.util.Locale.ROOT)));
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, Math.max(0.5f, Math.min(2f, p)),
					Math.min(1f, v * 0.85f)));
		}
	}
}
