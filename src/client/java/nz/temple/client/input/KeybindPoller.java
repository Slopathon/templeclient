package nz.temple.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import nz.temple.module.Module;
import nz.temple.module.ModuleManager;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class KeybindPoller {
	private final ModuleManager modules;
	private final Set<Integer> wasDown = new HashSet<>();
	private final Map<Module, Integer> heldModules = new IdentityHashMap<>();
	private final Map<Module, Integer> ownKeys = new IdentityHashMap<>();

	public KeybindPoller(ModuleManager modules) {
		this.modules = modules;
	}

	public void tick(Minecraft mc) {
		Window window = mc.getWindow();
		Set<Integer> down = new HashSet<>();
		for (Module m : modules.all()) {
			if (m.keybind().isBound() && KeyNames.isDown(window, m.keybind().get())) {
				down.add(m.keybind().get());
			}
		}
		Iterator<Map.Entry<Module, Integer>> held = heldModules.entrySet().iterator();
		while (held.hasNext()) {
			Map.Entry<Module, Integer> entry = held.next();
			if (!KeyNames.isDown(window, entry.getValue())) {
				entry.getKey().setEnabled(false);
				held.remove();
			}
		}
		Iterator<Map.Entry<Module, Integer>> own = ownKeys.entrySet().iterator();
		while (own.hasNext()) {
			Map.Entry<Module, Integer> entry = own.next();
			if (!KeyNames.isDown(window, entry.getValue())) {
				Module released = entry.getKey();
				own.remove();
				released.keyReleased();
			}
		}
		if (mc.player != null && mc.gui.screen() == null) {
			for (Module m : modules.all()) {
				int key = m.keybind().get();
				if (!m.isAlwaysOn() && m.keybind().isBound() && down.contains(key) && !wasDown.contains(key)) {
					if (m.ownsKey()) {
						ownKeys.put(m, key);
						m.keyPressed();
					} else if (m.disableOnKeyRelease().get()) {
						m.setEnabled(true);
						heldModules.put(m, key);
					} else {
						m.toggle();
					}
				}
			}
		}
		wasDown.clear();
		wasDown.addAll(down);
	}
}
