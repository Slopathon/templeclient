package nz.temple.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class Place {
	private Place() {
	}

	public static String path(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
	}

	public static boolean isAir(Level level, BlockPos pos) {
		return level.isEmptyBlock(pos);
	}

	public static boolean replaceable(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.isAir() || state.canBeReplaced();
	}
	public static boolean solid(Level level, BlockPos pos) {
		return !replaceable(level, pos) && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}
	public static boolean buildable(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) {
			return false;
		}
		net.minecraft.world.level.block.Block block = item.getBlock();
		if (block instanceof net.minecraft.world.level.block.FallingBlock) {
			return false;
		}
		return block.defaultBlockState().isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}
	public static int blockHotbarSlot(LocalPlayer player) {
		int held = player.getInventory().getSelectedSlot();
		if (buildable(player.getInventory().getItem(held))) {
			return held;
		}
		for (int i = 0; i < 9; i++) {
			if (buildable(player.getInventory().getItem(i))) {
				return i;
			}
		}
		return -1;
	}
	public static boolean placeAt(LocalPlayer player, BlockPos target) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gameMode == null || mc.level == null) {
			return false;
		}
		if (!replaceable(mc.level, target)) {
			return false;
		}
		int held = player.getInventory().getSelectedSlot();
		int slot = player.getInventory().getItem(held).getItem() instanceof BlockItem ? held : blockHotbarSlot(player);
		if (slot < 0) {
			return false;
		}
		for (Direction face : Direction.values()) {
			BlockPos neighbor = target.relative(face);
			if (!solid(mc.level, neighbor)) {
				continue;
			}
			Direction clickFace = face.getOpposite();
			Vec3 hit = Vec3.atCenterOf(neighbor).add(clickFace.getStepX() * 0.5, clickFace.getStepY() * 0.5, clickFace.getStepZ() * 0.5);
			int prev = player.getInventory().getSelectedSlot();
			player.getInventory().setSelectedSlot(slot);
			mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND,
					new BlockHitResult(hit, clickFace, neighbor, false));
			nz.temple.client.compat.Players.swing(player, InteractionHand.MAIN_HAND);
			player.getInventory().setSelectedSlot(prev);
			return true;
		}
		return false;
	}
	public static int hotbarSlot(LocalPlayer player, java.util.function.Predicate<ItemStack> test) {
		for (int i = 0; i < 9; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.isEmpty() && test.test(stack)) {
				return i;
			}
		}
		return -1;
	}
	public static java.util.function.Predicate<ItemStack> item(String... paths) {
		return stack -> {
			String p = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
			for (String want : paths) {
				if (p.equals(want)) {
					return true;
				}
			}
			return false;
		};
	}
	public static boolean placeWith(LocalPlayer player, BlockPos target, java.util.function.Predicate<ItemStack> item,
			boolean rotate, boolean airPlace) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gameMode == null || mc.level == null || !replaceable(mc.level, target)) {
			return false;
		}
		int slot = hotbarSlot(player, item);
		if (slot < 0) {
			return false;
		}
		for (Direction face : Direction.values()) {
			BlockPos neighbor = target.relative(face);
			if (!solid(mc.level, neighbor)) {
				continue;
			}
			Direction clickFace = face.getOpposite();
			Vec3 hit = Vec3.atCenterOf(neighbor).add(clickFace.getStepX() * 0.5, clickFace.getStepY() * 0.5, clickFace.getStepZ() * 0.5);
			return click(player, slot, new BlockHitResult(hit, clickFace, neighbor, false), rotate);
		}
		if (airPlace) {
			return click(player, slot, new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false), rotate);
		}
		return false;
	}
	public static boolean click(LocalPlayer player, int slot, BlockHitResult hit, boolean rotate) {
		Minecraft mc = Minecraft.getInstance();
		if (rotate) {
			float[] look = Rotations.toward(player.getEyePosition(), hit.getLocation(), player.getYRot());
			player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(look[0], look[1],
					player.onGround(), player.horizontalCollision));
		}
		int prev = player.getInventory().getSelectedSlot();
		player.getInventory().setSelectedSlot(slot);
		mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
		nz.temple.client.compat.Players.swing(player, InteractionHand.MAIN_HAND);
		player.getInventory().setSelectedSlot(prev);
		return true;
	}
	public static void useFacing(LocalPlayer player, int slot, Vec3 at) {
		Minecraft mc = Minecraft.getInstance();
		float[] look = Rotations.toward(player.getEyePosition(), at, player.getYRot());
		player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(look[0], look[1],
				player.onGround(), player.horizontalCollision));
		float yaw = player.getYRot();
		float pitch = player.getXRot();
		player.setYRot(look[0]);
		player.setXRot(look[1]);
		int prev = player.getInventory().getSelectedSlot();
		player.getInventory().setSelectedSlot(slot);
		mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
		player.getInventory().setSelectedSlot(prev);
		player.setYRot(yaw);
		player.setXRot(pitch);
	}
}
