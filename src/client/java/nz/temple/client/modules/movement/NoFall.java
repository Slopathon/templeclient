package nz.temple.client.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.mixin.MovePacketAccessor;
import nz.temple.client.util.Place;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;

public final class NoFall extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private static final double SAFE_FALL = 3.0;
	private static NoFall instance;
	private final ModeSetting mode;
	private final NumberSetting trigger;
	private final BoolSetting silentLook;
	private final BoolSetting pickUp;
	private BlockPos water;
	private int waterTicks;
	private int previousSlot = -1;
	private float previousPitch = Float.NaN;
	private int restoreTicks;
	public NoFall() {
		super("No Fall", Category.MOVEMENT, "Prevents fall damage. Packet tells the server you landed; Water bucket places water under you.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Uses a water bucket and turns your camera down to place it, like Water bucket mode with Silent look off."));
		mode = add(new ModeSetting("Mode", List.of("Packet", "Water bucket"), "Packet")
				.describe("Packet marks your normal movement packets as on the ground, so the fall is not counted. It sends no extra "
						+ "packets, so it works with TP Aura, Blink, Criticals and flight. Water bucket places water from your hotbar just "
						+ "before you land, then picks it back up."));
		trigger = add(new NumberSetting("Trigger fall", 2, 1, 2.9, 0.1, " m")
				.describe("Minimum fall distance before it acts. Short drops are left alone so jump crits still work.")
				.visibleWhen(() -> mode.is("Packet")));
		silentLook = add(new BoolSetting("Silent look", false)
				.describe("Aims the bucket server-side only and leaves your camera alone. Off turns your view down.")
				.visibleWhen(() -> mode.is("Water bucket")));
		pickUp = add(new BoolSetting("Pick water up", true)
				.describe("Picks the water back up after landing.")
				.visibleWhen(() -> bucket()));
		hideWhenLegit(legit, mode, trigger, silentLook);
		on(TickEvent.class, e -> tick());
		instance = this;
	}
	@Override
	protected void onDisable() {
		finish(Minecraft.getInstance().player);
	}
	private static double serverFall;
	private static double lastSentY = Double.NaN;
	private static boolean bypass;
	public static void untouched(Runnable send) {
		bypass = true;
		try {
			send.run();
		} finally {
			bypass = false;
		}
	}
	private boolean bucket() {
		return legit.get() || mode.is("Water bucket");
	}
	private boolean silent() {
		return silentLook.get() && !legit.get();
	}
	public static void outgoing(Packet<?> packet) {
		if (!(packet instanceof ServerboundMovePlayerPacket move)) {
			return;
		}
		double drop = 0;
		if (move.hasPosition()) {
			double y = move.getY(0);
			drop = Double.isNaN(lastSentY) ? 0 : Math.max(0, lastSentY - y);
			lastSentY = y;
		}
		NoFall self = instance;
		LocalPlayer player = Minecraft.getInstance().player;
		if (!move.isOnGround() && !bypass && self != null && self.isEnabled() && self.mode.is("Packet") && !self.legit.get() && player != null
				&& player.isAlive() && !player.isFallFlying() && !player.getAbilities().flying && !player.isSpectator()
				&& serverFall + drop > self.trigger.get()) {
			((MovePacketAccessor) move).temple$setOnGround(true);
		}
		if (move.isOnGround() || player != null && (player.isInWater() || player.isFallFlying())) {
			serverFall = 0;
		} else {
			serverFall += drop;
		}
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.gameMode == null || !bucket()) {
			return;
		}
		if (water != null) {
			collect(mc, player);
			return;
		}
		if (restoreTicks > 0) {
			easeBack(player);
		}
		if (player.onGround() || player.isInWater() || player.isInLava() || player.isFallFlying() || player.getAbilities().flying
				|| player.onClimbable() || player.getDeltaMovement().y > -0.2 || mc.gui.screen() != null) {
			if (restoreTicks == 0 && (!Float.isNaN(previousPitch) || previousSlot >= 0)) {
				finish(player);
			}
			return;
		}
		double reach = player.blockInteractionRange();
		Vec3 eye = player.getEyePosition();
		BlockHitResult ground = mc.level.clip(new ClipContext(eye, eye.add(0, -(reach + 12), 0),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
		if (ground.getType() != HitResult.Type.BLOCK || mc.level.getFluidState(ground.getBlockPos()).is(FluidTags.WATER)) {
			return;
		}
		double drop = player.getY() - ground.getLocation().y;
		if (player.fallDistance + drop <= SAFE_FALL + 0.5) {
			return;
		}
		InteractionHand hand = bucketHand(player);
		if (hand == null) {
			return;
		}
		if (!silent()) {
			if (Float.isNaN(previousPitch)) {
				previousPitch = player.getXRot();
			}
			float pitch = Mth.approach(player.getXRot(), 90f, 30f);
			player.setXRot(pitch);
			player.xRotO = pitch;
		}
		if (eye.distanceTo(ground.getLocation()) > reach - 0.2 || !silent() && player.getXRot() < 80f) {
			return;
		}
		if (use(mc, player, hand, 90f)) {
			water = ground.getBlockPos().relative(ground.getDirection());
			waterTicks = 0;
		}
	}
	private void collect(Minecraft mc, LocalPlayer player) {
		waterTicks++;
		boolean landed = player.onGround() || player.isInWater();
		if (waterTicks > 60 || !landed && waterTicks > 30) {
			finish(player);
			return;
		}
		if (!landed || waterTicks < 2) {
			return;
		}
		if (pickUp.get() && mc.level.getFluidState(water).isSource()) {
			InteractionHand hand = emptyBucketHand(player);
			if (hand != null) {
				if (!silent()) {
					player.setXRot(90f);
					player.xRotO = 90f;
				}
				use(mc, player, hand, 90f);
			}
		}
		finish(player);
	}
	private boolean use(Minecraft mc, LocalPlayer player, InteractionHand hand, float pitch) {
		float real = player.getXRot();
		player.setXRot(pitch);
		boolean ok = mc.gameMode.useItem(player, hand).consumesAction();
		if (silent()) {
			player.setXRot(real);
		}
		if (ok) {
			nz.temple.client.compat.Players.swing(player, hand);
		}
		return ok;
	}
	private InteractionHand bucketHand(LocalPlayer player) {
		return handFor(player, "water_bucket");
	}
	private InteractionHand emptyBucketHand(LocalPlayer player) {
		return handFor(player, "bucket");
	}
	private InteractionHand handFor(LocalPlayer player, String item) {
		var test = Place.item(item);
		if (test.test(player.getOffhandItem())) {
			return InteractionHand.OFF_HAND;
		}
		if (test.test(player.getMainHandItem())) {
			return InteractionHand.MAIN_HAND;
		}
		int slot = Place.hotbarSlot(player, test);
		if (slot < 0) {
			return null;
		}
		if (previousSlot < 0) {
			previousSlot = player.getInventory().getSelectedSlot();
		}
		player.getInventory().setSelectedSlot(slot);
		return InteractionHand.MAIN_HAND;
	}
	private void finish(LocalPlayer player) {
		water = null;
		waterTicks = 0;
		if (player != null && previousSlot >= 0) {
			player.getInventory().setSelectedSlot(previousSlot);
		}
		previousSlot = -1;
		restoreTicks = Float.isNaN(previousPitch) ? 0 : 6;
	}
	private void easeBack(LocalPlayer player) {
		if (Float.isNaN(previousPitch) || --restoreTicks <= 0) {
			if (!Float.isNaN(previousPitch)) {
				player.setXRot(previousPitch);
				player.xRotO = previousPitch;
			}
			previousPitch = Float.NaN;
			restoreTicks = 0;
			return;
		}
		float pitch = Mth.approach(player.getXRot(), previousPitch, 20f);
		player.setXRot(pitch);
		player.xRotO = pitch;
	}
}
