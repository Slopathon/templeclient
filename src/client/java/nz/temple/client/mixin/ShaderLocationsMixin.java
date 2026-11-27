//? if >=26.3 {
/*// 26.3 compiles shaders to SPIR-V, which needs explicit input and output locations; see ShaderLocations. Earlier
// versions bind by name and leave this out (mixins_excluded).
package nz.temple.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.frontend.shaders.PipelineBuilder;
import net.minecraft.resources.Identifier;
import nz.temple.client.compat.ShaderLocations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

// Fills in the locations Temple's shaders leave out, as each pipeline loads its shaders. A fragment shader's inputs
// take the locations of its pipeline's vertex shader outputs, so both stages are read here.
@Mixin(PipelineBuilder.class)
public abstract class ShaderLocationsMixin {
	@WrapOperation(method = "generateBackendCreateInfo", at = @At(value = "INVOKE",
			target = "Lcom/mojang/renderpearl/frontend/shaders/PipelineBuilder;loadShaderSource(Lnet/minecraft/resources/Identifier;Lcom/mojang/renderpearl/api/pipeline/ShaderType;Lcom/mojang/renderpearl/api/pipeline/ShaderSource;)Ljava/lang/String;"))
	private String temple$locations(Identifier id, ShaderType type, ShaderSource source, Operation<String> original,
			RenderPipeline pipeline, ShaderSource pipelineSource,
			it.unimi.dsi.fastutil.objects.ReferenceArrayList<?> shaders, it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap<?> locations) {
		String text = original.call(id, type, source);
		if (text == null || !"templenz".equals(id.getNamespace())) {
			return text;
		}
		// Both stages are laid out the same way whichever is asked for, so they agree.
		Identifier fragmentId = pipeline.getShaders().get(ShaderType.FRAGMENT);
		Identifier vertexId = pipeline.getShaders().get(ShaderType.VERTEX);
		String fragment = type == ShaderType.FRAGMENT ? text
				: fragmentId == null ? null : original.call(fragmentId, ShaderType.FRAGMENT, source);
		String vertex = type == ShaderType.VERTEX ? text
				: vertexId == null ? null : original.call(vertexId, ShaderType.VERTEX, source);
		Map<String, Integer> fixed = fragment == null ? Map.of() : ShaderLocations.explicitInputs(fragment);
		ShaderLocations.Vertex laidOut = vertex == null ? null : ShaderLocations.vertex(vertex, fixed);
		if (type == ShaderType.VERTEX) {
			return laidOut == null ? text : laidOut.source();
		}
		return ShaderLocations.fragment(text, laidOut == null ? Map.of() : laidOut.outputs());
	}
}
*///?}
