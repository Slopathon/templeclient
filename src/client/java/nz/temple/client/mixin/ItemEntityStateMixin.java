package nz.temple.client.mixin;

import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import nz.temple.client.render.ItemPhysicsState;

@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityStateMixin implements ItemPhysicsState {
	@Unique
	private int temple$id;
	@Unique
	private boolean temple$grounded;
	@Unique
	private boolean temple$inWater;
	@Unique
	private float temple$speed;

	@Override
	public int temple$id() {
		return temple$id;
	}

	@Override
	public boolean temple$grounded() {
		return temple$grounded;
	}

	@Override
	public boolean temple$inWater() {
		return temple$inWater;
	}

	@Override
	public float temple$speed() {
		return temple$speed;
	}

	@Override
	public void temple$setPhysics(int id, boolean grounded, boolean inWater, float speed) {
		temple$id = id;
		temple$grounded = grounded;
		temple$inWater = inWater;
		temple$speed = speed;
	}
}
