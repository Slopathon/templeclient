package nz.temple.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

class HkdfTest {
	@Test
	void matchesTheBotHkdfVector() {
		byte[] ikm = "temple-dev-secret-0123456789abcd".getBytes(StandardCharsets.UTF_8);
		byte[] salt = new byte[32];
		for (int i = 0; i < 32; i++) {
			salt[i] = (byte) i;
		}
		byte[] info = "temple-payload-v2|TOK|NON|123|nz.temple.X".getBytes(StandardCharsets.UTF_8);
		byte[] k = Hkdf.derive(ikm, salt, info, 32);
		assertEquals("f07330316d6dc973ec0f86e00106de6a54a0dcfa2a5c71fbad1b955f20a53e7a",
				HexFormat.of().formatHex(k));
	}
	@Test
	void derivedKeyDecryptsWhatTheSameKeyEncrypts() throws GeneralSecurityException {
		byte[] secret = "another-shared-secret-value-01234".getBytes(StandardCharsets.UTF_8);
		byte[] salt = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
		byte[] info = "temple-payload-v2|tok|non|999|nz.temple.Y".getBytes(StandardCharsets.UTF_8);
		byte[] key = Hkdf.derive(secret, salt, info, 32);
		byte[] plain = "the lazy module bytes".getBytes(StandardCharsets.UTF_8);
		String aad = "tok|non|999|nz.temple.Y";
		byte[] blob = RuntimePayload.encrypt(plain, key, aad);
		byte[] key2 = Hkdf.derive(secret, salt, info, 32);
		byte[] round = RuntimePayload.decrypt(blob, key2, aad);
		assertArrayEquals(plain, round);
	}
}
