package nz.temple.client.modules.render;

import nz.temple.client.util.DetachedCamera;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.NumberSetting;

public final class Freelook extends Module {
	private final NumberSetting distance;
	private final BoolSetting noclip;
	private final BoolSetting hideHand;
	private final BoolSetting robloxStyle;
	private final BoolSetting cinematic;
	private final NumberSetting smoothness;
	private final BoolSetting letterbox;
	private final NumberSetting barSize;
	private float bars;
	private long lastFrame;

	public Freelook() {
		super("Freelook", Category.RENDER, "Rotates the camera around you without turning your body. Toggle again to return to your normal "
				+ "view.");
		distance = add(new NumberSetting("Distance", 4, 1, 12, 0.5, " b")
				.describe("Distance from the camera to you."));
		noclip = add(new BoolSetting("Camera noclip", false)
				.describe("Lets the camera pass through blocks instead of moving in front of walls."));
		hideHand = add(new BoolSetting("Hide hand", true)
				.describe("Hides your hand and held item while orbiting."));
		robloxStyle = add(new BoolSetting("Roblox style", false)
				.describe("While you hold right click, the mouse turns you and the camera follows behind like F5. Released, the camera "
						+ "orbits freely again."));
		group("Cinematic");
		cinematic = add(new BoolSetting("Cinematic", false)
				.describe("Smooths camera movement so it follows your mouse gradually."));
		smoothness = add(new NumberSetting("Smoothness", 6, 1, 10, 0.5, "")
				.describe("How slowly the camera catches up. 1 is barely smoothed, 10 is very slow.")
				.visibleWhen(cinematic::get));
		letterbox = add(new BoolSetting("Letterbox", true)
				.describe("Adds black bars at the top and bottom while cinematic Freelook is on.")
				.visibleWhen(cinematic::get));
		barSize = add(new NumberSetting("Bar size", 11, 4, 20, 1, "%").describe("Height of each bar.")
				.visibleWhen(() -> cinematic.get() && letterbox.get()));
		on(TickEvent.class, e -> {
			DetachedCamera.configure(noclip.get(), distance.getFloat());
			DetachedCamera.hideHand(hideHand.get());
			DetachedCamera.robloxStyle(robloxStyle.get());
			DetachedCamera.cinematic(cinematic.get(), smoothness.getFloat());
			DetachedCamera.tick(this, 0);
		});
	}

	@Override
	protected void onEnable() {
		DetachedCamera.configure(noclip.get(), distance.getFloat());
		DetachedCamera.hideHand(hideHand.get());
		DetachedCamera.robloxStyle(robloxStyle.get());
		DetachedCamera.begin(this, false);
		DetachedCamera.cinematic(cinematic.get(), smoothness.getFloat());
	}

	@Override
	protected void onDisable() {
		DetachedCamera.cinematic(false, 0);
		DetachedCamera.robloxStyle(false);
		DetachedCamera.end(this);
	}

	public void renderBars(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker delta) {
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		boolean want = isEnabled() && cinematic.get() && letterbox.get() && DetachedCamera.cinematicOn();
		bars += ((want ? 1 : 0) - bars) * (1 - (float) Math.exp(-6 * dt));
		if (bars < 0.005f) {
			return;
		}
		int h = Math.round(g.guiHeight() * barSize.getFloat() / 100f * bars);
		g.fill(0, 0, g.guiWidth(), h, 0xFF000000);
		g.fill(0, g.guiHeight() - h, g.guiWidth(), g.guiHeight(), 0xFF000000);
	}
}
