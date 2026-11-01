package com.example.client.tool;

import com.example.tool.FloatingTool;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Draws a floating tool with a finished item model.
 * While no one is attacking, it spins and swings a little like an abandoned thing.
 * While attacking, he looks at the target and does not rotate around his axis.
 * Size — {@link FloatingTool#VISUAL_SCALE}, gate — {@link FloatingTool#ATTACK_FACING_OFFSET}.
 */
public class FloatingToolRenderer extends EntityRenderer<FloatingTool, FloatingToolRenderer.State> {

	private final ItemModelResolver itemModelResolver;

	public FloatingToolRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemModelResolver = context.getItemModelResolver();
		this.shadowRadius = 0.25f;
		this.shadowStrength = 0.6f;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(FloatingTool entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		// GROUND — three-dimensional model, like an object on the ground. The enchantments give the glare themselves.
		this.itemModelResolver.updateForNonLiving(
				state.item,
				entity.getMainHandItem(),
				ItemDisplayContext.GROUND,
				entity
		);
		state.attacking = entity.isAttacking();
		// Where the mobile is looking. In the attack, the picture takes this angle instead of twisting.
		state.facingYaw = entity.getYRot(partialTicks);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
		if (!state.isInvisible && !state.item.isEmpty()) {
			poseStack.pushPose();
			float bob = Mth.sin(state.ageInTicks / 10.0f) * 0.08f;
			poseStack.translate(0.0f, 0.25f + bob, 0.0f);
			float scale = FloatingTool.VISUAL_SCALE;
			poseStack.scale(scale, scale, scale);
			if (state.attacking) {
				// 180 — like ordinary mobs: the "face" of the model looks the same as yRot.
				poseStack.rotateDegrees(Axis.YP, 180.0f - state.facingYaw + FloatingTool.ATTACK_FACING_OFFSET);
			} else {
				poseStack.rotate(Axis.YP, ItemEntity.getSpin(state.ageInTicks, 0.0f));
			}
			state.item.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
			poseStack.popPose();
		}
		super.submit(state, poseStack, submitNodeCollector, camera);
	}

	public static class State extends EntityRenderState {
		public final ItemStackRenderState item = new ItemStackRenderState();
		public boolean attacking;
		public float facingYaw;
	}
}
