package nz.temple.client.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import nz.temple.client.util.Inv;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;

public final class AutoWeapon extends Module {
	private final BoolSetting swords;
	private final BoolSetting axes;
	private final BoolSetting legit;
	private long swapAt;

	public AutoWeapon() {
		super("Auto Weapon", Category.COMBAT, "Switches to your best sword or axe when you start fighting, and back afterwards.");
		legit = add(new BoolSetting("Legit", false)
				.describe("Only switches once your crosshair is on a target, after a reaction delay, and never on the same tick as an "
						+ "attack."));
		swords = add(new BoolSetting("Swords", true).describe("Prefer swords, best material first."));
		axes = add(new BoolSetting("Axes", true).describe("Use an axe if you have no sword."));
		on(TickEvent.class, e -> tick());
	}

	private int previous = -1;
	private int calmTicks;

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		boolean fighting = legit.get() ? mc.crosshairPickEntity instanceof net.minecraft.world.entity.LivingEntity
				: mc.options.keyAttack.isDown() || mc.crosshairPickEntity instanceof net.minecraft.world.entity.LivingEntity;
		if (!fighting) {
			swapAt = 0;
			if (previous >= 0 && ++calmTicks > 40) {
				player.getInventory().setSelectedSlot(previous);
				previous = -1;
			}
			return;
		}
		calmTicks = 0;
		if (legit.get()) {
			long now = System.currentTimeMillis();
			if (swapAt == 0) {
				swapAt = now + nz.temple.client.util.Human.reactionMs();
			}
			if (now < swapAt || nz.temple.client.compat.Players.isSwinging(player)) {
				return;
			}
		}
		int best = bestSlot(player);
		if (best >= 0 && best != player.getInventory().getSelectedSlot()) {
			if (previous < 0) {
				previous = player.getInventory().getSelectedSlot();
			}
			player.getInventory().setSelectedSlot(best);
		}
	}

	private int bestSlot(LocalPlayer player) {
		int best = -1;
		int bestScore = -1;
		for (int i = 0; i < 9; i++) {
			int score = score(player.getInventory().getItem(i));
			if (score > bestScore) {
				bestScore = score;
				best = i;
			}
		}
		return bestScore > 0 ? best : -1;
	}

	private int score(ItemStack stack) {
		String p = Inv.path(stack);
		boolean sword = p.contains("sword");
		boolean axe = p.contains("axe");
		boolean mace = p.contains("mace");
		if (sword && !swords.get()) {
			return -1;
		}
		if (axe && !axes.get()) {
			return -1;
		}
		if (!sword && !axe && !mace && !p.contains("trident")) {
			return -1;
		}
		int material = p.contains("netherite") ? 60 : p.contains("diamond") ? 50 : p.contains("iron") ? 40
				: p.contains("stone") ? 30 : p.contains("gold") ? 25 : p.contains("wood") ? 20 : p.contains("copper") ? 15 : 10;
		int kind = sword ? 300 : mace ? 200 : axe ? 100 : 50;
		return kind + material;
	}

	@Override
	protected void onDisable() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && previous >= 0) {
			player.getInventory().setSelectedSlot(previous);
			previous = -1;
		}
	}
}
