package nz.temple.client.mixin;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import nz.temple.client.render.EspStateAccess;

@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements EspStateAccess {
	@Unique private int temple$espFill;
	@Unique private boolean temple$chams;
	@Unique private boolean temple$self;
	@Unique private boolean temple$mine;
	@Unique private int temple$friendTint;
	@Unique private int temple$entityId;

	@Override
	public int temple$entityId() {
		return temple$entityId;
	}

	@Override
	public void temple$setEntityId(int id) {
		temple$entityId = id;
	}

	@Override
	public int temple$friendTint() {
		return temple$friendTint;
	}

	@Override
	public void temple$setFriendTint(int argb) {
		temple$friendTint = argb;
	}

	@Override
	public boolean temple$self() {
		return temple$self;
	}

	@Override
	public void temple$setSelf(boolean self) {
		temple$self = self;
	}

	@Override
	public boolean temple$mine() {
		return temple$mine;
	}

	@Override
	public void temple$setMine(boolean mine) {
		temple$mine = mine;
	}

	@Override
	public boolean temple$chams() {
		return temple$chams;
	}

	@Override
	public void temple$setChams(boolean on) {
		temple$chams = on;
	}

	@Override
	public int temple$espFill() {
		return temple$espFill;
	}

	@Override
	public void temple$setEspFill(int argb) {
		temple$espFill = argb;
	}
}
