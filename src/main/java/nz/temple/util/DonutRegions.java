package nz.temple.util;

public final class DonutRegions {
	public static final String MAP_NOTE = "Based on the public DonutSMP region map (Miep/StackingDev); servers may move.";
	public static final int GRID = 9;
	public static final int MIN = -225_000;
	public static final int MAX = 225_000;
	public static final int CELL_SIZE = 50_000;
	public static final String[] REGIONS = {"NA East", "NA West", "EU Central", "EU West", "Asia", "Oceania"};
	private static final String[] ROWS = {
			"OWWWEEEEE",
			"OWWWEEEEE",
			"OWWWEEEEE",
			"OOOWEEEEE",
			"AAAAEEEEE",
			"AUUCCCCCE",
			"AUUCCCCCC",
			"CUCCCCCCC",
			"CUUUUUUUC"
	};
	private static final int[][] GOLIATH = {
			{82, 100, 101, 102, 103, 104, 105, 106, 91},
			{83, 44, 75, 42, 41, 40, 39, 38, 92},
			{84, 45, 14, 13, 12, 11, 10, 37, 93},
			{85, 46, 74, 3, 2, 1, 25, 36, 94},
			{86, 47, 72, 71, 5, 4, 24, 35, 95},
			{87, 51, 17, 9, 8, 7, 23, 34, 96},
			{88, 54, 18, 61, 62, 21, 22, 33, 97},
			{89, 26, 27, 28, 29, 30, 59, 32, 98},
			{90, 107, 108, 109, 110, 111, 112, 113, 99}
	};
	private DonutRegions() {
	}

	public record Result(int cell, String region) {
		public int goliath() {
			return DonutRegions.goliath(cell);
		}
		public String server() {
			return DonutRegions.server(cell);
		}
	}
	public static Result locate(double x, double z) {
		if (x < MIN || x >= MAX || z < MIN || z >= MAX) {
			return null;
		}
		int column = (int) Math.floor((x - MIN) / CELL_SIZE);
		int row = (int) Math.floor((z - MIN) / CELL_SIZE);
		return new Result(1 + column + row * GRID, regionAt(column, row));
	}
	public static String regionAt(int column, int row) {
		return switch (ROWS[row].charAt(column)) {
			case 'O' -> "Oceania";
			case 'W' -> "NA West";
			case 'E' -> "NA East";
			case 'A' -> "Asia";
			case 'U' -> "EU West";
			default -> "EU Central";
		};
	}
	public static int goliathAt(int column, int row) {
		return GOLIATH[row][column];
	}
	public static int goliath(int cell) {
		if (cell < 1 || cell > GRID * GRID) {
			return 0;
		}
		return GOLIATH[(cell - 1) / GRID][(cell - 1) % GRID];
	}
	public static int cellOf(int number) {
		for (int row = 0; row < GRID; row++) {
			for (int column = 0; column < GRID; column++) {
				if (GOLIATH[row][column] == number) {
					return 1 + column + row * GRID;
				}
			}
		}
		return 0;
	}
	public static String regionOf(int cell) {
		if (cell < 1 || cell > GRID * GRID) {
			return null;
		}
		return regionAt((cell - 1) % GRID, (cell - 1) / GRID);
	}
	public static String server(int cell) {
		return "Goliath " + goliath(cell);
	}
	public static int[] bounds(int cell) {
		int column = (cell - 1) % GRID;
		int row = (cell - 1) / GRID;
		int x = MIN + column * CELL_SIZE;
		int z = MIN + row * CELL_SIZE;
		return new int[] {x, z, x + CELL_SIZE, z + CELL_SIZE};
	}
}
