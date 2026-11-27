package nz.temple.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.event.WorldRenderEvent;
import nz.temple.client.render.WorldEsp;

/**
 * Immediate-style world boxes and lines for combat modules, routed through Temple's {@link WorldEsp} so they use Temple's
 * renderer on every supported Minecraft version. Calls made while {@link WorldRenderEvent} fires are collected and handed
 * to WorldEsp for that frame. Coordinates are world space.
 */
public final class WorldDraw {
	private static final List<WorldEsp.Box> boxes = new ArrayList<>();
	private static final List<WorldEsp.Line> lines = new ArrayList<>();
	private static final float BOX_LINE = 0.008f;

	private WorldDraw() {
	}

	/** Adds the feeds that fire {@link WorldRenderEvent} once per frame and draw what the listeners queued. */
	public static void attach(WorldEsp esp) {
		esp.addSource(out -> {
			boxes.clear();
			lines.clear();
			float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
			WorldRenderEvent.fire(new PoseStack(), partial);
			out.addAll(boxes);
		});
		esp.addLineSource((out, camera, partial) -> out.addAll(lines));
	}

	public static Vec3 getCameraPos() {
		Minecraft mc = Minecraft.getInstance();
		return mc.gameRenderer == null ? Vec3.ZERO : mc.gameRenderer.mainCamera().position();
	}

	public static void drawBoxes(PoseStack poseStack, List<AABB> list, int argbFill, int argbLines, boolean doFill, boolean doLines) {
		for (AABB b : list) {
			boxes.add(new WorldEsp.Box(b, doLines ? argbLines : 0, doFill ? argbFill : 0, doLines ? BOX_LINE : 0));
		}
	}

	public static void drawBoxes(PoseStack poseStack, List<AABB> list, int argbFill, int argbLines) {
		drawBoxes(poseStack, list, argbFill, argbLines, true, true);
	}

	public static void drawWorldLine(PoseStack poseStack, Vec3 a, Vec3 b, int argb, float lineWidth) {
		lines.add(new WorldEsp.Line(a.x, a.y, a.z, b.x, b.y, b.z, argb, lineWidth));
	}

	public static void drawWorldLine(PoseStack poseStack, Vec3 a, Vec3 b, int argb, float lineWidth, boolean throughWalls) {
		drawWorldLine(poseStack, a, b, argb, lineWidth);
	}
}
