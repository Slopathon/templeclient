package nz.temple.client.compat.legacy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.model.BakedQuad;

public final class LegacyQuads {
	private LegacyQuads() {
	}

	public static void put(VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad, QuadInstance instance) {
		int c = instance.color();
		consumer.putBulkData(pose, quad, (c >> 16 & 0xFF) / 255f, (c >> 8 & 0xFF) / 255f, (c & 0xFF) / 255f, (c >>> 24) / 255f,
				instance.lightCoords(), instance.overlayCoords());
	}
}
