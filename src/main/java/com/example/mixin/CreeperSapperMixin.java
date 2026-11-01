package com.example.mixin;

import com.example.entity.SapperCreeperEntity;
import com.example.entity.ai.SapperBreachGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 Creeper: the flag "subversive", the purpose of AI and conservation.
 * Chance when appearing - in {@link MobDynamiteCreeperSpawnMixin}, together with the thrower.
 * Regular detonation is turned off in {@link SwellGoalMixin}, only detonates its target.
 */
@Mixin(Creeper.class)
public class CreeperSapperMixin implements SapperCreeperEntity {

	@Unique
	private boolean evolvingthreats$sapperCreeper;

	@Override
	public boolean evolvingthreats$isSapperCreeper() {
		return evolvingthreats$sapperCreeper;
	}

	@Override
	public void evolvingthreats$setSapperCreeper(boolean sapper) {
		this.evolvingthreats$sapperCreeper = sapper;
	}

	@Inject(method = "registerGoals", at = @At("RETURN"))
	private void evolvingthreats$registerSapperGoal(CallbackInfo ci) {
		Creeper self = (Creeper) (Object) this;
		self.getGoalSelector().addGoal(2, new SapperBreachGoal(self));
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveSapperCreeper(ValueOutput output, CallbackInfo ci) {
		if (evolvingthreats$sapperCreeper) {
			output.putBoolean("EvolvingThreatsSapperCreeper", true);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadSapperCreeper(ValueInput input, CallbackInfo ci) {
		evolvingthreats$sapperCreeper = input.getBooleanOr("EvolvingThreatsSapperCreeper", false);
	}
}
