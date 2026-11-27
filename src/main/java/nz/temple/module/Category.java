package nz.temple.module;

public enum Category {
	COMBAT("Combat"),
	HVH("HvH"),
	MOVEMENT("Movement"),
	RENDER("Render"),
	COSMETICS("Cosmetics"),
	WORLD("World"),
	PLAYER("Player"),
	HUD("HUD"),
	CLIENT("Client");

	private final String displayName;

	Category(String displayName) {
		this.displayName = displayName;
	}

	public String displayName() {
		return displayName;
	}
}
