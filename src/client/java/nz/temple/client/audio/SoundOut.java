package nz.temple.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import nz.temple.TempleNz;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SoundOut {
	private static final Logger LOGGER = LoggerFactory.getLogger(TempleNz.NAME + "/audio");
	private static final int RATE = 44100;
	private static final int CHUNK = 128;
	private static final int LINE_FRAMES = 1024;
	private static final long IDLE_CLOSE_NANOS = 60_000_000_000L;
	private static final int AHEAD_FRAMES = CHUNK * 3;
	private static final int MAX_VOICES = 24;
	public static final class Voice {
		private final float[] samples;
		private final double end;
		private final double start;
		private final double step;
		private final float gain;
		private volatile double position;
		private volatile boolean stopped;
		private Voice(Pcm pcm, double startSec, double endSec, float gain, float pitch) {
			this.samples = pcm.samples();
			this.start = Math.max(0, Math.min(samples.length - 1, startSec * pcm.rate()));
			this.end = Math.max(start + 1, Math.min(samples.length - 1, endSec * pcm.rate()));
			this.step = pcm.rate() / (double) RATE * Math.max(0.25f, Math.min(4f, pitch));
			this.gain = gain;
			this.position = start;
		}
		public double progress() {
			return (position - start) / Math.max(1, end - start);
		}
		public boolean playing() {
			return !stopped && position < end;
		}
		public void stop() {
			stopped = true;
		}
	}
	private static final List<Voice> VOICES = new CopyOnWriteArrayList<>();
	private static final Object WAKE = new Object();
	private static Thread thread;
	private static boolean broken;
	private SoundOut() {
	}
	public static Voice play(Pcm pcm, float volume, float pitch) {
		return play(pcm, 0, pcm == null ? 0 : pcm.seconds(), volume, pitch);
	}
	public static Voice play(Pcm pcm, double startSec, double endSec, float volume, float pitch) {
		if (pcm == null || pcm.samples().length < 2 || broken) {
			return null;
		}
		float master = 1;
		try {
			master = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER);
		} catch (RuntimeException ignored) {
		}
		float gain = Math.max(0, volume) * master;
		if (gain <= 0.0005f) {
			return null;
		}
		Voice v = new Voice(pcm, startSec, endSec, gain, pitch);
		if (VOICES.size() >= MAX_VOICES) {
			VOICES.remove(0);
		}
		VOICES.add(v);
		start();
		return v;
	}
	public static String status() {
		return broken ? "broken" : VOICES.isEmpty() ? "idle" : VOICES.size() + " playing";
	}
	public static void stopAll() {
		for (Voice v : VOICES) {
			v.stop();
		}
	}

	private static synchronized void start() {
		if (thread == null || !thread.isAlive()) {
			thread = new Thread(SoundOut::run, "Temple.nz audio");
			thread.setDaemon(true);
			thread.setPriority(Thread.MAX_PRIORITY - 1);
			thread.start();
		}
		synchronized (WAKE) {
			WAKE.notifyAll();
		}
	}
	private static void run() {
		AudioFormat format = new AudioFormat(RATE, 16, 2, true, false);
		float[] mix = new float[CHUNK * 2];
		byte[] out = new byte[CHUNK * 4];
		SourceDataLine line = null;
		long quietSince = System.nanoTime();
		try {
			while (true) {
				if (VOICES.isEmpty()) {
					if (line != null && System.nanoTime() - quietSince > IDLE_CLOSE_NANOS) {
						line.drain();
						line.close();
						line = null;
					}
					synchronized (WAKE) {
						if (VOICES.isEmpty()) {
							WAKE.wait(line == null ? 0 : 250);
						}
					}
					continue;
				}
				if (line == null) {
					line = AudioSystem.getSourceDataLine(format);
					line.open(format, LINE_FRAMES * 4);
					line.start();
				}
				while (line.getBufferSize() - line.available() > AHEAD_FRAMES * 4) {
					java.util.concurrent.locks.LockSupport.parkNanos(400_000);
				}
				java.util.Arrays.fill(mix, 0);
				for (Voice v : VOICES) {
					if (!v.playing()) {
						VOICES.remove(v);
						continue;
					}
					mixVoice(v, mix);
				}
				for (int i = 0; i < mix.length; i++) {
					float s = mix[i];
					s = s / (1 + Math.abs(s) * 0.35f);
					int v = Math.round(Math.max(-1, Math.min(1, s)) * 32767);
					out[i * 2] = (byte) v;
					out[i * 2 + 1] = (byte) (v >> 8);
				}
				line.write(out, 0, out.length);
				quietSince = System.nanoTime();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception | LinkageError e) {
			broken = true;
			VOICES.clear();
			LOGGER.warn("Client sounds disabled: no audio output ({})", e.toString());
		} finally {
			if (line != null) {
				line.close();
			}
		}
	}
	private static void mixVoice(Voice v, float[] mix) {
		float[] s = v.samples;
		double pos = v.position;
		double fadeLen = Math.max(1, Math.min(220 * v.step, (v.end - v.start) / 4));
		for (int f = 0; f < CHUNK; f++) {
			if (pos >= v.end) {
				break;
			}
			int i = (int) pos;
			double frac = pos - i;
			float a = s[i];
			float b = s[Math.min(s.length - 1, i + 1)];
			float sample = (float) (a + (b - a) * frac);
			double env = Math.min(1, Math.min((pos - v.start) / fadeLen + 0.02, (v.end - pos) / fadeLen));
			float out = sample * v.gain * (float) env;
			mix[f * 2] += out;
			mix[f * 2 + 1] += out;
			pos += v.step;
		}
		v.position = pos;
	}
}
