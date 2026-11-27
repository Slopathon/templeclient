package nz.temple.client.util;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import nz.temple.TempleNz;
import nz.temple.client.TempleNzClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerHistory {
	private static final long ENCOUNTER_GAP = 5 * 60_000;
	private static final int MAX_SIGHTINGS = 16;
	private static final int MAX_HELD = 36;
	private static final Gson GSON = new GsonBuilder().create();
	public static final class Item {
		public String id;
		public int count;
		public String name;
		public boolean enchanted;
		public int damage;
		public int maxDamage;
		public List<String> enchantments = new ArrayList<>();
		public String full;
	}
	public static final class Sighting {
		public long time;
		public String server;
		public String dimension;
		public int x;
		public int y;
		public int z;
	}
	public static final class Record {
		public String uuid;
		public String name;
		public List<String> otherNames = new ArrayList<>();
		public String skin;
		public String skinSignature;
		public long firstSeen;
		public long lastSeen;
		public int encounters;
		public long millisNear;
		public double closest = Double.MAX_VALUE;
		public float health;
		public float absorption;
		public float maxHealth;
		public int ping;
		public String gameMode;
		public int totemPops;
		public int deathsSeen;
		public Set<String> servers = new LinkedHashSet<>();
		public List<Item> gear = new ArrayList<>();
		public long gearTime;
		public List<Item> held = new ArrayList<>();
		public List<Sighting> sightings = new ArrayList<>();
		public Sighting last() {
			return sightings.isEmpty() ? null : sightings.get(sightings.size() - 1);
		}

		public GameProfile profile() {
			UUID id;
			try {
				id = UUID.fromString(uuid);
			} catch (IllegalArgumentException e) {
				id = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
			}
			if (skin == null) {
				return new GameProfile(id, name);
			}
			Property textures = skinSignature == null ? new Property("textures", skin) : new Property("textures", skin, skinSignature);
			return new GameProfile(id, name, new PropertyMap(ImmutableMultimap.of("textures", textures)));
		}
	}
	private static final Map<String, Record> RECORDS = new ConcurrentHashMap<>();
	private static final Map<Integer, Integer> popsSeen = new HashMap<>();
	private static final Map<Integer, Boolean> deadSeen = new HashMap<>();
	private static boolean loaded;
	private static boolean dirty;
	private static long lastUpdate;
	private static long lastSave;
	private PlayerHistory() {
	}
	public static void register() {
		load();
		ClientTickEvents.END_CLIENT_TICK.register(PlayerHistory::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			popsSeen.clear();
			deadSeen.clear();
			save();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> save());
	}
	public static List<Record> all() {
		load();
		return new ArrayList<>(RECORDS.values());
	}

	public static void forget(Record r) {
		if (RECORDS.remove(r.uuid) != null) {
			dirty = true;
			save();
		}
	}
	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(TempleNz.MOD_ID).resolve("players.json");
	}
	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path file = file();
		if (!Files.exists(file)) {
			return;
		}
		try {
			List<Record> read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<List<Record>>() { }.getType());
			if (read != null) {
				for (Record r : read) {
					if (r != null && r.uuid != null && r.name != null) {
						RECORDS.put(r.uuid, r);
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			TempleNzClient.LOGGER.warn("Could not read {}", file, e);
		}
	}
	public static void save() {
		if (!dirty) {
			return;
		}
		dirty = false;
		lastSave = System.currentTimeMillis();
		Path file = file();
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling("players.json.tmp");
			Files.writeString(tmp, GSON.toJson(new ArrayList<>(RECORDS.values())), StandardCharsets.UTF_8);
			Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			TempleNzClient.LOGGER.warn("Could not write {}", file, e);
		}
	}
	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null || mc.getConnection() == null || mc.player.tickCount % 10 != 0) {
			return;
		}
		long now = System.currentTimeMillis();
		long step = lastUpdate == 0 ? 500 : Math.min(2000, now - lastUpdate);
		lastUpdate = now;
		String server = ServerKey.current();
		String dim = ServerKey.dimension();
		for (Player p : mc.level.players()) {
			if (p == mc.player) {
				continue;
			}
			PlayerInfo info = mc.getConnection().getPlayerInfo(p.getUUID());
			if (info == null) {
				continue;
			}
			update(mc, p, info, now, step, server, dim);
		}
		if (dirty && now - lastSave > 30_000) {
			save();
		}
	}
	private static void update(Minecraft mc, Player p, PlayerInfo info, long now, long step, String server, String dim) {
		String uuid = p.getUUID().toString();
		Record r = RECORDS.computeIfAbsent(uuid, k -> {
			Record fresh = new Record();
			fresh.uuid = k;
			fresh.firstSeen = now;
			return fresh;
		});
		String name = p.getGameProfile().name();
		if (r.name != null && !r.name.equals(name) && !r.otherNames.contains(r.name)) {
			r.otherNames.add(r.name);
		}
		r.name = name;
		for (Property prop : info.getProfile().properties().get("textures")) {
			r.skin = prop.value();
			r.skinSignature = prop.signature();
		}
		boolean newEncounter = now - r.lastSeen > ENCOUNTER_GAP;
		if (newEncounter) {
			r.encounters++;
		} else {
			r.millisNear += step;
		}
		r.lastSeen = now;
		r.closest = Math.min(r.closest, mc.player.distanceTo(p));
		r.health = p.getHealth();
		r.absorption = p.getAbsorptionAmount();
		r.maxHealth = p.getMaxHealth();
		r.ping = info.getLatency();
		r.gameMode = info.getGameMode() == null ? null : info.getGameMode().getName();
		if (server != null) {
			r.servers.add(server);
		}
		Sighting last = r.last();
		boolean moved = last == null || Math.abs(last.x - p.getBlockX()) + Math.abs(last.z - p.getBlockZ()) > 24
				|| !java.util.Objects.equals(last.dimension, dim) || !java.util.Objects.equals(last.server, server);
		if (newEncounter || moved) {
			Sighting s = new Sighting();
			r.sightings.add(s);
			while (r.sightings.size() > MAX_SIGHTINGS) {
				r.sightings.remove(0);
			}
			last = s;
		}
		last.time = now;
		last.server = server;
		last.dimension = dim;
		last.x = p.getBlockX();
		last.y = p.getBlockY();
		last.z = p.getBlockZ();
		List<Item> gear = new ArrayList<>(6);
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
				EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
			gear.add(item(mc, p.getItemBySlot(slot)));
		}
		r.gear = gear;
		r.gearTime = now;
		for (Item held : java.util.Arrays.asList(gear.get(4), gear.get(5))) {
			if (held != null) {
				r.held.removeIf(h -> h.id.equals(held.id) && java.util.Objects.equals(h.name, held.name));
				r.held.add(0, held);
				while (r.held.size() > MAX_HELD) {
					r.held.remove(r.held.size() - 1);
				}
			}
		}
		int pops = TotemTracker.count(p.getId());
		int before = popsSeen.getOrDefault(p.getId(), 0);
		if (pops > before) {
			r.totemPops += pops - before;
		}
		popsSeen.put(p.getId(), pops);
		boolean dead = p.isDeadOrDying();
		if (dead && !deadSeen.getOrDefault(p.getId(), false)) {
			r.deathsSeen++;
		}
		deadSeen.put(p.getId(), dead);
		dirty = true;
	}
	private static Item item(Minecraft mc, ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		Item it = new Item();
		it.id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		it.count = stack.getCount();
		it.name = stack.getHoverName().getString();
		it.enchanted = stack.hasFoil();
		it.damage = stack.getDamageValue();
		it.maxDamage = stack.getMaxDamage();
		var enchants = stack.getEnchantments();
		for (var entry : enchants.entrySet()) {
			it.enchantments.add(net.minecraft.world.item.enchantment.Enchantment.getFullname(entry.getKey(), entry.getIntValue()).getString());
		}
		try {
			var ops = mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
			JsonElement json = ItemStack.CODEC.encodeStart(ops, stack).result().orElse(null);
			it.full = json == null ? null : json.toString();
		} catch (RuntimeException e) {
			it.full = null;
		}
		return it;
	}
	public static ItemStack stack(Item it) {
		if (it == null) {
			return ItemStack.EMPTY;
		}
		Minecraft mc = Minecraft.getInstance();
		if (it.full != null && mc.level != null) {
			try {
				var ops = mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
				var parsed = ItemStack.CODEC.parse(ops, com.google.gson.JsonParser.parseString(it.full)).result();
				if (parsed.isPresent()) {
					return parsed.get();
				}
			} catch (RuntimeException ignored) {
			}
		}
		Identifier id = Identifier.tryParse(it.id);
		var item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
		return item == null ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, it.count));
	}
}
