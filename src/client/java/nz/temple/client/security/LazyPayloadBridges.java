package nz.temple.client.security;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import nz.temple.client.cosmetic.PetsAccess;
import nz.temple.client.hud.Notifications;
import nz.temple.client.modules.cosmetic.Previewable;
import nz.temple.client.render.Fx;
import nz.temple.client.render.FxSource;
import nz.temple.client.render.GlowUser;
import nz.temple.client.render.HudSource;
import nz.temple.client.render.Projection;
import nz.temple.client.render.SwingAnimator;
import nz.temple.client.render.SnowSource;
import nz.temple.client.render.SnowVolume;
import nz.temple.client.render.snow.SnowDrifts;
import nz.temple.module.LazyPayloadModule;
import nz.temple.module.Module;

public final class LazyPayloadBridges {
	private LazyPayloadBridges() {
	}

	public static void emit(Module module, Fx fx, float partial) {
		FxSource source = implementation(module, FxSource.class);
		if (source != null) {
			source.emit(fx, partial);
		}
	}

	public static void renderHud(Module module, GuiGraphicsExtractor graphics, DeltaTracker delta, Projection projection) {
		HudSource source = implementation(module, HudSource.class);
		if (source != null) {
			source.render(graphics, delta, projection);
		}
	}

	public static boolean applySwing(Module module, PoseStack pose, HumanoidArm arm, float swing, ItemStack stack) {
		SwingAnimator animator = implementation(module, SwingAnimator.class);
		return animator != null && animator.applySwing(pose, arm, swing, stack);
	}

	public static boolean glowing(Module module) {
		GlowUser user = implementation(module, GlowUser.class);
		return user != null && user.glowing();
	}

	public static SnowVolume.Settings snowVolume(Module module) {
		SnowSource source = implementation(module, SnowSource.class);
		return source == null ? null : source.snowVolume();
	}

	public static SnowDrifts.Settings driftSettings(Module module) {
		SnowSource source = implementation(module, SnowSource.class);
		return source == null ? null : source.driftSettings();
	}

	public static void renderPetBubble(Module module, GuiGraphicsExtractor graphics, DeltaTracker delta, Projection projection) {
		PetsAccess pets = implementation(module, PetsAccess.class);
		if (pets != null) {
			pets.renderBubble(graphics, delta, projection);
		}
	}

	public static boolean say(Module module, String title, String detail, Notifications.Kind kind) {
		PetsAccess pets = implementation(module, PetsAccess.class);
		return pets != null && pets.say(title, detail, kind);
	}

	public static boolean preview(Module module, Vec3 at) {
		Previewable previewable = implementation(module, Previewable.class);
		if (previewable == null) {
			return false;
		}
		previewable.preview(at);
		return true;
	}

	public static boolean previewable(Module module) {
		return implementation(module, Previewable.class) != null;
	}

	private static <T> T implementation(Module module, Class<T> type) {
		Module candidate = module instanceof LazyPayloadModule lazy ? lazy.implementationIfLoaded() : module;
		return type.isInstance(candidate) ? type.cast(candidate) : null;
	}
}
