package nz.temple.client.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class StringVaultTest {
	// Must equal StringVault.LEGACY_BASE exactly. Replicated here because that field is
	// private; if the vault's constant ever changes, this test fails and flags it.
	private static final long LEGACY_BASE = (0x531D6A2FL << 32) ^ (0x4471083CL * 0x9E3779B97F4A7C15L);

	// Independent re-implementation of the build-time encoder, so we can prove the vault
	// inverts whatever the EncryptStringsTask keystream produced for a given base.
	private static String encode(String plaintext, int seed, long base) {
		byte[] bytes = plaintext.getBytes(StandardCharsets.UTF_8);
		long state = (base ^ (seed * 0x9E3779B97F4A7C15L)) + 0x165667B19E3779F9L;
		for (int i = 0; i < bytes.length; i++) {
			state ^= state << 13;
			state ^= state >>> 7;
			state ^= state << 17;
			bytes[i] ^= (byte) (state ^ (i * 0x9DL));
		}
		return Base64.getEncoder().encodeToString(bytes);
	}

	@Test
	void decryptInvertsEncodeForTheSameBase() {
		long base = 0x0123456789ABCDEFL;
		for (int seed : new int[] {0, 1, -7, 0x5EED}) {
			String plain = "combat.aimbot.fov=" + seed + " /slot é☃";
			assertEquals(plain, StringVault.decrypt(encode(plain, seed, base), seed, base));
		}
	}

	@Test
	void wrongBaseDoesNotRecoverPlaintext() {
		long buildBase = LEGACY_BASE ^ 0xE6CB594C1A148AC5L; // a "signed" key
		String plain = "https://verify.temple.nz/api/session";
		String enc = encode(plain, 42, buildBase);
		// Same ciphertext, unbound runtime key (lifted/unsigned jar) -> garbage, not the secret.
		assertNotEquals(plain, StringVault.decrypt(enc, 42, LEGACY_BASE));
	}

	@Test
	void publicApiFallsBackToLegacyBaseWhenUnsigned() {
		// Loaded from a build directory (not a signed jar) in tests, so certMix()==0 and
		// EFFECTIVE_BASE==LEGACY_BASE. This guards the "dev/smoke/offline jar unchanged" path.
		String plain = "AutoTotem toggled";
		int seed = 0x1234;
		assertEquals(plain, StringVault.decrypt(encode(plain, seed, LEGACY_BASE), seed));
	}

	@Test
	void foldMatchesTheSharedVector() {
		byte[] sha = new byte[32];
		for (int i = 0; i < 32; i++) {
			sha[i] = (byte) i;
		}
		// Same literal is asserted on the build side (EncryptStringsKeyTest), proving the
		// two fold() implementations agree byte-for-byte.
		assertEquals(0xE6CB594C1A148AC5L, StringVault.fold(sha));
	}
}
