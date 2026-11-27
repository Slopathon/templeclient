package nz.temple.client.modules.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import nz.temple.client.TempleNzClient;
import nz.temple.event.TickEvent;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.NumberSetting;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NamespaceListSetting;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class Privacy extends Module {
	private static volatile String activeMode = "Disabled";
	private static volatile Set<String> namespaces = Set.of();
	private static volatile boolean announce = true;
	private static volatile boolean whoosh = true;
	private static volatile long quietMillis = 15_000;
	private static final Map<String, Long> lastShown = new ConcurrentHashMap<>();
	private static final AtomicInteger blockedCount = new AtomicInteger();
	private final ModeSetting mode;
	private final NamespaceListSetting whitelist;
	private final BoolSetting notify;
	private final BoolSetting sound;
	private final NumberSetting quiet;

	public Privacy() {
		super("Privacy", Category.CLIENT, "Reports the client as vanilla, or only allows custom payload channels from a whitelist of "
				+ "namespaces.");
		mode = add(new ModeSetting("Network mode", List.of("Vanilla", "Whitelist"), "Vanilla")
				.describe("Vanilla reports a vanilla client and blocks non-vanilla custom payloads. Whitelist keeps Fabric branding and "
						+ "allows the listed namespaces."));
		whitelist = add(new NamespaceListSetting("Namespaces", List.of("fabric"))
				.describe("Namespaces allowed in Whitelist mode, for example: .set Privacy Namespaces fabric,voicechat (or none)."));
		group();
		notify = add(new BoolSetting("Notify on block", true)
				.describe("Shows a notification with the channel name when a server probe is blocked."));
		sound = add(new BoolSetting("Whoosh", true)
				.describe("Plays a sound with the notification.")
				.visibleWhen(notify::get));
		quiet = add(new NumberSetting("Repeat delay", 15, 0, 120, 5, " s")
				.describe("How long before the same channel is announced again. Servers often probe repeatedly.")
				.visibleWhen(notify::get));
		mode.addListener(this::sync);
		whitelist.addListener(this::sync);
		notify.addListener(this::sync);
		sound.addListener(this::sync);
		quiet.addListener(this::sync);
		on(TickEvent.class, e -> {
			sync();
		});
	}

	@Override
	public String disableWarning() {
		return "Privacy hides this client: it reports a vanilla brand and blocks modded payload channels. "
				+ "With it off, servers can see that you are running a modded client and which channels it registers.";
	}

	@Override
	protected void onEnable() {
		sync();
	}

	@Override
	protected void onDisable() {
		activeMode = "Disabled";
		namespaces = Set.of();
	}

	private void sync() {
		if (isEnabled()) {
			activeMode = mode.get();
			namespaces = whitelist.get();
			announce = notify.get();
			whoosh = sound.get();
			quietMillis = (long) (quiet.get() * 1000);
		}
	}

	public static int blocked() {
		return blockedCount.get();
	}

	public static void blockedProbe(Identifier channel) {
		blockedCount.incrementAndGet();
		if (!announce) {
			return;
		}
		String key = channel.toString();
		long now = System.currentTimeMillis();
		Long previous = lastShown.get(key);
		if (previous != null && now - previous < quietMillis) {
			return;
		}
		lastShown.put(key, now);
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (TempleNzClient.notifications() != null) {
				TempleNzClient.notifications().info("Probe blocked", key);
			}
			if (whoosh) {
				mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_ATTACK_SWEEP, 0.72f, 0.09f));
			}
		});
	}
	public static void resetAnnouncements() {
		lastShown.clear();
	}
	public static boolean spoofsVanillaBrand() {
		return activeMode.equals("Vanilla");
	}
	public static boolean filtersPayloads() {
		return !activeMode.equals("Disabled");
	}
	public static boolean allowsPayload(Identifier channel) {
		if (!filtersPayloads()) {
			return true;
		}
		if (channel.getNamespace().equals("minecraft")) {
			return channel.getPath().equals("brand") || activeMode.equals("Whitelist");
		}
		return activeMode.equals("Whitelist") && namespaces.contains(channel.getNamespace());
	}
	public static boolean allowsRegisteredChannel(Identifier channel) {
		return filtersPayloads() && activeMode.equals("Whitelist")
				&& namespaces.contains(channel.getNamespace());
	}
}
