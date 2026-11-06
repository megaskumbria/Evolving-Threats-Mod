package com.example.mixin;

import com.example.creeper.ChargedCreeper;
import com.example.creeper.DynamiteCreeper;
import com.example.creeper.FastCreeper;
import com.example.creeper.FeintCreeper;
import com.example.creeper.MagnetCreeper;
import com.example.creeper.SapperCreeper;
import com.example.creeper.ShieldBreakCreeper;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rolls special creepers when one spawns.
 * Charged first (separate roll, can stack). Then sapper, dynamite thrower, shield-breaker,
 * feint, super-fast, magnet. Magnet skips those chase variants, but can still be charged
 * or a shield-breaker.
 * Mixin is on Mob because Creeper does not override finalizeSpawn.
 */
@Mixin(Mob.class)
public class MobDynamiteCreeperSpawnMixin {

	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void evolvingthreats$rollDynamiteCreeper(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason,
			SpawnGroupData groupData,
			CallbackInfoReturnable<SpawnGroupData> cir) {
		if ((Object) this instanceof Creeper creeper) {
			ChargedCreeper.rollOnSpawn(creeper, spawnReason);
			SapperCreeper.rollOnSpawn(creeper, spawnReason);
			DynamiteCreeper.rollOnSpawn(creeper, level, spawnReason);
			ShieldBreakCreeper.rollOnSpawn(creeper, spawnReason);
			FeintCreeper.rollOnSpawn(creeper, spawnReason);
			FastCreeper.rollOnSpawn(creeper, spawnReason);
			MagnetCreeper.rollOnSpawn(creeper, spawnReason);
		}
	}
}
