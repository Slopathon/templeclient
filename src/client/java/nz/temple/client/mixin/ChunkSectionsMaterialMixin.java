package nz.temple.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.render.RealisticWater;
import nz.temple.client.modules.render.WavingFoliage;
import nz.temple.client.modules.render.WetGround;

/** Reuses vanilla chunk draw groups with Temple's terrain pipelines (Wet Ground, Waving Foliage, Realistic Water). */
@Mixin(ChunkSectionsToRender.class)
public abstract class ChunkSectionsMaterialMixin {
	/** Picks Temple's pipeline for {@code original}, or returns it. Wet Ground uses the foliage vertex shader, so it goes first. */
	private static RenderPipeline temple$select(ChunkSectionLayerGroup group, RenderPipeline original) {
		RenderPipeline selected = WetGround.pipeline(group, original);
		if (selected == original) {
			selected = WavingFoliage.pipeline(group, original);
		}
		if (selected == original) {
			selected = RealisticWater.pipeline(group, original);
		}
		return selected;
	}

	private static void temple$bind(RenderPass pass, RenderPipeline selected) {
		if (!WetGround.bind(pass, selected) && !RealisticWater.bind(pass, selected)) {
			WavingFoliage.bind(pass, selected);
		}
	}

	//? if >=26.3 {
	/*// 26.3 draws each layer through render(layer, pass, ..., override, multiDrawOverride), which binds the override when
	// one is given and the layer's own pipeline otherwise. Temple's pipeline goes in as the single-draw override. The
	// multi-draw (indirect) path keeps vanilla's pipeline: Temple's pipelines are built from the single-draw ones.
	// A render pass only collects uniforms until it draws, so they are bound here, before render() binds the pipeline.

	@org.spongepowered.asm.mixin.Unique
	private static ChunkSectionLayerGroup temple$group;

	@org.spongepowered.asm.mixin.Shadow
	protected abstract void render(net.minecraft.client.renderer.chunk.ChunkSectionLayer layer, RenderPass pass,
			com.mojang.blaze3d.buffers.GpuBuffer indices, com.mojang.blaze3d.IndexType indexType, RenderPipeline override,
			RenderPipeline multiDrawOverride);

	@Inject(method = "renderGroup", at = @At("HEAD"))
	private void temple$prepareMaterial(ChunkSectionLayerGroup group, RenderPass pass, GpuSampler sampler,
			com.mojang.blaze3d.textures.GpuTextureView lightmap, boolean wireframe, CallbackInfo ci) {
		// The settings were uploaded before the pass opened (MainPassSplitMixin): no GPU work is allowed in it.
		temple$group = group;
	}

	@Inject(method = "renderOit", at = @At("HEAD"))
	private void temple$prepareTranslucent(CallbackInfo ci) {
		// With improved transparency the translucent terrain gets passes of its own, after the solid pass has closed.
		temple$group = ChunkSectionLayerGroup.TRANSLUCENT;
		nz.temple.client.compat.Gpu.outsidePass(() -> RealisticWater.prepare(ChunkSectionLayerGroup.TRANSLUCENT));
	}

	@Redirect(method = "renderLayers", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;render(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;Lcom/mojang/blaze3d/systems/RenderPass;Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/IndexType;Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
	private void temple$materialPipeline(ChunkSectionsToRender self, net.minecraft.client.renderer.chunk.ChunkSectionLayer layer,
			RenderPass pass, com.mojang.blaze3d.buffers.GpuBuffer indices, com.mojang.blaze3d.IndexType indexType,
			RenderPipeline override, RenderPipeline multiDrawOverride) {
		RenderPipeline original = override != null ? override : layer.pipeline(false);
		RenderPipeline selected = temple$group == null ? original : temple$select(temple$group, original);
		if (selected != original) {
			temple$bind(pass, selected);
			override = selected;
		}
		render(layer, pass, indices, indexType, override, multiDrawOverride);
	}
	*///?} else {
	@Inject(method = "renderGroup", at = @At("HEAD"))
	private void temple$prepareMaterial(ChunkSectionLayerGroup group, GpuSampler sampler, CallbackInfo ci) {
		WetGround.prepare(group);
		WavingFoliage.prepare(group);
		RealisticWater.prepare(group);
	}

	@Redirect(method = "renderGroup", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
	private void temple$materialPipeline(RenderPass pass, RenderPipeline original,
			ChunkSectionLayerGroup group, GpuSampler sampler) {
		RenderPipeline selected = temple$select(group, original);
		pass.setPipeline(selected);
		temple$bind(pass, selected);
	}
	//?}
}
