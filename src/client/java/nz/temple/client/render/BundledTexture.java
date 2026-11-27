package nz.temple.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import nz.temple.TempleNz;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

public final class BundledTexture {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/texture");

	private final String resource;
	private final Identifier id;
	private boolean attempted;
	private volatile boolean ready;

	public BundledTexture(String key, String resource) {
		this.resource = resource;
		this.id = Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "bundled/" + key);
	}

	public Identifier id() {
		return id;
	}

	public boolean ready() {
		if (!attempted) {
			attempted = true;
			Thread loader = new Thread(this::load, "Temple texture " + id.getPath());
			loader.setDaemon(true);
			loader.start();
		}
		return ready;
	}
	private void load() {
		try (InputStream source = BundledTexture.class.getResourceAsStream(resource)) {
			if (source == null) {
				throw new IllegalStateException("Missing resource " + resource);
			}
			BufferedImage decoded = ImageIO.read(source);
			if (decoded == null) {
				throw new IllegalArgumentException("Unsupported image " + resource);
			}
			int w = decoded.getWidth();
			int h = decoded.getHeight();
			int[] argb = decoded.getRGB(0, 0, w, h, null, 0, w);
			NativeImage image = new NativeImage(w, h, false);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					image.setPixel(x, y, argb[y * w + x]);
				}
			}
			Minecraft.getInstance().execute(() -> {
				Minecraft.getInstance().getTextureManager().register(id,
						new DynamicTexture(() -> "temple-bundled-" + id.getPath(), image));
				ready = true;
			});
		} catch (Exception e) {
			LOGGER.error("Could not load bundled texture {}; using the vanilla fallback", resource, e);
		}
	}
}
