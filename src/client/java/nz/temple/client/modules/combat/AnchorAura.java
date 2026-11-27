package nz.temple.client.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.render.WorldEsp;
import nz.temple.client.util.AimSmoother;
import nz.temple.client.util.Blast;
import nz.temple.client.util.DetachedCamera;
import nz.temple.client.util.LegitAim;
import nz.temple.client.util.Place;
import nz.temple.client.util.Rotations;
import nz.temple.client.util.Targets;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;
import nz.temple.social.FriendManager;

import java.util.List;

public final class AnchorAura extends Module {
	private final FriendManager friends;
	private final BoolSetting legit;
	private final ModeSetting mode;
	private final NumberSetting aimSpeed;
	private final NumberSetting aimSmoothing;
	private final NumberSetting targetRange;
	private final NumberSetting placeRange;
	private final NumberSetting minDamage;
	private final NumberSetting maxSelf;
	private final BoolSetting antiSuicide;
	private final NumberSetting delay;
	private final BoolSetting rotate;
	private final BoolSetting useExisting;
	private final BoolSetting pauseEating;
	private final BoolSetting render;
	private final ColorSetting color;
	private BlockPos anchor;
	private int wait;
	private int stuck;
	private int homeSlot = -1;
	public AnchorAura(FriendManager friends) {
		super("Anchor Aura", Category.HVH, "Places, charges and detonates respawn anchors next to enemies. Anchors only explode outside the "
				+ "Nether. Legit mode uses your real view and only clicks what your crosshair is on.");
		this.friends = friends;
		legit = add(new BoolSetting("Legit", false)
				.describe("Looks at the block, switches hotbar slots normally and only clicks when the crosshair is on it, within vanilla "
						+ "reach and never through walls."));
		mode = add(new ModeSetting("Mode", List.of("Packet", "Legit"), "Packet").internal());
		mode.addListener(() -> {
			if (mode.is("Legit")) {
				legit.set(true);
				mode.set("Packet");
			}
		});
		aimSpeed = add(new NumberSetting("Aim speed", 30, 5, 90, 1, "°/t")
				.describe("How fast the view turns toward the next block.")
				.visibleWhen(this::legit));
		aimSmoothing = add(new NumberSetting("Aim smoothing", 40, 0, 100, 5, "%")
				.describe("Higher values slow the turn down more as it approaches the target.")
				.visibleWhen(this::legit));
		targetRange = add(new NumberSetting("Target range", 9, 3, 12, 0.5, " m").describe("How far away an enemy can be."));
		placeRange = add(new NumberSetting("Place range", 4.5, 2, 6, 0.1, " m")
				.describe("How far away the anchor may go. Legit mode never goes past your real reach."));
		minDamage = add(new NumberSetting("Min damage", 7, 0, 36, 0.5, " hp").describe("Only use a spot that deals at least this much."));
		maxSelf = add(new NumberSetting("Max self damage", 7, 0, 36, 0.5, " hp").describe("Never use a spot that hurts you more than this."));
		antiSuicide = add(new BoolSetting("Anti suicide", true).describe("Never use a spot that could kill you."));
		delay = add(new NumberSetting("Delay", 1, 0, 10, 1, " ticks").describe("Ticks between each place, charge and detonate step."));
		rotate = add(new BoolSetting("Rotate", true).describe("Face the anchor before using it.").visibleWhen(() -> !legit()));
		useExisting = add(new BoolSetting("Use existing anchors", true)
				.describe("Also charge and set off anchors that are already next to the enemy."));
		pauseEating = add(new BoolSetting("Pause while eating", true).describe("Hold off while you eat or drink."));
		render = add(new BoolSetting("Render", true).describe("Highlight the anchor being used."));
		color = add(new ColorSetting("Color", 0xFFB06CFF).themed().describe("Highlight colour.").visibleWhen(render::get));
		hideWhenLegit(legit, rotate);
		on(TickEvent.class, e -> tick());
	}
	private boolean legit() {
		return legit.get();
	}
	@Override
	protected void onDisable() {
		anchor = null;
		wait = 0;
		giveBackSlot(Minecraft.getInstance().player);
	}

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.gameMode == null || player.isDeadOrDying()
				|| mc.level.dimension() == Level.NETHER || pauseEating.get() && player.isUsingItem()
				|| legit() && (mc.gui.screen() != null || DetachedCamera.active())) {
			anchor = null;
			return;
		}
		if (wait-- > 0) {
			return;
		}
		List<Player> enemies = Targets.enemies(player, targetRange.get(), friends, false);
		if (enemies.isEmpty()) {
			anchor = null;
			giveBackSlot(player);
			return;
		}
		double range = legit() ? Math.min(placeRange.get(), player.blockInteractionRange()) : placeRange.get();
		if (anchor != null && (anchor.distToCenterSqr(player.getEyePosition()) > sq(range) || ++stuck > 40)) {
			anchor = null;
		}
		if (anchor == null) {
			stuck = 0;
			anchor = choose(mc, player, enemies, range);
			if (anchor == null) {
				giveBackSlot(player);
				return;
			}
		}
		BlockState state = mc.level.getBlockState(anchor);
		boolean isAnchor = state.getBlock() instanceof RespawnAnchorBlock;
		if (legit()) {
			legitStep(mc, player, state, isAnchor, range);
			return;
		}
		if (!isAnchor) {
			if (!Place.replaceable(mc.level, anchor)) {
				anchor = null;
				return;
			}
			if (Place.placeWith(player, anchor, Place.item("respawn_anchor"), rotate.get(), false)) {
				wait = (int) Math.round(delay.get());
			} else {
				anchor = null;
			}
			return;
		}
		int charge = state.getValue(RespawnAnchorBlock.CHARGE);
		if (charge == 0) {
			int glow = Place.hotbarSlot(player, Place.item("glowstone"));
			if (glow < 0) {
				return;
			}
			use(player, glow);
		} else {
			int other = detonator(player);
			if (other < 0 || Place.item("glowstone").test(player.getOffhandItem())) {
				return;
			}
			use(player, other);
			anchor = null;
		}
		wait = (int) Math.round(delay.get());
	}
	private void legitStep(Minecraft mc, LocalPlayer player, BlockState state, boolean isAnchor, double range) {
		if (!isAnchor) {
			if (!Place.replaceable(mc.level, anchor)) {
				anchor = null;
				return;
			}
			for (Direction d : Direction.values()) {
				BlockPos neighbour = anchor.relative(d);
				Direction face = d.getOpposite();
				if (!Place.solid(mc.level, neighbour)) {
					continue;
				}
				Vec3 point = LegitAim.visiblePoint(player, neighbour, face, range);
				if (point == null) {
					continue;
				}
				if (holding(player, "respawn_anchor")) {
					AimSmoother.look(player, point, aimSpeed.getFloat(), aimSmoothing.getFloat() / 100f);
					click(mc, player, LegitAim.blockUnderCrosshair(neighbour, face));
				}
				return;
			}
			anchor = null;
			return;
		}
		Vec3 point = LegitAim.visiblePoint(player, anchor, range);
		if (point == null) {
			anchor = null;
			return;
		}
		boolean charged = state.getValue(RespawnAnchorBlock.CHARGE) > 0;
		if (charged ? holdingDetonator(player) : holding(player, "glowstone")) {
			AimSmoother.look(player, point, aimSpeed.getFloat(), aimSmoothing.getFloat() / 100f);
			if (click(mc, player, LegitAim.blockUnderCrosshair(anchor, null)) && charged) {
				anchor = null;
			}
		}
	}
	private boolean click(Minecraft mc, LocalPlayer player, BlockHitResult hit) {
		if (hit == null || player.isShiftKeyDown()) {
			return false;
		}
		if (mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction()) {
			nz.temple.client.compat.Players.swing(player, InteractionHand.MAIN_HAND);
			wait = (int) Math.round(delay.get());
			return true;
		}
		return false;
	}
	private boolean holding(LocalPlayer player, String item) {
		if (Place.item(item).test(player.getMainHandItem())) {
			return true;
		}
		int slot = Place.hotbarSlot(player, Place.item(item));
		if (slot >= 0) {
			rememberSlot(player);
			LegitAim.select(player, slot);
		}
		return false;
	}
	private boolean holdingDetonator(LocalPlayer player) {
		if (!Place.item("glowstone").test(player.getMainHandItem())) {
			return true;
		}
		int slot = homeSlot >= 0 && !Place.item("glowstone").test(player.getInventory().getItem(homeSlot)) ? homeSlot : detonator(player);
		if (slot >= 0) {
			LegitAim.select(player, slot);
		}
		return false;
	}
	private void rememberSlot(LocalPlayer player) {
		if (homeSlot < 0) {
			homeSlot = player.getInventory().getSelectedSlot();
		}
	}
	private void giveBackSlot(LocalPlayer player) {
		if (player != null && homeSlot >= 0 && legit()) {
			LegitAim.select(player, homeSlot);
		}
		homeSlot = -1;
	}

	private static int detonator(LocalPlayer player) {
		for (int i = 0; i < 9; i++) {
			if (!Place.item("glowstone").test(player.getInventory().getItem(i))) {
				return i;
			}
		}
		return -1;
	}

	private void use(LocalPlayer player, int slot) {
		Vec3 top = new Vec3(anchor.getX() + 0.5, anchor.getY() + 1, anchor.getZ() + 0.5);
		if (rotate.get()) {
			float[] look = Rotations.toward(player.getEyePosition(), top, player.getYRot());
			player.connection.send(new ServerboundMovePlayerPacket.Rot(look[0], look[1], player.onGround(), player.horizontalCollision));
		}
		Place.click(player, slot, new BlockHitResult(top, Direction.UP, anchor, false), false);
	}
	private BlockPos choose(Minecraft mc, LocalPlayer player, List<Player> enemies, double range) {
		boolean haveAnchors = Place.hotbarSlot(player, Place.item("respawn_anchor")) >= 0;
		Vec3 eye = player.getEyePosition();
		int r = (int) Math.ceil(range);
		BlockPos origin = BlockPos.containing(eye);
		BlockPos best = null;
		float bestScore = 0;
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
			if (pos.distToCenterSqr(eye) > range * range) {
				continue;
			}
			BlockState state = mc.level.getBlockState(pos);
			boolean existing = state.getBlock() instanceof RespawnAnchorBlock;
			if (existing ? !useExisting.get() : !haveAnchors || !Place.replaceable(mc.level, pos) || !supported(mc, pos)
					|| !mc.level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(pos), e -> e.blocksBuilding).isEmpty()) {
				continue;
			}
			Vec3 center = Vec3.atCenterOf(pos);
			float damage = 0;
			for (Player enemy : enemies) {
				if (enemy.distanceToSqr(center) < 100) {
					damage = Math.max(damage, Blast.damage(enemy, center, Blast.ANCHOR));
				}
			}
			if (damage < minDamage.get()) {
				continue;
			}
			float self = Blast.damage(player, center, Blast.ANCHOR);
			if (self > maxSelf.get() || antiSuicide.get() && self >= player.getHealth() + player.getAbsorptionAmount() - 1) {
				continue;
			}
			if (legit() && !reachableByHand(player, pos, existing, range)) {
				continue;
			}
			float score = damage - self * 0.5f;
			if (score > bestScore) {
				bestScore = score;
				best = pos.immutable();
			}
		}
		return best;
	}
	private static boolean reachableByHand(LocalPlayer player, BlockPos pos, boolean existing, double range) {
		if (existing) {
			return LegitAim.visiblePoint(player, pos, range) != null;
		}
		Minecraft mc = Minecraft.getInstance();
		for (Direction d : Direction.values()) {
			BlockPos neighbour = pos.relative(d);
			if (Place.solid(mc.level, neighbour) && LegitAim.visiblePoint(player, neighbour, d.getOpposite(), range) != null) {
				return true;
			}
		}
		return false;
	}
	private static boolean supported(Minecraft mc, BlockPos pos) {
		for (Direction d : Direction.values()) {
			if (Place.solid(mc.level, pos.relative(d))) {
				return true;
			}
		}
		return false;
	}
	private static double sq(double v) {
		return v * v;
	}
	public void collect(List<WorldEsp.Box> out) {
		if (isEnabled() && render.get() && anchor != null) {
			int c = color.get();
			out.add(new WorldEsp.Box(new AABB(anchor), 0xFF000000 | (c & 0xFFFFFF), (60 << 24) | (c & 0xFFFFFF), 0.01f));
		}
	}
}
