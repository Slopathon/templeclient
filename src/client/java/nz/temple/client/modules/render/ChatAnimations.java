package nz.temple.client.modules.render;

import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

public final class ChatAnimations extends Module {
	private static ChatAnimations instance;
	private final NumberSetting duration;
	private long lineAt;

	public ChatAnimations() {
		super("Chat Animations", Category.RENDER, "New chat messages slide in instead of the chat jumping up a line.");
		duration = add(new NumberSetting("Slide time", 220, 80, 600, 20, " ms")
				.describe("How long the slide takes."));
		instance = this;
	}

	public static ChatAnimations active() {
		return instance != null && instance.isEnabled() ? instance : null;
	}

	public static void line() {
		if (instance != null) {
			instance.lineAt = System.nanoTime();
		}
	}

	public float offset() {
		float t = (System.nanoTime() - lineAt) / 1e6f / duration.getFloat();
		if (t >= 1) {
			return 0;
		}
		float u = 1 - Math.max(0, t);
		return 9 * u * u * u;
	}
}
