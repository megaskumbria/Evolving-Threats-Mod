package com.example.mixin;

import com.example.witch.WitchInvisibility;
import net.minecraft.world.entity.monster.Witch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Глоток невидимости в начале тика ведьмы.
 * Игра потом сама допивает бутылку и вешает эффект. Пока идёт глоток, бросок не начинается.
 */
@Mixin(Witch.class)
public class WitchInvisibilityMixin {

	/** Сколько тиков ещё держать бутылку у рта. Поле игры, само в мир не пишется. */
	@Shadow
	private int usingTime;

	/** Тики до следующей проверки. Меньше нуля — шанс уже выпал, ждём свободный глоток. */
	@Unique
	private int evolvingthreats$invisibilityCheck;

	@Inject(method = "aiStep", at = @At("HEAD"))
	private void evolvingthreats$drinkInvisibility(CallbackInfo ci) {
		WitchInvisibility.Drink drink = WitchInvisibility.tick((Witch) (Object) this, this.evolvingthreats$invisibilityCheck);
		this.evolvingthreats$invisibilityCheck = drink.cooldownTicks();
		if (drink.useTicks() > 0) {
			this.usingTime = drink.useTicks();
		}
	}
}
