package nz.temple.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.util.DetachedCamera;
import nz.temple.client.util.Steering;

@Mixin(KeyboardInput.class)
public abstract class FreecamInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("RETURN"))
	private void temple$input(CallbackInfo ci) {
		if (DetachedCamera.freecam()) {
			keyPresses = DetachedCamera.bodySneaking()
					? new Input(false, false, false, false, false, true, false)
					: Input.EMPTY;
			moveVector = Vec2.ZERO;
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && Steering.active(player)) {
			keyPresses = Steering.keys(player, keyPresses);
			moveVector = Steering.move(player);
		}
		if (player != null) {
			Vec2 scaffold = nz.temple.client.modules.world.Scaffold.input(player, this);
			if (scaffold != null) {
				moveVector = scaffold;
			}
			Input fixed = nz.temple.client.util.MoveFix.keys(player, keyPresses, moveVector);
			if (fixed != null) {
				keyPresses = fixed;
				moveVector = nz.temple.client.util.MoveFix.vector(fixed);
			}
		}
	}
}
