package nz.temple.client.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class GifFrames {
	public record Animation(List<BufferedImage> frames, int[] delays, int totalMillis) {
		public BufferedImage first() {
			return frames.get(0);
		}
		public int frameAt(long millis) {
			if (frames.size() < 2 || totalMillis <= 0) {
				return 0;
			}
			long t = Math.floorMod(millis, (long) totalMillis);
			for (int i = 0; i < delays.length; i++) {
				t -= delays[i];
				if (t < 0) {
					return i;
				}
			}
			return frames.size() - 1;
		}
	}
	private static final int MAX_FRAMES = 256;
	private static final int MAX_SIDE = 4096;
	private GifFrames() {
	}

	public static Animation read(File file) throws Exception {
		byte[] data = Files.readAllBytes(file.toPath());
		if (data.length >= 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F') {
			return decode(data);
		}
		BufferedImage img = ImageIO.read(file);
		if (img == null) {
			throw new IllegalArgumentException("Not a picture");
		}
		return new Animation(List.of(img), new int[] {0}, 0);
	}
	static Animation decode(byte[] data) {
		Reader in = new Reader(data);
		in.skip(6);
		int width = in.u16();
		int height = in.u16();
		int packed = in.u8();
		int background = in.u8();
		in.u8();
		if (width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE) {
			throw new IllegalArgumentException("Bad GIF size");
		}
		int[] global = (packed & 0x80) != 0 ? in.palette(2 << (packed & 7)) : null;
		int[] canvas = new int[width * height];
		List<BufferedImage> frames = new ArrayList<>();
		List<Integer> delays = new ArrayList<>();
		int delay = 10;
		int disposal = 0;
		int transparent = -1;
		while (in.more() && frames.size() < MAX_FRAMES) {
			int block = in.u8();
			if (block == 0x3B) {
				break;
			}
			if (block == 0x21) {
				int label = in.u8();
				if (label == 0xF9) {
					int size = in.u8();
					int flags = in.u8();
					delay = in.u16();
					int index = in.u8();
					in.skip(size - 4);
					disposal = (flags >> 2) & 7;
					transparent = (flags & 1) != 0 ? index : -1;
				}
				in.skipBlocks();
				continue;
			}
			if (block != 0x2C) {
				break;
			}
			int left = in.u16();
			int top = in.u16();
			int w = in.u16();
			int h = in.u16();
			int flags = in.u8();
			int[] palette = (flags & 0x80) != 0 ? in.palette(2 << (flags & 7)) : global;
			boolean interlaced = (flags & 0x40) != 0;
			int minCode = in.u8();
			byte[] indices = lzw(in.blocks(), minCode, w * h);
			int[] before = disposal == 3 ? canvas.clone() : null;
			if (palette != null) {
				for (int y = 0; y < h; y++) {
					int row = interlaced ? interlacedRow(y, h) : y;
					int cy = top + row;
					if (cy < 0 || cy >= height) {
						continue;
					}
					for (int x = 0; x < w; x++) {
						int cx = left + x;
						int index = indices[y * w + x] & 0xFF;
						if (cx < 0 || cx >= width || index == transparent || index >= palette.length) {
							continue;
						}
						canvas[cy * width + cx] = palette[index];
					}
				}
			}
			BufferedImage frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			frame.setRGB(0, 0, width, height, canvas, 0, width);
			frames.add(frame);
			delays.add((delay <= 1 ? 10 : delay) * 10);
			if (disposal == 2) {
				for (int y = Math.max(0, top); y < Math.min(height, top + h); y++) {
					Arrays.fill(canvas, y * width + Math.max(0, left), y * width + Math.min(width, left + w), 0);
				}
			} else if (before != null) {
				canvas = before;
			}
			delay = 10;
			disposal = 0;
			transparent = -1;
		}
		if (frames.isEmpty()) {
			throw new IllegalArgumentException("Empty GIF");
		}
		int[] d = new int[delays.size()];
		int total = 0;
		for (int i = 0; i < d.length; i++) {
			d[i] = delays.get(i);
			total += d[i];
		}
		return new Animation(List.copyOf(frames), d, total);
	}
	private static int interlacedRow(int y, int h) {
		int[] starts = {0, 4, 2, 1};
		int[] steps = {8, 8, 4, 2};
		for (int pass = 0; pass < 4; pass++) {
			int rows = (h - starts[pass] + steps[pass] - 1) / steps[pass];
			if (rows <= 0) {
				continue;
			}
			if (y < rows) {
				return starts[pass] + y * steps[pass];
			}
			y -= rows;
		}
		return y;
	}
	private static byte[] lzw(byte[] data, int minCode, int count) {
		byte[] out = new byte[count];
		if (minCode < 2 || minCode > 8) {
			return out;
		}
		int clear = 1 << minCode;
		int end = clear + 1;
		short[] prefix = new short[4096];
		byte[] suffix = new byte[4096];
		byte[] stack = new byte[4097];
		for (int i = 0; i < clear; i++) {
			suffix[i] = (byte) i;
		}
		int codeSize = minCode + 1;
		int next = clear + 2;
		int old = -1;
		int first = 0;
		int bits = 0;
		int buffer = 0;
		int pos = 0;
		int written = 0;
		while (written < count) {
			while (bits < codeSize) {
				if (pos >= data.length) {
					return out;
				}
				buffer |= (data[pos++] & 0xFF) << bits;
				bits += 8;
			}
			int code = buffer & ((1 << codeSize) - 1);
			buffer >>>= codeSize;
			bits -= codeSize;
			if (code == clear) {
				codeSize = minCode + 1;
				next = clear + 2;
				old = -1;
				continue;
			}
			if (code == end) {
				break;
			}
			if (old == -1) {
				if (code >= clear) {
					return out;
				}
				out[written++] = (byte) code;
				old = code;
				first = code;
				continue;
			}
			int in = code;
			int top = 0;
			if (code >= next) {
				stack[top++] = (byte) first;
				code = old;
			}
			while (code >= clear) {
				if (top >= stack.length - 1 || code >= 4096) {
					return out;
				}
				stack[top++] = suffix[code];
				code = prefix[code];
			}
			first = suffix[code] & 0xFF;
			stack[top++] = (byte) first;
			if (next < 4096) {
				prefix[next] = (short) old;
				suffix[next] = (byte) first;
				next++;
				if (next == (1 << codeSize) && codeSize < 12) {
					codeSize++;
				}
			}
			old = in;
			while (top > 0 && written < count) {
				out[written++] = stack[--top];
			}
		}
		return out;
	}
	private static final class Reader {
		private final byte[] data;
		private int pos;
		Reader(byte[] data) {
			this.data = data;
		}
		boolean more() {
			return pos < data.length;
		}

		int u8() {
			return pos < data.length ? data[pos++] & 0xFF : 0x3B;
		}

		int u16() {
			return u8() | u8() << 8;
		}

		void skip(int n) {
			pos = Math.min(data.length, pos + Math.max(0, n));
		}

		int[] palette(int size) {
			int[] p = new int[size];
			for (int i = 0; i < size; i++) {
				p[i] = 0xFF000000 | u8() << 16 | u8() << 8 | u8();
			}
			return p;
		}

		void skipBlocks() {
			int n;
			while ((n = u8()) != 0 && more()) {
				skip(n);
			}
		}
		byte[] blocks() {
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			int n;
			while ((n = u8()) != 0 && more()) {
				int len = Math.min(n, data.length - pos);
				out.write(data, pos, len);
				pos += len;
			}
			return out.toByteArray();
		}
	}
}
