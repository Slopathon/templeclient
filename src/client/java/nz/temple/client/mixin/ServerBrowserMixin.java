package nz.temple.client.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.client.MenuModule;
import nz.temple.client.render.ServerRow;

@Mixin(ServerSelectionList.OnlineServerEntry.class)
public abstract class ServerBrowserMixin {
	@Shadow
	@org.spongepowered.asm.mixin.Final
	private FaviconTexture icon;
	@Shadow
	private byte[] lastIconBytes;

	@Shadow
	protected abstract void extractIcon(GuiGraphicsExtractor g, int x, int y, Identifier icon);

	@Shadow
	protected abstract boolean uploadServerIcon(byte[] bytes);

	@Inject(method = "extractContent",
			at = @At(value = "INVOKE", ordinal = 0,
					target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"),
			cancellable = true)
	private void temple$row(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float delta, CallbackInfo ci) {
		if (!MenuModule.glassWidgets()) {
			return;
		}
		ServerSelectionList.OnlineServerEntry entry = (ServerSelectionList.OnlineServerEntry) (Object) this;
		EntryGeometryAccessor bounds = (EntryGeometryAccessor) entry;
		int x = bounds.temple$x();
		int y = bounds.temple$y();
		int w = bounds.temple$width();
		int h = bounds.temple$height();
		if (w < 60 || h < 20) {
			return;
		}
		byte[] iconBytes = entry.getServerData().getIconBytes();
		if (!java.util.Arrays.equals(iconBytes, lastIconBytes)) {
			if (uploadServerIcon(iconBytes)) {
				lastIconBytes = iconBytes;
			} else {
				entry.getServerData().setIconBytes(null);
			}
		}
		ServerRow.draw(g, entry.getServerData(), x, y, w, h, hovered, false);
		extractIcon(g, x + 6, y + (h - 32) / 2, icon.textureLocation());
		ci.cancel();
	}
}
