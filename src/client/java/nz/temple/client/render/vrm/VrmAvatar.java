package nz.temple.client.render.vrm;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import nz.temple.TempleNz;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A loaded VRM avatar ready to draw: its model and its textures on the GPU, with its primitives grouped by material
 * in draw order (solid first, see-through last). One avatar is shared by every player wearing it; each wearer has its
 * own {@link VrmRig}.
 */
public final class VrmAvatar implements AutoCloseable {
	private static int next;
	private static Identifier white;

	final VrmModel model;
	private final Identifier[] textures;
	/** Material numbers in draw order, and the primitives of each. */
	final int[] drawOrder;
	final int[][] primsOf;
	private final RenderType[] types;
	private RenderType[] outlineTypes;

	private VrmAvatar(VrmModel model) {
		this.model = model;
		int id = next++;
		textures = new Identifier[model.images.length];
		for (int i = 0; i < textures.length; i++) {
			NativeImage image = model.images[i];
			if (image != null) {
				textures[i] = Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "vrm/" + id + "/" + i);
				Minecraft.getInstance().getTextureManager().register(textures[i], new DynamicTexture(() -> "Temple avatar texture", image));
				model.images[i] = null;
			}
		}
		model.images = null;
		List<Integer> materials = new ArrayList<>();
		List<List<Integer>> prims = new ArrayList<>();
		for (int i = 0; i < model.materials.size(); i++) {
			materials.add(i);
			prims.add(new ArrayList<>());
		}
		for (int p = 0; p < model.prims.size(); p++) {
			prims.get(model.prims.get(p).material()).add(p);
		}
		materials.removeIf(m -> prims.get(m).isEmpty());
		materials.sort(Comparator.comparingInt((Integer m) -> model.materials.get(m).mode() == 2 ? 1 : 0)
				.thenComparingInt(m -> model.materials.get(m).queue()));
		drawOrder = materials.stream().mapToInt(Integer::intValue).toArray();
		primsOf = new int[model.materials.size()][];
		for (int m = 0; m < primsOf.length; m++) {
			primsOf[m] = prims.get(m).stream().mapToInt(Integer::intValue).toArray();
		}
		types = new RenderType[model.materials.size()];
		for (int m = 0; m < types.length; m++) {
			VrmModel.Material mat = model.materials.get(m);
			Identifier tex = mat.image() >= 0 && mat.image() < textures.length && textures[mat.image()] != null ? textures[mat.image()] : white();
			types[m] = mat.mode() == 2 ? RenderTypes.entityTranslucent(tex) : mat.doubleSided() ? noCull(tex) : cull(tex);
		}
	}

	/** Uploads {@code model}'s textures; on the render thread. */
	public static VrmAvatar upload(VrmModel model) {
		return new VrmAvatar(model);
	}

	/** Whether this is a VRM 1.0 avatar, which faces +Z (VRM 0.x avatars face -Z, as the player does). */
	public boolean vrm1() {
		return model.vrm1;
	}

	public String title() {
		return model.title;
	}

	public String author() {
		return model.author;
	}

	public String license() {
		return model.license;
	}

	RenderType type(int material) {
		return types[material];
	}

	/**
	 * The outline hull's drawing for material {@code material}. A cut-out material keeps its texture so the hull has
	 * the same holes (VRoid hides skin under clothes with fully clear texels; a plain hull would outline those hidden
	 * faces as a floating box); the dark vertex colour turns the texture into the outline colour.
	 */
	RenderType outline(int material) {
		if (outlineTypes == null) {
			outlineTypes = new RenderType[model.materials.size()];
		}
		if (outlineTypes[material] == null) {
			VrmModel.Material mat = model.materials.get(material);
			Identifier tex = mat.mode() == 1 && mat.image() >= 0 && mat.image() < textures.length && textures[mat.image()] != null
					? textures[mat.image()] : white();
			outlineTypes[material] = cull(tex);
		}
		return outlineTypes[material];
	}

	private static RenderType noCull(Identifier tex) {
		//? if >=26 {
		return RenderTypes.entityCutout(tex);
		//?} else {
		/*return RenderTypes.entityCutoutNoCull(tex);
		*///?}
	}

	private static RenderType cull(Identifier tex) {
		//? if >=26 {
		return RenderTypes.entityCutoutCull(tex);
		//?} else {
		/*return RenderTypes.entityCutout(tex);
		*///?}
	}

	/** A plain white texture, for untextured materials and the outline (its colour comes from the vertices). */
	private static Identifier white() {
		if (white == null) {
			white = Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "vrm/white");
			NativeImage image = new NativeImage(1, 1, false);
			image.setPixelABGR(0, 0, 0xFFFFFFFF);
			Minecraft.getInstance().getTextureManager().register(white, new DynamicTexture(() -> "Temple avatar white", image));
		}
		return white;
	}

	@Override
	public void close() {
		for (Identifier t : textures) {
			if (t != null) {
				Minecraft.getInstance().getTextureManager().release(t);
			}
		}
	}
}
