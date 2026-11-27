package nz.temple.client.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.Rotations;
import nz.temple.client.util.Teleport;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;
import nz.temple.social.FriendManager;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class TPAura extends Module {
	private static final double REACH = 2.95;
	private final FriendManager friends;
	private final ModeSetting travel;
	private final NumberSetting range;
	private final ModeSetting position;
	private final NumberSetting distance;
	private final NumberSetting orbitStep;
	private final ModeSetting timing;
	private final NumberSetting cooldown;
	private final NumberSetting cps;
	private final NumberSetting hitCharge;
	private final BoolSetting confirm;
	private final NumberSetting retries;
	private final BoolSetting players;
	private final BoolSetting hostiles;
	private final BoolSetting animals;
	private final BoolSetting ignoreFriends;
	private final BoolSetting ignoreInvisible;
	private final ModeSetting priority;
	private final BoolSetting throughWalls;
	private final ModeSetting swing;
	private final BoolSetting pauseWhileUsing;
	private final BoolSetting legit;
	private final BoolSetting quick;
	private LivingEntity current;
	private int angleIndex;
	private long nextAttack;
	private LivingEntity pending;
	private float pendingHealth;
	private int pendingTicks;
	private int misses;
	private int stuck;
	private final java.util.Map<Integer, Long> unreachable = new java.util.HashMap<>();
	public TPAura(FriendManager friends) {
		super("TP Aura", Category.HVH, "Teleports next to targets, attacks, confirms the hit, and repeats. Replaces Kill Aura, so "
				+ "turning one on turns the other off. Uses the vanilla movement allowance; servers with stricter movement checks may "
				+ "pull you back.");
		this.friends = friends;
		legit = add(new BoolSetting("Legit", false)
				.describe("No teleporting. Sprints to the target with your movement keys, jumps over obstacles, turns your camera onto them "
						+ "and attacks when the crosshair is on them. Range still limits how far it chases."));
		quick = add(new BoolSetting("Quick hit", false)
				.describe("Teleports to the near side of the target, hits once and teleports straight back, all in one tick. Does not wait "
						+ "to confirm the hit. Travel, Position and Confirm hits are not used."));
		travel = add(new ModeSetting("Travel", List.of("Return", "Chase"), "Return")
				.describe("Return teleports out, attacks and comes back within one tick. Chase stays next to the target and follows it, for "
						+ "targets further away."));
		range = add(new NumberSetting("Range", 16, 4, 64, 1, " m")
				.describe("Maximum target distance. Return mode is limited to about 21 blocks, the most one tick allows."));
		position = add(new ModeSetting("Position", List.of("Behind", "Above", "Orbit", "Random"), "Orbit")
				.describe("Where to appear. Behind places you at their back. Above places you over their head. Orbit uses a different side "
						+ "each hit. Random picks any side."));
		distance = add(new NumberSetting("Distance", 2.4, 0.8, 3, 0.1, " m")
				.describe("Distance from the target to appear at. Keep it within your reach."));
		orbitStep = add(new NumberSetting("Orbit step", 3, 1, 8, 1, "")
				.describe("How many of 16 positions to move around the target after each hit.")
				.visibleWhen(() -> position.is("Orbit")));
		group();
		timing = add(new ModeSetting("Timing", List.of("Cooldown", "CPS"), "Cooldown")
				.describe("Cooldown waits for a full attack charge. CPS attacks at a fixed rate."));
		cooldown = add(new NumberSetting("Attack cooldown", 95, 30, 100, 1, "%")
				.describe("Required attack charge before each hit.")
				.visibleWhen(() -> timing.is("Cooldown")));
		cps = add(new NumberSetting("CPS", 10, 1, 20, 0.5, "")
				.describe("Attacks per second. A target can only take damage twice a second, so attacks are held for the moments "
						+ "they can land.")
				.visibleWhen(() -> timing.is("CPS")));
		hitCharge = add(new NumberSetting("Hit charge", 90, 0, 100, 5, "%")
				.describe("Attack charge needed before a hit is sent. Over 90% deals full damage and allows crits.")
				.visibleWhen(() -> timing.is("CPS")));
		confirm = add(new BoolSetting("Confirm hits", true)
				.describe("Checks that the target took damage. A missed hit is retried from another side immediately."));
		retries = add(new NumberSetting("Retries", 2, 0, 6, 1, "")
				.describe("How many times a missed hit is retried before waiting for the next attack.")
				.visibleWhen(confirm::get));
		group();
		players = add(new BoolSetting("Players", true).describe("Attack other players."));
		hostiles = add(new BoolSetting("Hostiles", false).describe("Attack hostile mobs."));
		animals = add(new BoolSetting("Animals", false).describe("Attack passive animals."));
		ignoreFriends = add(new BoolSetting("Ignore friends", true).describe("Never attack players on your friends list."));
		ignoreInvisible = add(new BoolSetting("Ignore invisible", false).describe("Skip invisible entities."));
		priority = add(new ModeSetting("Priority", List.of("Distance", "Health", "Armor"), "Distance")
				.describe("Which target to go for first."));
		throughWalls = add(new BoolSetting("Through walls", true)
				.describe("Targets entities you cannot see. The path to them must still be clear."));
		group();
		swing = add(new ModeSetting("Swing", List.of("Client", "Packet", "None"), "Client")
				.describe("Client plays the normal swing. Packet sends the swing to the server without animating your hand. None sends no "
						+ "swing."));
		pauseWhileUsing = add(new BoolSetting("Pause while using", true)
				.describe("Pauses while eating, drinking or blocking."));
		hideWhenLegit(legit, quick, travel, position, distance, orbitStep, confirm, retries, throughWalls, swing);
		for (var s : List.of(travel, position, orbitStep, confirm, retries)) {
			s.andVisibleWhen(() -> !quick.get());
		}
		on(TickEvent.class, e -> tick());
	}
	public LivingEntity target() {
		return isEnabled() ? current : null;
	}

	@Override
	protected void onDisable() {
		current = null;
		pending = null;
		nextAttack = 0;
		misses = 0;
		stuck = 0;
		unreachable.clear();
		if (legit.get()) {
			nz.temple.client.util.Steering.stop();
		}
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.gameMode == null || player.isDeadOrDying()
				|| pauseWhileUsing.get() && player.isUsingItem()) {
			current = null;
			pending = null;
			return;
		}
		if (legit.get()) {
			legitTick(player);
			return;
		}
		boolean retry = !quick.get() && checkPending();
		long now = System.currentTimeMillis();
		unreachable.values().removeIf(until -> until < now);
		LivingEntity previous = current;
		current = pick(player);
		if (current != previous) {
			stuck = 0;
		}
		if (current == null) {
			return;
		}
		if (!Teleport.ready(player)) {
			return;
		}
		if (!retry && (!ready(player) || !nz.temple.client.util.HitTiming.canDamage(player, current))) {
			if (chasing()) {
				follow(player, current);
			}
			return;
		}
		strike(player, current);
	}
	private boolean checkPending() {
		if (pending == null) {
			return false;
		}
		boolean landed = pending.hurtTime > 0 || pending.getHealth() < pendingHealth || !pending.isAlive();
		if (landed) {
			pending = null;
			misses = 0;
			return false;
		}
		if (++pendingTicks < 3) {
			return false;
		}
		pending = null;
		if (confirm.get() && misses < retries.get()) {
			misses++;
			angleIndex += 5;
			return true;
		}
		misses = 0;
		return false;
	}
	private void strike(LocalPlayer player, LivingEntity target) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 home = player.position();
		boolean chase = chasing();

		int extra = 0;
		Vec3 spot = spotFor(player, target, home, extra);
		if (spot == null && extra > 0) {
			extra = 0;
			spot = spotFor(player, target, home, 0);
		}
		if (spot == null) {
			angleIndex++;
			giveUpIfStuck(target);
			return;
		}
		if (chase && home.distanceTo(spot) > Teleport.MAX_PER_TICK) {
			Teleport.step(player, spot);
			return;
		}
		float[] look = Rotations.toward(spot.add(0, player.getEyeHeight(), 0), target.getBoundingBox().getCenter(), player.getYRot());
		if (!Teleport.hop(player, home, spot, chase, look[0], look[1], extra)) {
			angleIndex++;
			giveUpIfStuck(target);
			return;
		}
		stuck = 0;
		pendingHealth = target.getHealth();
		mc.gameMode.attack(player, target);
		nz.temple.client.util.HitTiming.attacked(player, target);
		switch (swing.get()) {
			case "Client" -> nz.temple.client.compat.Players.swing(player, InteractionHand.MAIN_HAND);
			case "Packet" -> nz.temple.client.compat.Players.swingPacket(player, InteractionHand.MAIN_HAND);
			default -> {
			}
		}
		if (!chase) {
			Teleport.back(player);
		}
		pending = quick.get() ? null : target;
		pendingTicks = 0;
		if (position.is("Orbit") && !quick.get()) {
			angleIndex += (int) Math.round(orbitStep.get());
		}
		nextAttack = timing.is("CPS") ? System.currentTimeMillis() + (long) (1000 / Math.max(1, cps.get())) : 0;
	}
	private void legitTick(LocalPlayer player) {
		if (Minecraft.getInstance().gui.screen() != null) {
			nz.temple.client.util.Steering.stop();
			return;
		}
		current = pick(player);
		if (current == null) {
			nz.temple.client.util.Steering.stop();
			return;
		}
		double reach = player.entityInteractionRange();
		if (current.getBoundingBox().distanceToSqr(player.getEyePosition()) > (reach - 0.4) * (reach - 0.4)) {
			nz.temple.client.util.LegitAim.walk(player, current.position(), true);
		} else {
			nz.temple.client.util.Steering.stop();
		}
		float charge = timing.is("CPS") ? 0.5f : cooldown.getFloat() / 100f;
		nz.temple.client.util.LegitAim.hit(player, current, 40, charge);
	}
	private void giveUpIfStuck(LivingEntity target) {
		if (++stuck >= 10) {
			unreachable.put(target.getId(), System.currentTimeMillis() + 3000);
			current = null;
			stuck = 0;
		}
	}
	private void follow(LocalPlayer player, LivingEntity target) {
		if (player.position().distanceTo(target.position()) > distance.get() + 1.5) {
			Vec3 spot = spotFor(player, target, player.position(), 0);
			if (spot != null) {
				Teleport.step(player, spot);
			}
		}
	}
	private Vec3 spotFor(LocalPlayer player, LivingEntity target, Vec3 home, int extra) {
		boolean chase = chasing();
		Vec3 base = target.position();
		net.minecraft.world.phys.AABB box = target.getBoundingBox();
		if (position.is("Above") && !quick.get()) {
			Vec3 above = base.add(0, target.getBbHeight() + 0.2, 0);
			if (Teleport.fits(player, above) && (chase || reachable(player, home, above, extra))) {
				return above;
			}
		}
		double start = quick.get() ? Math.atan2(home.z - base.z, home.x - base.x) : switch (position.get()) {
			case "Behind" -> Math.toRadians(target.getYRot() + 90);
			case "Random" -> ThreadLocalRandom.current().nextDouble(Math.PI * 2);
			default -> Math.PI * 2 * angleIndex / 16;
		};
		double d = distance.get();
		for (int i = 0; i < 16; i++) {
			int k = (i + 1) / 2 * (i % 2 == 0 ? -1 : 1);
			double a = start + Math.PI * 2 * k / 16;
			for (double dy : new double[] {0, 1, -1}) {
				Vec3 spot = new Vec3(base.x + Math.cos(a) * d, base.y + dy, base.z + Math.sin(a) * d);
				if (box.distanceToSqr(spot.add(0, player.getEyeHeight(), 0)) > REACH * REACH || !Teleport.fits(player, spot)) {
					continue;
				}
				if (chase || reachable(player, home, spot, extra)) {
					return spot;
				}
			}
		}
		return null;
	}
	private boolean reachable(LocalPlayer player, Vec3 home, Vec3 spot, int extra) {
		return Teleport.reachable(player, home, spot, !chasing(), extra);
	}

	private boolean chasing() {
		return travel.is("Chase") && !quick.get();
	}

	private boolean ready(LocalPlayer player) {
		if (timing.is("CPS")) {
			return System.currentTimeMillis() >= nextAttack && player.getAttackStrengthScale(0.5f) >= hitCharge.get() / 100f;
		}
		return player.getAttackStrengthScale(0.5f) >= cooldown.get() / 100f;
	}
	private LivingEntity pick(LocalPlayer player) {
		double max = !chasing() ? Math.min(range.get(), Teleport.MAX_PER_TICK + distance.get()) : range.get();
		if (current != null && valid(player, current, max)) {
			return current;
		}
		Comparator<LivingEntity> order = switch (priority.get()) {
			case "Health" -> Comparator.comparingDouble(e -> e.getHealth() + e.getAbsorptionAmount());
			case "Armor" -> Comparator.comparingInt(LivingEntity::getArmorValue);
			default -> Comparator.comparingDouble(e -> e.distanceToSqr(player));
		};
		LivingEntity best = null;
		for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
			if (entity instanceof LivingEntity living && valid(player, living, max) && (best == null || order.compare(living, best) < 0)) {
				best = living;
			}
		}
		return best;
	}
	private boolean valid(LocalPlayer player, LivingEntity e, double max) {
		if (e == player || !e.isAlive() || e.isDeadOrDying() || unreachable.containsKey(e.getId()) || e.distanceToSqr(player) > max * max
				|| ignoreInvisible.get() && e.isInvisible() || !throughWalls.get() && !player.hasLineOfSight(e)) {
			return false;
		}
		if (e instanceof Player p) {
			return players.get() && !p.isSpectator() && !p.isCreative()
					&& !(ignoreFriends.get() && friends.isFriend(p.getName().getString()));
		}
		if (e instanceof Enemy) {
			return hostiles.get();
		}
		return e instanceof Animal && animals.get();
	}
}
