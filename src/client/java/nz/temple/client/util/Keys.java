package nz.temple.client.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import nz.temple.client.compat.Input;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class Keys {
	private static final Map<KeyMapping, Integer> presses = new HashMap<>();
	private Keys() {
	}

	public static void press(KeyMapping key, int ticks) {
		key.setDown(true);
		presses.merge(key, ticks, Math::max);
	}
	public static void release(KeyMapping key) {
		if (presses.remove(key) != null && !physicallyDown(key)) {
			key.setDown(false);
		}
	}
	public static void tick() {
		Iterator<Map.Entry<KeyMapping, Integer>> it = presses.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<KeyMapping, Integer> entry = it.next();
			int left = entry.getValue() - 1;
			if (left <= 0) {
				if (!physicallyDown(entry.getKey())) {
					entry.getKey().setDown(false);
				}
				it.remove();
			} else {
				entry.setValue(left);
			}
		}
	}
	public static boolean physicallyDown(KeyMapping key) {
		var window = Minecraft.getInstance().getWindow();
		InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(key);
		if (bound.getType() == InputConstants.Type.MOUSE) {
			return Input.isMouseDown(window, bound.getValue());
		}
		return bound.getType() == Input.KEYBOARD && bound.getValue() >= 0 && Input.isKeyDown(window, bound.getValue());
	}
}
