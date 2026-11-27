package nz.temple.client.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import nz.temple.client.TempleNzClient;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.TextSetting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class StaffDisplay extends Module {
	public record Staff(String name, String role, boolean spectator, double distance) {
	}
	private static final java.util.Comparator<PlayerInfo> TAB_ORDER = java.util.Comparator
			.comparingInt((PlayerInfo p) -> -p.getTabListOrder())
			.thenComparingInt(p -> p.getGameMode() == GameType.SPECTATOR ? 1 : 0)
			.thenComparing(p -> p.getTeam() == null ? "" : p.getTeam().getName())
			.thenComparing(p -> p.getProfile().name(), String::compareToIgnoreCase);

	private final TextSetting stars;
	private final BoolSetting admins;
	private final TextSetting names;
	private final TextSetting keywords;
	private final BoolSetting spectators;
	private final BoolSetting notifyJoin;
	private final BoolSetting notifyLeave;
	private final BoolSetting chat;
	private final BoolSetting sound;
	private final BoolSetting showEmpty;
	private final BoolSetting showNearby;
	private final Map<UUID, Staff> online = new LinkedHashMap<>();
	private List<Staff> snapshot = List.of();
	private Object connection;
	private String keywordSource;
	private Pattern keywordPattern;
	private boolean primed;
	private long connectedAt;
	public StaffDisplay() {
		super("Staff Display", Category.PLAYER, "Lists online staff on the HUD and notifies you when they join or leave.");
		stars = add(new TextSetting("Staff marks", "★☆✦✧✩✪✫✬✭✮✯⭐", 100, "★")
				.describe("Symbols that mark staff in the tab list. DonutSMP puts a star before staff names. If staff are being missed, "
						+ "press Copy tab marks and add the symbol they use."));
		admins = add(new BoolSetting("Admins above staff", true)
				.describe("DonutSMP admins have no star but are listed above staff. When on, anyone without a star listed above a starred "
						+ "player is treated as an admin."));
		names = add(new TextSetting("Staff names", "DrDonutt", 1000, "name, name, ...")
				.describe("Usernames always treated as admins, separated by commas. Needed when no starred staff are online, since nothing "
						+ "else in the tab list identifies admins."));
		keywords = add(new TextSetting("Rank tags", "", 500, "admin, mod, ...")
				.describe("For other servers: words that mark staff in a tab-list name or rank prefix, such as [Mod]. Matches whole words "
						+ "only. Empty by default because DonutSMP uses the star."));
		add(new nz.temple.setting.ActionSetting("Copy tab marks", "Copy", this::copyMarks)
				.describe("Copies every tab-list entry with the symbols around each name, so you can see which marks staff use."));
		spectators = add(new BoolSetting("Spectators", true)
				.describe("Counts players in spectator mode as staff. Most vanish plugins leave vanished staff in the tab list this way."));
		group();
		notifyJoin = add(new BoolSetting("Alert on join", true).describe("Notifies you when a staff member comes online."));
		notifyLeave = add(new BoolSetting("Alert on leave", true).describe("Notifies you when a staff member goes offline."));
		chat = add(new BoolSetting("Chat message", false).describe("Also shows joins and leaves in chat."));
		sound = add(new BoolSetting("Sound", true).describe("Plays a sound with each alert."));
		group();
		showEmpty = add(new BoolSetting("Show when none", false).describe("Keeps the HUD widget visible with \"No staff online\" when nobody "
				+ "is on."));
		showNearby = add(new BoolSetting("Show distance", true).describe("Shows how far away staff are when they are within render "
				+ "distance."));
		on(TickEvent.class, e -> tick());
	}
	public List<Staff> online() {
		return isEnabled() ? snapshot : List.of();
	}
	public boolean showWhenEmpty() {
		return showEmpty.get();
	}
	@Override
	protected void onDisable() {
		online.clear();
		snapshot = List.of();
		primed = false;
	}

	private Set<String> nameSet() {
		return java.util.Arrays.stream(names.get().split("[,\\s]+"))
				.map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
	}
	private Pattern keywordPattern() {
		String src = keywords.get();
		if (!src.equals(keywordSource)) {
			keywordSource = src;
			List<String> words = java.util.Arrays.stream(src.split(","))
					.map(String::trim).filter(s -> !s.isEmpty()).map(Pattern::quote).toList();
			keywordPattern = words.isEmpty() ? null
					: Pattern.compile("(?<![\\p{L}\\p{N}])(" + String.join("|", words) + ")(?![\\p{L}\\p{N}])", Pattern.CASE_INSENSITIVE);
		}
		return keywordPattern;
	}
	private static String marks(PlayerInfo info) {
		String name = info.getProfile().name();
		StringBuilder tags = new StringBuilder();
		for (Component c : new Component[] {info.getTabListDisplayName(),
				info.getTeam() == null ? null : info.getTeam().getPlayerPrefix(),
				info.getTeam() == null ? null : info.getTeam().getPlayerSuffix()}) {
			if (c != null) {
				tags.append(' ').append(c.getString());
			}
		}
		return tags.toString().replace(name, " ");
	}
	private boolean starred(String marks) {
		String set = stars.get();
		return marks.codePoints().anyMatch(cp -> !Character.isWhitespace(cp) && set.indexOf(cp) >= 0);
	}
	private void copyMarks() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null) {
			return;
		}
		List<PlayerInfo> sorted = new ArrayList<>(mc.getConnection().getOnlinePlayers());
		sorted.sort(TAB_ORDER);
		StringBuilder out = new StringBuilder();
		for (PlayerInfo info : sorted) {
			String marks = marks(info).trim();
			out.append(info.getProfile().name()).append("  [").append(marks).append("]");
			marks.codePoints().filter(cp -> !Character.isLetterOrDigit(cp) && !Character.isWhitespace(cp))
					.forEach(cp -> out.append(String.format(Locale.ROOT, " U+%04X", cp)));
			out.append('\n');
		}
		mc.keyboardHandler.setClipboard(out.toString());
		if (TempleNzClient.notifications() != null) {
			TempleNzClient.notifications().info("Staff Display", "copied " + sorted.size() + " tab entries to the clipboard");
		}
	}
	private String role(PlayerInfo info, String text, boolean aboveStaff, Set<String> listed, Pattern ranks) {
		String name = info.getProfile().name();
		if (starred(text)) {
			return "Staff";
		}
		if (admins.get() && aboveStaff) {
			return "Admin";
		}
		if (listed.contains(name.toLowerCase(Locale.ROOT))) {
			return "Admin";
		}
		if (ranks != null) {
			var m = ranks.matcher(text);
			if (m.find()) {
				String word = m.group(1);
				return word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1).toLowerCase(Locale.ROOT);
			}
		}
		if (spectators.get() && info.getGameMode() == GameType.SPECTATOR) {
			return "Spectator";
		}
		return null;
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null || mc.player == null) {
			online.clear();
			snapshot = List.of();
			primed = false;
			return;
		}
		if (connection != mc.getConnection()) {

			connection = mc.getConnection();
			connectedAt = System.currentTimeMillis();
			online.clear();
			primed = false;
		}
		Set<String> listed = nameSet();
		Pattern ranks = keywordPattern();
		Map<UUID, Staff> now = new LinkedHashMap<>();
		List<PlayerInfo> sorted = new ArrayList<>(mc.getConnection().getOnlinePlayers());
		sorted.sort(TAB_ORDER);
		String[] marks = new String[sorted.size()];
		int lastStar = -1;
		for (int i = 0; i < sorted.size(); i++) {
			marks[i] = marks(sorted.get(i));
			if (starred(marks[i])) {
				lastStar = i;
			}
		}
		for (int i = 0; i < sorted.size(); i++) {
			PlayerInfo info = sorted.get(i);
			UUID id = info.getProfile().id();
			if (id.equals(mc.player.getUUID())) {
				continue;
			}
			String role = role(info, marks[i], i < lastStar, listed, ranks);
			if (role == null) {
				continue;
			}
			Player entity = mc.level == null ? null : mc.level.getPlayerByUUID(id);
			double dist = entity != null && showNearby.get() ? mc.player.distanceTo(entity) : -1;
			now.put(id, new Staff(info.getProfile().name(), role, info.getGameMode() == GameType.SPECTATOR, dist));
		}
		if (primed) {
			for (Map.Entry<UUID, Staff> e : now.entrySet()) {
				if (!online.containsKey(e.getKey()) && notifyJoin.get()) {
					alert(mc, e.getValue(), true);
				}
			}
			for (Map.Entry<UUID, Staff> e : online.entrySet()) {
				if (!now.containsKey(e.getKey()) && notifyLeave.get()) {
					alert(mc, e.getValue(), false);
				}
			}
		}
		if (System.currentTimeMillis() - connectedAt > 4000) {
			primed = true;
		}
		Map<UUID, Staff> merged = new LinkedHashMap<>();
		for (UUID id : online.keySet()) {
			if (now.containsKey(id)) {
				merged.put(id, now.get(id));
			}
		}
		now.forEach(merged::putIfAbsent);
		online.clear();
		online.putAll(merged);
		snapshot = Collections.unmodifiableList(new ArrayList<>(online.values()));
	}
	private void alert(Minecraft mc, Staff s, boolean joined) {
		String title = s.name() + (joined ? " joined" : " left");
		String detail = s.role() + (joined ? " is online" : " went offline");
		if (TempleNzClient.notifications() != null) {
			TempleNzClient.notifications().info(title, detail);
		}
		if (chat.get()) {
			nz.temple.client.compat.Players.message(mc.player, Component.literal("§8[§bTemple§8] §e" + s.role() + " §f" + s.name() + (joined ? " §ajoined" : " §cleft")));
		}
		if (sound.get()) {
			mc.getSoundManager().play(SimpleSoundInstance.forUI(joined ? SoundEvents.NOTE_BLOCK_PLING.value() : SoundEvents.NOTE_BLOCK_BASS.value(),
					joined ? 1.4f : 0.8f, 0.5f));
		}
	}
	/** Shown and hidden from the HUD editor (middle-click → Staff list), not from the module list. */
	@Override
	public boolean listedInCategory() {
		return false;
	}
}
