package com.example.client.whiteeyes;

import com.example.EvolvingThreats;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * The eye pixels, drawn a second time at full brightness.
 * The rest of this picture is empty, so it does not cover the face or the helmet.
 * Night does not turn these pixels gray. A brighter pass is the one that stays white in the dark.
 */
public class WhiteEyesEyesLayer extends EyesLayer<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {

	private static final Identifier TEXTURE = EvolvingThreats.id("textures/entity/white_eyes_eyes.png");
	private static final RenderType EYES = RenderTypes.eyes(TEXTURE);

	public WhiteEyesEyesLayer(RenderLayerParent<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> renderer) {
		super(renderer);
	}

	@Override
	public RenderType renderType() {
		return EYES;
	}
}
