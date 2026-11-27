package com.mojang.blaze3d;

import com.mojang.blaze3d.vertex.VertexFormat;

public enum PrimitiveTopology {
	LINES(VertexFormat.Mode.LINES),
	DEBUG_LINES(VertexFormat.Mode.DEBUG_LINES),
	DEBUG_LINE_STRIP(VertexFormat.Mode.DEBUG_LINE_STRIP),
	POINTS(VertexFormat.Mode.POINTS),
	TRIANGLES(VertexFormat.Mode.TRIANGLES),
	TRIANGLE_STRIP(VertexFormat.Mode.TRIANGLE_STRIP),
	TRIANGLE_FAN(VertexFormat.Mode.TRIANGLE_FAN),
	QUADS(VertexFormat.Mode.QUADS);

	public final VertexFormat.Mode mode;

	PrimitiveTopology(VertexFormat.Mode mode) {
		this.mode = mode;
	}

	public int indexCount(int vertices) {
		return mode.indexCount(vertices);
	}

	public static PrimitiveTopology of(VertexFormat.Mode mode) {
		for (PrimitiveTopology t : values()) {
			if (t.mode == mode) {
				return t;
			}
		}
		throw new IllegalArgumentException(String.valueOf(mode));
	}
}
