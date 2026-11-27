package nz.temple.client.security;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import nz.temple.module.Category;
import nz.temple.module.LazyPayloadModule;
import nz.temple.module.Module;
import nz.temple.module.PayloadModuleDescriptor;

public final class PayloadModules {
	private static volatile ClassLoader source;
	private static final Map<String, Supplier<? extends ClassLoader>> artifacts = new ConcurrentHashMap<>();
	private PayloadModules() {
	}
	public static void source(ClassLoader loader) {
		source = loader;
	}
	public static boolean usingPayload() {
		return source != null;
	}
	public static LazyPayloadModule lazy(PayloadModuleDescriptor descriptor, String className) {
		return lazy(descriptor, className, className);
	}
	public static LazyPayloadModule lazy(String name, Category category, String description, String className) {
		return lazy(new PayloadModuleDescriptor(name, category, description), className);
	}
	public static void artifact(String artifact, Supplier<? extends ClassLoader> loader) {
		artifacts.put(requireArtifact(artifact), Objects.requireNonNull(loader, "loader"));
	}
	public static void removeArtifact(String artifact) {
		artifacts.remove(requireArtifact(artifact));
	}
	public static LazyPayloadModule lazy(PayloadModuleDescriptor descriptor, String artifact, String className) {
		Objects.requireNonNull(descriptor, "descriptor");
		Objects.requireNonNull(className, "className");
		String artifactName = requireArtifact(artifact);
		return new LazyPayloadModule(descriptor, () -> load(artifactName, className), () -> ensureArtifact(artifactName));
	}
	public static void ensureArtifact(String artifact) {
		Supplier<? extends ClassLoader> supplier = artifacts.get(requireArtifact(artifact));
		if (supplier != null) {
			Objects.requireNonNull(supplier.get(), "payload artifact loader");
		}
	}
	public static Module load(String className) {
		return construct(className, source != null ? source : PayloadModules.class.getClassLoader());
	}
	public static Module load(String artifact, String className) {
		Supplier<? extends ClassLoader> supplier = artifacts.get(requireArtifact(artifact));
		ClassLoader loader = supplier != null ? Objects.requireNonNull(supplier.get(), "payload artifact loader")
				: PayloadModules.class.getClassLoader();
		return construct(className, loader);
	}
	private static Module construct(String className, ClassLoader loader) {
		try {
			Class<?> type = Class.forName(className, true, loader);
			return (Module) type.getDeclaredConstructor().newInstance();
		} catch (ReflectiveOperationException | ClassCastException e) {
			throw new IllegalStateException("payload module unavailable: " + className, e);
		}
	}
	private static String requireArtifact(String artifact) {
		if (artifact == null || artifact.isBlank()) {
			throw new IllegalArgumentException("payload artifact must not be blank");
		}
		return artifact;
	}
}
