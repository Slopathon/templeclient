package nz.temple.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Streamproof draws its clean GUI by pointing the game renderer's main target at the clean frame for a moment. (On
 * 1.21.11 the main target is Minecraft's, see LegacyMinecraftTargetAccessor, and a GUI renderer needs more of the game
 * renderer's parts.)
 */
@Mixin(GameRenderer.class)
public interface GameRendererTargetAccessor {
	//? if >=26 {
	@Mutable
	@Accessor("mainRenderTarget")
	void templenz$setMainTarget(RenderTarget target);
	//?} else {
	/*@Accessor("renderBuffers")
	net.minecraft.client.renderer.RenderBuffers templenz$renderBuffers();

	@Accessor("submitNodeStorage")
	net.minecraft.client.renderer.SubmitNodeStorage templenz$submitNodes();

	@Accessor("fogRenderer")
	net.minecraft.client.renderer.fog.FogRenderer templenz$fog();
	*///?}

	@Accessor("featureRenderDispatcher")
	FeatureRenderDispatcher templenz$features();
}
