package com.example.mixin;

import com.example.entity.ZombieExcaliburEntity;
import com.example.zombie.ZombieExcalibur;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Обычный зомби помнит, что он Excalibur, и после загрузки мира не теряет это.
 * Бирка — {@link ZombieExcaliburLootMixin}. Меч и имя выдаёт {@link ZombieExcalibur}.
 */
@Mixin(Zombie.class)
public class ZombieExcaliburMixin implements ZombieExcaliburEntity {

	@Unique
	private boolean evolvingthreats$excalibur;

	@Override
	public boolean evolvingthreats$isExcalibur() {
		return evolvingthreats$excalibur;
	}

	@Override
	public void evolvingthreats$setExcalibur(boolean excalibur) {
		this.evolvingthreats$excalibur = excalibur;
	}

	@Inject(method = "getBaseExperienceReward", at = @At("RETURN"), cancellable = true)
	private void evolvingthreats$excaliburXp(ServerLevel level, CallbackInfoReturnable<Integer> cir) {
		int xp = ZombieExcalibur.experienceReward((Zombie) (Object) this);
		if (xp >= 0) {
			cir.setReturnValue(xp);
		}
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveExcalibur(ValueOutput output, CallbackInfo ci) {
		if (!evolvingthreats$excalibur) {
			return;
		}
		output.putBoolean("EvolvingThreatsZombieExcalibur", true);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadExcalibur(ValueInput input, CallbackInfo ci) {
		evolvingthreats$excalibur = input.getBooleanOr("EvolvingThreatsZombieExcalibur", false);
	}
}
