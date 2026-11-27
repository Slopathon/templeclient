package nz.temple.client.modules.world;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import nz.temple.client.TempleNzClient;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;

public final class NoBlockInteract extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private static NoBlockInteract instance;
	private final BoolSetting containers;
	private final BoolSetting doors;
	private final BoolSetting everything;
	public NoBlockInteract() {
		super("No Block Interact", Category.WORLD, "Sends right-clicks to your held item instead of the block, useful when throwing pearls "
				+ "near containers.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Holds sneak while your crosshair is on one of these blocks and you are holding a usable item, so the click goes "
						+ "to the item as a sneak-click would."));
		instance = this;
		containers = add(new BoolSetting("Containers", true)
				.describe("Chests, shulker boxes, barrels, hoppers, furnaces and other containers."));
		doors = add(new BoolSetting("Doors and buttons", false)
				.describe("Doors, trapdoors, gates, buttons and levers."));
		everything = add(new BoolSetting("Everything", false)
				.describe("Every block with a right-click action, including crafting tables and beds."));
		on(nz.temple.event.TickEvent.class, e -> tick());
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (!legit.get() || mc.player == null || mc.level == null || mc.gui.screen() != null) {
			return;
		}
		var held = mc.player.getMainHandItem();
		if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
				&& !held.isEmpty() && !(held.getItem() instanceof net.minecraft.world.item.BlockItem)
				&& filtered(mc.level.getBlockState(hit.getBlockPos()))) {
			nz.temple.client.util.Keys.press(mc.options.keyShift, 2);
		}
	}
	public static boolean blocks(BlockHitResult hit) {
		if (instance == null && TempleNzClient.modules() != null) {
			instance = TempleNzClient.modules().get(NoBlockInteract.class);
		}
		Minecraft mc = Minecraft.getInstance();
		if (instance == null || !instance.isEnabled() || instance.legit.get() || mc.player == null || mc.level == null
				|| mc.player.isShiftKeyDown()) {
			return false;
		}
		return instance.filtered(mc.level.getBlockState(hit.getBlockPos()));
	}
	private boolean filtered(BlockState state) {
		NoBlockInteract instance = this;
		if (instance.everything.get()) {
			return true;
		}
		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		if (instance.containers.get() && (id.contains("chest") || id.contains("shulker_box") || id.equals("barrel")
				|| id.equals("hopper") || id.contains("furnace") || id.equals("smoker") || id.equals("dropper")
				|| id.equals("dispenser") || id.equals("brewing_stand") || id.equals("enchanting_table")
				|| id.equals("anvil") || id.contains("chiseled_bookshelf"))) {
			return true;
		}
		return instance.doors.get() && (id.contains("door") || id.contains("gate") || id.contains("button") || id.equals("lever"));
	}
}
