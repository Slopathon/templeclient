package nz.temple.config;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

final class ShareCode {
	static final String PREFIX = "TR2";
	private static final String DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
	private static final int FLAG_DEFLATED = 1;
	private ShareCode() {
	}
	static boolean looksLike(String text) {
		return text != null && clean(text).contains(PREFIX);
	}

	static String encode(byte[] payload) {
		byte[] packed = deflate(payload);
		boolean useDeflate = packed.length < payload.length;
		byte[] data = useDeflate ? packed : payload;
		byte[] body = new byte[data.length + 3];
		body[0] = (byte) (useDeflate ? FLAG_DEFLATED : 0);
		System.arraycopy(data, 0, body, 1, data.length);
		int crc = crc16(body, body.length - 2);
		body[body.length - 2] = (byte) (crc >> 8);
		body[body.length - 1] = (byte) crc;
		return PREFIX + toBase62(body);
	}
	static byte[] decode(String text) {
		String cleaned = clean(text);
		int at = cleaned.indexOf(PREFIX);
		if (at < 0) {
			throw new IllegalArgumentException("That isn't a Temple.nz config code.");
		}
		byte[] body = fromBase62(cleaned.substring(at + PREFIX.length()));
		if (body.length < 3 || crc16(body, body.length - 2) != (((body[body.length - 2] & 0xFF) << 8) | (body[body.length - 1] & 0xFF))) {
			throw new IllegalArgumentException("The code is damaged or incomplete. Copy the whole thing again.");
		}
		byte[] data = Arrays.copyOfRange(body, 1, body.length - 2);
		if ((body[0] & FLAG_DEFLATED) == 0) {
			return data;
		}
		try {
			return inflate(data);
		} catch (DataFormatException e) {
			throw new IllegalArgumentException("The code is damaged or incomplete. Copy the whole thing again.");
		}
	}
	private static String clean(String text) {
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c < 128 && Character.isLetterOrDigit(c)) {
				out.append(c);
			}
		}
		return out.toString();
	}
	private static String toBase62(byte[] bytes) {
		byte[] number = new byte[bytes.length + 1];
		number[0] = 1;
		System.arraycopy(bytes, 0, number, 1, bytes.length);
		StringBuilder out = new StringBuilder();
		int start = 0;
		while (start < number.length) {
			int remainder = 0;
			for (int i = start; i < number.length; i++) {
				int acc = (remainder << 8) | (number[i] & 0xFF);
				number[i] = (byte) (acc / 62);
				remainder = acc % 62;
			}
			out.append(DIGITS.charAt(remainder));
			while (start < number.length && number[start] == 0) {
				start++;
			}
		}
		return out.reverse().toString();
	}
	private static byte[] fromBase62(String text) {
		if (text.isEmpty() || text.length() > 20_000) {
			throw new IllegalArgumentException("The code is damaged or incomplete. Copy the whole thing again.");
		}

		byte[] bytes = new byte[text.length()];
		int used = 1;
		for (int i = 0; i < text.length(); i++) {
			int digit = DIGITS.indexOf(text.charAt(i));
			int carry = digit;
			for (int j = 0; j < used; j++) {
				int acc = (bytes[j] & 0xFF) * 62 + carry;
				bytes[j] = (byte) acc;
				carry = acc >>> 8;
			}
			while (carry > 0) {
				bytes[used++] = (byte) carry;
				carry >>>= 8;
			}
		}
		while (used > 1 && bytes[used - 1] == 0) {
			used--;
		}
		if (bytes[used - 1] != 1) {
			throw new IllegalArgumentException("The code is damaged or incomplete. Copy the whole thing again.");
		}
		byte[] out = new byte[used - 1];
		for (int i = 0; i < out.length; i++) {
			out[i] = bytes[used - 2 - i];
		}
		return out;
	}
	private static int crc16(byte[] data, int length) {
		int crc = 0xFFFF;
		for (int i = 0; i < length; i++) {
			crc ^= (data[i] & 0xFF) << 8;
			for (int b = 0; b < 8; b++) {
				crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
				crc &= 0xFFFF;
			}
		}
		return crc;
	}
	static byte[] deflate(byte[] data) {
		Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
		deflater.setInput(data);
		deflater.finish();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] chunk = new byte[1024];
		while (!deflater.finished()) {
			out.write(chunk, 0, deflater.deflate(chunk));
		}
		deflater.end();
		return out.toByteArray();
	}
	static byte[] inflate(byte[] data) throws DataFormatException {
		Inflater inflater = new Inflater(true);
		inflater.setInput(data);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] chunk = new byte[1024];
		try {
			while (!inflater.finished()) {
				int n = inflater.inflate(chunk);
				if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
					throw new DataFormatException("truncated");
				}
				out.write(chunk, 0, n);
				if (out.size() > 4_000_000) {
					throw new DataFormatException("too large");
				}
			}
		} finally {
			inflater.end();
		}
		return out.toByteArray();
	}
}
