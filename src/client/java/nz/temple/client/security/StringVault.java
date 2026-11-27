package nz.temple.client.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class StringVault {
	// Legacy, unbound key. Used verbatim when the running jar is not signed by the
	// pinned release certificate (dev, smoke, showcase, the offline `protected` jar,
	// or anything lifted into a foreign classloader/jar). In those cases certMix()
	// returns 0 and EFFECTIVE_BASE == LEGACY_BASE, so behaviour is byte-identical to
	// the previous constant-key vault.
	private static final long LEGACY_BASE;
	// Build-time EncryptStringsTask.encode() derives the same value from the signer
	// certificate fingerprint it is given, so a cert-bound release decrypts here only
	// when this jar carries that signature. A patched/unsigned/re-signed/extracted copy
	// derives a different key and every bound constant comes out as garbage.
	private static final long EFFECTIVE_BASE;
	static {
		long a = 0x531D6A2FL;
		long b = 0x4471083CL;
		LEGACY_BASE = (a << 32) ^ (b * 0x9E3779B97F4A7C15L);
		EFFECTIVE_BASE = LEGACY_BASE ^ certMix();
	}

	private StringVault() {
	}
	public static String decrypt(String value) {
		return decrypt(value, 0);
	}
	public static String decrypt(String value, int seed) {
		return decrypt(value, seed, EFFECTIVE_BASE);
	}
	// Package-private, base explicit: lets tests exercise the keystream without a
	// signed jar on disk. Production calls always route through EFFECTIVE_BASE above.
	static String decrypt(String value, int seed, long base) {
		byte[] bytes = Base64.getDecoder().decode(value);
		long state = (base ^ (seed * 0x9E3779B97F4A7C15L)) + 0x165667B19E3779F9L;
		for (int i = 0; i < bytes.length; i++) {
			state ^= state << 13;
			state ^= state >>> 7;
			state ^= state << 17;
			bytes[i] ^= (byte) (state ^ (i * 0x9DL));
		}
		return new String(bytes, StandardCharsets.UTF_8).intern();
	}

	// FNV-1a-64 over the 32-byte SHA-256 of the signer certificate. The build side
	// (EncryptStringsTask.fold) computes this identically from the same bytes, so the
	// two keys agree exactly iff the runtime signer matches the build-time fingerprint.
	static long fold(byte[] sha) {
		long mix = 0xCBF29CE484222325L;
		for (byte x : sha) {
			mix ^= (x & 0xFFL);
			mix *= 0x100000001B3L;
		}
		return mix;
	}

	// Signature of this jar's own StringVault entry, SHA-256'd and folded. Returns 0
	// (the "no binding" sentinel) whenever the jar is not a signed file on disk, the
	// entry is missing, or it carries no valid code signer. Runs once, in <clinit>.
	private static long certMix() {
		try {
			java.security.CodeSource source = StringVault.class.getProtectionDomain().getCodeSource();
			if (source == null || source.getLocation() == null) {
				return 0L;
			}
			java.io.File file = new java.io.File(source.getLocation().toURI());
			if (!file.isFile() || !file.getName().endsWith(".jar")) {
				return 0L;
			}
			try (java.util.jar.JarFile jar = new java.util.jar.JarFile(file, true)) {
				java.util.jar.JarEntry entry = jar.getJarEntry("nz/temple/client/security/StringVault.class");
				if (entry == null) {
					return 0L;
				}
				// Reading the entry to EOF is what makes the JarFile verify its signature
				// and populate the code signers; getCodeSigners() is null before that.
				try (java.io.InputStream in = jar.getInputStream(entry)) {
					byte[] buffer = new byte[8192];
					while (in.read(buffer) != -1) {
					}
				}
				java.security.CodeSigner[] signers = entry.getCodeSigners();
				if (signers == null || signers.length == 0) {
					return 0L;
				}
				java.security.cert.Certificate certificate = signers[0].getSignerCertPath().getCertificates().get(0);
				byte[] sha = java.security.MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
				return fold(sha);
			}
		} catch (Throwable t) {
			return 0L;
		}
	}
}
