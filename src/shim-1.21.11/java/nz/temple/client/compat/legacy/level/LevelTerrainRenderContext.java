package nz.temple.client.compat.legacy.level;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.LevelRenderState;

public interface LevelTerrainRenderContext {
	GameRenderer gameRenderer();

	LevelRenderer levelRenderer();

	LevelRenderState levelState();

	ChunkSectionsToRender sectionsToRender();
}
