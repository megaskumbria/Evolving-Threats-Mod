package com.example.mixin;

import com.example.entity.ai.BreakToTargetGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Добавляет зомби цель «прокопать путь к игроку».
 * Кто умеет ломать (все / часть, утопленники и пиглины нет) — {@link com.example.digging.BlockBreaking#isBreaker}.
 */
@Mixin(Zombie.class)
public class ZombieBreakGoalsMixin {

	@Inject(method = "registerGoals", at = @At("RETURN"))
	private void evolvingthreats$registerBreakGoal(CallbackInfo ci) {
		Zombie self = (Zombie) (Object) this;
		self.getGoalSelector().addGoal(4, new BreakToTargetGoal(self));
	}
}
