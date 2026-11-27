package nz.temple.client.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.render.Chams;
import nz.temple.client.modules.render.Esp;
import nz.temple.client.render.EspStateAccess;

@Mixin(EntityRenderer.class)
public abstract class EntityOutlineColorMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void temple$espColors(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
		Esp esp = Esp.instance();
		int fill = 0;
		if (esp != null && esp.glows(entity)) {
			int rgb = esp.outlineColor(entity);
			state.outlineColor = rgb == 0 ? 1 : rgb;
		} else if (nz.temple.client.modules.render.ItemESP.glows(entity)) {
			int rgb = nz.temple.client.modules.render.ItemESP.outlineColor(entity);
			state.outlineColor = rgb == 0 ? 1 : rgb;
		}
		if (esp != null) {
			fill = esp.fillArgb(entity);
		}
		((EspStateAccess) state).temple$setEspFill(fill);
		Chams chams = Chams.instance();
		boolean chamsOn = chams != null && chams.appliesTo(entity);
		((EspStateAccess) state).temple$setChams(chamsOn);
		((EspStateAccess) state).temple$setSelf(entity == net.minecraft.client.Minecraft.getInstance().player);
		((EspStateAccess) state).temple$setMine(nz.temple.client.util.Wearer.mine(entity));
		((EspStateAccess) state).temple$setFriendTint(nz.temple.client.util.Friends.tintFor(entity));
		((EspStateAccess) state).temple$setEntityId(entity.getId());

	}
}
