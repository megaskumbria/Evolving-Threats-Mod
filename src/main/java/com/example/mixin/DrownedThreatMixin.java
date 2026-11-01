package com.example.mixin;

import com.example.drowned.DrownedThreats;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 The appearance of the drowned: a turtle helmet and charms on the trident, which has already given the game.
 The trident itself does not create fashion. If the game has managed to hang its charms, they are replaced.
 * Numbers - {@link DrownedThreats}.
 */
@Mixin(Drowned.class)
public class DrownedThreatMixin {

	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void evolvingthreats$drownedGear(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason,
			SpawnGroupData groupData,
			CallbackInfoReturnable<SpawnGroupData> cir) {
		DrownedThreats.onSpawn((Drowned) (Object) this);
	}
}
