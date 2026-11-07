package com.example.client.mixin;

import com.example.client.illager.PillagerArmorLayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PillagerRenderer;
import net.minecraft.client.renderer.entity.VindicatorRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Puts the armor layer on pillagers and vindicators. Worn pieces are copied in {@link PillagerArmorExtractMixin}. */
@Mixin({ PillagerRenderer.class, VindicatorRenderer.class })
public class PillagerArmorRendererMixin {

	@Inject(method = "<init>", at = @At("RETURN"))
	private void evolvingthreats$addArmorLayer(EntityRendererProvider.Context context, CallbackInfo ci) {
		ArmorModelSet<HumanoidModel<net.minecraft.client.renderer.entity.state.HumanoidRenderState>> models = ArmorModelSet.bake(
				ModelLayers.PLAYER_ARMOR,
				context.getModelSet(),
				part -> new HumanoidModel<>(part));
		((LivingEntityRendererAccessor) (Object) this).evolvingthreats$layers().add(
				new PillagerArmorLayer(
						(RenderLayerParent<IllagerRenderState, IllagerModel<IllagerRenderState>>) (Object) this,
						models,
						context.getEquipmentRenderer()));
	}
}
