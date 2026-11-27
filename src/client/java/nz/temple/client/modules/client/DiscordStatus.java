package nz.temple.client.modules.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.TextSetting;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

public final class DiscordStatus extends Module {
	private static final String APPLICATION = "1553288861453656104";
	private static final String SITE = "https://temple.nz";
	private static final String LOGO = "https://temple.nz/assets/img/mark.png";
	private static final long INTERVAL_MILLIS = 4_000;
	private static final long RETRY_MILLIS = 15_000;
	private final long started = System.currentTimeMillis() / 1000;
	private final TextSetting details;
	private final TextSetting state;
	private final BoolSetting elapsed;
	private final BoolSetting image;
	private final TextSetting imageLink;
	private final TextSetting imageText;
	private final BoolSetting button;
	private final TextSetting buttonText;
	private final TextSetting buttonLink;
	private volatile String wanted;
	private Thread worker;
	public DiscordStatus() {
		super("Custom Discord Status", Category.CLIENT, "Shows a Temple status on your Discord profile. Turn off to show plain "
				+ "Minecraft.");
		details = add(new TextSetting("First line", "Using Temple.nz", 128, "Using Temple.nz")
				.describe("The first line of the status. Leave empty to hide it."));
		state = add(new TextSetting("Second line", "", 128, "Empty")
				.describe("The second line of the status. Leave empty to hide it."));
		elapsed = add(new BoolSetting("Show time played", true)
				.describe("Shows how long the game has been open."));
		group();
		image = add(new BoolSetting("Show image", true)
				.describe("Shows an image beside the status."));
		imageLink = add(new TextSetting("Image link", LOGO, 256, LOGO)
				.describe("A link to the image to show. The Temple logo by default.")
				.visibleWhen(image::get));
		imageText = add(new TextSetting("Image text", "Temple.nz", 128, "Temple.nz")
				.describe("The text shown when the image is hovered.")
				.visibleWhen(image::get));
		group();
		button = add(new BoolSetting("Show button", true)
				.describe("Adds a button to the status that opens a link."));
		buttonText = add(new TextSetting("Button text", "Temple.nz", 32, "Temple.nz")
				.describe("The text on the button.")
				.visibleWhen(button::get));
		buttonLink = add(new TextSetting("Button link", SITE, 256, SITE)
				.describe("The link the button opens.")
				.visibleWhen(button::get));
		addChangeListener(this::sync);
	}
	@Override
	protected void onEnable() {
		sync();
		if (worker == null) {
			worker = new Thread(this::run, "Temple Discord");
			worker.setDaemon(true);
			worker.start();
		}
	}
	@Override
	protected void onDisable() {
		wanted = null;
	}
	private void sync() {
		wanted = isEnabled() ? activity().toString() : null;
	}
	private JsonObject activity() {
		JsonObject activity = new JsonObject();

		String first = details.get().trim();
		String second = state.get().trim();
		if (first.length() >= 2) {
			activity.addProperty("details", first);
		}
		if (second.length() >= 2) {
			activity.addProperty("state", second);
		}
		if (elapsed.get()) {
			JsonObject timestamps = new JsonObject();
			timestamps.addProperty("start", started);
			activity.add("timestamps", timestamps);
		}
		if (image.get() && isLink(imageLink.get())) {
			JsonObject assets = new JsonObject();
			assets.addProperty("large_image", imageLink.get().trim());
			if (imageText.get().trim().length() >= 2) {
				assets.addProperty("large_text", imageText.get().trim());
			}
			activity.add("assets", assets);
		}
		if (button.get() && !buttonText.get().isBlank() && isLink(buttonLink.get())) {
			JsonObject link = new JsonObject();
			link.addProperty("label", buttonText.get().trim());
			link.addProperty("url", buttonLink.get().trim());
			JsonArray buttons = new JsonArray();
			buttons.add(link);
			activity.add("buttons", buttons);
		}
		return activity;
	}
	private static boolean isLink(String text) {
		String v = text.trim().toLowerCase(Locale.ROOT);
		return v.startsWith("https://") || v.startsWith("http://");
	}
	private void run() {
		Pipe pipe = null;
		String shown = null;
		long retryAt = 0;
		while (true) {
			String want = wanted;
			try {
				if (want == null) {
					if (pipe != null) {
						pipe.close();
						pipe = null;
						shown = null;
					}
				} else if (pipe == null) {
					if (System.currentTimeMillis() >= retryAt) {
						retryAt = System.currentTimeMillis() + RETRY_MILLIS;
						pipe = connect();
					}
				}
				if (pipe != null && want != null && !want.equals(shown)) {
					JsonObject args = new JsonObject();
					args.addProperty("pid", ProcessHandle.current().pid());
					args.add("activity", com.google.gson.JsonParser.parseString(want));
					JsonObject command = new JsonObject();
					command.addProperty("cmd", "SET_ACTIVITY");
					command.add("args", args);
					command.addProperty("nonce", UUID.randomUUID().toString());
					pipe.send(1, command.toString());
					pipe.receive();
					shown = want;
				}
			} catch (IOException | RuntimeException e) {
				close(pipe);
				pipe = null;
				shown = null;
			}
			try {
				Thread.sleep(INTERVAL_MILLIS);
			} catch (InterruptedException e) {
				close(pipe);
				return;
			}
		}
	}
	private static Pipe connect() {
		for (int i = 0; i < 10; i++) {
			Pipe pipe = null;
			try {
				pipe = open(i);
				if (pipe == null) {
					continue;
				}
				JsonObject hello = new JsonObject();
				hello.addProperty("v", 1);
				hello.addProperty("client_id", APPLICATION);
				pipe.send(0, hello.toString());
				pipe.receive();
				return pipe;
			} catch (IOException | RuntimeException e) {
				close(pipe);
			}
		}
		return null;
	}
	private static Pipe open(int index) throws IOException {
		String name = "discord-ipc-" + index;
		if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
			return new FilePipe(new RandomAccessFile("\\\\.\\pipe\\" + name, "rw"));
		}
		for (String variable : new String[] {"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
			String dir = System.getenv(variable);
			if (dir != null && Files.exists(Path.of(dir, name))) {
				return socket(Path.of(dir, name));
			}
		}
		return Files.exists(Path.of("/tmp", name)) ? socket(Path.of("/tmp", name)) : null;
	}
	private static Pipe socket(Path path) throws IOException {
		SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
		try {
			channel.connect(UnixDomainSocketAddress.of(path));
		} catch (IOException | RuntimeException e) {
			channel.close();
			throw e;
		}
		return new SocketPipe(channel);
	}
	private static void close(Closeable pipe) {
		if (pipe != null) {
			try {
				pipe.close();
			} catch (IOException ignored) {
			}
		}
	}
	private abstract static class Pipe implements Closeable {
		abstract void write(byte[] bytes) throws IOException;
		abstract void read(byte[] bytes) throws IOException;
		final void send(int op, String json) throws IOException {
			byte[] body = json.getBytes(StandardCharsets.UTF_8);
			ByteBuffer frame = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
			frame.putInt(op).putInt(body.length).put(body);
			write(frame.array());
		}
		final void receive() throws IOException {
			byte[] head = new byte[8];
			read(head);
			ByteBuffer header = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
			int op = header.getInt();
			int length = header.getInt();
			if (length < 0 || length > 1 << 20) {
				throw new IOException("bad frame");
			}
			read(new byte[length]);
			if (op == 2) {
				throw new EOFException("closed");
			}
		}
	}
	private static final class FilePipe extends Pipe {
		private final RandomAccessFile file;

		FilePipe(RandomAccessFile file) {
			this.file = file;
		}
		@Override
		void write(byte[] bytes) throws IOException {
			file.write(bytes);
		}
		@Override
		void read(byte[] bytes) throws IOException {
			file.readFully(bytes);
		}
		@Override
		public void close() throws IOException {
			file.close();
		}
	}
	private static final class SocketPipe extends Pipe {
		private final SocketChannel channel;

		SocketPipe(SocketChannel channel) {
			this.channel = channel;
		}
		@Override
		void write(byte[] bytes) throws IOException {
			ByteBuffer buffer = ByteBuffer.wrap(bytes);
			while (buffer.hasRemaining()) {
				channel.write(buffer);
			}
		}
		@Override
		void read(byte[] bytes) throws IOException {
			ByteBuffer buffer = ByteBuffer.wrap(bytes);
			while (buffer.hasRemaining()) {
				if (channel.read(buffer) < 0) {
					throw new EOFException("closed");
				}
			}
		}
		@Override
		public void close() throws IOException {
			channel.close();
		}
	}
}
