package com.example.client.piglin;

import com.example.EvolvingThreats;
import com.example.piglin.HellBerserk;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.resources.Identifier;

/**
 * Draws the berserk from {@link HellBerserkModel} and one netherite axe in the right hand.
 */
public class HellBerserkRenderer extends LivingEntityRenderer<HellBerserk, HellBerserkRenderState, HellBerserkModel> {

	private static final Identifier TEXTURE = EvolvingThreats.id("textures/entity/hell_berserk.png");

	private final ItemModelResolver itemModels;

	public HellBerserkRenderer(EntityRendererProvider.Context context) {
		super(context, new HellBerserkModel(HellBerserkModel.createRoot()), 0.5f);
		this.itemModels = context.getItemModelResolver();
		this.addLayer(new HellBerserkAxeLayer(this));
	}

	@Override
	public Identifier getTextureLocation(HellBerserkRenderState state) {
		return TEXTURE;
	}

	@Override
	public HellBerserkRenderState createRenderState() {
		return new HellBerserkRenderState();
	}

	/** No name over his head. The game would otherwise write "Hell Berserk" whenever you look at him. */
	@Override
	protected boolean shouldShowName(HellBerserk entity, double distanceSquared) {
		return false;
	}

	@Override
	public void extractRenderState(HellBerserk entity, HellBerserkRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		ArmedEntityRenderState.extractArmedEntityRenderState(entity, state, this.itemModels, partialTicks);
		state.aggressive = entity.isAggressive();
	}
}
