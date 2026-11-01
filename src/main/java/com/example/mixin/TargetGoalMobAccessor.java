package com.example.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The mob field is announced in TargetGoal, not HurtByTargetGoal.
 * @Shadow from the child class does not see him and drops the launch of the game.
 */
@Mixin(TargetGoal.class)
public interface TargetGoalMobAccessor {

	@Accessor("mob")
	Mob evolvingthreats$getMob();
}
