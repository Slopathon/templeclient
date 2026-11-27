package nz.temple.client.cosmetic.aura;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.cosmetic.Anchor;
import nz.temple.client.render.Fx;
import nz.temple.client.render.Meshes;

public final class AuraContext {
	public final Fx fx;
	public final Meshes mesh;
	public final Player player;
	public final float partial;
	public final Anchor anchor;
	public final double time;
	public final double dt;
	public final double size;
	public final double speed;
	public final Vec3 feet;
	public final boolean cameraInHead;
	public final boolean detailed;
	public final boolean far;
	public final BlockPos lightPos;

	public AuraContext(Fx fx, Player player, float partial, double time, double dt, double size, double speed, boolean detailed) {
		this.fx = fx;
		this.mesh = fx.mesh();
		this.player = player;
		this.partial = partial;
		this.anchor = Anchor.of(player, partial);
		this.time = time;
		this.dt = dt;
		this.size = size;
		this.speed = speed;
		this.feet = player.getPosition(partial);
		this.cameraInHead = Anchor.cameraInHead(player);
		this.detailed = detailed;
		this.lightPos = BlockPos.containing(player.getEyePosition(partial));
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		this.far = mc.player != null && player != mc.player && player.distanceToSqr(mc.player) > 12 * 12;
	}

	public nz.temple.cosmetic.Model model(String name) {
		return nz.temple.client.render.ModelCache.get(name, far);
	}

	public double moving() {
		return Math.min(1, Math.hypot(player.getX() - player.xo, player.getZ() - player.zo) * 4.5);
	}
}
