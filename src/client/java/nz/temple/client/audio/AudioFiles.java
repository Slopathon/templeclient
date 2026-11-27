package nz.temple.client.audio;

import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCStdlib;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class AudioFiles {
	public static final double MAX_SECONDS = 30;
	private static final long MAX_BYTES = 64L << 20;
	private static Path cachedPath;
	private static long cachedStamp;
	private static Pcm cached;
	private AudioFiles() {
	}

	public static synchronized Pcm load(Path path) throws IOException {
		long stamp = Files.getLastModifiedTime(path).toMillis() ^ Files.size(path);
		if (path.equals(cachedPath) && stamp == cachedStamp && cached != null) {
			return cached;
		}
		if (Files.size(path) > MAX_BYTES) {
			throw new IOException("That file is over 64 MB");
		}
		String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
		Pcm pcm;
		if (name.endsWith(".mp3")) {
			pcm = mp3(path);
		} else if (name.endsWith(".ogg")) {
			pcm = ogg(path);
		} else {
			pcm = sampled(path);
		}
		cachedPath = path;
		cachedStamp = stamp;
		cached = pcm;
		return pcm;
	}
	private static Pcm mp3(Path path) throws IOException {
		try (InputStream in = new BufferedInputStream(Files.newInputStream(path));
				fr.delthas.javamp3.Sound sound = new fr.delthas.javamp3.Sound(in)) {
			int rate = sound.getSamplingFrequency();
			int channels = sound.isStereo() ? 2 : 1;
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			byte[] buffer = new byte[16384];
			long limit = (long) (MAX_SECONDS * rate * channels * 2);
			int read;
			while (bytes.size() < limit && (read = sound.read(buffer)) > 0) {
				bytes.write(buffer, 0, read);
			}
			return fromPcm16(bytes.toByteArray(), channels, rate, false);
		} catch (RuntimeException e) {
			throw new IOException("Not a readable MP3", e);
		}
	}
	private static Pcm ogg(Path path) throws IOException {
		byte[] data = Files.readAllBytes(path);
		ByteBuffer native_ = MemoryUtil.memAlloc(data.length);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			native_.put(data).flip();
			IntBuffer channels = stack.mallocInt(1);
			IntBuffer rate = stack.mallocInt(1);
			ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(native_, channels, rate);
			if (pcm == null) {
				throw new IOException("Not a readable OGG Vorbis file");
			}
			try {
				int ch = Math.max(1, channels.get(0));
				int frames = (int) Math.min(pcm.remaining() / ch, (long) (MAX_SECONDS * rate.get(0)));
				float[] out = new float[frames];
				for (int f = 0; f < frames; f++) {
					float sum = 0;
					for (int c = 0; c < ch; c++) {
						sum += pcm.get(f * ch + c) / 32768f;
					}
					out[f] = sum / ch;
				}
				return new Pcm(out, rate.get(0));
			} finally {
				LibCStdlib.free(pcm);
			}
		} finally {
			MemoryUtil.memFree(native_);
		}
	}
	private static Pcm sampled(Path path) throws IOException {
		try (AudioInputStream raw = AudioSystem.getAudioInputStream(path.toFile())) {
			AudioFormat source = raw.getFormat();
			AudioFormat target = new AudioFormat(source.getSampleRate(), 16, source.getChannels(), true, false);
			try (AudioInputStream in = AudioSystem.getAudioInputStream(target, raw)) {
				long limit = (long) (MAX_SECONDS * target.getSampleRate()) * target.getChannels() * 2;
				byte[] data = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 16, limit));
				return fromPcm16(data, target.getChannels(), Math.round(target.getSampleRate()), false);
			}
		} catch (javax.sound.sampled.UnsupportedAudioFileException | IllegalArgumentException e) {
			throw new IOException("Unsupported audio format (use MP3, OGG or WAV)", e);
		}
	}
	private static Pcm fromPcm16(byte[] data, int channels, int rate, boolean bigEndian) throws IOException {
		int frames = data.length / (2 * channels);
		if (frames < 2 || rate <= 0) {
			throw new IOException("The file has no sound in it");
		}
		float[] out = new float[frames];
		for (int f = 0; f < frames; f++) {
			float sum = 0;
			for (int c = 0; c < channels; c++) {
				int i = (f * channels + c) * 2;
				int lo = data[bigEndian ? i + 1 : i] & 0xFF;
				int hi = data[bigEndian ? i : i + 1];
				sum += (short) ((hi << 8) | lo) / 32768f;
			}
			out[f] = sum / channels;
		}
		return new Pcm(out, rate);
	}
}
