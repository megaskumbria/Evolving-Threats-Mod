package com.example.mixin;

import com.example.entity.ZombieGrudgeEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(Zombie.class)
public class ZombieGrudgeSaveMixin {

	@Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$saveGrudge(ValueOutput output, CallbackInfo ci) {
		Zombie self = (Zombie) (Object) this;
		if (!(self instanceof ZombieGrudgeEntity grudge)) {
			return;
		}
		UUID id = grudge.evolvingthreats$getGrudgePlayerId();
		if (id != null) {
			output.putString("EvolvingThreatsGrudgePlayer", id.toString());
			output.putLong("EvolvingThreatsGrudgeStarted", grudge.evolvingthreats$getGrudgeStartedGameTime());
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void evolvingthreats$loadGrudge(ValueInput input, CallbackInfo ci) {
		Zombie self = (Zombie) (Object) this;
		if (!(self instanceof ZombieGrudgeEntity grudge)) {
			return;
		}
		input.getString("EvolvingThreatsGrudgePlayer").ifPresent(s -> {
			try {
				grudge.evolvingthreats$setGrudgePlayerId(UUID.fromString(s));
				grudge.evolvingthreats$setGrudgeStartedGameTime(input.getLongOr("EvolvingThreatsGrudgeStarted", 0L));
			} catch (IllegalArgumentException ignored) {
				grudge.evolvingthreats$setGrudgePlayerId(null);
			}
		});
	}
}
