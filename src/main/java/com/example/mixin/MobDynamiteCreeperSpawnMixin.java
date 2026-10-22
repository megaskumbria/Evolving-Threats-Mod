package com.example.mixin;

import com.example.creeper.DynamiteCreeper;
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
 * Шанс особого крипера при появлении: подрывник (5%), метатель (4%), ломатель щита (5%).
 * Если один уже выпал, следующие шансы на этого крипера не бросаются.
 * Метод finalizeSpawn объявлен в Mob, у Creeper своей копии нет — поэтому mixin на Mob.
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
			SapperCreeper.rollOnSpawn(creeper, spawnReason);
			DynamiteCreeper.rollOnSpawn(creeper, level, spawnReason);
			ShieldBreakCreeper.rollOnSpawn(creeper, spawnReason);
		}
	}
}
