package com.example.mixin;

import com.example.RandomWeapons;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * В ванили скелет включает либо стрельбу, либо ближний бой.
 * Для «лук + меч» после стандартной логики добавляем ещё цель ближнего боя.
 */
@Mixin(AbstractSkeleton.class)
public abstract class AbstractSkeletonDualWieldMixin {

	private static final int WEAPON_GOAL_PRIORITY = 4;

	@Shadow
	private MeleeAttackGoal meleeGoal;

	@Inject(method = "reassessWeaponGoal", at = @At("RETURN"))
	private void evolvingthreats$addMeleeForDualWield(CallbackInfo ci) {
		AbstractSkeleton self = (AbstractSkeleton) (Object) this;
		if (RandomWeapons.isDualWieldArcher(self)) {
			// goalSelector объявлен в классе Mob — берём через getter, а не @Shadow.
			self.getGoalSelector().addGoal(WEAPON_GOAL_PRIORITY, meleeGoal);
		}
	}
}
