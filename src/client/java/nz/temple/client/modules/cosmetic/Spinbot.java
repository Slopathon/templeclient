package nz.temple.client.modules.cosmetic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import nz.temple.client.util.Rotations;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;

public final class Spinbot extends Module {
	private static final int ROTATION_PRIORITY = 10;
	private static Spinbot instance;

	private final ModeSetting side;
	private final NumberSetting yawSpeed;
	private final NumberSetting pitch;
	private float previousYaw;
	private float yaw;
	private boolean initialized;

	public Spinbot() {
		super("Spinbot", Category.COSMETICS, "Spins your player model while your camera stays still. Client only changes what you see; "
				+ "Server sends the rotation in normal movement packets.");
		instance = this;
		side = add(new ModeSetting("Side", List.of("Client", "Server"), "Client")
				.describe("Client only changes your local model. Server sends the rotation in normal movement packets while your camera "
						+ "stays free."));
		yawSpeed = add(new NumberSetting("Yaw speed", 45, -180, 180, 5, "°/t")
				.describe("Degrees per tick. Negative values spin the other way; 0 faces one direction."));
		pitch = add(new NumberSetting("Pitch", 0, -90, 90, 5, "°")
				.describe("Vertical angle shown while spinning."));
		on(TickEvent.class, event -> tick());
	}

	public static Spinbot instance() {
		return instance;
	}

	private void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !player.isAlive() || player.isDeadOrDying()) {
			initialized = false;
			return;
		}
		if (!initialized) {
			yaw = player.getYRot();
			previousYaw = yaw;
			initialized = true;
		} else {
			previousYaw = yaw;
			yaw += yawSpeed.getFloat();
		}
		if (side.is("Server")) {
			Rotations.request(yaw, pitch.getFloat(), ROTATION_PRIORITY);
		}
	}

	public float renderYaw(float partialTick) {
		return Mth.lerp(partialTick, previousYaw, yaw);
	}

	public float renderPitch() {
		return pitch.getFloat();
	}

	@Override
	protected void onEnable() {
		initialized = false;
	}

	@Override
	protected void onDisable() {
		initialized = false;
	}
}
