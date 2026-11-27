package nz.temple.client.modules.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Theme;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.io.InputStream;
import java.util.List;

public final class GlintColor extends Module {
	private static final Identifier ITEM = Identifier.withDefaultNamespace("textures/misc/enchanted_glint_item.png");
	private static final Identifier ARMOR = Identifier.withDefaultNamespace("textures/misc/enchanted_glint_armor.png");
	private static GlintColor instance;
	private final ModeSetting colors;
	private final ColorSetting custom;
	private final NumberSetting brightness;
	private final NumberSetting speed;
	private final NumberSetting strength;
	private final Recolour item = new Recolour(ITEM);
	private final Recolour armor = new Recolour(ARMOR);
	private static final class Recolour {
		final Identifier id;
		float[] shade;
		byte[] level;
		int width;
		int height;
		GlintTexture texture;
		int applied = -1;
		Recolour(Identifier id) {
			this.id = id;
		}
	}
	private static final class GlintTexture extends DynamicTexture {
		GlintTexture(String label, int width, int height) {
			super(label, width, height, true);
			this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
		}
	}
	public GlintColor() {
		super("Glint Color", Category.COSMETICS, "Changes the colour, speed and strength of the enchantment glint on items and armour.");
		colors = add(new ModeSetting("Color", List.of("Theme", "Custom", "Rainbow"), "Theme")
				.describe("Theme uses the HUD accent. Custom uses the colour below. Rainbow cycles through colours."));
		custom = add(new ColorSetting("Custom color", 0xFFE9B949).themed().describe("Glint colour when Color is Custom.")
				.visibleWhen(() -> colors.is("Custom")));
		brightness = add(new NumberSetting("Brightness", 100, 20, 200, 5, "%").describe("How bright the glint is."));
		speed = add(new NumberSetting("Speed", 100, 0, 400, 10, "%").describe("Glint speed. 0 stops it."));
		strength = add(new NumberSetting("Strength", 100, 10, 200, 5, "%")
				.describe("Glint strength, applied on top of Minecraft's Glint Strength accessibility setting."));
		on(TickEvent.class, e -> tick());
		instance = this;
	}
	public static GlintColor active() {
		return instance != null && instance.isEnabled() ? instance : null;
	}
	public double speedFactor() {
		return speed.get() / 100.0;
	}
	public double strengthFactor() {
		return strength.get() / 100.0;
	}
	private int colour() {
		return switch (colors.get()) {
			case "Custom" -> custom.get();
			case "Rainbow" -> Fx.hue(Theme.time() * 0.12);
			default -> Theme.color(0);
		};
	}
	private void tick() {
		if (!isEnabled()) {
			return;
		}
		int target = (colour() & 0xFFFFFF) | (Math.round(brightness.getFloat()) << 24);
		apply(item, target);
		apply(armor, target);
	}
	private void apply(Recolour r, int target) {
		Minecraft mc = Minecraft.getInstance();
		try {
			boolean ours = r.texture != null && mc.getTextureManager().getTexture(r.id) == r.texture;
			if (ours && (r.applied == target || close(r.applied, target))) {
				return;
			}
			if (r.shade == null) {
				try (InputStream in = mc.getResourceManager().open(r.id); NativeImage original = NativeImage.read(in)) {
					r.width = original.getWidth();
					r.height = original.getHeight();
					r.shade = new float[r.width * r.height];
					float max = 1;
					for (int y = 0; y < r.height; y++) {
						for (int x = 0; x < r.width; x++) {
							int p = original.getPixel(x, y);
							int v = Math.max((p >> 16) & 0xFF, Math.max((p >> 8) & 0xFF, p & 0xFF));
							r.shade[y * r.width + x] = v;
							max = Math.max(max, v);
						}
					}
					for (int i = 0; i < r.shade.length; i++) {
						r.shade[i] /= max;
					}
					r.level = new byte[r.shade.length];
					for (int i = 0; i < r.shade.length; i++) {
						r.level[i] = (byte) Math.round(r.shade[i] * 255);
					}
				}
			}
			if (!ours) {
				r.texture = new GlintTexture("temple-glint", r.width, r.height);
				mc.getTextureManager().register(r.id, r.texture);
			}
			float gain = ((target >>> 24) & 0xFF) / 100f;
			int cr = (target >> 16) & 0xFF;
			int cg = (target >> 8) & 0xFF;
			int cb = target & 0xFF;
			int[] lut = new int[256];
			for (int i = 0; i < 256; i++) {
				float k = (float) Math.min(1, Math.pow(i / 255.0, 0.7) * gain * 1.6f);
				float core = Math.max(0, k - 0.7f) / 0.3f * 0.55f;
				int rr = Math.min(255, Math.round((cr + (255 - cr) * core) * k));
				int gg = Math.min(255, Math.round((cg + (255 - cg) * core) * k));
				int bb = Math.min(255, Math.round((cb + (255 - cb) * core) * k));
				lut[i] = 0xFF000000 | (rr << 16) | (gg << 8) | bb;
			}
			NativeImage pixels = r.texture.getPixels();
			for (int y = 0; y < r.height; y++) {
				for (int x = 0; x < r.width; x++) {
					pixels.setPixel(x, y, lut[r.level[y * r.width + x] & 0xFF]);
				}
			}
			r.texture.upload();
			r.applied = target;
		} catch (Exception e) {
			r.applied = target;
		}
	}
	private static boolean close(int a, int b) {
		if (a == -1 || (a >>> 24) != (b >>> 24)) {
			return false;
		}
		for (int shift = 0; shift <= 16; shift += 8) {
			if (Math.abs(((a >> shift) & 0xFF) - ((b >> shift) & 0xFF)) > 2) {
				return false;
			}
		}
		return true;
	}
	@Override
	protected void onDisable() {

		Minecraft mc = Minecraft.getInstance();
		for (Recolour r : new Recolour[] {item, armor}) {
			if (r.texture != null && mc.getTextureManager().getTexture(r.id) == r.texture) {
				mc.getTextureManager().release(r.id);
			}
			r.texture = null;
			r.applied = -1;
		}
	}
}
