package nz.temple.client.modules.player;

import net.minecraft.client.Minecraft;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;

public final class AutoWalk extends Module {
	public AutoWalk() {
		super("Auto Walk", Category.PLAYER, "Walks forward automatically.");
		on(TickEvent.class, e -> {
			if (Minecraft.getInstance().player != null) {
				Minecraft.getInstance().options.keyUp.setDown(true);
			}
		});
	}

	@Override
	protected void onDisable() {
		Minecraft.getInstance().options.keyUp.setDown(false);
	}
}
