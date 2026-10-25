package com.example.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Поле mob объявлено в TargetGoal, а не в HurtByTargetGoal.
 * @Shadow с дочернего класса его не видит и роняет запуск игры.
 */
@Mixin(TargetGoal.class)
public interface TargetGoalMobAccessor {

	@Accessor("mob")
	Mob evolvingthreats$getMob();
}
