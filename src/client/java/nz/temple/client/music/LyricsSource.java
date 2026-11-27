package nz.temple.client.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import nz.temple.music.Lyrics;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class LyricsSource {
	public enum State {
		LOADING, FOUND, NONE, UNSYNCED
	}
	public record Result(State state, Lyrics lyrics) {
	}
	private static final String AGENT = "Temple.nz (https://temple.nz)";
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6))
			.followRedirects(HttpClient.Redirect.NORMAL).build();
	private final Map<String, Result> cache = new ConcurrentHashMap<>();
	public Result get(MediaSession.Track track) {
		String key = track.key();
		Result known = cache.get(key);
		if (known != null) {
			return known;
		}
		Result loading = new Result(State.LOADING, null);
		cache.put(key, loading);
		CompletableFuture.supplyAsync(() -> lookup(track)).whenComplete((result, error) ->
				cache.put(key, result != null ? result : new Result(State.NONE, null)));
		return loading;
	}
	private Result lookup(MediaSession.Track t) {
		String title = t.title();
		String artist = t.artist();
		String cleanTitle = Lyrics.cleanTitle(title);
		String cleanArtist = Lyrics.cleanArtist(artist);
		List<String> urls = new ArrayList<>();
		if (!t.album().isEmpty() && t.duration() > 0) {
			urls.add("https://lrclib.net/api/get?track_name=" + enc(title) + "&artist_name=" + enc(artist) + "&album_name="
					+ enc(t.album()) + "&duration=" + Math.round(t.duration()));
		}
		urls.add("https://lrclib.net/api/search?track_name=" + enc(title) + "&artist_name=" + enc(artist));
		if (!cleanTitle.equals(title) || !cleanArtist.equals(artist)) {
			urls.add("https://lrclib.net/api/search?track_name=" + enc(cleanTitle) + "&artist_name=" + enc(cleanArtist));
		}
		urls.add("https://lrclib.net/api/search?q=" + enc(cleanTitle + " " + cleanArtist));
		int dash = title.indexOf(" - ");
		if (dash > 0) {
			String videoArtist = Lyrics.cleanArtist(title.substring(0, dash));
			String videoTitle = Lyrics.cleanTitle(title.substring(dash + 3));
			urls.add("https://lrclib.net/api/search?track_name=" + enc(videoTitle) + "&artist_name=" + enc(videoArtist));
		}
		String plain = null;
		for (String url : urls) {
			JsonElement body = fetch(url);
			if (body == null) {
				continue;
			}
			List<JsonObject> candidates = new ArrayList<>();
			if (body.isJsonObject()) {
				candidates.add(body.getAsJsonObject());
			} else if (body.isJsonArray()) {
				JsonArray array = body.getAsJsonArray();
				for (JsonElement e : array) {
					if (e.isJsonObject()) {
						candidates.add(e.getAsJsonObject());
					}
				}
			}
			JsonObject best = null;
			double bestScore = Double.MAX_VALUE;
			for (JsonObject c : candidates) {
				String synced = string(c, "syncedLyrics");
				if (synced.isEmpty()) {
					if (plain == null && !string(c, "plainLyrics").isEmpty()) {
						plain = string(c, "plainLyrics");
					}
					continue;
				}
				double score = t.duration() > 0 && c.has("duration") ? Math.abs(c.get("duration").getAsDouble() - t.duration()) : 0;
				if (score < bestScore) {
					bestScore = score;
					best = c;
				}
			}
			if (best != null && bestScore < 12) {
				Lyrics lyrics = Lyrics.parse(string(best, "syncedLyrics"), t.duration());
				if (!lyrics.isEmpty()) {
					return new Result(State.FOUND, lyrics);
				}
			}
		}
		return new Result(plain != null ? State.UNSYNCED : State.NONE, null);
	}
	private static JsonElement fetch(String url) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
					.header("User-Agent", AGENT).GET().build();
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() != 200) {
				return null;
			}
			return JsonParser.parseString(response.body());
		} catch (Exception e) {
			return null;
		}
	}
	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static String string(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}
}
