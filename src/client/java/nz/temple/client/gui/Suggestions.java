package nz.temple.client.gui;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import nz.temple.TempleNz;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class Suggestions {
	private static final URI BOT = URI.create("https://verify.temple.nz/api/suggest");
	public static final int MIN_LENGTH = 15;
	public static final int MAX_LENGTH = 1500;
	private static final long COOLDOWN_MS = 60_000;

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private static long lastSent;
	private Suggestions() {
	}
	public static long cooldownSeconds() {
		long left = COOLDOWN_MS - (System.currentTimeMillis() - lastSent);
		return left <= 0 ? 0 : (left + 999) / 1000;
	}
	public static CompletableFuture<String> send(String text) {
		String token = nz.temple.client.security.TempleSession.sessionToken();
		if (token == null) {
			return CompletableFuture.completedFuture("Start Temple from the loader to send suggestions.");
		}
		lastSent = System.currentTimeMillis();
		Minecraft mc = Minecraft.getInstance();
		String player = mc.getUser() == null ? "unknown" : mc.getUser().getName();
		String uuid = mc.getUser() == null || mc.getUser().getProfileId() == null ? "unknown" : mc.getUser().getProfileId().toString();
		JsonObject body = new JsonObject();
		body.addProperty("token", token);
		body.addProperty("text", text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH) : text);
		body.addProperty("player", player);
		body.addProperty("uuid", uuid);
		body.addProperty("version", TempleNz.NAME);
		HttpRequest request = HttpRequest.newBuilder(BOT)
				.timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();
		return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(r -> {
					if (r.statusCode() == 429) {
						return "Too many suggestions right now, try again in a minute.";
					}
					if (r.statusCode() / 100 != 2) {
						return "The server answered " + r.statusCode() + ".";
					}
					return r.body().replace(" ", "").contains("\"ok\":true") ? (String) null
							: "The team could not take it right now. Try again later.";
				})
				.exceptionally(e -> "Could not reach the server. Check your connection.");
	}
}
