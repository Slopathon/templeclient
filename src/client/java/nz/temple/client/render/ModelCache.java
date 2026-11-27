package nz.temple.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import nz.temple.TempleNz;
import nz.temple.cosmetic.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ModelCache {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/models");
	private static final Map<String, Model> MODELS = new HashMap<>();
	private static final Set<String> FAILED = new HashSet<>();
	private ModelCache() {
	}
	public static Model get(String name) {
		Model m = MODELS.get(name);
		if (m != null || FAILED.contains(name)) {
			return m;
		}
		Identifier id = Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "models/" + name + ".tmdl");
		try {
			Optional<net.minecraft.server.packs.resources.Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
			if (res.isEmpty()) {
				throw new java.io.FileNotFoundException(id.toString());
			}
			try (InputStream in = res.get().open()) {
				m = Model.read(in);
			}
			MODELS.put(name, m);
			return m;
		} catch (Exception e) {
			FAILED.add(name);
			LOGGER.warn("Cosmetic model {} could not be loaded: {}", name, e.toString());
			return null;
		}
	}
	public static Model get(String name, boolean far) {
		if (far) {
			Model m = get(name + "_far");
			if (m != null) {
				return m;
			}
		}
		return get(name);
	}
	public static void clear() {
		MODELS.clear();
		FAILED.clear();
	}
}
