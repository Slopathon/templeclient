package nz.temple.client.modules.player;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.TempleNzClient;
import nz.temple.client.util.Wearer;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.ActionSetting;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;
import nz.temple.setting.TextSetting;

import java.util.List;
import java.util.UUID;

public final class FakePlayer extends Module {
	private static final int ID = -1_337_420;
	private static final int IMMUNE_TICKS = 10;
	private final TextSetting name;
	private final ModeSetting skin;
	private final BoolSetting gear;
	private final BoolSetting cosmetics;
	private final ModeSetting motion;
	private final NumberSetting speed;
	private final NumberSetting turn;
	private final BoolSetting lookAtYou;
	private final NumberSetting health;
	private final BoolSetting hits;
	private final BoolSetting totems;
	private final BoolSetting regen;
	private final BoolSetting respawn;
	private Dummy dummy;
	private Vec3 home;
	private float homeYaw;
	private int age;
	private int lastHit = -100;
	private float lastDamage;
	private int deadFor = -1;
	private double knockX;
	private double knockZ;
	private Vec3 mirrorOffset = Vec3.ZERO;
	private boolean youSwung;
	private final class Dummy extends RemotePlayer {
		Dummy(ClientLevel level, GameProfile profile) {
			super(level, profile);
		}
		@Override
		public PlayerSkin getSkin() {
			LocalPlayer you = Minecraft.getInstance().player;
			return skin.is("Mine") && you != null ? you.getSkin() : super.getSkin();
		}
		@Override
		public boolean isModelPartShown(PlayerModelPart part) {
			LocalPlayer you = Minecraft.getInstance().player;
			return you == null || you.isModelPartShown(part);
		}
	}
	public FakePlayer() {
		super("Fake Player", Category.PLAYER, "Spawns a dummy player only you can see, to test modules and cosmetics on. Your hits "
				+ "on it are never sent to the server.");
		name = add(new TextSetting("Name", "Dummy", 16, "Dummy").describe("The name over its head."));
		skin = add(new ModeSetting("Skin", List.of("Mine", "Default"), "Mine").describe("Mine uses your own skin. Default uses a "
				+ "vanilla skin."));
		gear = add(new BoolSetting("Copy my gear", true).describe("Gives it a copy of your armour and held items when it spawns."));
		cosmetics = add(new BoolSetting("Wear my cosmetics", true).describe("It wears every cosmetic you have set to show on you, "
				+ "with the same settings."));
		group();
		motion = add(new ModeSetting("Motion", List.of("Still", "Strafe", "Circle", "Spin", "Mirror"), "Still").describe("Still stands "
				+ "where it spawned. Strafe walks side to side. Circle walks round in a circle. Spin turns on the spot, to see a "
				+ "cosmetic from every side. Mirror walks beside you and copies how you move, look, crouch and swing."));
		speed = add(new NumberSetting("Speed", 4, 1, 10, 0.5, " b/s").describe("How fast it walks.")
				.visibleWhen(() -> motion.is("Strafe") || motion.is("Circle")));
		turn = add(new NumberSetting("Turn speed", 6, -30, 30, 1, "°/t").describe("Degrees per tick. Negative values turn the other "
				+ "way.").visibleWhen(() -> motion.is("Spin")));
		lookAtYou = add(new BoolSetting("Look at you", false).describe("Turns to face you.")
				.visibleWhen(() -> !motion.is("Spin") && !motion.is("Mirror")));
		group();
		health = add(new NumberSetting("Health", 20, 1, 100, 1, "").describe("Its health. 20 is ten hearts, like a player's."));
		hits = add(new BoolSetting("Takes damage", true).describe("Your hits hurt it, using your weapon's damage and charge, and so do "
				+ "explosions near it, such as end crystals, anchors and TNT, worked out the way the server does."));
		totems = add(new BoolSetting("Totem pops", true).describe("Pops a totem instead of dying, as if it always held one.")
				.visibleWhen(hits::get));
		regen = add(new BoolSetting("Regenerate", true).describe("Heals back to full a few seconds after it was last hit.")
				.visibleWhen(hits::get));
		respawn = add(new BoolSetting("Respawn", true).describe("Stands back up a second after it dies.").visibleWhen(hits::get));
		add(new ActionSetting("Position", "Move here", () -> Minecraft.getInstance().execute(this::moveHere))
				.describe("Moves it to where you stand."));
		on(TickEvent.class, e -> tick());
	}
	public static boolean is(Entity entity) {
		var modules = TempleNzClient.modules();
		FakePlayer m = modules == null ? null : modules.get(FakePlayer.class);
		return m != null && m.dummy != null && entity == m.dummy;
	}
	@Override
	protected void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null && mc.player != null) {
			spawn(mc);
		}
	}

	@Override
	protected void onDisable() {
		despawn();
	}
	private void spawn(Minecraft mc) {
		despawn();
		String shown = name.get().isBlank() ? "Dummy" : name.get().trim();
		Dummy d = new Dummy(mc.level, new GameProfile(UUID.nameUUIDFromBytes(("temple-fake:" + shown).getBytes()), shown));
		d.setId(ID);
		LocalPlayer you = mc.player;
		home = you.position();
		homeYaw = you.getYRot();
		d.snapTo(home.x, home.y, home.z, homeYaw, you.getXRot());
		d.yHeadRot = homeYaw;
		d.yBodyRot = homeYaw;
		var max = d.getAttribute(Attributes.MAX_HEALTH);
		if (max != null) {
			max.setBaseValue(health.get());
		}
		d.setHealth(health.getFloat());
		if (gear.get()) {
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				ItemStack worn = you.getItemBySlot(slot);
				if (!worn.isEmpty()) {
					d.setItemSlot(slot, worn.copy());
				}
			}
		}
		mc.level.addEntity(d);
		dummy = d;
		age = 0;
		lastHit = -100;
		deadFor = -1;
		knockX = 0;
		knockZ = 0;
		double yawRad = Math.toRadians(homeYaw);
		mirrorOffset = new Vec3(-Math.cos(yawRad) * 2, 0, -Math.sin(yawRad) * 2);
		youSwung = false;
		Wearer.mirror(cosmetics.get() ? d : null);
	}
	private void despawn() {
		Wearer.mirror(null);
		Dummy d = dummy;
		dummy = null;
		ClientLevel level = Minecraft.getInstance().level;
		if (d != null && level != null && level.getEntity(ID) == d) {
			level.removeEntity(ID, Entity.RemovalReason.DISCARDED);
		}
	}
	private void moveHere() {
		Minecraft mc = Minecraft.getInstance();
		if (isEnabled() && mc.level != null && mc.player != null) {
			spawn(mc);
		}
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			dummy = null;
			Wearer.mirror(null);
			return;
		}

		if (dummy == null || dummy.isRemoved() || dummy.level() != mc.level) {
			spawn(mc);
			return;
		}
		Dummy d = dummy;
		Wearer.mirror(cosmetics.get() ? d : null);
		age++;
		if (deadFor >= 0) {
			if (++deadFor >= 20 && respawn.get()) {
				d.deathTime = 0;
				d.hurtTime = 0;
				d.setHealth(health.getFloat());
				deadFor = -1;
			}
			return;
		}
		float max = health.getFloat();
		if (regen.get() && d.getHealth() < max && age - lastHit > 60) {
			d.setHealth(Math.min(max, d.getHealth() + max / 40f));
		}
		walk(mc, d);
	}
	private void walk(Minecraft mc, Dummy d) {
		double step = speed.get() / 20.0;
		double t = age * step;
		Vec3 at = switch (motion.get()) {
			case "Strafe" -> {
				double reach = 3;
				double side = Math.toRadians(homeYaw);
				double phase = (t / reach) % 2;
				double off = (phase < 1 ? phase : 2 - phase) * reach * 2 - reach;
				yield home.add(Math.cos(side) * off, 0, Math.sin(side) * off);
			}
			case "Circle" -> {
				double r = 2.5;
				double a = t / r;
				yield home.add(Math.cos(a) * r, 0, Math.sin(a) * r);
			}
			default -> home;
		};
		knockX *= 0.6;
		knockZ *= 0.6;
		if (!mc.level.noCollision(d, d.getBoundingBox().move(knockX, 0, knockZ))) {
			knockX = 0;
			knockZ = 0;
		}
		home = home.add(knockX, 0, knockZ);
		at = at.add(knockX, 0, knockZ);
		LocalPlayer you = mc.player;
		boolean mirror = motion.is("Mirror");
		d.setShiftKeyDown(mirror && you.isShiftKeyDown());
		d.setSprinting(mirror && you.isSprinting());
		if (mirror) {
			mirrorOffset = mirrorOffset.add(knockX, 0, knockZ);
			Vec3 beside = you.position().add(mirrorOffset);
			d.setPos(beside.x, beside.y, beside.z);
			d.setYRot(you.getYRot());
			d.setXRot(you.getXRot());
			d.yHeadRot = you.getYHeadRot();
			d.yBodyRot = you.yBodyRot;
			boolean swinging = nz.temple.client.compat.Players.isSwinging(you);
			if (swinging && !youSwung) {
				nz.temple.client.compat.Players.swing(d, net.minecraft.world.InteractionHand.MAIN_HAND);
			}
			youSwung = swinging;
			return;
		}
		if (motion.is("Spin")) {
			float spun = d.getYRot() + turn.getFloat();
			d.setPos(at.x, at.y, at.z);
			d.setYRot(spun);
			d.setXRot(0);
			d.yHeadRot = spun;
			d.yBodyRot = spun;
			return;
		}
		double dx = at.x - d.getX();
		double dz = at.z - d.getZ();
		float yaw;
		float pitch = 0;
		if (lookAtYou.get()) {
			Vec3 eye = mc.player.getEyePosition();
			double ex = eye.x - d.getX();
			double ey = eye.y - d.getEyeY();
			double ez = eye.z - d.getZ();
			yaw = (float) Math.toDegrees(Math.atan2(-ex, ez));
			pitch = (float) -Math.toDegrees(Math.atan2(ey, Math.sqrt(ex * ex + ez * ez)));
		} else if (dx * dx + dz * dz > 1e-5) {
			yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		} else {
			yaw = d.getYRot();
		}
		d.setPos(at.x, at.y, at.z);
		d.setYRot(yaw);
		d.setXRot(pitch);
		d.yHeadRot = yaw;
		d.yBodyRot = Mth.approachDegrees(d.yBodyRot, yaw, 20);
	}
	public void hit(LocalPlayer you) {
		Dummy d = dummy;
		if (d == null || !hits.get() || deadFor >= 0 || Minecraft.getInstance().getConnection() == null) {
			return;
		}
		float charge = you.getAttackStrengthScale(0.5f);
		float damage = weaponDamage(you) * (0.2f + charge * charge * 0.8f);
		boolean crit = charge > 0.9f && you.fallDistance > 0 && !you.onGround() && !you.onClimbable() && !you.isInWater()
				&& !you.isPassenger() && !you.isSprinting();
		if (crit) {
			damage *= 1.5f;
		}
		float armour = d.getArmorValue();
		float toughness = (float) d.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
		float cut = Mth.clamp(armour - damage / (2 + toughness / 4), armour * 0.2f, 20f);
		damage *= 1 - cut / 25f;
		double away = Math.atan2(d.getZ() - you.getZ(), d.getX() - you.getX());
		double push = you.isSprinting() ? 0.55 : 0.35;
		hurt(d, damage, you.damageSources().playerAttack(you), Math.cos(away) * push, Math.sin(away) * push);
	}
	public void explosion(Vec3 center, float power) {
		Dummy d = dummy;
		Minecraft mc = Minecraft.getInstance();
		if (d == null || !hits.get() || deadFor >= 0 || mc.getConnection() == null) {
			return;
		}
		double reach = power * 2.0;
		double dist = Math.sqrt(d.distanceToSqr(center)) / reach;
		if (dist > 1) {
			return;
		}
		float damage = nz.temple.client.util.Blast.damage(d, center, power);
		double exposure = net.minecraft.world.level.ServerExplosion.getSeenPercent(center, d);
		double knock = Math.min(1.5, (1 - dist) * exposure * 0.8);
		double dx = d.getX() - center.x;
		double dz = d.getZ() - center.z;
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1e-4) {
			dx = 0;
			dz = 0;
		} else {
			dx = dx / len * knock;
			dz = dz / len * knock;
		}
		if (damage > 0) {
			hurt(d, damage, d.damageSources().explosion(null, null), dx, dz);
		}
	}
	private void hurt(Dummy d, float damage, net.minecraft.world.damagesource.DamageSource source, double pushX, double pushZ) {
		var connection = Minecraft.getInstance().getConnection();
		if (age - lastHit < IMMUNE_TICKS) {
			if (damage <= lastDamage) {
				return;
			}
			float extra = damage - lastDamage;
			lastDamage = damage;
			damage = extra;
		} else {
			lastDamage = damage;
			lastHit = age;
		}
		connection.handleDamageEvent(new ClientboundDamageEventPacket(d, source));
		knockX += pushX;
		knockZ += pushZ;
		float left = d.getHealth() - damage;
		if (left > 0) {
			d.setHealth(left);
		} else if (totems.get()) {
			d.setHealth(1);
			connection.handleEntityEvent(new ClientboundEntityEventPacket(d, (byte) 35));
		} else {
			d.setHealth(0);
			connection.handleEntityEvent(new ClientboundEntityEventPacket(d, (byte) 3));
			deadFor = 0;
		}
	}
	private static float weaponDamage(LocalPlayer you) {
		double[] sum = {you.getAttributeBaseValue(Attributes.ATTACK_DAMAGE), 0, 1};
		you.getMainHandItem().forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			if (!attribute.equals(Attributes.ATTACK_DAMAGE)) {
				return;
			}
			switch (modifier.operation()) {
				case ADD_VALUE -> sum[0] += modifier.amount();
				case ADD_MULTIPLIED_BASE -> sum[1] += modifier.amount();
				case ADD_MULTIPLIED_TOTAL -> sum[2] *= 1 + modifier.amount();
			}
		});
		return (float) Math.max(0, sum[0] * (1 + sum[1]) * sum[2]);
	}
	public static FakePlayer active() {
		var modules = TempleNzClient.modules();
		FakePlayer m = modules == null ? null : modules.get(FakePlayer.class);
		return m != null && m.isEnabled() && m.dummy != null ? m : null;
	}
}
