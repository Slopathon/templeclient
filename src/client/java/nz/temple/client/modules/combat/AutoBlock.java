package nz.temple.client.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import nz.temple.client.util.Targets;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.NumberSetting;
import nz.temple.social.FriendManager;

public final class AutoBlock extends Module {
	private final NumberSetting range;
	private final BoolSetting ignoreFriends;
	private final FriendManager friends;
	private final BoolSetting legit;
	private long seenAt;

	public AutoBlock(FriendManager friends) {
		super("Auto Block", Category.COMBAT, "Raises your shield when an enemy gets close. Requires a shield in your offhand.");
		this.friends = friends;
		legit = add(new BoolSetting("Legit", false)
				.describe("Only blocks after a human reaction delay, only against visible enemies facing you, and lowers the shield while "
						+ "you attack."));
		range = add(new NumberSetting("Range", 4, 2, 6, 0.1, " m")
				.describe("Block when an enemy is closer than this."));
		ignoreFriends = add(new BoolSetting("Ignore friends", true).describe("Do not block for friends."));
		on(TickEvent.class, e -> tick());
	}

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		boolean shield = nz.temple.client.util.Inv.pathContains(player.getOffhandItem(), "shield");
		LivingEntity near = shield ? Targets.nearest(player, range.get(), "Players", ignoreFriends.get(), friends) : null;
		if (legit.get()) {
			if (near != null && (!player.hasLineOfSight(near) || !facingYou(near, player) || mc.options.keyAttack.isDown())) {
				near = null;
			}
			long now = System.currentTimeMillis();
			if (near == null) {
				seenAt = 0;
			} else if (seenAt == 0) {
				seenAt = now + nz.temple.client.util.Human.reactionMs();
			}
			mc.options.keyUse.setDown(near != null && now >= seenAt);
			return;
		}
		mc.options.keyUse.setDown(near != null);
	}

	private static boolean facingYou(LivingEntity enemy, LocalPlayer you) {
		net.minecraft.world.phys.Vec3 look = enemy.getLookAngle();
		net.minecraft.world.phys.Vec3 to = you.getEyePosition().subtract(enemy.getEyePosition()).normalize();
		return look.dot(to) > 0.5;
	}

	@Override
	protected void onDisable() {
		mc_optionsKeyUseUp();
	}

	private static void mc_optionsKeyUseUp() {
		Minecraft mc = Minecraft.getInstance();
		if (mc != null) {
			mc.options.keyUse.setDown(false);
		}
	}
}
