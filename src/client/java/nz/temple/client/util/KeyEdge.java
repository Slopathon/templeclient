package nz.temple.client.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import nz.temple.setting.KeybindSetting;

public final class KeyEdge {
	private final KeybindSetting key;
	private boolean wasDown;
	public KeyEdge(KeybindSetting key) {
		this.key = key;
	}
	public boolean pressed() {
		Minecraft mc = Minecraft.getInstance();
		boolean down = key.isBound() && nz.temple.client.input.KeyNames.isDown(mc.getWindow(), key.get());
		boolean fire = down && !wasDown && mc.gui.screen() == null && mc.player != null;
		wasDown = down;
		return fire;
	}
}
