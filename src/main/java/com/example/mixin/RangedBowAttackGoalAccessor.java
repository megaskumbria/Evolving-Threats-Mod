package com.example.mixin;

import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/*The range of shooting in vanilla is set once and closed. You can change it here. */
@Mixin(RangedBowAttackGoal.class)
public interface RangedBowAttackGoalAccessor {

	@Mutable
	@Accessor("attackRadiusSqr")
	void evolvingthreats$setAttackRadiusSqr(float radiusSqr);
}
