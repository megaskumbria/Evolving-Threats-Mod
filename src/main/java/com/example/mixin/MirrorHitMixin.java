package com.example.mixin;

import com.example.mirror.Mirror;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Удар по живому существу. Число в начале метода — до брони того, кого ударили.
 * Если удар дошёл и это Mirror, то же число возвращается игроку, и уже его броня его режет.
 * Щит мода гасит удар в начале другого mixin: тогда сюда приходит «удар не дошёл».
 */
@Mixin(LivingEntity.class)
public class MirrorHitMixin {

	/** Урон до брони цели. Игра дальше может уменьшить местную переменную, это число не трогает. */
	@Unique
	private float evolvingthreats$mirrorIncoming;

	@Inject(method = "hurtServer", at = @At("HEAD"))
	private void evolvingthreats$rememberMirrorIncoming(
			ServerLevel level,
			DamageSource source,
			float damage,
			CallbackInfoReturnable<Boolean> cir) {
		this.evolvingthreats$mirrorIncoming = damage;
	}

	@Inject(method = "hurtServer", at = @At("RETURN"))
	private void evolvingthreats$mirrorHit(
			ServerLevel level,
			DamageSource source,
			float damage,
			CallbackInfoReturnable<Boolean> cir) {
		Mirror.onHurt(
				(LivingEntity) (Object) this,
				level,
				source,
				this.evolvingthreats$mirrorIncoming,
				Boolean.TRUE.equals(cir.getReturnValue()));
	}
}
