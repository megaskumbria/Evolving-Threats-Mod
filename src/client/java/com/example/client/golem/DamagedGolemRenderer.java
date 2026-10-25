package com.example.client.golem;

import com.example.EvolvingThreats;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.resources.Identifier;

/**
 * Тот же голем, что у игры, но текстура с чёрными глазами.
 * Трещины рисует ванильный слой поверх: чем выше CRACK_LEVEL, тем они сильнее.
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
