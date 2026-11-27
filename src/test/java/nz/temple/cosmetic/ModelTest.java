package nz.temple.cosmetic;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelTest {
	static byte[] triangle(int version, int lastIndex) {
		ByteBuffer b = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);
		b.put("TMDL".getBytes(StandardCharsets.US_ASCII)).putInt(version);
		b.putInt(1);
		name(b, "head");
		b.putInt(-1).putFloat(0).putFloat(1.5f).putFloat(0);
		b.putInt(1);
		name(b, "horn");
		b.putInt(0).putInt(0).putInt(3);
		b.putInt(3);
		float[][] pos = {{0, 0, 0}, {1, 0, 0}, {0, 1, 0}};
		for (int i = 0; i < 3; i++) {
			b.putFloat(pos[i][0]).putFloat(pos[i][1]).putFloat(pos[i][2]);
			b.putFloat(0).putFloat(0).putFloat(1);
			b.putFloat(i * 0.5f).putFloat(1 - i * 0.5f);
			b.put((byte) 255).put((byte) (i == 2 ? 255 : 0)).put((byte) (i * 127)).put((byte) 0);
		}
		b.putInt(3).putInt(0).putInt(1).putInt(lastIndex);
		byte[] out = new byte[b.position()];
		b.flip().get(out);
		return out;
	}

	private static void name(ByteBuffer b, String s) {
		byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
		b.put((byte) bytes.length).put(bytes);
	}

	@Test
	void readsWhatTheExporterWrites() throws IOException {
		Model m = Model.read(new ByteArrayInputStream(triangle(Model.VERSION, 2)));
		assertEquals(1, m.joints().size());
		assertEquals("head", m.joints().get(0).name());
		assertEquals(1.5f, m.joints().get(0).py());
		assertEquals(3, m.vertexCount());
		assertEquals(1f, m.x(1));
		assertEquals(1f, m.nz(0));
		assertEquals(0.5f, m.u(1));
		assertEquals(1.0, m.occlusion(0), 1e-9);
		assertEquals(1.0, m.glowMask(2), 1e-9);
		assertEquals(0.0, m.glowMask(0), 1e-9);
		assertEquals(254 / 255.0, m.ramp(2), 1e-9);
		Model.Part horn = m.part("horn");
		assertNotNull(horn);
		assertEquals(0, horn.joint());
		assertEquals(3, horn.indexCount());
		assertNull(m.part("gem"));
	}

	@Test
	void rejectsOtherFilesVersionsAndDamage() {
		assertThrows(IOException.class, () -> Model.read(new ByteArrayInputStream("hello world".getBytes())));
		assertThrows(IOException.class, () -> Model.read(new ByteArrayInputStream(triangle(99, 2))));
		assertThrows(IOException.class, () -> Model.read(new ByteArrayInputStream(triangle(Model.VERSION, 7))));
		byte[] cut = triangle(Model.VERSION, 2);
		assertThrows(IOException.class, () -> Model.read(new ByteArrayInputStream(java.util.Arrays.copyOf(cut, cut.length - 6))));
	}
}
