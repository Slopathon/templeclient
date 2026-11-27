package nz.temple.client.mixin;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.ToggleKeyMapping;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import nz.temple.client.event.MovementInputEvents;
import nz.temple.client.modules.movement.MoveFix;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fires the movement-input event (MoveFix, Sprint Reset, Target Strafe, Speed...) and applies what listeners changed. */
@Mixin(KeyboardInput.class)
public class MovementInputMixin {
	@Shadow
	@Final
	private Options options;
	@Unique
	private boolean temple$forward;
	@Unique
	private boolean temple$back;
	@Unique
	private boolean temple$left;
	@Unique
	private boolean temple$right;
	@Unique
	private boolean temple$jump;
	@Unique
	private boolean temple$sneak;
	@Unique
	private boolean temple$sprint;

	@Inject(method = "tick", at = @At("HEAD"))
	private void temple$tickHead(CallbackInfo ci) {
		temple$forward = options.keyUp.isDown();
		temple$back = options.keyDown.isDown();
		temple$left = options.keyLeft.isDown();
		temple$right = options.keyRight.isDown();
		temple$jump = options.keyJump.isDown();
		temple$sneak = options.keyShift.isDown();
		temple$sprint = options.keySprint.isDown();
		MoveFix.EventMovementInput event = new MoveFix.EventMovementInput(temple$forward, temple$back, temple$left, temple$right,
				temple$jump, temple$sneak, temple$sprint);
		MoveFix.onMovementInput(event);
		MovementInputEvents.fire(event);
		if (MoveFix.isMovementFrozen()) {
			event.forward = false;
			event.back = false;
			event.left = false;
			event.right = false;
			event.jump = false;
			event.sneak = false;
			event.sprint = false;
		}
		options.keyUp.setDown(event.forward);
		options.keyDown.setDown(event.back);
		options.keyLeft.setDown(event.left);
		options.keyRight.setDown(event.right);
		options.keyJump.setDown(event.jump);
		temple$setKey(options.keyShift, event.sneak, options.toggleCrouch().get());
		temple$setKey(options.keySprint, event.sprint, options.toggleSprint().get());
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void temple$tickReturn(CallbackInfo ci) {
		options.keyUp.setDown(temple$forward);
		options.keyDown.setDown(temple$back);
		options.keyLeft.setDown(temple$left);
		options.keyRight.setDown(temple$right);
		options.keyJump.setDown(temple$jump);
		temple$setKey(options.keyShift, temple$sneak, options.toggleCrouch().get());
		temple$setKey(options.keySprint, temple$sprint, options.toggleSprint().get());
		if (MoveFix.isMovementFrozen()) {
			((ClientInput) (Object) this).keyPresses = Input.EMPTY;
		}
	}

	@Unique
	private static void temple$setKey(KeyMapping key, boolean desired, boolean toggleEnabled) {
		if (key instanceof ToggleKeyMapping && toggleEnabled) {
			if (key.isDown() != desired) {
				key.setDown(true);
			}
		} else {
			key.setDown(desired);
		}
	}
}
