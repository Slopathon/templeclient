package nz.temple.cosmetic;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShippedModelsTest {
	private static final Path DIR = Path.of("src/client/resources/assets/templenz/models");
	private static final int PIECE_BUDGET = 4_500;

	@Test
	void everyShippedModelLoadsWithinBudget() throws IOException {
		List<Path> files;
		try (Stream<Path> s = Files.list(DIR)) {
			files = s.filter(p -> p.toString().endsWith(".tmdl")).toList();
		}
		assertFalse(files.isEmpty(), "no models shipped");
		for (Path f : files) {
			Model m;
			try (InputStream in = Files.newInputStream(f)) {
				m = Model.read(in);
			}
			int tris = m.indices().length / 3;
			String name = f.getFileName().toString();
			int budget = name.startsWith("pet_") || name.startsWith("crown_") ? PIECE_BUDGET * 2 : PIECE_BUDGET;
			assertTrue(tris <= budget, f + " has " + tris + " triangles, budget " + budget);
			assertFalse(m.parts().isEmpty(), f + " has no parts");
			for (int v = 0; v < m.vertexCount(); v++) {
				double len = Math.sqrt(m.nx(v) * m.nx(v) + m.ny(v) * m.ny(v) + m.nz(v) * m.nz(v));
				assertTrue(Math.abs(len - 1) < 0.01, f + " vertex " + v + " normal length " + len);
			}
		}
	}
}
