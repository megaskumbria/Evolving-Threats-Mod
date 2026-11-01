package com.example.mixin;

import com.example.skeleton.TacticianSkeleton;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** All skeletons have an object on their back. Normal has it empty. */
@Mixin(LivingEntity.class)
public class TacticianSkeletonDataInitMixin {

	@Inject(method = "defineSynchedData", at = @At("RETURN"))
	private void evolvingthreats$defineBackItem(SynchedEntityData.Builder builder, CallbackInfo ci) {
		if ((Object) this instanceof AbstractSkeleton) {
			builder.define(TacticianSkeleton.BACK_ITEM, ItemStack.EMPTY);
		}
	}
}
