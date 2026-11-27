package nz.temple.security;

import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class Hkdf {
	private Hkdf() {
	}
	public static byte[] derive(byte[] ikm, byte[] salt, byte[] info, int length) {
		if (length < 1 || length > 255 * 32) {
			throw new IllegalArgumentException("bad HKDF length");
		}
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			byte[] usedSalt = (salt == null || salt.length == 0) ? new byte[32] : salt;
			mac.init(new SecretKeySpec(usedSalt, "HmacSHA256"));
			byte[] prk = mac.doFinal(ikm == null ? new byte[0] : ikm);
			mac.init(new SecretKeySpec(prk, "HmacSHA256"));
			byte[] safeInfo = info == null ? new byte[0] : info;
			byte[] out = new byte[length];
			byte[] block = new byte[0];
			int pos = 0;
			int counter = 1;
			while (pos < length) {
				mac.update(block);
				mac.update(safeInfo);
				mac.update((byte) counter);
				block = mac.doFinal();
				int n = Math.min(block.length, length - pos);
				System.arraycopy(block, 0, out, pos, n);
				pos += n;
				counter++;
			}
			Arrays.fill(prk, (byte) 0);
			return out;
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("HKDF failed", e);
		}
	}
}
