package com.mojang.blaze3d.systems;

import com.mojang.blaze3d.textures.GpuTextureView;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;

public final class RenderPassDescriptor {
	public final @Nullable Object renderArea = null;
	private final Supplier<String> label;
	private final List<ColorAttachment> colors = new ArrayList<>();
	private @Nullable DepthAttachment depth;

	private RenderPassDescriptor(Supplier<String> label) {
		this.label = label;
	}

	public static RenderPassDescriptor create(Supplier<String> label) {
		return new RenderPassDescriptor(label);
	}

	public Supplier<String> label() {
		return label;
	}

	public List<@Nullable ColorAttachment> colorAttachments() {
		return colors;
	}

	public @Nullable DepthAttachment depthAttachment() {
		return depth;
	}

	public RenderPassDescriptor withColorAttachment(GpuTextureView view, OptionalInt clear) {
		colors.add(new ColorAttachment(view, clear));
		return this;
	}
	public RenderPassDescriptor withUnusedColorAttachment() {
		colors.add(null);
		return this;
	}
	public RenderPassDescriptor withDepthAttachment(GpuTextureView view, OptionalDouble clear) {
		depth = new DepthAttachment(view, clear);
		return this;
	}
	public RenderPassDescriptor withRenderArea(Object area) {
		return this;
	}

	public RenderPass open(CommandEncoder encoder) {
		ColorAttachment color = colors.isEmpty() ? null : colors.get(0);
		if (color == null) {
			throw new IllegalStateException("A 1.21.11 render pass needs a colour target");
		}
		if (depth == null) {
			return encoder.createRenderPass(label, color.textureView(), color.clearValue());
		}
		return encoder.createRenderPass(label, color.textureView(), color.clearValue(), depth.textureView(), depth.clearValue());
	}
	public record ColorAttachment(GpuTextureView textureView, OptionalInt clearValue) {
	}
	public record DepthAttachment(GpuTextureView textureView, OptionalDouble clearValue) {
	}
}
