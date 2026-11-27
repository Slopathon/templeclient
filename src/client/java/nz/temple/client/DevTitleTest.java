package nz.temple.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.input.MouseButtonInfo;
import nz.temple.client.gui.TempleTitleScreen;

import java.lang.reflect.Method;

final class DevTitleTest {
	private static final float SINGLEPLAYER_U = 0.085f;
	private static final float SINGLEPLAYER_V = 0.307f;
	private int tick;
	private int shot;
	private DevTitleTest() {
	}
	static void start() {
		ClientTickEvents.END_CLIENT_TICK.register(new DevTitleTest()::tick);
	}
	private void tick(Minecraft mc) {
		if (!(mc.gui.screen() instanceof TempleTitleScreen) && tick == 0 || mc.gui.overlay() != null) {
			return;
		}
		tick++;
		if (tick == 60) {
			move(mc, 0.6f, 0.9f);
		}
		if (tick >= 100 && tick <= 103) {
			grab(mc, "idle");
		}
		if (tick == 105) {
			move(mc, SINGLEPLAYER_U, SINGLEPLAYER_V);
		}
		if (tick >= 115 && tick <= 122) {
			grab(mc, "hover");
		}
		if (tick == 125) {
			move(mc, SINGLEPLAYER_U, SINGLEPLAYER_V);
			button(mc, InputConstants.PRESS);
			button(mc, InputConstants.RELEASE);
		}
		if (tick == 150 || tick == 151) {
			grab(mc, "singleplayer");
		}
		if (tick == 160 && mc.gui.screen() instanceof TempleTitleScreen title) {
			title.showcase("close");
		}
		if (tick == 170) {
			click(mc, SINGLEPLAYER_U, 0.515f);
		}
		if (tick == 200) {
			grab(mc, "temple");
			mc.gui.setScreen(new TempleTitleScreen());
		}
		if (tick == 220) {
			click(mc, SINGLEPLAYER_U, 0.583f);
		}
		if (tick == 250) {
			grab(mc, "options");
		}
		if (tick == 270) {
			System.out.println("[temple] title test: done, on " + (mc.gui.screen() == null ? "no screen" : mc.gui.screen().getClass().getSimpleName()));
			mc.stop();
		}
	}
	private static void move(Minecraft mc, float u, float v) {
		double x = u * mc.getWindow().getScreenWidth();
		double y = v * mc.getWindow().getScreenHeight();
		Method onMove = method("onMove");
		Object[] args = onMove.getParameterCount() == 5
				? new Object[] {mc.getWindow().handle(), x, y, 0.0, 0.0}
				: new Object[] {mc.getWindow().handle(), x, y};
		invoke(onMove, mc.mouseHandler, args);
		System.out.println("[temple] title test: cursor at " + Math.round(x) + "," + Math.round(y));
	}
	private static void click(Minecraft mc, float u, float v) {
		move(mc, u, v);
		button(mc, InputConstants.PRESS);
		button(mc, InputConstants.RELEASE);
		System.out.println("[temple] title test: clicked, now on " + (mc.gui.screen() == null ? "no screen" : mc.gui.screen().getClass().getSimpleName()));
	}
	private static void button(Minecraft mc, int action) {
		invoke(method("onButton"), mc.mouseHandler,
				mc.getWindow().handle(), new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0), action);
	}
	private static Method method(String name) {
		for (Method method : MouseHandler.class.getDeclaredMethods()) {
			if (method.getName().equals(name)) {
				method.setAccessible(true);
				return method;
			}
		}
		throw new IllegalStateException("MouseHandler has no " + name);
	}
	private static void invoke(Method method, Object target, Object... args) {
		try {
			method.invoke(target, args);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}
	private void grab(Minecraft mc, String what) {
		String name = "title-test-" + (++shot) + "-" + what + ".png";
		Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1,
				message -> System.out.println("[temple] title test: saved " + name));
	}
}
