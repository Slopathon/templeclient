package nz.temple.client.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import nz.temple.client.TempleNzClient;
import nz.temple.client.util.Friends;
import nz.temple.client.util.TotemTracker;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Notifier extends Module {
	private final BoolSetting pops;
	private final BoolSetting visualRange;
	private final BoolSetting leaving;
	private final BoolSetting friends;
	private final BoolSetting pearls;
	private final ModeSetting where;
	private final BoolSetting sound;
	private final Set<UUID> inView = new HashSet<>();
	private final Map<UUID, String> names = new HashMap<>();
	private final Set<Integer> pearlsSeen = new HashSet<>();
	private final Map<Integer, Boolean> dead = new HashMap<>();
	private Object level;
	public Notifier() {
		super("Notifier", Category.PLAYER, "Alerts you to totem pops, players entering or leaving view, and thrown ender pearls, in chat, "
				+ "as notifications, or both.");
		pops = add(new BoolSetting("Totem pops", true).describe("When a player pops a totem, with their total count, and when they die "
				+ "after popping."));
		visualRange = add(new BoolSetting("Visual range", true).describe("When a player comes into view."));
		leaving = add(new BoolSetting("Leaving view", true).describe("When a player goes out of view again.").visibleWhen(visualRange::get));
		friends = add(new BoolSetting("Include friends", false).describe("Include friends.")
				.visibleWhen(visualRange::get));
		pearls = add(new BoolSetting("Pearls thrown", true).describe("When another player throws an ender pearl."));
		group();
		where = add(new ModeSetting("Show in", List.of("Chat", "Notifications", "Both"), "Both")
				.describe("Chat sends a message only you can see. Notifications shows a notification. Both does both."));
		sound = add(new BoolSetting("Sound", false).describe("Plays a sound with each alert."));
		TotemTracker.onPop(entity -> {
			if (isEnabled() && pops.get() && entity instanceof Player p && p != Minecraft.getInstance().player) {
				int count = TotemTracker.count(p.getId());
				tell("Totem pop", name(p) + " popped " + (count == 1 ? "a totem" : count + " totems"));
			}
		});
		on(TickEvent.class, e -> tick());
	}
	@Override
	protected void onEnable() {
		inView.clear();
		pearlsSeen.clear();
		dead.clear();
		level = null;
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		boolean fresh = level != mc.level;
		if (fresh) {
			level = mc.level;
			inView.clear();
			pearlsSeen.clear();
			dead.clear();
		}
		Set<UUID> now = new HashSet<>();
		for (Player p : mc.level.players()) {
			if (p == mc.player) {
				continue;
			}
			UUID id = p.getUUID();
			now.add(id);
			names.put(id, name(p));
			boolean counts = friends.get() || !Friends.isFriend(p);
			if (!fresh && visualRange.get() && counts && !inView.contains(id)) {
				tell("In view", name(p) + " came into view");
			}
			boolean isDead = p.isDeadOrDying();
			if (pops.get() && isDead && !dead.getOrDefault(p.getId(), false) && TotemTracker.count(p.getId()) > 0) {
				int count = TotemTracker.count(p.getId());
				tell("Death", name(p) + " died after popping " + (count == 1 ? "a totem" : count + " totems"));
			}
			dead.put(p.getId(), isDead);
		}
		if (!fresh && visualRange.get() && leaving.get()) {
			for (UUID id : inView) {
				if (!now.contains(id)) {
					String n = names.getOrDefault(id, "Someone");
					boolean counts = friends.get() || Friends.manager() == null || !Friends.manager().isFriend(n);
					if (counts) {
						tell("Out of view", n + " left view");
					}
				}
			}
		}
		inView.clear();
		inView.addAll(now);
		if (pearls.get()) {
			Set<Integer> seen = new HashSet<>();
			for (Entity e : mc.level.entitiesForRendering()) {
				if (e instanceof ThrownEnderpearl pearl) {
					seen.add(pearl.getId());
					if (!fresh && !pearlsSeen.contains(pearl.getId()) && pearl.getOwner() instanceof Player owner && owner != mc.player) {
						tell("Pearl", name(owner) + " threw a pearl");
					}
				}
			}
			pearlsSeen.clear();
			pearlsSeen.addAll(seen);
		}
	}
	private static String name(Player p) {
		return p.getGameProfile().name();
	}
	private void tell(String title, String text) {
		Minecraft mc = Minecraft.getInstance();
		if (!where.is("Notifications") && mc.player != null) {
			nz.temple.client.compat.Players.message(mc.player, Component.literal("§8[§bTemple§8] §f" + text));
		}
		if (!where.is("Chat") && TempleNzClient.notifications() != null) {
			TempleNzClient.notifications().info(title, text);
		}
		if (sound.get()) {
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.6f, 0.5f));
		}
	}
}
