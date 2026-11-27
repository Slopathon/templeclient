package nz.temple.client.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;
import java.util.Random;

public final class AntiAFK extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private final ModeSetting action;
	private final NumberSetting baseDelay;
	private final BoolSetting lookJitter;
	private final Random random = new Random();
	private long nextAction;
	private int spinTicks;
	private int sneakTicks;
	private int walkTicks;
	private net.minecraft.world.phys.Vec3 lookAt;
	public AntiAFK() {
		super("Anti AFK", Category.PLAYER, "Prevents AFK kicks with randomised actions, including on servers with macro-check plugins.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Turns the camera smoothly like a mouse instead of in fixed steps, and jumps with your jump key."));
		action = add(new ModeSetting("Action", List.of("Random", "Spin", "Jump", "Swing", "Sneak", "Walk"), "Random")
				.describe("Which action to perform. Random mixes them so there is no repeating pattern."));
		baseDelay = add(new NumberSetting("Action delay", 25, 5, 120, 1, " s")
				.describe("Average time between actions. The actual timing is randomised."));
		lookJitter = add(new BoolSetting("Look jitter", true)
				.describe("Adds small random mouse movements to each action, which macro plugins look for."));
		hideWhenLegit(legit, lookJitter);
		on(TickEvent.class, e -> tick());
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		if (spinTicks > 0 && legit.get()) {
			if (lookAt == null) {
				lookAt = player.getEyePosition().add(net.minecraft.world.phys.Vec3.directionFromRotation(
						(float) nz.temple.client.util.Human.around(5, 10, -30, 30),
						player.getYRot() + (float) nz.temple.client.util.Human.around(0, 100, -180, 180)).scale(10));
			}
			nz.temple.client.util.AimSmoother.look(player, lookAt, 10, 0.7f);
			if (--spinTicks == 0) {
				lookAt = null;
			}
		} else if (spinTicks > 0) {
			player.setYRot(player.getYRot() + 24);
			spinTicks--;
		}
		if (sneakTicks > 0) {
			mc.options.keyShift.setDown(true);
			if (--sneakTicks == 0) {
				mc.options.keyShift.setDown(false);
			}
		}
		if (walkTicks > 0) {
			mc.options.keyUp.setDown(true);
			if (random.nextBoolean()) {
				mc.options.keyLeft.setDown(random.nextBoolean());
			}
			if (--walkTicks == 0) {
				mc.options.keyUp.setDown(false);
				mc.options.keyLeft.setDown(false);
			}
		}
		long now = System.currentTimeMillis();
		if (now < nextAction || spinTicks + sneakTicks + walkTicks > 0) {
			return;
		}
		nextAction = now + (long) (baseDelay.get() * 1000 * (0.6 + random.nextDouble() * 0.8));
		String pick = action.is("Random")
				? List.of("Spin", "Jump", "Swing", "Sneak", "Walk").get(random.nextInt(5))
				: action.get();
		switch (pick) {
			case "Spin" -> spinTicks = 10 + random.nextInt(15);
			case "Jump" -> {
				if (legit.get()) {
					nz.temple.client.util.Keys.press(mc.options.keyJump, 1);
				} else {
					player.jumpFromGround();
				}
			}
			case "Swing" -> nz.temple.client.compat.Players.swing(player, InteractionHand.MAIN_HAND);
			case "Sneak" -> sneakTicks = 20 + random.nextInt(30);
			case "Walk" -> walkTicks = 20 + random.nextInt(40);
			default -> {
			}
		}
		if (lookJitter.get() && !legit.get()) {
			player.setYRot(player.getYRot() + (random.nextFloat() - 0.5f) * 6);
			player.setXRot(Math.max(-90, Math.min(90, player.getXRot() + (random.nextFloat() - 0.5f) * 4)));
		}
	}
	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc != null) {
			mc.options.keyShift.setDown(false);
			mc.options.keyUp.setDown(false);
			mc.options.keyLeft.setDown(false);
		}
		spinTicks = sneakTicks = walkTicks = 0;
	}
}
