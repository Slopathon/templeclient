package net.minecraft.client.renderer;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

public class StagedVertexBuffer implements AutoCloseable {
	private final Supplier<String> label;
	private final ByteBufferBuilder bytes;
	private @Nullable BufferBuilder builder;
	private @Nullable GpuBuffer buffer;
	private @Nullable GpuBuffer indices;
	private PrimitiveTopology topology = PrimitiveTopology.QUADS;
	private int indexCount;
	public StagedVertexBuffer(Supplier<String> label, int size) {
		this.label = label;
		this.bytes = new ByteBufferBuilder(size);
	}
	public Draw appendDraw(VertexFormat format, PrimitiveTopology topology, @Nullable VertexSorting sorting) {
		this.topology = topology;
		builder = new BufferBuilder(bytes, topology.mode, format);
		return new Draw();
	}
	public VertexConsumer getVertexBuilder(Draw draw) {
		return builder;
	}
	public void upload() {
		indexCount = 0;
		if (builder == null) {
			return;
		}
		MeshData mesh = builder.build();
		builder = null;
		if (mesh == null) {
			return;
		}
		try (mesh) {
			ByteBuffer vertices = mesh.vertexBuffer();
			int size = vertices.remaining();
			if (buffer == null || buffer.size() < size) {
				if (buffer != null) {
					buffer.close();
				}
				buffer = RenderSystem.getDevice().createBuffer(label, GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, Math.max(size, 4096));
			}
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0, size), vertices);
			indexCount = mesh.drawState().indexCount();
		}
	}
	public @Nullable ExecuteInfo getExecuteInfo(Draw draw) {
		if (indexCount == 0 || buffer == null) {
			return null;
		}
		if (indices == null || indices.size() < (long) indexCount * Integer.BYTES) {
			if (indices != null) {
				indices.close();
			}
			int count = Math.max(indexCount, 6 * 1024);
			ByteBuffer data = org.lwjgl.system.MemoryUtil.memAlloc(count * Integer.BYTES);
			try {
				for (int quad = 0; quad < count / 6; quad++) {
					int v = quad * 4;
					data.putInt(v).putInt(v + 1).putInt(v + 2).putInt(v + 2).putInt(v + 3).putInt(v);
				}
				data.flip();
				indices = RenderSystem.getDevice().createBuffer(() -> label.get() + " indices", GpuBuffer.USAGE_INDEX, data);
			} finally {
				org.lwjgl.system.MemoryUtil.memFree(data);
			}
		}
		return new ExecuteInfo(buffer, indices, VertexFormat.IndexType.INT, 0, 0, indexCount);
	}
	public void endDraw() {
	}

	public void endFrame() {
	}

	@Override
	public void close() {
		bytes.close();
		if (buffer != null) {
			buffer.close();
			buffer = null;
		}
		if (indices != null) {
			indices.close();
			indices = null;
		}
	}
	public static final class Draw {
	}

	public record ExecuteInfo(GpuBuffer vertexBuffer, GpuBuffer indexBuffer, VertexFormat.IndexType indexType, int baseVertex, int firstIndex,
			int indexCount) {
	}
}
