package nz.temple.client.util;

import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import nz.temple.client.event.PacketReceiveEvents;

/**
 * The server's ticks per second, estimated from how often it sends the time of day (every 20 ticks). Averaged over the last
 * few updates; 20 when nothing has arrived yet.
 */
public final class ServerTps {
	private static final float[] samples = new float[8];
	private static int count;
	private static int head;
	private static long last;
	private static boolean registered;

	private ServerTps() {
	}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		PacketReceiveEvents.register(event -> {
			if (event.getPacket() instanceof ClientboundSetTimePacket) {
				long now = System.nanoTime();
				if (last != 0) {
					float seconds = (now - last) / 1e9f;
					if (seconds > 0.2f && seconds < 30) {
						samples[head] = Math.min(20, 20 / seconds);
						head = (head + 1) % samples.length;
						count = Math.min(samples.length, count + 1);
					}
				}
				last = now;
			}
		});
	}

	public static void reset() {
		count = 0;
		last = 0;
	}

	public static float tps() {
		if (count == 0) {
			return 20;
		}
		float sum = 0;
		for (int i = 0; i < count; i++) {
			sum += samples[i];
		}
		return sum / count;
	}
}
