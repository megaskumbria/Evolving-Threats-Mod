package com.example.mixin;

import com.example.mirror.Mirror;
import com.example.piglin.PiglinBruteHealth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 The appearance of brutal piglin. The game has already put him in 50 health.
 * Here the strip becomes the one in {@link PiglinBruteHealth}.
 */
@Mixin(PiglinBrute.class)
public class PiglinBruteHealthMixin {

	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void evolvingthreats$bruteHealth(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason,
			SpawnGroupData groupData,
			CallbackInfoReturnable<SpawnGroupData> cir) {
		PiglinBrute brute = (PiglinBrute) (Object) this;
		PiglinBruteHealth.onSpawn(brute);
		Mirror.tryRoll(brute, spawnReason);
	}
}
