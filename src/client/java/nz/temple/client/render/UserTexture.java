package nz.temple.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import nz.temple.TempleNz;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

public final class UserTexture {
	private final String key;
	private final Supplier<Path> source;
	private final Identifier id;
	private Path loadedPath;
	private boolean loaded;
	private boolean failed;
	private long stamp;
	private long lastCheck;
	private int width;
	private int height;
	public UserTexture(String key, Supplier<Path> source) {
		this.key = key;
		this.source = source;
		this.id = Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "user/" + key);
	}
	public Identifier id() {
		return id;
	}
	public int width() {
		return width;
	}
	public int height() {
		return height;
	}
	public boolean ready() {
		Path path;
		try {
			path = source.get();
		} catch (RuntimeException e) {
			path = null;
		}
		if (path == null) {
			return false;
		}
		long now = System.currentTimeMillis();
		boolean samePath = path.equals(loadedPath);
		if (samePath && now - lastCheck < 500) {
			return loaded;
		}
		lastCheck = now;
		long current;
		try {
			current = Files.isRegularFile(path) ? Files.getLastModifiedTime(path).toMillis() : 0;
		} catch (Exception e) {
			return loaded && samePath;
		}
		if (current == 0) {
			loaded = false;
			loadedPath = path;
			return false;
		}
		if (samePath && current == stamp) {
			return loaded;
		}
		stamp = current;
		loadedPath = path;
		try {
			NativeImage image = read(path);
			width = image.getWidth();
			height = image.getHeight();
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "temple-" + key, image));
			loaded = true;
			failed = false;
		} catch (Exception e) {
			loaded = false;
			failed = true;
		}
		return loaded;
	}
	public boolean broken() {
		return failed;
	}
	private static NativeImage read(Path path) throws Exception {
		byte[] bytes = Files.readAllBytes(path);
		boolean png = bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
		if (!png) {
			BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
			if (decoded == null) {
				throw new IllegalArgumentException("Unsupported image: " + path);
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(decoded, "png", out);
			bytes = out.toByteArray();
		}
		try (InputStream in = new ByteArrayInputStream(bytes)) {
			return NativeImage.read(in);
		}
	}
}
