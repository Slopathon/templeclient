package nz.temple.client.modules.render;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3x2fStack;
import nz.temple.client.render.Fonts;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Projection;
import nz.temple.client.render.Theme;
import nz.temple.client.render.UiScale;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DamageNumbers extends Module implements nz.temple.client.render.HudSource {
	private static final int MAX = 60;
	private final ModeSetting who;
	private final BoolSetting healing;
	private final NumberSetting size;
	private final NumberSetting lifetime;
	private final NumberSetting rise;
	private final NumberSetting range;
	private final BoolSetting decimals;
	private final ModeSetting colors;
	private final NumberSetting heavyAt;
	private final Map<Integer, Float> health = new HashMap<>();
	private final List<Hit> hits = new ArrayList<>();
	private ClientLevel level;
	private int ticks;
	private record Hit(double x, double y, double z, float amount, boolean heal, long born, double drift) {
	}
	public DamageNumbers() {
		super("Damage Numbers", Category.RENDER, "Shows damage numbers where hits land, sized and coloured by how much damage was dealt.");
		who = add(new ModeSetting("Who", List.of("Everything", "Players", "Targets"), "Everything")
				.describe("Everything shows all nearby damage. Players shows players only. Targets shows only your current target."));
		healing = add(new BoolSetting("Show healing", true).describe("Also shows healing, in green."));
		group();
		size = add(new NumberSetting("Size", 1, 0.4, 3, 0.05, "x").describe("Text size."));
		lifetime = add(new NumberSetting("Lifetime", 1.1, 0.3, 4, 0.1, " s").describe("How long each number stays."));
		rise = add(new NumberSetting("Rise", 0.8, 0, 3, 0.1, " b").describe("How far the number rises before fading."));
		range = add(new NumberSetting("Range", 48, 8, 128, 4, " m").describe("Only shows damage within this distance."));
		decimals = add(new BoolSetting("Decimals", false).describe("Shows half hearts as .5 instead of rounding."));
		group();
		colors = add(new ModeSetting("Color", List.of("Severity", "Theme"), "Severity")
				.describe("Severity goes from white for small hits through gold to red for heavy hits. Theme uses the HUD accent."));
		heavyAt = add(new NumberSetting("Heavy hit at", 7, 2, 20, 0.5, " hp")
				.describe("Damage at or above this counts as a heavy hit, shown larger and redder.")
				.visibleWhen(() -> colors.is("Severity")));
		on(TickEvent.class, e -> tick());
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		if (level != mc.level) {
			level = mc.level;
			health.clear();
			hits.clear();
			return;
		}
		double max = range.get() * range.get();
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity living) || !matches(living, mc) || living.distanceToSqr(mc.player) > max) {
				continue;
			}
			float current = living.getHealth() + living.getAbsorptionAmount();
			Float previous = health.put(living.getId(), current);
			if (previous == null || !living.isAlive() && current <= 0) {
				continue;
			}
			float delta = previous - current;
			if (delta > 0.05f) {
				spawn(living, delta, false);
			} else if (healing.get() && delta < -0.05f) {
				spawn(living, -delta, true);
			}
		}
		if (++ticks % 40 == 0) {
			health.keySet().removeIf(id -> mc.level.getEntity(id) == null);
		}
	}
	private boolean matches(LivingEntity e, Minecraft mc) {
		return switch (who.get()) {
			case "Players" -> e instanceof Player;
			case "Targets" -> e == nz.temple.client.util.CombatTracker.recent(4000);
			default -> true;
		};
	}
	private void spawn(LivingEntity e, float amount, boolean heal) {
		if (hits.size() >= MAX) {
			hits.remove(0);
		}
		hits.add(new Hit(e.getX(), e.getY() + e.getBbHeight() * 0.75, e.getZ(), amount, heal,
				System.nanoTime(), (System.nanoTime() % 1000) / 1000.0 - 0.5));
	}
	public void render(GuiGraphicsExtractor g, DeltaTracker delta, Projection projection) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || mc.player == null || mc.level == null || level != mc.level || !projection.ready()) {
			return;
		}
		long now = System.nanoTime();
		double life = lifetime.get() * 1e9;
		hits.removeIf(h -> now - h.born() > life);
		int gw = g.guiWidth();
		int gh = g.guiHeight();
		var font = mc.font;
		for (Hit h : hits) {
			double t = (now - h.born()) / life;
			double eased = 1 - Math.pow(1 - t, 2);
			double y = h.y() + rise.get() * eased;
			double x = h.x() + h.drift() * 0.5 * eased;
			double z = h.z() + h.drift() * 0.35 * eased;
			float[] p = projection.toScreen(x, y, z, gw, gh);
			if (p == null || p[0] < -40 || p[1] < -30 || p[0] > gw + 40 || p[1] > gh + 30) {
				continue;
			}
			boolean heavy = h.amount() >= heavyAt.get();
			String label = (h.heal() ? "+" : "") + (decimals.get()
					? String.format(Locale.ROOT, "%.1f", h.amount())
					: String.valueOf(Math.round(h.amount())));
			double pop = t < 0.14 ? 1.45 - 0.45 * (t / 0.14) : 1 - Math.max(0, (t - 0.7) / 0.3) * 0.25;
			float alpha = (float) Math.min(1, (1 - t) * 3);
			float scale = UiScale.factor() * (float) (0.5 * size.get() * pop * (heavy ? 1.35 : 1)
					* Math.max(0.5, Math.min(1.4, 12 / Math.max(3, Math.sqrt(mc.player.distanceToSqr(x, y, z))))));
			int color = color(h, heavy);
			int argb = (Math.round(alpha * 255) << 24) | (color & 0xFFFFFF);
			Component text = Fonts.text(label, Fonts.SEMIBOLD);
			float w = font.width(text) * scale;
			Matrix3x2fStack pose = g.pose();
			pose.pushMatrix();
			pose.translate(p[0] - w / 2, p[1]);
			pose.scale(scale, scale);
			g.text(font, text, 0, 0, argb, true);
			pose.popMatrix();
		}
	}
	private int color(Hit h, boolean heavy) {
		if (h.heal()) {
			return 0xFF34C759;
		}
		if (colors.is("Theme")) {
			return Theme.color(h.drift());
		}
		if (heavy) {
			return 0xFFFF453A;
		}
		return Fx.lerp(0xFFFFFFFF, 0xFFFFB340, Math.min(1, h.amount() / Math.max(1, heavyAt.getFloat())));
	}
	@Override
	protected void onDisable() {
		hits.clear();
		health.clear();
	}
}
