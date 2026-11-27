package nz.temple.client.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.Place;
import nz.temple.event.TickEvent;
import nz.temple.setting.BoolSetting;
import nz.temple.setting.ModeSetting;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Surround extends PlaceModule {
	private final ModeSetting shape;
	private final BoolSetting center;
	private final BoolSetting floor;
	private final BoolSetting extend;
	private final BoolSetting head;
	private final BoolSetting disableOnJump;
	private final BoolSetting disableOnLeave;
	private final BoolSetting disableWhenDone;
	private BlockPos start;

	public Surround() {
		super("Surround", "Puts obsidian around your feet so enemy crystals can't hit your legs.");
		group();
		shape = add(new ModeSetting("Shape", List.of("Normal", "Double", "Anti-city"), "Normal")
				.describe("Normal places one block on each side. Double makes it two blocks high. Anti-city adds a second outer ring so "
						+ "broken blocks cannot be used for crystals."));
		center = add(new BoolSetting("Center", true)
				.describe("Moves you to the centre of your block first so the ring fits."));
		floor = add(new BoolSetting("Floor", true).describe("Also fills the block under your feet if it is missing."));
		extend = add(new BoolSetting("Extend", true)
				.describe("When you stand across several blocks, surrounds all of them."));
		head = add(new BoolSetting("Self trap", false)
				.describe("Also covers the block above your head."));
		disableOnJump = add(new BoolSetting("Off on jump", true).describe("Turns off when you jump."));
		disableOnLeave = add(new BoolSetting("Off when moved", true)
				.describe("Turns off when you leave your starting block."));
		disableWhenDone = add(new BoolSetting("Off when done", false)
				.describe("Turns off once complete instead of replacing broken blocks."));
		on(TickEvent.class, e -> tick());
	}

	@Override
	protected void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		start = player.blockPosition();
		if (center.get() && player.onGround()) {
			Vec3 middle = Vec3.atBottomCenterOf(start);
			player.setPos(middle.x, player.getY(), middle.z);
			player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
		}
	}

	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.gameMode == null) {
			return;
		}
		if (disableOnJump.get() && mc.options.keyJump.isDown()
				|| disableOnLeave.get() && start != null && !player.blockPosition().equals(start) && player.getY() - start.getY() > -0.5) {
			setEnabled(false);
			return;
		}
		if (!hasBlocks(player)) {
			return;
		}
		List<BlockPos> wanted = positions(player);
		boolean missing = false;
		for (BlockPos p : wanted) {
			if (Place.replaceable(mc.level, p)) {
				missing = true;
				break;
			}
		}
		if (!missing) {
			if (disableWhenDone.get()) {
				setEnabled(false);
			}
			return;
		}
		placeAll(player, wanted);
	}

	private List<BlockPos> positions(LocalPlayer player) {
		int y = (int) Math.floor(player.getY() + 0.2);
		Set<BlockPos> feet = new LinkedHashSet<>();
		AABB box = player.getBoundingBox();
		if (extend.get()) {
			for (int x = (int) Math.floor(box.minX + 0.001); x <= (int) Math.floor(box.maxX - 0.001); x++) {
				for (int z = (int) Math.floor(box.minZ + 0.001); z <= (int) Math.floor(box.maxZ - 0.001); z++) {
					feet.add(new BlockPos(x, y, z));
				}
			}
		} else {
			feet.add(BlockPos.containing(player.getX(), y, player.getZ()));
		}
		List<BlockPos> out = new ArrayList<>();
		if (floor.get()) {
			for (BlockPos f : feet) {
				out.add(f.below());
			}
		}
		Set<BlockPos> ring = new LinkedHashSet<>();
		for (BlockPos f : feet) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos side = f.relative(d);
				if (!feet.contains(side)) {
					if (Place.replaceable(Minecraft.getInstance().level, side.below()) && !airPlace.get()) {
						out.add(side.below());
					}
					ring.add(side);
				}
			}
		}
		out.addAll(ring);
		if (shape.is("Double")) {
			for (BlockPos r : ring) {
				out.add(r.above());
			}
		}
		if (shape.is("Anti-city")) {
			for (BlockPos r : ring) {
				for (Direction d : Direction.Plane.HORIZONTAL) {
					BlockPos outer = r.relative(d);
					if (!feet.contains(outer) && !ring.contains(outer)) {
						out.add(outer);
					}
				}
			}
		}
		if (head.get()) {
			for (BlockPos f : feet) {
				out.add(f.above(2));
			}
		}
		return out;
	}
}
