package nz.temple.client.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.AimSmoother;
import nz.temple.client.util.Human;
import nz.temple.client.util.LegitAim;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public final class Macro extends Module {
	private static Macro instance;
	private final BoolSetting anchor;
	private final ModeSetting detonator;
	private final BoolSetting crystal;
	private final ModeSetting trigger;
	private final NumberSetting delay;
	private final BoolSetting humanize;
	private final NumberSetting jitter;
	private final BoolSetting aim;
	private final BoolSetting switchBack;
	private enum Step { CHARGE_SELECT, CHARGE_CLICK, BLOW_SELECT, BLOW_CLICK, CRYSTAL_SELECT, CRYSTAL_PLACE, CRYSTAL_BREAK, DONE }
	private BlockPos target;
	private Step step;
	private long at;
	private int homeSlot = -1;
	private int timeout;
	private boolean clicking;
	public Macro() {
		super("Macro", Category.HVH, "Charges and sets off a respawn anchor you place, and places and breaks an end crystal on obsidian "
				+ "you place, with real item switches and clicks.");
		instance = this;
		anchor = add(new BoolSetting("Anchor macro", true).describe("After you place a respawn anchor, charges it with glowstone and "
				+ "sets it off."));
		detonator = add(new ModeSetting("Set off with", List.of("Totem", "Previous item", "Any"), "Totem")
				.describe("What to hold when clicking the charged anchor. Totem leaves you holding a totem for the blast. Previous "
						+ "item goes back to the slot you placed the anchor from, if it is not an anchor or glowstone.")
				.visibleWhen(anchor::get));
		crystal = add(new BoolSetting("Crystal macro", true).describe("Places and breaks end crystals for you."));
		trigger = add(new ModeSetting("Crystal trigger", List.of("On obsidian place", "On crystal place"), "On obsidian place")
				.describe("On obsidian place puts a crystal on obsidian you place and breaks it. On crystal place only breaks a "
						+ "crystal you place yourself, as soon as it appears.")
				.visibleWhen(crystal::get));
		delay = add(new NumberSetting("Delay", 50, 0, 400, 5, " ms").describe("Time between each switch and click."));
		humanize = add(new BoolSetting("Humanize", true).describe("Varies each delay a little and clicks a slightly different spot on "
				+ "the block each time."));
		jitter = add(new NumberSetting("Variation", 30, 0, 150, 5, " ms").describe("How far each delay may vary either way.")
				.visibleWhen(humanize::get));
		aim = add(new BoolSetting("Aim", false).describe("Turns your view onto the block before each click when the crosshair is not "
				+ "already on it, instead of clicking where you are not looking."));
		switchBack = add(new BoolSetting("Switch back", true).describe("Goes back to the slot you started from when the play is done."));
		on(TickEvent.class, e -> tick());
	}
	public static Macro instance() {
		return instance;
	}
	@Override
	protected void onDisable() {
		target = null;
		step = null;
		homeSlot = -1;
	}
	public void placed(LocalPlayer player, Item used, BlockHitResult hit) {
		if (!isEnabled() || clicking || target != null) {
			return;
		}
		BlockPos on = hit.getBlockPos();
		BlockPos placedAt = player.level().getBlockState(on).canBeReplaced() ? on : on.relative(hit.getDirection());
		if (anchor.get() && used == Items.RESPAWN_ANCHOR) {
			start(player, placedAt, Step.CHARGE_SELECT);
		} else if (crystal.get() && trigger.is("On obsidian place") && (used == Items.OBSIDIAN || used == Items.BEDROCK)) {
			start(player, placedAt, Step.CRYSTAL_SELECT);
		} else if (crystal.get() && used == Items.END_CRYSTAL) {
			start(player, on, Step.CRYSTAL_BREAK);
		}
	}
	private void start(LocalPlayer player, BlockPos pos, Step first) {
		target = pos;
		step = first;
		homeSlot = player.getInventory().getSelectedSlot();
		timeout = 40;
		schedule();
	}
	private void schedule() {
		double ms = delay.get();
		if (humanize.get() && jitter.get() > 0) {
			ms += ThreadLocalRandom.current().nextDouble(-jitter.get(), jitter.get());
		}
		at = System.currentTimeMillis() + Math.max(0, Math.round(ms));
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (target == null || player == null || mc.level == null || mc.gameMode == null) {
			return;
		}
		if (--timeout < 0 || target.distToCenterSqr(player.getEyePosition()) > 36) {
			finish(player);
			return;
		}
		for (int guard = 0; guard < 4 && target != null && System.currentTimeMillis() >= at; guard++) {
			if (!run(mc, player)) {
				return;
			}
		}
	}
	private boolean run(Minecraft mc, LocalPlayer player) {
		BlockState state = mc.level.getBlockState(target);
		switch (step) {
			case CHARGE_SELECT -> {
				if (!(state.getBlock() instanceof RespawnAnchorBlock)) {
					return false;
				}
				if (!select(player, s -> s.is(Items.GLOWSTONE))) {
					finish(player);
					return false;
				}
				return next(Step.CHARGE_CLICK);
			}
			case CHARGE_CLICK -> {
				if (!(state.getBlock() instanceof RespawnAnchorBlock) || !player.getMainHandItem().is(Items.GLOWSTONE)) {
					finish(player);
					return false;
				}
				if (state.getValue(RespawnAnchorBlock.CHARGE) > 0) {
					return next(Step.BLOW_SELECT);
				}
				return click(mc, player, target) && next(Step.BLOW_SELECT);
			}
			case BLOW_SELECT -> {
				if (!(state.getBlock() instanceof RespawnAnchorBlock) || state.getValue(RespawnAnchorBlock.CHARGE) == 0) {
					return false;
				}
				if (!selectDetonator(player)) {
					finish(player);
					return false;
				}
				return next(Step.BLOW_CLICK);
			}
			case BLOW_CLICK -> {
				if (!(state.getBlock() instanceof RespawnAnchorBlock)) {
					finish(player);
					return false;
				}
				return click(mc, player, target) && next(Step.DONE);
			}
			case CRYSTAL_SELECT -> {
				if (!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK)) {
					return false;
				}
				if (!select(player, s -> s.is(Items.END_CRYSTAL))) {
					finish(player);
					return false;
				}
				return next(Step.CRYSTAL_PLACE);
			}
			case CRYSTAL_PLACE -> {
				if (!player.getMainHandItem().is(Items.END_CRYSTAL) || crystalOn(mc, target) != null) {
					return next(Step.CRYSTAL_BREAK);
				}
				return click(mc, player, target) && next(Step.CRYSTAL_BREAK);
			}
			case CRYSTAL_BREAK -> {
				EndCrystal c = crystalOn(mc, target);
				if (c == null) {
					return false;
				}
				if (!aimAt(player, c.getBoundingBox().getCenter(), c)) {
					return false;
				}
				mc.gameMode.attack(player, c);
				nz.temple.client.compat.Players.swing(player, InteractionHand.MAIN_HAND);
				return next(Step.DONE);
			}
			case DONE -> {
				finish(player);
				return false;
			}
		}
		return false;
	}
	private boolean next(Step s) {
		step = s;
		if (s == Step.DONE) {
			at = 0;
			return true;
		}
		schedule();
		return true;
	}
	private void finish(LocalPlayer player) {
		if (player != null && switchBack.get() && homeSlot >= 0) {
			LegitAim.select(player, homeSlot);
		}
		target = null;
		step = null;
		homeSlot = -1;
	}
	private static boolean select(LocalPlayer player, Predicate<ItemStack> item) {
		if (item.test(player.getMainHandItem())) {
			return true;
		}
		for (int i = 0; i < 9; i++) {
			if (item.test(player.getInventory().getItem(i))) {
				LegitAim.select(player, i);
				return true;
			}
		}
		return false;
	}
	private boolean selectDetonator(LocalPlayer player) {
		Predicate<ItemStack> notGlow = s -> !s.is(Items.GLOWSTONE) && !s.is(Items.RESPAWN_ANCHOR);
		if (detonator.is("Totem") && select(player, s -> s.is(Items.TOTEM_OF_UNDYING))) {
			return true;
		}
		if (detonator.is("Previous item") && homeSlot >= 0 && notGlow.test(player.getInventory().getItem(homeSlot))) {
			LegitAim.select(player, homeSlot);
			return true;
		}
		return select(player, notGlow);
	}
	private boolean click(Minecraft mc, LocalPlayer player, BlockPos pos) {
		BlockHitResult hit;
		if (mc.hitResult instanceof BlockHitResult crosshair && crosshair.getType() == HitResult.Type.BLOCK
				&& crosshair.getBlockPos().equals(pos)) {
			hit = crosshair;
		} else {
			Vec3 point = facePoint(pos);
			if (!aimAt(player, point, null)) {
				return false;
			}
			hit = new BlockHitResult(point, Direction.UP, pos, false);
		}
		clicking = true;
		try {
			if (mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction()) {
				nz.temple.client.compat.Players.swing(player, InteractionHand.MAIN_HAND);
			}
		} finally {
			clicking = false;
		}
		return true;
	}
	private Vec3 facePoint(BlockPos pos) {
		double ox = 0.5;
		double oz = 0.5;
		if (humanize.get()) {
			ox = Human.around(0.5, 0.15, 0.15, 0.85);
			oz = Human.around(0.5, 0.15, 0.15, 0.85);
		}
		return new Vec3(pos.getX() + ox, pos.getY() + 1, pos.getZ() + oz);
	}
	private boolean aimAt(LocalPlayer player, Vec3 point, EndCrystal entity) {
		if (!aim.get() || entity != null && LegitAim.onEntity(entity)) {
			return true;
		}
		AimSmoother.look(player, point, 40f, 0.25f);
		return AimSmoother.offBy(player, point) < 4f;
	}
	private static EndCrystal crystalOn(Minecraft mc, BlockPos base) {
		AABB above = new AABB(base.above()).inflate(0.2, 1, 0.2);
		List<EndCrystal> found = mc.level.getEntitiesOfClass(EndCrystal.class, above, c -> c.isAlive());
		return found.isEmpty() ? null : found.get(0);
	}
}
