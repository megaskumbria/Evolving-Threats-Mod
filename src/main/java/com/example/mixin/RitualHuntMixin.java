package com.example.mixin;

import com.example.ritual.RitualBand;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Стая с головой игрока снова берёт цель до того, как цели ИИ успеют её сбросить.
 * Точки те же, что у памяти обычного зомби: иначе за стеной цель пропадает.
 * В конце шага стая пиглина тушится: солнце поджигает зомби уже после целей.
 */
@Mixin(Mob.class)
public abstract class RitualHuntMixin {

	@Inject(
			method = "serverAiStep",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tickRunningGoals(Z)V",
					ordinal = 1,
					shift = At.Shift.BEFORE))
	private void evolvingthreats$ritualHuntBeforeRunGoals(CallbackInfo ci) {
		RitualBand.syncHuntTarget((Mob) (Object) this);
	}

	@Inject(
			method = "serverAiStep",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tick()V",
					ordinal = 1,
					shift = At.Shift.BEFORE))
	private void evolvingthreats$ritualHuntBeforeGoalTick(CallbackInfo ci) {
		RitualBand.syncHuntTarget((Mob) (Object) this);
	}

	@Inject(method = "aiStep", at = @At("RETURN"))
	private void evolvingthreats$ritualNoSun(CallbackInfo ci) {
		RitualBand.keepOutOfFire((Mob) (Object) this);
	}
}
