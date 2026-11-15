package com.example.client.whiteeyes;

import com.example.EvolvingThreats;
import com.example.whiteeyes.WhiteEyes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * Steve's body and the iron armor on it.
 * This is an ordinary mob drawing, not the player drawing.
 * The player drawing throws this picture away and paints the default Steve, purple pupils included.
 * The picture is textures/entity/white_eyes.png — Steve, with those pupils painted white.
 * 0.9375 is the same shrink the game uses for a real player, so the height matches.
 */
public class WhiteEyesRenderer extends HumanoidMobRenderer<WhiteEyes, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {

	private static final Identifier TEXTURE = EvolvingThreats.id("textures/entity/white_eyes.png");

	public WhiteEyesRenderer(EntityRendererProvider.Context context) {
		super(
				context,
				new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER), RenderTypes::entityTranslucent),
				new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER), RenderTypes::entityTranslucent),
				0.5f
		);
		this.addLayer(new HumanoidArmorLayer<>(
				this,
				ArmorModelSet.bake(
						ModelLayers.PLAYER_ARMOR,
						context.getModelSet(),
						part -> new HumanoidModel<>(part)
				),
				context.getEquipmentRenderer()
		));
		this.addLayer(new WhiteEyesEyesLayer(this));
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return TEXTURE;
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	protected HumanoidModel.ArmPose getArmPose(WhiteEyes mob, HumanoidArm arm) {
		ItemStack held = mob.getItemHeldByArm(arm);
		if (held.isEmpty()) {
			return HumanoidModel.ArmPose.EMPTY;
		}
		if (mob.isUsingItem() && held.is(net.minecraft.world.item.Items.BOW) && arm == mob.getMainArm()) {
			return HumanoidModel.ArmPose.BOW_AND_ARROW;
		}
		return HumanoidModel.ArmPose.ITEM;
	}

	@Override
	protected void scale(HumanoidRenderState state, PoseStack poseStack) {
		poseStack.scale(0.9375f, 0.9375f, 0.9375f);
	}
}
