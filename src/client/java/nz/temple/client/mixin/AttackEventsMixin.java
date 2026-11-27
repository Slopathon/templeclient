package nz.temple.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import nz.temple.client.event.AttackStartEvent;
import nz.temple.client.event.AttackEvents;
import nz.temple.client.util.Friends;
import nz.temple.client.modules.SchemaModules;
import nz.temple.client.modules.combat.Piercing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class AttackEventsMixin {
	@Shadow
	public LocalPlayer player;
	@Shadow
	public HitResult hitResult;

	@Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
	private void temple$startAttack(CallbackInfoReturnable<Boolean> cir) {
		if (player == null) {
			return;
		}
		Entity target = hitResult != null && hitResult.getType() == HitResult.Type.ENTITY ? ((EntityHitResult) hitResult).getEntity() : null;
		AttackStartEvent event = new AttackStartEvent(player, target);
		AttackEvents.fire(event);
		if (event.isCancelled()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "handleKeybinds", at = @At("HEAD"))
	private void temple$piercing(CallbackInfo ci) {
		Minecraft mc = (Minecraft) (Object) this;
		if (mc.player != null && mc.level != null && SchemaModules.get("Piercing") instanceof Piercing p && p.isEnabled()) {
			p.onHandleInput(mc);
		}
	}
}
