package nz.temple.client.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.TempleNzClient;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;

public final class Velocity extends Module {
	private final ModeSetting mode;
	private final NumberSetting horizontal;
	private final NumberSetting vertical;
	private final NumberSetting chance;
	private final BoolSetting explosions;
	private boolean jumpQueued;
	private final BoolSetting legit;
	private boolean jumpPressed;
	private int lastHurt;
	private boolean grimHit;
	public Velocity() {
		super("Velocity", Category.COMBAT, "Changes the knockback you take from hits and explosions.");
		legit = add(new BoolSetting("Legit", false)
				.describe("Takes full knockback and reduces it by jumping as it lands, the way players do manually, occasionally missing "
						+ "the timing. Explosions are not affected."));
		mode = add(new ModeSetting("Mode", List.of("Scale", "Cancel", "Jump reset", "Tick", "Grim"), "Scale")
				.describe("Scale reduces knockback by the percentages below. Cancel ignores it completely. Jump reset takes full knockback "
						+ "and jumps as it lands. Tick scales your motion once on the tick you are hurt, for servers that send knockback "
						+ "differently. Grim takes only reductions Grim allows: a jump as the hit lands, a sprint hit on whoever is under "
						+ "your crosshair (which keeps 60%), and a little less while players or mobs are touching you."));
		horizontal = add(new NumberSetting("Horizontal", 90, 0, 100, 1, "%")
				.describe("Horizontal knockback kept. 0% removes it.")
				.visibleWhen(() -> mode.is("Scale") || mode.is("Tick")));
		vertical = add(new NumberSetting("Vertical", 100, 0, 100, 1, "%")
				.describe("Vertical knockback kept. Lower it to avoid being comboed into the air.")
				.visibleWhen(() -> mode.is("Scale") || mode.is("Tick")));
		chance = add(new NumberSetting("Chance", 100, 0, 100, 5, "%")
				.describe("How often it applies. Below 100 some hits are taken in full, which looks more natural."));
		explosions = add(new BoolSetting("Explosions", true)
				.describe("Also changes knockback from explosions (crystals, TNT, beds).")
				.visibleWhen(() -> !mode.is("Tick") && !mode.is("Jump reset") && !mode.is("Grim")));
		hideWhenLegit(legit, mode, horizontal, vertical, chance, explosions);
		on(TickEvent.class, e -> tick());
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			if (isEnabled() && grimHit) {
				grimHit = false;
				grimReduce(mc);
			}
		});
	}
	private static Velocity active() {
		var modules = TempleNzClient.modules();
		Velocity v = modules == null ? null : modules.get(Velocity.class);
		return v != null && v.isEnabled() ? v : null;
	}
	public static Vec3 knockback(Vec3 incoming, LocalPlayer player) {
		Velocity v = active();
		if (v != null && v.legit.get()) {
			v.jumpQueued = player.onGround() && nz.temple.client.util.Human.chance(0.85);
			return incoming;
		}
		if (v == null || !v.roll()) {
			return incoming;
		}
		return switch (v.mode.get()) {
			case "Scale" -> v.scale(incoming);
			case "Cancel" -> player.getDeltaMovement();
			case "Grim" -> {
				v.grimHit = true;
				yield v.grimPush(incoming, player);
			}
			case "Jump reset" -> {
				v.jumpQueued = player.onGround();
				yield incoming;
			}
			default -> incoming;
		};
	}
	public static Vec3 explosion(Vec3 push) {
		Velocity v = active();
		if (v == null || v.legit.get() || !v.explosions.get() || !v.roll()) {
			return push;
		}
		return switch (v.mode.get()) {
			case "Scale" -> v.scale(push);
			case "Cancel" -> Vec3.ZERO;
			default -> push;
		};
	}
	private Vec3 grimPush(Vec3 v, LocalPlayer player) {
		double off = nz.temple.client.util.GrimLenience.PUSH_PER_ENTITY * nz.temple.client.util.GrimLenience.pushers(player);
		return new Vec3(Math.signum(v.x) * Math.max(0, Math.abs(v.x) - off), v.y, Math.signum(v.z) * Math.max(0, Math.abs(v.z) - off));
	}
	private void grimReduce(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.gui.screen() != null) {
			return;
		}
		if (player.onGround()) {
			nz.temple.client.util.Keys.press(mc.options.keyJump, 1);
		}
		if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit && player.isSprinting()
				&& player.getAttackStrengthScale(0.5f) > 0.9f && mc.gameMode != null) {
			mc.gameMode.attack(player, hit.getEntity());
			nz.temple.client.compat.Players.swing(player, net.minecraft.world.InteractionHand.MAIN_HAND);
		}
	}
	private Vec3 scale(Vec3 v) {
		return new Vec3(v.x * horizontal.get() / 100.0, v.y * vertical.get() / 100.0, v.z * horizontal.get() / 100.0);
	}
	private boolean roll() {
		return chance.get() >= 100 || Math.random() * 100 < chance.get();
	}
	private void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		if (jumpPressed) {
			jumpPressed = false;
			Minecraft.getInstance().options.keyJump.setDown(false);
		}
		if (jumpQueued) {
			jumpQueued = false;
			if (legit.get()) {
				nz.temple.client.util.Keys.press(Minecraft.getInstance().options.keyJump, 1);
			} else if (player.onGround()) {
				player.jumpFromGround();
			}
		}
		boolean freshHit = player.hurtTime > 0 && player.hurtTime > lastHurt;
		lastHurt = player.hurtTime;
		if (!legit.get() && mode.is("Tick") && freshHit && roll()) {
			player.setDeltaMovement(scale(player.getDeltaMovement()));
		}
	}
}
