package nz.temple.client.cosmetic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import nz.temple.client.TempleNzClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.DoubleConsumer;

public final class ModelCatalog {
	private static final URI CATALOG = URI.create("https://verify.temple.nz/api/models");
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL).build();
	private static final long MAX_BYTES = 80L * 1024 * 1024;
	public record Asset(String url, String sha256) {
	}

	public record Entry(String id, String name, String author, String license, Asset model, Map<String, Asset> animations) {
	}
	private static final String MIRROR = "https://raw.githubusercontent.com/madjin/vrm-samples/master/vroid/";
	private static final List<Entry> STARTING = List.of(
			sample("vroid-shino", "Sendagaya Shino", "VRoid (pixiv)", "CC0", "beta/Sendagaya_Shino.vrm",
					"1e177c1a7b14f783a9c48395831db8616260d3bddd4154cb2784b779adca49b5"));
	private static Entry sample(String id, String name, String author, String license, String path, String sha) {
		return new Entry(id, name, author, license, new Asset(MIRROR + path, sha), Map.of());
	}
	private static volatile List<Entry> entries;
	private ModelCatalog() {
	}

	public static List<Entry> entries() {
		if (entries == null) {
			entries = readCached();
		}
		return entries;
	}
	public static Entry byName(String name) {
		for (Entry e : entries()) {
			if (e.name().equals(name)) {
				return e;
			}
		}
		return null;
	}
	private static Path dir() throws IOException {
		Path d = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("templenz").resolve("models").resolve(".downloads");
		Files.createDirectories(d);
		return d;
	}
	private static List<Entry> readCached() {
		try {
			Path f = dir().resolve("catalog.json");
			if (Files.exists(f)) {
				List<Entry> list = parse(JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject());
				if (!list.isEmpty()) {
					return list;
				}
			}
		} catch (IOException | RuntimeException e) {
			TempleNzClient.LOGGER.warn("Could not read the saved avatar list", e);
		}
		return STARTING;
	}
	public static CompletableFuture<List<Entry>> refresh() {
		HttpRequest request = HttpRequest.newBuilder(CATALOG).timeout(Duration.ofSeconds(10)).GET().build();
		return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(r -> {
			if (r.statusCode() / 100 != 2) {
				return entries();
			}
			JsonObject root = JsonParser.parseString(r.body()).getAsJsonObject();
			List<Entry> list = parse(root);
			if (list.isEmpty()) {
				return entries();
			}
			try {
				Files.writeString(dir().resolve("catalog.json"), r.body(), StandardCharsets.UTF_8);
			} catch (IOException e) {
				TempleNzClient.LOGGER.warn("Could not save the avatar list", e);
			}
			entries = list;
			return list;
		}).exceptionally(e -> entries());
	}
	static List<Entry> parse(JsonObject root) {
		List<Entry> out = new ArrayList<>();
		if (!root.has("models")) {
			return out;
		}
		for (JsonElement el : root.getAsJsonArray("models")) {
			JsonObject o = el.getAsJsonObject();
			Asset model = asset(o);
			if (model == null || !o.has("name") || !o.has("id")) {
				continue;
			}
			Map<String, Asset> anims = new LinkedHashMap<>();
			if (o.has("animations")) {
				JsonArray a = o.getAsJsonArray("animations");
				for (JsonElement ae : a) {
					JsonObject ao = ae.getAsJsonObject();
					Asset as = asset(ao);
					if (as != null && ao.has("state")) {
						anims.put(ao.get("state").getAsString(), as);
					}
				}
			}
			out.add(new Entry(o.get("id").getAsString(), o.get("name").getAsString(), str(o, "author"), str(o, "license"), model, anims));
		}
		return out;
	}
	private static Asset asset(JsonObject o) {
		String url = str(o, "url");
		String sha = str(o, "sha256").toLowerCase(java.util.Locale.ROOT);
		if (!url.startsWith("https://") || !sha.matches("[0-9a-f]{64}")) {
			return null;
		}
		return new Asset(url, sha);
	}
	private static String str(JsonObject o, String key) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
	}

	public static Path file(Asset asset, String extension) throws IOException {
		return dir().resolve(asset.sha256() + extension);
	}

	public static CompletableFuture<Path> fetch(Asset asset, String extension, DoubleConsumer progress) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				Path target = file(asset, extension);
				if (Files.exists(target)) {
					return target;
				}
				HttpRequest request = HttpRequest.newBuilder(URI.create(asset.url())).timeout(Duration.ofMinutes(5)).GET().build();
				HttpResponse<InputStream> r = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
				if (r.statusCode() / 100 != 2) {
					throw new IOException("download answered " + r.statusCode());
				}
				long total = r.headers().firstValueAsLong("Content-Length").orElse(-1);
				if (total > MAX_BYTES) {
					throw new IOException("file is too large");
				}
				Path part = target.resolveSibling(target.getFileName() + ".part");
				MessageDigest sha = MessageDigest.getInstance("SHA-256");
				long got = 0;
				try (InputStream in = r.body(); OutputStream out = Files.newOutputStream(part)) {
					byte[] buf = new byte[1 << 16];
					int n;
					while ((n = in.read(buf)) > 0) {
						got += n;
						if (got > MAX_BYTES) {
							throw new IOException("file is too large");
						}
						sha.update(buf, 0, n);
						out.write(buf, 0, n);
						if (total > 0) {
							progress.accept(Math.min(1, got / (double) total));
						}
					}
				}
				if (!HexFormat.of().formatHex(sha.digest()).equals(asset.sha256())) {
					Files.deleteIfExists(part);
					throw new IOException("download did not match its checksum");
				}
				Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				return target;
			} catch (IOException | NoSuchAlgorithmException e) {
				throw new java.util.concurrent.CompletionException(e);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new java.util.concurrent.CompletionException(e);
			}
		});
	}
}
