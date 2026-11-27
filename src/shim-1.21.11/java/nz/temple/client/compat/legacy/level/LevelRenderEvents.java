package nz.temple.client.compat.legacy.level;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.LevelRenderState;

import java.util.function.Consumer;

public final class LevelRenderEvents {
	public static final Hook AFTER_OPAQUE_TERRAIN = new Hook(l -> WorldRenderEvents.BEFORE_ENTITIES.register(c -> l.on(wrap(c))));
	public static final Hook COLLECT_SUBMITS = new Hook(l -> WorldRenderEvents.BEFORE_ENTITIES.register(c -> l.on(wrap(c))));
	public static final Hook AFTER_SOLID_FEATURES = new Hook(l -> WorldRenderEvents.AFTER_ENTITIES.register(c -> l.on(wrap(c))));
	public static final Hook AFTER_TRANSLUCENT_FEATURES = new Hook(l -> WorldRenderEvents.BEFORE_TRANSLUCENT.register(c -> l.on(wrap(c))));
	public static final Hook AFTER_TRANSLUCENT_TERRAIN = new Hook(l -> WorldRenderEvents.END_MAIN.register(c -> l.on(wrap(c))));
	public static final Hook END_MAIN = new Hook(l -> WorldRenderEvents.END_MAIN.register(c -> l.on(wrap(c))));
	private LevelRenderEvents() {
	}
	private static LevelRenderContext wrap(WorldRenderContext c) {
		return new LevelRenderContext() {
			@Override
			public SubmitNodeCollector submitNodeCollector() {
				return c.commandQueue();
			}
			@Override
			public PoseStack poseStack() {
				return c.matrices();
			}
			@Override
			public GameRenderer gameRenderer() {
				return c.gameRenderer();
			}
			@Override
			public LevelRenderer levelRenderer() {
				return c.worldRenderer();
			}
			@Override
			public LevelRenderState levelState() {
				return c.worldState();
			}
			@Override
			public ChunkSectionsToRender sectionsToRender() {
				return c.sectionState();
			}
		};
	}
	@FunctionalInterface
	public interface Listener {
		void on(LevelRenderContext context);
	}
	public static final class Hook {
		private final Consumer<Listener> registrar;
		private Hook(Consumer<Listener> registrar) {
			this.registrar = registrar;
		}

		public void register(Listener listener) {
			registrar.accept(listener);
		}
	}
}
