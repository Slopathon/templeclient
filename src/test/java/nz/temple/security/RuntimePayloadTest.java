package nz.temple.security;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RuntimePayloadTest {
	private static final String FIXTURE = "nz.temple.security.PayloadFixture";
	private static final String TOKEN = "live-session-token-abc123";
	private static final String AAD = TOKEN + "|request-nonce|1760000000000";
	private static final byte[] KEY = new byte[32];

	static {
		new SecureRandom().nextBytes(KEY);
	}

	private static byte[] payloadJar() throws Exception {
		try (InputStream in = RuntimePayloadTest.class.getResourceAsStream("/nz/temple/security/PayloadFixture.class")) {
			assertNotNull(in, "fixture bytecode must be on the test classpath");
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			in.transferTo(out);
			return RuntimePayload.packClasses(Map.of(FIXTURE, out.toByteArray()));
		}
	}

	@Test
	void decryptsAndRunsAClassFromMemory() throws Exception {
		byte[] encrypted = RuntimePayload.encrypt(payloadJar(), KEY, AAD);

		ClassLoader loader = RuntimePayload.load(encrypted, KEY, AAD, getClass().getClassLoader());
		Class<?> loaded = loader.loadClass(FIXTURE);

		assertSame(loader, loaded.getClassLoader());
		Object instance = loaded.getDeclaredConstructor().newInstance();
		Object result = loaded.getMethod("greet", int.class).invoke(instance, 3);
		assertEquals("payload-alive x3", result);
	}

	@Test
	void wrongResponseKeyCannotDecrypt() throws Exception {
		byte[] encrypted = RuntimePayload.encrypt(payloadJar(), KEY, AAD);
		byte[] wrongKey = KEY.clone();
		wrongKey[0] ^= 1;
		assertThrows(GeneralSecurityException.class,
				() -> RuntimePayload.load(encrypted, wrongKey, AAD, getClass().getClassLoader()));
	}

	@Test
	void replayedPayloadWithAnotherNonceIsRejected() throws Exception {
		byte[] encrypted = RuntimePayload.encrypt(payloadJar(), KEY, AAD);
		assertThrows(GeneralSecurityException.class,
				() -> RuntimePayload.load(encrypted, KEY, TOKEN + "|other-nonce|1760000000000", getClass().getClassLoader()));
	}

	@Test
	void tamperedPayloadIsRejected() throws Exception {
		byte[] encrypted = RuntimePayload.encrypt(payloadJar(), KEY, AAD);
		encrypted[encrypted.length - 1] ^= 0x01;
		assertThrows(GeneralSecurityException.class,
				() -> RuntimePayload.load(encrypted, KEY, AAD, getClass().getClassLoader()));
	}
}
