package nz.temple.client.modules.render;

import nz.temple.client.util.DetachedCamera;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.NumberSetting;

public final class Freecam extends Module {
	private final NumberSetting speed;
	private final BoolSetting hideHand;
	private final BoolSetting staySneaked;
	private final BoolSetting mining;

	public Freecam() {
		super("Freecam", Category.RENDER, "Moves the camera freely through loaded terrain while your body stays in place. WASD moves, Space "
				+ "and Shift go up and down, sprint triples speed.");
		speed = add(new NumberSetting("Speed", 0.6, 0.1, 3, 0.1, " b/t").describe("Camera speed."));
		hideHand = add(new BoolSetting("Hide hand", true)
				.describe("Hides your hand and held item while the camera is detached."));
		staySneaked = add(new BoolSetting("Keep body sneaked", true)
				.describe("Keeps sending the sneak state while the camera is detached, so your body stays crouched."));
		mining = add(new BoolSetting("Allow mining", true)
				.describe("Attack still mines: your body breaks the block it was looking at when the camera detached, "
						+ "so you can dig down while watching from elsewhere."));
		on(TickEvent.class, e -> {
			DetachedCamera.hideHand(hideHand.get());
			DetachedCamera.staySneaking(staySneaked.get());
			DetachedCamera.allowMining(mining.get());
			DetachedCamera.tick(this, speed.get());
		});
	}

	@Override
	protected void onEnable() {
		DetachedCamera.hideHand(hideHand.get());
		DetachedCamera.staySneaking(staySneaked.get());
		DetachedCamera.allowMining(mining.get());
		DetachedCamera.begin(this, true);
	}

	@Override
	protected void onDisable() {
		DetachedCamera.end(this);
	}
}
