package com.example.mixin;

import com.example.entity.DynamiteCreeperEntity;
import com.example.entity.ai.DynamiteThrowGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Крипер: флаг «метатель динамита», цель ИИ и сохранение в мире.
 * Шанс при появлении — в {@link MobDynamiteCreeperSpawnMixin}: у Creeper нет своего finalizeSpawn.
 * Подрыв вплотную выключен отдельно в {@link SwellGoalMixin}.
 */
@Mixin(Creeper.class)
public class CreeperDynamiteMixin implements DynamiteCreeperEntity {

	@Unique
	private boolean evolvingthreats$dynamiteCreeper;

	@Override
	public boolean evolvingthreats$isDynamiteCreeper() {
		return evolvingthreats$dynamiteCreeper;
	}

	@Override
	public void evolvingthreats$setDynamiteCreeper(boolean dynamite) {
		this.evolvingthreats$dynamiteCreeper = dynamite;
	}

	@Inject(method = "registerGoals", at = @At("RETURN"))
	private void evolvingthreats$registerDynamiteGoal(CallbackInfo ci) {
		Creeper self = (Creeper) (Object) this;
		// Приоритет 2 — как у ванильного подрыва. Подрыв для метателя выключен, поэтому слот занимает бросок.
		self.getGoalSelector().addGoal(2, new DynamiteThrowGoal(self));
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveDynamiteCreeper(ValueOutput output, CallbackInfo ci) {
		if (evolvingthreats$dynamiteCreeper) {
			output.putBoolean("EvolvingThreatsDynamiteCreeper", true);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadDynamiteCreeper(ValueInput input, CallbackInfo ci) {
		evolvingthreats$dynamiteCreeper = input.getBooleanOr("EvolvingThreatsDynamiteCreeper", false);
	}
}
