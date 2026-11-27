package nz.temple.client.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.Motion;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;

public final class Fly extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private final ModeSetting mode;
	private final NumberSetting speed;
	private final NumberSetting vertical;
	private final BoolSetting antiKick;
	private final BoolSetting spoofGround;
	private int ticks;

	public Fly() {
		super("Fly", Category.MOVEMENT, "Lets you fly. Space goes up, Shift goes down, movement keys steer. The safer modes reduce the "
				+ "chance of server corrections but cannot remove it.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Uses an elytra: jumps, opens it, and uses firework rockets from your hotbar to stay airborne. Steer with the "
						+ "camera."));
		mode = add(new ModeSetting("Mode", List.of("Vanilla", "Glide", "Conservative glide", "Grim"), "Vanilla")
				.describe("Glide has no sustained lift. Conservative glide also caps horizontal speed and disables upward movement. Grim "
						+ "only flies within a block of a boat, shulker or happy ghast, where Grim does not check movement closely; "
						+ "elsewhere you fall normally."));
		speed = add(new NumberSetting("Speed", 1.5, 0.1, 8, 0.1, " b/t")
				.describe("Horizontal flight speed, in blocks per tick."));
		vertical = add(new NumberSetting("Vertical speed", 1.0, 0.1, 5, 0.1, " b/t")
				.describe("How fast Space and Shift move you up and down."));
		group();
		antiKick = add(new BoolSetting("Anti kick", true)
				.describe("Moves you down slightly every few seconds so vanilla and plugin flight checks (NoCheatPlus, Spartan) do not kick "
						+ "you for hovering."));
		spoofGround = add(new BoolSetting("Spoof ground", false)
				.describe("Tells the server you are on the ground while flying. Helps against Matrix-style flight checks; turn off if you "
						+ "rubber-band."));
		hideWhenLegit(legit, mode, speed, vertical, antiKick, spoofGround);
		on(TickEvent.class, e -> tick());
	}

	private void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		if (legit.get()) {
			if (!nz.temple.client.util.Elytra.worn(player)) {
				return;
			}
			if (player.onGround()) {
				nz.temple.client.util.Keys.press(Minecraft.getInstance().options.keyJump, 1);
			} else if (!player.isFallFlying()) {
				nz.temple.client.util.Elytra.open(player);
			} else {
				nz.temple.client.util.Elytra.boost(player, 1.1);
			}
			return;
		}
		ticks++;
		Input k = Motion.keys(player);
		Vec3 dir = Motion.wishDirection(player).scale(speed.get());
		if (mode.is("Grim")) {
			if (!nz.temple.client.util.GrimLenience.hardCollision(player)) {
				return;
			}
			Vec3 flat = Motion.wishDirection(player).scale(speed.get());
			double y = k.jump() ? vertical.get() : k.shift() ? -vertical.get() : 0.08;
			player.setDeltaMovement(nz.temple.client.util.GrimLenience.stepTowards(player.getDeltaMovement(), new Vec3(flat.x, y, flat.z)));
			player.fallDistance = 0;
			return;
		}
		if (mode.is("Glide") || mode.is("Conservative glide")) {
			Vec3 v = player.getDeltaMovement();
			double y = Math.min(v.y, mode.is("Conservative glide") ? -0.08 : k.jump() ? vertical.get() * 0.5 : -0.12);
			if (k.shift()) {
				y = mode.is("Conservative glide") ? -0.18 : -vertical.get();
			}
			double horizontal = mode.is("Conservative glide") ? Math.min(speed.get(), 0.28) : speed.get();
			player.setDeltaMovement(dir.x * horizontal / speed.get(), y, dir.z * horizontal / speed.get());
		} else {
			double y = (k.jump() ? 1 : 0) - (k.shift() ? 1 : 0);
			if (antiKick.get() && ticks % 60 == 0) {
				y -= 0.08;
			}
			player.setDeltaMovement(dir.x, y * vertical.get(), dir.z);
		}
		player.fallDistance = 0;
		if (spoofGround.get()) {
			player.connection.send(new ServerboundMovePlayerPacket.StatusOnly(true, player.horizontalCollision));
		}
	}

	@Override
	protected void onDisable() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.setDeltaMovement(player.getDeltaMovement().x, 0, player.getDeltaMovement().z);
		}
	}
}
