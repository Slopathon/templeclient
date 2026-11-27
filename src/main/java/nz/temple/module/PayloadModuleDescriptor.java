package nz.temple.module;

import java.util.Objects;

public record PayloadModuleDescriptor(String name, Category category, String description, boolean alwaysOn) {
	public PayloadModuleDescriptor {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(category, "category");
		Objects.requireNonNull(description, "description");
	}
	public PayloadModuleDescriptor(String name, Category category, String description) {
		this(name, category, description, false);
	}
}
