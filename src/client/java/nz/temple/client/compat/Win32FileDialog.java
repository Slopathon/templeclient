package nz.temple.client.compat;

import com.sun.jna.Function;
import com.sun.jna.Memory;

import java.util.List;

final class Win32FileDialog {
	private static final int OFN_NOCHANGEDIR = 0x8;
	private static final int OFN_PATHMUSTEXIST = 0x800;
	private static final int OFN_FILEMUSTEXIST = 0x1000;
	private static final int OFN_EXPLORER = 0x80000;
	private static final int MAX_PATH_CHARS = 4096;
	private Win32FileDialog() {
	}

	static String open(String title, List<String> extensions, String label) {
		if (!System.getProperty("os.name", "").startsWith("Windows")) {
			return null;
		}
		StringBuilder filter = new StringBuilder();
		if (!extensions.isEmpty()) {
			String patterns = String.join(";", extensions.stream().map(e -> "*." + e).toList());
			filter.append(label == null ? patterns : label).append('\0').append(patterns).append('\0');
		}
		filter.append("All files").append('\0').append("*.*").append('\0').append('\0');
		Memory filterText = wide(filter.toString());
		Memory titleText = wide(title + '\0');
		Memory file = new Memory(MAX_PATH_CHARS * 2L);
		file.clear();
		Memory ofn = new Memory(152);
		ofn.clear();
		ofn.setInt(0, 152);
		ofn.setPointer(24, filterText);
		ofn.setInt(44, 1);
		ofn.setPointer(48, file);
		ofn.setInt(56, MAX_PATH_CHARS);
		ofn.setPointer(88, titleText);
		ofn.setInt(96, OFN_EXPLORER | OFN_FILEMUSTEXIST | OFN_PATHMUSTEXIST | OFN_NOCHANGEDIR);
		int ok = Function.getFunction("comdlg32", "GetOpenFileNameW").invokeInt(new Object[] {ofn});
		return ok == 0 ? null : file.getWideString(0);
	}
	private static Memory wide(String text) {
		Memory m = new Memory((text.length() + 1) * 2L);
		m.clear();
		for (int i = 0; i < text.length(); i++) {
			m.setChar(i * 2L, text.charAt(i));
		}
		return m;
	}
}
