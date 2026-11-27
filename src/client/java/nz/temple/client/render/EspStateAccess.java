package nz.temple.client.render;

public interface EspStateAccess {
	int temple$espFill();

	void temple$setEspFill(int argb);

	boolean temple$chams();

	void temple$setChams(boolean on);

	boolean temple$self();

	void temple$setSelf(boolean self);

	boolean temple$mine();

	void temple$setMine(boolean mine);

	int temple$friendTint();

	void temple$setFriendTint(int argb);
	int temple$entityId();

	void temple$setEntityId(int id);
}
