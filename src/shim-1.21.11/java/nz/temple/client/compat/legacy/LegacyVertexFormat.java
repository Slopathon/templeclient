package nz.temple.client.compat.legacy;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

public final class LegacyVertexFormat {
	private LegacyVertexFormat() {
	}
	public static Builder builder(int stepRate) {
		return new Builder();
	}
	public static int offset(VertexFormat format, String name) {
		int i = format.getElementAttributeNames().indexOf(name);
		if (i < 0) {
			throw new IllegalArgumentException(format + " has no attribute " + name);
		}
		return format.getOffset(format.getElements().get(i));
	}
	public static final class Builder {
		private final VertexFormat.Builder builder = VertexFormat.builder();

		public Builder addAttribute(String name, GpuFormat format) {
			VertexFormatElement element = switch (name) {
				case "Position" -> VertexFormatElement.POSITION;
				case "Color" -> VertexFormatElement.COLOR;
				case "UV0" -> VertexFormatElement.UV0;
				case "UV1" -> VertexFormatElement.UV1;
				case "UV2" -> VertexFormatElement.UV2;
				case "Normal" -> VertexFormatElement.NORMAL;
				case "LineWidth" -> VertexFormatElement.LINE_WIDTH;
				default -> throw new IllegalArgumentException("No 1.21.11 vertex element for " + name);
			};
			builder.add(name, element);
			int padding = format.blockSize() - element.byteSize();
			if (padding > 0) {
				builder.padding(padding);
			}
			return this;
		}
		public VertexFormat build() {
			return builder.build();
		}
	}
}
