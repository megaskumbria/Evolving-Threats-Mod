package com.example.client.mixin;

import com.example.client.skeleton.TacticianBackItemLayer;
import com.example.client.skeleton.TacticianBackItemState;
import com.example.entity.TacticianSkeletonEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.monster.skeleton.SkeletonModel;
import net.minecraft.client.renderer.entity.AbstractSkeletonRenderer;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hangs a layer of "object on the back" and each frame puts a bow or sword there. */
@Mixin(AbstractSkeletonRenderer.class)
public abstract class TacticianSkeletonRendererMixin {

	@Inject(
			method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Lnet/minecraft/client/renderer/entity/ArmorModelSet;Lnet/minecraft/client/model/monster/skeleton/SkeletonModel;)V",
			at = @At("RETURN"))
	private void evolvingthreats$addBackItemLayer(
			EntityRendererProvider.Context context,
			ArmorModelSet<ModelLayerLocation> armorSet,
			SkeletonModel<?> model,
			CallbackInfo ci) {
		((LivingEntityRendererAccessor) (Object) this).evolvingthreats$layers()
				.add(new TacticianBackItemLayer<>((AbstractSkeletonRenderer<?, ?>) (Object) this));
	}

	@Inject(method = "extractRenderState", at = @At("RETURN"))
	private void evolvingthreats$extractBackItem(AbstractSkeleton entity, SkeletonRenderState state, float partialTicks, CallbackInfo ci) {
		if (!(state instanceof TacticianBackItemState back)) {
			return;
		}
		ItemStack stack = entity instanceof TacticianSkeletonEntity data
				? data.evolvingthreats$getBackItem()
				: ItemStack.EMPTY;
		((LivingEntityRendererAccessor) (Object) this).evolvingthreats$itemModelResolver().updateForLiving(
				back.evolvingthreats$getBackItem(),
				stack,
				// FIXED — a flat picture, as in a frame. In the hand (THIRD_PERSON), the sword stands with its rib and sticks into the back.
				ItemDisplayContext.FIXED,
				entity);
	}
}
