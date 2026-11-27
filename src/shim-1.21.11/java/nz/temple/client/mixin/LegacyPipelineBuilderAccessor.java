package nz.temple.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

@Mixin(RenderPipeline.Builder.class)
public interface LegacyPipelineBuilderAccessor {
	@Accessor("vertexFormat")
	Optional<VertexFormat> templenz$vertexFormat();

	@Accessor("vertexFormatMode")
	Optional<VertexFormat.Mode> templenz$vertexFormatMode();

	@Accessor("vertexFormat")
	void templenz$setVertexFormat(Optional<VertexFormat> format);

	@Accessor("vertexFormatMode")
	void templenz$setVertexFormatMode(Optional<VertexFormat.Mode> mode);
}
