package com.example.mixin;

import com.example.creeper.DynamiteCreeper;
import com.example.creeper.SapperCreeper;
import net.minecraft.world.entity.ai.goal.SwellGoal;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Метатель динамита и подрывник не используют обычное «надуться вплотную».
 * Подрывник взрывается своей целью, метатель кидает TNT.
 * Огниво по-прежнему поджигает крипера: это делает сам крипер в tick(), не эта цель.
 */
@Mixin(SwellGoal.class)
public class SwellGoalMixin {

	@Shadow
	private Creeper creeper;

	@Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
	private void evolvingthreats$noSwellForDynamiteThrower(CallbackInfoReturnable<Boolean> cir) {
		if (DynamiteCreeper.isDynamite(creeper) || SapperCreeper.isSapper(creeper)) {
			cir.setReturnValue(false);
		}
	}
}
