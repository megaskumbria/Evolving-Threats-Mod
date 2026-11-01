package com.example.client.skeleton;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.monster.skeleton.SkeletonModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Draws a bow or sword on the back. The model of the skeleton is ordinary, a separate model is not needed.
 * If the item sits crooked, spin the numbers below and restart the game.
 */
public class TacticianBackItemLayer<S extends SkeletonRenderState> extends RenderLayer<S, SkeletonModel<S>> {

	// ===================== SETTINGS (change these numbers) =====================

	/** Shift to the right, in blocks. 0 = center of back. Plus — to the right shoulder of the skeleton. */
	private static final float OFFSET_X = 0.12F;

	/**
	 * Shift down the back, in blocks. 0 = at the neck, 0.38 = just above the middle,
	 * so that the handle reaches the shoulder. More — the sword is lower, to the belt.
	 */
	private static final float OFFSET_Y = 0.38F;

	/**
	 * As far as the object moves away from the back, in blocks. 0.22 = lies outside, not inside the ribs.
	 * If the sword is still in the body, zoom in. If it's too far, reduce it.
	 */
	private static final float OFFSET_Z = 0.22F;

	/**
	 * Tilt on the back, in degrees.
	 * 0 = sword across, horizontally.
	 * -58 = handle to right shoulder, blade down.
	 * Plus (for example, 58) — vice versa: blade up, handle down.
	 */
	private static final float ROT_Z = -58.0F;

	/** Rotate around vertical, in degrees. 0 = face out. 180 = rotate picture. */
	private static final float ROT_Y = 0.0F;

	/**
	 * Rotation "flat", in degrees.
	 * 0 = wide side of the sword on the back. 90 = rib into the body again.
	 */
	private static final float ROT_X = 0.0F;

	/**
	 * Size: The flat image is already smaller than the object in the hand.
	 * 1.0 = normal sword on the back. 0.7 = shorter.
	 */
	private static final float SCALE = 1.0F;

	// ========================================================================

	public TacticianBackItemLayer(net.minecraft.client.renderer.entity.RenderLayerParent<S, SkeletonModel<S>> renderer) {
		super(renderer);
	}

	@Override
	public void submit(
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int lightCoords,
			S state,
			float yRot,
			float xRot) {
		if (!(state instanceof TacticianBackItemState back)) {
			return;
		}
		ItemStackRenderState item = back.evolvingthreats$getBackItem();
		if (item.isEmpty()) {
			return;
		}
		poseStack.pushPose();
		this.getParentModel().body.translateAndRotate(poseStack);
		poseStack.translate(OFFSET_X, OFFSET_Y, OFFSET_Z);
		poseStack.rotateDegrees(Axis.XP, ROT_X);
		poseStack.rotateDegrees(Axis.YP, ROT_Y);
		poseStack.rotateDegrees(Axis.ZP, ROT_Z);
		poseStack.scale(SCALE, SCALE, SCALE);
		item.submit(poseStack, submitNodeCollector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
	}
}
