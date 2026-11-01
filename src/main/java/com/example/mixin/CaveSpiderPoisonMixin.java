package com.example.mixin;

import com.example.entity.LethalPoisonEntity;
import com.example.spider.CaveSpiderPoison;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 Marks whose poison is currently hanging and remembers it after resetting.
 It works when the creature is hanged, filmed or recorded effects.
 * The damage itself is on {@link PoisonTickMixin}.
 */
@Mixin(LivingEntity.class)
public class CaveSpiderPoisonMixin implements LethalPoisonEntity {

	@Unique
	private boolean evolvingthreats$lethalCavePoison;

	@Override
	public boolean evolvingthreats$isLethalCavePoison() {
		return evolvingthreats$lethalCavePoison;
	}

	@Override
	public void evolvingthreats$setLethalCavePoison(boolean lethal) {
		this.evolvingthreats$lethalCavePoison = lethal;
	}

	@Inject(
			method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
			at = @At("RETURN"))
	private void evolvingthreats$markCavePoison(
			MobEffectInstance effect,
			@Nullable Entity source,
			CallbackInfoReturnable<Boolean> cir) {
		CaveSpiderPoison.onPoisonApplied(
				(LivingEntity) (Object) this,
				effect,
				source,
				Boolean.TRUE.equals(cir.getReturnValue()));
	}

	@Inject(method = "forceAddEffect", at = @At("RETURN"))
	private void evolvingthreats$forceCavePoison(MobEffectInstance effect, @Nullable Entity source, CallbackInfo ci) {
		CaveSpiderPoison.onPoisonForced((LivingEntity) (Object) this, effect, source);
	}

	@Inject(method = "removeEffect", at = @At("RETURN"))
	private void evolvingthreats$clearCavePoison(Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> cir) {
		if (effect.equals(MobEffects.POISON)) {
			evolvingthreats$lethalCavePoison = false;
		}
	}

	@Inject(method = "removeAllEffects", at = @At("RETURN"))
	private void evolvingthreats$clearCavePoisonMilk(CallbackInfoReturnable<Boolean> cir) {
		evolvingthreats$lethalCavePoison = false;
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveCavePoison(ValueOutput output, CallbackInfo ci) {
		if (evolvingthreats$lethalCavePoison && ((LivingEntity) (Object) this).hasEffect(MobEffects.POISON)) {
			output.putBoolean("EvolvingThreatsLethalCavePoison", true);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadCavePoison(ValueInput input, CallbackInfo ci) {
		evolvingthreats$lethalCavePoison = input.getBooleanOr("EvolvingThreatsLethalCavePoison", false);
	}
}
