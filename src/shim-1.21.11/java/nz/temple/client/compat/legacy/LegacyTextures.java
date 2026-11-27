package nz.temple.client.compat.legacy;

import com.mojang.blaze3d.GpuFormat;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public final class LegacyTextures {
	private static final ThreadLocal<GpuFormat> PENDING = new ThreadLocal<>();
	private LegacyTextures() {
	}

	public static <T> T withFormat(GpuFormat format, Supplier<T> create) {
		if (format == null || format.native121()) {
			return create.get();
		}
		GpuFormat previous = PENDING.get();
		PENDING.set(format);
		try {
			return create.get();
		} finally {
			PENDING.set(previous);
		}
	}
	public static @Nullable GpuFormat pending() {
		return PENDING.get();
	}
}
