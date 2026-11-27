package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.VisualRange;
import nz.temple.client.render.Theme;
import nz.temple.client.render.WorldEsp;
import nz.temple.client.util.Entities;
import nz.temple.client.util.EntityCatalog;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.EntitySelection;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;
import nz.temple.social.FriendManager;

import java.util.ArrayList;
import java.util.List;

public final class Tracers extends Module {
	private final EntitySelection entities;
	private final BoolSetting exactFilter;
	private final BoolSetting players;
	private final BoolSetting friendsOn;
	private final BoolSetting mobs;
	private final BoolSetting passive;
	private final BoolSetting villagers;
	private final BoolSetting other;
	private final NumberSetting range;
	private final ModeSetting target;
	private final ModeSetting colorMode;
	private final ColorSetting playerColor;
	private final ColorSetting mobColor;
	private final ColorSetting friendColor;
	private final ColorSetting passiveColor;
	private final ColorSetting villagerColor;
	private final ColorSetting otherColor;
	private final BoolSetting themedTypes;
	private final NumberSetting width;
	private final NumberSetting opacity;
	private final BoolSetting fadeDistance;
	private final BoolSetting stem;

	private final FriendManager friends;

	public Tracers(FriendManager friends) {
		super("Tracers", Category.RENDER, "Draws lines from your crosshair to selected entities.");
		this.friends = friends;
		players = add(new BoolSetting("Players", true).describe("Draws lines to players who are not friends."));
		friendsOn = add(new BoolSetting("Friends", true).describe("Draws lines to friends, with their own colour."));
		mobs = add(new BoolSetting("Hostile mobs", true).describe("Draws lines to hostile mobs."));
		passive = add(new BoolSetting("Passive", false).describe("Draws lines to passive animals and other non-hostile entities."));
		villagers = add(new BoolSetting("Villagers", true).describe("Draws lines to villagers, separately from other passive entities."));
		other = add(new BoolSetting("Other entities", false).describe("Draws lines to selected non-living entities, such as items or "
				+ "projectiles."));
		exactFilter = add(new BoolSetting("Exact entity filter", false)
				.describe("Also requires the type to be selected in the entity picker below."));
		entities = add(new EntitySelection("Entities", defaults())
				.describe("Entity types used when Exact entity filter is on.").visibleWhen(exactFilter::get));
		range = add(new NumberSetting("Range", 128, 8, 512, 4, " m").describe("Only draws lines to entities within this distance."));
		group();
		target = add(new ModeSetting("Target", List.of("Body", "Head", "Feet"), "Body")
				.describe("Where on the entity the line ends."));
		width = add(new NumberSetting("Width", 1, 0.5, 4, 0.25, " px").describe("Line width in pixels."));
		opacity = add(new NumberSetting("Opacity", 85, 10, 100, 5, "%").describe("Line opacity."));
		fadeDistance = add(new BoolSetting("Fade with distance", true).describe("Makes lines to distant entities fainter, so nearby ones "
				+ "stand out."));
		stem = add(new BoolSetting("Stem", false).describe("Also draws a vertical line through each entity from feet to head."));
		group();
		colorMode = add(new ModeSetting("Color", List.of("Type", "Distance", "Health", "Theme"), "Type")
				.describe("Type gives players and mobs their own colours. Distance goes from red when close to green when far. Health "
						+ "colours by remaining health. Theme uses the HUD accent."));
		themedTypes = add(new BoolSetting("Theme type palette", true)
				.describe("Builds the type colours from the current theme. Turn off to use the colours below.")
				.visibleWhen(() -> colorMode.is("Type")));
		playerColor = add(new ColorSetting("Player color", 0xFFE9B949).themed().describe("Colour for players in Type mode.")
				.visibleWhen(() -> colorMode.is("Type") && !themedTypes.get()));
		mobColor = add(new ColorSetting("Mob color", 0xFFFF5A5A).describe("Colour for everything else in Type mode.")
				.visibleWhen(() -> colorMode.is("Type") && !themedTypes.get()));
		friendColor = add(new ColorSetting("Friend color", 0xFF5AC8FA).describe("Colour for friends in Type mode.")
				.visibleWhen(() -> colorMode.is("Type") && !themedTypes.get()));
		passiveColor = add(new ColorSetting("Passive color", 0xFF55D187).describe("Colour for passive entities in Type mode.")
				.visibleWhen(() -> colorMode.is("Type") && !themedTypes.get()));
		villagerColor = add(new ColorSetting("Villager color", 0xFFFFC857).describe("Colour for villagers in Type mode.")
				.visibleWhen(() -> colorMode.is("Type") && !themedTypes.get()));
		otherColor = add(new ColorSetting("Other color", 0xFFFFFFFF).describe("Colour for other selected entities in Type mode.")
				.visibleWhen(() -> colorMode.is("Type") && !themedTypes.get()));
	}

