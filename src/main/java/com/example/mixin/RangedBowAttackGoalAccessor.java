package com.example.mixin;

import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Дальность стрельбы в ванили задана один раз и закрыта. Здесь её можно сменить. */
@Mixin(RangedBowAttackGoal.class)
public interface RangedBowAttackGoalAccessor {

	@Mutable
	@Accessor("attackRadiusSqr")
	void evolvingthreats$setAttackRadiusSqr(float radiusSqr);
}
