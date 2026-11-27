package nz.temple.client.render;

import nz.temple.client.compat.Gpu;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import nz.temple.TempleNz;
import nz.temple.client.mixin.RenderTypeAccessor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Render types for the through-wall full-body chams pass. */
public final class ChamsRenderTypes {
	private static final Map<Identifier, RenderType> WALLS = new HashMap<>();
	private static RenderPipeline wallPipeline;

	private ChamsRenderTypes() {
	}

	/** How a chams pass relates to the depth buffer. */
	public enum Depth {
		/** Drawn over everything: the through-wall passes. */
		NONE,
		/** Hidden behind terrain, written into depth, nudged in front of the body it covers. */
		TESTED,
		/** Hidden behind terrain but leaves depth alone, so the body pass after it still lands. */
		TESTED_NO_WRITE
	}

	private static final Map<String, RenderPipeline> PIPELINES = new HashMap<>();
	/** Looked up per entity, per pass, every frame: a record key, so no string is built for each lookup. */
	private record Key(String kind, Object a, boolean b, Object c, Object d) {
	}

	private static final Map<Key, RenderType> TYPES = new HashMap<>();

	/**
	 * Flat-colour silhouette with no depth test: the body becomes one solid shape in the colour passed as the
	 * model tint, keeping only the texture's alpha so cut-out parts stay the right shape.
	 */
	public static RenderType flat(Identifier texture) {
		return flat(texture, false);
	}

	/** As {@link #flat(Identifier)}; with {@code depth} the shape is hidden behind terrain like the model itself. */
	public static RenderType flat(Identifier texture, boolean depth) {
		return type("chams_flat", depth ? Depth.TESTED : Depth.NONE, false, texture, null);
	}

	/**
	 * The outline shell: the same model pushed outward along its normals by a distance carried in the overlay
	 * coordinate, drawn before the body so only the rim is left showing. Because the push is in world units, the
	 * rim is a fixed fraction of the entity at every distance instead of a fixed number of pixels.
	 */
	public static RenderType shell(Identifier texture, boolean depth) {
		return type("chams_flat", depth ? Depth.TESTED_NO_WRITE : Depth.NONE, true, texture, null);
	}

	/**
	 * The screen-space image pass: the entity's geometry is drawn with our own shader pair, which samples the
	 * picture by screen position instead of the model's own texture coordinates. The body becomes a window onto
	 * the image rather than a thing wearing it; the model's texture is still read for its cut-outs.
	 */
	public static RenderType image(Identifier texture, Identifier picture, boolean depth) {
		return type("chams_image", depth ? Depth.TESTED : Depth.NONE, false, texture, picture);
	}

	/**
	 * An energy body (Astral, Void, Plasma, Hologram; the look rides in the overlay's first coordinate): an animated
	 * otherworldly surface with a burning rim, drawn by chams_energy.
	 */
	public static RenderType energy(Identifier texture, boolean depth) {
		return energyType(texture, depth ? Depth.TESTED : Depth.NONE, false);
	}

	/**
	 * One layer of the glow aura: the model pushed outward along its normals (distance in the overlay's first
	 * coordinate, in thousandths of a block) and added onto the scene, bright only at its grazing edges. Stacked at a
	 * few distances it reads as light spilling off the body.
	 */
	public static RenderType glow(Identifier texture, boolean depth) {
		return energyType(texture, depth ? Depth.TESTED_NO_WRITE : Depth.NONE, true);
	}

	/**
	 * Hand/weapon chams body (see {@link nz.temple.client.modules.render.HandChams}): the look rides in the overlay's
	 * first coordinate and the animation speed in its second. Nudged in front of the real arm so it can overlay it.
	 */
	public static RenderType hand(Identifier texture) {
		return handType(texture, false, false);
	}