	private static List<String> defaults() {
		List<String> ids = new ArrayList<>(EntityCatalog.idsInCategory("monster"));
		ids.add("minecraft:player");
		return ids;
	}

	public void collect(List<WorldEsp.Line> out, CameraRenderState camera, float partial) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer self = mc.player;
		if (!isEnabled() || self == null || mc.level == null || camera.pos == null) {
			return;
		}
		Vec3 forward = Vec3.directionFromRotation(camera.xRot, camera.yRot);
		org.joml.Vector3f ahead = nz.temple.client.render.LevelProjection.ahead(1.5f);
		Vec3 start = ahead != null ? camera.pos.add(ahead.x, ahead.y, ahead.z) : camera.pos.add(forward.scale(1.5));
		double startAhead = start.subtract(camera.pos).dot(forward);
		double maxSq = VisualRange.of(range.get()) * VisualRange.of(range.get());
		float w = width.getFloat();
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (entity == self || !entity.isAlive() || entity.isInvisible() || !shows(entity) || entity.distanceToSqr(self) > maxSq) {
				continue;
			}
			Vec3 p = entity.getPosition(partial);
			double y = switch (target.get()) {
				case "Head" -> p.y + entity.getEyeHeight();
				case "Feet" -> p.y;
				default -> p.y + entity.getBbHeight() * 0.5;
			};
			double distance = Math.sqrt(entity.distanceToSqr(self));
			float alpha = opacity.getFloat() / 100f;
			if (fadeDistance.get()) {
				alpha *= (float) Math.max(0.25, 1 - distance / Math.max(16, VisualRange.of(range.get())));
			}
			int color = (Math.round(alpha * 255) << 24) | (color(entity, distance) & 0xFFFFFF);
			Vec3 end = new Vec3(p.x, y, p.z);
			double endAhead = end.subtract(camera.pos).dot(forward);
			if (endAhead < 0.5) {
				double t = (0.5 - startAhead) / (endAhead - startAhead);
				if (!(t > 0) || !(t < 1)) {
					continue;
				}
				end = start.add(end.subtract(start).scale(t));
			}
			out.add(new WorldEsp.Line(start.x, start.y, start.z, end.x, end.y, end.z, color, w));
			if (stem.get()) {
				out.add(new WorldEsp.Line(p.x, p.y, p.z, p.x, p.y + entity.getBbHeight(), p.z, color, w));
			}
		}
	}

	private boolean shows(Entity entity) {
		if (exactFilter.get() && !entities.has(Entities.id(entity))) {
			return false;
		}
		if (friend(entity)) return friendsOn.get();
		if (entity instanceof Player) return players.get();
		if (entity instanceof Villager) return villagers.get();
		if (entity instanceof Enemy) return mobs.get();
		if (entity instanceof LivingEntity) return passive.get();
		return other.get();
	}

	private int color(Entity entity, double distance) {
		return switch (colorMode.get()) {
			case "Type" -> typeColor(entity);
			case "Health" -> nz.temple.client.render.Fx.lerp(0xFFFF453A, 0xFF34C759, Entities.healthFraction(entity));
			case "Theme" -> Theme.color(0);
			default -> nz.temple.client.render.Fx.lerp(0xFFFF453A, 0xFF34C759, Math.min(1, distance / 48));
		};
	}

	private int typeColor(Entity entity) {
		double offset;
		int custom;
		if (friend(entity)) { offset = 0.55; custom = friendColor.get(); }
		else if (entity instanceof Player) { offset = 0; custom = playerColor.get(); }
		else if (entity instanceof Villager) { offset = 0.18; custom = villagerColor.get(); }
		else if (entity instanceof Enemy) { offset = 0.82; custom = mobColor.get(); }
		else if (entity instanceof LivingEntity) { offset = 0.35; custom = passiveColor.get(); }
		else { offset = 0.68; custom = otherColor.get(); }
		return themedTypes.get() ? Theme.color(offset) : custom;
	}

	private boolean friend(Entity e) {
		return e instanceof Player p && friends != null && friends.isFriend(p.getName().getString());
	}
}
