package nz.temple.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class ConfigLibrary {
	private static final String BASE = "https://verify.temple.nz/api/configs/";
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	public static final int TITLE_MIN = 3;
	public static final int TITLE_MAX = 48;
	public static final int DESCRIPTION_MAX = 500;
	public static final int COMMENT_MAX = 300;

	private ConfigLibrary() {
	}
	public static CompletableFuture<JsonObject> browse(String query, String sort, int page, boolean mine) {
		JsonObject body = new JsonObject();
		body.addProperty("query", query);
		body.addProperty("sort", sort);
		body.addProperty("page", page);
		body.addProperty("mine", mine);
		return call("browse", body);
	}
	public static CompletableFuture<JsonObject> get(String code, boolean load) {
		JsonObject body = new JsonObject();
		body.addProperty("code", code);
		body.addProperty("load", load);
		return call("get", body);
	}
	public static CompletableFuture<JsonObject> publish(String title, String description, String data, int modules) {
		JsonObject body = new JsonObject();
		body.addProperty("title", title);
		body.addProperty("description", description);
		body.addProperty("data", data);
		body.addProperty("modules", modules);
		return call("publish", body);
	}
	public static CompletableFuture<JsonObject> vote(String code, int value) {
		JsonObject body = new JsonObject();
		body.addProperty("code", code);
		body.addProperty("value", value);
		return call("vote", body);
	}
	public static CompletableFuture<JsonObject> comment(String code, String text) {
		JsonObject body = new JsonObject();
		body.addProperty("code", code);
		body.addProperty("text", text.length() > COMMENT_MAX ? text.substring(0, COMMENT_MAX) : text);
		return call("comment", body);
	}
	public static CompletableFuture<JsonObject> delete(String code) {
		JsonObject body = new JsonObject();
		body.addProperty("code", code);
		return call("delete", body);
	}
	public static CompletableFuture<JsonObject> deleteComment(long id) {
		JsonObject body = new JsonObject();
		body.addProperty("comment", id);
		return call("delete", body);
	}
	public static String reason(JsonObject reply) {
		String status = reply != null && reply.has("status") ? reply.get("status").getAsString() : "";
		return switch (status) {
			case "no_session" -> "Start Temple from the loader to use the config library.";
			case "offline" -> "Could not reach the server. Check your connection.";
			case "slow_down" -> "Slow down a little, then try again.";
			case "title" -> "Give it a title of at least " + TITLE_MIN + " characters.";
			case "data" -> "Your config is too large to publish.";
			case "limit" -> "You have published as many configs as you can. Remove an old one first.";
			case "too_short" -> "Write a little more.";
			case "missing" -> "That config is no longer in the library.";
			case "invalid" -> "Your session ran out. Restart Temple from the loader.";
			default -> "The library could not do that right now. Try again later.";
		};
	}
	private static CompletableFuture<JsonObject> call(String endpoint, JsonObject body) {
		String token = nz.temple.client.security.TempleSession.sessionToken();
		String base = System.getProperty("temple.showcase") != null ? System.getProperty("temple.showcase.configs") : null;
		if (token == null && base != null) {
			token = "showcase";
		}
		if (token == null) {
			return CompletableFuture.completedFuture(failure("no_session"));
		}
		body.addProperty("token", token);
		Minecraft mc = Minecraft.getInstance();
		body.addProperty("author", mc.getUser() == null ? "Player" : mc.getUser().getName());
		HttpRequest request = HttpRequest.newBuilder(URI.create((base != null ? base : BASE) + endpoint))
				.timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();
		return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(r -> {
					try {
						return JsonParser.parseString(r.body()).getAsJsonObject();
					} catch (RuntimeException e) {
						return failure(r.statusCode() == 429 ? "slow_down" : "error");
					}
				})
				.exceptionally(e -> failure("offline"));
	}
	private static JsonObject failure(String status) {
		JsonObject o = new JsonObject();
		o.addProperty("ok", false);
		o.addProperty("status", status);
		return o;
	}
	public static boolean ok(JsonObject reply) {
		return reply != null && reply.has("ok") && reply.get("ok").getAsBoolean();
	}
}
