package com.example.mixin;

import com.example.MobNames;
import com.example.entity.ZombieMjolnirEntity;
import com.example.zombie.ZombieMjolnir;
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
 * Обычный зомби помнит, что он Mjölnir, и после загрузки мира не теряет это.
 * Бирка и редкий молот — {@link ZombieMjolnirLootMixin}. Молот и имя выдаёт {@link ZombieMjolnir}.
 */
@Mixin(Zombie.class)
public class ZombieMjolnirMixin implements ZombieMjolnirEntity {

	@Unique
	private boolean evolvingthreats$mjolnir;

	@Override
	public boolean evolvingthreats$isMjolnir() {
		return evolvingthreats$mjolnir;
	}

	@Override
	public void evolvingthreats$setMjolnir(boolean mjolnir) {
		this.evolvingthreats$mjolnir = mjolnir;
	}

	@Inject(method = "getBaseExperienceReward", at = @At("RETURN"), cancellable = true)
	private void evolvingthreats$mjolnirXp(ServerLevel level, CallbackInfoReturnable<Integer> cir) {
		int xp = ZombieMjolnir.experienceReward((Zombie) (Object) this);
		if (xp >= 0) {
			cir.setReturnValue(xp);
		}
	}

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveMjolnir(ValueOutput output, CallbackInfo ci) {
		if (!evolvingthreats$mjolnir) {
			return;
		}
		output.putBoolean("EvolvingThreatsZombieMjolnir", true);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadMjolnir(ValueInput input, CallbackInfo ci) {
		evolvingthreats$mjolnir = input.getBooleanOr("EvolvingThreatsZombieMjolnir", false);
		if (evolvingthreats$mjolnir) {
			MobNames.showOnlyWhenLookedAt((Zombie) (Object) this);
		}
	}
}
