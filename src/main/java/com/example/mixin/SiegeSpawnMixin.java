package com.example.mixin;

import com.example.siege.SiegeNight;
import com.example.siege.SiegeWaves;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * С предупреждения и до рассвета каждый зомби и скелет, что появился сам (ночной спавн игры),
 * тоже получает метку осады и идёт к игроку, как и мобы волн.
 * Появились они командой, спавнером, яйцом или как помощники — не трогаем: это не осада.
 * Подмена других мобов на зомби и скелетов — в NaturalMonsterSwap.
 */
@Mixin({ Zombie.class, AbstractSkeleton.class })
public class SiegeSpawnMixin {

	@Inject(method = "finalizeSpawn", at = @At("RETURN"))
	private void evolvingthreats$siegeEnlist(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason spawnReason,
			SpawnGroupData groupData,
			CallbackInfoReturnable<SpawnGroupData> cir) {
		if (spawnReason != EntitySpawnReason.NATURAL) {
			return;
		}
		Mob mob = (Mob) (Object) this;
		if (SiegeNight.isWindowOpen(mob.level())) {
			SiegeWaves.enlist(mob);
		}
	}
}
