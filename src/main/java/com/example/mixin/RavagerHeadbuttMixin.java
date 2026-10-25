package com.example.mixin;

import com.example.entity.ai.RavagerHeadbuttGoal;
import net.minecraft.world.entity.monster.Ravager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Разоритель получает цель «пробить стену головой».
 * Что ломается и какая пауза — {@link com.example.illager.RavagerWallBreak}.
 */
@Mixin(Ravager.class)
public class RavagerHeadbuttMixin {

	@Inject(method = "registerGoals", at = @At("RETURN"))
	private void evolvingthreats$headbuttWall(CallbackInfo ci) {
		Ravager self = (Ravager) (Object) this;
		// 3 — выше обычного укуса (4). Если путь есть, эта цель молчит и работает укус.
		self.getGoalSelector().addGoal(3, new RavagerHeadbuttGoal(self));
	}
}
