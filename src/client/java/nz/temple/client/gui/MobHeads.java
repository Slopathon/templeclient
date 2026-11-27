package nz.temple.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public final class MobHeads {
	private static final Map<String, LivingEntity> MOBS = new HashMap<>();
	private static final java.util.Set<String> NONE = new java.util.HashSet<>();
	private static Level builtFor;
	private MobHeads() {
	}

	private static LivingEntity mob(String id) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return null;
		}
		if (builtFor != mc.level) {
			MOBS.clear();
			NONE.clear();
			builtFor = mc.level;
		}
		if (NONE.contains(id)) {
			return null;
		}
		LivingEntity made = MOBS.computeIfAbsent(id, key -> {
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(net.minecraft.resources.Identifier.tryParse(key)).orElse(null);
			if (type == null) {
				return null;
			}
			try {
				Entity e = type.create(mc.level, EntitySpawnReason.LOAD);
				if (!(e instanceof LivingEntity living)) {
					return null;
				}
				living.setId(-1_000_000 - MOBS.size());
				return living;
			} catch (RuntimeException | LinkageError ex) {
				return null;
			}
		});
		if (made == null) {
			NONE.add(id);
		}
		return made;
	}
	public static boolean draw(GuiGraphicsExtractor g, String id, float lx0, float ly0, float lx1, float ly1) {
		org.joml.Vector2f a = g.pose().transformPosition(lx0, ly0, new org.joml.Vector2f());
		org.joml.Vector2f b = g.pose().transformPosition(lx1, ly1, new org.joml.Vector2f());
		int x0 = Math.round(Math.min(a.x, b.x));
		int y0 = Math.round(Math.min(a.y, b.y));
		int x1 = Math.round(Math.max(a.x, b.x));
		int y1 = Math.round(Math.max(a.y, b.y));
		if (x1 - x0 < 2 || y1 - y0 < 2) {
			return false;
		}
		LivingEntity mob = mob(id);
		if (mob == null) {
			return false;
		}
		try {
			EntityRenderState state = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(mob).createRenderState(mob, 1f);
			state.shadowPieces.clear();
			state.outlineColor = 0;
			state.lightCoords = 0xF000F0;
			if (state instanceof LivingEntityRenderState living) {
				living.bodyRot = mob.getBbWidth() >= 0.9f && mob.getEyeHeight() >= 1.3f ? 150 : 180;
				living.yRot = 0;
				living.xRot = 0;
			}
			float head = Math.max(0.35f, Math.min(1.3f, Math.min(mob.getBbWidth(), mob.getBbHeight()) * 0.5f + 0.2f));
			float scale = (y1 - y0) / head;
			Vector3f centre = new Vector3f(0, mob.getEyeHeight(), 0);
			Quaternionf turn = new Quaternionf().rotateZ((float) Math.PI);
			Quaternionf camera = new Quaternionf();
			g.entity(state, scale, centre, turn, camera, x0, y0, x1, y1);
			return true;
		} catch (RuntimeException | LinkageError ex) {
			MOBS.remove(id);
			NONE.add(id);
			return false;
		}
	}
}
