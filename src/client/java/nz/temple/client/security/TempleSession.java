package nz.temple.client.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class TempleSession {
	private TempleSession() {
	}
	public static String sessionToken() {
		try {
			String appData = System.getenv("APPDATA");
			if (appData == null) {
				return null;
			}
			Path path = Paths.get(appData, ".temple", "session");
			if (!Files.isReadable(path)) {
				return null;
			}
			String token = Files.readString(path).trim();
			return token.isEmpty() ? null : token;
		} catch (Exception e) {
			return null;
		}
	}
}
