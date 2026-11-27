package nz.temple.client.compat;

/**
 * Names of vanilla methods that mixins target and that changed between versions, for mixins already inside a version
 * condition of their own (a condition cannot be nested in another one's commented-out code).
 */
public final class MixinNames {
	/** ItemInHandRenderer's one-arm draw, rendered rather than submitted before 26.1. */
	//? if >=26 {
	public static final String ARM_WITH_ITEM = "submitArmWithItem";
	//?} else {
	/*public static final String ARM_WITH_ITEM = "renderArmWithItem";
	*///?}

	private MixinNames() {
	}
}
