package nz.temple.client.render.streamproof;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.WString;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

final class OverlayWindow {
	private static final int GWL_EXSTYLE = -20;
	private static final int GWLP_HWNDPARENT = -8;
	private static final long WS_POPUP = 0x80000000L;
	private static final long WS_EX_TOPMOST = 0x8;
	private static final long WS_EX_TRANSPARENT = 0x20;
	private static final long WS_EX_TOOLWINDOW = 0x80;
	private static final long WS_EX_APPWINDOW = 0x40000;
	private static final long WS_EX_LAYERED = 0x80000;
	private static final long WS_EX_NOACTIVATE = 0x08000000;
	private static final int LWA_ALPHA = 2;
	private static final int WDA_EXCLUDEFROMCAPTURE = 0x11;
	private static final int SW_HIDE = 0;
	private static final int SW_SHOWNOACTIVATE = 4;
	private static final int SWP_NOZORDER = 0x4;
	private static final int SWP_NOACTIVATE = 0x10;
	private static final int SWP_NOOWNERZORDER = 0x200;
	private static final Pointer HWND_TOPMOST = Pointer.createConstant(-1L);
	private static final Pointer HWND_NOTOPMOST = Pointer.createConstant(-2L);
	private static final String CLASS_NAME = "TempleOverlay";
	private static Function setWindowDisplayAffinity;
	private static Function getWindowLongPtr;
	private static Function setWindowLongPtr;
	private static Function setLayeredWindowAttributes;
	private static Function setWindowPos;
	private static Function showWindow;
	private static Function getDC;
	private static Function releaseDC;
	private static Function clientToScreen;
	private static Function getClientRect;
	private static Function isIconic;
	private static Function setDIBitsToDevice;
	private static Function createWindowEx;
	private static Function destroyWindow;
	private static boolean classRegistered;
	private final Pointer hwnd;
	private final Pointer owner;
	private final Memory point = new Memory(8);
	private final Memory rect = new Memory(16);
	private final Memory header = new Memory(40);
	private Memory pixels;
	private int[] bgra = new int[0];
	private int x = Integer.MIN_VALUE;
	private int y;
	private int width;
	private int height;
	private boolean shown;
	private int sinceRaised = Integer.MAX_VALUE;
	private boolean topmost;
	private OverlayWindow(Pointer hwnd, Pointer owner) {
		this.hwnd = hwnd;
		this.owner = owner;
	}
	static OverlayWindow create(long ownerHwnd) {
		bind();
		if (ownerHwnd == 0) {
			throw new IllegalStateException("could not find the game window");
		}
		Pointer hwnd = (Pointer) createWindowEx.invoke(Pointer.class, new Object[] {0, new WString(CLASS_NAME),
				new WString("Temple overlay"), (int) WS_POPUP, 0, 0, 16, 16, null, null, moduleHandle(), null});
		if (hwnd == null) {
			throw new IllegalStateException("could not create the overlay window");
		}
		Pointer owner = new Pointer(ownerHwnd);
		try {
			long ex = getWindowLongPtr.invokeLong(new Object[] {hwnd, GWL_EXSTYLE});
			ex = (ex | WS_EX_LAYERED | WS_EX_TRANSPARENT | WS_EX_NOACTIVATE | WS_EX_TOOLWINDOW) & ~WS_EX_APPWINDOW;
			setWindowLongPtr.invokeLong(new Object[] {hwnd, GWL_EXSTYLE, ex});
			setLayeredWindowAttributes.invokeInt(new Object[] {hwnd, 0, (byte) 255, LWA_ALPHA});
			setWindowLongPtr.invokeLong(new Object[] {hwnd, GWLP_HWNDPARENT, ownerHwnd});
			int ok = Boolean.getBoolean("temple.streamproof.visible") ? 1
					: setWindowDisplayAffinity.invokeInt(new Object[] {hwnd, WDA_EXCLUDEFROMCAPTURE});
			if (ok == 0) {
				destroyWindow.invokeInt(new Object[] {hwnd});
				throw new IllegalStateException("needs Windows 10 version 2004 or newer");
			}
		} catch (IllegalStateException e) {
			throw e;
		} catch (RuntimeException | LinkageError t) {
			destroyWindow.invokeInt(new Object[] {hwnd});
			throw new IllegalStateException("could not set up the overlay window", t);
		}
		OverlayWindow window = new OverlayWindow(hwnd, owner);
		window.header.clear();
		window.header.setInt(0, 40);
		window.header.setShort(12, (short) 1);
		window.header.setShort(14, (short) 32);
		return window;
	}
	private static Pointer moduleHandle() {
		return (Pointer) Function.getFunction("kernel32", "GetModuleHandleW").invoke(Pointer.class, new Object[] {null});
	}
	private static synchronized void bind() {
		if (createWindowEx != null) {
			return;
		}
		setWindowDisplayAffinity = user32("SetWindowDisplayAffinity");
		getWindowLongPtr = user32("GetWindowLongPtrW");
		setWindowLongPtr = user32("SetWindowLongPtrW");
		setLayeredWindowAttributes = user32("SetLayeredWindowAttributes");
		setWindowPos = user32("SetWindowPos");
		showWindow = user32("ShowWindow");
		getDC = user32("GetDC");
		releaseDC = user32("ReleaseDC");
		clientToScreen = user32("ClientToScreen");
		getClientRect = user32("GetClientRect");
		isIconic = user32("IsIconic");
		setDIBitsToDevice = Function.getFunction("gdi32", "SetDIBitsToDevice");
		destroyWindow = user32("DestroyWindow");
		if (!classRegistered) {
			Memory wc = new Memory(80);
			wc.clear();
			wc.setInt(0, 80);
			wc.setPointer(8, user32("DefWindowProcW"));
			wc.setPointer(24, moduleHandle());
			Memory name = new Memory((CLASS_NAME.length() + 1) * 2L);
			name.setWideString(0, CLASS_NAME);
			wc.setPointer(64, name);
			int atom = user32("RegisterClassExW").invokeInt(new Object[] {wc});
			if (atom == 0 && com.sun.jna.Native.getLastError() != 1410) {
				throw new IllegalStateException("could not register the overlay window class");
			}
			classRegistered = true;
		}
		createWindowEx = user32("CreateWindowExW");
	}
	private static Function user32(String name) {
		try {
			return Function.getFunction("user32", name);
		} catch (UnsatisfiedLinkError e) {
			throw new IllegalStateException("missing " + name, e);
		}
	}
	void follow() {
		try {
			if (isIconic.invokeInt(new Object[] {owner}) != 0) {
				return;
			}
			point.setInt(0, 0);
			point.setInt(4, 0);
			int ok = clientToScreen.invokeInt(new Object[] {owner, point});
			ok &= getClientRect.invokeInt(new Object[] {owner, rect});
			if (ok == 0) {
				return;
			}
			int nx = point.getInt(0);
			int ny = point.getInt(4);
			int nw = rect.getInt(8) - rect.getInt(0);
			int nh = rect.getInt(12) - rect.getInt(4);
			boolean raise = ++sinceRaised > 30;
			boolean ownerTopmost = (getWindowLongPtr.invokeLong(new Object[] {owner, GWL_EXSTYLE}) & WS_EX_TOPMOST) != 0;
			boolean restack = raise || ownerTopmost != topmost;
			if (restack || nx != x || ny != y || nw != width || nh != height) {
				x = nx;
				y = ny;
				width = nw;
				height = nh;
				if (ownerTopmost != topmost && !ownerTopmost) {
					setWindowPos.invokeInt(new Object[] {hwnd, HWND_NOTOPMOST, nx, ny, Math.max(1, nw), Math.max(1, nh),
							SWP_NOACTIVATE | SWP_NOOWNERZORDER});
				}
				topmost = ownerTopmost;
				Pointer above = ownerTopmost ? HWND_TOPMOST : null;
				setWindowPos.invokeInt(new Object[] {hwnd, above, nx, ny, Math.max(1, nw), Math.max(1, nh),
						SWP_NOACTIVATE | SWP_NOOWNERZORDER | (restack ? 0 : SWP_NOZORDER)});
				if (raise) {
					sinceRaised = 0;
				}
			}
		} catch (RuntimeException | LinkageError t) {
			throw new IllegalStateException("could not move the overlay", t);
		}
	}
	void present(ByteBuffer rgba, int w, int h) {
		int count = w * h;
		long size = (long) count * 4;
		if (pixels == null || pixels.size() != size) {
			pixels = new Memory(size);
			bgra = new int[count];
		}
		IntBuffer src = rgba.duplicate().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
		for (int i = 0; i < count; i++) {
			int v = src.get(i);
			bgra[i] = 0xFF000000 | (v & 0x0000FF00) | ((v & 0xFF) << 16) | ((v >>> 16) & 0xFF);
		}
		pixels.write(0, bgra, 0, count);
		header.setInt(4, w);
		header.setInt(8, h);
		try {
			if (!shown) {
				shown = true;
				showWindow.invokeInt(new Object[] {hwnd, SW_SHOWNOACTIVATE});
			}
			Pointer dc = (Pointer) getDC.invoke(Pointer.class, new Object[] {hwnd});
			if (dc == null) {
				return;
			}
			try {
				setDIBitsToDevice.invokeInt(new Object[] {dc, 0, 0, w, h, 0, 0, 0, h, pixels, header, 0});
			} finally {
				releaseDC.invokeInt(new Object[] {hwnd, dc});
			}
		} catch (RuntimeException | LinkageError t) {
			throw new IllegalStateException("could not paint the overlay", t);
		}
	}
	void hide() {
		if (!shown) {
			return;
		}
		shown = false;
		try {
			showWindow.invokeInt(new Object[] {hwnd, SW_HIDE});
		} catch (RuntimeException | LinkageError t) {
			throw new IllegalStateException("could not hide the overlay", t);
		}
	}
	void destroy() {
		destroyWindow.invokeInt(new Object[] {hwnd});
		pixels = null;
		bgra = new int[0];
	}
}
