package nz.temple.client.util;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import nz.temple.client.mixin.GameModeCarriedItemInvoker;
import nz.temple.client.mixin.KeyMappingClickAccessor;
import nz.temple.client.mixin.LocalPlayerSprintAccessor;

public final class AccessorCompat {
	private AccessorCompat() {
	}

	/** Stand-in for the old by-name field lookups, which fail on obfuscated (production) Minecraft; the modules now use
	 *  MinecraftClickDelayAccessor instead, so this always reports the field as missing. */
	public static java.lang.reflect.Field noField() throws NoSuchFieldException {
		throw new NoSuchFieldException("use MinecraftClickDelayAccessor");
	}

	public static int getClickCount(KeyMapping k) {
		return ((KeyMappingClickAccessor) k).temple$getClickCount();
	}

	public static void setClickCount(KeyMapping k, int v) {
		((KeyMappingClickAccessor) k).temple$setClickCount(v);
	}

	public static void addClick(KeyMapping k) {
		setClickCount(k, getClickCount(k) + 1);
	}

	public static void ensureHasSentCarriedItem(MultiPlayerGameMode gm) {
		((GameModeCarriedItemInvoker) gm).temple$ensureHasSentCarriedItem();
	}

	public static void setWasSprinting(LocalPlayer p, boolean v) {
		((LocalPlayerSprintAccessor) p).temple$setWasSprinting(v);
	}
}
