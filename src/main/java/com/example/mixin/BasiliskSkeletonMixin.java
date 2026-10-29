package com.example.mixin;

import com.example.MobNames;
import com.example.entity.BasiliskSkeletonEntity;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Обычный скелет помнит, что он Василиск.
 * После загрузки мира заново выбирает стрельбу, чтобы дальняя дистанция не сбросилась.
 * Опыт и лут — {@link BasiliskLootMixin}. Дальность выстрела — {@link BasiliskBowMixin}.
 */
@Mixin(Skeleton.class)
public class BasiliskSkeletonMixin implements BasiliskSkeletonEntity {

	@Unique
	private boolean evolvingthreats$basilisk;

	@Override
	public boolean evolvingthreats$isBasilisk() {
		return evolvingthreats$basilisk;
	}

	@Override
	public void evolvingthreats$setBasilisk(boolean basilisk) {
		this.evolvingthreats$basilisk = basilisk;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveBasilisk(ValueOutput output, CallbackInfo ci) {
		if (evolvingthreats$basilisk) {
			output.putBoolean("EvolvingThreatsBasilisk", true);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadBasilisk(ValueInput input, CallbackInfo ci) {
		evolvingthreats$basilisk = input.getBooleanOr("EvolvingThreatsBasilisk", false);
		if (evolvingthreats$basilisk) {
			Skeleton self = (Skeleton) (Object) this;
			self.reassessWeaponGoal();
			MobNames.showOnlyWhenLookedAt(self);
		}
	}
}
