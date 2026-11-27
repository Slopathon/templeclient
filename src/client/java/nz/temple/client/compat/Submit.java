package nz.temple.client.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jspecify.annotations.Nullable;

/** Submitting entity models for drawing, across the collector signature changes between versions. */
public final class Submit {
	private Submit() {
	}

	/**
	 * A model with a tint, an optional sprite (null: the render type's texture as is) and an outline colour. Up to 26.2
	 * the call also took a block-cracking overlay, which Temple never passes; 26.3 dropped it.
	 */
	public static <S> void model(OrderedSubmitNodeCollector out, Model<? super S> model, S state, PoseStack pose, RenderType type,
			int light, int overlay, int color, @Nullable TextureAtlasSprite sprite, int outline) {
		//? if >=26.3 {
		/*out.submitModel(model, state, pose, type, light, overlay, color, sprite, outline);
		*///?} else {
		out.submitModel(model, state, pose, type, light, overlay, color, sprite, outline, null);
		//?}
	}
}
