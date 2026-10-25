package com.example.mixin;

import com.example.entity.SkeletonSquadMember;
import com.example.entity.ai.CaptainHuntGoal;
import com.example.entity.ai.SkeletonSquadLeashGoal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Поводок отряда вешается на всех обычных скелетов, но молчит, пока скелет не капитан и не помощник.
 * Цель регистрируется здесь: у странника и иссушителя своего registerGoals, но список целей общий.
 */
@Mixin(AbstractSkeleton.class)
public class SkeletonSquadGoalMixin {

	@Inject(method = "registerGoals", at = @At("RETURN"))
	private void evolvingthreats$squadLeash(CallbackInfo ci) {
		if (!((Object) this instanceof Skeleton skeleton) || !((Object) this instanceof SkeletonSquadMember squad)) {
			return;
		}
		skeleton.getGoalSelector().addGoal(1, new CaptainHuntGoal(skeleton, squad));
		skeleton.getGoalSelector().addGoal(2, new SkeletonSquadLeashGoal(skeleton, squad));
	}
}
