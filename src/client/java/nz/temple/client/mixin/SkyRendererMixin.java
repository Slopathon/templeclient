package nz.temple.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import nz.temple.client.modules.render.Skybox;
import nz.temple.client.render.SkyboxRenderer;

/**
 * Hands the sky to {@link Skybox}. The sky disc is where the picture goes; the sunset fan and the dark lower disc
 * would paint over it, so they are skipped while the skybox is on. From 26.3 vanilla draws the whole sky inside one
 * render pass it passes to each part, and the skybox draws into that pass.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
	//? if >=26 {
	@Shadow
	@Final
	private RenderTarget renderTarget;

	private RenderTarget temple$target() {
		return renderTarget;
	}
	//?} else {
	/*// 1.21.11 draws the sky straight into the game's main target.
	private RenderTarget temple$target() {
		return net.minecraft.client.Minecraft.getInstance().getMainRenderTarget();
	}
	*///?}

	//? if >=26.3 {
	/*@Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
	private void temple$sky(com.mojang.blaze3d.systems.RenderPass pass, org.joml.Vector3fc color, CallbackInfo ci) {
		int skyColor = net.minecraft.util.ARGB.colorFromFloat(1f, color.x(), color.y(), color.z());
		nz.temple.client.modules.render.WetGround.sky(skyColor);
		SkyboxRenderer.drawInto(pass);
		try {
			if (Skybox.active(false) && Skybox.render(temple$target(), skyColor)) {
				ci.cancel();
			}
		} finally {
			SkyboxRenderer.drawInto(null);
		}
	}

	@Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
	private void temple$endSky(com.mojang.blaze3d.systems.RenderPass pass, CallbackInfo ci) {
		SkyboxRenderer.drawInto(pass);
		try {
			if (Skybox.active(true) && Skybox.render(temple$target(), 0)) {
				ci.cancel();
			}
		} finally {
			SkyboxRenderer.drawInto(null);
		}
	}

	@Inject(method = "renderSunMoonAndStars", at = @At("HEAD"), cancellable = true)
	private void temple$celestials(com.mojang.blaze3d.systems.RenderPass pass, com.mojang.blaze3d.vertex.PoseStack pose,
			float sunAngle, float moonAngle, float starAngle, net.minecraft.world.level.MoonPhase phase, float rain,
			float starBrightness, CallbackInfo ci) {
		nz.temple.client.modules.render.WetGround.sun(sunAngle);
		if (Skybox.active(false) && !Skybox.keepCelestials()) {
			ci.cancel();
		}
	}
	*///?} else {
	@Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
	private void temple$sky(int skyColor, CallbackInfo ci) {
		nz.temple.client.modules.render.WetGround.sky(skyColor);
		if (Skybox.active(false) && Skybox.render(temple$target(), skyColor)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
	private void temple$endSky(CallbackInfo ci) {
		if (Skybox.active(true) && Skybox.render(temple$target(), 0)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderSunMoonAndStars", at = @At("HEAD"), cancellable = true)
	private void temple$celestials(com.mojang.blaze3d.vertex.PoseStack pose, float sunAngle, float moonAngle, float starAngle,
			net.minecraft.world.level.MoonPhase phase, float rain, float starBrightness, CallbackInfo ci) {
		nz.temple.client.modules.render.WetGround.sun(sunAngle);
		if (Skybox.active(false) && !Skybox.keepCelestials()) {
			ci.cancel();
		}
	}
	//?}

	@Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
	private void temple$sunset(CallbackInfo ci) {
		if (Skybox.active(false)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true)
	private void temple$darkDisc(CallbackInfo ci) {
		if (Skybox.active(false)) {
			ci.cancel();
		}
	}
}
