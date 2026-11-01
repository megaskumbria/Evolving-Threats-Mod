package com.example.client.golem;

import com.example.EvolvingThreats;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.resources.Identifier;

/**
 * Same golem as the game, but texture with black eyes.
 * The cracks paint the vanilla layer on top: the higher the CRACK_LEVEL, the stronger they are.
 */
public class DamagedGolemRenderer extends IronGolemRenderer {

	private static final Identifier TEXTURE = EvolvingThreats.id("textures/entity/damaged_golem.png");

	public DamagedGolemRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public Identifier getTextureLocation(IronGolemRenderState state) {
		return TEXTURE;
	}
}
