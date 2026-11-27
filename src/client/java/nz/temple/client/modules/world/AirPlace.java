package nz.temple.client.modules.world;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.util.Place;
import nz.temple.event.TickEvent;
import nz.temple.module.Category;
import nz.temple.module.Module;
import nz.temple.setting.NumberSetting;

public final class AirPlace extends Module {
	private final NumberSetting range;
	private final NumberSetting delay;
	private long lastPlace;
	public AirPlace() {
		super("Air Place", Category.WORLD, "Places blocks in the air along your crosshair, up to your reach.");
		range = add(new NumberSetting("Range", 4.5, 1, 6, 0.5, " b")
				.describe("How far along your view to look for a spot."));
		delay = add(new NumberSetting("Delay", 4, 0, 20, 1, " ticks")
				.describe("Ticks between placements. Raise it if a server rejects fast placement."));
		on(TickEvent.class, e -> tick());
	}
	private void tick() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.gameMode == null || !mc.options.keyUse.isDown() || mc.gui.screen() != null) {
			return;
		}
		if (player.tickCount - lastPlace < delay.get()) {
			return;
		}
		if (!(player.getMainHandItem().getItem() instanceof net.minecraft.world.item.BlockItem)) {
			return;
		}
		BlockPos target = findSpot(mc, player);
		if (target != null && Place.placeAt(player, target)) {
			lastPlace = player.tickCount;
		}
	}
	private BlockPos findSpot(Minecraft mc, LocalPlayer player) {
		if (mc.hitResult instanceof BlockHitResult hit && hit.getType() != HitResult.Type.MISS) {
			BlockPos against = hit.getBlockPos().relative(hit.getDirection());
			if (Place.replaceable(mc.level, against)) {
				return against;
			}
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		double max = range.get();
		BlockPos previous = null;
		for (double d = 0.5; d <= max; d += 0.25) {
			BlockPos pos = BlockPos.containing(eye.add(look.scale(d)));
			if (pos.equals(previous)) {
				continue;
			}
			previous = pos;
			if (!Place.replaceable(mc.level, pos)) {
				continue;
			}
			for (net.minecraft.core.Direction face : net.minecraft.core.Direction.values()) {
				if (Place.solid(mc.level, pos.relative(face))) {
					return pos;
				}
			}
		}
		return null;
	}
}
