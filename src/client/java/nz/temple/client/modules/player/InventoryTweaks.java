package nz.temple.client.modules.player;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import nz.temple.client.mixin.ContainerScreenAccessor;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class InventoryTweaks extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private static InventoryTweaks instance;
	private static final Comparator<ItemStack> ORDER = Comparator.<ItemStack>comparingInt(s -> s.isEmpty() ? 1 : 0)
			.thenComparingInt(s -> s.isEmpty() ? 0 : BuiltInRegistries.ITEM.getId(s.getItem()))
			.thenComparing(s -> s.isEmpty() ? "" : s.getHoverName().getString())
			.thenComparingInt(s -> -s.getCount());
	private final BoolSetting buttons;
	private final BoolSetting autoSteal;
	private final BoolSetting autoDump;
	private final BoolSetting closeAfter;
	private final BoolSetting dumpHotbar;
	private final NumberSetting delay;
	private final BoolSetting shiftDrag;
	private final BoolSetting middleSort;
	private final BoolSetting protect;
	private final BoolSetting xcarry;
	private Job job;
	private Runnable after;
	private int jobContainer = -1;
	private int wait;
	private int autoHandled = -1;

	private interface Job {
		boolean step(Minecraft mc, AbstractContainerMenu menu);
	}
	public InventoryTweaks() {
		super("Inventory Tweaks", Category.PLAYER, "Steal, dump and sort buttons for containers, shift-drag moving, middle-click sorting, "
				+ "drop protection and XCarry.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Automatic clicks (steal, dump, sort, shift-drag) use uneven delays, and XCarry is turned off since vanilla does "
						+ "not allow keeping items in the crafting grid."));
		instance = this;
		buttons = add(new BoolSetting("Container buttons", true)
				.describe("Adds Steal, Dump and Sort buttons beside chests, shulkers, barrels, hoppers and dispensers."));
		autoSteal = add(new BoolSetting("Auto steal", false)
				.describe("Takes everything out of a container when you open it."));
		autoDump = add(new BoolSetting("Auto dump", false)
				.describe("Moves your inventory into a container when you open it.")
				.visibleWhen(() -> !autoSteal.get()));
		closeAfter = add(new BoolSetting("Close when done", false)
				.describe("Closes the container after an auto steal or dump.")
				.visibleWhen(() -> autoSteal.get() || autoDump.get()));
		dumpHotbar = add(new BoolSetting("Dump hotbar", false)
				.describe("Includes your hotbar when dumping. Off keeps your hotbar items."));
		delay = add(new NumberSetting("Click delay", 1, 0, 10, 1, " ticks")
				.describe("Ticks between item moves. 0 moves everything at once; raise it if a server kicks you for clicking too fast."));
		group();
		shiftDrag = add(new BoolSetting("Shift-drag move", true)
				.describe("Hold Shift and drag across slots to move every stack you pass over."));
		middleSort = add(new BoolSetting("Middle-click sort", true)
				.describe("Middle-click a slot to sort that container, your inventory or your hotbar. (In creative, middle-click still "
						+ "picks blocks.)"));
		protect = add(new BoolSetting("Protect valuables", true)
				.describe("Prevents dropping totems, shulker boxes, elytra, netherite, enchanted gear and similar items with Q or by "
						+ "clicking outside the inventory."));
		xcarry = add(new BoolSetting("XCarry", false)
				.describe("Items in your 2x2 crafting grid stay there when you close your inventory, giving you four extra slots. The "
						+ "server is not told the inventory was closed."));
		hideWhenLegit(legit, delay, xcarry);
		on(TickEvent.class, e -> pump());
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> onScreen(screen));
	}
	public static InventoryTweaks active() {
		return instance != null && instance.isEnabled() ? instance : null;
	}
	public boolean shiftDrag() {
		return shiftDrag.get();
	}
	public boolean middleSort() {
		return middleSort.get();
	}
	public boolean protect() {
		return protect.get();
	}
	public boolean xcarry() {
		return xcarry.get() && !legit.get();
	}
	public static boolean valuable(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.isEnchanted()) {
			return true;
		}
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return id.equals("totem_of_undying") || id.equals("elytra") || id.equals("enchanted_golden_apple")
				|| id.equals("nether_star") || id.equals("beacon") || id.equals("dragon_egg") || id.equals("heavy_core")
				|| id.endsWith("shulker_box") || id.startsWith("netherite_");
	}
	private static boolean storage(AbstractContainerMenu menu) {
		return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu || menu instanceof HopperMenu || menu instanceof DispenserMenu;
	}
	private void onScreen(Screen screen) {
		if (!isEnabled() || !(screen instanceof AbstractContainerScreen<?> container) || !storage(container.getMenu())) {
			return;
		}
		AbstractContainerMenu menu = container.getMenu();
		if (buttons.get()) {
			ContainerScreenAccessor bounds = (ContainerScreenAccessor) container;
			int w = 50;
			int x = bounds.temple$leftPos() + bounds.temple$imageWidth() + 4;
			if (x + w > screen.width) {
				x = bounds.temple$leftPos() - w - 4;
			}
			int y = bounds.temple$topPos() + 4;
			List<AbstractWidget> widgets = Screens.getWidgets(screen);
			widgets.add(Button.builder(Component.literal("Steal"), b -> steal(menu, false)).bounds(x, y, w, 16)
					.tooltip(Tooltip.create(Component.literal("Move everything into your inventory"))).build());
			widgets.add(Button.builder(Component.literal("Dump"), b -> dump(menu, false)).bounds(x, y + 20, w, 16)
					.tooltip(Tooltip.create(Component.literal("Move your inventory into this container"))).build());
			widgets.add(Button.builder(Component.literal("Sort"), b -> sortContainer(menu)).bounds(x, y + 40, w, 16)
					.tooltip(Tooltip.create(Component.literal("Sort this container"))).build());
		}
		if (menu.containerId != autoHandled) {
			autoHandled = menu.containerId;
			if (autoSteal.get()) {
				steal(menu, closeAfter.get());
			} else if (autoDump.get()) {
				dump(menu, closeAfter.get());
			}
		}
	}
	private void steal(AbstractContainerMenu menu, boolean close) {
		start(menu, move(true), close);
	}
	private void dump(AbstractContainerMenu menu, boolean close) {
		start(menu, move(false), close);
	}
	private void sortContainer(AbstractContainerMenu menu) {
		for (Slot slot : menu.slots) {
			if (!(slot.container instanceof Inventory)) {
				sort(menu, slot);
				return;
			}
		}
	}
	public boolean sort(AbstractContainerMenu menu, Slot hovered) {
		List<Integer> region = region(menu, hovered);
		if (region.size() < 5 || !menu.getCarried().isEmpty()) {
			return false;
		}
		start(menu, sortJob(region), false);
		return true;
	}
	private void start(AbstractContainerMenu menu, Job newJob, boolean close) {
		Minecraft mc = Minecraft.getInstance();
		job = newJob;
		jobContainer = menu.containerId;
		wait = 0;
		after = close ? () -> {
			if (mc.player != null) {
				mc.player.closeContainer();
			}
		} : null;
		pump();
	}
	private void pump() {
		if (job == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gameMode == null || mc.player.containerMenu.containerId != jobContainer) {
			job = null;
			after = null;
			return;
		}
		if (wait > 0) {
			wait--;
			return;
		}
		if (legit.get()) {
			if (!nz.temple.client.util.LegitInv.ready()) {
				return;
			}
			if (!job.step(mc, mc.player.containerMenu)) {
				job = null;
				Runnable done = after;
				after = null;
				if (done != null) {
					done.run();
				}
				return;
			}
			nz.temple.client.util.LegitInv.clicked();
			return;
		}
		int ticks = (int) Math.round(delay.get());
		int budget = ticks == 0 ? 512 : 1;
		AbstractContainerMenu menu = mc.player.containerMenu;
		while (budget-- > 0) {
			if (!job.step(mc, menu)) {
				job = null;
				Runnable done = after;
				after = null;
				if (done != null) {
					done.run();
				}
				return;
			}
		}
		wait = ticks - 1;
	}
	private Job move(boolean steal) {
		Set<Integer> tried = new HashSet<>();
		return (mc, menu) -> {
			for (Slot slot : menu.slots) {
				boolean mine = slot.container instanceof Inventory;
				if (mine == steal || !slot.hasItem() || tried.contains(slot.index)) {
					continue;
				}
				if (mine && (slot.getContainerSlot() >= 36 || !dumpHotbar.get() && slot.getContainerSlot() < 9)) {
					continue;
				}
				tried.add(slot.index);
				click(mc, menu, slot.index, ContainerInput.QUICK_MOVE);
				return true;
			}
			return false;
		};
	}
	private Job sortJob(List<Integer> region) {
		int[] steps = {0};
		return (mc, menu) -> {
			if (++steps[0] > 400 || !menu.getCarried().isEmpty()) {
				return false;
			}
			for (int i = 0; i < region.size(); i++) {
				int best = i;
				for (int j = i + 1; j < region.size(); j++) {
					if (ORDER.compare(menu.slots.get(region.get(j)).getItem(), menu.slots.get(region.get(best)).getItem()) < 0) {
						best = j;
					}
				}
				if (best == i) {
					continue;
				}
				int from = region.get(best);
				int to = region.get(i);
				click(mc, menu, from, ContainerInput.PICKUP);
				click(mc, menu, to, ContainerInput.PICKUP);
				if (!menu.getCarried().isEmpty()) {
					click(mc, menu, from, ContainerInput.PICKUP);
				}
				return true;
			}
			return false;
		};
	}
	private static List<Integer> region(AbstractContainerMenu menu, Slot hovered) {
		List<Integer> out = new ArrayList<>();
		boolean mine = hovered.container instanceof Inventory;
		int cs = hovered.getContainerSlot();
		if (mine && cs >= 36) {
			return out;
		}
		boolean hotbar = mine && cs < 9;
		for (Slot slot : menu.slots) {
			if (slot.container != hovered.container) {
				continue;
			}
			if (mine) {
				int c = slot.getContainerSlot();
				if (c >= 36 || c < 9 != hotbar) {
					continue;
				}
			}
			out.add(slot.index);
		}
		return out;
	}
	private static void click(Minecraft mc, AbstractContainerMenu menu, int slot, ContainerInput input) {
		mc.gameMode.handleContainerInput(menu.containerId, slot, 0, input, mc.player);
	}
	@Override
	protected void onDisable() {
		job = null;
		after = null;
	}
}
