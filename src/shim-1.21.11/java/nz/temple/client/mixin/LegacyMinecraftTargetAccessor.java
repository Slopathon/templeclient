package nz.temple.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface LegacyMinecraftTargetAccessor {
	@Mutable
	@Accessor("mainRenderTarget")
	void templenz$setMainTarget(RenderTarget target);
}
