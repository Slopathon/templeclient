package nz.temple.client.compat.legacy;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.Identifier;
import nz.temple.client.mixin.LegacyPipelineBuilderAccessor;

import java.util.Optional;

public final class LegacyPipeline {
	private LegacyPipeline() {
	}
	public static Builder builder(RenderPipeline.Snippet... snippets) {
		return new Builder(RenderPipeline.builder(snippets));
	}

	public static final class Builder {
		private final RenderPipeline.Builder builder;
		private VertexFormat format;
		private PrimitiveTopology topology;
		private Builder(RenderPipeline.Builder builder) {
			this.builder = builder;
		}

		public Builder withLocation(String location) {
			builder.withLocation(location);
			return this;
		}
		public Builder withLocation(Identifier location) {
			builder.withLocation(location);
			return this;
		}
		public Builder withVertexShader(String shader) {
			builder.withVertexShader(shader);
			return this;
		}
		public Builder withVertexShader(Identifier shader) {
			builder.withVertexShader(shader);
			return this;
		}
		public Builder withFragmentShader(String shader) {
			builder.withFragmentShader(shader);
			return this;
		}
		public Builder withFragmentShader(Identifier shader) {
			builder.withFragmentShader(shader);
			return this;
		}
		public Builder withShaderDefine(String flag) {
			builder.withShaderDefine(flag);
			return this;
		}
		public Builder withShaderDefine(String name, int value) {
			builder.withShaderDefine(name, value);
			return this;
		}
		public Builder withShaderDefine(String name, float value) {
			builder.withShaderDefine(name, value);
			return this;
		}
		public Builder withBindGroupLayout(BindGroupLayout layout) {
			for (String sampler : layout.getSamplers()) {
				builder.withSampler(sampler);
			}
			for (BindGroupLayout.UniformDescription uniform : layout.getUniforms()) {
				if (uniform.format() != null) {
					builder.withUniform(uniform.name(), uniform.type(), uniform.format().textureFormat());
				} else {
					builder.withUniform(uniform.name(), uniform.type());
				}
			}
			return this;
		}
		public Builder withPolygonMode(PolygonMode mode) {
			builder.withPolygonMode(mode);
			return this;
		}
		public Builder withCull(boolean cull) {
			builder.withCull(cull);
			return this;
		}
		public Builder withColorTargetState(ColorTargetState state) {
			return withColorTargetState(0, state);
		}

		public Builder withColorTargetState(int slot, ColorTargetState state) {
			if (slot != 0) {
				return this;
			}
			state.blendFunction().ifPresentOrElse(builder::withBlend, builder::withoutBlend);
			builder.withColorWrite(state.writeRed() || state.writeGreen() || state.writeBlue(), state.writeAlpha());
			return this;
		}
		public Builder withUnusedColorTargetState(int slot) {
			return this;
		}

		public Builder withDepthStencilState(DepthStencilState state) {
			builder.withDepthTestFunction(state.depthTest().depthTest());
			builder.withDepthWrite(state.writeDepth());
			if (state.depthBiasScaleFactor() != 0 || state.depthBiasConstant() != 0) {
				builder.withDepthBias(state.depthBiasScaleFactor(), state.depthBiasConstant());
			}
			return this;
		}
		public Builder withDepthStencilState(Optional<DepthStencilState> state) {
			if (state.isPresent()) {
				return withDepthStencilState(state.get());
			}
			builder.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
			builder.withDepthWrite(false);
			return this;
		}
		public Builder withVertexBinding(int slot, VertexFormat vertexFormat) {
			if (slot == 0) {
				format = vertexFormat;
			}
			return this;
		}
		public Builder withPrimitiveTopology(PrimitiveTopology primitiveTopology) {
			topology = primitiveTopology;
			return this;
		}
		private void applyVertexFormat() {
			LegacyPipelineBuilderAccessor fields = (LegacyPipelineBuilderAccessor) builder;
			if (format != null) {
				fields.templenz$setVertexFormat(Optional.of(format));
			}
			if (topology != null) {
				fields.templenz$setVertexFormatMode(Optional.of(topology.mode));
			}
		}
		public RenderPipeline.Snippet buildSnippet() {
			applyVertexFormat();
			return builder.buildSnippet();
		}
		public RenderPipeline build() {
			applyVertexFormat();

			LegacyPipelineBuilderAccessor fields = (LegacyPipelineBuilderAccessor) builder;
			if (fields.templenz$vertexFormat().isEmpty()) {
				fields.templenz$setVertexFormat(Optional.of(com.mojang.blaze3d.vertex.DefaultVertexFormat.EMPTY));
			}
			if (fields.templenz$vertexFormatMode().isEmpty()) {
				fields.templenz$setVertexFormatMode(Optional.of(VertexFormat.Mode.TRIANGLES));
			}
			return builder.build();
		}
	}
}
