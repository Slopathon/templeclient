package nz.temple.client.compat.legacy.level;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;

public interface LevelRenderContext extends LevelTerrainRenderContext {
	SubmitNodeCollector submitNodeCollector();

	PoseStack poseStack();
}
