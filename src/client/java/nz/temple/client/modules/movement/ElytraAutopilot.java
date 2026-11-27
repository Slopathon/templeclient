package nz.temple.client.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.client.StreamerMode;
import nz.temple.client.util.AimSmoother;
import nz.temple.client.util.Elytra;
import nz.temple.client.util.Inv;
import nz.temple.client.util.Keys;
import nz.temple.client.util.LegitAim;
import nz.temple.client.util.Place;
import nz.temple.client.util.ServerKey;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;
import nz.temple.setting.TextSetting;

import java.util.List;
import java.util.Locale;

public final class ElytraAutopilot extends Module {
	private static ElytraAutopilot instance;
	private static final float CLIMB_PITCH = -40;
	private static final float DIVE_PITCH = 36;
	private static final double CLIMB_UNTIL = 0.3;
	private static final double DIVE_UNTIL = 2.0;
	private static final float TURN = 8;
	private final TextSetting target;
	private final ModeSetting targetDimension;
	private final ModeSetting mode;
	private final NumberSetting overworldHeight;
	private final NumberSetting netherHeight;
	private final NumberSetting band;
	private final BoolSetting rescue;
	private final BoolSetting takeOff;
	private final NumberSetting swapAt;
	private final BoolSetting landWhenWorn;
	private final ModeSetting arrival;
	private final NumberSetting arrivalRadius;
	private enum State { TAKEOFF, CRUISE, RESCUE, LAND, CIRCLE }
	private State state = State.CRUISE;
	private boolean climbing;
	private int groundTicks;
	private int swapCooldown;
	private String lastWarning;
	public ElytraAutopilot() {
		super("Elytra Autopilot", Category.MOVEMENT, "Flies you to a set of coordinates with an elytra and no rockets, by pitching up and "
				+ "down to hold altitude. Rockets are only used if it drops too low. Works above the Nether roof. Set the target here, "
				+ "with \".fly <x> <z> [nether]\", or from the map.");
		instance = this;
		target = add(new TextSetting("Target", "", 40, "x z").describe("Destination as \"x z\". Can also be set with .fly or from the map."));
		targetDimension = add(new ModeSetting("Target coords are", List.of("Overworld", "Nether", "End"), "Overworld")
				.describe("Which dimension the target coordinates are in. Nether coordinates are multiplied by 8 in the Overworld, and "
						+ "Overworld coordinates divided by 8 in the Nether."));
		mode = add(new ModeSetting("Mode", List.of("No rockets", "Rockets"), "No rockets")
				.describe("No rockets glides up and down without fuel. Rockets flies level and uses a rocket whenever it slows down."));
		group("Height");
		overworldHeight = add(new NumberSetting("Overworld height", 340, 200, 800, 10, "")
				.describe("Cruising altitude in the Overworld and End. Nothing can block you above 320."));
		netherHeight = add(new NumberSetting("Nether height", 200, 140, 600, 10, "")
				.describe("Cruising altitude in the Nether, above the bedrock roof at 128."));
		band = add(new NumberSetting("Band", 40, 15, 150, 5, " b")
				.describe("How far above or below the cruising altitude it can drift before correcting."));
		rescue = add(new BoolSetting("Rocket rescue", true)
				.describe("Uses rockets from your hotbar to climb back if it drops below the band (lag, rubber-banding or a failed glide)."));
		takeOff = add(new BoolSetting("Take off", true)
				.describe("Takes off from the ground: jumps, opens the elytra and uses rockets to reach cruising altitude. Requires rockets "
						+ "in your hotbar."));
		group("Elytra");
		swapAt = add(new NumberSetting("Swap at", 12, 2, 100, 1, " uses")
				.describe("Equips a spare elytra from your inventory when the current one has this many uses left."));
		landWhenWorn = add(new BoolSetting("Land when out", true)
				.describe("Lands before your last elytra breaks when you have no spare. Off keeps flying until it breaks."));
		group("Arrival");
		arrival = add(new ModeSetting("On arrival", List.of("Land", "Circle", "Stop"), "Land")
				.describe("Land spirals down and lands. Circle keeps circling overhead. Stop gives control back to you."));
		arrivalRadius = add(new NumberSetting("Arrive within", 32, 8, 256, 4, " b").describe("How close counts as arrived."));
		on(TickEvent.class, e -> tick());
	}
	public static void flyTo(int x, int z, String dim, String label) {
		if (instance == null) {
			return;
		}
		instance.target.set(x + " " + z);
		instance.targetDimension.set(ServerKey.isNether(dim) ? "Nether" : "the_end".equals(dim) ? "End" : "Overworld");
		instance.setEnabled(false);
		instance.setEnabled(true);
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			nz.temple.client.compat.Players.message(mc.player, Component.literal("§8[§bTemple§8] §fElytra Autopilot §7flying to §f"
					+ (label != null ? label + " §7(" : "") + StreamerMode.xz(x, z) + " " + instance.targetDimension.get()
					+ (label != null ? ")" : "")));
		}
	}
	public static double[] targetIn(String dim) {
		if (instance == null || !instance.isEnabled()) {
			return null;
		}
		return instance.target(dim);
	}
	private double[] target(String dim) {
		String[] parts = target.get().trim().split("[\\s,]+");
		if (parts.length < 2) {
			return null;
		}
		double x;
		double z;
		try {
			x = Double.parseDouble(parts[0]);
			z = Double.parseDouble(parts[parts.length - 1]);
		} catch (NumberFormatException e) {
			return null;
		}
		String from = switch (targetDimension.get()) {
			case "Nether" -> "the_nether";
			case "End" -> "the_end";
			default -> "overworld";
		};
		if (from.equals(dim)) {
			return new double[] {x + 0.5, z + 0.5};
		}
		boolean pair = from.equals("overworld") && dim.equals("the_nether") || from.equals("the_nether") && dim.equals("overworld");
		if (!pair) {
			return null;
		}
		double f = ServerKey.scale(dim) / ServerKey.scale(from);
		return new double[] {x * f + 0.5, z * f + 0.5};
	}
	@Override
	protected void onEnable() {
		state = State.CRUISE;
		climbing = false;
		groundTicks = 0;
		lastWarning = null;
	}
	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		Keys.release(mc.options.keyJump);
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return;
		}
		String dim = ServerKey.dimension();
		double[] goal = target(dim);
		if (goal == null) {
			stop(target.get().isBlank() ? "no target set" : "the target is in a dimension you can't fly to from here");
			return;
		}
		boolean nether = ServerKey.isNether(dim);
		double cruise = nether ? netherHeight.get() : overworldHeight.get();
		double y = player.getY();
		if (nether && y < 128 && !player.isFallFlying()) {
			stop("in the Nether it flies above the roof: get on top of the bedrock roof first");
			return;
		}
		if (swapCooldown > 0) {
			swapCooldown--;
		}
		if (!elytraReady(player)) {
			return;
		}
		double dx = goal[0] - player.getX();
		double dz = goal[1] - player.getZ();
		double distance = Math.hypot(dx, dz);
		double speed = player.getDeltaMovement().horizontalDistance();
		if (player.onGround()) {
			if (state == State.LAND) {
				finish("landed", player);
				return;
			}
			if (++groundTicks > 3) {
				if (!takeOff.get()) {
					stop("on the ground: jump off something or turn on Take off");
					return;
				}
				if (rockets(player) < 0) {
					stop("put firework rockets in your hotbar to take off, or jump off something high");
					return;
				}
				state = State.TAKEOFF;
				Keys.press(mc.options.keyJump, 2);
			}
			return;
		}
		groundTicks = 0;
		if (!player.isFallFlying()) {
			Elytra.open(player);
			face(player, yawTo(dx, dz), 0);
			return;
		}
		if (distance < arrivalRadius.get() && state != State.LAND && state != State.CIRCLE && state != State.TAKEOFF) {
			switch (arrival.get()) {
				case "Stop" -> {
					finish("arrived", player);
					return;
				}
				case "Circle" -> state = State.CIRCLE;
				default -> state = State.LAND;
			}
			say("arrived, " + (state == State.LAND ? "landing" : "circling"));
		}
		float yaw = yawTo(dx, dz);
		float pitch;
		switch (state) {
			case TAKEOFF -> {
				pitch = -50;
				fireRocket(player, 1.6);
				if (y >= cruise - band.get() / 2) {
					state = State.CRUISE;
				}
			}
			case RESCUE -> {
				pitch = -35;
				fireRocket(player, 1.4);
				if (y >= cruise) {
					state = State.CRUISE;
					say("back at cruising height");
				}
			}
			case LAND -> {
				yaw = yawTo(dx, dz) + (float) Mth.clamp(90 * (1 - distance / (arrivalRadius.get() + 1)), 0, 80);
				double ground = y - groundBelow(player);
				pitch = ground > 12 ? 22 : ground > 4 ? 8 : -6;
			}
			default -> {
				if (state == State.CIRCLE) {
					yaw = yawTo(dx, dz) + 75;
				}
				if (rescue.get() && y < cruise - band.get() - 15 && rockets(player) >= 0) {
					state = State.RESCUE;
					say("dropped below the band, climbing back with rockets");
					pitch = -35;
				} else if (mode.is("Rockets")) {
					pitch = (float) Mth.clamp((y - cruise) * 0.6, -25, 20);
					fireRocket(player, 1.25);
				} else {
					pitch = rhythm(speed, y, cruise);
				}
			}
		}
		face(player, yaw, pitch);
	}
	private float rhythm(double speed, double y, double cruise) {
		if (climbing && speed < CLIMB_UNTIL) {
			climbing = false;
		} else if (!climbing && speed > DIVE_UNTIL) {
			climbing = true;
		}
		if (y > cruise + band.get()) {

			climbing = false;
			return 12;
		}
		return climbing ? CLIMB_PITCH : DIVE_PITCH;
	}
	private static float yawTo(double dx, double dz) {
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}
	private void face(LocalPlayer player, float yaw, float pitch) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.screen() == null) {
			float yr = (float) Math.toRadians(yaw);
			float pr = (float) Math.toRadians(pitch);
			Vec3 dir = new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr));
			AimSmoother.look(player, player.getEyePosition().add(dir.scale(64)), TURN, 0.2f);
			return;
		}
		float dy = Mth.wrapDegrees(yaw - player.getYRot());
		float dp = pitch - player.getXRot();
		float stepY = Mth.clamp(dy, -TURN, TURN);
		float stepP = Mth.clamp(dp, -TURN, TURN);
		player.setYRot(player.getYRot() + stepY);
		player.setXRot(Mth.clamp(player.getXRot() + stepP, -90, 90));
		player.yRotO += stepY;
		player.xRotO += stepP;
	}
	private static double groundBelow(LocalPlayer player) {
		var level = player.level();
		var pos = player.blockPosition().mutable();
		for (int i = 0; i < 64 && pos.getY() > level.getMinY(); i++) {
			pos.move(0, -1, 0);
			if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() || !level.getFluidState(pos).isEmpty()) {
				return pos.getY() + 1;
			}
		}
		return pos.getY() - 64;
	}
	private static int rockets(LocalPlayer player) {
		return Place.hotbarSlot(player, Place.item("firework_rocket"));
	}
	private void fireRocket(LocalPlayer player, double below) {
		if (rockets(player) < 0) {
			warn("out of firework rockets");
			return;
		}
		Elytra.boost(player, below);
	}

	private static int usesLeft(ItemStack stack) {
		return stack.is(Items.ELYTRA) ? stack.getMaxDamage() - stack.getDamageValue() - 1 : -1;
	}
	private boolean elytraReady(LocalPlayer player) {
		ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
		int left = usesLeft(worn);
		if (left > swapAt.get()) {
			return true;
		}
		int spare = spareSlot(player);
		if (spare >= 0 && swapCooldown == 0 && player.containerMenu == player.inventoryMenu) {
			Inv.pickup(spare);
			Inv.pickup(6);
			Inv.pickup(spare);
			swapCooldown = 10;
			say(left < 0 ? "put on an elytra" : "swapped in a fresh elytra");
			return usesLeft(player.getItemBySlot(EquipmentSlot.CHEST)) > 0;
		}
		if (left < 0) {
			stop("wear an elytra (or keep a spare in your inventory)");
			return false;
		}
		if (left <= 0) {
			return true;
		}
		if (landWhenWorn.get() && state != State.LAND && player.isFallFlying()) {
			state = State.LAND;
			say("last elytra is nearly broken, landing");
		}
		return true;
	}
	private int spareSlot(LocalPlayer player) {
		int best = -1;
		int bestLeft = (int) Math.round(swapAt.get());
		var slots = player.inventoryMenu.slots;
		for (int i = 9; i <= 44 && i < slots.size(); i++) {
			int left = usesLeft(slots.get(i).getItem());
			if (left > bestLeft) {
				best = i;
				bestLeft = left;
			}
		}
		return best;
	}
	private void finish(String what, LocalPlayer player) {
		say(what);
		if (TempleNzClient.notifications() != null) {
			TempleNzClient.notifications().info("Elytra Autopilot", what);
		}
		setEnabled(false);
	}
	private void stop(String why) {
		say("stopped: " + why);
		if (TempleNzClient.notifications() != null) {
			TempleNzClient.notifications().error("Elytra Autopilot", why);
		}
		setEnabled(false);
	}
	private void warn(String what) {
		if (!what.equals(lastWarning)) {
			lastWarning = what;
			say(what);
		}
	}
	private void say(String text) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			nz.temple.client.compat.Players.message(mc.player, Component.literal("§8[§bTemple§8] §fElytra Autopilot §7" + text));
		}
		TempleNzClient.LOGGER.info("Elytra Autopilot: {}", text);
	}

	public String status() {
		return String.format(Locale.ROOT, "%s climbing=%s", state, climbing);
	}
}
