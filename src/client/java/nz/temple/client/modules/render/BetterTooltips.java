package nz.temple.client.modules.render;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.saveddata.maps.MapId;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class BetterTooltips extends Module {
	private static volatile BetterTooltips instance;
	private final BoolSetting shulkers;
	private final BoolSetting enderChest;
	private final BoolSetting maps;
	private final NumberSetting mapSize;
	private final BoolSetting food;
	private final BoolSetting shiftOnly;
	private List<ItemStack> ender;
	public BetterTooltips() {
		super("Better Tooltips", Category.RENDER, "Adds previews to item tooltips: shulker box contents, your ender chest, filled maps and "
				+ "food values.");
		shulkers = add(new BoolSetting("Shulker preview", true).describe("Shows a shulker box's contents as an item grid, tinted to match "
				+ "the box."));
		enderChest = add(new BoolSetting("Ender chest preview", true)
				.describe("Shows what was in your ender chest the last time you opened it."));
		maps = add(new BoolSetting("Map preview", true).describe("Shows the map image for filled maps."));
		mapSize = add(new NumberSetting("Map size", 100, 50, 200, 10, "%").describe("Size of the map preview.").visibleWhen(maps::get));
		food = add(new BoolSetting("Food values", true).describe("Hunger and saturation restored by food."));
		shiftOnly = add(new BoolSetting("Only while holding Shift", false).describe("Only shows previews while Shift is held."));
		on(TickEvent.class, e -> rememberEnderChest());
		ClientTooltipComponentCallback.EVENT.register(data -> {
			if (data instanceof ContainerData c) {
				return new ContainerClient(c);
			}
			if (data instanceof MapData m) {
				return new MapClient(m);
			}
			return null;
		});
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> foodLine(stack, lines));
		instance = this;
	}
	private boolean showing() {
		return isEnabled() && (!shiftOnly.get() || Minecraft.getInstance().hasShiftDown());
	}
	public static Optional<TooltipComponent> image(ItemStack stack) {
		BetterTooltips m = instance;
		if (m == null || !m.showing() || stack.isEmpty()) {
			return Optional.empty();
		}
		if (m.shulkers.get() && stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock box) {
			ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
			if (contents == null || contents.nonEmptyItemCopyStream().findAny().isEmpty()) {
				return Optional.empty();
			}
			net.minecraft.core.NonNullList<ItemStack> slots = net.minecraft.core.NonNullList.withSize(27, ItemStack.EMPTY);
			contents.copyInto(slots);
			int tint = box.getColor() == null ? 0x8B5E9A : box.getColor().getTextureDiffuseColor() & 0xFFFFFF;
			return Optional.of(new ContainerData(new ArrayList<>(slots), tint));
		}
		if (m.enderChest.get() && stack.is(Items.ENDER_CHEST) && m.ender != null) {
			return Optional.of(new ContainerData(m.ender, 0x1F5C4E));
		}
		if (m.maps.get() && stack.is(Items.FILLED_MAP)) {
			MapId id = stack.get(DataComponents.MAP_ID);
			if (id != null) {
				return Optional.of(new MapData(id, m.mapSize.getFloat() / 100f));
			}
		}
		return Optional.empty();
	}
	public static boolean hideContainerText() {
		BetterTooltips m = instance;
		return m != null && m.showing() && m.shulkers.get();
	}
	private void foodLine(ItemStack stack, List<Component> lines) {
		if (!isEnabled() || !food.get()) {
			return;
		}
		var props = stack.get(DataComponents.FOOD);
		if (props == null) {
			return;
		}
		lines.add(Math.min(1, lines.size()), Component.literal(props.nutrition() + " hunger  ·  " + String.format(java.util.Locale.ROOT,
				"%.1f", props.saturation()) + " saturation").withStyle(ChatFormatting.GOLD));
	}
	private void rememberEnderChest() {
		Minecraft mc = Minecraft.getInstance();
		if (!(mc.gui.screen() instanceof ContainerScreen screen) || !(screen.getMenu() instanceof ChestMenu menu)) {
			return;
		}
		if (!(screen.getTitle().getContents() instanceof TranslatableContents t) || !t.getKey().equals("container.enderchest")) {
			return;
		}
		List<ItemStack> copy = new ArrayList<>();
		for (int i = 0; i < 27 && i < menu.slots.size(); i++) {
			copy.add(menu.slots.get(i).getItem().copy());
		}
		ender = copy;
	}
	private record ContainerData(List<ItemStack> items, int tint) implements TooltipComponent {
	}

	private record MapData(MapId id, float scale) implements TooltipComponent {
	}

	private static final class ContainerClient implements ClientTooltipComponent {
		private final ContainerData data;
		ContainerClient(ContainerData data) {
			this.data = data;
		}
		@Override
		public int getHeight(Font font) {
			return 3 * 18 + 4;
		}
		@Override
		public int getWidth(Font font) {
			return 9 * 18;
		}
		@Override
		public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor g) {
			int slotBg = 0x38000000 | data.tint();
			for (int i = 0; i < 27; i++) {
				int sx = x + (i % 9) * 18;
				int sy = y + 2 + (i / 9) * 18;
				g.fill(sx + 1, sy + 1, sx + 17, sy + 17, slotBg);
				ItemStack stack = i < data.items().size() ? data.items().get(i) : ItemStack.EMPTY;
				if (!stack.isEmpty()) {
					g.item(stack, sx + 1, sy + 1);
					g.itemDecorations(font, stack, sx + 1, sy + 1);
				}
			}
		}
	}
	private static final class MapClient implements ClientTooltipComponent {
		private final MapData data;
		private final MapRenderState state = new MapRenderState();
		MapClient(MapData data) {
			this.data = data;
		}
		private int size() {
			return Math.round(96 * data.scale());
		}
		@Override
		public int getHeight(Font font) {
			return size() + 4;
		}
		@Override
		public int getWidth(Font font) {
			return size();
		}
		@Override
		public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor g) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null) {
				return;
			}
			var saved = net.minecraft.world.item.MapItem.getSavedData(data.id(), mc.level);
			if (saved == null) {
				return;
			}
			mc.getMapRenderer().extractRenderState(data.id(), saved, state);
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(x, y + 2);
			float scale = size() / 128f;
			pose.scale(scale, scale);
			g.map(state);
			pose.popMatrix();
		}
	}
}
