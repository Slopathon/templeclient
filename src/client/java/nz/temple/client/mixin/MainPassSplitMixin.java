//? if >=26.3 {
/*// 26.3 draws the level's solid and (without improved transparency) translucent parts inside one render pass, and no
// other GPU work may happen while it is open. Temple's effects that 26.2 drew between the two (grass, glow, Fx, the
// copy of the frame Realistic Water refracts) need that gap, so the pass is ended after the solid part and a second one
// is opened on the same target for the translucent part. Earlier versions leave this out (mixins_excluded).
//
// Iris splits the same call the same way (its iris$beginTranslucents closes the pass, draws the hand, and opens a pass
// of its own for the translucent part). Two second passes cannot both be open, so with Iris installed Temple only closes
// the solid part and lets Iris open the next one. The priority puts Temple's wrap outside Iris's: Temple closes first,
// Iris then opens, and no pass is ever opened while another is live.
package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import nz.temple.client.compat.Gpu;
import nz.temple.client.modules.render.RealisticWater;
import nz.temple.client.modules.render.WavingFoliage;
import nz.temple.client.modules.render.WetGround;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

@Mixin(value = LevelRenderer.class, priority = 1100)
public abstract class MainPassSplitMixin {
	private static final String MAIN_PASS = "lambda$addMainPass$0";

	@Unique
	private Supplier<String> temple$label;
	@Unique
	private GpuTextureView temple$color;
	@Unique
	private GpuTextureView temple$depth;
	// The pass vanilla opened for the solid part.
	@Unique
	private RenderPass temple$first;
	// The pass opened for the translucent part, which vanilla's close at the end must close instead of its own.
	@Unique
	private RenderPass temple$second;

	// Terrain materials upload their settings before any terrain is drawn, which is before the pass opens.
	@Inject(method = MAIN_PASS, at = @At("HEAD"))
	private void temple$beforeMainPass(CallbackInfo ci) {
		temple$second = null;
		WetGround.prepare(ChunkSectionLayerGroup.OPAQUE);
		WavingFoliage.prepare(ChunkSectionLayerGroup.OPAQUE);
		RealisticWater.prepare(ChunkSectionLayerGroup.OPAQUE);
	}

	@WrapOperation(method = MAIN_PASS, at = @At(value = "INVOKE",
			target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/Optional;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/renderpearl/api/commands/RenderPass;"))
	private RenderPass temple$rememberTarget(CommandEncoder encoder, Supplier<String> label, GpuTextureView color,
			Optional<org.joml.Vector4fc> clearColor, GpuTextureView depth, OptionalDouble clearDepth, Operation<RenderPass> original) {
		temple$label = label;
		temple$color = color;
		temple$depth = depth;
		temple$first = original.call(encoder, label, color, clearColor, depth, clearDepth);
		return temple$first;
	}

	@WrapOperation(method = MAIN_PASS, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/LevelRenderer;executeClassicTransparency(Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
	private void temple$splitPass(LevelRenderer self, ChunkSectionsToRender sections, FeatureRenderDispatcher.PreparedFrame frame,
			RenderPass solid, Operation<Void> original) {
		// Closing the solid part runs the work Temple's effects queued during it (Gpu.outsidePass).
		solid.close();
		Gpu.outsidePass(() -> RealisticWater.prepare(ChunkSectionLayerGroup.TRANSLUCENT));
		// Iris opens the translucent pass itself (and closes the solid one again, which is harmless). It only does so
		// when it sits inside this wrap and so still sees vanilla's pass; an outer wrap that handed in a pass of its own
		// has already done its part, and the pass for the translucent part is Temple's to open as usual.
		if (Gpu.irisSplitsMainPass() && solid == temple$first) {
			original.call(self, sections, frame, solid);
			return;
		}
		// Loads what is there (no clear), with the same per-frame uniforms vanilla bound on the first pass.
		RenderPass translucent = RenderSystem.getDevice().createCommandEncoder().createRenderPass(temple$label, temple$color, Optional.empty(),
				temple$depth, OptionalDouble.empty());
		RenderSystem.bindDefaultUniforms(translucent);
		temple$second = translucent;
		original.call(self, sections, frame, translucent);
	}

	@WrapOperation(method = MAIN_PASS, at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/commands/RenderPass;close()V"))
	private void temple$closeSecond(RenderPass pass, Operation<Void> original) {
		RenderPass second = temple$second;
		temple$second = null;
		original.call(second != null ? second : pass);
	}
}
*///?}
