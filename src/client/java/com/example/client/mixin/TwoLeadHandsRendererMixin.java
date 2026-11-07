package com.example.client.mixin;

import com.example.client.skeleton.TwoLeadHandsPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.AbstractSkeletonRenderer;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Skeleton bow pose looks only at the right hand, and only while he is aggressive.
 * When the bow stays in the left hand, this raises the draw while he shoots.
 * Idle, he is not aggressive, so both arms stay down.
 * Once the player is in melee range the sword pose is left to the game.
 */
@Mixin(AbstractSkeletonRenderer.class)
public class TwoLeadHandsRendererMixin {

	@Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
	private void evolvingthreats$offhandBowPose(AbstractSkeleton mob, HumanoidArm arm, CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
		if (!TwoLeadHandsPose.showBowPose(mob)) {
			return;
		}
		InteractionHand hand = arm == mob.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
		if (mob.getItemInHand(hand).is(Items.BOW)) {
			cir.setReturnValue(HumanoidModel.ArmPose.BOW_AND_ARROW);
		}
	}

	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void evolvingthreats$keepBowPose(AbstractSkeleton entity, SkeletonRenderState state, float partialTicks, CallbackInfo ci) {
		// Without this, the model treats a sword in the right hand as "not a bow" and forces the melee arms even while he shoots.
		if (TwoLeadHandsPose.showBowPose(entity)) {
			state.isHoldingBow = true;
		}
	}
}
