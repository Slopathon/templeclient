package nz.temple.client.modules.combat;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;

public final class AttributeSwap extends Module {
	private static AttributeSwap instance;
	private final ModeSetting target;
	private final NumberSetting slot;
	private final ModeSetting swapBack;
	private int pendingBack = -1;
	public AttributeSwap() {
		super("Attribute Swap", Category.COMBAT, "Switches to another hotbar item for the moment of each hit, adding its enchantment or "
				+ "mace smash damage to the item you hold, then switches back.");
		instance = this;
		target = add(new ModeSetting("Swap to", List.of("Best damage", "Mace", "Slot"), "Best damage")
				.describe("Best damage picks the hotbar item with the largest hit bonus against this target right now: Sharpness, "
						+ "Smite, Bane of Arthropods, or a falling mace's smash. Mace always uses the mace. Slot always uses the slot "
						+ "below."));
		slot = add(new NumberSetting("Slot", 1, 1, 9, 1, "").describe("Hotbar slot to swap to.")
				.visibleWhen(() -> target.is("Slot")));
		swapBack = add(new ModeSetting("Swap back", List.of("Same tick", "Next tick"), "Same tick")
				.describe("Same tick switches back right after the hit, so the charge carries over to the next hit too. Next tick "
						+ "switches back a tick later, which looks like an ordinary hotbar switch."));
		on(TickEvent.class, e -> {
			if (pendingBack >= 0) {
				LocalPlayer p = net.minecraft.client.Minecraft.getInstance().player;
				if (p != null) {
					restore(p, pendingBack);
				}
				pendingBack = -1;
			}
		});
	}
	public static AttributeSwap instance() {
		return instance;
	}

	public int select(LocalPlayer p, Entity victim) {
		if (!isEnabled() || !(victim instanceof LivingEntity)) {
			return -1;
		}
		int held = p.getInventory().getSelectedSlot();
		int to = choose(p, held, victim);
		if (to < 0 || to == held) {
			return -1;
		}
		p.getInventory().setSelectedSlot(to);
		if (swapBack.is("Next tick")) {
			pendingBack = held;
			return -1;
		}
		return held;
	}
	private int choose(LocalPlayer p, int held, Entity victim) {
		switch (target.get()) {
			case "Slot" -> {
				return (int) Math.round(slot.get()) - 1;
			}
			case "Mace" -> {
				return find(p, s -> s.is(Items.MACE));
			}
			default -> {
				int best = -1;
				double bestDamage = bonus(p, p.getInventory().getItem(held), victim);
				for (int i = 0; i < 9; i++) {
					double d = bonus(p, p.getInventory().getItem(i), victim);
					if (d > bestDamage + 0.01) {
						bestDamage = d;
						best = i;
					}
				}
				return best;
			}
		}
	}
	private static int find(LocalPlayer p, java.util.function.Predicate<ItemStack> test) {
		for (int i = 0; i < 9; i++) {
			if (test.test(p.getInventory().getItem(i))) {
				return i;
			}
		}
		return -1;
	}
	private static double bonus(LocalPlayer p, ItemStack stack, Entity victim) {
		double d = 0;
		int sharpness = level(p, stack, Enchantments.SHARPNESS);
		if (sharpness > 0) {
			d += 0.5 * sharpness + 0.5;
		}
		if (victim.getType().builtInRegistryHolder().is(net.minecraft.tags.EntityTypeTags.SENSITIVE_TO_SMITE)) {
			d += 2.5 * level(p, stack, Enchantments.SMITE);
		}
		if (victim.getType().builtInRegistryHolder().is(net.minecraft.tags.EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) {
			d += 2.5 * level(p, stack, Enchantments.BANE_OF_ARTHROPODS);
		}
		if (stack.is(Items.MACE) && p.fallDistance > 1.5) {
			double fall = p.fallDistance;
			d += Math.min(fall, 3) * 4 + Math.max(0, Math.min(fall, 8) - 3) * 2 + Math.max(0, fall - 8);
			d += 0.5 * level(p, stack, Enchantments.DENSITY) * fall;
		}
		return d;
	}
	private static int level(LocalPlayer p, ItemStack stack, ResourceKey<Enchantment> key) {
		Holder<Enchantment> holder = p.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key).orElse(null);
		return holder == null ? 0 : EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
	}
	private static void restore(LocalPlayer p, int slot) {
		if (p.getInventory().getSelectedSlot() != slot) {
			p.getInventory().setSelectedSlot(slot);
			p.connection.send(new ServerboundSetCarriedItemPacket(slot));
		}
	}
	@Override
	protected void onDisable() {
		LocalPlayer p = net.minecraft.client.Minecraft.getInstance().player;
		if (p != null && pendingBack >= 0) {
			restore(p, pendingBack);
		}
		pendingBack = -1;
	}
}
