package com.example.mixin;

import com.example.skeleton.SpectralArrowHunt;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Охота после спектральной стрелы.
 * Цель ставится уже после выбора цели мобом и прямо перед шагом пути,
 * иначе он тут же забывает игрока за стеной и никуда не идёт.
 */
@Mixin(Mob.class)
public class MobSpectralHuntMixin {

	@Inject(
			method = "serverAiStep",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;tick()V"
			)
	)
	private void evolvingthreats$keepSpectralHunt(CallbackInfo ci) {
		SpectralArrowHunt.keepChasing((Mob) (Object) this);
	}
}
