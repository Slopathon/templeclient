package nz.temple.client.render;

public interface ItemPhysicsState {
	int temple$id();

	boolean temple$grounded();

	boolean temple$inWater();

	float temple$speed();

	void temple$setPhysics(int id, boolean grounded, boolean inWater, float speed);
}
