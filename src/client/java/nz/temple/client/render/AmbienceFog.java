package nz.temple.client.render;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.world.level.material.FogType;
import nz.temple.client.modules.render.Ambience;
import org.joml.Vector4f;

public final class AmbienceFog {
	private static final float FOG_MARK = -10000f;
	private static final boolean SODIUM = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium");
	private AmbienceFog() {
	}

	public static void apply(Camera camera, FogData fog, Vector4f color) {
		Ambience ambience = Ambience.active();
		if (ambience == null || !ambience.customFog()
				|| !ambience.fogInFluids() && camera.getFluidInCamera() != FogType.NONE) {
			Vector4f horizon = camera.getFluidInCamera() == FogType.NONE ? nz.temple.client.modules.render.Skybox.horizonFog() : null;
			if (horizon != null) {
				color.set(horizon);
			}
			return;
		}
		color.set(ambience.fogColor());
		if (SODIUM) {
			fog.environmentalStart = ambience.fogStart();
			fog.environmentalEnd = ambience.fogEnd();
			if (WorldEffects.current() == null) {
				fog.skyEnd = Math.min(fog.skyEnd, ambience.fogEnd());
			}
			fog.cloudEnd = Math.min(fog.cloudEnd, ambience.fogEnd());
			return;
		}
		float thickness = ambience.thickness();
		int end = Math.round(ambience.fogEnd());
		int start = Math.min(end - 1, Math.round(ambience.exponentialStart()));
		fog.environmentalStart = FOG_MARK - (end * 1024 + Math.max(0, start));
		fog.environmentalEnd = Math.round(thickness * 100) * 1000 + Math.round(ambience.gradient());
		if (WorldEffects.current() == null) {
			fog.skyEnd = -Math.max(0.0001f, thickness);
		}
		if (thickness > 0.5f) {
			fog.cloudEnd = Math.min(fog.cloudEnd, ambience.fogEnd() / thickness);
		}
	}
}
