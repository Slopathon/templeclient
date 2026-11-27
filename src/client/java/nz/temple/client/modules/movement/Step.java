package nz.temple.client.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.resources.Identifier;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.client.util.Motion;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.List;

public final class Step extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private static final Identifier MODIFIER = Identifier.fromNamespaceAndPath("templenz", "step");
	private final ModeSetting mode;
	private final NumberSetting height;

	public Step() {
		super("Step", Category.MOVEMENT, "Walk up blocks without jumping, up to the set height.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Presses your jump key when you walk into a one-block step. Does not change step height."));
		mode = add(new ModeSetting("Mode", List.of("Instant", "Jump"), "Instant")
				.describe("Instant raises your step height so you walk up ledges. Jump hops automatically at one-block ledges, which works "
						+ "on servers that reject step height changes."));
		height = add(new NumberSetting("Height", 1.0, 0.6, 3, 0.1, " b")
				.describe("Maximum ledge height you can walk up.")
				.visibleWhen(() -> mode.is("Instant")));
		height.addListener(this::apply);
		mode.addListener(this::apply);
		legit.addListener(this::apply);
		hideWhenLegit(legit, mode, height);
		on(TickEvent.class, e -> {
			apply();
			hop();
		});
	}

	private void hop() {
		LocalPlayer player = Minecraft.getInstance().player;
		if ((mode.is("Jump") || legit.get()) && player != null && player.onGround() && player.horizontalCollision
				&& !player.isShiftKeyDown() && Motion.wishDirection(player).lengthSqr() > 1e-6) {
			if (legit.get()) {
				nz.temple.client.util.Keys.press(Minecraft.getInstance().options.keyJump, 1);
			} else {
				player.jumpFromGround();
			}
		}
	}

	private void apply() {
		AttributeInstance attr = attribute();
		if (attr != null && isEnabled() && (mode.is("Jump") || legit.get())) {
			attr.removeModifier(MODIFIER);
			return;
		}
		if (attr != null && isEnabled()) {
			attr.addOrUpdateTransientModifier(new AttributeModifier(MODIFIER,
					Math.max(0, height.get() - attr.getBaseValue()), AttributeModifier.Operation.ADD_VALUE));
		}
	}

	@Override
	protected void onDisable() {
		AttributeInstance attr = attribute();
		if (attr != null) {
			attr.removeModifier(MODIFIER);
		}
	}

	private static AttributeInstance attribute() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player == null ? null : player.getAttribute(Attributes.STEP_HEIGHT);
	}
}
