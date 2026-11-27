package nz.temple.client.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.render.WorldEsp;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ColorSetting;
import nz.temple.setting.ModeSetting;
import nz.temple.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;

public final class Blink extends Module {
	private final nz.temple.setting.BoolSetting legit;
	private static Blink instance;
	private final ModeSetting hold;
	private final ModeSetting release;
	private final NumberSetting pulseTicks;
	private final NumberSetting maxPackets;
	private final ModeSetting onRelease;
	private final BoolSetting render;
	private final ColorSetting color;
	private final List<Packet<?>> held = new ArrayList<>();
	private Vec3 start;
	private int ticks;
	private boolean flushing;
	private int legitHold = 4;
	public Blink() {
		super("Blink", Category.MOVEMENT, "Holds back your movement packets so the server sees you standing still, then sends them all at "
				+ "once.");
		legit = add(new nz.temple.setting.BoolSetting("Legit", false)
				.describe("Holds for a few ticks at most (under a third of a second), so it looks like a normal lag spike rather than a "
						+ "teleport."));
		instance = this;
		hold = add(new ModeSetting("Hold", List.of("Movement", "Movement + actions"), "Movement")
				.describe("Movement holds position and rotation. Movement + actions also holds sprint, sneak and input changes."));
		release = add(new ModeSetting("Release", List.of("On disable", "Pulse", "Limit"), "On disable")
				.describe("On disable sends everything when you turn it off. Pulse sends every few ticks. Limit sends once the queue is "
						+ "full."));
		pulseTicks = add(new NumberSetting("Pulse every", 20, 2, 100, 1, " ticks")
				.visibleWhen(() -> release.is("Pulse")).describe("Release interval in Pulse mode."));
		maxPackets = add(new NumberSetting("Max packets", 60, 5, 400, 5, "")
				.visibleWhen(() -> release.is("Limit")).describe("Packets to hold before releasing."));
		onRelease = add(new ModeSetting("On disable", List.of("Send", "Discard"), "Send")
				.describe("Send moves you to your current position. Discard drops the held packets and puts you back where you started."));
		render = add(new BoolSetting("Show start", true).describe("Shows where the server thinks you are."));
		color = add(new ColorSetting("Color", 0xFFE8C872).themed().describe("Marker colour.").visibleWhen(render::get));
		hideWhenLegit(legit, release, pulseTicks, maxPackets);
		on(TickEvent.class, e -> tick());
	}
	public static boolean intercept(Packet<?> packet) {
		Blink b = instance;
		if (b == null || !b.isEnabled() || b.flushing || Minecraft.getInstance().player == null) {
			return false;
		}
		boolean movement = packet instanceof ServerboundMovePlayerPacket;
		boolean action = packet instanceof ServerboundPlayerCommandPacket || packet instanceof ServerboundPlayerInputPacket;
		if (movement || action && b.hold.is("Movement + actions")) {
			synchronized (b.held) {
				b.held.add(packet);
			}
			return true;
		}
		return false;
	}
	@Override
	protected void onEnable() {
		LocalPlayer player = Minecraft.getInstance().player;
		start = player == null ? null : player.position();
		ticks = 0;
	}
	@Override
	protected void onDisable() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (onRelease.is("Discard") && player != null && start != null) {
			synchronized (held) {
				held.clear();
			}
			player.setPos(start);
			player.setDeltaMovement(Vec3.ZERO);
		} else {
			flush();
		}
		start = null;
	}
	private void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			synchronized (held) {
				held.clear();
			}
			return;
		}
		ticks++;
		int size;
		synchronized (held) {
			size = held.size();
		}
		if (legit.get() && ticks >= legitHold) {
			flush();
			start = player.position();
			ticks = 0;
			legitHold = nz.temple.client.util.Human.ticks(2, 6);
			return;
		}
		if (release.is("Pulse") && ticks % Math.max(1, (int) Math.round(pulseTicks.get())) == 0
				|| release.is("Limit") && size >= maxPackets.get()) {
			flush();
			start = player.position();
		}
	}
	private void flush() {
		LocalPlayer player = Minecraft.getInstance().player;
		List<Packet<?>> out;
		synchronized (held) {
			out = new ArrayList<>(held);
			held.clear();
		}
		if (player == null) {
			return;
		}
		flushing = true;
		try {
			for (Packet<?> p : out) {
				player.connection.send(p);
			}
		} finally {
			flushing = false;
		}
	}
	public void collect(List<WorldEsp.Box> out) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (isEnabled() && render.get() && start != null && player != null) {
			int c = color.get();
			AABB box = player.getBoundingBox().move(start.subtract(player.position()));
			out.add(new WorldEsp.Box(box, 0xFF000000 | (c & 0xFFFFFF), (40 << 24) | (c & 0xFFFFFF), 0.01f));
		}
	}
}
