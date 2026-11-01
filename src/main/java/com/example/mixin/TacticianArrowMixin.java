package com.example.mixin;

import com.example.skeleton.TacticianSkeleton;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/*The hit of a special arrow includes a sword. The shield does not miss the impact - then this code is not called. */
@Mixin(Arrow.class)
public class TacticianArrowMixin {

	@Inject(method = "doPostHurtEffects", at = @At("RETURN"))
	private void evolvingthreats$tacticianHit(LivingEntity mob, CallbackInfo ci) {
		Arrow arrow = (Arrow) (Object) this;
		PotionContents contents = arrow.getPickupItemStackOrigin().get(DataComponents.POTION_CONTENTS);
		if (contents == null || contents.customName().isEmpty() || !contents.customName().get().equals(TacticianSkeleton.ARROW_NAME)) {
			return;
		}
		if (!(arrow.getOwner() instanceof AbstractSkeleton skeleton) || !(mob instanceof Player player)) {
			return;
		}
		TacticianSkeleton.onArrowHit(skeleton, player);
	}
}
