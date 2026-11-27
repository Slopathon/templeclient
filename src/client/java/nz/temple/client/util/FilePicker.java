package nz.temple.client.util;

import net.minecraft.client.Minecraft;
import nz.temple.setting.FileSetting;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FilePicker {
	private static final AtomicBoolean OPEN = new AtomicBoolean();
	private FilePicker() {
	}

	public static java.nio.file.Path path(FileSetting setting) {
		if (setting == null || setting.isDefault()) {
			return null;
		}
		try {
			return java.nio.file.Path.of(setting.get());
		} catch (RuntimeException e) {
			return null;
		}
	}
	public static boolean isOpen() {
		return OPEN.get();
	}
	public static void choose(FileSetting setting) {
		if (!OPEN.compareAndSet(false, true)) {
			return;
		}
		Thread worker = new Thread(() -> {
			String picked = null;
			try {
				picked = open("Choose " + setting.name().toLowerCase(), setting.extensions());
			} catch (Throwable ignored) {
			} finally {
				OPEN.set(false);
			}
			if (picked != null && !picked.isBlank()) {
				String path = picked;
				Minecraft.getInstance().execute(() -> setting.set(path));
			}
		}, "Temple file picker");
		worker.setDaemon(true);
		worker.start();
	}
	private static String open(String title, List<String> extensions) {
		String label = extensions.isEmpty() ? null : "Images (" + String.join(", ", extensions) + ")";
		return nz.temple.client.compat.Platform.openFileDialog(title, extensions, label);
	}
}
