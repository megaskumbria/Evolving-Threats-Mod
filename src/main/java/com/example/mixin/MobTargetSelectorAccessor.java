package com.example.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 TargetSelector is declared in Mob and closed.
 It does not have an open method, like the usual goals (getGoalSelector).
 * @Shadow from mixin on zombies or skeleton doesn't see this field: it lies in the parent.
 */
@Mixin(Mob.class)
public interface MobTargetSelectorAccessor {

	@Accessor("targetSelector")
	GoalSelector evolvingthreats$getTargetSelector();
}
