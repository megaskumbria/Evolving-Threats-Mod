package com.example.mixin;

import com.example.ritual.RitualBand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Свои из стаи не бьют друг друга.
 * Стая пиглина не берёт урон от лавы и от огня в тени.
 * Удар стаи иссушителя вешает короткое иссушение, если удар прошёл.
 */
@Mixin(LivingEntity.class)
public class RitualHurtMixin {

	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void evolvingthreats$ritualHurt(
			ServerLevel level,
			DamageSource source,
			float damage,
			CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (source.getEntity() instanceof LivingEntity attacker && RitualBand.areAllies(self, attacker)) {
			cir.setReturnValue(false);
			return;
		}
		if (self instanceof Mob mob && RitualBand.shouldCancelFire(mob, source)) {
			mob.clearFire();
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "hurtServer", at = @At("RETURN"))
	private void evolvingthreats$ritualWither(
			ServerLevel level,
			DamageSource source,
			float damage,
			CallbackInfoReturnable<Boolean> cir) {
		if (!Boolean.TRUE.equals(cir.getReturnValue())) {
			return;
		}
		RitualBand.applyWither(source, (LivingEntity) (Object) this);
	}
}
