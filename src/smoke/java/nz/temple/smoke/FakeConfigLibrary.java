package nz.temple.smoke;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

final class FakeConfigLibrary {
	private static final String[][] CONFIGS = {
			{"K7Q2M", "Crystal PvP", "Fast crystals and anchors for 2b2t-style anarchy. Offhand totem, surround and auto armor are set up.", "Vex", "34", "41"},
			{"P3WXA", "Legit closet", "Nothing that shows on a screen share: subtle aim assist, reach 3.1, fast place on blocks only.", "lunar", "18", "27"},
			{"HN8R4", "Highway travel", "Elytra fly, auto walk, baritone ready. Built for long trips on the nether highways.", "Ark", "22", "13"},
			{"ZT5CE", "Pretty cosmetics", "Every cosmetic I like: aura, trails, custom swings and the music main menu.", "Bread kitten", "29", "9"},
			{"B2YDF", "Base hunter", "Chunk Finder, new chunks, stash finder and storage ESP tuned for finding bases.", "sxlxs", "16", "4"},
			{"M9GUV", "Minimal HUD", "", "toxin", "6", "-2"}};
	private static HttpServer server;
	private FakeConfigLibrary() {
	}

	static void stop() {
		if (server != null) {
			server.stop(0);
			server = null;
		}
	}
	static String start() throws IOException {
		if (server == null) {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/", exchange -> {
				String endpoint = exchange.getRequestURI().getPath().replaceAll(".*/", "");
				JsonObject body = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
				JsonObject out = reply(endpoint, body);
				byte[] bytes = out.toString().getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, bytes.length);
				try (OutputStream os = exchange.getResponseBody()) {
					os.write(bytes);
				}
			});
			server.start();
		}
		return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
	}
	private static JsonObject reply(String endpoint, JsonObject body) {
		JsonObject out = new JsonObject();
		out.addProperty("ok", true);
		switch (endpoint) {
			case "browse" -> {
				JsonArray list = new JsonArray();
				java.util.List<JsonObject> rows = new java.util.ArrayList<>();
				for (int i = 0; i < CONFIGS.length; i++) {
					rows.add(summary(i));
				}
				String sort = body.has("sort") ? body.get("sort").getAsString() : "top";
				java.util.Comparator<JsonObject> by = switch (sort) {
					case "new" -> java.util.Comparator.comparingLong(o -> -o.get("created").getAsLong());
					case "old" -> java.util.Comparator.comparingLong(o -> o.get("created").getAsLong());
					case "downloads" -> java.util.Comparator.comparingInt(o -> -o.get("downloads").getAsInt());
					case "comments" -> java.util.Comparator.comparingInt(o -> -o.get("comments").getAsInt());
					default -> java.util.Comparator.comparingInt(o -> -o.get("score").getAsInt());
				};
				rows.sort(by);
				rows.forEach(list::add);
				out.add("configs", list);
				out.addProperty("more", false);
				out.addProperty("page", 0);
			}
			case "get" -> {
				String code = body.get("code").getAsString();
				for (int i = 0; i < CONFIGS.length; i++) {
					if (CONFIGS[i][0].equals(code)) {
						JsonObject c = summary(i);
						c.addProperty("data", "");
						JsonArray comments = new JsonArray();
						comments.add(comment(1, "Jadenmann", "Works great on 2b, the anchor timing is perfect.", 3600));
						comments.add(comment(2, "4uture", "Turned reach down a bit and it is my main now.", 86400 * 2));
						comments.add(comment(3, "Vanix", "Could you add a version with the karambit swing?", 86400 * 5));
						c.add("comment_list", comments);
						out.add("config", c);
					}
				}
			}
			case "publish" -> out.addProperty("code", "R4ND7");
			case "vote" -> {
				out.addProperty("score", 42);
				out.addProperty("vote", body.get("value").getAsInt());
			}
			default -> out.addProperty("ok", false);
		}
		return out;
	}
	private static JsonObject summary(int i) {
		String[] c = CONFIGS[i];
		JsonObject o = new JsonObject();
		o.addProperty("code", c[0]);
		o.addProperty("title", c[1]);
		o.addProperty("description", c[2]);
		o.addProperty("author", c[3]);
		o.addProperty("modules", Integer.parseInt(c[4]));
		o.addProperty("score", Integer.parseInt(c[5]));
		o.addProperty("downloads", Math.abs(Integer.parseInt(c[5])) * 7 + 3);
		o.addProperty("comments", 3);
		o.addProperty("created", System.currentTimeMillis() / 1000 - 86400L * (i + 1));
		o.addProperty("vote", i == 0 ? 1 : 0);
		o.addProperty("mine", i == 3);
		return o;
	}
	private static JsonObject comment(int id, String author, String text, long age) {
		JsonObject o = new JsonObject();
		o.addProperty("id", id);
		o.addProperty("author", author);
		o.addProperty("text", text);
		o.addProperty("created", System.currentTimeMillis() / 1000 - age);
		o.addProperty("mine", id == 2);
		return o;
	}
}
