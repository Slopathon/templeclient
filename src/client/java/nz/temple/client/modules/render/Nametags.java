package nz.temple.client.modules.render;

import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;
import nz.temple.client.util.VisualRange;
import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.client.HudModule;
import nz.temple.client.render.Draw2D;
import nz.temple.client.render.Fonts;
import nz.temple.client.render.Fx;
import nz.temple.client.render.GlassRenderer;
import nz.temple.client.render.Projection;
import nz.temple.client.render.Theme;
import nz.temple.client.render.UiScale;
import nz.temple.client.util.CombatTracker;
import nz.temple.client.util.EntityCatalog;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.EntitySelection;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Nametags extends Module {
	private final EntitySelection entities;
	private final NumberSetting range;
	private final BoolSetting self;
	private final ModeSetting style;
	private final BoolSetting healthBar;
	private final BoolSetting healthText;
	private final BoolSetting distance;
	private final BoolSetting ping;
	private final BoolSetting totems;
	private final BoolSetting highlightTarget;
	private final BoolSetting armor;
	private final BoolSetting hands;
	private final BoolSetting hotbar;
	private final BoolSetting durability;
	private final BoolSetting enchants;
	private final ModeSetting scaleMode;
	private final NumberSetting scale;
	private final NumberSetting opacity;
	private final Map<Integer, float[]> state = new HashMap<>();
	private float fade = 1;
	private GuiGraphicsExtractor gfx;
	private long lastNanos = System.nanoTime();
	public Nametags() {
		super("Nametags", Category.RENDER, "Shows name, health, distance and gear on a card above each entity.");
		List<String> ids = new ArrayList<>(EntityCatalog.idsInCategory("monster"));
		ids.add("minecraft:player");
		entities = add(new EntitySelection("Entities", ids)
				.describe("Choose which entity types get a nametag."));
		range = add(new NumberSetting("Range", 64, 8, 512, 8, " m")
				.describe("Only shows nametags within this distance."));
		self = add(new BoolSetting("Self", false)
				.describe("Shows your own nametag in third person or Freecam."));
		group();
		style = add(new ModeSetting("Style", List.of("Glass", "Solid", "Minimal"), "Glass")
				.describe("Glass uses the client's glass style. Solid uses a dark rounded card. Minimal shows text and bars only."));
		opacity = add(new NumberSetting("Background", 70, 0, 100, 5, "%")
				.describe("How solid the card background is.")
				.visibleWhen(() -> !style.is("Minimal")));
		scaleMode = add(new ModeSetting("Scaling", List.of("Fixed", "Distance"), "Distance")
				.describe("Fixed keeps tags the same size at any distance. Distance shrinks tags further away."));
		scale = add(new NumberSetting("Scale", 1, 0.5, 2.5, 0.1, "x").describe("Tag size."));
		group();
		healthBar = add(new BoolSetting("Health bar", true)
				.describe("A health bar that animates to the new value, with a trailing bar showing recent damage."));
		healthText = add(new BoolSetting("Health number", true).describe("Shows exact health next to the name."));
		distance = add(new BoolSetting("Distance", true).describe("Distance to the entity."));
		ping = add(new BoolSetting("Ping", true).describe("Player ping, shown as a coloured dot and number."));
		totems = add(new BoolSetting("Totem pops", true)
				.describe("Totems this player has used. Requires Totem Pops to be on."));
		highlightTarget = add(new BoolSetting("Ring target", true).describe("An accent ring around your current target."));
		group();
		armor = add(new BoolSetting("Armor", true).describe("Shows their armour above the card."));
		hands = add(new BoolSetting("Held items", true).describe("Shows the items in each hand."));
		durability = add(new BoolSetting("Durability", true)
				.describe("A durability bar under each damaged item, green to red.")
				.visibleWhen(() -> armor.get() || hands.get()));
		enchants = add(new BoolSetting("Enchantments", true)
				.describe("Short enchantment names above each armour piece and held item, such as Prot IV and Unbr III.")
				.visibleWhen(() -> armor.get() || hands.get()));
		hotbar = add(new BoolSetting("Hotbar", false)
				.describe("Shows your full hotbar on your own tag. Servers only share other players' held items, not their hotbars.")
				.visibleWhen(self::get));
	}
	public void render(GuiGraphicsExtractor g, DeltaTracker delta, Projection projection) {
		if (!isEnabled() || !projection.ready()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return;
		}
		gfx = g;
		long now = System.nanoTime();
		float dt = (float) Math.min((now - lastNanos) / 1e9, 0.2);
		lastNanos = now;
		float partial = delta.getGameTimeDeltaPartialTick(false);
		int gw = g.guiWidth();
		int gh = g.guiHeight();
		float uiScale = UiScale.factor();
		double maxSq = VisualRange.of(range.get()) * VisualRange.of(range.get());
		LivingEntity target = combatTarget();
		boolean seeSelf = self.get() && (mc.options.getCameraType() != CameraType.FIRST_PERSON || mc.getCameraEntity() != player);
		Entity viewer = mc.getCameraEntity() != null ? mc.getCameraEntity() : player;
		List<Entity> list = new ArrayList<>();
		for (Entity entity : mc.level.entitiesForRendering()) {
			boolean mine = entity == player;
			if (mine ? seeSelf : entity.isAlive() && entities.has(nz.temple.client.util.Entities.id(entity))
					&& entity.distanceToSqr(player) <= maxSq) {
				list.add(entity);
			}
		}
		list.sort(Comparator.comparingDouble(e -> -e.distanceToSqr(viewer)));
		Set<Integer> shown = new HashSet<>();
		int drawn = 0;
		for (Entity entity : list) {
			shown.add(entity.getId());
			float[] st = state.computeIfAbsent(entity.getId(), k -> new float[] {-1, -1, 0});
			st[2] = Math.min(1, st[2] + dt * 4.5f);
			fade = 1 - (1 - st[2]) * (1 - st[2]) * (1 - st[2]);
			Vec3 at = entity.getPosition(partial);
			float[] p = projection.toScreen(at.x, at.y + entity.getBbHeight() + 0.45, at.z, gw, gh);
			if (p == null || p[0] < -120 || p[1] < -80 || p[0] > gw + 120 || p[1] > gh + 80) {
				continue;
			}
			if (drawn++ % 4 == 0) {
				g.nextStratum();
			}
			double dist = Math.sqrt(entity.distanceToSqr(viewer));
			float s = scale.getFloat() * 0.5f * uiScale;
			if (scaleMode.is("Distance")) {
				s *= (float) Math.max(0.45, Math.min(1.35, 6 / Math.max(2, dist)));
			}
			s *= 0.9f + 0.1f * fade;
			draw(g, entity, entity == player, p[0], p[1] + (1 - fade) * 6 * uiScale, s, dist,
					entity == target && highlightTarget.get(), st, dt);
		}
		state.keySet().retainAll(shown);
		fade = 1;
	}
	private LivingEntity combatTarget() {
		LivingEntity target = nz.temple.client.util.CombatTarget.current();
		return target != null ? target : CombatTracker.recent(4000);
	}
	private void draw(GuiGraphicsExtractor g, Entity entity, boolean mine, float cx, float bottom, float s, double dist,
			boolean ringed, float[] st, float dt) {
		var font = Minecraft.getInstance().font;
		LivingEntity living = entity instanceof LivingEntity l ? l : null;
		float health = living != null ? living.getHealth() + living.getAbsorptionAmount() : 0;
		float max = living != null ? Math.max(1, living.getMaxHealth()) : 1;
		float frac = Math.max(0, Math.min(1, health / max));
		if (st[0] < 0) {
			st[0] = frac;
			st[1] = frac;
		}
		st[0] += (frac - st[0]) * (1 - (float) Math.exp(-12 * dt));
		st[1] = st[1] < st[0] ? st[0] : Math.max(st[0], st[1] - dt * 0.4f);
		String name = entity.getName().getString();
		String hp = healthText.get() && living != null ? String.format(Locale.ROOT, "%.0f", health) : "";
		int popped = totems.get() ? nz.temple.client.util.TotemTracker.count(entity.getId()) : 0;
		int latency = -1;
		if (ping.get() && entity instanceof Player p && Minecraft.getInstance().getConnection() != null) {
			PlayerInfo info = Minecraft.getInstance().getConnection().getPlayerInfo(p.getUUID());
			latency = info == null ? -1 : info.getLatency();
		}
		String meta = distance.get() && !mine ? String.format(Locale.ROOT, "%.0fm", dist) : "";
		if (latency >= 0) {
			meta += (meta.isEmpty() ? "" : "   ") + latency + "ms";
		}
		Component nameText = Fonts.text(name, Fonts.SEMIBOLD);
		Component hpText = Fonts.text(hp, Fonts.SEMIBOLD);
		Component metaText = Fonts.text(meta, Fonts.MEDIUM);
		Component popText = Fonts.text("×" + popped, Fonts.SEMIBOLD);
		float nameW = font.width(nameText) * s;
		float hpW = hp.isEmpty() ? 0 : font.width(hpText) * s + 5 * s;
		float popW = popped > 0 ? font.width(popText) * s * 0.85f + 9 * s : 0;
		float metaS = s * 0.8f;
		float metaW = meta.isEmpty() ? 0 : font.width(metaText) * metaS + (latency >= 0 ? 6 * s : 0);
		float pad = 6 * s;
		float lineH = 9 * s;
		float barH = healthBar.get() && living != null ? 3 * s : 0;
		float w = Math.max(nameW + hpW + popW, metaW) + pad * 2;
		float h = pad * 0.8f + lineH + (barH > 0 ? barH + 3 * s : 0) + (meta.isEmpty() ? 0 : lineH * 0.8f + 1 * s) + pad * 0.6f;
		float x0 = cx - w / 2;
		float y0 = bottom - h;
		float radius = Math.min(7 * s, h / 2);
		background(g, x0, y0, w, h, radius, ringed);
		float ty = y0 + pad * 0.8f;
		float rowW = nameW + hpW + popW;
		float tx = cx - rowW / 2;
		text(g, nameText, tx, ty, s, 0xFFFFFFFF);
		tx += nameW;
		if (!hp.isEmpty()) {
			text(g, hpText, tx + 5 * s, ty, s, Fx.lerp(0xFFFF6B6B, 0xFF7CE38B, frac));
			tx += hpW;
		}
		if (popped > 0) {
			float chipW = popW - 4 * s;
			roundRect(tx + 4 * s, ty - 1 * s, chipW, lineH + 1 * s, (lineH + 1 * s) / 2, faded((0x40 << 24) | (HudModule.GOLD & 0xFFFFFF)));
			text(g, popText, tx + 4 * s + (chipW - font.width(popText) * s * 0.85f) / 2, ty + 0.8f * s, s * 0.85f, 0xFFFFE3A1);
		}
		ty += lineH;
		if (barH > 0) {
			ty += 2 * s;
			float bx = x0 + pad;
			float bw = w - pad * 2;
			roundRect(bx, ty, bw, barH, barH / 2, faded(0x66000000));
			roundRect(bx, ty, bw * st[1], barH, barH / 2, faded(0x70FFFFFF));
			roundRect(bx, ty, Math.max(barH, bw * st[0]), barH, barH / 2, faded(Fx.lerp(0xFFFF453A, 0xFF34C759, st[0])));
			ty += barH + 1 * s;
		}
		if (!meta.isEmpty()) {
			ty += 1 * s;
			float mx = cx - metaW / 2;
			if (latency >= 0) {
				int dot = latency < 80 ? 0xFF5EE07A : latency < 180 ? 0xFFE9B949 : 0xFFFF6B6B;
				float d = 3.2f * s;
				roundRect(mx, ty + (lineH * 0.8f - d) / 2 - 0.5f * s, d, d, d / 2, faded(dot));
				mx += 6 * s;
			}
			text(g, metaText, mx, ty, metaS, 0xB8FFFFFF);
		}
		if (living != null && fade > 0.35f) {
			items(g, living, mine, cx, y0 - 2 * s, s);
		}
	}
	private void background(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, boolean ringed) {
		float a = opacity.getFloat() / 100f;
		GlassRenderer glass = TempleNzClient.glass();
		boolean shapes = glass != null && glass.available() && glass.hasRoom(0, 2);
		if (style.is("Glass") && shapes && glass.hasRoom(1, 2)) {
			glass.glass(x, y, w, h, r, fade);
			glass.rect(x, y, w, h, r, faded(Math.round(a * 0x70) << 24 | 0x0A0806), 0, 0, null);
		} else if (!style.is("Minimal")) {
			roundRect(x, y, w, h, r, faded(Math.round(a * 0xD0) << 24 | 0x0C0A0E));
		}
		if (ringed) {
			float pulse = (float) (0.75 + 0.25 * Math.sin(Theme.time() * 4));
			int accent = Theme.color(0) & 0xFFFFFF;
			if (shapes) {
				glass.rect(x - 1, y - 1, w + 2, h + 2, r + 1, faded(Math.round(0xFF * pulse) << 24 | accent), 1.2f, 0, null);
				glass.rect(x - 3, y - 3, w + 6, h + 6, r + 3, faded(Math.round(0x40 * pulse) << 24 | accent), 0, 4, null);
			} else {
				Draw2D.pixelOutline(g, x, y, x + w, y + h, 1, faded(0xFF000000 | accent));
			}
		}
	}
	private void items(GuiGraphicsExtractor g, LivingEntity living, boolean mine, float cx, float bottom, float s) {
		List<ItemStack> row = new ArrayList<>();
		if (hands.get()) {
			row.add(living.getItemBySlot(EquipmentSlot.MAINHAND));
		}
		if (armor.get()) {
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				row.add(living.getItemBySlot(slot));
			}
		}
		if (hands.get()) {
			row.add(living.getItemBySlot(EquipmentSlot.OFFHAND));
		}
		row.removeIf(ItemStack::isEmpty);
		float icon = 16 * s * 0.8f;
		float gap = 2 * s;
		float y = bottom - icon - (durability.get() ? 3 * s : 0);
		if (!row.isEmpty()) {
			slots(g, row, -1, cx, y, icon, gap, s, false);
		}
		if (mine && hotbar.get() && living instanceof Player p) {
			List<ItemStack> bar = new ArrayList<>();
			for (int i = 0; i < 9; i++) {
				bar.add(p.getInventory().getItem(i));
			}
			float by = (row.isEmpty() ? y : y - icon - gap * 3) - 2 * s;
			slots(g, bar, p.getInventory().getSelectedSlot(), cx, by, icon, gap, s, true);
		}
	}
	private void slots(GuiGraphicsExtractor g, List<ItemStack> stacks, int selected, float cx, float y, float icon, float gap,
			float s, boolean wells) {
		var font = Minecraft.getInstance().font;
		float total = stacks.size() * icon + (stacks.size() - 1) * gap;
		float x = cx - total / 2;
		if (wells) {
			float pad = 2.5f * s;
			roundRect(x - pad, y - pad, total + pad * 2, icon + pad * 2, 5 * s, faded(0x8C0A0806));
		}
		Matrix3x2fStack pose = g.pose();
		for (int i = 0; i < stacks.size(); i++) {
			ItemStack stack = stacks.get(i);
			if (i == selected) {
				roundRect(x - 1.5f * s, y - 1.5f * s, icon + 3 * s, icon + 3 * s, 3.5f * s,
						faded((0x55 << 24) | (HudModule.GOLD & 0xFFFFFF)));
			}
			if (!stack.isEmpty()) {
				pose.pushMatrix();
				pose.translate(x, y);
				pose.scale(icon / 16f, icon / 16f);
				g.item(stack, 0, 0);
				if (stack.getCount() > 1) {
					g.itemDecorations(font, stack, 0, 0);
				}
				pose.popMatrix();
				if (enchants.get() && !wells) {
					List<String> lines = enchantLines(stack);
					float ts = 0.42f * s;
					float lh = font.lineHeight * ts;
					for (int l = 0; l < lines.size(); l++) {
						String line = lines.get(l);
						float tw = font.width(line) * ts;
						text(g, Component.literal(line), x + (icon - tw) / 2, y - (lines.size() - l) * lh - 1 * s, ts, 0xFFE6E6E6);
					}
				}
				if (durability.get() && stack.isDamageableItem() && stack.isDamaged()) {
					float left = 1 - stack.getDamageValue() / (float) Math.max(1, stack.getMaxDamage());
					float bh = 1.6f * s;
					float by = y + icon + 1.2f * s;
					roundRect(x + 1 * s, by, icon - 2 * s, bh, bh / 2, faded(0x80000000));
					roundRect(x + 1 * s, by, Math.max(bh, (icon - 2 * s) * left), bh, bh / 2,
							faded(Fx.lerp(0xFFFF453A, 0xFF34C759, left)));
				}
			}
			x += icon + gap;
		}
	}
	private static final java.util.Map<String, String> SHORT = java.util.Map.ofEntries(java.util.Map.entry("protection", "Prot"),
			java.util.Map.entry("unbreaking", "Unbr"), java.util.Map.entry("mending", "Mend"), java.util.Map.entry("sharpness", "Sharp"),
			java.util.Map.entry("fire_aspect", "FA"), java.util.Map.entry("blast_protection", "BProt"), java.util.Map.entry("fire_protection", "FP"),
			java.util.Map.entry("projectile_protection", "PP"), java.util.Map.entry("feather_falling", "FF"),
			java.util.Map.entry("depth_strider", "DS"), java.util.Map.entry("aqua_affinity", "AA"), java.util.Map.entry("respiration", "Resp"),
			java.util.Map.entry("thorns", "Thorn"), java.util.Map.entry("sweeping_edge", "Sweep"), java.util.Map.entry("looting", "Loot"),
			java.util.Map.entry("knockback", "KB"), java.util.Map.entry("efficiency", "Eff"), java.util.Map.entry("fortune", "Fort"),
			java.util.Map.entry("silk_touch", "Silk"), java.util.Map.entry("power", "Pow"), java.util.Map.entry("infinity", "Inf"),
			java.util.Map.entry("density", "Dens"), java.util.Map.entry("breach", "Brch"), java.util.Map.entry("wind_burst", "Wind"),
			java.util.Map.entry("swift_sneak", "SS"), java.util.Map.entry("soul_speed", "Soul"), java.util.Map.entry("frost_walker", "FW"),
			java.util.Map.entry("binding_curse", "Bind"), java.util.Map.entry("vanishing_curse", "Van"));
	private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

	/** Short enchantment names with levels ("Prot IV"), at most four. */
	private static List<String> enchantLines(ItemStack stack) {
		List<String> out = new ArrayList<>();
		for (var entry : stack.getEnchantments().entrySet()) {
			String id = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
			String name = SHORT.getOrDefault(id, id.isEmpty() ? "?" : Character.toUpperCase(id.charAt(0)) + id.substring(1, Math.min(4, id.length())));
			int level = entry.getIntValue();
			out.add(level > 1 || stack.getEnchantments().size() > 0 && level > 0 && !id.equals("mending") && !id.equals("silk_touch")
					&& !id.equals("infinity") && !id.equals("aqua_affinity") ? name + " " + (level < ROMAN.length ? ROMAN[level] : level) : name);
			if (out.size() == 4) {
				break;
			}
		}
		return out;
	}

	private void roundRect(float x, float y, float w, float h, float r, int argb) {
		GlassRenderer glass = TempleNzClient.glass();
		if (glass != null && glass.available() && glass.hasRoom(0, 1)) {
			glass.rect(x, y, w, h, r, argb, 0, 0, null);
		} else if (gfx != null) {
			Draw2D.pixelRect(gfx, x, y, x + w, y + h, argb);
		}
	}
	private void text(GuiGraphicsExtractor g, Component c, float x, float y, float s, int color) {
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(s, s);
		g.text(Minecraft.getInstance().font, c, 0, 0, faded(color), true);
		pose.popMatrix();
	}
	private int faded(int argb) {
		return (Math.round(((argb >>> 24) & 0xFF) * fade) << 24) | (argb & 0xFFFFFF);
	}
}
