package com.mojang.blaze3d;

import com.mojang.blaze3d.textures.TextureFormat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

public enum GpuFormat {
	R8_UNORM(TextureFormat.RED8, GL30.GL_R8, GL30.GL_RED, GL11.GL_UNSIGNED_BYTE, 1),
	RGBA8_UNORM(TextureFormat.RGBA8, GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 4),
	RGBA8_SNORM(TextureFormat.RGBA8, GL31.GL_RGBA8_SNORM, GL11.GL_RGBA, GL11.GL_BYTE, 4),
	RG16_SINT(TextureFormat.RGBA8, GL30.GL_RG16I, GL30.GL_RG_INTEGER, GL11.GL_SHORT, 4),
	R16_FLOAT(TextureFormat.RGBA8, GL30.GL_R16F, GL30.GL_RED, GL30.GL_HALF_FLOAT, 2),
	RGBA16_FLOAT(TextureFormat.RGBA8, GL30.GL_RGBA16F, GL11.GL_RGBA, GL30.GL_HALF_FLOAT, 8),
	R32_FLOAT(TextureFormat.RGBA8, GL30.GL_R32F, GL30.GL_RED, GL11.GL_FLOAT, 4),
	RG32_FLOAT(TextureFormat.RGBA8, GL30.GL_RG32F, GL30.GL_RG, GL11.GL_FLOAT, 8),
	RGB32_FLOAT(TextureFormat.RGBA8, GL30.GL_RGB32F, GL11.GL_RGB, GL11.GL_FLOAT, 12),
	RGBA32_FLOAT(TextureFormat.RGBA8, GL30.GL_RGBA32F, GL11.GL_RGBA, GL11.GL_FLOAT, 16),
	D32_FLOAT(TextureFormat.DEPTH32, GL30.GL_DEPTH_COMPONENT32F, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, 4);
	private final TextureFormat textureFormat;
	private final int glInternalFormat;
	private final int glFormat;
	private final int glType;
	private final int blockSize;
	GpuFormat(TextureFormat textureFormat, int glInternalFormat, int glFormat, int glType, int blockSize) {
		this.textureFormat = textureFormat;
		this.glInternalFormat = glInternalFormat;
		this.glFormat = glFormat;
		this.glType = glType;
		this.blockSize = blockSize;
	}
	public TextureFormat textureFormat() {
		return textureFormat;
	}
	public boolean native121() {
		return this == R8_UNORM || this == RGBA8_UNORM || this == D32_FLOAT;
	}
	public int glInternalFormat() {
		return glInternalFormat;
	}

	public int glFormat() {
		return glFormat;
	}

	public int glType() {
		return glType;
	}

	public int blockSize() {
		return blockSize;
	}

	public boolean hasColorAspect() {
		return this != D32_FLOAT;
	}

	public boolean hasDepthAspect() {
		return this == D32_FLOAT;
	}
}
