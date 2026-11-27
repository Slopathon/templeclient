package nz.temple.client.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import nz.temple.module.Category;
import nz.temple.module.Module;

public final class CoordSnapper extends Module {
	public CoordSnapper() { super("Coord Snapper", Category.PLAYER, "Copies your block coordinates and dimension to the clipboard. Bind it "
			+ "to a key for quick use."); }
	@Override protected void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.level != null) {
			var p = mc.player.blockPosition();
			if (nz.temple.client.modules.client.StreamerMode.hidesAlerts()) {
				nz.temple.client.compat.Players.message(mc.player, Component.literal("§8[§bTemple§8] §fCoord Snapper §7not copied: " + nz.temple.client.modules.client.StreamerMode.HIDDEN));
			} else {
				String text = p.getX() + ", " + p.getY() + ", " + p.getZ() + " (" + mc.level.dimension().identifier() + ")";
				mc.keyboardHandler.setClipboard(text);
				nz.temple.client.compat.Players.message(mc.player, Component.literal("§8[§bTemple§8] §fCopied §7" + text));
			}
		}
		setEnabled(false);
	}
}
