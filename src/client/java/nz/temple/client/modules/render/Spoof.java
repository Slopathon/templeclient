package nz.temple.client.modules.render;

import net.minecraft.client.Minecraft;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.TextSetting;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Spoof extends Module {
	private static final Pattern MONEY = Pattern.compile("(\\$\\s*)?(\\d[\\d,]*(?:\\.\\d+)?\\s*[KkMmBbTtQq]?)");
	private static volatile Spoof instance;
	private static boolean inSidebar;
	private static boolean afterDollar;
	private static final Pattern PAY = Pattern.compile("^pay\\s+(\\S+)\\s+\\$?([\\d.,]+\\s*[KkMmBbTt]?)\\s*$");
	private final BoolSetting money;
	private final TextSetting fakeMoney;
	private final BoolSetting fakePay;
	public Spoof() {
		super("Spoof", Category.RENDER, "Shows a fake balance on your screen in the sidebar and tab list, and can fake /pay messages in "
				+ "chat. The server and other players still see your real balance.");
		money = add(new BoolSetting("Spoof money", true)
				.describe("Replaces your balance in the sidebar and tab list, where DonutSMP shows \"$ 1.5B\"."));
		fakeMoney = add(new TextSetting("Fake money", "999.9B", 16, "amount")
				.describe("Balance to show, without the $, for example 250M or 1.2B.")
				.visibleWhen(money::get));
		fakePay = add(new BoolSetting("Fake payment", false)
				.describe("/pay <player> <amount> is not sent. Chat shows \"You paid <player> $<amount>\" as DonutSMP does, and the amount is "
						+ "taken from the fake balance. No one is paid and no one else sees it."));
		net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_COMMAND.register(this::pay);
		instance = this;
	}
	public static void enterSidebar() {
		inSidebar = true;
		afterDollar = false;
	}
	public static void leaveSidebar() {
		inSidebar = false;
		afterDollar = false;
	}

	public static String apply(String s) {
		if (s == null || s.isEmpty()) {
			return s;
		}
		String out = nz.temple.client.modules.client.StreamerMode.replaceName(s);
		Spoof m = instance;
		if (m != null && m.isEnabled() && inSidebar && m.money.get() && !m.fakeMoney.get().isEmpty()) {
			out = money(out, m.fakeMoney.get());
		}
		return out;
	}
	private boolean pay(String command) {
		if (!isEnabled() || !fakePay.get()) {
			return true;
		}
		Matcher m = PAY.matcher(command.trim());
		if (!m.matches()) {
			return true;
		}
		double amount = parse(m.group(2));
		Minecraft mc = Minecraft.getInstance();
		if (amount <= 0 || mc.player == null) {
			return true;
		}
		var green = net.minecraft.network.chat.Style.EMPTY.withColor(net.minecraft.ChatFormatting.GREEN);
		nz.temple.client.compat.Players.message(mc.player, net.minecraft.network.chat.Component.literal("You paid " + m.group(1) + " ")
				.append(net.minecraft.network.chat.Component.literal("$" + format(amount)).withStyle(green)));
		if (money.get()) {
			double balance = parse(fakeMoney.get());
			fakeMoney.set(format(Math.max(0, balance - amount)));
		}
		return false;
	}
	static double parse(String text) {
		String t = text.trim().replace(",", "").replace("$", "").toUpperCase(java.util.Locale.ROOT);
		if (t.isEmpty()) {
			return 0;
		}
		double mul = switch (t.charAt(t.length() - 1)) {
			case 'K' -> 1e3;
			case 'M' -> 1e6;
			case 'B' -> 1e9;
			case 'T' -> 1e12;
			default -> 1;
		};
		if (mul != 1) {
			t = t.substring(0, t.length() - 1).trim();
		}
		try {
			return Double.parseDouble(t) * mul;
		} catch (NumberFormatException e) {
			return 0;
		}
	}
	static String format(double amount) {
		String[] units = {"", "K", "M", "B", "T"};
		int u = 0;
		double v = amount;
		while (v >= 1000 && u < units.length - 1) {
			v /= 1000;
			u++;
		}
		String number = u == 0 && v == Math.floor(v) ? String.valueOf((long) v)
				: String.format(java.util.Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
		return number + units[u];
	}
	private static String money(String s, String fake) {
		String trimmed = s.trim();
		boolean wasDollar = afterDollar;
		afterDollar = trimmed.endsWith("$");
		if (!s.contains("$") && !wasDollar) {
			return s;
		}
		Matcher matcher = MONEY.matcher(s);
		StringBuilder out = new StringBuilder();
		boolean replaced = false;
		while (matcher.find()) {
			if (matcher.group(1) == null && !wasDollar) {
				continue;
			}
			matcher.appendReplacement(out, Matcher.quoteReplacement((matcher.group(1) == null ? "" : matcher.group(1)) + fake));
			replaced = true;
			wasDollar = false;
		}
		if (!replaced) {
			return s;
		}
		matcher.appendTail(out);
		return out.toString();
	}
}
