package nz.temple.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import nz.temple.client.modules.render.Animations;
import nz.temple.client.modules.render.HandChams;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class Afterimages {
	private static final int ORDER = 42;
	private static final int TINTED = 1;
	private record Part(boolean arm, Model<Object> model, Object state, Identifier texture, TextureAtlasSprite sprite,
			List<BakedQuad> quads, Matrix4f pose, Matrix3f normal, int light, ItemStack stack) {
	}
	private record Frame(double time, List<Part> parts) {
	}
	private static final Deque<Frame> trail = new ArrayDeque<>();
	private static List<Part> recording;
	private static SubmitNodeCollector root;
	private static double lastSnapshot = -100;
	private static Matrix4f lastKey;

	private Afterimages() {
	}
	public static boolean recording() {
		return recording != null;
	}

	public static void begin(SubmitNodeCollector collector) {
		Animations a = Animations.active();
		if (a == null || !a.afterimages()) {
			recording = null;
			trail.clear();
			return;
		}
		recording = new ArrayList<>();
		root = collector;
	}
	@SuppressWarnings("unchecked")
	static void model(boolean arm, Model<?> model, Object state, PoseStack pose, Identifier texture, TextureAtlasSprite sprite,
			int light, ItemStack stack) {
		if (recording != null) {
			recording.add(new Part(arm, (Model<Object>) model, state, texture, sprite, null, new Matrix4f(pose.last().pose()),
					new Matrix3f(pose.last().normal()), light, stack));
		}
	}
	static void item(PoseStack pose, List<BakedQuad> quads, int light, ItemStack stack) {
		if (recording != null && !quads.isEmpty()) {
			recording.add(new Part(false, null, null, quads.get(0).materialInfo().sprite().atlasLocation(), null, List.copyOf(quads),
					new Matrix4f(pose.last().pose()), new Matrix3f(pose.last().normal()), light, stack));
		}
	}
	public static void end() {
		Animations a = Animations.active();
		if (recording == null || root == null || a == null) {
			return;
		}
		double now = System.nanoTime() / 1e9;
		double life = a.afterimageLength();
		while (!trail.isEmpty() && now - trail.peekFirst().time() > life) {
			trail.pollFirst();
		}
		HandChams chams = HandChams.active();
		int colour = a.afterimageColor();
		float strength = ((colour >>> 24) & 0xFF) / 255f;
		for (Frame frame : trail) {
			float age = (float) ((now - frame.time()) / life);
			float fade = (float) Math.pow(Math.max(0, 1 - age), 1.6) * strength * 0.8f;
			int argb = (Math.round(fade * 255) << 24) | (colour & 0xFFFFFF);
			if (fade < 0.01f) {
				continue;
			}
			for (Part part : frame.parts()) {
				draw(part, chams, argb);
			}
		}
		if (!recording.isEmpty() && inUse(now) && now - lastSnapshot >= a.afterimageGap() && moved(recording.get(0).pose())) {
			trail.addLast(new Frame(now, recording));
			lastSnapshot = now;
			lastKey = new Matrix4f(recording.get(0).pose());
		}
		recording = null;
		root = null;
	}
	private static boolean inUse(double now) {
		net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
		if (player == null) {
			return false;
		}
		ItemStack main = player.getMainHandItem();
		ItemStack off = player.getOffhandItem();
		if (!ItemStack.isSameItemSameComponents(main, lastMain) || !ItemStack.isSameItemSameComponents(off, lastOff)) {
			lastMain = main.copy();
			lastOff = off.copy();
			switchedAt = now;
		}
		return nz.temple.client.compat.Players.isSwinging(player) || player.isUsingItem() || now - switchedAt < 0.35;
	}
	private static ItemStack lastMain = ItemStack.EMPTY;
	private static ItemStack lastOff = ItemStack.EMPTY;
	private static double switchedAt = -100;
	private static boolean moved(Matrix4f pose) {
		if (lastKey == null) {
			return true;
		}
		float d = 0;
		for (int c = 0; c < 4; c++) {
			for (int r = 0; r < 4; r++) {
				float x = pose.get(c, r) - lastKey.get(c, r);
				d += x * x;
			}
		}
		return d > 1e-5f;
	}
	private static void draw(Part part, HandChams chams, int argb) {
		PoseStack stack = new PoseStack();
		stack.last().pose().set(part.pose());
		stack.last().normal().set(part.normal());
		boolean styled = chams != null && (part.arm() ? chams.handOn() : chams.weaponOn(part.stack()));
		int overlay = styled ? chams.overlay(part.arm()) : TINTED | (100 << 16);
		RenderType type = ChamsRenderTypes.handGhost(part.texture());
		if (part.model() != null) {
			nz.temple.client.compat.Submit.model(root.order(ORDER), part.model(), part.state(), stack, type, part.light(), overlay, argb, part.sprite(), 0);
		} else {
			List<BakedQuad> quads = part.quads();
			int light = part.light();
			root.order(ORDER).submitCustomGeometry(stack, type, (pose, consumer) -> {
				QuadInstance instance = new QuadInstance();
				instance.setColor(argb);
				instance.setLightCoords(light);
				instance.setOverlayCoords(overlay);
				for (BakedQuad quad : quads) {
					consumer.putBakedQuad(pose, quad, instance);
				}
			});
		}
	}
}
