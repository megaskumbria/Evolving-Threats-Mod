package com.example.client.piglin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;

/**
 * A netherite axe in each hand. The axes are real items, not part of the body.
 * If one sits in the wrist, change the numbers below and restart the game.
 */
public class HellBerserkAxeLayer extends RenderLayer<ArmedEntityRenderState, HellBerserkModel> {

	// ===================== SETTINGS (change these numbers) =====================

	/** Nudge along the arm, in blocks. Plus moves the axe out of the fist, toward the fingers. */
	private static final float OFFSET_X = 0.0f;

	/** Nudge down the hand, in blocks. Plus sinks the handle into the fist. */
	private static final float OFFSET_Y = 0.1f;

	/** Nudge forward, in blocks. Plus puts the axe in front of the hand. */
	private static final float OFFSET_Z = 0.05f;

	/** Tilt of the blade, in degrees. -80 holds it like a brute. 0 points it at the ground. */
	private static final float ROT_X = -80.0f;

	/** Spin around the hand, in degrees. 180 turns the blade the other way. */
	private static final float ROT_Y = 180.0f;

	/** Size of both axes. 1.0 is a normal item. 0.8 is a little smaller. */
	private static final float SCALE = 0.85f;

	// ========================================================================

	public HellBerserkAxeLayer(net.minecraft.client.renderer.entity.RenderLayerParent<ArmedEntityRenderState, HellBerserkModel> renderer) {
		super(renderer);
	}

	@Override
	public void submit(
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int lightCoords,
			ArmedEntityRenderState state,
			float yRot,
			float xRot) {
		this.draw(poseStack, submitNodeCollector, lightCoords, state, HumanoidArm.RIGHT, state.rightHandItemState);
		this.draw(poseStack, submitNodeCollector, lightCoords, state, HumanoidArm.LEFT, state.leftHandItemState);
	}

	private void draw(
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int lightCoords,
			ArmedEntityRenderState state,
			HumanoidArm arm,
			ItemStackRenderState item) {
		if (item.isEmpty()) {
			return;
		}
		HellBerserkModel model = this.getParentModel();
		ModelPart limb = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
		ModelPart hand = arm == HumanoidArm.RIGHT ? model.rightHand : model.leftHand;
		poseStack.pushPose();
		limb.translateAndRotate(poseStack);
		hand.translateAndRotate(poseStack);
		float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
		poseStack.translate(OFFSET_X * side, OFFSET_Y, OFFSET_Z);
		poseStack.rotateDegrees(Axis.XP, ROT_X);
		poseStack.rotateDegrees(Axis.YP, ROT_Y);
		poseStack.scale(SCALE, SCALE, SCALE);
		item.submit(poseStack, submitNodeCollector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
	}
}
