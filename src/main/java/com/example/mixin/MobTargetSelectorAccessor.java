package com.example.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Выбор цели (targetSelector) объявлен в Mob и закрыт.
 * Открытого метода, как у обычных целей (getGoalSelector), у него нет.
 * @Shadow из mixin на зомби или скелета это поле не видит: оно лежит в родителе.
 */
@Mixin(Mob.class)
public interface MobTargetSelectorAccessor {

	@Accessor("targetSelector")
	GoalSelector evolvingthreats$getTargetSelector();
}