	/**
	 * The hand chams look for a fading afterimage of the hand: depth tested but never written, so a trail of them
	 * neither hides the hand in front nor each other.
	 */
	public static RenderType handGhost(Identifier texture) {
		Key key = new Key("ghost", null, false, texture, null);
		RenderType cached = TYPES.get(key);
		if (cached != null) {
			return cached;
		}
		RenderPipeline pipeline = PIPELINES.computeIfAbsent("hand_chams_ghost", n -> {
			RenderPipeline.Builder builder = custom("pipeline/" + n, "core/hand_chams");
			var vanilla = Gpu.depthStencil(RenderPipelines.ENTITY_TRANSLUCENT);
			builder.withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(vanilla.depthTest(), false));
			return RenderPipelines.register(builder.build());
		});
		RenderType type = RenderTypeAccessor.templenz$create("templenz_hand_chams_ghost",
				RenderSetup.builder(pipeline).withTexture("Sampler0", texture).useLightmap().useOverlay().sortOnUpload()
						.createRenderSetup());
		TYPES.put(key, type);
		return type;
	}

	/** As {@link #hand}, but drawn over everything: held items of entities whose chams show through walls. */
	public static RenderType handWalls(Identifier texture) {
		return handType(texture, false, true);
	}

	/**
	 * One layer of the hand/weapon halo, added straight onto the frame. The first-person hand draws after the scene's
	 * glow buffer has been composited, so its light cannot go through that buffer like the entity aura does.
	 */
	public static RenderType handGlow(Identifier texture) {
		return handType(texture, true, false);
	}

	/**
	 * The Knives module's Polished finish: the knife drawn as cut gem, polished steel or anodized metal by knife_gem.
	 * Depth tested and written like any solid model; the model must come from {@code BedrockModel.draw} with its local
	 * position in the vertex colour and the finish in the overlay.
	 */
	public static RenderType knife(Identifier texture) {
		Key key = new Key("knife", null, false, texture, null);
		RenderType cached = TYPES.get(key);
		if (cached != null) {
			return cached;
		}
		RenderPipeline pipeline = PIPELINES.computeIfAbsent("knife_gem", n -> {
			RenderPipeline.Builder builder = custom("pipeline/" + n, "core/knife_gem");
			builder.withDepthStencilState(Gpu.depthStencil(RenderPipelines.ENTITY_TRANSLUCENT));
			return RenderPipelines.register(builder.build());
		});
		RenderType type = RenderTypeAccessor.templenz$create("templenz_knife_gem",
				RenderSetup.builder(pipeline).withTexture("Sampler0", texture).useLightmap().useOverlay().createRenderSetup());
		TYPES.put(key, type);
		return type;
	}

	private static RenderType handType(Identifier texture, boolean glow, boolean walls) {
		Key key = new Key("hand", walls, glow, texture, null);
		RenderType cached = TYPES.get(key);
		if (cached != null) {
			return cached;
		}
		String name = "hand_chams" + (glow ? "_glow" : "") + (walls ? "_walls" : "");
		RenderPipeline pipeline = PIPELINES.computeIfAbsent(name, n -> {
			RenderPipeline.Builder builder = custom("pipeline/" + n, "core/hand_chams");
			var vanilla = Gpu.depthStencil(RenderPipelines.ENTITY_TRANSLUCENT);
			if (glow) {
				builder.withShaderDefine("GLOW");
				builder.withDepthStencilState(new com.mojang.blaze3d.pipeline.DepthStencilState(vanilla.depthTest(), false));
				builder.withColorTargetState(0, new com.mojang.blaze3d.pipeline.ColorTargetState(
						com.mojang.blaze3d.pipeline.BlendFunction.LIGHTNING));
			} else if (!walls) {
				builder.withDepthStencilState(vanilla);
				builder.withShaderDefine("DEPTH_NUDGE");
			}
			return RenderPipelines.register(builder.build());
		});
		RenderType type = RenderTypeAccessor.templenz$create("templenz_" + name,
				RenderSetup.builder(pipeline).withTexture("Sampler0", texture).useLightmap().useOverlay().sortOnUpload()
						.createRenderSetup());
		TYPES.put(key, type);
		return type;
	}

	private static RenderType energyType(Identifier texture, Depth depth, boolean glow) {
		Key key = new Key("energy", depth, glow, texture, null);
		RenderType cached = TYPES.get(key);
		if (cached != null) {
			return cached;
		}
		String name = "chams_energy" + (glow ? "_glow" : "") + "_" + depth.name().toLowerCase(java.util.Locale.ROOT);
		RenderPipeline pipeline = PIPELINES.computeIfAbsent(name, n -> {
			RenderPipeline.Builder builder = custom("pipeline/" + n, "core/chams_energy");
			if (depth != Depth.NONE) {
				var vanilla = Gpu.depthStencil(RenderPipelines.ENTITY_TRANSLUCENT);
				builder.withDepthStencilState(depth == Depth.TESTED ? vanilla
						: new com.mojang.blaze3d.pipeline.DepthStencilState(vanilla.depthTest(), false));
			}
			if (depth == Depth.TESTED) {
				builder.withShaderDefine("DEPTH_NUDGE");
			}
			if (glow) {
				builder.withShaderDefine("GLOW");
				// Light adds onto whatever is behind it, so the halo glows against the dark and brightens the world.
				builder.withColorTargetState(0, new com.mojang.blaze3d.pipeline.ColorTargetState(
						com.mojang.blaze3d.pipeline.BlendFunction.LIGHTNING));
			}
			return RenderPipelines.register(builder.build());
		});
		RenderSetup.RenderSetupBuilder setup = RenderSetup.builder(pipeline).withTexture("Sampler0", texture).useLightmap().useOverlay()
				.sortOnUpload();
		//? if <26.3 {
		if (glow) {
			// Glow sources draw into the off-screen glow buffer, which is blurred and added back as bloom.
			setup.setOutputTarget(GlowBuffer.TARGET);
		}
		//?}
		RenderType type = RenderTypeAccessor.templenz$create("templenz_" + name, setup.createRenderSetup());
		TYPES.put(key, type);
		return type;
	}

	private static RenderType type(String shader, Depth depth, boolean shell, Identifier texture, Identifier picture) {
		Key key = new Key(shader, depth, shell, texture, picture);
		RenderType cached = TYPES.get(key);
		if (cached != null) {
			return cached;
		}
		RenderPipeline pipeline = pipeline(shader, depth, shell, picture != null);
		RenderSetup.RenderSetupBuilder setup = RenderSetup.builder(pipeline).withTexture("Sampler0", texture);
		if (picture != null) {
			setup.withTexture("PictureSampler", picture, () -> RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
		}
		RenderType type = RenderTypeAccessor.templenz$create("templenz_" + pipelineName(shader, depth, shell),
				setup.useLightmap().useOverlay().sortOnUpload().createRenderSetup());
		TYPES.put(key, type);
		return type;
	}

	private static String pipelineName(String shader, Depth depth, boolean shell) {
		return shader + (shell ? "_shell" : "") + "_" + depth.name().toLowerCase(java.util.Locale.ROOT);
	}

	private static RenderPipeline pipeline(String shader, Depth depth, boolean shell, boolean picture) {
		String name = pipelineName(shader, depth, shell);
		return PIPELINES.computeIfAbsent(name, n -> {
			RenderPipeline.Builder builder = custom("pipeline/" + n, "core/" + shader);
			if (depth != Depth.NONE) {
				var vanilla = Gpu.depthStencil(RenderPipelines.ENTITY_TRANSLUCENT);
				builder.withDepthStencilState(depth == Depth.TESTED ? vanilla
						: new com.mojang.blaze3d.pipeline.DepthStencilState(vanilla.depthTest(), false));
			}
			if (depth == Depth.TESTED) {
				builder.withShaderDefine("DEPTH_NUDGE");
			}
			if (shell) {
				builder.withShaderDefine("SHELL");
			}
			if (picture) {
				builder.withBindGroupLayout(com.mojang.blaze3d.pipeline.BindGroupLayout.builder().withSampler("PictureSampler").build());
			}
			return RenderPipelines.register(builder.build());
		});
	}

	/** Every chams pipeline, built if needed; for the startup shader check. */
	public static java.util.List<RenderPipeline> pipelines() {
		Identifier any = Identifier.withDefaultNamespace("textures/misc/white.png");
		for (boolean depth : new boolean[] {false, true}) {
			flat(any, depth);
			shell(any, depth);
			image(any, any, depth);
			energy(any, depth);
			glow(any, depth);
		}
		hand(any);
		handWalls(any);
		handGlow(any);
		knife(any);
		return java.util.List.copyOf(PIPELINES.values());
	}

	/** A pipeline cloned from vanilla's translucent entity one, with our own shaders and no depth test. */
	private static RenderPipeline.Builder custom(String location, String shader) {
		RenderPipeline source = RenderPipelines.ENTITY_TRANSLUCENT;
		RenderPipeline.Builder builder = RenderPipeline.builder()
				.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, location))
				.withVertexShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, shader))
				.withFragmentShader(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, shader))
				.withDepthStencilState(Optional.empty())
				.withCull(false)
				.withPrimitiveTopology(Gpu.topology(source));
		for (var layout : Gpu.bindGroupLayouts(source)) {
			builder.withBindGroupLayout(layout);
		}
		for (int i = 0; i < nz.temple.client.compat.Gpu.colorTargets(source).size(); i++) {
			builder.withColorTargetState(i, nz.temple.client.compat.Gpu.colorTargets(source).get(i));
		}
		for (int i = 0; i < nz.temple.client.compat.Gpu.vertexBindings(source).size(); i++) {
			builder.withVertexBinding(i, nz.temple.client.compat.Gpu.vertexBindings(source).get(i));
		}
		return builder;
	}

	public static RenderType walls(Identifier texture) {
		return WALLS.computeIfAbsent(texture, ChamsRenderTypes::create);
	}

	private static RenderType create(Identifier texture) {
		if (wallPipeline == null) {
			RenderPipeline source = RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE;
			RenderPipeline.Builder builder = RenderPipeline.builder()
					.withLocation(Identifier.fromNamespaceAndPath(TempleNz.MOD_ID, "pipeline/chams_walls"))
					.withVertexShader(nz.temple.client.compat.Gpu.vertexShader(source))
					.withFragmentShader(nz.temple.client.compat.Gpu.fragmentShader(source))
					.withDepthStencilState(Optional.empty())
					.withCull(false)
					.withPrimitiveTopology(Gpu.topology(source));
			for (var layout : Gpu.bindGroupLayouts(source)) {
				builder.withBindGroupLayout(layout);
			}
			for (int i = 0; i < nz.temple.client.compat.Gpu.colorTargets(source).size(); i++) {
				builder.withColorTargetState(i, nz.temple.client.compat.Gpu.colorTargets(source).get(i));
			}
			for (int i = 0; i < nz.temple.client.compat.Gpu.vertexBindings(source).size(); i++) {
				builder.withVertexBinding(i, nz.temple.client.compat.Gpu.vertexBindings(source).get(i));
			}
			ShaderDefines defines = source.getShaderDefines();
			for (String flag : defines.flags()) {
				builder.withShaderDefine(flag);
			}
			for (var entry : defines.values().entrySet()) {
				String value = entry.getValue();
				try {
					builder.withShaderDefine(entry.getKey(), Integer.parseInt(value));
				} catch (NumberFormatException ignored) {
					builder.withShaderDefine(entry.getKey(), Float.parseFloat(value));
				}
			}
			wallPipeline = RenderPipelines.register(builder.build());
		}
		RenderSetup setup = RenderSetup.builder(wallPipeline)
				.withTexture("Sampler0", texture)
				.useLightmap()
				.useOverlay()
				.sortOnUpload()
				.createRenderSetup();
		return RenderTypeAccessor.templenz$create("templenz_chams_walls", setup);
	}
}
