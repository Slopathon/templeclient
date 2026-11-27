package nz.temple.client.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.render.WorldEsp;
import nz.temple.client.util.AimSmoother;
import nz.temple.client.util.DetachedCamera;
import nz.temple.client.util.LegitAim;
import nz.temple.client.util.Place;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

abstract class PlaceModule extends Module {
	protected final BoolSetting legit;
	private final ModeSetting mode;
	protected final NumberSetting aimSpeed;
	protected final ModeSetting block;
	protected final NumberSetting perTick;
	protected final NumberSetting delay;
	protected final NumberSetting range;
	protected final BoolSetting rotate;
	protected final BoolSetting airPlace;
	protected final BoolSetting render;
	protected final ColorSetting color;
	private record Mark(BlockPos pos, long at) {
	}
	private final List<Mark> placed = new ArrayList<>();
	private int wait;
	private int homeSlot = -1;

	private static final int RIGHT_CLICK_DELAY = 4;
	protected PlaceModule(String name, String description) {
		super(name, Category.HVH, description);
		legit = add(new BoolSetting("Legit", false)
				.describe("Places one block at a time: turns your view to a visible face, switches hotbar slots normally and clicks when "
						+ "the crosshair is on it, no faster than holding right click."));
		mode = add(new ModeSetting("Mode", List.of("Packet", "Legit"), "Packet").internal());
		mode.addListener(() -> {
			if (mode.is("Legit")) {
				legit.set(true);
				mode.set("Packet");
			}
		});
		aimSpeed = add(new NumberSetting("Aim speed", 35, 5, 90, 1, "°/t")
				.describe("How fast the view turns toward the next block.")
				.visibleWhen(this::legit));
		block = add(new ModeSetting("Block", List.of("Obsidian", "Ender chest", "Any hard", "Any"), "Obsidian")
				.describe("What to build with. Any hard uses obsidian, crying obsidian, ender chests or netherite blocks, whichever you "
						+ "have."));
		perTick = add(new NumberSetting("Blocks per tick", 4, 1, 10, 1, "")
				.describe("Maximum blocks placed per tick.")
				.visibleWhen(() -> !legit()));
		delay = add(new NumberSetting("Delay", 0, 0, 10, 1, " ticks")
				.describe("Ticks to wait between placement rounds."));
		range = add(new NumberSetting("Place range", 4.5, 2, 6, 0.1, " m")
				.describe("How far from your eyes a block may be placed."));
		rotate = add(new BoolSetting("Rotate", true).describe("Face each block before placing it.")
				.visibleWhen(() -> !legit()));
		airPlace = add(new BoolSetting("Air place", false)
				.describe("Places blocks with nothing next to them. Many servers reject this.")
				.visibleWhen(() -> !legit()));
		render = add(new BoolSetting("Render", true).describe("Highlights blocks briefly as they are placed."));
		color = add(new ColorSetting("Color", 0xFFE8C872).themed().describe("Highlight colour.").visibleWhen(render::get));
	}
	protected Predicate<ItemStack> item() {
		return switch (block.get()) {
			case "Ender chest" -> Place.item("ender_chest");
			case "Any hard" -> Place.item("obsidian", "crying_obsidian", "ender_chest", "netherite_block", "respawn_anchor");
			case "Any" -> stack -> stack.getItem() instanceof net.minecraft.world.item.BlockItem;
			default -> Place.item("obsidian");
		};
	}
	protected final boolean legit() {
		return legit.get();
	}

	protected int placeAll(LocalPlayer player, List<BlockPos> targets) {
		if (wait > 0) {
			wait--;
			return 0;
		}
		if (legit()) {
			return placeByHand(player, targets);
		}
		Minecraft mc = Minecraft.getInstance();
		int count = 0;
		int max = (int) Math.round(perTick.get());
		double r = range.get();
		for (BlockPos pos : targets) {
			if (count >= max) {
				break;
			}
			if (!Place.replaceable(mc.level, pos) || pos.distToCenterSqr(player.getEyePosition()) > r * r || needsRoom() && occupied(mc, pos)) {
				continue;
			}
			if (Place.placeWith(player, pos, item(), rotate.get(), airPlace.get())) {
				count++;
				if (render.get()) {
					placed.add(new Mark(pos.immutable(), System.currentTimeMillis()));
				}
			}
		}
		if (count > 0) {
			wait = (int) Math.round(delay.get());
		}
		return count;
	}
	protected boolean needsRoom() {
		return true;
	}

	private static boolean occupied(Minecraft mc, BlockPos pos) {
		return !mc.level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(pos),
				e -> !(e instanceof net.minecraft.world.entity.item.ItemEntity) && !e.isSpectator() && e.blocksBuilding).isEmpty();
	}
	protected boolean hasBlocks(LocalPlayer player) {
		return Place.hotbarSlot(player, item()) >= 0;
	}
	private int placeByHand(LocalPlayer player, List<BlockPos> targets) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.screen() != null || DetachedCamera.active()) {
			return 0;
		}
		double reach = Math.min(range.get(), player.blockInteractionRange());
		for (BlockPos pos : targets) {
			if (!Place.replaceable(mc.level, pos) || pos.distToCenterSqr(player.getEyePosition()) > reach * reach || needsRoom() && occupied(mc, pos)) {
				continue;
			}
			for (Direction d : Direction.values()) {
				BlockPos neighbour = pos.relative(d);
				Direction face = d.getOpposite();
				if (!Place.solid(mc.level, neighbour)) {
					continue;
				}
				Vec3 point = LegitAim.visiblePoint(player, neighbour, face, reach);
				if (point == null) {
					continue;
				}
				int slot = Place.hotbarSlot(player, item());
				if (slot < 0) {
					return 0;
				}
				if (player.getInventory().getSelectedSlot() != slot) {
					if (homeSlot < 0) {
						homeSlot = player.getInventory().getSelectedSlot();
					}
					LegitAim.select(player, slot);
					return 0;
				}
				AimSmoother.look(player, point, aimSpeed.getFloat(), 0.4f);
				BlockHitResult hit = LegitAim.blockUnderCrosshair(neighbour, face);
				if (hit != null && mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction()) {
					nz.temple.client.compat.Players.swing(player, InteractionHand.MAIN_HAND);
					if (render.get()) {
						placed.add(new Mark(pos.immutable(), System.currentTimeMillis()));
					}
					wait = Math.max(RIGHT_CLICK_DELAY, (int) Math.round(delay.get()));
					return 1;
				}
				return 0;
			}
		}
		giveBackSlot(player);
		return 0;
	}
	private void giveBackSlot(LocalPlayer player) {
		if (homeSlot >= 0 && player != null) {
			LegitAim.select(player, homeSlot);
		}
		homeSlot = -1;
	}
	@Override
	protected void onDisable() {
		placed.clear();
		wait = 0;
		giveBackSlot(Minecraft.getInstance().player);
	}
	public void collect(List<WorldEsp.Box> out) {
		if (!isEnabled() || !render.get()) {
			placed.clear();
			return;
		}
		long now = System.currentTimeMillis();
		placed.removeIf(m -> now - m.at() > 700);
		int c = color.get();
		for (Mark m : placed) {
			int alpha = (int) (255 * (1 - (now - m.at()) / 700f));
			out.add(new WorldEsp.Box(new AABB(m.pos()), (alpha << 24) | (c & 0xFFFFFF), (alpha / 4 << 24) | (c & 0xFFFFFF), 0.01f));
		}
	}
}
