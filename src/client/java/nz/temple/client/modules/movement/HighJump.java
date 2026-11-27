package nz.temple.client.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

public final class HighJump extends Module {
	private final NumberSetting height;

	public HighJump() {
		super("High Jump", Category.MOVEMENT, "Makes your jumps higher.");
		height = add(new NumberSetting("Height", 0.9, 0.42, 2, 0.02, " b/t")
				.describe("Jump velocity. 0.42 is a vanilla jump."));
		instance = this;
		on(TickEvent.class, e -> tick());
	}

	private static HighJump instance;

	public static float power(net.minecraft.world.entity.LivingEntity entity) {
		HighJump jump = instance;
		Minecraft mc = Minecraft.getInstance();
		if (jump == null || !jump.isEnabled() || entity != mc.player || !mc.options.keyJump.isDown()) {
			return Float.NaN;
		}
		return jump.height.getFloat();
	}
	private void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
	}
}
