package nz.temple.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.status.ServerStatus;
import nz.temple.client.TempleNzClient;
import nz.temple.client.modules.client.HudModule;

public final class ServerRow {
	private static final int RIGHT_COLUMN = 108;
	private static final int ICON = 32;
	private ServerRow() {
	}
	public static void draw(GuiGraphicsExtractor g, ServerData server, int x, int y, int w, int h, boolean hovered, boolean selected) {
		Minecraft mc = Minecraft.getInstance();
		int gold = HudModule.GOLD & 0xFFFFFF;
		int light = HudModule.GOLD_LIGHT & 0xFFFFFF;
		plate(g, x, y, w, h, hovered, selected, gold, light);
		int textX = x + ICON + 14;
		int textRight = x + w - RIGHT_COLUMN;
		int room = Math.max(20, textRight - textX - 8);

		String name = server.name;
		if (mc.font.width(name) > room) {
			name = mc.font.plainSubstrByWidth(name, room - 6) + "…";
		}
		g.text(mc.font, Component.literal(name), textX, y + 7, selected ? 0xFFFFE3A1 : 0xFFF2F4F8, false);
		Component motd = server.motd != null && !server.motd.getString().isBlank()
				? server.motd : Component.literal(nz.temple.client.modules.client.StreamerMode.serverAddress(server.ip));
		String flat = tidy(motd.getString());
		if (flat.isBlank()) {
			flat = nz.temple.client.modules.client.StreamerMode.serverAddress(server.ip);
		}
		if (mc.font.width(flat) > room) {
			flat = mc.font.plainSubstrByWidth(flat, room - 6) + "…";
		}
		g.text(mc.font, Component.literal(flat), textX, y + 20, 0xFF8E97A8, false);
		int right = x + w - 10;
		ServerStatus.Players players = server.players;
		if (players != null) {

			String count = players.online() + " / " + players.max();
			g.text(mc.font, Component.literal(count), right - mc.font.width(count), y + 7, 0xFFDDE3EC, false);
			float fullness = players.max() <= 0 ? 0 : Math.min(1, players.online() / (float) players.max());
			int barW = 62;
			int barX = right - barW;
			g.fill(barX, y + 18, barX + barW, y + 20, argb(0.3f, 0x000000));
			g.fill(barX, y + 18, barX + Math.round(barW * fullness), y + 20, argb(0.95f, fullness > 0.92f ? 0xFF6B6B : gold));
		} else if (server.status != null && !server.status.getString().isBlank()) {
			String status = server.status.getString();
			g.text(mc.font, Component.literal(status), right - mc.font.width(status), y + 7, 0xFF8E97A8, false);
		}
		long ping = server.ping;
		if (ping > 0) {
			int bars = ping < 80 ? 5 : ping < 150 ? 4 : ping < 250 ? 3 : ping < 400 ? 2 : 1;
			int colour = ping < 150 ? 0x5EE07A : ping < 300 ? 0xE9B949 : 0xFF6B6B;
			int bx = right - 26;
			for (int i = 0; i < 5; i++) {
				int bh = 3 + i * 2;
				int bar = bx + i * 5;
				g.fill(bar, y + h - 8 - bh, bar + 3, y + h - 8, argb(i < bars ? 0.95f : 0.16f, i < bars ? colour : 0xFFFFFF));
			}
			String ms = ping + "ms";
			g.text(mc.font, Component.literal(ms), bx - 7 - mc.font.width(ms), y + h - 16, argb(0.85f, colour), false);
		}
	}
	private static void plate(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hovered, boolean selected,
			int gold, int light) {
		GlassRenderer glass = TempleNzClient.glass();
		float r = Math.min(12, h / 2f);
		if (glass != null && glass.available() && glass.hasRoom(1, 4)
				&& g.containsPointInScissor(x, y) && g.containsPointInScissor(x + w - 1, y + h - 1)) {
			glass.placeLayer(g);
			glass.glass(x + 1, y + 1, w - 2, h - 2, r, 1f);
			glass.rect(x + 1, y + 1, w - 2, h - 2, r, argb(selected ? 0.20f : hovered ? 0.11f : 0.04f, gold), 0, 0, null);
			glass.rect(x + 1, y + 1, w - 2, h - 2, r, argb(selected ? 0.7f : hovered ? 0.35f : 0.14f, light), 1f, 0, null);
			if (selected) {
				glass.rect(x + 4, y + h * 0.3f, 2.5f, h * 0.4f, 1.25f, argb(0.95f, gold), 0, 0, null);
			}
			return;
		}
		g.fillGradient(x, y, x + w, y + h, argb(selected ? 0.5f : 0.32f, 0x15110A), argb(selected ? 0.42f : 0.24f, 0x08070B));
		g.fill(x, y, x + w, y + 1, argb(0.14f, light));
	}
	private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇғɢʜɪᴊ"
			+ "ᴋʟᴍɴᴏᴘǫʀѕᴛᴜᴠᴡхʏᴢ";

	private static String tidy(String raw) {
		StringBuilder out = new StringBuilder(raw.length());
		boolean space = false;
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c == '§') {
				i++;
				continue;
			}
			if (c == '\n' || c == '\r' || c == '\t' || c == ' ') {
				space = true;
				continue;
			}
			int small = SMALL_CAPS.indexOf(c);
			if (small >= 0) {
				c = (char) ('A' + small);
			} else if (c >= 'Ａ' && c <= 'ｚ') {
				c = (char) (c - 0xfee0);
			} else if (c < ' ' || c >= '─' && c <= '➿' || c >= '' && c <= '') {
				continue;
			}
			if (space && out.length() > 0) {
				out.append(' ');
			}
			space = false;
			out.append(c);
		}
		return out.toString().trim();
	}
	private static int argb(float alpha, int rgb) {
		return (Math.round(Math.max(0, Math.min(1, alpha)) * 255) << 24) | rgb;
	}
}
