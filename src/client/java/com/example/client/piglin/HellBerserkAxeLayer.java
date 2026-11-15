package com.example.client.piglin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;

/**
 * The netherite axe in the right hand. It is a real item, not part of the body.
 * The turn and the shift are the same ones the game uses for a tool in a piglin's hand.
 * If it sits in the wrist, change the numbers below and restart the game.
 */
public class HellBerserkAxeLayer extends RenderLayer<HellBerserkRenderState, HellBerserkModel> {

	// ===================== SETTINGS (change these numbers) =====================

	/** Sideways, in blocks. Plus moves the axe out of the fist, toward the fingers. 0.0625 is one pixel. */
	private static final float OFFSET_X = 1.0f / 16.0f;

	/** Along the grip, in blocks. Plus sinks the handle deeper into the fist. */
	private static final float OFFSET_Y = 2.0f / 16.0f;

	/**
	 * Down the arm, in blocks. -0.625 reaches the fist from the shoulder, the same as a brute.
	 * More negative puts the axe past the fingers. Closer to 0 leaves it in the elbow.
	 */
	private static final float OFFSET_Z = -10.0f / 16.0f;

	/** Tilt of the blade, in degrees. -90 stands it in the fist like a held tool. 0 lays it flat. */
	private static final float ROT_X = -90.0f;

	/** Spin around the grip, in degrees. 180 turns the blade the other way. */
	private static final float ROT_Y = 180.0f;

	/** Size. 1.0 is a normal tool in a hand. 1.3 is bigger. 0.85 was too small. */
	private static final float SCALE = 1.0f;

	// ========================================================================

	public HellBerserkAxeLayer(net.minecraft.client.renderer.entity.RenderLayerParent<HellBerserkRenderState, HellBerserkModel> renderer) {
		super(renderer);
	}

	@Override
	public void submit(
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int lightCoords,
			HellBerserkRenderState state,
			float yRot,
			float xRot) {
		this.draw(poseStack, submitNodeCollector, lightCoords, state, HumanoidArm.RIGHT, state.rightHandItemState);
		this.draw(poseStack, submitNodeCollector, lightCoords, state, HumanoidArm.LEFT, state.leftHandItemState);
	}

	private void draw(
			PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector,
			int lightCoords,
			HellBerserkRenderState state,
			HumanoidArm arm,
			ItemStackRenderState item) {
		if (item.isEmpty()) {
			return;
		}
		HellBerserkModel model = this.getParentModel();
		ModelPart limb = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
		poseStack.pushPose();
		// From the shoulder. OFFSET_Z then reaches the fist. The empty hand point is not used.
		limb.translateAndRotate(poseStack);
		poseStack.rotateDegrees(Axis.XP, ROT_X);
		poseStack.rotateDegrees(Axis.YP, ROT_Y);
		float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
		poseStack.translate(OFFSET_X * side, OFFSET_Y, OFFSET_Z);
		poseStack.scale(SCALE, SCALE, SCALE);
		item.submit(poseStack, submitNodeCollector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
	}
}
