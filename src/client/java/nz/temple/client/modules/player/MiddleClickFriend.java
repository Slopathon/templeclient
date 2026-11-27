package nz.temple.client.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import nz.temple.client.TempleNzClient;
import nz.temple.client.util.Friends;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;

public final class MiddleClickFriend extends Module {
	private final BoolSetting tint;
	private final ColorSetting color;
	private boolean wasDown;
	public MiddleClickFriend() {
		super("Middle Click Friend", Category.PLAYER, "Middle-click a player to add or remove them as a friend. Friends are never targeted "
				+ "and are tinted blue in ESP and Chams. Saved between sessions; you can also use .friend add/remove <name>.");
		tint = add(new BoolSetting("Tint friends", true).describe("Shows friends in their own colour in ESP and Chams."));
		color = add(new ColorSetting("Friend colour", 0xFF4DA3FF).describe("Friend tint colour.").visibleWhen(tint::get));
		tint.addListener(this::applyTint);
		color.addListener(this::applyTint);
		on(TickEvent.class, e -> tick());
	}
	@Override
	protected void onEnable() {
		applyTint();
	}
	private void applyTint() {
		Friends.setTint(tint.get() ? 0xFF000000 | color.get() : 0);
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		boolean down = mc.options.keyPickItem.isDown();
		if (down && !wasDown && mc.player != null && mc.gui.screen() == null && Friends.manager() != null
				&& mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player target && target != mc.player) {
			String name = target.getGameProfile().name();
			boolean added;
			if (Friends.manager().isFriend(name)) {
				Friends.manager().remove(name);
				added = false;
			} else {
				Friends.manager().add(name);
				added = true;
			}
			nz.temple.client.compat.Players.message(mc.player, Component.literal("§8[§bTemple§8] §f" + name + " §7" + (added ? "is now a friend" : "is no longer a friend")));
			if (TempleNzClient.notifications() != null) {
				TempleNzClient.notifications().info(added ? "Friend added" : "Friend removed", name);
			}
		}
		wasDown = down;
	}
}
