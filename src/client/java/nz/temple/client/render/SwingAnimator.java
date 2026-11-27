package nz.temple.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

public interface SwingAnimator {
	boolean applySwing(PoseStack pose, HumanoidArm arm, float swing, ItemStack stack);
}
