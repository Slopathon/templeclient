package com.mojang.blaze3d.pipeline;

import com.mojang.blaze3d.GpuFormat;

import java.util.Optional;

public record ColorTargetState(Optional<BlendFunction> blendFunction, GpuFormat format, int writeMask) {
	public static final int WRITE_RED = 1;
	public static final int WRITE_GREEN = 2;
	public static final int WRITE_BLUE = 4;
	public static final int WRITE_ALPHA = 8;
	public static final int WRITE_COLOR = WRITE_RED | WRITE_GREEN | WRITE_BLUE;
	public static final int WRITE_ALL = WRITE_COLOR | WRITE_ALPHA;
	public static final int WRITE_NONE = 0;
	public static final ColorTargetState DEFAULT = new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, WRITE_ALL);
	public static final int MAX_COLOR_TARGETS = 1;

	public ColorTargetState(BlendFunction blend) {
		this(Optional.of(blend), GpuFormat.RGBA8_UNORM, WRITE_ALL);
	}

	public boolean writeRed() {
		return (writeMask & WRITE_RED) != 0;
	}

	public boolean writeGreen() {
		return (writeMask & WRITE_GREEN) != 0;
	}

	public boolean writeBlue() {
		return (writeMask & WRITE_BLUE) != 0;
	}

	public boolean writeAlpha() {
		return (writeMask & WRITE_ALPHA) != 0;
	}
}
