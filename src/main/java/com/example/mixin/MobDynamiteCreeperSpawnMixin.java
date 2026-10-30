package com.example.mixin;

import com.example.creeper.ChargedCreeper;
import com.example.creeper.DynamiteCreeper;
import com.example.creeper.FastCreeper;
import com.example.creeper.FeintCreeper;
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
 * Шанс особого крипера при появлении: заряженный (1%), подрывник (5%), метатель (4%), ломатель щита (5%),
 * обманка (0,3%), супербыстрый (3%). Быстрый и обманка не бывают подрывником и метателем и не бывают друг другом.
 * Заряженный динамит не бросает, но может быть подрывником или ломателем щита.
 * Если подрывник или метатель уже выпал, ломатель щита на этого крипера не бросается.
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
			ChargedCreeper.rollOnSpawn(creeper, spawnReason);
			SapperCreeper.rollOnSpawn(creeper, spawnReason);
			DynamiteCreeper.rollOnSpawn(creeper, level, spawnReason);
			ShieldBreakCreeper.rollOnSpawn(creeper, spawnReason);
			FeintCreeper.rollOnSpawn(creeper, spawnReason);
			FastCreeper.rollOnSpawn(creeper, spawnReason);
		}
	}
}
