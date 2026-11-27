package nz.temple.client.modules.player;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import nz.temple.client.TempleNzClient;
import nz.temple.client.util.WorldEvents;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.NumberSetting;

import java.util.concurrent.ConcurrentHashMap;

public final class StashDisconnect extends Module {
	private final BoolSetting shulkers;
	private final BoolSetting spawners;
	private final BoolSetting amethyst;
	private final BoolSetting amethystBuds;
	private final BoolSetting candles;
	private final NumberSetting grace;
	private final NumberSetting minDistance;
	private final BoolSetting ignoreOwn;
	private final BoolSetting disableAfter;
	private final ConcurrentHashMap<Long, Long> loadedAt = new ConcurrentHashMap<>();
	private boolean leaving;
	private volatile String pendingReason;
	public StashDisconnect() {
		super("Stash Disconnect", Category.PLAYER, "Disconnects when a shulker box, spawner, amethyst or candle appears in a chunk you "
				+ "already had loaded, which usually means someone is building nearby.");
		shulkers = add(new BoolSetting("Shulker boxes", true).describe("Any colour of shulker box."));
		spawners = add(new BoolSetting("Spawners", true).describe("Monster spawners."));
		amethyst = add(new BoolSetting("Amethyst blocks", true).describe("Blocks of amethyst."));
		amethystBuds = add(new BoolSetting("Amethyst buds", false)
				.describe("Also counts amethyst buds and clusters. Off by default since they grow naturally in geodes."));
		candles = add(new BoolSetting("Candles", true).describe("Candles of any colour, including candles on cakes."));
		group();
		grace = add(new NumberSetting("Chunk grace", 3, 0, 20, 0.5, " s")
				.describe("A chunk must be loaded this long before changes in it count. Covers anti-xray revealing hidden blocks as you "
						+ "arrive."));
		minDistance = add(new NumberSetting("Ignore within", 0, 0, 16, 1, " m")
				.describe("Ignores blocks this close to you, for example spawners revealed by anti-xray as you mine toward them. 0 counts "
						+ "everything."));
		ignoreOwn = add(new BoolSetting("Ignore my own", true)
				.describe("Ignores blocks you just placed, such as a shulker box you set down."));
		disableAfter = add(new BoolSetting("Turn off after", true)
				.describe("Turns the module off after it disconnects you, so rejoining does not disconnect you again."));
		WorldEvents.onBlockChanged(this::changed);
		on(nz.temple.event.TickEvent.class, e -> tick());
		ClientChunkEvents.CHUNK_LOAD.register((lvl, chunk) -> loadedAt.put(chunk.getPos().pack(), System.currentTimeMillis()));
		ClientChunkEvents.CHUNK_UNLOAD.register((lvl, chunk) -> loadedAt.remove(chunk.getPos().pack()));
	}
	@Override
	protected void onEnable() {
		leaving = false;
		pendingReason = null;
	}
	private boolean watched(BlockState state) {
		Block b = state.getBlock();
		return shulkers.get() && state.is(BlockTags.SHULKER_BOXES)
				|| spawners.get() && b == Blocks.SPAWNER
				|| amethyst.get() && b == Blocks.AMETHYST_BLOCK
				|| amethystBuds.get() && (b == Blocks.AMETHYST_CLUSTER || b == Blocks.LARGE_AMETHYST_BUD
						|| b == Blocks.MEDIUM_AMETHYST_BUD || b == Blocks.SMALL_AMETHYST_BUD)
				|| candles.get() && (state.is(BlockTags.CANDLES) || state.is(BlockTags.CANDLE_CAKES));
	}
	private void changed(BlockPos pos, BlockState oldState, BlockState newState) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || leaving || mc.level == null || mc.player == null) {
			return;
		}
		if (!watched(newState) || oldState.getBlock() == newState.getBlock() || watched(oldState)) {
			return;
		}
		Long since = loadedAt.get(ChunkPos.pack(pos));
		if (since == null || System.currentTimeMillis() - since < grace.get() * 1000) {
			return;
		}
		double dist = Math.sqrt(mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
		if (dist < minDistance.get()) {
			return;
		}
		if (ignoreOwn.get() && dist < 7 && (holds(mc.player.getItemInHand(InteractionHand.MAIN_HAND), newState)
				|| holds(mc.player.getItemInHand(InteractionHand.OFF_HAND), newState))) {
			return;
		}
		leaving = true;
		String what = newState.getBlock().getName().getString();
		String where = pos.getX() + " " + pos.getY() + " " + pos.getZ();
		TempleNzClient.LOGGER.info("Stash Disconnect: {} appeared at {} ({} m away)", what, where, Math.round(dist));
		pendingReason = "Temple.nz Stash Disconnect: " + what + " appeared at " + nz.temple.client.modules.client.StreamerMode.alert(pos.getX(), pos.getY(), pos.getZ())
				+ " (" + Math.round(dist) + " m away)";
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		String reason = pendingReason;
		if (reason == null) {
			return;
		}
		pendingReason = null;
		if (disableAfter.get()) {
			setEnabled(false);
		}
		if (mc.level != null) {
			AutoReconnect.leftOnPurpose();
			mc.disconnectFromWorld(Component.literal(reason));
		}
	}
	private static boolean holds(ItemStack stack, BlockState state) {
		if (!(stack.getItem() instanceof BlockItem item)) {
			return false;
		}
		Block held = item.getBlock();
		Block placed = state.getBlock();
		return held == placed || state.is(BlockTags.CANDLE_CAKES) && held.defaultBlockState().is(BlockTags.CANDLES);
	}
}
