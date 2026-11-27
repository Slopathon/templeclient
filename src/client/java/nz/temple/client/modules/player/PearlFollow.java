package nz.temple.client.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.TempleNzClient;
import nz.temple.client.render.WorldEsp;
import nz.temple.client.util.Inv;
import nz.temple.client.util.Place;
import nz.temple.client.util.ProjectileSim;
import nz.temple.client.util.Targets;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PearlFollow extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private final NumberSetting delay;
	private final ModeSetting pearlSource;
	private final NumberSetting maxDistance;
	private final NumberSetting tolerance;
	private final BoolSetting notify;
	private final BoolSetting render;
	private final ColorSetting color;
	private final Set<Integer> handled = new HashSet<>();

	private int pendingId = -1;
	private int waited;
	private int retryWait;
	private Vec3 landing;
	private BlockPos landingBlock;
	private Vec3 legitAim;
	private int legitReturn = -1;
	private int legitUntil;
	private int returnAt;

	public PearlFollow() {
		super("Pearl Follow", Category.PLAYER, "Throws your pearl to another player's predicted landing spot, using the fastest simulated "
				+ "trajectory that reaches it.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Turns your camera to the calculated throw, switches to the pearl normally (hotbar only), throws once aimed, then "
						+ "switches back. Slower than the instant version."));
		delay = add(new NumberSetting("Delay", 0, 0, 40, 1, " ticks").describe("Ticks to wait before copying the throw. The landing is recalculated while waiting."));
		pearlSource = add(new ModeSetting("Pearl source", List.of("Hotbar only", "Inventory + hotbar"), "Hotbar only")
				.describe("Optionally moves a pearl from your inventory into your selected hotbar slot for the throw, then puts it back."));
		maxDistance = add(new NumberSetting("Max landing distance", 48, 6, 128, 2, " m").describe("Ignore landings further away than this."));
		tolerance = add(new NumberSetting("Landing tolerance", 1.1, 0.25, 2.5, 0.05, " m").describe("Maximum difference in simulated impact "
				+ "point, while still requiring the same hit block."));
		notify = add(new BoolSetting("Notify", true).describe("Shows whether a matching trajectory was found."));
		group();
		render = add(new BoolSetting("Show landing", true).describe("Highlights the landing spot being targeted."));
		color = add(new ColorSetting("Landing color", 0xFFB06CFF).themedSecondary().describe("Landing highlight colour.").visibleWhen(render::get));
		hideWhenLegit(legit, pearlSource);
		on(TickEvent.class, e -> tick());
	}

	@Override
	protected void onDisable() {
		pendingId = -1;
		landing = null;
		landingBlock = null;
		handled.clear();
	}

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.gameMode == null || player.isDeadOrDying()
				|| mc.gui.screen() != null || player.isUsingItem()) {
			return;
		}
		if (legitAim != null) {
			int slot = Place.hotbarSlot(player, Place.item("ender_pearl"));
			nz.temple.client.util.LegitAim.Step step = nz.temple.client.util.LegitAim.useToward(player, slot,
					player.getEyePosition().add(legitAim.scale(20)), 45, 1.5f);
			if (step != nz.temple.client.util.LegitAim.Step.WORKING || player.tickCount > legitUntil) {
				legitAim = null;
				returnAt = player.tickCount + nz.temple.client.util.Human.ticks(3, 7);
			}
			return;
		}
		if (legitReturn >= 0 && player.tickCount >= returnAt) {
			player.getInventory().setSelectedSlot(legitReturn);
			legitReturn = -1;
		}
		handled.removeIf(id -> mc.level.getEntity(id) == null);
		if (pendingId < 0) {
			findPearl(mc, player);
			return;
		}
		Entity entity = mc.level.getEntity(pendingId);
		if (!(entity instanceof ThrownEnderpearl pearl)) {
			clearPending();
			return;
		}
		ProjectileSim.Flight incoming = ProjectileSim.predict(pearl, true);
		if (incoming == null || incoming.hitBlock() == null) {
			clearPending();
			return;
		}
		landing = incoming.end();
		landingBlock = incoming.hitBlock();
		if (waited++ < delay.get()) {
			return;
		}
		if (retryWait-- > 0) {
			return;
		}
		ProjectileSim.LaunchSolution solution = solve(player);
		if (solution == null && incoming.ticks() > 2) {
			retryWait = 2;
			return;
		}
		boolean thrown = solution != null && (legit.get() ? startLegit(player, solution.direction()) : throwPearl(player, solution.direction()));
		if (notify.get() && TempleNzClient.notifications() != null) {
			TempleNzClient.notifications().info("Pearl Follow", thrown
					? "Copied landing in " + solution.flight().ticks() + " ticks"
					: "No matching pearl trajectory");
		}
		handled.add(pendingId);
		clearPending();
	}

	private void findPearl(Minecraft mc, LocalPlayer player) {
		ThrownEnderpearl best = null;
		ProjectileSim.Flight bestFlight = null;
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (!(entity instanceof ThrownEnderpearl pearl) || handled.contains(pearl.getId())
					|| !(pearl.getOwner() instanceof Player owner) || owner == player) {
				continue;
			}
			ProjectileSim.Flight flight = ProjectileSim.predict(pearl, true);
			if (flight == null || flight.hitBlock() == null || flight.end().distanceTo(player.position()) > maxDistance.get()) {
				continue;
			}
			if (bestFlight == null || flight.ticks() < bestFlight.ticks()) {
				best = pearl;
				bestFlight = flight;
			}
		}
		if (best != null) {
			pendingId = best.getId();
			waited = 0;
			retryWait = 0;
			landing = bestFlight.end();
			landingBlock = bestFlight.hitBlock();
		}
	}

	private ProjectileSim.LaunchSolution solve(LocalPlayer player) {
		Vec3 motion = player.getDeltaMovement();
		Vec3 inherited = new Vec3(motion.x, player.onGround() ? 0 : motion.y, motion.z);
		return ProjectileSim.quickestLaunch(player.getEyePosition().subtract(0, 0.1, 0), inherited, 1.5,
				ProjectileSim.Physics.THROWN, player, landing, landingBlock, 100, tolerance.get());
	}

	private boolean startLegit(LocalPlayer player, Vec3 direction) {
		int slot = Place.hotbarSlot(player, Place.item("ender_pearl"));
		if (slot < 0 || player.getCooldowns().isOnCooldown(player.getInventory().getItem(slot))) {
			return false;
		}
		legitAim = direction.normalize();
		legitReturn = legitReturn >= 0 ? legitReturn : player.getInventory().getSelectedSlot();
		legitUntil = player.tickCount + 20;
		return true;
	}

	private boolean throwPearl(LocalPlayer player, Vec3 direction) {
		Minecraft mc = Minecraft.getInstance();
		int hotbar = -1;
		for (int i = 0; i < 9; i++) {
			if (player.getInventory().getItem(i).is(Items.ENDER_PEARL)) {
				hotbar = i;
				break;
			}
		}
		int inventorySlot = -1;
		int selected = player.getInventory().getSelectedSlot();
		boolean swappedInventory = false;
		if (hotbar < 0 && pearlSource.is("Inventory + hotbar") && player.containerMenu instanceof InventoryMenu
				&& player.containerMenu.getCarried().isEmpty()) {
			inventorySlot = Inv.findMain(stack -> stack.is(Items.ENDER_PEARL));
			if (inventorySlot >= 0) {
				swappedInventory = Inv.swapHotbar(inventorySlot, selected);
				hotbar = selected;
			}
		}
		if (hotbar < 0) {
			return false;
		}
		if (player.getCooldowns().isOnCooldown(player.getInventory().getItem(hotbar))) {
			if (swappedInventory) {
				Inv.swapHotbar(inventorySlot, selected);
			}
			return false;
		}
		float[] look = Targets.rotations(Vec3.ZERO, direction);
		float oldYaw = player.getYRot();
		float oldPitch = player.getXRot();
		int oldSlot = selected;
		try {
			player.connection.send(new ServerboundMovePlayerPacket.Rot(look[0], look[1], player.onGround(), player.horizontalCollision));
			player.setYRot(look[0]);
			player.setXRot(look[1]);
			player.getInventory().setSelectedSlot(hotbar);
			mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
			return true;
		} finally {
			player.getInventory().setSelectedSlot(oldSlot);
			player.connection.send(new ServerboundSetCarriedItemPacket(oldSlot));
			player.setYRot(oldYaw);
			player.setXRot(oldPitch);
			if (swappedInventory) {
				Inv.swapHotbar(inventorySlot, selected);
			}
		}
	}

	private void clearPending() {
		pendingId = -1;
		waited = 0;
		retryWait = 0;
		landing = null;
		landingBlock = null;
	}

	public void collect(List<WorldEsp.Box> out) {
		if (!isEnabled() || !render.get() || landingBlock == null) {
			return;
		}
		int c = color.get();
		out.add(new WorldEsp.Box(new AABB(landingBlock).inflate(0.04), 0xE6000000 | c & 0xFFFFFF,
				0x38000000 | c & 0xFFFFFF, 0.02f));
	}
}
