package nz.temple.client.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import nz.temple.client.TempleNzClient;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.NumberSetting;

import java.util.HashMap;
import java.util.Map;

public final class DurabilityAlert extends Module {
	private static final EquipmentSlot[] WATCHED = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
			EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
	};
	private final NumberSetting threshold;
	private final BoolSetting armor;
	private final BoolSetting held;
	private final BoolSetting sound;
	private final BoolSetting repeatWarning;
	private final NumberSetting repeatDelay;
	private final Map<EquipmentSlot, Boolean> warned = new HashMap<>();
	private final Map<EquipmentSlot, Long> lastWarned = new HashMap<>();
	public DurabilityAlert() {
		super("Durability Alert", Category.PLAYER, "Warns when armour or a weapon is about to break.");
		threshold = add(new NumberSetting("Warn at", 15, 1, 50, 1, "%")
				.describe("Durability at which a warning is shown."));
		group();
		armor = add(new BoolSetting("Armor", true).describe("Checks your four armour slots."));
		held = add(new BoolSetting("Held items", true).describe("Checks your main hand and offhand."));
		group();
		sound = add(new BoolSetting("Sound", true).describe("Plays a sound with the warning."));
		repeatWarning = add(new BoolSetting("Keep reminding", false)
				.describe("Repeats the warning while an item stays low, instead of warning once."));
		repeatDelay = add(new NumberSetting("Reminder delay", 10, 2, 60, 1, " s")
				.describe("Time between repeated warnings.")
				.visibleWhen(repeatWarning::get));
		on(TickEvent.class, e -> tick());
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		for (EquipmentSlot slot : WATCHED) {
			boolean isArmor = slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND;
			if (isArmor && !armor.get() || !isArmor && !held.get()) {
				continue;
			}
			ItemStack stack = player.getItemBySlot(slot);
			if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
				warned.remove(slot);
				continue;
			}
			int remaining = stack.getMaxDamage() - stack.getDamageValue();
			float percent = remaining * 100f / stack.getMaxDamage();
			if (percent > threshold.get()) {
				warned.remove(slot);
				continue;
			}
			long now = System.currentTimeMillis();
			boolean first = !warned.containsKey(slot);
			boolean due = repeatWarning.get() && now - lastWarned.getOrDefault(slot, 0L) > repeatDelay.get() * 1000;
			if (!first && !due) {
				continue;
			}
			warned.put(slot, true);
			lastWarned.put(slot, now);
			warn(mc, stack, percent);
		}
	}
	private void warn(Minecraft mc, ItemStack stack, float percent) {
		String name = stack.getHoverName().getString();
		if (TempleNzClient.notifications() != null) {
			TempleNzClient.notifications().error(name, Math.round(percent) + "% durability left");
		}
		if (sound.get()) {
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_LAND, 1.6f, 0.35f));
		}
	}
	@Override
	protected void onDisable() {
		warned.clear();
		lastWarned.clear();
	}
}
