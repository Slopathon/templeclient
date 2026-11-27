package com.mojang.blaze3d.pipeline;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.shaders.UniformType;

import java.util.ArrayList;
import java.util.List;

public final class BindGroupLayout {
	public record UniformDescription(String name, UniformType type, GpuFormat format) {
		public UniformDescription(String name, UniformType type) {
			this(name, type, null);
		}
		public UniformDescription(String name, GpuFormat format) {
			this(name, UniformType.TEXEL_BUFFER, format);
		}
	}
	private final List<String> samplers;
	private final List<UniformDescription> uniforms;

	private BindGroupLayout(List<String> samplers, List<UniformDescription> uniforms) {
		this.samplers = List.copyOf(samplers);
		this.uniforms = List.copyOf(uniforms);
	}
	public static Builder builder() {
		return new Builder();
	}
	public List<String> getSamplers() {
		return samplers;
	}
	public List<UniformDescription> getUniforms() {
		return uniforms;
	}
	public static final class Builder {
		private final List<String> samplers = new ArrayList<>();
		private final List<UniformDescription> uniforms = new ArrayList<>();
		public Builder withSampler(String name) {
			samplers.add(name);
			return this;
		}
		public Builder withUniform(String name, UniformType type) {
			uniforms.add(new UniformDescription(name, type));
			return this;
		}
		public Builder withUniform(String name, UniformType type, GpuFormat format) {
			uniforms.add(new UniformDescription(name, type, format));
			return this;
		}
		public BindGroupLayout build() {
			return new BindGroupLayout(samplers, uniforms);
		}
	}
}
