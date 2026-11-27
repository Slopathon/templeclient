package nz.temple.client.render;

import nz.temple.client.compat.Gpu;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.function.Supplier;

public final class VertexRing implements AutoCloseable {
	public record Upload(GpuBuffer vertices, @org.jspecify.annotations.Nullable GpuBuffer ownIndices,
			@org.jspecify.annotations.Nullable PrimitiveTopology sequential, int indexCount) {
		public GpuBuffer indices() {
			return ownIndices != null ? ownIndices : Gpu.sequentialBuffer(sequential).getBuffer(indexCount);
		}
		public IndexType indexType() {
			return ownIndices != null ? IndexType.INT : Gpu.sequentialBuffer(sequential).type();
		}
	}
	private static final int RING = 3;
	private final Supplier<String> label;
	private final VertexFormat format;
	private final PrimitiveTopology topology;
	private final ByteBufferBuilder staging;
	private final GpuBuffer[] buffers = new GpuBuffer[RING];
	private int current;
	private BufferBuilder builder;
	private int rawVertices;
	private ByteBufferBuilder indexStaging;
	private final GpuBuffer[] indexBuffers = new GpuBuffer[RING];
	private int rawIndices;
	public VertexRing(Supplier<String> label, VertexFormat format, PrimitiveTopology topology, int initialCapacity) {
		this.label = label;
		this.format = format;
		this.topology = topology;
		this.staging = new ByteBufferBuilder(initialCapacity);
	}
	public VertexConsumer consumer() {
		if (builder == null) {
			builder = Gpu.bufferBuilder(staging, topology, format);
		}
		return builder;
	}
	public boolean building() {
		return builder != null || rawVertices > 0;
	}

	public long vertex() {
		rawVertices++;
		return staging.reserve(format.getVertexSize());
	}
	public long vertices(int count) {
		rawVertices += count;
		return staging.reserve(format.getVertexSize() * count);
	}

	public int vertexCount() {
		return rawVertices;
	}
	public long indices(int count) {
		if (indexStaging == null) {
			indexStaging = new ByteBufferBuilder(1 << 16);
		}
		rawIndices += count;
		return indexStaging.reserve(count * 4);
	}
	public Upload upload() {
		if (rawIndices > 0) {
			int count = rawIndices;
			rawIndices = 0;
			rawVertices = 0;
			try (ByteBufferBuilder.Result vertices = staging.build(); ByteBufferBuilder.Result indices = indexStaging.build()) {
				if (vertices == null || indices == null) {
					return null;
				}
				Upload sent = send(vertices.byteBuffer(), count);
				java.nio.ByteBuffer data = indices.byteBuffer();
				int bytes = data.remaining();
				GpuDevice device = RenderSystem.getDevice();
				GpuBuffer target = indexBuffers[current];
				if (target == null || target.size() < bytes) {
					if (target != null) {
						target.close();
					}
					long size = Math.max(1 << 16, Long.highestOneBit(bytes - 1L) << 1);
					int slot = current;
					target = device.createBuffer(() -> label.get() + " indices #" + slot, GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST, size);
					indexBuffers[current] = target;
				}
				device.createCommandEncoder().writeToBuffer(target.slice(0, bytes), data);
				return new Upload(sent.vertices(), target, null, count);
			}
		}
		if (rawVertices > 0) {
			int count = rawVertices;
			rawVertices = 0;
			try (ByteBufferBuilder.Result result = staging.build()) {
				if (result == null) {
					return null;
				}
				int indexCount = topology == PrimitiveTopology.QUADS ? count / 4 * 6 : count;
				return send(result.byteBuffer(), indexCount);
			}
		}
		if (builder == null) {
			return null;
		}
		BufferBuilder b = builder;
		builder = null;
		try (MeshData mesh = b.build()) {
			if (mesh == null) {
				return null;
			}
			return send(mesh.vertexBuffer(), mesh.drawState().indexCount());
		}
	}
	private Upload send(java.nio.ByteBuffer data, int indexCount) {
		{
			int bytes = data.remaining();
			current = (current + 1) % RING;
			GpuDevice device = RenderSystem.getDevice();
			GpuBuffer target = buffers[current];
			if (target == null || target.size() < bytes) {
				if (target != null) {
					target.close();
				}
				long size = Math.max(1 << 18, Long.highestOneBit(bytes - 1L) << 1);
				int slot = current;
				target = device.createBuffer(() -> label.get() + " #" + slot, GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, size);
				buffers[current] = target;
			}
			device.createCommandEncoder().writeToBuffer(target.slice(0, bytes), data);
			return new Upload(target, null, topology, indexCount);
		}
	}

	@Override
	public void close() {
		for (int i = 0; i < RING; i++) {
			if (buffers[i] != null) {
				buffers[i].close();
				buffers[i] = null;
			}
			if (indexBuffers[i] != null) {
				indexBuffers[i].close();
				indexBuffers[i] = null;
			}
		}
		staging.close();
		if (indexStaging != null) {
			indexStaging.close();
		}
	}
}
